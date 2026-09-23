package com.zoliana.khampat.mizobible.ui.passage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.zoliana.khampat.mizobible.databinding.ItemBookBinding
import com.zoliana.khampat.mizobible.databinding.ItemBookHeaderBinding

class BookRecyclerAdapter(private val onBookClick: (String) -> Unit) :
    RecyclerView.Adapter<BookRecyclerAdapter.BaseViewHolder>() {

    private var fullList = listOf<PassageListItem>()
    private var filteredList = listOf<PassageListItem>()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_BOOK = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (filteredList[position]) {
            is PassageListItem.Header -> TYPE_HEADER
            is PassageListItem.Book -> TYPE_BOOK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemBookHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                HeaderViewHolder(binding)
            }
            TYPE_BOOK -> {
                val binding = ItemBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                BookViewHolder(binding, onBookClick)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.bind(filteredList[position])
    }

    override fun getItemCount(): Int = filteredList.size

    fun submitList(list: List<PassageListItem>) {
        fullList = list
        filteredList = list
        notifyDataSetChanged()
    }

    fun filter(query: String?) {
        if (query.isNullOrEmpty()) {
            filteredList = fullList
        } else {
            val lowerCaseQuery = query.lowercase()
            val result = mutableListOf<PassageListItem>()
            var currentHeader: PassageListItem.Header? = null
            
            fullList.forEach { item ->
                when (item) {
                    is PassageListItem.Header -> currentHeader = item
                    is PassageListItem.Book -> {
                        if (item.name.lowercase().contains(lowerCaseQuery)) {
                            if (currentHeader != null && !result.contains(currentHeader!!)) {
                                result.add(currentHeader!!)
                            }
                            result.add(item)
                        }
                    }
                }
            }
            filteredList = result
        }
        notifyDataSetChanged()
    }

    abstract class BaseViewHolder(binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
        abstract fun bind(item: PassageListItem)
    }

    class HeaderViewHolder(private val binding: ItemBookHeaderBinding) : BaseViewHolder(binding) {
        override fun bind(item: PassageListItem) {
            val header = item as PassageListItem.Header
            binding.textHeader.text = header.title
        }
    }

    class BookViewHolder(
        private val binding: ItemBookBinding,
        private val onBookClick: (String) -> Unit
    ) : BaseViewHolder(binding) {
        override fun bind(item: PassageListItem) {
            val book = item as PassageListItem.Book
            (binding.root as? android.widget.TextView)?.text = book.name
            binding.root.setOnClickListener {
                onBookClick(book.name)
            }
        }
    }
}
