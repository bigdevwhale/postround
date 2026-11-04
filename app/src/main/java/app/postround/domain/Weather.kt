package app.postround.domain

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Today's and tomorrow's sky at home, as the game needs it. */
data class Forecast(val today: Sky, val tomorrow: Sky, val at: Long)

object Weather {
    private const val AGENT = "Postround/1.0 github.com/bigdevwhale/postround"

    /** MET Norway's forecast for the next two days. Only the rounded coordinates are sent. */
    fun fetch(lat: Double, lon: Double, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): Forecast {
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.2f&lon=%.2f".format(Locale.US, lat, lon)
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 7000; c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", AGENT)
        val body = try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
        return parse(body, Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), zone, now)
    }

    fun parse(json: String, today: LocalDate, zone: ZoneId, now: Long): Forecast {
        val ts = JSONObject(json).getJSONObject("properties").getJSONArray("timeseries")
        class Acc { var min = 99.0; var max = -99.0; var rain = false; var snow = false; var fog = false; var clear = 0; var n = 0 }
        val days = mapOf(today to Acc(), today.plusDays(1) to Acc())
        for (i in 0 until ts.length()) {
            val e = ts.getJSONObject(i)
            val t = Instant.parse(e.getString("time")).atZone(zone)
            val a = days[t.toLocalDate()] ?: continue
            val d = e.getJSONObject("data")
            val temp = d.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")
            a.min = minOf(a.min, temp); a.max = maxOf(a.max, temp)
            val sym = d.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
                ?: d.optJSONObject("next_6_hours")?.optJSONObject("summary")?.optString("symbol_code") ?: ""
            if (t.hour in 6..21) {
                a.n++
                if ("snow" in sym || "sleet" in sym) a.snow = true
                else if ("rain" in sym) a.rain = true
                if (sym.startsWith("fog")) a.fog = true
                if (sym.startsWith("clearsky") || sym.startsWith("fair")) a.clear++
            }
        }
        fun sky(a: Acc?): Sky = if (a == null || a.n == 0 && a.min > 90) Sky() else Sky(
            rain = a.rain, snow = a.snow, frost = a.min <= 0.0, fog = a.fog, sun = a.clear * 2 > a.n, heat = a.max >= 25,
            temp = if (a.min > 90) 10.0 else (a.min + a.max) / 2,
        )
        return Forecast(sky(days[today]), sky(days[today.plusDays(1)]), now)
    }

    /** A plausible sky for a day with no forecast on record: the same for everyone, by the month. */
    fun simulated(date: LocalDate, month: Int): Sky {
        val r = hash(date.toEpochDay().toInt(), 77)
        val r2 = hash(date.toEpochDay().toInt(), 13)
        val base = doubleArrayOf(-7.0, -6.0, -1.0, 6.0, 12.0, 16.0, 19.0, 17.0, 11.0, 5.0, 0.0, -4.0)[month - 1] + (r2 - .5) * 8
        val winter = month in listOf(12, 1, 2)
        return Sky(
            rain = !winter && r < .3, snow = winter && r < .3, frost = base < 1, fog = month in 9..11 && r in .3..0.4,
            sun = r > .65, heat = base > 24, temp = base,
        )
    }

    private fun hash(i: Int, j: Int): Double = Terrain.hash(i, j)
}
