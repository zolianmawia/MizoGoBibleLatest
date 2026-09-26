package com.zoliana.khampat.mizobible.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.zoliana.khampat.mizobible.R

class TitleSuggestionAdapter(
    context: Context,
    private val allTitles: List<String>
) : ArrayAdapter<String>(context, R.layout.item_dropdown_suggestion, ArrayList(allTitles)) {

    private var currentList: List<String> = allTitles

    override fun getCount(): Int = currentList.size

    override fun getItem(position: Int): String? {
        return if (position in currentList.indices) currentList[position] else null
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.item_dropdown_suggestion,
            parent,
            false
        )
        val textTitle = view.findViewById<TextView>(R.id.text_suggestion_title)
        textTitle.text = getItem(position) ?: ""
        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                val q = constraint?.toString()?.trim()?.lowercase() ?: ""
                val filtered = if (q.isEmpty()) {
                    allTitles
                } else {
                    allTitles.filter { it.lowercase().contains(q) }
                }
                results.values = filtered
                results.count = filtered.size
                return results
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                currentList = (results?.values as? List<String>) ?: allTitles
                clear()
                addAll(currentList)
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as? String) ?: ""
            }
        }
    }
}
