package app.postround.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** A point on the map, in degrees. */
data class Geo(val lat: Double, val lon: Double)

/** Distance in metres between two points (equirectangular, plenty for a district). */
fun Geo.metres(o: Geo): Double {
    val k = Math.cos(Math.toRadians((lat + o.lat) / 2))
    val dx = (lon - o.lon) * 111_320.0 * k
    val dy = (lat - o.lat) * 110_574.0
    return Math.sqrt(dx * dx + dy * dy)
}

/** The weather as the game needs it. */
data class Sky(
    val rain: Boolean = false,
    val snow: Boolean = false,
    val frost: Boolean = false,
    val fog: Boolean = false,
    val sun: Boolean = false,
    val heat: Boolean = false,
    /** Mean temperature, °C. */
    val temp: Double = 12.0,
)

/** The moment the game is asked about. */
data class Now(
    val date: LocalDate,
    /** 0–23 local hour. */
    val hour: Int = 12,
    val sky: Sky = Sky(),
    val tomorrow: Sky = Sky(),
    /** Minute of the day, 0–1439. */
    val minute: Int = hour * 60,
    /** Wall-clock milliseconds (for telegram deadlines). */
    val ms: Long = 0L,
) {
    val day: Long get() = date.toEpochDay()
    val month: Int get() = date.monthValue
    val sunday: Boolean get() = date.dayOfWeek == DayOfWeek.SUNDAY
}

/** A text in both languages. */
data class Tx(val ru: String, val en: String) {
    operator fun invoke(ru: Boolean) = if (ru) this.ru else en
    val blank get() = ru.isBlank() && en.isBlank()
    companion object { val EMPTY = Tx("", "") }
}
