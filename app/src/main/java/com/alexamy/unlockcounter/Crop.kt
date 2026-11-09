package com.alexamy.unlockcounter

/**
 * Part of a src bitmap (left, top, right, bottom) that fills dst with the same aspect ratio.
 * [x] slides a crop of a wider image from left (0) to right (1); a taller image is cropped centered.
 */
fun cropToFill(srcW: Int, srcH: Int, dstW: Int, dstH: Int, x: Float = 0.5f): IntArray =
    if (srcW.toLong() * dstH > srcH.toLong() * dstW) {
        val w = (srcH.toLong() * dstW / dstH).toInt()
        val left = ((srcW - w) * x.coerceIn(0f, 1f)).toInt()
        intArrayOf(left, 0, left + w, srcH)
    } else {
        val h = (srcW.toLong() * dstH / dstW).toInt()
        val top = (srcH - h) / 2
        intArrayOf(0, top, srcW, top + h)
    }
