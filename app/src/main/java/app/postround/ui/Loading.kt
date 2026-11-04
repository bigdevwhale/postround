package app.postround.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.postround.container
import app.postround.ui.art.Buf
import app.postround.ui.art.G
import app.postround.ui.art.PixelView
import app.postround.ui.art.R
import app.postround.ui.art.Scene
import app.postround.ui.art.hash
import app.postround.ui.art.line
import app.postround.ui.art.rp
import app.postround.ui.theme.C
import app.postround.ui.theme.T

/** A sheet of paper on which a pencil draws a little district: streets first, then houses, trees and water. */
class DrawingScene : Scene(96, 56) {
    override val key get() = "drawing"

    override fun base(b: Buf) {
        for (y in 0 until h) for (x in 0 until w) b.p(x, y, rp(R.paper, .78 + (hash(x, y) - .5) * .06 - if (x < 2 || y < 2 || x > w - 3 || y > h - 3) .2 else 0.0, x, y))
    }

    override fun anim(b: Buf, t: Int) {
        val cycle = t % 140
        // the streets, drawn one after another
        val streets = listOf(intArrayOf(4, 20, 92, 18), intArrayOf(30, 4, 34, 52), intArrayOf(62, 4, 58, 52), intArrayOf(4, 40, 92, 42))
        var px = 4.0; var py = 20.0
        for ((i, s) in streets.withIndex()) {
            val k = ((cycle - i * 12) / 12.0).coerceIn(0.0, 1.0)
            if (k <= 0) continue
            val ex = s[0] + (s[2] - s[0]) * k; val ey = s[1] + (s[3] - s[1]) * k
            b.line(s[0].toDouble(), s[1].toDouble(), ex, ey, R.wood[2])
            b.line(s[0].toDouble(), s[1] + 1.0, ex, ey + 1, R.wood[4])
            if (k < 1) { px = ex; py = ey }
        }
        // houses pop up in the blocks, then trees, then the pond
        val after = cycle - 48
        for (i in 0 until 14) {
            if (after < i * 3) break
            val x = 6 + (hash(i, 3) * 80).toInt(); val y = 4 + (hash(3, i) * 44).toInt()
            if (kotlin.math.abs(y - 19) < 4 || kotlin.math.abs(y - 41) < 4 || kotlin.math.abs(x - 32) < 4 || kotlin.math.abs(x - 60) < 4) continue
            val roof = listOf(G.rust, G.tin, G.roofGl)[i % 3]
            for (j in 0 until 3) for (k in 0 until 5) b.p(x + k, y + j, rp(roof, .7 - j * .15, x + k, y + j))
            for (k in 0 until 5) b.p(x + k, y + 3, G.ochre[3])
            if (i == (after / 3).coerceAtMost(13)) { px = x + 2.0; py = y + 1.0 }
        }
        val trees = cycle - 92
        for (i in 0 until 10) {
            if (trees < i * 2) break
            val x = 70 + (hash(i, 9) * 22).toInt(); val y = 24 + (hash(9, i) * 14).toInt()
            b.p(x, y, R.green[4]); b.p(x + 1, y, R.green[5]); b.p(x, y + 1, R.green[3]); b.p(x + 1, y + 1, R.green[4])
            px = x.toDouble(); py = y.toDouble()
        }
        if (cycle > 114) for (j in 0 until 6) for (i in 0 until 12) if ((i - 5.5) * (i - 5.5) / 36 + (j - 2.5) * (j - 2.5) / 9 < 1) b.p(10 + i, 46 + j, rp(G.water, .5, i, j))
        // the pencil
        val x = px.toInt(); val y = py.toInt()
        for (k in 0 until 8) { b.p(x + 1 + k, y - 1 - k, R.honey[3]); b.p(x + 2 + k, y - 1 - k, R.honey[2]) }
        b.p(x, y, R.INK); b.p(x + 1, y - 1, R.wood[4]); b.p(x + 9, y - 9, R.red[3]); b.p(x + 10, y - 9, R.red[2])
    }
}

/** The drawing and a line about what is being done right now. */
@Composable
fun Loading(modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    val stage by c.stage.collectAsState()
    val ru = ru()
    Column(modifier.padding(16.dp)) {
        PixelView(remember { DrawingScene() })
        Text(when (stage) {
            "download" -> w(ru, "Скачиваю карту из OpenStreetMap…", "Downloading the map from OpenStreetMap…")
            "parse" -> w(ru, "Прокладываю улицы…", "Laying out the streets…")
            "terrain" -> w(ru, "Ставлю дома и сажаю деревья…", "Raising houses, planting trees…")
            "bind" -> w(ru, "Нумерую дома…", "Numbering the houses…")
            else -> w(ru, "Раскрашиваю район…", "Colouring the district…")
        }, style = T.Body.copy(color = C.green), modifier = Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = TextAlign.Center)
        Text(w(ru, "Это может занять минуту-другую. Не закрывайте приложение.", "This can take a minute or two. Keep the app open."),
            style = T.Small, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center)
    }
}
