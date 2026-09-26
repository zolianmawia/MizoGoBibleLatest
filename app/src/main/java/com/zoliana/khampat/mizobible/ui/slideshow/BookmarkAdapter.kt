package com.zoliana.khampat.mizobible.ui.slideshow

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.CompoundButtonCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zoliana.khampat.mizobible.data.Bookmark
import com.zoliana.khampat.mizobible.databinding.ItemTreeDateHeaderBinding
import com.zoliana.khampat.mizobible.databinding.ItemTreeVerseChildBinding
import com.zoliana.khampat.mizobible.ui.common.TreeConnectorView
import com.zoliana.khampat.mizobible.ui.transform.FontSettings
import com.zoliana.khampat.mizobible.utils.ThemeHelper

sealed class BookmarkTreeItem {
    data class GroupHeader(
        val groupId: String,
        val dateStr: String,
        val count: Int,
        val title: String,
        val isExpanded: Boolean,
        val isFirstRoot: Boolean,
        val isLastRoot: Boolean,
        val hasChildrenWhenExpanded: Boolean,
        val groupBookmarks: List<Bookmark> = emptyList()
    ) : BookmarkTreeItem()

    data class VerseChild(
        val bookmarks: List<Bookmark>,
        val displayReference: String,
        val groupId: String,
        val isFirstChild: Boolean = false,
        val isLastChild: Boolean = false,
        val parentHasBelowRoot: Boolean = false
    ) : BookmarkTreeItem() {
        val primaryBookmark: Bookmark get() = bookmarks.first()
        val color: String get() = primaryBookmark.color
        val title: String get() = primaryBookmark.title
        val note: String get() = primaryBookmark.note
        val selectionKey: String get() = bookmarks.joinToString("_") { "${it.verseId}_${it.version}" }
    }
}

