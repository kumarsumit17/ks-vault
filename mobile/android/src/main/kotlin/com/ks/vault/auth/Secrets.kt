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
}
