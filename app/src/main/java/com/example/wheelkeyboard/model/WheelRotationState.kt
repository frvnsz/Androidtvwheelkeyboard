package com.example.wheelkeyboard.model

import android.os.SystemClock
import android.view.KeyEvent

data class WheelRotationState(
    val direction: Int,
    val startedAtMillis: Long = SystemClock.uptimeMillis(),
    val lastTickAtMillis: Long = 0L
) {
    fun delayMillis(now: Long = SystemClock.uptimeMillis()): Long {
        val heldFor = now - startedAtMillis
        return when {
            heldFor < 400L -> 360L
            heldFor < 1_200L -> 140L
            else -> 60L
        }
    }

    companion object {
        fun directionFor(keyCode: Int): Int? = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> 1
            KeyEvent.KEYCODE_DPAD_UP -> -1
            else -> null
        }
    }
}
