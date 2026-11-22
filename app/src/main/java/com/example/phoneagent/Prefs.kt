package com.example.phoneagent

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object Prefs {
    fun sp(c: Context) = c.getSharedPreferences("agent", Context.MODE_PRIVATE)
    fun get(c: Context, k: String, d: String = "") = sp(c).getString(k, d) ?: d

    fun wrap(c: Context): Context {
        val lang = get(c, "lang", "auto")
        if (lang == "auto") return c
        val loc = Locale.forLanguageTag(lang)
        Locale.setDefault(loc)
        val cfg = Configuration(c.resources.configuration)
        cfg.setLocale(loc)
        cfg.setLayoutDirection(loc)
        return c.createConfigurationContext(cfg)
    }
}
