package com.zoliana.khampat.mizobible.ui.slideshow

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zoliana.khampat.mizobible.data.Note
import com.zoliana.khampat.mizobible.databinding.ItemNoteBinding
import com.zoliana.khampat.mizobible.ui.transform.WavyUnderlineSpan
import java.text.SimpleDateFormat
import java.util.*

class NoteAdapter(
    private val onNoteClick: (Note) -> Unit,
    private val onLongClick: (Note) -> Unit,
    private val onSelectionChange: (Note, Boolean) -> Unit
) : ListAdapter<Note, NoteAdapter.NoteViewHolder>(NoteDiffCallback()) {

    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<Int>()

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) selectedItems.clear()
        notifyDataSetChanged()
    }

    fun toggleSelection(noteId: Int) {
        if (selectedItems.contains(noteId)) {
            selectedItems.remove(noteId)
        } else {
            selectedItems.add(noteId)
        }
        notifyDataSetChanged()
    }

    fun selectAll() {
        selectedItems.clear()
        currentList.forEach { selectedItems.add(it.verseId) }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, isSelectionMode, selectedItems.contains(item.verseId))
    }

    inner class NoteViewHolder(private val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(note: Note, selectionMode: Boolean, isSelected: Boolean) {
            binding.textNoteReference.text = "${note.book} ${note.chapter}:${note.verse}"
            binding.textNoteContent.text = note.text
            binding.textNoteVersion?.text = note.version.uppercase()
            
            if (note.bibleText.isNotEmpty()) {
                binding.textBibleContent.visibility = View.VISIBLE
                val spannable = SpannableString(note.bibleText)
                val colorInt = try { Color.parseColor(note.color) } catch (e: Exception) { Color.RED }
                spannable.setSpan(WavyUnderlineSpan(colorInt), 0, note.bibleText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                binding.textBibleContent.text = spannable
            } else {
                binding.textBibleContent.visibility = View.GONE
            }
            
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            binding.textNoteDate.text = sdf.format(Date(note.timestamp))

            val context = binding.root.context
            val isDarkTheme = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            if (isDarkTheme) {
                binding.textNoteContent.setTextColor(Color.WHITE)
                binding.textNoteDate.setTextColor(Color.GRAY)
                if (note.bibleText.isNotEmpty()) {
                    binding.textBibleContent.setTextColor(Color.LTGRAY)
                    binding.textNoteVersion?.setTextColor(Color.WHITE)
                }
            } else {
                binding.textNoteContent.setTextColor(Color.BLACK)
                binding.textBibleContent.setTextColor(Color.DKGRAY)
            }

            binding.viewNoteColor.visibility = View.GONE

            binding.checkboxNote?.visibility = if (selectionMode) View.VISIBLE else View.GONE
            binding.checkboxNote?.isChecked = isSelected

            binding.root.setOnClickListener {
                if (selectionMode) {
                    onSelectionChange(note, !isSelected)
                } else {
                    onNoteClick(note)
                }
            }

            binding.root.setOnLongClickListener {
                onLongClick(note)
                true
            }
        }
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean = oldItem.verseId == newItem.verseId
        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean = oldItem == newItem
    }
}
