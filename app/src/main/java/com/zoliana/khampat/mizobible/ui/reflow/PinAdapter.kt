package com.zoliana.khampat.mizobible.ui.reflow

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
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
import com.zoliana.khampat.mizobible.data.Pin
import com.zoliana.khampat.mizobible.databinding.ItemTreeDateHeaderBinding
import com.zoliana.khampat.mizobible.databinding.ItemTreePinChildBinding
import com.zoliana.khampat.mizobible.ui.common.TreeConnectorView
import com.zoliana.khampat.mizobible.ui.slideshow.BookmarkAdapter
import com.zoliana.khampat.mizobible.ui.transform.FontSettings
import com.zoliana.khampat.mizobible.utils.ThemeHelper

sealed class PinTreeItem {
    data class GroupHeader(
        val groupId: String,
        val dateStr: String,
        val count: Int,
        val pins: List<Pin> = emptyList(),
        val isExpanded: Boolean,
        val isFirstRoot: Boolean,
        val isLastRoot: Boolean,
        val hasChildrenWhenExpanded: Boolean
    ) : PinTreeItem()

    data class PinChild(
        val pin: Pin,
        val groupId: String,
        val isFirstChild: Boolean,
        val isLastChild: Boolean,
        val parentHasBelowRoot: Boolean
    ) : PinTreeItem()
}

