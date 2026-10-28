package dev.ftassi.cockpit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.ftassi.cockpit.config.loadConfig
import dev.ftassi.cockpit.ui.CockpitTheme
import dev.ftassi.cockpit.ui.DashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = loadConfig(this)
        setContent {
            CockpitTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DashScreen(config.blocks, onBlockClick = {})
                }
            }
        }
    }
}
