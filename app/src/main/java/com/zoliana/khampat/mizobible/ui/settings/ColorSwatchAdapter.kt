package com.zoliana.khampat.mizobible.ui.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zoliana.khampat.mizobible.databinding.ItemColorSwatchBinding

class ColorSwatchAdapter(
    private val items: List<SwatchItem>,
    private var selectedId: String,
    private val onSelected: (SwatchItem) -> Unit
) : RecyclerView.Adapter<ColorSwatchAdapter.ViewHolder>() {

    data class SwatchItem(
        val id: String,
        val name: String,
        val colorInt: Int,
        val isDefault: Boolean = false,
        val hexValue: String = ""
    )

    fun setSelectedId(id: String) {
        val oldPos = items.indexOfFirst { it.id.equals(selectedId, ignoreCase = true) }
        val newPos = items.indexOfFirst { it.id.equals(id, ignoreCase = true) }
        selectedId = id
        if (oldPos != -1) notifyItemChanged(oldPos)
        if (newPos != -1) notifyItemChanged(newPos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemColorSwatchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemColorSwatchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SwatchItem) {
            binding.textColorName.text = item.name

            val isSelected = item.id.equals(selectedId, ignoreCase = true)

            val circleDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(item.colorInt)
                setStroke(
                    2,
                    if (isColorDark(item.colorInt)) Color.parseColor("#33FFFFFF") else Color.parseColor("#33000000")
                )
            }
            binding.viewColorCircle.background = circleDrawable

            binding.viewSelectionRing.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.iconCheckmark.visibility = if (isSelected) View.VISIBLE else View.GONE

            val checkmarkColor = if (isColorDark(item.colorInt)) Color.WHITE else Color.BLACK
            binding.iconCheckmark.setColorFilter(checkmarkColor)

            binding.root.setOnClickListener {
                if (!item.id.equals(selectedId, ignoreCase = true)) {
                    setSelectedId(item.id)
                    onSelected(item)
                }
            }
        }

        private fun isColorDark(color: Int): Boolean {
            val darkness =
                1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return darkness >= 0.45
        }
    }
}
