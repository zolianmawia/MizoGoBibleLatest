package com.zoliana.khampat.mizobible.ui.transform

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.FragmentBibleGridBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BibleGridDialog : BottomSheetDialogFragment() {

    private var _binding: FragmentBibleGridBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    private var maxChapters = 150
    private var currentBook = ""
    private var allBooksList = listOf<String>()

    private var isOldTestamentExpanded = false
    private var isNewTestamentExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_MizoGoBible_BottomSheet)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                it.setBackgroundResource(android.R.color.transparent)

                val width = resources.displayMetrics.widthPixels
                val tabletWidth = (600 * resources.displayMetrics.density).toInt()
                if (width > tabletWidth) {
                    val params = it.layoutParams
                    params.width = tabletWidth
                    it.layoutParams = params
                }
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBibleGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handle top and bottom insets for Edge-to-Edge (status bar & navigation bar)
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }

        binding.btnClose.setOnClickListener { dismiss() }
        binding.layoutBookSelector.setOnClickListener { showBookSelectionDialog() }
        loadInitialData()
    }

    private fun loadInitialData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.repository.getAllBooks(viewModel.currentVersion.value)
                .collectLatest { books ->
                    allBooksList = books
                    currentBook = viewModel.currentBook.value
                    binding.textSelectedBook.text = currentBook
                    updateMaxChapters(currentBook)
                }
        }
    }

    private fun showBookSelectionDialog() {
        val ctx = context ?: return
        val items = mutableListOf<BookListItem>()

        fun populateItems() {
            items.clear()
            if (allBooksList.size >= 66) {
                val oldTestament = allBooksList.take(39)
                val newTestament = allBooksList.drop(39)
                items.add(BookListItem.Header("THUTHLUNG HLUI", isOldTestamentExpanded))
                if (isOldTestamentExpanded) items.addAll(oldTestament.map {
                    BookListItem.Book(it, true)
                })
                items.add(BookListItem.Header("THUTHLUNG THAR", isNewTestamentExpanded))
                if (isNewTestamentExpanded) items.addAll(newTestament.map {
                    BookListItem.Book(it, false)
                })
            } else {
                items.addAll(allBooksList.map { BookListItem.Book(it, true) })
            }
        }

        populateItems()
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_book_list, null)
        val listView = dialogView.findViewById<ListView>(R.id.list_books)
        val adapter = object : ArrayAdapter<BookListItem>(ctx, 0, items) {
            override fun getViewTypeCount(): Int = 2
            override fun getItemViewType(position: Int): Int =
                if (getItem(position) is BookListItem.Header) 0 else 1

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return when (val item = getItem(position)) {
                    is BookListItem.Header -> {
                        val view = convertView ?: LayoutInflater.from(ctx)
                            .inflate(R.layout.item_book_header, parent, false)
                        val textView = view.findViewById<TextView>(R.id.text_header)
                        textView.text = item.title
                        if (item.title.contains("HLUI")) textView.setTextColor(
                            MaterialColors.getColor(textView, androidx.appcompat.R.attr.colorPrimary)
                        )
                        else if (item.title.contains("THAR")) textView.setTextColor(
                            MaterialColors.getColor(textView, com.google.android.material.R.attr.colorTertiary)
                        )
                        view
                    }

                    is BookListItem.Book -> {
                        val view = convertView ?: LayoutInflater.from(ctx)
                            .inflate(android.R.layout.simple_list_item_1, parent, false)
                        if (view is TextView) {
                            view.text = item.name
                            val colorAttr =
                                if (item.isOldTestament) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorTertiary
                            view.setTextColor(MaterialColors.getColor(view, colorAttr))
                            view.setPadding(48, 40, 48, 40)
                        }
                        view
                    }

                    else -> View(ctx)
                }
            }
        }
        listView.adapter = adapter
        val dialog = AlertDialog.Builder(ctx).setView(dialogView).create()
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = items[position]
            if (item is BookListItem.Book) {
                currentBook = item.name
                binding.textSelectedBook.text = currentBook
                updateMaxChapters(currentBook)
                dialog.dismiss()
            } else if (item is BookListItem.Header) {
                if (item.title.contains("HLUI")) isOldTestamentExpanded = !isOldTestamentExpanded
                else if (item.title.contains("THAR")) isNewTestamentExpanded = !isNewTestamentExpanded
                populateItems()
                adapter.notifyDataSetChanged()
            }
        }
        dialog.show()
        (activity as? MainActivity)?.limitDialogWidth(dialog)
    }

    private fun updateMaxChapters(book: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val count = viewModel.repository.getChapterCountSync(viewModel.currentVersion.value, book)
            maxChapters = count ?: 1
            setupChapterGrid()
        }
    }

    private fun setupChapterGrid() {
        val chapters = (1..maxChapters).map { it.toString() }
        val adapter = ArrayAdapter(requireContext(), R.layout.item_chapter_grid, R.id.text_chapter, chapters)
        binding.gridViewChapters.adapter = adapter
        binding.gridViewChapters.setOnItemClickListener { _, _, position, _ ->
            val selectedChapter = chapters[position].toInt()
            viewLifecycleOwner.lifecycleScope.launch {
                val version = viewModel.currentVersion.value
                val verseId = viewModel.repository.getVerseId(version, currentBook, selectedChapter, "1")
                viewModel.updateSelection(currentBook, selectedChapter, verseId)
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
