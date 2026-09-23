package com.zoliana.khampat.mizobible.ui.search

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zoliana.khampat.mizobible.data.BibleVerse
import com.zoliana.khampat.mizobible.databinding.ItemSearchBinding
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
            
            val content = verse.text ?: ""
            if (query.isNotEmpty() && query.length >= 2) {
                val spannable = SpannableString(content)
                try {
                    // Improved Mizo-friendly regex for multi-word phrases
                    val regex = buildMizoRegex(query)
                    val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
                    val matcher = pattern.matcher(content)
                    
                    var found = false
                    while (matcher.find()) {
                        spannable.setSpan(
                            ForegroundColorSpan(Color.parseColor("#FF5252")),
                            matcher.start(),
                            matcher.end(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        found = true
                    }
                    binding.textContent.text = if (found) spannable else content
                } catch (e: Exception) {
                    binding.textContent.text = content
                }
            } else {
                binding.textContent.text = content
            }
            
            binding.root.setOnClickListener { onClick(verse) }
        }

        private fun buildMizoRegex(query: String): String {
            // Escape special regex characters except the ones we want to use for Mizo accents
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
        }
    }

    class SearchDiffCallback : DiffUtil.ItemCallback<BibleVerse>() {
        override fun areItemsTheSame(oldItem: BibleVerse, newItem: BibleVerse): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BibleVerse, newItem: BibleVerse): Boolean = oldItem == newItem
    }
}