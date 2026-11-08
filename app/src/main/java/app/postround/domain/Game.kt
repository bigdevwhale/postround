package app.postround.domain

import app.postround.data.Content
import app.postround.data.Step
import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Who lives in a house, as the door scene and the bag show them. */
class Resident(val name: Tx, val dat: Tx, val role: Tx, val look: Map<String, String>, val cast: String? = null)

/** What one piece of mail turned into at the door. */
class Shown(val item: Item, val from: Tx, val text: Tx, val say: Tx, val gift: Tx, val late: Boolean = false)

/** The handing-over at a door: what was given, what came of it. */
class Result(val house: House, val shown: List<Shown>, val notes: List<Tx>, val stamp: String?, val rankUp: Tx?)

/** Asking at a house about a letter with no address. */
class Asked(val found: Boolean, val house: House, val memory: Tx, val say: Tx, val hint: Tx, val stamp: String?, val last: Boolean)

/** The morning: what yesterday was and what the bag holds today. */
class Morning(val yesterday: DayLog?, val letters: Int, val fresh: Boolean)

/**
 * The rules of the round. Pure: the state is passed in, the map comes as a [Round].
 * Real time drives it: a new bag each morning, stories advance a day per letter (or at once, at the door).
 */
class Game(val c: Content) {

    /** Cast id → the story they first appear in (by order). */
    private val intro: Map<String, String> = HashMap<String, String>().also { m -> for (a in c.arcs) for (id in a.cast) m.putIfAbsent(id, a.id) }

    fun cap(s: GameState) = CAP + if (s.belt) 4 else 0
    fun load(s: GameState) = s.bag.sumOf { it.weight }

    /** The round grows with the letters handed: from 300 m around home to most of the district. */
    fun radius(s: GameState) = min(MAX_RADIUS, 300.0 + s.done * 5.0)

    fun rank(s: GameState): Int = RANKS.indexOfLast { s.done >= it.first }.coerceAtLeast(0)
    fun rankName(s: GameState) = RANKS[rank(s)].second

    fun newGame(home: Geo, now: Now): GameState = GameState().also { it.home = home; it.started = now.day; it.nextArcDay = now.day }

    // ------------------------------------------------------------------ residents

    fun resident(s: GameState, h: House): Resident {
        s.cast.entries.firstOrNull { it.value == h.id }?.key?.let { id ->
            c.cast[id]?.let { m -> return Resident(m.name, Tx(m.datRu.ifBlank { m.name.ru }, m.name.en), m.role, m.look, id) }
        }
        val hs = Round.strHash(h.id).toInt()
        fun r(k: Int) = Terrain.hash(hs, k * 7 + 1)
        if (h.kind == "shop") {
            val pool = c.names.filter { !it.old }.ifEmpty { c.names }
            val n = pool[(r(1) * pool.size).toInt()]
            return Resident(Tx(n.ru, n.en), Tx(n.datRu, n.en), Tx("за прилавком", "behind the counter"), look(hs, n.male, false) + ("apron" to "1"))
        }
        if (h.kind == "inst") return Resident(Tx("Вахтёр", "The caretaker"), Tx("вахтёру", "the caretaker"), Tx("на вахте", "at the front desk"), mapOf("hair" to "grey", "cloth" to "navy", "cap" to "1", "moustache" to "1"))
        val n = c.names[(r(2) * c.names.size).toInt()]
        return Resident(Tx(n.ru, n.en), Tx(n.datRu, n.en), Tx("жилец", "resident"), look(hs, n.male, n.old))
    }

    private fun look(hs: Int, male: Boolean, old: Boolean): Map<String, String> {
        fun r(k: Int) = Terrain.hash(hs, k * 13 + 5)
        val m = HashMap<String, String>()
        m["hair"] = if (old) listOf("grey", "grey", "dark")[(r(1) * 3).toInt()] else listOf("wood", "dark", "ginger", "honey")[(r(1) * 4).toInt()]
        m["cloth"] = listOf("cream", "green", "jam", "roofB", "navy", "apple", "lilac", "oak", "honey", "enamel", "autumn", "red")[(r(2) * 12).toInt()]
        if (old && r(3) < .45) m["glasses"] = "1"
        if (male) { if (r(4) < .3) m["beard"] = "1" else if (old && r(4) < .6) m["moustache"] = "1"; if (r(5) < .15) m["cap"] = "1" }
        else { if (old && r(4) < .35) m["kerchief"] = listOf("red", "enamel", "lilac")[(r(5) * 3).toInt()] else if (r(4) < .6) m["long"] = "1" else m["bun"] = "1"; if (r(6) < .25) m["earrings"] = "1" }
        return m
    }

