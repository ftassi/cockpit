package dev.ftassi.cockpit.carmode

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import android.view.KeyEvent
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.ftassi.cockpit.MainActivity

private const val ACTIVE = "active"
private const val LAST_PLAYER = "last_player"

// Driving setup: a dedicated Do Not Disturb mode and the car's DAC at full
// volume. State lives in SharedPreferences so it survives the app being killed
// mid-drive.
class CarMode(private val context: Context) {
    private val prefs = context.getSharedPreferences("car_mode", Context.MODE_PRIVATE)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val condition = "condition://dev.ftassi.cockpit/driving".toUri()

    val isActive: Boolean get() = prefs.getBoolean(ACTIVE, false)

    val hasDndAccess: Boolean get() = notifications.isNotificationPolicyAccessGranted

    // Package of the app that was playing when car mode last ended.
    var lastPlayer: String?
        get() = prefs.getString(LAST_PLAYER, null)
        set(value) = prefs.edit { putString(LAST_PLAYER, value) }

    // Requires hasDndAccess.
    fun enter() {
        if (isActive) return
        prefs.edit { putBoolean(ACTIVE, true) }
        setDriving(true)
        maxOutUsbVolume()
    }

    fun exit() {
        if (!isActive) return
        pauseWhateverIsPlaying()
        setDriving(false)
        prefs.edit { remove(ACTIVE) }
    }

    // The car's AUX input needs the DAC at full volume. Android keeps a volume
    // per output, so this never touches the speaker's; but in the EU it drops
    // headset outputs to a "safe" level after 20 hours of listening, and then
    // asks the user to confirm before going above it again.
    fun maxOutUsbVolume() {
        if (!isActive || !mediaGoesToUsb()) return
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
    }

    private fun mediaGoesToUsb(): Boolean {
        val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
        return audio.getAudioDevicesForAttributes(media).any { it.isUsbAudio() }
    }

    // The system restores whatever Do Not Disturb state was there before when
    // the rule goes off, including one the user turned on by hand.
    private fun setDriving(on: Boolean) {
        val state = if (on) Condition.STATE_TRUE else Condition.STATE_FALSE
        notifications.setAutomaticZenRuleState(
            drivingRuleId(),
            Condition(condition, "Guida", state, Condition.SOURCE_USER_ACTION),
        )
    }

    private fun drivingRuleId(): String {
        val rule = AutomaticZenRule.Builder("Cockpit – Guida", condition)
            .setType(AutomaticZenRule.TYPE_DRIVING)
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            .setZenPolicy(
                ZenPolicy.Builder()
                    // Known people get through, unknown numbers and call centers do not.
                    .allowCalls(ZenPolicy.PEOPLE_TYPE_CONTACTS)
                    .allowRepeatCallers(true)
                    // WhatsApp/Telegram: worth pulling over for.
                    .allowMessages(ZenPolicy.PEOPLE_TYPE_ANYONE)
                    .allowConversations(ZenPolicy.CONVERSATION_SENDERS_ANYONE)
                    // Without this, Do Not Disturb would mute the radio itself.
                    .allowMedia(true)
                    .allowAlarms(true)
                    .build(),
            )
            .setConfigurationActivity(ComponentName(context, MainActivity::class.java))
            .build()
        val existing = notifications.automaticZenRules.entries.firstOrNull { it.value.conditionId == condition }?.key
        return if (existing == null) {
            notifications.addAutomaticZenRule(rule)
        } else {
            notifications.updateAutomaticZenRule(existing, rule)
            existing
        }
    }

    // A media key reaches whichever session is current: the radio or Spotify.
    private fun pauseWhateverIsPlaying() {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
    }
}

fun openDndAccessSettings(context: Context) =
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

fun AudioDeviceInfo.isUsbAudio() = type == AudioDeviceInfo.TYPE_USB_HEADSET || type == AudioDeviceInfo.TYPE_USB_DEVICE
