package com.example.sportapp.core.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibrationUtils {

    fun vibrateForNotification(
        context: Context,
        level: String
    ) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (!vibrator.hasVibrator()) return

            val pattern = vibrationPattern(level.lowercase())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (pattern.size == 1) {
                    // One-shot
                    VibrationEffect.createOneShot(
                        pattern[0],
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                } else {
                    // Waveform
                    VibrationEffect.createWaveform(pattern, -1)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: SecurityException) {
            // Permission missing or restricted → ignore
        }
    }

    /**
     * Vibration patterns:
     * - success: short
     * - info: normal
     * - warning: double tap
     * - error: strong double
     */
    private fun vibrationPattern(level: String): LongArray {
        return when (level) {
            "success" -> longArrayOf(40)
            "info" -> longArrayOf(60)
            "warning" -> longArrayOf(0, 30, 40, 30)
            "error" -> longArrayOf(0, 80, 40, 80)
            else -> longArrayOf(60) // info / default
        }
    }
}