    // ------------------------------------------------------------------ the morning

    private fun homeXY(s: GameState, r: Round): Pair<Float, Float> {
        val h = s.home ?: return r.d.w / 2f to r.d.h / 2f
        return r.d.x(h.lon) to r.d.y(h.lat)
    }

    /** Houses of the round today: within the radius around home, and not marked unreachable. */
    fun onRound(s: GameState, r: Round): List<House> {
        val (hx, hy) = homeXY(s, r)
        val rad = radius(s) / r.d.mpp
        val open = r.houses.filter { it.id !in s.closed }
        val list = open.filter { hypot((it.doorX - hx).toDouble(), (it.doorY - hy).toDouble()) <= rad }
        // a sparse map: take the nearest forty, wherever they are
        return if (list.size >= 25) list else open.sortedBy { hypot((it.doorX - hx).toDouble(), (it.doorY - hy).toDouble()) }.take(40)
    }

    /** Mail can go to this door: the post office, or a house on the map not marked unreachable. */
    fun reachableDoor(s: GameState, r: Round, id: String) = id == "post" || (r.byId[id] != null && id !in s.closed)

    /** The postman can't get near [id]: no more mail for it, and what was going there goes elsewhere (#4). */
    fun close(s: GameState, r: Round, id: String) { s.closed += id; rehome(s, r) }

    fun reopen(s: GameState, id: String) { s.closed -= id }

    /**
     * Everyone and everything whose house is gone from the map (after a move, #5) or marked unreachable (#4)
     * gets a new one on the round: the people of the stories, the paper's subscribers, the mail in the bag and
     * on its way. Stories, stamps and the journal stay as they were.
     */
    fun rehome(s: GameState, r: Round) {
        val lost = s.cast.filterValues { !reachableDoor(s, r, it) }.keys.toList()
        lost.forEach { s.cast.remove(it) }
        if (lost.isNotEmpty()) castFor(s, r, lost)
        val round = onRound(s, r)
        if (round.isEmpty()) return
        val rnd = Random(s.nextId * 31 + s.day)
        s.subs.removeAll { !reachableDoor(s, r, it) }
        s.cast["arist"]?.let { if (it !in s.subs) s.subs.add(0, it) }
        while (s.subs.size < 3) {
            val free = round.filter { it.kind == "home" && it.id !in s.subs && it.id !in s.cast.values }
            if (free.isEmpty()) break
            s.subs += free[rnd.nextInt(free.size)].id
        }
        s.threads.removeAll { !reachableDoor(s, r, it.first) || !reachableDoor(s, r, it.second) }
        fun fix(list: MutableList<Item>) {
            for (i in list.indices) {
                val it = list[i]
                if (reachableDoor(s, r, it.to)) continue
                val to = when {
                    it.arc.isNotEmpty() -> c.arc[it.arc]?.steps?.getOrNull(it.st)?.to?.let { k -> if (k == "post") "post" else s.cast[k] }
                    it.kind == "riddle" -> riddleTarget(s, r)?.id
                    it.kind == "paper" -> s.subs.getOrNull(rnd.nextInt(s.subs.size.coerceAtLeast(1)))
                    else -> round[rnd.nextInt(round.size)].id
                } ?: continue
                list[i] = it.readdressed(to)
            }
            list.removeAll { !reachableDoor(s, r, it.to) }
        }
        fix(s.bag)
        fix(s.queue)
    }

