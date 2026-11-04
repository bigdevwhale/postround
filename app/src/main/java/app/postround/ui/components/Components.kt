package app.postround.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.postround.ui.art.Buf
import app.postround.ui.art.toBitmap
import app.postround.ui.theme.C
import app.postround.ui.theme.T
import kotlin.math.roundToInt

/** One "UI pixel": outlines, bevels and notched corners come in multiples of it. */
val PX: Dp = 2.dp

/** A pixel box: a dark outline with notched corners, a fill, a light bevel on top and a darker lip below. */
fun Modifier.pixelBox(fill: Color, outline: Color = C.ink, bevel: Color? = Color.White.copy(alpha = .5f), lip: Color? = Color(0x38502814), pressed: Boolean = false): Modifier = drawBehind {
    val p = PX.toPx()
    val w = size.width
    val h = size.height
    drawRect(outline, Offset(p, 0f), Size(w - 2 * p, h))
    drawRect(outline, Offset(0f, p), Size(w, h - 2 * p))
    drawRect(fill, Offset(2 * p, p), Size(w - 4 * p, h - 2 * p))
    drawRect(fill, Offset(p, 2 * p), Size(w - 2 * p, h - 4 * p))
    if (!pressed) {
        bevel?.let { drawRect(it, Offset(2 * p, p), Size(w - 4 * p, p)) }
        lip?.let { drawRect(it, Offset(2 * p, h - 3 * p), Size(w - 4 * p, 2 * p)) }
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    fill: Color = C.light,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier
            .pixelBox(fill, pressed = pressed && onClick != null)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

enum class Kind { PRIMARY, LAMP, GHOST, RED }

@Composable
fun PixelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: Kind = Kind.PRIMARY, enabled: Boolean = true) {
    val (fill, ink, lip) = when (kind) {
        Kind.PRIMARY -> Triple(C.green, C.light, C.greenDeep)
        Kind.LAMP -> Triple(C.lamp, C.ink, C.lampDeep)
        Kind.GHOST -> Triple(C.light, C.green, Color(0xFFD9C290))
        Kind.RED -> Triple(C.red, C.light, C.redDeep)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val a = if (enabled) 1f else .45f
    Box(
        modifier
            .offset { IntOffset(0, if (pressed) PX.roundToPx() else 0) }
            .pixelBox(fill.copy(alpha = a), C.ink, Color.White.copy(alpha = .35f * a), lip, pressed)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = T.Button.copy(color = ink.copy(alpha = a)), textAlign = TextAlign.Center)
    }
}

/** A segmented progress bar, like the mockups. */
@Composable
fun Segments(fraction: Float, modifier: Modifier = Modifier, n: Int = 10) {
    Row(modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val on = (fraction.coerceIn(0f, 1f) * n).roundToInt()
        repeat(n) { i ->
            Box(
                Modifier.weight(1f).height(10.dp).drawBehind {
                    val p = PX.toPx()
                    drawRect(if (i < on) C.ochre else Color(0xFFD9C290))
                    drawRect(if (i < on) C.ochreDeep else Color(0xFFC2A872), Offset(0f, size.height - p), Size(size.width, p))
                    if (i < on) drawRect(Color(0xFFF2B860), Offset(0f, 0f), Size(size.width, p))
                },
            )
        }
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = C.muted) =
    Text(text.lowercase(), style = T.Eyebrow.copy(color = color), modifier = modifier)

/** A still pixel picture (an item, a stamp, an icon), scaled with hard pixels. */
@Composable
fun BufImage(buf: Buf, modifier: Modifier = Modifier, scale: Dp = 2.dp) {
    val image = remember(buf) { buf.toBitmap().asImageBitmap() }
    Canvas(modifier.size(scale * buf.w, scale * buf.h)) {
        drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(buf.w, buf.h), dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None)
    }
}

/** A title bar with a back arrow drawn in pixels. */
@Composable
fun TopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = false) {
    Row(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(20.dp)) {
                val p = size.width / 10
                val col = if (dark) C.light else C.ink
                // "<": the tip on the left, two arms going right
                for (i in 0 until 5) {
                    drawRect(col, Offset((1 + i) * p, (5 - i) * p - p / 2), Size(p * 2, p))
                    drawRect(col, Offset((1 + i) * p, (4 + i) * p + p / 2), Size(p * 2, p))
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = T.Title.copy(color = if (dark) C.light else C.ink))
    }
}

@Composable
fun Spacer8() = Spacer(Modifier.height(8.dp))

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.pixelBox(C.paper2).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(value, style = T.Number)
        Text(label, style = T.Small)
    }
}

/** A small tag, like «срочно». */
@Composable
fun Tag(text: String, color: Color = C.red) {
    Text(text, style = T.Small.copy(color = C.light), modifier = Modifier.background(color).padding(horizontal = 4.dp, vertical = 1.dp))
}
