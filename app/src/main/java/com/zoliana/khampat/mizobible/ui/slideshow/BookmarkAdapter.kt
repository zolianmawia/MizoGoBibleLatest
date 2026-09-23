package com.zoliana.khampat.mizobible.ui.slideshow

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zoliana.khampat.mizobible.data.Bookmark
import com.zoliana.khampat.mizobible.databinding.ItemBookmarkBinding
import com.zoliana.khampat.mizobible.ui.transform.FontSettings
import java.text.SimpleDateFormat
import java.util.*

class BookmarkAdapter(
    private val onBookmarkClick: (Bookmark) -> Unit,
    private val onLongClick: (Bookmark) -> Unit,
    private val onSelectionChange: (Bookmark, Boolean) -> Unit
) : ListAdapter<Bookmark, BookmarkAdapter.BookmarkViewHolder>(BookmarkDiffCallback()) {

    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<String>()
    private var fontSettings = FontSettings()

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) selectedItems.clear()
        notifyDataSetChanged()
    }

    fun setFontSettings(settings: FontSettings) {
        this.fontSettings = settings
        notifyDataSetChanged()
    }

    fun toggleSelection(bookmark: Bookmark) {
        val key = "${bookmark.verseId}_${bookmark.version}"
        if (selectedItems.contains(key)) {
            selectedItems.remove(key)
        } else {
            selectedItems.add(key)
        }
        notifyDataSetChanged()
    }

    fun selectAll() {
        selectedItems.clear()
        currentList.forEach { selectedItems.add("${it.verseId}_${it.version}") }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val binding = ItemBookmarkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BookmarkViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        val item = getItem(position)
        val key = "${item.verseId}_${item.version}"
        holder.bind(item, isSelectionMode, selectedItems.contains(key), fontSettings)
    }

    inner class BookmarkViewHolder(private val binding: ItemBookmarkBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bookmark: Bookmark, selectionMode: Boolean, isSelected: Boolean, settings: FontSettings) {
            binding.textBookmarkReference.text = "${bookmark.book} ${bookmark.chapter}:${bookmark.verse}"
            binding.textBookmarkContent.text = bookmark.text
            binding.textBookmarkVersion?.text = bookmark.version
            
            // Font Settings (Only Size, Bold, Italic)
            binding.textBookmarkContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.fontSize)
            val style = when {
                settings.isBold && settings.isItalic -> Typeface.BOLD_ITALIC
                settings.isBold -> Typeface.BOLD
                settings.isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            binding.textBookmarkContent.setTypeface(binding.textBookmarkContent.typeface, style)

            // Note logic
            if (bookmark.note.isNotEmpty()) {
                binding.textBookmarkNote.visibility = View.VISIBLE
                binding.textBookmarkNote.text = bookmark.note
            } else {
                binding.textBookmarkNote.visibility = View.GONE
            }

            // Background rawng leh Text rawng logic
            try {
                if (bookmark.color.isNotEmpty()) {
                    binding.bookmarkContainer.setBackgroundColor(Color.parseColor(bookmark.color))
                    binding.textBookmarkReference.setTextColor(Color.BLACK)
                    binding.textBookmarkContent.setTextColor(Color.BLACK)
                    binding.textBookmarkNote.setTextColor(Color.BLACK)
                    binding.textBookmarkDate.setTextColor(Color.BLACK)
                    binding.textBookmarkVersion?.setTextColor(Color.WHITE)
                } else {
                    binding.bookmarkContainer.setBackgroundResource(android.R.color.transparent)
                }
            } catch (e: Exception) {
                binding.bookmarkContainer.setBackgroundResource(android.R.color.transparent)
            }

            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            binding.textBookmarkDate.text = sdf.format(Date(bookmark.timestamp))

            binding.checkboxBookmark?.visibility = if (selectionMode) View.VISIBLE else View.GONE
            binding.checkboxBookmark?.isChecked = isSelected

            binding.root.setOnClickListener {
                if (selectionMode) {
                    onSelectionChange(bookmark, !isSelected)
                } else {
                    onBookmarkClick(bookmark)
                }
            }

            binding.root.setOnLongClickListener {
                onLongClick(bookmark)
                true
            }
        }
    }

    class BookmarkDiffCallback : DiffUtil.ItemCallback<Bookmark>() {
        override fun areItemsTheSame(oldItem: Bookmark, newItem: Bookmark): Boolean = 
            oldItem.verseId == newItem.verseId && oldItem.version == newItem.version
        override fun areContentsTheSame(oldItem: Bookmark, newItem: Bookmark): Boolean = oldItem == newItem
    }
}
