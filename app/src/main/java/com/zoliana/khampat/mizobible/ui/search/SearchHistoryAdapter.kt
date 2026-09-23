package com.zoliana.khampat.mizobible.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zoliana.khampat.mizobible.R

class SearchHistoryAdapter(
    private var history: List<String>,
    private val onItemClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder>() {

    fun updateHistory(newHistory: List<String>) {
        history = newHistory
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val query = history[position]
        holder.textQuery.text = query
        holder.itemView.setOnClickListener { onItemClick(query) }
        holder.btnDelete.setOnClickListener { onDeleteClick(query) }
    }

    override fun getItemCount() = history.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textQuery: TextView = view.findViewById(R.id.text_history_query)
        val btnDelete: ImageView = view.findViewById(R.id.btn_delete_history)
    }
}
