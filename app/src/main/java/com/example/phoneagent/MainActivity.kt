package com.example.phoneagent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    override fun attachBaseContext(b: Context) = super.attachBaseContext(Prefs.wrap(b))

    private lateinit var status: TextView
    private lateinit var logv: TextView
    private lateinit var task: EditText
    private var lang = ""

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        lang = Prefs.get(this, "lang", "auto")
        val p = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, p, p, p) }
        fun btn(t: Int, f: () -> Unit) = Button(this).apply { setText(t); setOnClickListener { f() } }
        status = TextView(this).apply { textSize = 16f }
        task = EditText(this).apply { setHint(R.string.task_hint); minLines = 2 }
        logv = TextView(this).apply { textSize = 13f; setTextIsSelectable(true) }
        root.addView(status)
        root.addView(btn(R.string.enable_service) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        root.addView(task)
        root.addView(btn(R.string.start) { start() })
        root.addView(btn(R.string.stop) { Agent.stop = true })
        root.addView(btn(R.string.settings) { startActivity(Intent(this, SettingsActivity::class.java)) })
        root.addView(ScrollView(this).apply { addView(logv) })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (lang != Prefs.get(this, "lang", "auto")) { recreate(); return }
        status.setText(if (PhoneService.inst != null) R.string.svc_on else R.string.svc_off)
        logv.text = Agent.log.toString()
        Agent.onLog = { t -> runOnUiThread { logv.text = t } }
    }

    override fun onPause() { super.onPause(); Agent.onLog = null }

    private fun start() {
        val svc = PhoneService.inst
        if (svc == null) { status.setText(R.string.svc_off); return }
        if (Prefs.get(this, "url").isBlank() || Prefs.get(this, "model").isBlank()) {
            Toast.makeText(this, R.string.need_setup, Toast.LENGTH_LONG).show(); return
        }
        val t = task.text.toString().trim()
        if (t.isEmpty()) return
        Agent.start(applicationContext, svc, t)
    }
}
