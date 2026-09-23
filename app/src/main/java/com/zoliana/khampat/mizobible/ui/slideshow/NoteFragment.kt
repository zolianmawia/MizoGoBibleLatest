package com.zoliana.khampat.mizobible.ui.slideshow

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.*
import com.zoliana.khampat.mizobible.databinding.FragmentNoteBinding
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModel
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModelFactory

class NoteFragment : Fragment() {

    private var _binding: FragmentNoteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    private lateinit var noteAdapter: NoteAdapter
    private var actionMode: ActionMode? = null
    private val selectedNotes = mutableSetOf<Note>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        noteAdapter = NoteAdapter(
            onNoteClick = { note ->
                viewModel.updateVersion(note.version)
                viewModel.updateSelection(note.book, note.chapter, note.verseId)
                findNavController().popBackStack(R.id.nav_home, false)
            },
            onLongClick = { note ->
                if (actionMode == null) {
                    actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(actionModeCallback)
                    toggleSelection(note)
                }
            },
            onSelectionChange = { note, _ ->
                toggleSelection(note)
            }
        )

        binding.recyclerviewNotes.adapter = noteAdapter

        viewModel.allNotes.observe(viewLifecycleOwner) { notes ->
            noteAdapter.submitList(notes)
            updateEmptyState(notes.isEmpty())
        }
    }

    private fun toggleSelection(note: Note) {
        if (selectedNotes.contains(note)) {
            selectedNotes.remove(note)
        } else {
            selectedNotes.add(note)
        }
        
        noteAdapter.toggleSelection(note.verseId)
        
        if (selectedNotes.isEmpty()) {
            actionMode?.finish()
        } else {
            actionMode?.title = "${selectedNotes.size} selected"
            noteAdapter.setSelectionMode(true)
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_bookmark_selection, menu)
            noteAdapter.setSelectionMode(true)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_delete_selected -> {
                    showDeleteConfirmationDialog("He Note ${selectedNotes.size} hi i delete duh tak tak em?") {
                        selectedNotes.forEach { viewModel.deleteNote(it) }
                        mode.finish()
                    }
                    true
                }
                R.id.action_select_all -> {
                    val allNotes = viewModel.allNotes.value ?: return true
                    selectedNotes.clear()
                    selectedNotes.addAll(allNotes)
                    noteAdapter.selectAll()
                    mode.title = "${selectedNotes.size} selected"
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            noteAdapter.setSelectionMode(false)
            selectedNotes.clear()
            actionMode = null
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.emptyState.textEmptyMessage.text = "Note a la awm lo"
    }

    private fun showDeleteConfirmationDialog(message: String, onConfirm: () -> Unit) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete?")
            .setMessage(message)
            .setPositiveButton("Aw") { _, _ -> onConfirm() }
            .setNegativeButton("Aih", null)
            .show()
        (requireActivity() as? MainActivity)?.limitDialogWidth(dialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