    /** Brings the bag up to today. Null when it's still the same day. */
    fun morning(s: GameState, r: Round, now: Now): Morning? {
        if (s.day == now.day) return null
        val first = s.day < 0
        val yesterday = s.log.lastOrNull { it.day == s.day }
        s.day = now.day; s.kmToday = 0.0; s.chessToday = 0
        s.today()
        val rnd = Random(now.day * 7919 + s.nextId)
        // yesterday's papers go to the recycling, an old telegram is taken over by the day shift
        s.bag.removeAll { it.kind == "paper" || it.kind == "telegram" && it.arc.isEmpty() }
        // what was promised for today
        val due = s.queue.filter { it.arrive <= now.day }
        s.queue.removeAll(due.toSet())
        due.forEach { s.bag += it.copy(id = s.nextId++) }
        val round = onRound(s, r)
        if (s.subs.isEmpty()) {
            castFor(s, r, listOf("arist"))
            s.cast["arist"]?.let { s.subs += it }
            round.filter { it.kind == "home" && it.id !in s.subs && it.id !in s.cast.values }.shuffled(rnd).take(2).forEach { s.subs += it.id }
        }
        // a new story every couple of days, at most three at once
        // a new story every five days or so (the first three sooner), at most three at once; seasonal ones wait for their months
        val running = s.arcs.keys.count { it !in s.arcDone }
        if (running < 3 && now.day >= s.nextArcDay) {
            // someone met in another story waits until the story they first appeared in is over: sequels come after their first part
            val fresh = c.arcs.filter { a -> a.id !in s.arcs && a.steps.isNotEmpty() && a.cast.all { id -> intro[id].let { it == null || it == a.id || it in s.arcDone } } }
            // a story of this season goes first, before its window closes; otherwise the next one in order
            (fresh.firstOrNull { it.months.isNotEmpty() && now.month in it.months } ?: fresh.firstOrNull { it.months.isEmpty() })?.let { a ->
                castFor(s, r, a.cast)
                s.arcs[a.id] = 0
                s.bag += stepItem(s, a.id, 0, now.day)
                s.nextArcDay = now.day + when { first -> 1; s.arcs.size <= 3 -> 2; else -> 5 + (Terrain.hash(s.arcs.size, 41) * 3).toInt() }
            }
        }
        // the letter with no address: from the second day, one at a time
        if (now.day >= s.started + 1 && now.day >= s.riddleNext && s.bag.none { it.kind == "riddle" } && c.riddles.isNotEmpty()) riddleTarget(s, r)?.let { h ->
            s.bag += Item(s.nextId++, "riddle", h.id, v = s.riddles % c.riddles.size)
        }
        for (h in s.subs) if (r.byId[h] != null) {
            val v = if (s.cast["arist"] == h && s.paperN < c.arist.size) s.paperN else ANY_PAPER + deck(s.paperUsed, c.anyPaper.indices.filter { c.anyPaper[it].dow == 0 || c.anyPaper[it].dow == now.date.dayOfWeek.value }, rnd)
            s.bag += Item(s.nextId++, "paper", h, v = v)
        }
        // the everyday mail: more after days of long walks, fewer after quiet ones
        val walked = s.log.filter { it.day < now.day }.takeLast(7).map { it.walked }.sorted().let { if (it.isEmpty()) 1.5 else it[it.size / 2] }
        val want = (2 + walked * 1.5 + rank(s) / 2.0).roundToInt().coerceIn(2, 8)
        val per = HashMap<String, Int>(); s.bag.forEach { per[it.to] = (per[it.to] ?: 0) + 1 }
        var n = 0; var guard = 0
        val fresh = round.filter { it.street !in s.streets }
        while (n < want && guard++ < 300 && round.isNotEmpty()) {
            val h = if (fresh.isNotEmpty() && rnd.nextDouble() < .35) fresh[rnd.nextInt(fresh.size)] else round[rnd.nextInt(round.size)]
            if ((per[h.id] ?: 0) >= 2) continue
            val fits = c.fillers.indices.filter { i -> val f = c.fillers[i]; (f.where == "any" || f.where == (if (h.kind == "shop") "shop" else "home")) && (f.months.isEmpty() || now.month in f.months) }
            if (fits.isEmpty()) break
            // the season's own mail first, when there is some left in this pass
            val seasonal = fits.filter { c.fillers[it].months.isNotEmpty() && it !in s.fillerUsed }
            val i = deck(s.fillerUsed, if (seasonal.isNotEmpty() && rnd.nextDouble() < .45) seasonal else fits, rnd)
            val f = c.fillers[i]
            val it = Item(s.nextId++, f.kind, h.id, v = i)
            if (load(s) + it.weight > cap(s)) { if (f.kind == "parcel") continue else break }
            s.bag += it; s.fillerUsed += i; per[h.id] = (per[h.id] ?: 0) + 1; n++
        }
        // a year on the round: Galina writes
        if (s.started >= 0 && now.day - s.started >= 365 && s.mine.none { it.arc == "year" }) s.mine += Mine(now.day, "year")
        val newMine = s.mine.count { !it.read && it.day <= now.day }
        return Morning(yesterday, newMine, first)
    }

