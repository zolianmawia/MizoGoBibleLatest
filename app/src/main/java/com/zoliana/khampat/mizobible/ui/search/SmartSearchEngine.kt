package com.zoliana.khampat.mizobible.ui.search

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.zoliana.khampat.mizobible.data.BibleVerse
import kotlin.math.max
import kotlin.math.min

object SmartSearchEngine {

    data class SmartSearchResult(
        val verses: List<BibleVerse>,
        val suggestedPhrase: String? = null
    )

    private val STOP_WORDS = setOf(
        "a", "an", "i", "in", "ka", "kan", "ni", "te", "ten", "va", "vin",
        "chu", "le", "leh", "pawh", "chuang", "mahse", "chuan", "ti", "tih",
        "meuh", "ta", "teh", "eng", "hi", "he"
    )

    // Common Mizo pronoun, grammatical, and spelling variation pairs
    private val MIZO_VARIATION_PAIRS = listOf(
        setOf("i", "in"),
        setOf("ka", "kan"),
        setOf("a", "an"),
        setOf("chunga", "chungah"),
        setOf("krista", "khrista"),
        setOf("isua", "isuan"),
        setOf("pathian", "pathianin"),
        setOf("pathian", "pathianah"),
        setOf("va", "vin"),
        setOf("te", "ten"),
        setOf("chu", "chuan"),
        setOf("ti", "tih"),
        setOf("ropui", "ropuina"),
        setOf("thlamuan", "thlamuanna"),
        setOf("chhandam", "chhandamna"),
        setOf("chhandam", "chhandamtu"),
        setOf("beram", "berampu"),
        setOf("beram", "beramte")
    )

    fun normalizeMizo(text: String): String {
        return text.lowercase().trim()
            .replace("â", "a")
            .replace("ê", "e")
            .replace("î", "i")
            .replace("ô", "o")
            .replace("û", "u")
            .replace("ṭ", "t")
            .replace("ṛ", "r")
    }

    private fun isMizoVariation(w1: String, w2: String): Boolean {
        if (w1 == w2) return true
        for (pair in MIZO_VARIATION_PAIRS) {
            if (pair.contains(w1) && pair.contains(w2)) return true
        }
        return false
    }

    fun levenshtein(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val len1 = s1.length
        val len2 = s2.length
        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)

