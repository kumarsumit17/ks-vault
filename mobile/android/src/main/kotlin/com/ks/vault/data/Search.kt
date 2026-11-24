package com.ks.vault.data

/** Search text as a SQL LIKE pattern: the words anywhere, with %, _ and \ taken literally. */
object Search {
    fun likePattern(query: String): String? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return "%$escaped%"
    }
}
