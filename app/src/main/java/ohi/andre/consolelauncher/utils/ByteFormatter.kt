package ohi.andre.consolelauncher.utils

import java.text.DecimalFormat

/**
 * An object containing the logic and constants for converting raw byte counts
 * into human-readable strings (KB, MB, GB, etc.).
 */
object ByteFormatter {

    // Using binary multipliers (powers of 1024) - Standard for OS/memory
    private const val KILOBYTE: Long = 1024
    private const val MEGABYTE: Long = KILOBYTE * 1024
    private const val GIGABYTE: Long = MEGABYTE * 1024
    private const val TERABYTE: Long = GIGABYTE * 1024
    private const val PETABYTE: Long = TERABYTE * 1024

    // Used for formatting the output string to two decimal places
    private val DECIMAL_FORMAT = DecimalFormat("#.##")

    fun toHumanReadableSize(bytes: Long): String {
        // Handle negative or zero bytes
        if (bytes <= 0) return "0 Bytes"

        // Determine the correct unit and calculate the result
        return when {
            bytes >= PETABYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / PETABYTE)} PB"
            bytes >= TERABYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / TERABYTE)} TB"
            bytes >= GIGABYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / GIGABYTE)} GB"
            bytes >= MEGABYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / MEGABYTE)} MB"
            bytes >= KILOBYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / KILOBYTE)} KB"

            else ->
                "$bytes Bytes" // Less than 1 KB
        }.toString()
    }
}