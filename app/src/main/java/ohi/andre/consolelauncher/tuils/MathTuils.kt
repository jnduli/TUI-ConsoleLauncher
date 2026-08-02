package ohi.andre.consolelauncher.tuils

import java.math.BigDecimal
import java.math.RoundingMode

object MathTuils {

    private val calculusPattern = Regex("([+\\-*/^])(\\d+\\.?\\d*)")

    @JvmStatic
    fun textCalculus(inputArg: Double, text: String): Double {
        return calculusPattern.findAll(text).fold(inputArg) { acc, match ->
            val (operator, valueStr) = match.destructured
            val value = valueStr.toDouble()
            val result = when (operator) {
                "+" -> acc + value
                "-" -> acc - value
                "*" -> acc * value
                "/" -> acc / value
                "^" -> Math.pow(acc, value)
                else -> acc
            }
            LogTuils.log("now im", result)
            result
        }
    }

    @JvmStatic
    fun percentage(part: Double, total: Double): Double {
        return round(part * 100 / total, 2)
    }

    @JvmStatic
    fun round(value: Double, placesArg: Int): Double {
        val places = placesArg.coerceAtLeast(0)

        return try {
            BigDecimal(value).setScale(places, RoundingMode.HALF_UP).toDouble()
        } catch (e: Exception) {
            value
        }
    }

    @JvmStatic
    fun scale(from: IntArray, to: IntArray, n: Int): Int {
        return (to[1] - to[0]) * (n - from[0]) / (from[1] - from[0]) + to[0]
    }

    @JvmStatic
    fun eval(str: String): Double {
        // Kotlin local functions can't forward-reference each other the way these three
        // mutually-recursive parse steps need to, so this stays an object with member functions.
        return object {
            var pos = -1
            var ch = 0

            fun nextChar() {
                pos++
                ch = if (pos < str.length) str[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    x = when {
                        eat('+'.code) -> x + parseTerm()
                        eat('-'.code) -> x - parseTerm()
                        else -> return x
                    }
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    x = when {
                        eat('*'.code) -> x * parseFactor()
                        eat('/'.code) -> x / parseFactor()
                        else -> return x
                    }
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = pos

                when {
                    eat('('.code) -> {
                        x = parseExpression()
                        eat(')'.code)
                    }
                    ch in '0'.code..'9'.code || ch == '.'.code -> {
                        while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                        x = str.substring(startPos, pos).toDouble()
                    }
                    ch in 'a'.code..'z'.code -> {
                        while (ch in 'a'.code..'z'.code) nextChar()
                        val func = str.substring(startPos, pos)
                        val arg = parseFactor()
                        x = when (func) {
                            "sqrt" -> Math.sqrt(arg)
                            "sin" -> Math.sin(Math.toRadians(arg))
                            "cos" -> Math.cos(Math.toRadians(arg))
                            "tan" -> Math.tan(Math.toRadians(arg))
                            else -> throw RuntimeException("Unknown function: $func")
                        }
                    }
                    else -> throw RuntimeException("Unexpected: " + ch.toChar())
                }

                if (eat('^'.code)) x = Math.pow(x, parseFactor())

                return x
            }
        }.parse()
    }
}
