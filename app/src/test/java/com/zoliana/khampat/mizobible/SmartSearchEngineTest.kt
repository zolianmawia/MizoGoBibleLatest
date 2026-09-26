package com.zoliana.khampat.mizobible

import com.zoliana.khampat.mizobible.data.BibleVerse
import com.zoliana.khampat.mizobible.ui.search.SmartSearchEngine
import org.junit.Assert.*
import org.junit.Test

class SmartSearchEngineTest {

    @Test
    fun testNormalizeMizo() {
        val raw = "Ṭah hla leh Lalpa Âûna"
        val normalized = SmartSearchEngine.normalizeMizo(raw)
        assertEquals("tah hla leh lalpa auna", normalized)
    }

    @Test
    fun testLevenshtein() {
        assertEquals(0, SmartSearchEngine.levenshtein("khawngaihna", "khawngaihna"))
        assertEquals(1, SmartSearchEngine.levenshtein("khawngaiha", "khawngaihna"))
        assertEquals(1, SmartSearchEngine.levenshtein("chungah", "chunga"))
    }

    @Test
    fun testMizoPronounVariationRanking() {
        // User query: "khawngaihna chu i chungah"
        // Bible verse text: "Lalpa Isua Krista khawngaihna chu in chungah awm rawh se."
        val verse1 = BibleVerse(
            id = 1,
            type = "MzOV",
            book = "2 Korinth",
            chapter = 13,
            verse = "14",
            text = "Lalpa Isua Krista khawngaihna chu in chungah awm rawh se.",
            normalizedText = SmartSearchEngine.normalizeMizo("Lalpa Isua Krista khawngaihna chu in chungah awm rawh se.")
        )
        val verse2 = BibleVerse(
            id = 2,
            type = "MzOV",
            book = "Genesis",
            chapter = 1,
            verse = "1",
            text = "A tirin Pathianin lei leh van a siam a.",
            normalizedText = SmartSearchEngine.normalizeMizo("A tirin Pathianin lei leh van a siam a.")
        )
        val verse3 = BibleVerse(
            id = 3,
            type = "MzOV",
            book = "Sam",
            chapter = 23,
            verse = "6",
            text = "I khawngaihna leh i ngilneihna chauhvin mi zui zel ang.",
            normalizedText = SmartSearchEngine.normalizeMizo("I khawngaihna leh i ngilneihna chauhvin mi zui zel ang.")
        )

        val result = SmartSearchEngine.rankAndRefine(
            candidates = listOf(verse2, verse3, verse1),
            rawQuery = "khawngaihna chu i chungah",
            isFuzzy = false
        )

        // verse 1 should be ranked #1
        assertTrue(result.verses.isNotEmpty())
        assertEquals(1, result.verses[0].id)
        assertEquals("2 Korinth", result.verses[0].book)

        // Suggestion should suggest the correct phrase found in the verse
        assertNotNull(result.suggestedPhrase)
        assertEquals("khawngaihna chu in chungah", result.suggestedPhrase?.lowercase())
    }

    @Test
    fun testTypoInQueryMatchesAndRanks() {
        // Query has typo: "khawngaiha chu in chungah" (missing 'n' in khawngaihna)
        val verse = BibleVerse(
            id = 10,
            type = "MzOV",
            book = "Rom",
            chapter = 1,
            verse = "7",
            text = "Khawngaihna leh thlamuanna in chungah awm rawh se.",
            normalizedText = SmartSearchEngine.normalizeMizo("Khawngaihna leh thlamuanna in chungah awm rawh se.")
        )

        val result = SmartSearchEngine.rankAndRefine(
            candidates = listOf(verse),
            rawQuery = "khawngaiha chu in chungah",
            isFuzzy = false
        )

        assertTrue(result.verses.isNotEmpty())
        assertEquals(10, result.verses[0].id)
    }
}