    private fun stepItem(s: GameState, arc: String, st: Int, day: Long, arrive: Long = day): Item {
        val step = c.arc.getValue(arc).steps[st]
        val to = if (step.to == "post") "post" else s.cast[step.to] ?: ""
        return Item(s.nextId++, step.kind, to, arc, st, arrive = arrive)
    }

    /** Settles the cast of a story into houses of the round by their rules. */
    fun castFor(s: GameState, r: Round, ids: List<String>) {
        val round = onRound(s, r)
        val (hx, hy) = homeXY(s, r)
        for (id in ids) {
            if (id in s.cast) continue
            val m = c.cast[id] ?: continue
            val taken = s.cast.values.toSet() + s.subs
            val homes = round.filter { it.id !in taken && it.kind == "home" }.ifEmpty { r.houses.filter { it.id !in taken && it.id !in s.closed } }
            if (homes.isEmpty()) continue
            fun far(a: House, b: House) = hypot((a.doorX - b.doorX).toDouble(), (a.doorY - b.doorY).toDouble()) * r.d.mpp
            fun jitter(h: House) = Terrain.hash(Round.strHash(h.id + id).toInt(), 3)
            val pick = m.pick
            val other = pick.substringAfter(':', "").let { s.cast[it] }?.let { r.byId[it] }
            val chosen = when {
                pick == "water" -> homes.maxByOrNull { (if (r.t.nearWater(it.doorX, it.doorY, 15)) 10.0 else 0.0) + jitter(it) - hypot((it.doorX - hx).toDouble(), (it.doorY - hy).toDouble()) * .002 }
                pick == "park" -> homes.maxByOrNull { greenAround(r, it) / 30.0 + jitter(it) }
                pick == "shop" -> round.filter { it.id !in taken && it.kind == "shop" }.maxByOrNull { jitter(it) } ?: homes.maxByOrNull { jitter(it) }
                pick == "inst" -> round.filter { it.id !in taken && it.kind == "inst" }.maxByOrNull { jitter(it) } ?: homes.maxByOrNull { jitter(it) }
                pick.startsWith("far:") && other != null -> homes.maxByOrNull { far(it, other) + jitter(it) * 40 }
                pick.startsWith("near:") && other != null -> homes.minByOrNull { val dd = far(it, other); (if (dd in 90.0..260.0) 0.0 else abs(dd - 170)) + jitter(it) * 30 }
                else -> homes.maxByOrNull { jitter(it) }
            } ?: continue
            s.cast[id] = chosen.id
        }
    }

    private fun greenAround(r: Round, h: House): Int {
        var n = 0
        for (j in -10..10 step 2) for (i in -10..10 step 2) { val k = r.t.at(h.doorX + i, h.doorY + j); if (k == K.LAWN || k == K.WOOD) n++ }
        return n
    }

    // ------------------------------------------------------------------ at the door

    /** Is the postman close enough to this house (a GPS fix at [x], [y] in map pixels)? */
    fun inReach(r: Round, h: House, x: Float, y: Float) = r.metresTo(h, x, y) <= REACH

    fun step(it: Item): Step? = if (it.arc.isEmpty()) null else c.arc[it.arc]?.steps?.getOrNull(it.st)

