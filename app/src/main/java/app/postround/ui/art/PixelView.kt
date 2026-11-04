package app.postround.ui.art

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Global switches for the art. */
object Art {
    /** Screenshots and renders want a still frame: no loop, a fixed moment. */
    @Volatile var still = false

    /** Art pixels advance about this often. */
    const val FRAME_MS = 110L
}

/**
 * A picture painted into a [Buf]: [base] once (cached while [key] stays the same), then [anim] on
 * top for every frame with a growing tick.
 */
abstract class Scene(val w: Int, val h: Int) {
    abstract val key: Any
    abstract fun base(b: Buf)
    open fun anim(b: Buf, t: Int) = Unit

    /** A single finished frame, for widgets and renders. */
    fun frame(t: Int = 7): Buf {
        val b = Buf(w, h)
        base(b)
        anim(b, t)
        return b
    }
}

fun Buf.toBitmap(): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(d, 0, w, 0, 0, w, h) }

/** Shows a scene scaled up with hard pixels, animating while on screen. Width fills, height follows. */
@Composable
fun PixelView(scene: Scene, modifier: Modifier = Modifier) {
    val base = remember(scene.key, scene.w, scene.h) { Buf(scene.w, scene.h).also { scene.base(it) } }
    val frame = remember(scene.w, scene.h) { Buf(scene.w, scene.h) }
    val bitmap = remember(scene.w, scene.h) { Bitmap.createBitmap(scene.w, scene.h, Bitmap.Config.ARGB_8888) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var tick by remember { mutableIntStateOf(if (Art.still) 7 else 0) }
    if (!Art.still) {
        LaunchedEffect(scene.key) {
            var last = 0L
            while (true) {
                withFrameMillis { ms ->
                    if (ms - last >= Art.FRAME_MS) { last = ms; tick++ }
                }
            }
        }
    }
    Canvas(modifier.aspectRatio(scene.w.toFloat() / scene.h)) {
        val t = tick
        frame.copyFrom(base)
        scene.anim(frame, t)
        bitmap.setPixels(frame.d, 0, frame.w, 0, 0, frame.w, frame.h)
        val scale = size.width / scene.w
        drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(scene.w, scene.h),
            dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), (scene.h * scale).roundToInt()), filterQuality = FilterQuality.None)
    }
}
