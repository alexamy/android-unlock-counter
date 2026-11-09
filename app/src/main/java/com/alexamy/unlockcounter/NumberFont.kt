package com.alexamy.unlockcounter

import android.content.res.AssetManager
import android.graphics.Typeface

enum class NumberFont(private val file: String, private val weight: Int?) {
    SIX_CAPS("SixCaps.ttf", null),
    ANTONIO("Antonio.ttf", 100),
    BIG_SHOULDERS("BigShouldersDisplay.ttf", 100),
    OSWALD("Oswald.ttf", 200);

    fun load(assets: AssetManager): Typeface = Typeface.Builder(assets, "fonts/$file")
        .apply { weight?.let { setFontVariationSettings("'wght' $it") } }
        .build()
}
