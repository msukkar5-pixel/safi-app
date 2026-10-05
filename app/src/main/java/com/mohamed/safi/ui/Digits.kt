package com.mohamed.safi.ui

import kotlin.text.toDoubleOrNull as ktToDouble
import kotlin.text.toFloatOrNull as ktToFloat
import kotlin.text.toIntOrNull as ktToInt
import kotlin.text.toLongOrNull as ktToLong

/** Arabic-Indic / Persian digits and separators → ASCII, so "١٢٫٥" parses like "12.5". */
fun normalizeDigits(s: String): String {
    val b = StringBuilder(s.length)
    for (c in s) {
        when (c) {
            in '٠'..'٩' -> b.append('0' + (c - '٠'))
            in '۰'..'۹' -> b.append('0' + (c - '۰'))
            '٫' -> b.append('.')
            '٬', '،' -> {}
            else -> b.append(c)
        }
    }
    return b.toString()
}

// These shadow the stdlib versions inside the ui package (and files that star-import it),
// so every number typed on an Arabic keyboard is understood.
fun String.toDoubleOrNull(): Double? = normalizeDigits(this).trim().ktToDouble()
fun String.toFloatOrNull(): Float? = normalizeDigits(this).trim().ktToFloat()
fun String.toIntOrNull(): Int? = normalizeDigits(this).trim().ktToInt()
fun String.toLongOrNull(): Long? = normalizeDigits(this).trim().ktToLong()
