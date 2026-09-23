package com.zoliana.khampat.mizobible.ui.slideshow

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.*
import com.zoliana.khampat.mizobible.databinding.FragmentSlideshowBinding
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModel
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SlideshowFragment : Fragment() {

    private var _binding: FragmentSlideshowBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    private lateinit var bookmarkAdapter: BookmarkAdapter
    private var actionMode: ActionMode? = null
    private val selectedBookmarks = mutableSetOf<Bookmark>()
    private var isBottomBarVisible = true

    private var searchQuery = ""
    private var testamentFilter = "ALL" // "ALL", "OT", "NT"
    private var rawBookmarksList = listOf<Bookmark>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSlideshowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bookmarkAdapter = BookmarkAdapter(
            onBookmarkClick = { bookmark ->
                viewModel.updateVersion(bookmark.version)
                viewModel.updateSelection(bookmark.book, bookmark.chapter, bookmark.verseId, bookmark.verse)
                findNavController().navigate(R.id.nav_home)
            },
            onLongClick = { bookmark ->
                if (actionMode == null) {
                    actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(actionModeCallback)
                    toggleSelection(bookmark)
                }
            },
            onSelectionChange = { bookmark, _ -> toggleSelection(bookmark) }
        )

        binding.recyclerviewBookmarks.adapter = bookmarkAdapter
        
        viewModel.allBookmarks.observe(viewLifecycleOwner) { bookmarks ->
            rawBookmarksList = bookmarks ?: emptyList()
            applyFilter()
        }

        // Sync Font Settings to Bookmarks
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fontSettings.collectLatest { settings ->
                    bookmarkAdapter.setFontSettings(settings)
                }
            }
        }

        setupSearchAndFilterUI()
        setupScrollHiding()
    }

    private fun setupSearchAndFilterUI() {
        binding.editSearchBookmark.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString() ?: ""
                binding.btnClearSearch.isVisible = searchQuery.isNotEmpty()
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.editSearchBookmark.text.clear()
        }

        binding.btnFilterAll.setOnClickListener {
            testamentFilter = "ALL"
            updateFilterButtonStates()
            applyFilter()
        }

        binding.btnFilterOt.setOnClickListener {
            testamentFilter = if (testamentFilter == "OT") "ALL" else "OT"
            updateFilterButtonStates()
            applyFilter()
        }

        binding.btnFilterNt.setOnClickListener {
            testamentFilter = if (testamentFilter == "NT") "ALL" else "NT"
            updateFilterButtonStates()
            applyFilter()
        }

        updateFilterButtonStates()
    }

    private fun updateFilterButtonStates() {
        binding.btnFilterAll.alpha = if (testamentFilter == "ALL") 1.0f else 0.5f
        binding.btnFilterOt.alpha = if (testamentFilter == "OT") 1.0f else 0.5f
        binding.btnFilterNt.alpha = if (testamentFilter == "NT") 1.0f else 0.5f
    }

    private fun applyFilter() {
        val filtered = rawBookmarksList.filter { bookmark ->
            val matchesTestament = when (testamentFilter) {
                "OT" -> isOldTestament(bookmark.book)
                "NT" -> isNewTestament(bookmark.book)
                else -> true
            }

            val query = searchQuery.trim().lowercase()
            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                bookmark.book.lowercase().contains(query) ||
                "${bookmark.book} ${bookmark.chapter}:${bookmark.verse}".lowercase().contains(query) ||
                bookmark.text.lowercase().contains(query) ||
                bookmark.note.lowercase().contains(query) ||
                bookmark.version.lowercase().contains(query)
            }

            matchesTestament && matchesQuery
        }

        bookmarkAdapter.submitList(filtered)
        updateEmptyState(filtered.isEmpty())
    }

    private fun isOldTestament(book: String): Boolean {
        val otBooks = listOf(
            "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Josua-I", "Josua", "Joshua",
            "Roreltute", "Ruthi", "I - Samuela", "II - Samuela", "I - Lalte", "II - Lalte",
            "I - Chronicles", "II - Chronicles", "EZRA", "Nehemia", "Estheri", "Joba",
            "Sam", "Thufingte", "Thuhriltu", "Hla Thlan Khawmte", "Isaia", "Jeremia",
            "Ṭah hla", "Ezekiela", "Daniela", "Hosea", "Joela", "Amosa", "Obadia",
            "Jona", "Mika", "Nahuma", "Habakuka", "Zephania", "Hagaia", "Zakaria", "Malakia"
        )
        return otBooks.any { it.equals(book.trim(), ignoreCase = true) }
    }

    private fun isNewTestament(book: String): Boolean {
        val ntBooks = listOf(
            "Matthaia", "Marka", "Luka", "Johana", "Tirhkohte", "Rom", "1 Korinth",
            "2 Korinth", "Galatia", "Ephesi", "Philippi", "Kolossa", "1 Thessalonika",
            "2 Thessalonika", "1 Timothea", "2 Timothea", "Tita", "Philemona", "Hebrai",
            "Jakoba", "1 Petera", "2 Petera", "1 Johana", "2 Johana", "3 Johana", "Juda",
            "Thupuan"
        )
        return ntBooks.any { it.equals(book.trim(), ignoreCase = true) }
    }

    private fun setupScrollHiding() {
        val mainActivity = activity as? MainActivity ?: return
        val bottomContainer = mainActivity.binding.appBarMain.bottomContainer
        val windowInsetsController = WindowInsetsControllerCompat(requireActivity().window, requireActivity().window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy > 30 && isBottomBarVisible) {
                    isBottomBarVisible = false
                    windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
                    val transition = androidx.transition.Slide(android.view.Gravity.BOTTOM)
                    transition.duration = 250
                    transition.addTarget(bottomContainer)
                    androidx.transition.TransitionManager.beginDelayedTransition(bottomContainer.parent as ViewGroup, transition)
                    bottomContainer.visibility = View.GONE
                } else if (dy < -30 && !isBottomBarVisible) {
                    isBottomBarVisible = true
                    windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
                    val transition = androidx.transition.Slide(android.view.Gravity.BOTTOM)
                    transition.duration = 250
                    transition.addTarget(bottomContainer)
                    androidx.transition.TransitionManager.beginDelayedTransition(bottomContainer.parent as ViewGroup, transition)
                    bottomContainer.visibility = View.VISIBLE
                }
            }
        }
        binding.recyclerviewBookmarks.addOnScrollListener(listener)
    }

    private fun toggleSelection(bookmark: Bookmark) {
        if (selectedBookmarks.contains(bookmark)) selectedBookmarks.remove(bookmark) else selectedBookmarks.add(bookmark)
        bookmarkAdapter.toggleSelection(bookmark)
        if (selectedBookmarks.isEmpty()) {
            actionMode?.finish()
        } else {
            actionMode?.title = "${selectedBookmarks.size} selected"
            bookmarkAdapter.setSelectionMode(true)
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_bookmark_selection, menu)
            bookmarkAdapter.setSelectionMode(true)
            return true
        }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_delete_selected -> {
                    showDeleteConfirmationDialog("He Bookmark ${selectedBookmarks.size} hi i delete duh tak tak em?") {
                        selectedBookmarks.forEach { viewModel.deleteBookmark(it) }
                        mode.finish()
                    }
                    true
                }
                R.id.action_select_all -> {
                    val allBookmarks = viewModel.allBookmarks.value ?: return true
                    selectedBookmarks.clear(); selectedBookmarks.addAll(allBookmarks)
                    bookmarkAdapter.selectAll(); mode.title = "${selectedBookmarks.size} selected"
                    true
                }
                else -> false
            }
        }
        override fun onDestroyActionMode(mode: ActionMode) {
            bookmarkAdapter.setSelectionMode(false); selectedBookmarks.clear(); actionMode = null
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.emptyState.textEmptyMessage.text = if (searchQuery.isNotEmpty() || testamentFilter != "ALL") "Chutiang Bookmark a awm lo" else "Bookmark a la awm lo"
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

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
