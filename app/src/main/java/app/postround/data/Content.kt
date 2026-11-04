package app.postround.data

import app.postround.domain.Tx
import java.io.InputStream

class Name(val male: Boolean, val old: Boolean, val ru: String, val datRu: String, val en: String)

class CastMember(val id: String, val pick: String, val name: Tx, val datRu: String, val role: Tx, val look: Map<String, String>)

/** One step of a story. [from]/[to] are cast ids, `ext:` senders, or `post`. */
class Step(
    val arc: String, val n: Int, val from: String, val to: String, val kind: String, val extra: String, val next: String,
    val text: Tx, val say: Tx, val gift: Tx,
) {
    /** Days until the following step arrives; 0 = straight into the bag. */
    val delay: Int get() = if (next == "now") 0 else next.toIntOrNull() ?: 1
}

class Arc(val id: String, val order: Int, val title: Tx, val steps: List<Step>, val end: Step?, val months: Set<Int> = emptySet()) {
    /** The cast this story needs in houses. */
    val cast: List<String> get() = steps.flatMap { listOf(it.from, it.to) }.filter { it.isNotBlank() && !it.startsWith("ext:") && it != "post" }.distinct()
}

class Filler(val kind: String, val where: String, val months: Set<Int>, val from: Tx, val text: Tx, val say: Tx, val gift: Tx)
class Telegram(val text: Tx, val say: Tx, val late: Tx)
class Riddle(val memory: Tx, val say: Tx)
class PaperLine(val who: String, val n: Int, val text: Tx, val dow: Int = 0)

/** Everything written for the game, read from the TSV files in assets/content. */
class Content(
    val names: List<Name>,
    val cast: Map<String, CastMember>,
    val arcs: List<Arc>,
    val fillers: List<Filler>,
    val telegrams: List<Telegram>,
    val riddles: List<Riddle>,
    val paper: List<PaperLine>,
    val special: Map<String, Tx>,
) {
    val arc: Map<String, Arc> = arcs.associateBy { it.id }
    val arist: List<PaperLine> = paper.filter { it.who == "arist" }.sortedBy { it.n }
    val anyPaper: List<PaperLine> = paper.filter { it.who != "arist" }
    fun sp(key: String) = special[key] ?: Tx(key, key)

    /** A sender written as `ext:Имя|Name` or a cast id. */
    fun who(id: String): Tx = when {
        id.startsWith("ext:") -> id.removePrefix("ext:").split("|").let { p -> Tx(p[0], p.getOrElse(1) { p[0] }) }
        else -> cast[id]?.name ?: Tx(id, id)
    }

    companion object {
        fun rows(open: (String) -> InputStream?, file: String): List<Map<String, String>> {
            val text = open(file)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return emptyList()
            val lines = text.split('\n').map { it.trimEnd('\r') }.filter { it.isNotBlank() && !it.startsWith("#") }
            if (lines.isEmpty()) return emptyList()
            val head = lines[0].split('\t')
            return lines.drop(1).map { l -> val c = l.split('\t'); head.indices.associate { head[it] to c.getOrElse(it) { "" }.trim() } }
        }

        fun read(open: (String) -> InputStream?): Content {
            fun tx(r: Map<String, String>, k: String) = Tx(r["${k}_ru"].orEmpty(), r["${k}_en"].orEmpty().ifBlank { r["${k}_ru"].orEmpty() })
            val names = rows(open, "names.tsv").map { Name(it["sex"] == "m", it["age"] == "old", it.getValue("ru"), it["dat_ru"].orEmpty(), it["en"].orEmpty()) }
            val cast = rows(open, "cast.tsv").map { r ->
                val look = r["look"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.associate { p -> p.substringBefore('=') to (if ('=' in p) p.substringAfter('=') else "1") }
                CastMember(r.getValue("id"), r["pick"].orEmpty().ifBlank { "any" }, Tx(r["name_ru"].orEmpty(), r["name_en"].orEmpty()), r["dat_ru"].orEmpty(), tx(r, "role"), look)
            }.associateBy { it.id }
            val meta = rows(open, "arcs_meta.tsv").associateBy { it.getValue("arc") }
            val steps = rows(open, "arcs.tsv").map { r ->
                Step(r.getValue("arc"), if (r["n"] == "end") -1 else r["n"]?.toIntOrNull() ?: 0, r["from"].orEmpty(), r["to"].orEmpty(), r["kind"].orEmpty().ifBlank { "letter" },
                    r["extra"].orEmpty(), r["next"].orEmpty(), tx(r, "text"), tx(r, "say"), tx(r, "gift"))
            }
            val arcs = steps.groupBy { it.arc }.map { (id, ss) ->
                val m = meta[id]
                Arc(id, m?.get("order")?.toIntOrNull() ?: 99, Tx(m?.get("title_ru").orEmpty(), m?.get("title_en").orEmpty()), ss.filter { it.n > 0 }.sortedBy { it.n }, ss.firstOrNull { it.n < 0 },
                    m?.get("months").orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet())
            }.sortedBy { it.order }
            val fillers = rows(open, "fillers.tsv").map { r ->
                Filler(r.getValue("kind"), r["where"].orEmpty().ifBlank { "any" }, r["months"].orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
                    tx(r, "from"), tx(r, "text"), tx(r, "say"), tx(r, "gift"))
            }
            val telegrams = rows(open, "telegrams.tsv").map { Telegram(tx(it, "text"), tx(it, "say"), tx(it, "late")) }
            val riddles = rows(open, "riddles.tsv").map { Riddle(tx(it, "memory"), tx(it, "say")) }
            val paper = rows(open, "paper.tsv").map { PaperLine(it["who"].orEmpty(), it["n"]?.toIntOrNull() ?: 0, Tx(it["ru"].orEmpty(), it["en"].orEmpty()), it["dow"]?.toIntOrNull() ?: 0) }
            val special = rows(open, "special.tsv").associate { it.getValue("key") to Tx(it["ru"].orEmpty(), it["en"].orEmpty()) }
            return Content(names, cast, arcs, fillers, telegrams, riddles, paper, special)
        }
    }
}
