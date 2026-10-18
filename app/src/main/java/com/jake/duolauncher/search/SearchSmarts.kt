package com.jake.duolauncher.search

import com.jake.duolauncher.*
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.pow

/** An answer the search box can give without any app match: the [text] to show (and copy), and the [detail] line. */
internal data class SmartAnswer(val text: String, val detail: String)

/**
 * Whether [raw] reads as a phone number worth dialing rather than a sum: at least seven digits and nothing but the usual
 * separators (spaces, dashes, parentheses, dots, a leading + or a trailing #). Shared by the dial action and by
 * [SearchSmarts], which must not report "555-123-4567" as the sum 555 − 123 − 4567. `*` is deliberately not a separator
 * here: the short dial-pad codes that use it are never seven digits, and allowing it would turn "1000*1000" into a "phone number".
 */
internal fun looksLikePhoneNumber(raw: String): Boolean {
    val q = raw.trim()
    if (q.isEmpty() || (q.first() != '+' && !q.first().isDigit())) return false
    val digits = q.count { it.isDigit() }
    if (digits !in 7..15) return false
    return q.all { it.isDigit() || it in "+()-. #" }
}

/** On-device answers for All apps search: arithmetic ("12*(3+4)", "15% of 80") and unit conversion
 * ("5 km in mi", "212 f to c"). Nothing is sent anywhere, and a query that isn't clearly one of these
 * returns null so ordinary app search is untouched ("7-zip" and "2048" are app names, not sums).
 */
internal object SearchSmarts {
    fun answer(query: String, now: java.time.LocalDateTime = java.time.LocalDateTime.now()): SmartAnswer? {
        val q = query.trim()
        if (q.isEmpty() || q.length > 80) return null
        // A phone number is a dial, not arithmetic ("555-123-4567" would otherwise answer "-4135").
        if (looksLikePhoneNumber(q)) return null
        return easterEgg(q) ?: dateOrTime(q, now) ?: percentOf(q) ?: conversion(q) ?: calculation(q)
    }

    /**
     * For readers of a certain desert novel. Whole phrases only (a lone "dune" or "spice" is still an app search), matched loosely, and
     * answered in the launcher's own words: the one quoted line is four words long.
     */
    internal fun easterEgg(q: String): SmartAnswer? {
        val key = q.lowercase().replace(Regex("[^a-z ]"), " ").trim().replace(Regex(" +"), " ")
        return when (key) {
            "the spice must flow", "spice must flow" -> SmartAnswer("The spice must flow.", "Arrakis, 10191 AG")
            "arrakis", "planet arrakis", "go to arrakis" -> SmartAnswer("Dry, hot, and the only source of spice.", "Arrakis: bring water")
            "shai hulud", "sandworm", "sand worm", "sandworms" -> SmartAnswer("Something large is moving under your Home screen.", "Shai-Hulud")
            "walk without rhythm", "how to avoid sandworms" -> SmartAnswer("Then the worm will not notice you.", "Fremen advice")
            "gom jabbar", "the gom jabbar", "hand in the box" -> SmartAnswer("Hand in the box. Hold still.", "A test of what is human")
            "litany against fear", "fear is the mind killer", "i must not fear" -> SmartAnswer("Face it. Let it pass over you.", "Litany against fear")
            "muad dib", "muaddib", "paul atreides", "usul" -> SmartAnswer("The mouse that jumps.", "Muad'Dib")
            "water of life", "bene gesserit", "kwisatz haderach", "fremen" -> SmartAnswer("Not on this phone. Try the Dune range wallpaper.", "Dune")
            else -> null
        }
    }

