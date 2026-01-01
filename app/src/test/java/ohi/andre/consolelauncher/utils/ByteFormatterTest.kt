package ohi.andre.consolelauncher.utils

import org.junit.Assert.assertEquals
import org.junit.Test


class ByteFormatterTest {

    @Test
    fun toHumanReadableSize() {

        assertEquals("0 Bytes", ByteFormatter.toHumanReadableSize(0))
        assertEquals("500 Bytes", ByteFormatter.toHumanReadableSize(500))
        assertEquals("1 KB", ByteFormatter.toHumanReadableSize(1024))
        assertEquals("1.33 KB", ByteFormatter.toHumanReadableSize(1365))
        assertEquals("1 MB", ByteFormatter.toHumanReadableSize(1024L * 1024))
        assertEquals("1 GB", ByteFormatter.toHumanReadableSize(1024L * 1024 * 1024))

        val oneTerabyte = 1024L * 1024 * 1024 * 1024
        val onePetabyte = oneTerabyte * 1024

        assertEquals("1 TB", ByteFormatter.toHumanReadableSize(oneTerabyte))
        assertEquals("1 PB", ByteFormatter.toHumanReadableSize(onePetabyte))
    }

}