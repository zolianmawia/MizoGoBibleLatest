package com.zoliana.khampat.mizobible.ui.reflow

import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zoliana.khampat.mizobible.data.Pin
import com.zoliana.khampat.mizobible.databinding.ItemPinBinding
import com.zoliana.khampat.mizobible.ui.transform.FontSettings
import java.text.SimpleDateFormat
import java.util.*

class PinAdapter(
    private val onPinClick: (Pin) -> Unit,
    private val onLongClick: (Pin) -> Unit,
    private val onSelectionChange: (Pin, Boolean) -> Unit
) : ListAdapter<Pin, PinAdapter.PinViewHolder>(PinDiffCallback()) {

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

    fun selectAll() {
        selectedItems.clear()
        currentList.forEach { selectedItems.add(it.verseId) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinViewHolder {
        val binding = ItemPinBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PinViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PinViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, isSelectionMode, selectedItems.contains(item.verseId), fontSettings)
    }

    inner class PinViewHolder(private val binding: ItemPinBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(pin: Pin, selectionMode: Boolean, isSelected: Boolean, settings: FontSettings) {
            binding.textPinReference.text = "${pin.book} ${pin.chapter}:${pin.verse}"
            binding.textPinContent.text = pin.text
            
            // Font Settings (Only Size, Bold, Italic)
            binding.textPinContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.fontSize)
            val style = when {
                settings.isBold && settings.isItalic -> Typeface.BOLD_ITALIC
                settings.isBold -> Typeface.BOLD
                settings.isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            binding.textPinContent.setTypeface(binding.textPinContent.typeface, style)

            binding.textPinVersion?.text = pin.version.uppercase()
            
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            binding.textPinDate.text = sdf.format(Date(pin.timestamp))
            
            try {
                binding.imgPinIcon?.setColorFilter(Color.parseColor(pin.color))
            } catch (e: Exception) {
                binding.imgPinIcon?.setColorFilter(Color.RED)
            }

            binding.checkboxPin?.visibility = if (selectionMode) View.VISIBLE else View.GONE
            binding.checkboxPin?.isChecked = isSelected

            binding.root.setOnClickListener {
                if (selectionMode) {
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

    class PinDiffCallback : DiffUtil.ItemCallback<Pin>() {
        override fun areItemsTheSame(oldItem: Pin, newItem: Pin): Boolean = oldItem.verseId == newItem.verseId
        override fun areContentsTheSame(oldItem: Pin, newItem: Pin): Boolean = oldItem == newItem
    }
}