    /** What a piece of mail says and how it's taken, without changing anything. */
    fun show(s: GameState, it: Item, late: Boolean = false): Shown {
        step(it)?.let { st ->
            return when {
                it.kind == "out" -> Shown(it, c.who(st.from), st.text, Tx("Принято. Поедет сегодня же.", "Taken in. It goes out today."), Tx.EMPTY)
                else -> Shown(it, c.who(st.from), st.text, st.say, st.gift)
            }
        }
        return when (it.kind) {
            "paper" -> {
                val line = if (it.v >= ANY_PAPER) c.anyPaper.getOrNull(it.v - ANY_PAPER)?.text else c.arist.getOrNull(it.v)?.text
                Shown(it, Tx("«Вечерняя почта»", "the evening paper"), Tx.EMPTY, line ?: Tx("Спасибо.", "Thank you."), Tx.EMPTY)
            }
            "telegram" -> { val tg = c.telegrams[it.v % c.telegrams.size]; Shown(it, Tx("Телеграф", "The telegraph office"), tg.text, if (late) tg.late else tg.say, Tx.EMPTY, late) }
            "riddle" -> Shown(it, Tx("без обратного адреса", "no return address"), c.riddles[it.v % c.riddles.size].memory, c.riddles[it.v % c.riddles.size].say, Tx.EMPTY)
            else -> c.fillers.getOrNull(it.v)?.let { f -> Shown(it, f.from, if (f.kind == "card") f.text else Tx.EMPTY, f.say, f.gift) }
                ?: Shown(it, Tx("—", "—"), Tx.EMPTY, Tx("Спасибо.", "Thank you."), Tx.EMPTY)
        }
    }

    /** Hands over everything for one house. Null if there was nothing for it. */
    fun deliver(s: GameState, r: Round, now: Now, houseId: String): Result? {
        val h = r.byId[houseId] ?: return null
        val items = s.bag.filter { it.to == houseId && it.kind != "riddle" }
        if (items.isEmpty()) return null
        s.bag.removeAll(items.toSet())
        val rankBefore = rank(s)
        val shown = ArrayList<Shown>(); val notes = ArrayList<Tx>()
        for (it in items) {
            val late = it.kind == "telegram" && it.due > 0 && now.ms > it.due
            if (it.kind == "telegram" && it.arc.isEmpty()) { s.tgAll++; if (!late) s.tgOk++ }
            if (it.kind == "paper" && it.v < ANY_PAPER) s.paperN++
            val sh = show(s, it, late)
            shown += sh
            if (!sh.gift.blank) s.gifts += sh.gift.ru + "|" + sh.gift.en
            step(it)?.let { st -> notes += advance(s, r, now, it, st) }
            if (it.kind == "pickup") s.done += 0 else s.done++
        }
        s.visited += houseId
        s.journal(Handed(now.day, houseId, items, true, now.ms))
        s.today().handed += items.count { it.kind != "pickup" }
        var stamp: String? = null
        if (h.street !in s.streets) { s.streets[h.street] = now.day; s.today().streets++; stamp = h.street }
        val up = rank(s).takeIf { it > rankBefore }?.let { RANKS[it].second }
        return Result(h, shown, notes, stamp, up)
    }

