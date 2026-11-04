package app.postround

import app.postround.ui.art.Buf
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/** A tiny PNG writer, since unit tests have no java.awt: the art buffer scaled up with hard pixels. */
object Png {
    fun write(file: File, b: Buf, scale: Int) {
        val w = b.w * scale
        val h = b.h * scale
        val raw = ByteArrayOutputStream()
        DeflaterOutputStream(raw).use { z ->
            for (y in 0 until h) {
                z.write(0)
                for (x in 0 until w) {
                    val c = b.d[(y / scale) * b.w + x / scale]
                    z.write(c shr 16 and 0xFF); z.write(c shr 8 and 0xFF); z.write(c and 0xFF); z.write(c ushr 24)
                }
            }
        }
        val out = DataOutputStream(file.outputStream().buffered())
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            out.writeInt(data.size)
            val t = type.toByteArray()
            out.write(t); out.write(data)
            val crc = CRC32(); crc.update(t); crc.update(data)
            out.writeInt(crc.value.toInt())
        }
        val hdr = ByteArrayOutputStream()
        DataOutputStream(hdr).apply { writeInt(w); writeInt(h); writeByte(8); writeByte(6); writeByte(0); writeByte(0); writeByte(0) }
        chunk("IHDR", hdr.toByteArray())
        chunk("IDAT", raw.toByteArray())
        chunk("IEND", ByteArray(0))
        out.close()
    }
}
