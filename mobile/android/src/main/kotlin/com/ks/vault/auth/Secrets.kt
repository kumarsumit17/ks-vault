package com.ks.vault.auth

/** The rules for a new secret (specs/plan.md, decision 3). */
object Secrets {
    const val MIN_PASSWORD = 6
    const val MIN_PATTERN_DOTS = 4

    fun passwordLongEnough(password: CharArray) = password.size >= MIN_PASSWORD

    fun patternLongEnough(dots: List<Int>) = dots.size >= MIN_PATTERN_DOTS

    /** A pattern as the secret's characters: dots 0 to 8 (left to right, top to bottom) become '1' to '9'. */
    fun patternChars(dots: List<Int>): CharArray {
        require(dots.all { it in 0..8 } && dots.distinct().size == dots.size) { "Not a pattern" }
        return CharArray(dots.size) { ('1' + dots[it]) }
    }

    /**
     * Adds dot [i] to [dots], with the dot passed over on the way from the last one (in a row, column
     * or diagonal) first, as on Android's own lock screen. The drawn pad and the button pad both use
     * it, so one pattern gives the same dots either way.
     */
    fun addPatternDot(dots: MutableList<Int>, i: Int) {
        if (i in dots) return
        val last = dots.lastOrNull()
        if (last != null) {
            val (r1, c1) = last / 3 to last % 3
            val (r2, c2) = i / 3 to i % 3
            if ((r1 + r2) % 2 == 0 && (c1 + c2) % 2 == 0) {
                val mid = (r1 + r2) / 2 * 3 + (c1 + c2) / 2
                if (mid != last && mid != i && mid !in dots) dots.add(mid)
            }
        }
        dots.add(i)
    }
}