    /** A story moves on after a step is handed over. Returns a note for the postman. */
    private fun advance(s: GameState, r: Round, now: Now, it: Item, st: Step): List<Tx> {
        val a = c.arc[it.arc] ?: return emptyList()
        if (it.kind == "pickup") {
            val dest = st.extra.split('|').let { p -> Tx(p[0], p.getOrElse(1) { p[0] }) }
            s.bag += Item(s.nextId++, "out", "post", it.arc, it.st)
            return listOf(Tx("В сумке посылка на отправку (${dest.ru}). Отнесите её на почту.", "There's a parcel to send in the bag (${dest.en}). Take it to the post office."))
        }
        if (st.from in s.cast && st.to in s.cast) {
            val p = s.cast.getValue(st.from) to s.cast.getValue(st.to)
            if (s.threads.none { (x, y) -> (x == p.first && y == p.second) || (x == p.second && y == p.first) }) s.threads += p
        }
        val nx = it.st + 1
        s.arcs[it.arc] = nx
        if (nx >= a.steps.size) {
            s.arcDone += it.arc
            if (a.end != null) s.mine += Mine(now.day + 1, it.arc)
            return listOf(Tx("Эта переписка закончилась. Завтра загляните в окошко «До востребования».", "This correspondence has come to an end. Look in the poste restante window tomorrow."))
        }
        val next = a.steps[nx]
        castFor(s, r, listOf(next.from, next.to).filter { c.cast.containsKey(it) })
        val toName = { id: String -> c.cast[id]?.let { m -> Tx(m.datRu.ifBlank { m.name.ru }, m.name.en) } }
        return if (st.delay == 0 && !(it.arc == "chess" && s.chessToday >= 6)) {
            if (it.arc == "chess") s.chessToday++
            s.bag += stepItem(s, it.arc, nx, now.day)
            val who = toName(next.to)
            val addr = s.cast[next.to]?.let { r.byId[it] }
            listOf(if (who != null && addr != null) Tx("Ответ — сразу в сумку: ${who.ru}, ${addr.addr(true)}.", "The reply goes straight into the bag: for ${who.en}, ${addr.addr(false)}.")
                   else Tx("Ответ — сразу в сумку.", "The reply goes straight into the bag."))
        } else {
            val d = max(1, st.delay) + if (Terrain.hash(it.st * 31 + it.arc.length, (now.day % 1000).toInt()) < .45) 1 else 0
            s.queue += stepItem(s, it.arc, nx, now.day, now.day + d)
            listOf(if (d == 1) Tx("Ответ будет в завтрашней сумке.", "The reply will be in tomorrow's bag.") else Tx("Ответ придёт через $d дн.", "The reply will come in $d days."))
        }
    }

    /** Parcels to send, taken in at the post office. */
    fun post(s: GameState, r: Round, now: Now): List<Shown> {
        val out = s.bag.filter { it.to == "post" }
        if (out.isEmpty()) return emptyList()
        s.bag.removeAll(out.toSet())
        s.journal(Handed(now.day, "post", out, true, now.ms))
        return out.map { it ->
            s.done++; s.today().handed++
            val sh = show(s, it)
            val a = c.arc[it.arc]
            if (a != null) {
                val nx = it.st + 1
                s.arcs[it.arc] = nx
                if (nx >= a.steps.size) { s.arcDone += it.arc; if (a.end != null) s.mine += Mine(now.day + 1, it.arc) }
                else s.queue += stepItem(s, it.arc, nx, now.day, now.day + max(1, a.steps[it.st].delay))
            }
            sh
        }
    }

    // ------------------------------------------------------------------ letters with no address

    private fun riddleTarget(s: GameState, r: Round): House? {
        val round = onRound(s, r).filter { it.kind == "home" && it.id !in s.cast.values && it.id !in s.subs && it.id !in s.riddleUsed }
        val sig = round.associateWith { r.signs(it) }
        fun key(sg: Signs) = "${COLOUR_IDX[sg.colour]}|${sg.storeys}|${sg.near.sorted()}"
        val count = sig.values.groupingBy { key(it) }.eachCount()
        val good = round.filter { val sg = sig.getValue(it); sg.near.isNotEmpty() && count[key(sg)] == 1 && sg.storeys in 2..9 }
        val pool = good.ifEmpty { round.filter { count[key(sig.getValue(it))] == 1 } }
        if (pool.isEmpty()) return null
        val h = pool[(Terrain.hash(s.riddles + 11, (s.day % 997).toInt()) * pool.size).toInt()]
        s.riddleUsed += h.id
        return h
    }

    /** The address as the old postwoman wrote it: colour, storeys and what is next to the house. */
    fun clue(r: Round, it: Item): Tx {
        val h = r.byId[it.to] ?: return Tx("Жильцам дома, которого я уже не найду", "To the people of a house I can no longer find")
        val sg = r.signs(h)
        val col = COLOURS[COLOUR_IDX[sg.colour]]
        val st = sg.storeys.coerceIn(1, 12)
        val nr = sg.near.mapNotNull { NEAR[it] }
        val ru = "Жильцам ${STOREYS_RU.getOrElse(st) { "$st-этажного" }} ${col.ru} дома" + if (nr.isEmpty()) "" else ", " + nr.joinToString(", ") { it.ru }
        val en = "To the people of the ${col.en} house, ${st} storeys high" + if (nr.isEmpty()) "" else ", " + nr.joinToString(", ") { it.en }
        return Tx(ru, en)
    }

