package dev.ftassi.cockpit.spotify

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

// No screen of its own: the user stays in Spotify.
class ShareToCockpitActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::sharedPlaylistUri)
        if (uri != null) SpotifyRecents(this).pin(uri)
        Toast.makeText(this, if (uri != null) "Playlist pinnata in Cockpit" else "Non è una playlist di Spotify", Toast.LENGTH_LONG).show()
        finish()
    }
}
