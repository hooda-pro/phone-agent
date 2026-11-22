package com.example.phoneagent

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*

fun Spinner.onPick(f: (Int) -> Unit) {
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: AdapterView<*>?, v: View?, i: Int, id: Long) = f(i)
        override fun onNothingSelected(p: AdapterView<*>?) {}
    }
}

class SettingsActivity : Activity() {
    override fun attachBaseContext(b: Context) = super.attachBaseContext(Prefs.wrap(b))

    // name, format, base url, default model
    private val presets = arrayOf(
        arrayOf("Anthropic", "anthropic", "https://api.anthropic.com", "claude-sonnet-5-5"),
        arrayOf("OpenAI", "openai", "https://api.openai.com/v1", "gpt-4o"),
        arrayOf("OpenRouter", "openai", "https://openrouter.ai/api/v1", "openai/gpt-4o"),
        arrayOf("Gemini", "openai", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
        arrayOf("Ollama / LM Studio", "openai", "http://192.168.1.10:11434/v1", "qwen2.5vl"),
        arrayOf("Custom", "openai", "", "")
    )
    private val fmts = arrayOf("anthropic", "openai")
    private val langs = arrayOf("auto", "ar", "en")

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val p = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(p, p, p, p) }
        fun label(t: Int) = root.addView(TextView(this).apply { setText(t); setPadding(0, p / 2, 0, 0) })
        fun field(v: String, type: Int = InputType.TYPE_CLASS_TEXT) =
            EditText(this).apply { setText(v); inputType = type; setSingleLine() }.also { root.addView(it) }
        fun spinner(items: Array<String>) =
            Spinner(this).apply { adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, items) }
                .also { root.addView(it) }

        label(R.string.preset)
        val preset = spinner(presets.map { it[0] }.toTypedArray())
        label(R.string.api_format)
        val fmt = spinner(arrayOf("Anthropic (Messages API)", "OpenAI-compatible"))
        label(R.string.base_url)
        val url = field(Prefs.get(this, "url"), InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT)
        label(R.string.api_key)
        val key = field(Prefs.get(this, "key"), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        label(R.string.model)
        val model = field(Prefs.get(this, "model"))
        label(R.string.max_steps)
        val steps = field(Prefs.get(this, "steps", "15"), InputType.TYPE_CLASS_NUMBER)
        label(R.string.language)
        val lang = spinner(arrayOf(getString(R.string.lang_auto), getString(R.string.lang_ar), getString(R.string.lang_en)))

        fmt.setSelection(fmts.indexOf(Prefs.get(this, "format", "openai")).coerceAtLeast(0))
        lang.setSelection(langs.indexOf(Prefs.get(this, "lang", "auto")).coerceAtLeast(0))
        val saved = presets.indexOfFirst { it[2] == url.text.toString() }
        preset.setSelection(if (saved >= 0) saved else presets.size - 1)

        var first = true
        preset.onPick { i ->
            if (first) { first = false; return@onPick }
            fmt.setSelection(fmts.indexOf(presets[i][1]))
            url.setText(presets[i][2])
            model.setText(presets[i][3])
        }

        root.addView(Button(this).apply {
            setText(R.string.save)
            setOnClickListener {
                Prefs.sp(this@SettingsActivity).edit()
                    .putString("format", fmts[fmt.selectedItemPosition])
                    .putString("url", url.text.toString().trim())
                    .putString("key", key.text.toString().trim())
                    .putString("model", model.text.toString().trim())
                    .putString("steps", steps.text.toString().trim())
                    .putString("lang", langs[lang.selectedItemPosition])
                    .apply()
                Toast.makeText(this@SettingsActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
