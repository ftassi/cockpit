package dev.ftassi.cockpit.launch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

private const val SPOTIFY = "com.spotify.music"
private const val WAZE = "com.waze"

fun openWaze(context: Context) = openApp(context, WAZE, "Waze")

// "Home" is the favourite Waze itself keeps; without one, Waze just opens.
fun navigateHome(context: Context) =
    start(context, Intent(Intent.ACTION_VIEW, "waze://?favorite=Home&navigate=yes".toUri()).setPackage(WAZE), "Waze")

fun openSpotify(context: Context, uri: String?) {
    if (uri == null) {
        openApp(context, SPOTIFY, "Spotify")
    } else {
        start(context, Intent(Intent.ACTION_VIEW, uri.toUri()).setPackage(SPOTIFY), "Spotify")
    }
}

fun openDialer(context: Context) = start(context, Intent(Intent.ACTION_DIAL), "Telefono")

// Requires CALL_PHONE: places the call without going through the dialer.
fun call(context: Context, number: String) =
    start(context, Intent(Intent.ACTION_CALL, "tel:$number".toUri()), "Telefono")

// ACTION_VOICE_COMMAND would be the obvious choice, but the Google app ignores
// it unless a Bluetooth headset is connected. VOICE_ASSIST asks once which
// assistant to use ("Sempre" makes it stick). The constant is hidden from the
// public SDK, hence the string.
fun startVoiceAssist(context: Context) =
    start(context, Intent("android.intent.action.VOICE_ASSIST"), "Assistente vocale")

fun openApp(context: Context, packageName: String, appName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent == null) notAvailable(context, appName) else start(context, intent, appName)
}

private fun start(context: Context, intent: Intent, appName: String) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        notAvailable(context, appName)
    }
}

private fun notAvailable(context: Context, appName: String) =
    Toast.makeText(context, "$appName non disponibile", Toast.LENGTH_LONG).show()