    /** Asks at a house about the letter with no address. */
    fun ask(s: GameState, r: Round, now: Now, houseId: String): Asked? {
        val it = s.bag.firstOrNull { it.kind == "riddle" } ?: return null
        val h = r.byId[houseId] ?: return null
        val target = r.byId[it.to]
        if (houseId == it.to || target == null) {
            s.bag.remove(it)
            s.riddles++; s.riddleTries = 0; s.done++; s.today().handed++
            s.riddleNext = now.day + 3
            s.visited += houseId
            s.journal(Handed(now.day, houseId, listOf(it), true, now.ms))
            val last = s.riddles == 3
            if (last) { s.mine += Mine(now.day + 1, "somova"); s.belt = true }
            var stamp: String? = null
            if (h.street !in s.streets) { s.streets[h.street] = now.day; s.today().streets++; stamp = h.street }
            val rd = c.riddles[it.v % c.riddles.size]
            return Asked(true, h, rd.memory, rd.say, Tx.EMPTY, stamp, last)
        }
        s.riddleTries++
        val dx = (target.doorX - h.doorX).toDouble(); val dy = (target.doorY - h.doorY).toDouble()
        val m = hypot(dx, dy) * r.d.mpp
        val ang = (Math.toDegrees(atan2(-dy, dx)) + 360) % 360
        val dir = DIRS[((ang + 22.5) / 45).toInt() % 8]
        val dist = when { m < 120 -> Tx("совсем рядом", "very close"); m < 400 -> Tx("через пару кварталов", "a couple of blocks away"); else -> Tx("далековато", "a fair way off") }
        val hint = when {
            m < 40 -> Tx("«Нет, у нас таких не было. Может, соседний дом?»", "\"No, nobody like that here. The next house, maybe?\"")
            s.riddleTries >= 4 -> Tx("«Нет, не у нас. Говорят, такой дом — ${dir.ru}, ${dist.ru}. Кажется, это ${Round.short(target.street, true)}».",
                "\"Not here. They say a house like that is ${dir.en}, ${dist.en}. On ${target.street}, I think.\"")
            else -> Tx("«Нет, у нас таких не было». Машут рукой: такой дом — ${dir.ru}, ${dist.ru}.", "\"No, nobody like that here.\" They wave: a house like that is ${dir.en}, ${dist.en}.")
        }
        return Asked(false, h, Tx.EMPTY, Tx.EMPTY, hint, null, false)
    }

    // ------------------------------------------------------------------ on the walk

    /** Walking adds up; once in a day a telegram comes, for a house a short walk away. */
    fun walked(s: GameState, r: Round, now: Now, metres: Double, x: Float, y: Float): Item? {
        s.km += metres / 1000; s.kmToday += metres / 1000; s.today().km = s.kmToday
        if (s.tgDay == now.day || now.hour !in 8..20 || c.telegrams.isEmpty() || s.day != now.day) return null
        val at = .25 + Terrain.hash(now.day.toInt(), 17) * 1.2
        if (s.kmToday < at) return null
        val pool = r.houses.filter { it.id !in s.closed && r.metresDoor(it, x, y) in 150.0..520.0 }
        if (pool.isEmpty()) return null
        val h = pool[(Terrain.hash(now.day.toInt(), 23) * pool.size).toInt()]
        val m = r.metresDoor(h, x, y)
        val mins = ((m / 1.2 + 480) * 1.5 / 60).roundToInt()
        val due = ((now.ms + mins * 60_000L) / 300_000L + 1) * 300_000L
        s.tgDay = now.day
        val v = deck(s.tgUsed, c.telegrams.indices.toList(), java.util.Random(now.day))
        s.tgUsed += v
        val it = Item(s.nextId++, "telegram", h.id, v = v, due = due)
        s.bag += it
        return it
    }

    // ------------------------------------------------------------------ the window

