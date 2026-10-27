package dev.vimivo.core.util

/**
 * Orders names the way people number them: "Episode 2" before "Episode 10", case-insensitively.
 * Digit runs compare by value (any length, leading zeros ignored, then fewer zeros first); everything
 * else compares by lowercase text. Names that are equal by these rules fall back to plain ordering.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val left = chunks(a)
        val right = chunks(b)
        for (index in 0 until minOf(left.size, right.size)) {
            val result = compareChunk(left[index], right[index])
            if (result != 0) return result
        }
        return left.size.compareTo(right.size).takeIf { it != 0 } ?: a.compareTo(b)
    }

    private fun compareChunk(x: String, y: String): Int {
        val bothNumbers = x.first().isDigit() && y.first().isDigit()
        if (!bothNumbers) return x.lowercase().compareTo(y.lowercase())
        val xDigits = x.trimStart('0')
        val yDigits = y.trimStart('0')
        return when {
            xDigits.length != yDigits.length -> xDigits.length.compareTo(yDigits.length)
            xDigits != yDigits -> xDigits.compareTo(yDigits)
            else -> x.length.compareTo(y.length)
        }
    }

    private fun chunks(value: String): List<String> {
        if (value.isEmpty()) return listOf(" ")
        val result = mutableListOf<String>()
        val current = StringBuilder()
        value.forEach { char ->
            if (current.isNotEmpty() && current.last().isDigit() != char.isDigit()) {
                result += current.toString()
                current.clear()
            }
            current.append(char)
        }
        result += current.toString()
        return result
    }
}
