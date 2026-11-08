package app.postround.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * One piece of mail in the bag.
 * kind: letter · card · bill · mag · parcel · paper · telegram · chess · riddle · pickup · out (a parcel to send, carried to the post office).
 * [to]: a house id, "post" for the post office, or the hidden house of a riddle.
 * [v]: which filler / telegram / riddle memory / paper line it is.
 */
class Item(
    val id: Long,
    val kind: String,
    val to: String,
    val arc: String = "",
    val st: Int = -1,
    val v: Int = 0,
    /** Telegrams: deliver before this wall-clock time (ms). */
    val due: Long = 0L,
    /** Queued items: the day they reach the bag. */
    val arrive: Long = 0L,
) {
    val weight: Int get() = when (kind) { "parcel", "out" -> 3; "pickup" -> 0; else -> 1 }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("k", kind).put("to", to).put("a", arc).put("s", st).put("v", v).put("due", due).put("ar", arrive)

    fun copy(id: Long = this.id, arrive: Long = this.arrive) = Item(id, kind, to, arc, st, v, due, arrive)

    /** The same piece of mail for another door. */
    fun readdressed(to: String) = Item(id, kind, to, arc, st, v, due, arrive)

    companion object {
        fun of(o: JSONObject) = Item(o.getLong("id"), o.getString("k"), o.optString("to"), o.optString("a"), o.optInt("s", -1), o.optInt("v"), o.optLong("due"), o.optLong("ar"))
    }
}

/** A letter to the postman in the «До востребования» window: the end of a story, or the old postwoman's. */
class Mine(val day: Long, val arc: String, var read: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("d", day).put("a", arc).put("r", read)
    companion object { fun of(o: JSONObject) = Mine(o.getLong("d"), o.getString("a"), o.optBoolean("r")) }
}

/**
 * A handing-over at one door ([house] is a house id, or "post" for the post office), at wall-clock time [at].
 * Kept twice: in [GameState.handed] when it was made with the phone in a pocket (a card to read later),
 * and in [GameState.delivered], the journal of the last days.
 */
class Handed(val day: Long, val house: String, val items: List<Item>, var seen: Boolean = false, val at: Long = 0L) {
    /** A telegram handed after its deadline. */
    fun late(it: Item) = it.kind == "telegram" && it.due > 0 && at > it.due
    fun toJson(): JSONObject = JSONObject().put("d", day).put("h", house).put("i", JSONArray(items.map { it.toJson() })).put("s", seen).put("at", at)
    companion object {
        fun of(o: JSONObject) = Handed(o.getLong("d"), o.getString("h"), o.getJSONArray("i").let { a -> List(a.length()) { Item.of(a.getJSONObject(it)) } }, o.optBoolean("s"), o.optLong("at"))
    }
}

/** A day of the round, for the morning summary and the bag size. */
class DayLog(val day: Long, var handed: Int = 0, var km: Double = 0.0, var streets: Int = 0, var steps: Long = -1) {
    /** How far was walked: by the GPS on the round, or by the steps a health app counted, whichever says more. */
    val walked: Double get() = maxOf(km, if (steps > 0) steps * 0.00072 else 0.0)
    fun toJson(): JSONObject = JSONObject().put("d", day).put("h", handed).put("k", km).put("s", streets).put("st", steps)
    companion object { fun of(o: JSONObject) = DayLog(o.getLong("d"), o.optInt("h"), o.optDouble("k"), o.optInt("s"), o.optLong("st", -1)) }
}

class GameState {
    var home: Geo? = null
    var box: DoubleArray? = null
    /** The day the bag belongs to (epoch day); -1 before the first morning. */
    var day: Long = -1
    var started: Long = -1
    var nextId: Long = 1
    val bag = ArrayList<Item>()
    val queue = ArrayList<Item>()
    /** Story → how many of its steps are done; a story missing here hasn't started. */
    val arcs = LinkedHashMap<String, Int>()
    val arcDone = HashSet<String>()
    var nextArcDay: Long = 0
    /** Cast id → the house they live in. */
    val cast = LinkedHashMap<String, String>()
    /** Houses that write to each other (a red thread on the map). */
    val threads = ArrayList<Pair<String, String>>()
    /** Street → the day of its first handing-over (its stamp). */
    val streets = LinkedHashMap<String, Long>()
    /** Houses the postman can't get near (a factory, a fenced yard): no mail goes there (#4). */
    val closed = LinkedHashSet<String>()
    /** Houses ever handed something, for the map's little marks. */
    val visited = HashSet<String>()
    val mine = ArrayList<Mine>()
    val handed = ArrayList<Handed>()
    /** Everything handed over in the last [JOURNAL_DAYS] days, in order: the day's history and the summary after a round. */
    val delivered = ArrayList<Handed>()
    val gifts = ArrayList<String>()
    val log = ArrayList<DayLog>()
    /** The houses that take the evening paper. */
    val subs = ArrayList<String>()
    var paperN = 0
    var chessToday = 0
    var done = 0
    var tgOk = 0
    var tgAll = 0
    var tgDay: Long = -1
    var km = 0.0
    var kmToday = 0.0
    var riddles = 0
    var riddleTries = 0
    var riddleNext: Long = 1
    val riddleUsed = ArrayList<String>()
    var belt = false
    /** Steps from Health Connect since this day; -1 when not connected. */
    var healthFrom: Long = -1
    /** The phone's step counter at its last reading (it counts from the last reboot); -1 before the first. */
    var counter: Long = -1
    /** Decks without repeats: what has already come round in this pass through the fillers, telegrams and paper lines. */
    val fillerUsed = HashSet<Int>()
    val tgUsed = HashSet<Int>()
    val paperUsed = HashSet<Int>()