class PinAdapter(
    private val onPinClick: (Pin) -> Unit,
    private val onLongClick: (Pin) -> Unit,
    private val onSelectionChange: (Pin, Boolean) -> Unit,
    private val onGroupToggle: (String) -> Unit,
    private val onDateMoreClick: (View, PinTreeItem.GroupHeader) -> Unit = { _, _ -> }
) : ListAdapter<PinTreeItem, RecyclerView.ViewHolder>(PinTreeDiffCallback()) {

    companion object {
        private const val TYPE_GROUP_HEADER = 0
        private const val TYPE_PIN_CHILD = 1
    }

    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<Int>()
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

    fun toggleSelection(pinId: Int) {
        if (selectedItems.contains(pinId)) {
            selectedItems.remove(pinId)
        } else {
            selectedItems.add(pinId)
        }
        notifyDataSetChanged()
    }

    fun selectAll(pins: List<Pin>) {
        selectedItems.clear()
        pins.forEach { selectedItems.add(it.verseId) }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is PinTreeItem.GroupHeader -> TYPE_GROUP_HEADER
            is PinTreeItem.PinChild -> TYPE_PIN_CHILD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GROUP_HEADER) {
            val binding = ItemTreeDateHeaderBinding.inflate(inflater, parent, false)
            GroupHeaderViewHolder(binding)
        } else {
            val binding = ItemTreePinChildBinding.inflate(inflater, parent, false)
            PinChildViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is PinTreeItem.GroupHeader -> (holder as GroupHeaderViewHolder).bind(item)
            is PinTreeItem.PinChild -> (holder as PinChildViewHolder).bind(item)
        }
    }

    inner class GroupHeaderViewHolder(private val binding: ItemTreeDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(header: PinTreeItem.GroupHeader) {
            val context = binding.root.context
            binding.treeConnector.apply {
                nodeType = TreeConnectorView.NodeType.ROOT
                isFirstRoot = header.isFirstRoot
                isLastRoot = header.isLastRoot
                isExpanded = header.isExpanded
                hasChildren = header.hasChildrenWhenExpanded
                customDotY = -1f
                updateThemeColors()
            }

            binding.textDateTitle.text = "Date: ${header.dateStr} (${header.count})"
            binding.iconExpandChevron.visibility = View.VISIBLE
            binding.iconExpandChevron.rotation = if (header.isExpanded) 90f else 0f
            binding.textTitleBadge.visibility = View.GONE
            binding.btnDateMore.visibility = View.VISIBLE

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

            binding.btnDateMore.setOnClickListener { v ->
                onDateMoreClick(v, header)
            }

            binding.root.setOnClickListener {
                onGroupToggle(header.groupId)
            }

            binding.textDateTitle.setOnClickListener {
                onGroupToggle(header.groupId)
            }

            binding.iconExpandChevron.setOnClickListener {
                onGroupToggle(header.groupId)
            }

            binding.treeConnector.setOnClickListener {
                onGroupToggle(header.groupId)
            }

            binding.root.setOnLongClickListener { v ->
                onDateMoreClick(v, header)
                true
            }
        }
    }

    inner class PinChildViewHolder(private val binding: ItemTreePinChildBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PinTreeItem.PinChild) {
            val pin = item.pin
            val dotColor = BookmarkAdapter.resolveDotColor(pin.color)
            val context = binding.root.context

            binding.treeConnector.apply {
                nodeType = TreeConnectorView.NodeType.CHILD
                isFirstChild = item.isFirstChild
                isLastChild = item.isLastChild
                parentHasBelowRoot = item.parentHasBelowRoot
                childDotColor = dotColor
                updateThemeColors()
            }

            val bgColor = ThemeHelper.getEffectiveBackgroundColor(context)
            val isDark = ThemeHelper.isColorDark(bgColor)
            val appPrimary = ThemeHelper.APP_COLORS.find {
                it.id.equals(ThemeHelper.getSelectedAppColor(context), ignoreCase = true)
            }?.colorInt ?: Color.parseColor("#1976D2")

            val refColor = ThemeHelper.getContrastingVerseNumberColor(bgColor, appPrimary)
            val defaultSuper = if (isDark) Color.parseColor("#64B5F6") else Color.parseColor("#1976D2")
            val superColor = ThemeHelper.getContrastingVerseNumberColor(bgColor, defaultSuper)

            // Reference formatting: Genesis 3:2 ¹ [Verse text]
            val ref = "${pin.book} ${pin.chapter}:${pin.verse}"
            val superVerse = BookmarkAdapter.toSuperscript(pin.verse)
            val fullText = "$ref $superVerse ${pin.text}"
            val ssb = SpannableStringBuilder(fullText)

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

            ssb.setSpan(
                ForegroundColorSpan(superColor),
                ref.length + 1,
                ref.length + 1 + superVerse.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            binding.textPinContent.text = ssb
            binding.textPinContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSettings.fontSize.toFloat())
            val customFontColor = try {
                if (fontSettings.fontColor.isNotBlank() && fontSettings.fontColor != "default") {
                    Color.parseColor(fontSettings.fontColor)
                } else null
            } catch (e: Exception) { null } ?: ThemeHelper.getEffectiveFontColor(context)
            val finalPinTextColor = ThemeHelper.getContrastingTextColor(bgColor, customFontColor)
            binding.textPinContent.setTextColor(finalPinTextColor)
            CompoundButtonCompat.setButtonTintList(binding.checkboxSelect, ColorStateList.valueOf(appPrimary))

            val isSelected = selectedItems.contains(pin.verseId)
            binding.checkboxSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.checkboxSelect.isChecked = isSelected

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(pin.verseId)
                    onSelectionChange(pin, !isSelected)
                } else {
                    onPinClick(pin)
                }
            }

            binding.root.setOnLongClickListener {
                onLongClick(pin)
                true
            }
        }
    }
}

class PinTreeDiffCallback : DiffUtil.ItemCallback<PinTreeItem>() {
    override fun areItemsTheSame(oldItem: PinTreeItem, newItem: PinTreeItem): Boolean {
        return when {
            oldItem is PinTreeItem.GroupHeader && newItem is PinTreeItem.GroupHeader ->
                oldItem.groupId == newItem.groupId
            oldItem is PinTreeItem.PinChild && newItem is PinTreeItem.PinChild ->
                oldItem.pin.verseId == newItem.pin.verseId &&
                        oldItem.pin.version == newItem.pin.version
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: PinTreeItem, newItem: PinTreeItem): Boolean {
        return oldItem == newItem
    }
}