        for (i in 0 until len1) {
            curr[0] = i + 1
            val c1 = s1[i]
            for (j in 0 until len2) {
                val cost = if (c1 == s2[j]) 0 else 1
                curr[j + 1] = min(
                    min(curr[j] + 1, prev[j + 1] + 1),
                    prev[j] + cost
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[len2]
    }

    /**
     * Builds a fast, targeted SQLite query to retrieve candidate verses for scoring.
     */
    fun buildCandidateQuery(
        version: String,
        query: String,
        bookName: String?,
        bookFilterList: List<String>?,
        isFuzzy: Boolean
    ): SupportSQLiteQuery {
        val normalizedQuery = normalizeMizo(query)
        val queryWithoutSpace = normalizedQuery.replace(Regex("[^a-z0-9]"), "")
        val tokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }

        val contentTokens = tokens.filter { it.length >= 3 && it !in STOP_WORDS }.let {
            if (it.isNotEmpty()) it else tokens.filter { t -> t.length >= 2 }.let { t2 ->
                if (t2.isNotEmpty()) t2 else tokens
            }
        }

        val sql = StringBuilder()
        val args = mutableListOf<Any>()

        sql.append("SELECT * FROM mizogobible WHERE ")
        sql.append(
            """
            (LOWER(TRIM(type)) = LOWER(TRIM(?)) 
            OR (LOWER(TRIM(?)) IN ('mzov', 'mgb', 'verse', 'mizogobible', 'mizo bible', 'mizo go bible') 
                AND (LOWER(TRIM(type)) = 'mzov' OR LOWER(TRIM(type)) IN ('pericope', 'percope', 'verse', 'mgb', 'mizogobible'))))
            """.trimIndent()
        )
        args.add(version)
        args.add(version)

        if (!bookName.isNullOrBlank() && bookName != "All Books") {
            sql.append(" AND LOWER(TRIM(book)) = LOWER(TRIM(?))")
            args.add(bookName)
        } else if (!bookFilterList.isNullOrEmpty()) {
            sql.append(" AND book IN (")
            sql.append(bookFilterList.joinToString(",") { "?" })
            sql.append(")")
            args.addAll(bookFilterList)
        }

        val matchClauses = mutableListOf<String>()

        // 1. Exact phrase match
        if (normalizedQuery.isNotEmpty()) {
            matchClauses.add("normalized_text LIKE ?")
            args.add("%$normalizedQuery%")
        }
        if (queryWithoutSpace.isNotEmpty() && queryWithoutSpace != normalizedQuery) {
            matchClauses.add("searchText LIKE ?")
            args.add("%$queryWithoutSpace%")
        }

        // 2. Token combinations
        if (contentTokens.size >= 2) {
            // All content tokens present in verse
            val allTokenClause = contentTokens.joinToString(" AND ") { "normalized_text LIKE ?" }
            matchClauses.add("($allTokenClause)")
            contentTokens.forEach { args.add("%$it%") }

            // Pairs of content tokens if 3+ tokens or fuzzy
            if (contentTokens.size >= 3 || isFuzzy) {
                val pairClauses = mutableListOf<String>()
                for (i in 0 until min(contentTokens.size - 1, 3)) {
                    for (j in (i + 1) until min(contentTokens.size, 4)) {
                        pairClauses.add("(normalized_text LIKE ? AND normalized_text LIKE ?)")
                        args.add("%${contentTokens[i]}%")
                        args.add("%${contentTokens[j]}%")
                    }
                }
                if (pairClauses.isNotEmpty()) {
                    matchClauses.add("(${pairClauses.joinToString(" OR ")})")
                }
            }

            // Also check longest token individually
            val longest = contentTokens.maxByOrNull { it.length }
            if (longest != null && longest.length >= 5) {
                matchClauses.add("normalized_text LIKE ?")
                args.add("%$longest%")
                if (longest.length >= 6) {
                    val stem = longest.substring(0, longest.length - 2)
                    matchClauses.add("normalized_text LIKE ?")
                    args.add("%$stem%")
                }
            }
        } else if (contentTokens.size == 1) {
            val single = contentTokens[0]
            matchClauses.add("normalized_text LIKE ?")
            args.add("%$single%")

            if (single.length >= 4) {
                val stem1 = single.substring(0, single.length - 1)
                matchClauses.add("normalized_text LIKE ?")
                args.add("%$stem1%")
            }
            if (single.length >= 6) {
                val stem2 = single.substring(0, single.length - 2)
                matchClauses.add("normalized_text LIKE ?")
                args.add("%$stem2%")
            }
        }

        if (matchClauses.isNotEmpty()) {
            sql.append(" AND (${matchClauses.joinToString(" OR ")})")
        }

        sql.append(" ORDER BY id ASC LIMIT 500")

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    /**
     * Scores candidate verses and extracts suggested correction phrase if applicable.
     */
    fun rankAndRefine(
        candidates: List<BibleVerse>,
        rawQuery: String,
        isFuzzy: Boolean
    ): SmartSearchResult {
        val query = rawQuery.trim()
        val normalizedQuery = normalizeMizo(query)
        val queryWithoutSpace = normalizedQuery.replace(Regex("[^a-z0-9]"), "")
        val queryTokens = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }

        if (candidates.isEmpty() || queryTokens.isEmpty()) {
            return SmartSearchResult(emptyList(), null)
        }

        data class ScoredVerse(
            val verse: BibleVerse,
            val score: Double,
            val matchedSpanText: String? = null
        )

        val scoredList = mutableListOf<ScoredVerse>()

        for (verse in candidates) {
            val vType = verse.type?.lowercase()?.trim()
            if (vType == "pericope" || vType == "percope") continue

            val rawVerseText = verse.text ?: ""
            val normVerseText = verse.normalizedText ?: normalizeMizo(rawVerseText)
            val verseWords = normVerseText.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
            val rawVerseWords = rawVerseText.split(Regex("\\s+")).filter { it.isNotBlank() }

            var score = 0.0

            // 1. Exact phrase matching bonuses
            if (rawVerseText.contains(query, ignoreCase = true)) {
                score += 50000.0
            } else if (normVerseText.contains(normalizedQuery, ignoreCase = true)) {
                score += 40000.0
            } else if (verse.searchText?.contains(queryWithoutSpace, ignoreCase = true) == true) {
                score += 35000.0
            }

            // 2. Token-level matching
            val matchedIndices = mutableListOf<Int>()
            var totalWordScore = 0.0
            var matchedTokenCount = 0

            for (qToken in queryTokens) {
                var bestWordMatch = 0.0
                var bestIdx = -1

                for ((vIdx, vWord) in verseWords.withIndex()) {
                    val match = when {
                        qToken == vWord -> 100.0
                        isMizoVariation(qToken, vWord) -> 95.0
                        vWord.startsWith(qToken) || qToken.startsWith(vWord) -> {
                            val ratio = min(qToken.length, vWord.length).toDouble() / max(qToken.length, vWord.length)
                            ratio * 88.0
                        }
                        qToken.length >= 4 && vWord.length >= 4 -> {
                            val d = levenshtein(qToken, vWord)
                            if (d == 1) 82.0
                            else if (d == 2 && qToken.length >= 6) 62.0
                            else 0.0
                        }
                        else -> 0.0
                    }

                    if (match > bestWordMatch) {
                        bestWordMatch = match
                        bestIdx = vIdx
                    }
                }

                val weight = when {
                    qToken.length <= 2 -> 1.0
                    qToken.length <= 4 -> 2.0
                    else -> 3.5
                }

                if (bestWordMatch >= 60.0) {
                    matchedTokenCount++
                    if (bestIdx != -1) matchedIndices.add(bestIdx)
                }

                totalWordScore += bestWordMatch * weight
            }

            score += totalWordScore

            // 3. Multi-word structure bonuses
            if (queryTokens.size >= 2) {
                if (matchedTokenCount == queryTokens.size) {
                    score += 15000.0 // All tokens matched!
                } else if (matchedTokenCount >= queryTokens.size - 1 && queryTokens.size >= 3) {
                    score += 8000.0
                }

                // Sequence order check
                var isOrdered = true
                for (i in 0 until matchedIndices.size - 1) {
                    if (matchedIndices[i] >= matchedIndices[i + 1]) {
                        isOrdered = false
                        break
                    }
                }
                if (isOrdered && matchedIndices.size >= 2) {
                    score += 5000.0
                }

                // Span proximity check
                if (matchedIndices.isNotEmpty()) {
                    val span = (matchedIndices.maxOrNull() ?: 0) - (matchedIndices.minOrNull() ?: 0) + 1
                    if (span <= queryTokens.size + 2) {
                        score += 5000.0
                    }
                }
            }

            // Extract matching span for suggestion if high confidence
            var extractedSpan: String? = null
            if (matchedIndices.isNotEmpty() && rawVerseWords.isNotEmpty()) {
                val minI = max(0, matchedIndices.minOrNull() ?: 0)
                val maxI = min(rawVerseWords.size - 1, matchedIndices.maxOrNull() ?: 0)
                if (maxI - minI <= queryTokens.size + 2 && minI <= maxI) {
                    extractedSpan = rawVerseWords.subList(minI, maxI + 1)
                        .joinToString(" ")
                        .trim { it in " .,;:'\"?![]()" }
                }
            }

            // Threshold: must have meaningful score to avoid false positives
            val minThreshold = if (queryTokens.size >= 2) 200.0 else 60.0
            if (score >= minThreshold) {
                scoredList.add(ScoredVerse(verse, score, extractedSpan))
            }
        }

        // Sort descending by score, then ascending by id
        val sorted = scoredList.sortedWith(
            compareByDescending<ScoredVerse> { it.score }
                .thenBy { it.verse.id ?: 0 }
        )

        val finalVerses = sorted.map { it.verse }

        // Find suggested phrase from top result if it differs from the query
        var suggestedPhrase: String? = null
        if (sorted.isNotEmpty()) {
            val top = sorted[0]
            val span = top.matchedSpanText
            if (!span.isNullOrBlank() && top.score >= 12000.0) {
                val normSpan = normalizeMizo(span)
                if (normSpan != normalizedQuery && normSpan.length >= 4) {
                    // Check if they are similar enough to be a genuine correction
                    val wordCountDiff = kotlin.math.abs(
                        span.split("\\s+".toRegex()).size - queryTokens.size
                    )
                    if (wordCountDiff <= 2) {
                        suggestedPhrase = span
                    }
                }
            }
        }

        return SmartSearchResult(finalVerses, suggestedPhrase)
    }
}