class BookmarkAdapter(
    private val onBookmarkClick: (Bookmark) -> Unit,
    private val onLongClick: (BookmarkTreeItem.VerseChild) -> Unit,
    private val onSelectionChange: (BookmarkTreeItem.VerseChild, Boolean) -> Unit,
    private val onGroupToggle: (String) -> Unit,
    private val onEditTitleClick: (BookmarkTreeItem.GroupHeader) -> Unit,
    private val onEditBookmarkClick: (BookmarkTreeItem.VerseChild) -> Unit,
    private val onDateMoreClick: (View, BookmarkTreeItem.GroupHeader) -> Unit,
    private val onVerseMoreClick: (View, BookmarkTreeItem.VerseChild) -> Unit
) : ListAdapter<BookmarkTreeItem, RecyclerView.ViewHolder>(BookmarkTreeDiffCallback()) {

    companion object {
        private const val TYPE_GROUP_HEADER = 0
        private const val TYPE_VERSE_CHILD = 1

        fun toSuperscript(verseStr: String): String {
            val map = mapOf(
                '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
                '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹'
            )
            return verseStr.map { map[it] ?: it }.joinToString("")
        }

        fun resolveDotColor(rawColor: String?): Int {
            if (rawColor.isNullOrBlank()) return Color.parseColor("#FFCA28")
            val c = rawColor.trim().lowercase()
            return when {
                c.contains("red") || c.contains("pink") || c == "#ff5252" || c == "#ff9999" || c == "#e53935" || c == "#ef9a9a" || c == "#ffcdd2" -> Color.parseColor("#EF5350")
                c.contains("blue") || c.contains("cyan") || c == "#448aff" || c == "#88b4fc" || c == "#1e88e5" || c == "#90caf9" || c == "#bbdefb" -> Color.parseColor("#42A5F5")
                c.contains("green") || c.contains("mint") || c == "#4caf50" || c == "#b3ffb6" || c == "#43a047" || c == "#a5d6a7" || c == "#c8e6c9" -> Color.parseColor("#66BB6A")
                c.contains("yellow") || c.contains("gold") || c.contains("amber") || c == "#ffd740" || c == "#ffeeb0" || c == "#ffe7ad" || c == "#ffe082" || c == "#fff59d" -> Color.parseColor("#FFCA28")
                c.contains("purple") || c.contains("violet") || c == "#e040fb" || c == "#9c27b0" || c == "#f0a4fc" || c == "#ce93d8" || c == "#e1bee7" -> Color.parseColor("#AB47BC")
                else -> {
                    try {
                        Color.parseColor(rawColor)
                    } catch (_: Exception) {
                        Color.parseColor("#FFCA28")
                    }
                }
            }
        }
    }

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

    fun toggleSelection(item: BookmarkTreeItem.VerseChild) {
        val key = item.selectionKey
        if (selectedItems.contains(key)) {
            selectedItems.remove(key)
        } else {
            selectedItems.add(key)
        }
        notifyDataSetChanged()
    }

    fun selectAll(verseItems: List<BookmarkTreeItem.VerseChild>) {
        selectedItems.clear()
        verseItems.forEach { selectedItems.add(it.selectionKey) }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is BookmarkTreeItem.GroupHeader -> TYPE_GROUP_HEADER
            is BookmarkTreeItem.VerseChild -> TYPE_VERSE_CHILD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GROUP_HEADER) {
            val binding = ItemTreeDateHeaderBinding.inflate(inflater, parent, false)
            GroupHeaderViewHolder(binding)
        } else {
            val binding = ItemTreeVerseChildBinding.inflate(inflater, parent, false)
            VerseChildViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is BookmarkTreeItem.GroupHeader -> (holder as GroupHeaderViewHolder).bind(item)
            is BookmarkTreeItem.VerseChild -> (holder as VerseChildViewHolder).bind(item)
        }
    }

    inner class GroupHeaderViewHolder(private val binding: ItemTreeDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(header: BookmarkTreeItem.GroupHeader) {
            val context = binding.root.context
            // Configure tree connector
            binding.treeConnector.apply {
                nodeType = TreeConnectorView.NodeType.ROOT
                isFirstRoot = header.isFirstRoot
                isLastRoot = header.isLastRoot
                isExpanded = header.isExpanded
                hasChildren = header.hasChildrenWhenExpanded
                customDotY = -1f
                updateThemeColors()
            }

            // Set Date Title: e.g. "Date: 24-09-2026 (18)"
            binding.textDateTitle.text = "Date: ${header.dateStr} (${header.count})"
            binding.iconExpandChevron.rotation = if (header.isExpanded) 90f else 0f

            val bgColor = ThemeHelper.getEffectiveBackgroundColor(context)
            val isDark = ThemeHelper.isColorDark(bgColor)
            val fontColor = ThemeHelper.getEffectiveFontColor(context)
            val defaultTitleColor = if (isDark) Color.parseColor("#ECEBF4") else Color.parseColor("#201A12")
            binding.textDateTitle.setTextColor(fontColor ?: defaultTitleColor)
            val iconColor = ThemeHelper.getEffectiveIconColor(context)
                ?: ThemeHelper.APP_COLORS.find { it.id.equals(ThemeHelper.getSelectedAppColor(context), ignoreCase = true) }?.colorInt
                ?: Color.parseColor("#1B6CB0")
            binding.btnDateMore.setColorFilter(iconColor)
            binding.iconExpandChevron.setColorFilter(iconColor)

            // Horizontal three-dots option menu button on the Date row
            binding.btnDateMore.setOnClickListener { v ->
                onDateMoreClick(v, header)
            }

            // Entire row toggles expansion of the group
            binding.root.setOnClickListener {
                onGroupToggle(header.groupId)
            }

            // Long click on header also opens Date options
            binding.root.setOnLongClickListener { v ->
                onDateMoreClick(v, header)
                true
            }
        }
    }

    inner class VerseChildViewHolder(private val binding: ItemTreeVerseChildBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BookmarkTreeItem.VerseChild) {
            val primary = item.primaryBookmark
            val context = binding.root.context
            val density = binding.root.resources.displayMetrics.density

            val bgColor = ThemeHelper.getEffectiveBackgroundColor(context)
            val isDark = ThemeHelper.isColorDark(bgColor)
            val appPrimary = ThemeHelper.APP_COLORS.find {
                it.id.equals(ThemeHelper.getSelectedAppColor(context), ignoreCase = true)
            }?.colorInt ?: Color.parseColor("#1976D2")

            // Title Badge above the scripture reference
            val hasTitle = item.title.isNotBlank()
            if (hasTitle) {
                binding.textChildTitle.visibility = View.VISIBLE
                binding.textChildTitle.text = item.title
                val pillBg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 12f * density
                    val pillColor = if (isDark) Color.parseColor("#2E2F45") else Color.parseColor("#E5D9C0")
                    setColor(pillColor)
                    setStroke((1 * density).toInt(), if (isDark) Color.parseColor("#44FFFFFF") else Color.parseColor("#DACFB9"))
                }
                binding.textChildTitle.background = pillBg
                binding.textChildTitle.setTextColor(if (isDark) Color.parseColor("#D3D4F2") else Color.parseColor("#201A12"))
                binding.textChildTitle.setOnClickListener {
                    onEditBookmarkClick(item)
                }
            } else {
                binding.textChildTitle.visibility = View.GONE
            }

            // Configure tree connector with bookmark's actual color
            val dotColor = resolveDotColor(item.color)
            binding.treeConnector.apply {
                nodeType = TreeConnectorView.NodeType.CHILD
                isFirstChild = item.isFirstChild
                isLastChild = item.isLastChild
                parentHasBelowRoot = item.parentHasBelowRoot
                childDotColor = dotColor
                // Align child dot with the scripture reference line
                customDotY = if (hasTitle) 38f * density else 16f * density
                updateThemeColors()
            }

            val refColor = ThemeHelper.getContrastingVerseNumberColor(bgColor, appPrimary)
            val defaultSuper = if (isDark) Color.parseColor("#64B5F6") else Color.parseColor("#1976D2")
            val superColor = ThemeHelper.getContrastingVerseNumberColor(bgColor, defaultSuper)

            // Reference formatting: Genesis 1:1-4 ¹ [text 1] ² [text 2] ...
            val ref = item.displayReference
            val ssb = SpannableStringBuilder()
            ssb.append(ref)
            ssb.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                ref.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            ssb.setSpan(
                ForegroundColorSpan(refColor),
                0,
                ref.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // Append each verse with superscript verse number and text
            for (b in item.bookmarks) {
                ssb.append(" ")
                val startSuper = ssb.length
                val superVerse = toSuperscript(b.verse)
                ssb.append(superVerse)
                val endSuper = ssb.length
                ssb.setSpan(
                    ForegroundColorSpan(superColor),
                    startSuper,
                    endSuper,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ssb.append(" ")
                ssb.append(b.text.trim())
            }

            binding.textVerseContent.text = ssb
            binding.textVerseContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSettings.fontSize.toFloat())
            val customFontColor = try {
                if (fontSettings.fontColor.isNotBlank() && fontSettings.fontColor != "default") {
                    Color.parseColor(fontSettings.fontColor)
                } else null
            } catch (e: Exception) { null } ?: ThemeHelper.getEffectiveFontColor(context)
            val finalBookmarkTextColor = ThemeHelper.getContrastingTextColor(bgColor, customFontColor)
            binding.textVerseContent.setTextColor(finalBookmarkTextColor)

            val iconColor = ThemeHelper.getEffectiveIconColor(context)
                ?: appPrimary
            binding.btnVerseMore.setColorFilter(iconColor)

            // Note (if present)
            if (item.note.isNotBlank()) {
                binding.textVerseNote.visibility = View.VISIBLE
                binding.textVerseNote.text = "Note: ${item.note}"
                binding.textVerseNote.setTextColor(if (isDark) Color.parseColor("#A0A2B8") else Color.parseColor("#5A5245"))
            } else {
                binding.textVerseNote.visibility = View.GONE
            }

            // Edit / Options button (•••) on each joined bookmark entry
            binding.btnVerseMore.setOnClickListener { v ->
                onVerseMoreClick(v, item)
            }

            // Selection checkbox (for action mode)
            val isSelected = selectedItems.contains(item.selectionKey)
            binding.checkboxSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.checkboxSelect.isChecked = isSelected
            CompoundButtonCompat.setButtonTintList(binding.checkboxSelect, ColorStateList.valueOf(appPrimary))

            val clickListener = View.OnClickListener {
                if (isSelectionMode) {
                    toggleSelection(item)
                    onSelectionChange(item, !isSelected)
                } else {
                    onBookmarkClick(primary)
                }
            }
            binding.root.setOnClickListener(clickListener)
            binding.textVerseContent.setOnClickListener(clickListener)

            // Long press on verse text or card pops up Color Change & Delete buttons
            val longListener = View.OnLongClickListener {
                onLongClick(item)
                true
            }
            binding.textVerseContent.setOnLongClickListener(longListener)
            binding.root.setOnLongClickListener(longListener)
        }
    }
}

class BookmarkTreeDiffCallback : DiffUtil.ItemCallback<BookmarkTreeItem>() {
    override fun areItemsTheSame(oldItem: BookmarkTreeItem, newItem: BookmarkTreeItem): Boolean {
        return when {
            oldItem is BookmarkTreeItem.GroupHeader && newItem is BookmarkTreeItem.GroupHeader ->
                oldItem.groupId == newItem.groupId
            oldItem is BookmarkTreeItem.VerseChild && newItem is BookmarkTreeItem.VerseChild ->
                oldItem.selectionKey == newItem.selectionKey
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: BookmarkTreeItem, newItem: BookmarkTreeItem): Boolean {
        return oldItem == newItem
    }
}
