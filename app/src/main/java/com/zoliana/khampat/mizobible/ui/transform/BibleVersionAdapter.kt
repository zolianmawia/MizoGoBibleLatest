package com.zoliana.khampat.mizobible.ui.transform

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.CompoundButtonCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.databinding.ItemBibleVersionBinding
import com.zoliana.khampat.mizobible.utils.ThemeHelper

data class BibleVersionItem(
    val title: String,
    val code: String,
    val driveId: String,
    val localVersion: Int,
    val remoteVersion: Int,
    val isDownloaded: Boolean,
    val updateMessage: String? = null,
    var isSelected: Boolean = false
)

class BibleVersionAdapter(
    private val onActionClick: (BibleVersionItem) -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<BibleVersionAdapter.ViewHolder>() {

    private var items = listOf<BibleVersionItem>()
    var isDeleteMode = false
        get() = field
        set(value) {
            field = value
            if (!value) items.forEach { it.isSelected = false }
            notifyDataSetChanged()
        }

    fun submitList(newList: List<BibleVersionItem>) {
        items = newList
        notifyDataSetChanged()
    }

    fun getSelectedItems() = items.filter { it.isSelected }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBibleVersionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemBibleVersionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BibleVersionItem) {
            val context = binding.root.context
            val bgColor = ThemeHelper.getEffectiveBackgroundColor(context)
            val isDark = ThemeHelper.isColorDark(bgColor)
            val cardColor = ThemeHelper.getEffectiveCardColor(context) ?: if (isDark) Color.parseColor("#2E2F45") else Color.parseColor("#F5F7FA")
            val primaryColor = ThemeHelper.APP_COLORS.find {
                it.id.equals(ThemeHelper.getSelectedAppColor(context), ignoreCase = true)
            }?.colorInt ?: ContextCompat.getColor(context, R.color.bible_blue)
            val fontColor = ThemeHelper.getEffectiveFontColor(context)
            val iconColor = ThemeHelper.getEffectiveIconColor(context) ?: primaryColor

            binding.root.setCardBackgroundColor(cardColor)
            binding.root.strokeColor = if (isDark) Color.parseColor("#30FFFFFF") else ColorUtils.setAlphaComponent(primaryColor, 45)

            ImageViewCompat.setImageTintList(binding.imgVersionIcon, ColorStateList.valueOf(iconColor))

            val titleColor = ThemeHelper.getContrastingTextColor(cardColor, fontColor)
            binding.textVersionName.setTextColor(titleColor)
            val statusColor = ColorUtils.setAlphaComponent(titleColor, 180)
            binding.textVersionStatus.setTextColor(statusColor)

            val btnBg = ColorUtils.setAlphaComponent(primaryColor, if (isDark) 50 else 30)
            binding.btnAction.backgroundTintList = ColorStateList.valueOf(btnBg)
            binding.btnAction.setTextColor(primaryColor)
            binding.btnAction.strokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primaryColor, 70))
            binding.btnAction.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()

            CompoundButtonCompat.setButtonTintList(binding.checkboxDelete, ColorStateList.valueOf(primaryColor))

            binding.textVersionName.text = item.title
            
            val hasUpdate = item.isDownloaded && item.remoteVersion > item.localVersion
            binding.viewUpdateDot.visibility = if (hasUpdate) View.VISIBLE else View.GONE
            
            when {
                !item.isDownloaded -> {
                    binding.textVersionStatus.text = "Download Now"
                    binding.btnAction.text = "Download"
                    binding.btnAction.visibility = View.VISIBLE
                    binding.checkboxDelete.visibility = View.GONE
                }
                hasUpdate -> {
                    binding.textVersionStatus.text = "Update thar a awm e"
                    binding.btnAction.text = "Update"
                    binding.btnAction.visibility = View.VISIBLE
                    binding.checkboxDelete.visibility = if (isDeleteMode) View.VISIBLE else View.GONE
                }
                else -> {
                    binding.textVersionStatus.text = "Downloaded"
                    binding.btnAction.visibility = View.GONE
                    binding.checkboxDelete.visibility = if (isDeleteMode) View.VISIBLE else View.GONE
                }
            }

            binding.checkboxDelete.isChecked = item.isSelected
            binding.checkboxDelete.setOnCheckedChangeListener { _, isChecked ->
                item.isSelected = isChecked
                onSelectionChanged()
            }

            binding.btnAction.setOnClickListener { onActionClick(item) }
            
            binding.root.setOnLongClickListener {
                if (item.isDownloaded) {
                    isDeleteMode = true
                    item.isSelected = true
                    onSelectionChanged()
                    true
                } else false
            }

            binding.root.setOnClickListener {
                if (isDeleteMode && item.isDownloaded) {
                    binding.checkboxDelete.isChecked = !binding.checkboxDelete.isChecked
                } else if (!item.isDownloaded || hasUpdate) {
                    onActionClick(item)
                }
            }
        }
    }
}
