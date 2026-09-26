package com.zoliana.khampat.mizobible.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.zoliana.khampat.mizobible.databinding.ItemThemePresetBinding
import com.zoliana.khampat.mizobible.utils.ThemeHelper

class ThemePresetAdapter(
    private val items: List<ThemeHelper.ThemePreset>,
    private var selectedPreset: ThemeHelper.ThemePreset,
    private val onSelected: (ThemeHelper.ThemePreset) -> Unit
) : RecyclerView.Adapter<ThemePresetAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemThemePresetBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemThemePresetBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val preset = items[position]
        val isSelected = preset == selectedPreset
        val context = holder.itemView.context
        val density = context.resources.displayMetrics.density

        holder.binding.textPresetName.text = preset.displayName
        holder.binding.textPresetSubtitle.text = preset.subtitle

        // Swatch BG setup
        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            val bgColor = try {
                Color.parseColor(preset.sampleBgHex)
            } catch (_: Exception) {
                Color.LTGRAY
            }
            setColor(bgColor)
            val strokeColor = if (ThemeHelper.isColorDark(bgColor)) {
                Color.parseColor("#33FFFFFF")
            } else {
                Color.parseColor("#33000000")
            }
            setStroke((1 * density).toInt(), strokeColor)
        }
        holder.binding.viewPresetBg.background = bgDrawable

        // Swatch Accent setup
        val accentDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            val accentColor = try {
                Color.parseColor(preset.sampleAccentHex)
            } catch (_: Exception) {
                Color.BLUE
            }
            setColor(accentColor)
        }
        holder.binding.viewPresetAccent.background = accentDrawable

        // Card styling based on selection state
        val tvPrimary = TypedValue()
        context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tvPrimary, true)
        val primaryColor = tvPrimary.data

        if (isSelected) {
            val selectionColor = try {
                Color.parseColor(preset.primaryHex)
            } catch (_: Exception) {
                primaryColor
            }
            holder.binding.cardThemePreset.strokeColor = selectionColor
            holder.binding.cardThemePreset.strokeWidth = (2 * density).toInt()
            holder.binding.iconPresetCheck.visibility = View.VISIBLE
            holder.binding.iconPresetCheck.imageTintList = ColorStateList.valueOf(selectionColor)
            holder.binding.textPresetName.setTextColor(selectionColor)
        } else {
            val tvOnSurface = TypedValue()
            context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorOnSurface,
                tvOnSurface,
                true
            )
            val strokeColor = ColorUtils.setAlphaComponent(tvOnSurface.data, 35)

            holder.binding.cardThemePreset.strokeColor = strokeColor
            holder.binding.cardThemePreset.strokeWidth = (1 * density).toInt()
            holder.binding.iconPresetCheck.visibility = View.GONE
            holder.binding.textPresetName.setTextColor(tvOnSurface.data)
        }

        holder.itemView.setOnClickListener {
            if (selectedPreset != preset) {
                val oldIndex = items.indexOf(selectedPreset)
                selectedPreset = preset
                if (oldIndex != -1) notifyItemChanged(oldIndex)
                notifyItemChanged(position)
                onSelected(preset)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun getSelected(): ThemeHelper.ThemePreset = selectedPreset

    fun setSelected(preset: ThemeHelper.ThemePreset) {
        val oldIndex = items.indexOf(selectedPreset)
        selectedPreset = preset
        val newIndex = items.indexOf(preset)
        if (oldIndex != -1) notifyItemChanged(oldIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }
}
