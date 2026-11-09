package com.alexamy.unlockcounter

/** Part of a src bitmap (left, top, right, bottom) that fills dst with the same aspect ratio, centered. */
fun centerCrop(srcW: Int, srcH: Int, dstW: Int, dstH: Int): IntArray =
    if (srcW.toLong() * dstH > srcH.toLong() * dstW) {
        val w = (srcH.toLong() * dstW / dstH).toInt()
        val left = (srcW - w) / 2
        intArrayOf(left, 0, left + w, srcH)
    } else {
        val h = (srcW.toLong() * dstH / dstW).toInt()
        val top = (srcH - h) / 2
        intArrayOf(0, top, srcW, top + h)
    }
