package app.postround.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.postround.R

/** The colours of the post office: parchment, ink, postal blue, the red of sealing wax. */
@Immutable
object C {
    val bg = Color(0xFF2A1A12)
    val paper = Color(0xFFF2E2BB)
    val paper2 = Color(0xFFE1CB9B)
    val light = Color(0xFFFBF3DC)
    val ink = Color(0xFF22160F)
    val muted = Color(0xFF7A5A3E)
    val faint = Color(0xFFA88A68)
    val green = Color(0xFF2F4A80)
    val greenDeep = Color(0xFF1A2A4A)
    val greenLight = Color(0xFF5A78B0)
    val red = Color(0xFFA33A2C)
    val redDeep = Color(0xFF6E1F1F)
    val ochre = Color(0xFFD9973A)
    val ochreDeep = Color(0xFFA86E22)
    val lamp = Color(0xFFF4CC5A)
    val lampDeep = Color(0xFFB87A1A)
    val blue = Color(0xFF26375E)
    val telegram = Color(0xFFF6DC84)
    val night = Color(0xFF151D34)
}

/**
 * Every text in the app: m6x11 by Daniel Linssen with Cyrillic by Natto Games (free with attribution),
 * the font Hearthspell uses. One family; sizes set the hierarchy.
 */
val Pixel = FontFamily(Font(R.font.m6x11, FontWeight.Normal))

object T {
    val Display = TextStyle(fontFamily = Pixel, fontSize = 32.sp, lineHeight = 32.sp, color = C.ink)
    val Title = TextStyle(fontFamily = Pixel, fontSize = 27.sp, lineHeight = 28.sp, color = C.ink)
    val Heading = TextStyle(fontFamily = Pixel, fontSize = 23.sp, lineHeight = 24.sp, color = C.ink)
    val Eyebrow = TextStyle(fontFamily = Pixel, fontSize = 16.sp, lineHeight = 17.sp, letterSpacing = 0.04.em, color = C.muted)
    val Body = TextStyle(fontFamily = Pixel, fontSize = 20.sp, lineHeight = 22.sp, color = C.ink)
    val Letter = TextStyle(fontFamily = Pixel, fontSize = 21.sp, lineHeight = 24.sp, color = C.ink)
    val Sub = TextStyle(fontFamily = Pixel, fontSize = 18.sp, lineHeight = 19.sp, color = C.muted)
    val Small = TextStyle(fontFamily = Pixel, fontSize = 16.sp, lineHeight = 17.sp, color = C.muted)
    val Number = TextStyle(fontFamily = Pixel, fontSize = 28.sp, lineHeight = 26.sp, color = C.ink)
    val Button = TextStyle(fontFamily = Pixel, fontSize = 22.sp, lineHeight = 22.sp)
    val Hand = TextStyle(fontFamily = Pixel, fontSize = 21.sp, lineHeight = 22.sp, color = C.blue)
}

@Composable
fun PostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.green, onPrimary = C.light, background = C.paper, surface = C.paper, onSurface = C.ink,
            onBackground = C.ink, surfaceContainer = C.paper, surfaceContainerHigh = C.paper, surfaceContainerHighest = C.paper2,
            outline = C.ink, error = C.red,
        ),
        typography = MaterialTheme.typography.copy(bodyLarge = T.Body, bodyMedium = T.Body, bodySmall = T.Small, labelLarge = T.Button, titleLarge = T.Title),
        content = content,
    )
}