    /** "date", "today", "time", "now": what the clock says, answered on the device. */
    private fun dateOrTime(q: String, now: java.time.LocalDateTime): SmartAnswer? {
        val fmt = { pattern: String -> java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()).format(now) }
        return when (q.lowercase().removePrefix("what is ").removePrefix("what's ").removePrefix("the ").trim().removeSuffix("?")) {
            "date", "today", "todays date", "today's date" -> SmartAnswer(fmt("EEEE, d MMMM yyyy"), fmt("'Week' w"))
            "time", "now", "current time" -> SmartAnswer(fmt("HH:mm"), fmt("EEEE, d MMMM"))
            else -> null
        }
    }

    // ---- arithmetic ------------------------------------------------------------------------------------------

    private val percentOfPattern = Regex("""^(-?[0-9]*\.?[0-9]+)\s*%\s*of\s*(-?[0-9]*\.?[0-9]+)$""", RegexOption.IGNORE_CASE)

    private fun percentOf(q: String): SmartAnswer? {
        val m = percentOfPattern.find(q) ?: return null
        val p = m.groupValues[1].toDoubleOrNull() ?: return null
        val of = m.groupValues[2].toDoubleOrNull() ?: return null
        return SmartAnswer(format(p / 100.0 * of), "$q")
    }

    private fun calculation(q: String): SmartAnswer? {
        val parser = ExpressionParser(q.replace('×', '*').replace('÷', '/').replace('−', '-'))
        val value = parser.parse() ?: return null
        if (!parser.meaningful || value.isNaN() || value.isInfinite()) return null
        return SmartAnswer(format(value), "$q =")
    }

    /** Ten significant digits, no trailing zeros; scientific notation only at the extremes. */
    internal fun format(value: Double, digits: Int = 10): String {
        if (value == 0.0) return "0"
        val a = abs(value)
        if (a >= 1e15 || a < 1e-9) return String.format("%.${digits - 1}e", value).replace(Regex("""\.?0+e"""), "e")
        return BigDecimal(value).round(MathContext(digits)).stripTrailingZeros().toPlainString()
    }

    /** Recursive descent over + - * / ^, unary signs, parentheses, postfix %, constants and a few functions. */
    private class ExpressionParser(private val s: String) {
        private var i = 0
        var meaningful = false
        private var lastWasPercent = false

        fun parse(): Double? {
            val v = runCatching { expression() }.getOrNull() ?: return null
            skipSpaces()
            return if (i == s.length) v else null
        }

        private fun skipSpaces() { while (i < s.length && s[i] == ' ') i++ }
        private fun peek(): Char? { skipSpaces(); return s.getOrNull(i) }
        private fun eat(c: Char): Boolean { if (peek() == c) { i++; return true }; return false }

        private fun expression(): Double {
            var left = term()
            while (true) {
                val op = peek()
                if (op != '+' && op != '-') return left
                i++
                meaningful = true
                lastWasPercent = false
                var right = term()
                // "50 + 10%" means ten percent of 50, as on a phone calculator.
                if (lastWasPercent) right = left * right
                lastWasPercent = false
                left = if (op == '+') left + right else left - right
            }
        }

        private fun term(): Double {
            var left = power()
            while (true) {
                val op = peek()
                if (op != '*' && op != '/') return left
                i++
                meaningful = true
                val right = power()
                left = if (op == '*') left * right else {
                    if (right == 0.0) throw ArithmeticException("divide by zero")
                    left / right
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            if (eat('^')) { meaningful = true; return base.pow(power()) }
            return base
        }

        private fun unary(): Double {
            if (eat('-')) return -unary()
            if (eat('+')) return unary()
            return postfix()
        }

        private fun postfix(): Double {
            var v = primary()
            if (eat('%')) { meaningful = true; v /= 100.0; lastWasPercent = true } else lastWasPercent = false
            return v
        }

        private fun primary(): Double {
            skipSpaces()
            val c = s.getOrNull(i) ?: throw IllegalArgumentException("end")
            if (c == '(') {
                i++
                val v = expression()
                if (!eat(')')) throw IllegalArgumentException("unclosed")
                meaningful = true
                return v
            }
            if (c.isDigit() || c == '.') {
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                return s.substring(start, i).toDouble()
            }
            if (c == 'π') { i++; return Math.PI }
            if (c.isLetter()) {
                val start = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(start, i).lowercase()
                when (name) { "pi" -> return Math.PI }
                val arg = run {
                    if (!eat('(')) throw IllegalArgumentException("function needs (")
                    val v = expression(); if (!eat(')')) throw IllegalArgumentException("unclosed"); v
                }
                meaningful = true
                return when (name) {
                    "sqrt" -> if (arg < 0) throw ArithmeticException("negative") else kotlin.math.sqrt(arg)
                    "abs" -> abs(arg)
                    "ln" -> if (arg <= 0) throw ArithmeticException("log") else kotlin.math.ln(arg)
                    "log" -> if (arg <= 0) throw ArithmeticException("log") else kotlin.math.log10(arg)
                    "sin" -> kotlin.math.sin(Math.toRadians(arg))
                    "cos" -> kotlin.math.cos(Math.toRadians(arg))
                    "tan" -> kotlin.math.tan(Math.toRadians(arg))
                    else -> throw IllegalArgumentException("unknown function")
                }
            }
            throw IllegalArgumentException("unexpected $c")
        }
    }

    // ---- unit conversion -------------------------------------------------------------------------------------

    private enum class Kind { LENGTH, MASS, VOLUME, SPEED, DATA, TIME, TEMPERATURE }

    private class Unit(val kind: Kind, val label: String, val toBase: Double)

    private val units: Map<String, Unit> = buildMap {
        fun add(kind: Kind, label: String, toBase: Double, vararg names: String) {
            val unit = Unit(kind, label, toBase); names.forEach { put(it, unit) }
        }
        add(Kind.LENGTH, "mm", .001, "mm", "millimeter", "millimeters", "millimetre", "millimetres")
        add(Kind.LENGTH, "cm", .01, "cm", "centimeter", "centimeters", "centimetre", "centimetres")
        add(Kind.LENGTH, "m", 1.0, "m", "meter", "meters", "metre", "metres")
        add(Kind.LENGTH, "km", 1000.0, "km", "kilometer", "kilometers", "kilometre", "kilometres")
        add(Kind.LENGTH, "in", .0254, "in", "inch", "inches", "\"")
        add(Kind.LENGTH, "ft", .3048, "ft", "foot", "feet")
        add(Kind.LENGTH, "yd", .9144, "yd", "yard", "yards")
        add(Kind.LENGTH, "mi", 1609.344, "mi", "mile", "miles")
        add(Kind.MASS, "mg", 1e-6, "mg", "milligram", "milligrams")
        add(Kind.MASS, "g", .001, "g", "gram", "grams")
        add(Kind.MASS, "kg", 1.0, "kg", "kilogram", "kilograms", "kilo", "kilos")
        add(Kind.MASS, "oz", .028349523125, "oz", "ounce", "ounces")
        add(Kind.MASS, "lb", .45359237, "lb", "lbs", "pound", "pounds")
        add(Kind.MASS, "st", 6.35029318, "st", "stone")
        add(Kind.VOLUME, "ml", .001, "ml", "milliliter", "milliliters", "millilitre", "millilitres")
        add(Kind.VOLUME, "L", 1.0, "l", "liter", "liters", "litre", "litres")
        add(Kind.VOLUME, "tsp", .00492892159375, "tsp", "teaspoon", "teaspoons")
        add(Kind.VOLUME, "tbsp", .01478676478125, "tbsp", "tablespoon", "tablespoons")
        add(Kind.VOLUME, "fl oz", .0295735295625, "floz", "fl oz")
        add(Kind.VOLUME, "cup", .2365882365, "cup", "cups")
        add(Kind.VOLUME, "pt", .473176473, "pt", "pint", "pints")
        add(Kind.VOLUME, "qt", .946352946, "qt", "quart", "quarts")
        add(Kind.VOLUME, "gal", 3.785411784, "gal", "gallon", "gallons")
        add(Kind.SPEED, "m/s", 1.0, "m/s", "mps")
        add(Kind.SPEED, "km/h", 1.0 / 3.6, "km/h", "kph", "kmh", "kmph")
        add(Kind.SPEED, "mph", .44704, "mph", "mi/h")
        add(Kind.SPEED, "kn", .514444444, "kn", "knot", "knots")
        add(Kind.DATA, "B", 1.0, "b", "byte", "bytes")
        add(Kind.DATA, "kB", 1e3, "kb", "kilobyte", "kilobytes")
        add(Kind.DATA, "MB", 1e6, "mb", "megabyte", "megabytes")
        add(Kind.DATA, "GB", 1e9, "gb", "gigabyte", "gigabytes")
        add(Kind.DATA, "TB", 1e12, "tb", "terabyte", "terabytes")
        add(Kind.DATA, "KiB", 1024.0, "kib")
        add(Kind.DATA, "MiB", 1048576.0, "mib")
        add(Kind.DATA, "GiB", 1073741824.0, "gib")
        add(Kind.TIME, "s", 1.0, "s", "sec", "secs", "second", "seconds")
        add(Kind.TIME, "min", 60.0, "min", "mins", "minute", "minutes")
        add(Kind.TIME, "h", 3600.0, "h", "hr", "hrs", "hour", "hours")
        add(Kind.TIME, "d", 86400.0, "d", "day", "days")
        add(Kind.TIME, "wk", 604800.0, "wk", "week", "weeks")
        add(Kind.TEMPERATURE, "°C", 0.0, "c", "°c", "celsius")
        add(Kind.TEMPERATURE, "°F", 0.0, "f", "°f", "fahrenheit")
        add(Kind.TEMPERATURE, "K", 0.0, "k", "kelvin")
    }

    private val conversionPattern = Regex(
        """^(-?[0-9]*\.?[0-9]+)\s*([^\s0-9][^\s]*(?:\s+oz)?)\s+(?:in|to|as|into)\s+([^\s]+(?:\s+oz)?)$""", RegexOption.IGNORE_CASE)

    private fun conversion(q: String): SmartAnswer? {
        val m = conversionPattern.find(q) ?: return null
        val amount = m.groupValues[1].toDoubleOrNull() ?: return null
        val from = units[m.groupValues[2].lowercase()] ?: return null
        val to = units[m.groupValues[3].lowercase()] ?: return null
        if (from.kind != to.kind) return null
        val result = if (from.kind == Kind.TEMPERATURE) fromKelvin(to.label, toKelvin(from.label, amount))
        else amount * from.toBase / to.toBase
        if (result.isNaN() || result.isInfinite()) return null
        return SmartAnswer("${format(result, 7)} ${to.label}", "${format(amount, 7)} ${from.label} =")
    }

    private fun toKelvin(label: String, v: Double) = when (label) {
        "°C" -> v + 273.15
        "°F" -> (v - 32.0) * 5.0 / 9.0 + 273.15
        else -> v
    }

    private fun fromKelvin(label: String, k: Double) = when (label) {
        "°C" -> k - 273.15
        "°F" -> (k - 273.15) * 9.0 / 5.0 + 32.0
        else -> k
    }
}
