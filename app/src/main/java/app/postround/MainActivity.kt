package app.postround

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import app.postround.ui.Root
import app.postround.ui.theme.PostTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(t), navigationBarStyle = SystemBarStyle.dark(t))
        setContent { PostTheme { Root() } }
    }

    override fun onPause() {
        container.visible = false
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        container.visible = true
        container.wake()
        container.refreshWeather()
        container.scope.launch { container.readSensor(); container.syncHealth() }
    }
}
