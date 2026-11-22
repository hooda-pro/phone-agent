package com.example.phoneagent

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object Agent {
    val log = StringBuilder()
    var onLog: ((String) -> Unit)? = null
    @Volatile var running = false
    @Volatile var stop = false

    private const val SYSTEM = """You control an Android phone. Each turn you receive a screenshot.
Reply with ONE JSON object only (no markdown). Coordinates are 0-1000 on both axes, (0,0) = top-left.
Actions:
{"action":"open_app","name":"app name"}
{"action":"tap","x":0,"y":0}
{"action":"long_press","x":0,"y":0}
{"action":"swipe","x1":0,"y1":0,"x2":0,"y2":0}
{"action":"type","text":"..."}  (replaces the text of the focused field; tap the field first)
{"action":"back"} {"action":"home"} {"action":"recents"} {"action":"wait"}
{"action":"done","message":"..."}
Add a short "thought" field to every reply. Use done when the task is finished or impossible.
For payments, sending money, deleting data or public posting: do not proceed, use done and ask the user to confirm.
Write messages in the user's language."""

    fun say(s: String) { log.append(s).append('\n'); onLog?.invoke(log.toString()) }

    fun start(c: Context, svc: PhoneService, task: String) {
        if (running) return
        running = true; stop = false; log.clear()
        Thread {
            try { loop(c, svc, task) } catch (e: Exception) { say("⚠ " + e.message) }
            running = false
        }.start()
    }

    private fun parse(s: String): JSONObject? = try {
        JSONObject(s.substring(s.indexOf('{'), s.lastIndexOf('}') + 1))
    } catch (e: Exception) { null }

    private fun loop(c: Context, svc: PhoneService, task: String) {
        val max = Prefs.get(c, "steps", "15").toIntOrNull() ?: 15
        val history = ArrayList<String>()
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        Thread.sleep(1200)
        for (step in 1..max) {
            if (stop) { say("■"); return }
            val bmp = svc.screenshot()
            if (bmp == null) { say("⚠ screenshot failed"); return }
            val w = bmp.width; val h = bmp.height
            val sc = 1024f / maxOf(w, h)
            val small = Bitmap.createScaledBitmap(bmp, (w * sc).toInt(), (h * sc).toInt(), true)
            val bos = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 70, bos)
            val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            val prompt = "Task: $task\nPrevious actions:\n" + history.joinToString("\n") + "\nNext action?"
            val reply = Llm.ask(c, SYSTEM, prompt, b64)
            if (stop) { say("■"); return }
            val a = parse(reply)
            if (a == null) { say("⚠ $reply"); history.add("(invalid reply)"); Thread.sleep(1000); continue }
            val act = a.optString("action")
            say("$step) ${a.optString("thought")} → $act")
            val fx = { k: String -> (a.optDouble(k, 0.0) / 1000.0 * w).toFloat() }
            val fy = { k: String -> (a.optDouble(k, 0.0) / 1000.0 * h).toFloat() }
            var res = "ok"
            when (act) {
                "tap" -> svc.gesture(fx("x"), fy("y"), fx("x"), fy("y"), 50)
                "long_press" -> svc.gesture(fx("x"), fy("y"), fx("x"), fy("y"), 800)
                "swipe" -> svc.gesture(fx("x1"), fy("y1"), fx("x2"), fy("y2"), 400)
                "type" -> if (!svc.typeText(a.optString("text"))) res = "no focused input"
                "open_app" -> if (!svc.openApp(a.optString("name"))) res = "app not found"
                "back" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "home" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                "recents" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                "wait" -> {}
                "done" -> { say("✅ " + a.optString("message")); return }
                else -> res = "unknown action"
            }
            if (res != "ok") say("   ⚠ $res")
            history.add("$step: $a -> $res")
            Thread.sleep(1500)
        }
        say("⚠ max steps reached")
    }
}

object Llm {
    fun ask(c: Context, system: String, text: String, b64: String): String {
        val base = Prefs.get(c, "url").trim().trimEnd('/')
        val key = Prefs.get(c, "key").trim()
        val model = Prefs.get(c, "model").trim()
        val anth = Prefs.get(c, "format", "openai") == "anthropic"
        val url: String
        val body: JSONObject
        if (anth) {
            url = base.removeSuffix("/v1") + "/v1/messages"
            val content = JSONArray()
                .put(JSONObject().put("type", "image").put("source", JSONObject()
                    .put("type", "base64").put("media_type", "image/jpeg").put("data", b64)))
                .put(JSONObject().put("type", "text").put("text", text))
            body = JSONObject().put("model", model).put("max_tokens", 1024).put("system", system)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        } else {
            url = "$base/chat/completions"
            val content = JSONArray()
                .put(JSONObject().put("type", "text").put("text", text))
                .put(JSONObject().put("type", "image_url").put("image_url",
                    JSONObject().put("url", "data:image/jpeg;base64,$b64")))
            body = JSONObject().put("model", model).put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", content)))
        }
        val con = URL(url).openConnection() as HttpURLConnection
        con.requestMethod = "POST"
        con.connectTimeout = 20000
        con.readTimeout = 90000
        con.doOutput = true
        con.setRequestProperty("Content-Type", "application/json")
        if (anth) {
            con.setRequestProperty("x-api-key", key)
            con.setRequestProperty("anthropic-version", "2023-06-01")
        } else if (key.isNotEmpty()) {
            con.setRequestProperty("Authorization", "Bearer $key")
        }
        con.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = con.responseCode
        val stream = if (code in 200..299) con.inputStream else con.errorStream
        val txt = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw Exception("HTTP $code: ${txt.take(300)}")
        val j = JSONObject(txt)
        return if (anth) j.getJSONArray("content").getJSONObject(0).getString("text")
        else j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }
}
