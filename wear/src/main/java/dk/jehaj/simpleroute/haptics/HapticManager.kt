package dk.jehaj.simpleroute.haptics

import android.Manifest
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import dk.jehaj.simpleroute.data.model.TurnType

class HapticManager(private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator ?: context.getSystemService(Vibrator::class.java)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Triggers the directional haptic vibration for the given turn type according to SPEC:
     * - Turn Left: Single long, weighted pulse [0, 500]
     * - Turn Right: Two crisp, rapid tap pulses [0, 150, 100, 150]
     * - Roundabout: Distinct rolling 3-pulse cadence [0, 100, 80, 100, 80, 250]
     * - U-Turn: Rapid alarm flutter [0, 80, 50, 80, 50, 80]
     * - Straight / Continue: Single crisp tap [0, 120]
     */
    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibrateForTurn(turnType: TurnType) {
        val pattern = turnType.hapticPattern
        if (pattern.isEmpty()) {
            Log.d(TAG, "No haptic pattern defined for turn: $turnType")
            return
        }

        vibratePattern(pattern)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibratePattern(timings: LongArray) {
        val vib = vibrator ?: run {
            Log.w(TAG, "Vibrator not available")
            return
        }

        if (!vib.hasVibrator()) {
            Log.w(TAG, "Device has no vibrator")
            return
        }

        try {
            // Provide explicit maximum amplitude (255) for active ON pulses
            // so vibrations are crisp and strongly felt on the cyclist's wrist over road buzz
            val amplitudes = IntArray(timings.size) { i ->
                if (i % 2 == 0) 0 else 255
            }
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)

            // Wear OS 3.0+ / Android 11+ requires ALARM or NAVIGATION attributes for background/service vibration.
            // Without explicit USAGE_ALARM attributes, Android classifies vibrations as USAGE_UNKNOWN / USAGE_TOUCH
            // and suppresses them when running from NavigationService, in ambient mode, or with the screen off.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vib.vibrate(effect, Api33Impl.ALARM_VIBRATION_ATTRIBUTES)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(effect, ALARM_AUDIO_ATTRIBUTES)
            }
            Log.i(TAG, "Triggered vibration pattern: ${timings.contentToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering vibration", e)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun cancel() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling vibration", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33Impl {
        val ALARM_VIBRATION_ATTRIBUTES: VibrationAttributes =
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
    }

    companion object {
        private const val TAG = "HapticManager"

        /**
         * Reusable attributes for API 30-32 background vibration.
         */
        @Suppress("DEPRECATION")
        private val ALARM_AUDIO_ATTRIBUTES: AudioAttributes by lazy {
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
        }
    }
}
