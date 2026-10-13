package com.jake.duolauncher

import com.jake.duolauncher.appearance.MoltenMetal
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Writes the Molten metal scene to PPM files for looking at: only when MOLTEN_DUMP=<dir> is set (a developer aid, not a check). */
class MoltenDumpTest {
    @Test fun dump() {
        val dir = System.getProperty("molten.dump") ?: System.getenv("MOLTEN_DUMP")
        assumeTrue(dir != null)
        File(dir!!).mkdirs()
        for (dark in listOf(false, true)) {
            val w = 460; val h = 1000
            val t0 = System.nanoTime()
            val px = MoltenMetal.render(w, h, dark)
            println("molten ${if (dark) "night" else "day"} ${(System.nanoTime() - t0) / 1_000_000} ms")
            val bytes = ByteArray(w * h * 3)
            for (i in px.indices) { bytes[i * 3] = (px[i] shr 16).toByte(); bytes[i * 3 + 1] = (px[i] shr 8).toByte(); bytes[i * 3 + 2] = px[i].toByte() }
            File(dir, "molten-${if (dark) "night" else "day"}.ppm").outputStream().use { it.write("P6\n$w $h\n255\n".toByteArray()); it.write(bytes) }
        }
    }
}
