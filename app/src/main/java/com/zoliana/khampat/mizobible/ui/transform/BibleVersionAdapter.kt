package com.zoliana.khampat.mizobible.ui.transform

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.databinding.ItemBibleVersionBinding

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