    fun mineText(m: Mine): Triple<Tx, Tx, Tx> {
        if (m.arc == "somova") return Triple(c.sp("somova_from"), c.sp("somova"), c.sp("somova_gift"))
        if (m.arc == "year") return Triple(c.sp("year_from"), c.sp("year"), c.sp("year_gift"))
        val e = c.arc[m.arc]?.end ?: return Triple(Tx.EMPTY, Tx.EMPTY, Tx.EMPTY)
        return Triple(c.who(e.from), e.text, e.gift)
    }

    /** Chess: the moves made so far, as coordinates. */
    fun chessMoves(s: GameState): List<String> {
        val a = c.arcs.firstOrNull { it.steps.any { st -> st.kind == "chess" } } ?: return emptyList()
        return a.steps.take(s.arcs[a.id] ?: 0).filter { it.kind == "chess" }.map { it.extra }
    }
    fun chessArc(): String? = c.arcs.firstOrNull { it.steps.any { st -> st.kind == "chess" } }?.id

    /** Draws from a deck without repeats: what's left of [pool] in this pass, or a fresh pass when it's all used. */
    private fun deck(used: MutableSet<Int>, pool: List<Int>, rnd: java.util.Random): Int {
        if (pool.isEmpty()) return 0
        var left = pool.filter { it !in used }
        if (left.isEmpty()) { used.removeAll(pool.toSet()); left = pool }
        return left[rnd.nextInt(left.size)].also { used += it }
    }

    companion object {
        /** Paper items: below this an index into the crossword man's saga, above it one of the ordinary subscribers' lines. */
        const val ANY_PAPER = 1000
        const val REACH = 30.0
        const val CAP = 14
        const val MAX_RADIUS = 720.0
        val RANKS = listOf(
            0 to Tx("Почтальон 3-го класса", "Postman, third class"),
            20 to Tx("Почтальон 2-го класса", "Postman, second class"),
            60 to Tx("Почтальон 1-го класса", "Postman, first class"),
            150 to Tx("Старший почтальон", "Senior postman"),
            320 to Tx("Почтальон, которого знают в лицо", "The postman everyone knows"),
            640 to Tx("Почтальон с именем", "A postman with a name"),
        )
        /** The palette of facades (the map's G.FAC order) → colour names. */
        val COLOUR_IDX = intArrayOf(0, 1, 2, 3, 4, 0, 1, 5, 4)
        val COLOURS = listOf(Tx("жёлтого", "yellow"), Tx("розового", "pink"), Tx("зелёного", "green"), Tx("голубого", "blue"), Tx("белого", "white"), Tx("сиреневого", "lilac"))
        val STOREYS_RU = listOf("", "одноэтажного", "двухэтажного", "трёхэтажного", "четырёхэтажного", "пятиэтажного", "шестиэтажного", "семиэтажного", "восьмиэтажного", "девятиэтажного",
            "десятиэтажного", "одиннадцатиэтажного", "двенадцатиэтажного")
        val NEAR = mapOf(
            "pharmacy" to Tx("у аптеки", "by the chemist's"), "cafe" to Tx("где кофейня", "with a café"), "bakery" to Tx("у булочной", "by the bakery"),
            "school" to Tx("у школы", "by the school"), "library" to Tx("возле библиотеки", "near the library"), "greengrocer" to Tx("у овощной лавки", "by the greengrocer's"),
            "station" to Tx("у станции", "by the station"), "florist" to Tx("где цветочный", "with a flower shop"), "kindergarten" to Tx("у детского сада", "by the nursery"),
            "water" to Tx("окнами на воду", "looking onto the water"), "tram" to Tx("трамвай под окнами", "with the tram under its windows"), "park" to Tx("у сквера", "by the little park"),
        )
        val DIRS = listOf(Tx("восточнее", "to the east"), Tx("северо-восточнее", "to the north-east"), Tx("севернее", "to the north"), Tx("северо-западнее", "to the north-west"),
            Tx("западнее", "to the west"), Tx("юго-западнее", "to the south-west"), Tx("южнее", "to the south"), Tx("юго-восточнее", "to the south-east"))
    }
}
