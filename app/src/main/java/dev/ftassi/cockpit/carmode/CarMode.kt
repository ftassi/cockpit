package dev.ftassi.cockpit.carmode

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import android.view.KeyEvent
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.ftassi.cockpit.BuildConfig
import dev.ftassi.cockpit.MainActivity
import dev.ftassi.cockpit.config.Block
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json

private const val ACTIVE = "active"
private const val SAVED_VOLUME = "saved_volume"
private const val LAST_STATION = "last_station"

// Driving setup: a dedicated Do Not Disturb mode and a fixed media volume,
// both undone on exit. State lives in SharedPreferences so exit still restores
// the right volume if Android killed the app mid-drive.
class CarMode(private val context: Context) {
    private val prefs = context.getSharedPreferences("car_mode", Context.MODE_PRIVATE)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val condition = "condition://dev.ftassi.cockpit/driving".toUri()

    val isActive: Boolean get() = prefs.getBoolean(ACTIVE, false)

    val hasDndAccess: Boolean get() = notifications.isNotificationPolicyAccessGranted

    var lastStation: Block.Radio?
        get() = prefs.getString(LAST_STATION, null)?.let { Json.decodeFromString(it) }
        set(value) = prefs.edit { putString(LAST_STATION, Json.encodeToString(value)) }

    // Requires hasDndAccess. Entering twice keeps the volume saved the first time.
    fun enter() {
        if (isActive) return
        prefs.edit {
            putBoolean(ACTIVE, true)
            putInt(SAVED_VOLUME, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volumeIndex(BuildConfig.CAR_VOLUME, max), 0)
        setDriving(true)
    }

    fun exit() {
        if (!isActive) return
        // Pause first, so restoring the volume never blasts whatever is on.
        pauseWhateverIsPlaying()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, prefs.getInt(SAVED_VOLUME, 0), 0)
        setDriving(false)
        prefs.edit {
            remove(ACTIVE)
            remove(SAVED_VOLUME)
        }
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

fun volumeIndex(fraction: Float, max: Int): Int = (fraction * max).roundToInt().coerceIn(0, max)
