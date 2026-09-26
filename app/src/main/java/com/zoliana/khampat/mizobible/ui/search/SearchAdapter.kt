package com.zoliana.khampat.mizobible.ui.search

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zoliana.khampat.mizobible.data.BibleVerse
import com.zoliana.khampat.mizobible.databinding.ItemSearchBinding
import com.zoliana.khampat.mizobible.utils.ThemeHelper
import java.util.regex.Pattern

class SearchAdapter(private val onVerseClick: (BibleVerse) -> Unit) :
    ListAdapter<BibleVerse, SearchAdapter.SearchViewHolder>(SearchDiffCallback()) {

    private var currentQuery: String = ""

    fun submitListWithQuery(list: List<BibleVerse>?, query: String) {
        currentQuery = query
        submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding = ItemSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(getItem(position), currentQuery, onVerseClick)
    }

    class SearchViewHolder(private val binding: ItemSearchBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(verse: BibleVerse, query: String, onClick: (BibleVerse) -> Unit) {
            binding.textReference.text = "${verse.book} ${verse.chapter}:${verse.verse}"
            
            val effectiveFont = ThemeHelper.getEffectiveFontColor(binding.root.context)
            if (effectiveFont != null) {
                binding.textContent.setTextColor(effectiveFont)
            }

            val content = verse.text ?: ""
            if (query.isNotEmpty() && query.length >= 2) {
                binding.textContent.text = highlightMatches(content, query)
            } else {
                binding.textContent.text = content
            }
            
            binding.root.setOnClickListener { onClick(verse) }
        }

        private fun highlightMatches(content: String, query: String): CharSequence {
            val spannable = SpannableString(content)
            val highlighted = BooleanArray(content.length)
            var foundAny = false

            // 1. Full phrase match first
            try {
                val fullRegex = buildMizoRegex(query)
                val pattern = Pattern.compile(fullRegex, Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(content)
                while (matcher.find()) {
                    for (i in matcher.start() until matcher.end()) {
                        if (i in highlighted.indices) highlighted[i] = true
                    }
                    foundAny = true
                }
            } catch (_: Exception) {}

            // 2. Token-level matches (especially when variations like i/in, a/an occur)
            val tokens = query.trim().split(Regex("\\s+")).filter { it.length >= 2 }
            if (tokens.isNotEmpty()) {
                for (token in tokens) {
                    try {
                        val tokenRegex = buildMizoTokenRegex(token)
                        val pattern = Pattern.compile(tokenRegex, Pattern.CASE_INSENSITIVE)
                        val matcher = pattern.matcher(content)
                        while (matcher.find()) {
                            for (i in matcher.start() until matcher.end()) {
                                if (i in highlighted.indices) highlighted[i] = true
                            }
                            foundAny = true
                        }
                    } catch (_: Exception) {}
                }
            }

            if (!foundAny) return content

            // Apply spans to contiguous highlighted ranges
            var inRange = false
            var start = 0
            for (i in content.indices) {
                if (highlighted[i] && !inRange) {
                    inRange = true
                    start = i
                } else if (!highlighted[i] && inRange) {
                    inRange = false
                    applySpan(spannable, start, i)
                }
            }
            if (inRange) {
                applySpan(spannable, start, content.length)
            }
            return spannable
        }

        private fun applySpan(spannable: SpannableString, start: Int, end: Int) {
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#FF5252")),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        private fun buildMizoTokenRegex(token: String): String {
            val t = token.lowercase().trim()
            // Check known Mizo variation regexes
            when (t) {
                "i", "in" -> return "\\b[iî][n]?\\b"
                "a", "an" -> return "\\b[aâ][n]?\\b"
                "ka", "kan" -> return "\\bka[n]?\\b"
                "chunga", "chungah" -> return "\\bchunga[h]?\\b"
                "krista", "khrista" -> return "\\bk[h]?rista\\b"
                "isua", "isuan" -> return "\\bisua[n]?\\b"
                "pathian", "pathianin" -> return "\\bpathian(in|ah)?\\b"
                "va", "vin" -> return "\\bv[a|i]n?\\b"
                "te", "ten" -> return "\\bte[n]?\\b"
                "chu", "chuan" -> return "\\bchua?n?\\b"
            }
            return "\\b" + buildMizoRegex(token) + "\\b"
        }

        private fun buildMizoRegex(query: String): String {
            val escaped = query.lowercase()
                .replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("*", "\\*")
                .replace("+", "\\+")
                .replace("?", "\\?")
                .replace("^", "\\^")
                .replace("$", "\\$")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("|", "\\|")

            return escaped
                .replace("a", "[aâ]")
                .replace("e", "[eê]")
                .replace("i", "[iî]")
                .replace("o", "[oô]")
                .replace("u", "[uû]")
                .replace("t", "[tṭ]")
                .replace("r", "[rṛ]")
        }
    }


    class SearchDiffCallback : DiffUtil.ItemCallback<BibleVerse>() {
        override fun areItemsTheSame(oldItem: BibleVerse, newItem: BibleVerse): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BibleVerse, newItem: BibleVerse): Boolean = oldItem == newItem
    }
}