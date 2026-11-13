package app.postround

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.postround.data.DemoSeed
import app.postround.ui.AlbumScreen
import app.postround.ui.AskSheet
import app.postround.ui.BagScreen
import app.postround.ui.DoorSheet
import app.postround.ui.LetterSheet
import app.postround.ui.MapCache
import app.postround.ui.MapScreen
import app.postround.ui.MorningSheet
import app.postround.ui.Nav
import app.postround.ui.Onboarding
import app.postround.ui.Root
import app.postround.ui.WindowScreen
import app.postround.ui.art.Art
import app.postround.ui.components.pixelBox
import app.postround.ui.theme.C
import app.postround.ui.theme.PostTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The main screens over the demo round on Petrogradka, drawn to PNGs (run with `-Prender`), in both languages. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreensBase {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    abstract val lang: String
    private val out by lazy { File(System.getProperty("render.out") ?: "build/art", "screens-$lang").apply { mkdirs() } }
    private val nav = Nav({}, {}, {})

    private val c: AppContainer by lazy {
        val app = ApplicationProvider.getApplicationContext<PostApp>()
        val c = app.container
        if (c.state == null) DemoSeed.seed(c, javaClass.classLoader!!.getResource("petrogradka.osm.json")!!.readText())
        val t = c.terrain!!
        if (!MapCache.fits(t, c.now().month)) MapCache.load(app, t, c.now().month)
        c.morning = null
        c
    }

    @Before fun still() { Art.still = true }

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { PostTheme { Box(Modifier.fillMaxSize().background(C.paper)) { content() } } }
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            val image = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(image))
            File(out, "$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** A sheet as it shows over the dimmed app. */
    @Composable private fun Sheet(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color(0xFF3A3028)), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().padding(10.dp).pixelBox(C.light).verticalScroll(rememberScrollState()).padding(14.dp)) { content() }
        }
    }

    @Test fun welcome() = shot("0-welcome") { Onboarding() }

    @Test fun map() { c.selected.value = c.state!!.bag.firstOrNull { it.kind != "riddle" && it.to != "post" }?.to; shot("1-map") { Root() } }

    @Test fun bag() = c.let { shot("2-bag") { BagScreen(nav) } }

    @Test fun album() = c.let { shot("3-stamps") { AlbumScreen(nav) } }

    @Test fun window() = c.let { shot("4-window") { WindowScreen(nav) } }

    @Test fun door() {
        val s = c.state!!
        val id = s.bag.firstOrNull { it.arc.isNotEmpty() && it.to != "post" }?.to ?: s.bag.first { it.kind != "riddle" && it.to != "post" }.to
        val res = c.deliver(id)!!
        shot("5-door") { Sheet { DoorSheet(res) {} } }
    }

    @Test fun morning() { c.morning = app.postround.domain.Morning(c.state!!.log.lastOrNull(), 1, false); shot("6-morning") { Sheet { MorningSheet(nav) {} } } }

    @Test fun riddle() {
        val s = c.state!!
        val it = s.bag.firstOrNull { it.kind == "riddle" } ?: return
        val a = c.ask(it.to)!!
        shot("7-riddle") { Sheet { AskSheet(a) {} } }
    }

    @Test fun letter() { val m = c.state!!.mine.firstOrNull() ?: return; shot("8-letter") { Sheet { LetterSheet(m) {} } } }

    @Test fun mapOnly() = c.let { shot("9-map-plain") { MapScreen(nav) } }
}

@Config(sdk = [34], qualifiers = "en-w411dp-h891dp-xxhdpi")
class RenderArtScreensEn : ScreensBase() { override val lang = "en" }

@Config(sdk = [34], qualifiers = "ru-w411dp-h891dp-xxhdpi")
class RenderArtScreensRu : ScreensBase() { override val lang = "ru" }
