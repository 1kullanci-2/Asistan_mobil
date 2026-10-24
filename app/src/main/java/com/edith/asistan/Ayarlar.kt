package com.edith.asistan

import android.content.Context

/** Ayarlar yalnızca bu telefonda saklanır (uygulamaya özel alan), repoya gitmez. */
class Ayarlar(ctx: Context) {
    private val sp = ctx.getSharedPreferences("edith", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()
}