    /** Writes a handing-over into the journal, dropping the days that have gone out of it. */
    fun journal(h: Handed) {
        delivered += h
        delivered.removeAll { it.day <= h.day - JOURNAL_DAYS }
        while (delivered.size > 400) delivered.removeAt(0)
    }

    fun today(): DayLog = log.lastOrNull()?.takeIf { it.day == day } ?: DayLog(day).also { log += it; while (log.size > 40) log.removeAt(0) }

    fun toJson(): JSONObject {
        val o = JSONObject()
        home?.let { o.put("home", JSONArray().put(it.lat).put(it.lon)) }
        box?.let { b -> o.put("box", JSONArray(b.toList())) }
        o.put("day", day).put("started", started).put("nextId", nextId)
        o.put("bag", JSONArray(bag.map { it.toJson() })).put("queue", JSONArray(queue.map { it.toJson() }))
        o.put("arcs", JSONObject(arcs as Map<*, *>)).put("arcDone", JSONArray(arcDone.toList())).put("nextArcDay", nextArcDay)
        o.put("cast", JSONObject(cast as Map<*, *>))
        o.put("threads", JSONArray(threads.map { JSONArray().put(it.first).put(it.second) }))
        o.put("streets", JSONObject(streets as Map<*, *>)).put("visited", JSONArray(visited.toList())).put("closed", JSONArray(closed.toList()))
        o.put("mine", JSONArray(mine.map { it.toJson() })).put("handed", JSONArray(handed.map { it.toJson() }))
        o.put("delivered", JSONArray(delivered.map { it.toJson() }))
        o.put("gifts", JSONArray(gifts)).put("log", JSONArray(log.map { it.toJson() })).put("subs", JSONArray(subs))
        o.put("paperN", paperN).put("chessToday", chessToday).put("done", done).put("tgOk", tgOk).put("tgAll", tgAll).put("tgDay", tgDay)
        o.put("km", km).put("kmToday", kmToday).put("riddles", riddles).put("riddleTries", riddleTries).put("riddleNext", riddleNext)
        o.put("riddleUsed", JSONArray(riddleUsed)).put("belt", belt).put("healthFrom", healthFrom).put("counter", counter)
        o.put("fillerUsed", JSONArray(fillerUsed.sorted())).put("tgUsed", JSONArray(tgUsed.sorted())).put("paperUsed", JSONArray(paperUsed.sorted()))
        return o
    }

    companion object {
        const val JOURNAL_DAYS = 7
        fun fromJson(o: JSONObject): GameState = GameState().apply {
            o.optJSONArray("home")?.let { home = Geo(it.getDouble(0), it.getDouble(1)) }
            o.optJSONArray("box")?.let { a -> box = DoubleArray(a.length()) { a.getDouble(it) } }
            day = o.optLong("day", -1); started = o.optLong("started", -1); nextId = o.optLong("nextId", 1)
            o.optJSONArray("bag")?.let { a -> for (i in 0 until a.length()) bag += Item.of(a.getJSONObject(i)) }
            o.optJSONArray("queue")?.let { a -> for (i in 0 until a.length()) queue += Item.of(a.getJSONObject(i)) }
            o.optJSONObject("arcs")?.let { a -> a.keys().forEach { arcs[it] = a.getInt(it) } }
            o.optJSONArray("arcDone")?.let { a -> for (i in 0 until a.length()) arcDone += a.getString(i) }
            nextArcDay = o.optLong("nextArcDay")
            o.optJSONObject("cast")?.let { a -> a.keys().forEach { cast[it] = a.getString(it) } }
            o.optJSONArray("threads")?.let { a -> for (i in 0 until a.length()) a.getJSONArray(i).let { p -> threads += p.getString(0) to p.getString(1) } }
            o.optJSONObject("streets")?.let { a -> a.keys().forEach { streets[it] = a.getLong(it) } }
            o.optJSONArray("visited")?.let { a -> for (i in 0 until a.length()) visited += a.getString(i) }
            o.optJSONArray("closed")?.let { a -> for (i in 0 until a.length()) closed += a.getString(i) }
            o.optJSONArray("mine")?.let { a -> for (i in 0 until a.length()) mine += Mine.of(a.getJSONObject(i)) }
            o.optJSONArray("handed")?.let { a -> for (i in 0 until a.length()) handed += Handed.of(a.getJSONObject(i)) }
            o.optJSONArray("delivered")?.let { a -> for (i in 0 until a.length()) delivered += Handed.of(a.getJSONObject(i)) }
            o.optJSONArray("gifts")?.let { a -> for (i in 0 until a.length()) gifts += a.getString(i) }
            o.optJSONArray("log")?.let { a -> for (i in 0 until a.length()) log += DayLog.of(a.getJSONObject(i)) }
            o.optJSONArray("subs")?.let { a -> for (i in 0 until a.length()) subs += a.getString(i) }
            paperN = o.optInt("paperN"); chessToday = o.optInt("chessToday"); done = o.optInt("done"); tgOk = o.optInt("tgOk"); tgAll = o.optInt("tgAll"); tgDay = o.optLong("tgDay", -1)
            km = o.optDouble("km"); kmToday = o.optDouble("kmToday"); riddles = o.optInt("riddles"); riddleTries = o.optInt("riddleTries"); riddleNext = o.optLong("riddleNext", 1)
            o.optJSONArray("riddleUsed")?.let { a -> for (i in 0 until a.length()) riddleUsed += a.getString(i) }
            belt = o.optBoolean("belt"); healthFrom = o.optLong("healthFrom", -1); counter = o.optLong("counter", -1)
            for ((k, set) in listOf("fillerUsed" to fillerUsed, "tgUsed" to tgUsed, "paperUsed" to paperUsed)) o.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) set += a.getInt(i) }
        }
    }
}
