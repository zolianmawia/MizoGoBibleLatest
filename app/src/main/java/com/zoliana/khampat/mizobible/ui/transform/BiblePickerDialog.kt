package com.zoliana.khampat.mizobible.ui.transform

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.*
import com.zoliana.khampat.mizobible.databinding.FragmentBiblePickerBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Sealed class for Book List Items (Header or Book)
sealed class BookListItem {
    data class Header(val title: String, val isExpanded: Boolean = false) : BookListItem()
    data class Book(val name: String, val isOldTestament: Boolean) : BookListItem()
}

/**
 * This Dialog Fragment is for the "DIALER" style of chapter/verse selection.
 * It uses fragment_bible_picker.xml layout.
 * All GridView related logic has been removed from this file.
 */
class BiblePickerDialog : BottomSheetDialogFragment() {

    private var _binding: FragmentBiblePickerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    private var isChapterSelection = true
    private var inputChapter = ""
    private var inputVerse = ""

    private var maxChapters = 150
    private var maxVerses = 176

    private var allBooksList = listOf<String>()
    private var currentBook = ""

    private var isOldTestamentExpanded = false
    private var isNewTestamentExpanded = false

    companion object {
        /**
         * This function decides which dialog to show based on user's preference.
         */
        fun newInstance(context: Context): DialogFragment {
            val prefs = context.getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)
            val style = prefs.getString("chapter_selector_style", "Dialer")
            return if (style == "Grid") {
                BibleGridDialog()
            } else {
                BiblePickerDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_MizoGoBible_BottomSheet)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet =
                dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
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
        _binding = FragmentBiblePickerBinding.inflate(inflater, container, false)
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

        setupKeypad()
        setupSelectionBoxes()
        loadBooks()
        binding.btnClose.setOnClickListener { dismiss() }
        binding.layoutBookSelector.setOnClickListener { showBookSelectionDialog() }
        binding.btnOk.setOnClickListener {
            if (currentBook.isEmpty()) return@setOnClickListener
            val chapter = inputChapter.toIntOrNull() ?: 1
            val verseStr = if (inputVerse.isEmpty()) "1" else inputVerse
            viewLifecycleOwner.lifecycleScope.launch {
                val version = viewModel.currentVersion.value
                val verseId =
                    viewModel.repository.getVerseId(version, currentBook, chapter, verseStr)
                viewModel.updateSelection(currentBook, chapter, verseId)
                dismiss()
            }
        }
        updateDisplay()
    }

    private fun loadBooks() {
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
        val showOld = binding.cbOldTestament.isChecked
        val showNew = binding.cbNewTestament.isChecked
        val items = mutableListOf<BookListItem>()

        fun populateItems() {
            items.clear()
            if (allBooksList.size >= 66) {
                val oldTestament = allBooksList.take(39)
                val newTestament = allBooksList.drop(39)
                if (showOld) {
                    items.add(BookListItem.Header("THUTHLUNG HLUI", isOldTestamentExpanded))
                    if (isOldTestamentExpanded) items.addAll(oldTestament.map {
                        BookListItem.Book(
                            it,
                            true
                        )
                    })
                }
                if (showNew) {
                    items.add(BookListItem.Header("THUTHLUNG THAR", isNewTestamentExpanded))
                    if (isNewTestamentExpanded) items.addAll(newTestament.map {
                        BookListItem.Book(
                            it,
                            false
                        )
                    })
                }
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
                            MaterialColors.getColor(
                                textView,
                                androidx.appcompat.R.attr.colorPrimary
                            )
                        )
                        else if (item.title.contains("THAR")) textView.setTextColor(
                            MaterialColors.getColor(
                                textView,
                                com.google.android.material.R.attr.colorTertiary
                            )
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
                else if (item.title.contains("THAR")) isNewTestamentExpanded =
                    !isNewTestamentExpanded
                populateItems()
                adapter.notifyDataSetChanged()
            }
        }
        dialog.show()
        (activity as? MainActivity)?.limitDialogWidth(dialog)
    }

    private fun updateMaxChapters(book: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val count =
                viewModel.repository.getChapterCountSync(viewModel.currentVersion.value, book)
            maxChapters = count ?: 150
            if (inputChapter.isNotEmpty() && (inputChapter.toIntOrNull() ?: 0) > maxChapters) {
                inputChapter = maxChapters.toString()
                updateDisplay()
            }
            updateMaxVerses()
        }
    }

    private fun updateMaxVerses() {
        if (currentBook.isEmpty()) return
        val chapter = inputChapter.toIntOrNull() ?: 1
        viewLifecycleOwner.lifecycleScope.launch {
            val count = viewModel.repository.getVerseCountSync(
                viewModel.currentVersion.value,
                currentBook,
                chapter
            )
            maxVerses = if (count > 0) count else 1
            if (inputVerse.isNotEmpty() && (inputVerse.toIntOrNull() ?: 0) > maxVerses) {
                inputVerse = maxVerses.toString()
                updateDisplay()
            }
        }
    }

    private fun setupSelectionBoxes() {
        binding.layoutChapterBox.setOnClickListener {
            isChapterSelection = true
            updateBoxHighlight()
        }
        binding.layoutVerseBox.setOnClickListener {
            isChapterSelection = false
            updateBoxHighlight()
        }
        updateBoxHighlight()
    }

    private fun updateBoxHighlight() {
        if (isChapterSelection) {
            binding.layoutChapterBox.setBackgroundResource(R.drawable.bg_selection_box)
            binding.layoutVerseBox.setBackgroundResource(0)
        } else {
            binding.layoutChapterBox.setBackgroundResource(0)
            binding.layoutVerseBox.setBackgroundResource(R.drawable.bg_selection_box)
        }
    }

    private fun setupKeypad() {
        binding.btn1.setOnClickListener { appendDigit("1") }
        binding.btn2.setOnClickListener { appendDigit("2") }
        binding.btn3.setOnClickListener { appendDigit("3") }
        binding.btn4.setOnClickListener { appendDigit("4") }
        binding.btn5.setOnClickListener { appendDigit("5") }
        binding.btn6.setOnClickListener { appendDigit("6") }
        binding.btn7.setOnClickListener { appendDigit("7") }
        binding.btn8.setOnClickListener { appendDigit("8") }
        binding.btn9.setOnClickListener { appendDigit("9") }
        binding.btn0.setOnClickListener { appendDigit("0") }
        binding.btnBackspace.setOnClickListener { backspace() }
    }

    private fun appendDigit(digit: String) {
        if (isChapterSelection) {
            val nextVal = (inputChapter + digit).toIntOrNull() ?: 0
            if (nextVal <= maxChapters) {
                if (inputChapter.length < 3) inputChapter += digit
            } else {
                inputChapter = maxChapters.toString()
            }
            updateMaxVerses()
        } else {
            val nextVal = (inputVerse + digit).toIntOrNull() ?: 0
            if (nextVal <= maxVerses) {
                if (inputVerse.length < 3) inputVerse += digit
            } else {
                inputVerse = maxVerses.toString()
            }
        }
        updateDisplay()
    }

    private fun backspace() {
        if (isChapterSelection) {
            if (inputChapter.isNotEmpty()) inputChapter =
                inputChapter.dropLast(1); updateMaxVerses()
        } else {
            if (inputVerse.isNotEmpty()) inputVerse = inputVerse.dropLast(1)
        }
        updateDisplay()
    }

    private fun updateDisplay() {
        binding.textSelectedChapter.text = if (inputChapter.isEmpty()) "0" else inputChapter
        binding.textSelectedVerse.text = if (inputVerse.isEmpty()) "0" else inputVerse
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
