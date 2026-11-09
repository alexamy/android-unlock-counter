package com.alexamy.unlockcounter

import android.content.res.AssetManager
import android.graphics.Typeface

enum class NumberFont(val label: String, private val file: String?, private val weight: Int?) {
    SIX_CAPS("Six Caps", "SixCaps.ttf", null),
    ANTONIO("Antonio", "Antonio.ttf", 100),
    BIG_SHOULDERS("Big Shoulders", "BigShouldersDisplay.ttf", 100),
    OSWALD("Oswald", "Oswald.ttf", 200),
    SYSTEM("System default", null, null);

    fun load(assets: AssetManager): Typeface = file?.let { path ->
        Typeface.Builder(assets, "fonts/$path")
            .apply { weight?.let { setFontVariationSettings("'wght' $it") } }
            .build()
    } ?: Typeface.DEFAULT
}
