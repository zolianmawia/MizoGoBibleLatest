package com.zoliana.khampat.mizobible.ui.search

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.BibleVerse
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.FragmentSearchBinding
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModel
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    private var currentFilter = "All"
    private var selectedBook: String = "All Books"
    private lateinit var searchAdapter: SearchAdapter
    private lateinit var historyAdapter: SearchHistoryAdapter
    private var searchJob: Job? = null

    private val PREFS_NAME = "search_history_prefs"
    private val HISTORY_KEY = "search_history"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        searchAdapter = SearchAdapter { verse ->
            saveToHistory(viewModel.searchQuery.value)
            viewModel.updateSelection(verse.book ?: "", verse.chapter ?: 1, verse.id)
            findNavController().navigate(R.id.action_nav_search_to_nav_home)
        }
        binding?.recyclerSearchResults?.adapter = searchAdapter

        setupHistory()
        setupSwipeToDismiss()

        // Restore Search State
        binding?.editSearch?.setText(viewModel.searchQuery.value)
        binding?.btnFuzzyToggle?.isChecked = viewModel.searchFuzzyEnabled.value
        searchAdapter.submitListWithQuery(viewModel.searchResults.value, viewModel.searchQuery.value)
        binding?.textSearchCount?.text = viewModel.searchResults.value.size.toString()
        updateEmptyState(viewModel.searchResults.value.isEmpty() && viewModel.searchQuery.value.isNotEmpty())
        if (viewModel.searchQuery.value.isEmpty()) showHistory() else hideHistory()

        ViewCompat.setOnApplyWindowInsetsListener(binding!!.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            // Search toolbar hi a chung berah (status bar hnuaiah) kan dah ang
            // AppBarLayout behavior kan paih tak avangin status bar height kan hman a ngai leh a ni
            binding?.btnDrawer?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = systemBars.top + (8 * resources.displayMetrics.density).toInt()
            }
            
            binding?.layoutFilter?.let { filterBar ->
                filterBar.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    bottomMargin = if (ime.bottom > 0) {
                        ime.bottom + (8 * resources.displayMetrics.density).toInt()
                    } else {
                        systemBars.bottom + (16 * resources.displayMetrics.density).toInt()
                    }
                }
            }
            insets
        }

        binding?.btnFuzzyToggle?.setOnCheckedChangeListener { _, isChecked ->
            viewModel.searchFuzzyEnabled.value = isChecked
            if (viewModel.searchQuery.value.isNotEmpty()) performSearch()
        }

        binding?.editSearch?.postDelayed({
            _binding?.let { b ->
                if (viewModel.searchQuery.value.isEmpty()) {
                    b.editSearch.requestFocus()
                    val imm =
                        context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(b.editSearch, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }, 200)

        binding?.editSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                viewModel.searchQuery.value = query
                binding?.btnClearSearch?.isVisible = query.isNotEmpty()
                if (query.isEmpty()) {
                    viewModel.searchResults.value = emptyList()
                    clearResults()
                    showHistory()
                } else {
                    hideHistory()
                    performSearch()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        binding?.editSearch?.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (viewModel.searchQuery.value.isNotEmpty()) saveToHistory(viewModel.searchQuery.value)
                val imm =
                    context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else false
        }

        binding?.btnClearSearch?.setOnClickListener { binding?.editSearch?.text?.clear() }
        setupFilters()
        binding?.btnSelectBook?.setOnClickListener { showBookSelectionDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentVersion.collectLatest { version ->
                binding?.btnSearchVersion?.text = getVersionDisplayName(version)
                if (viewModel.searchQuery.value.isNotEmpty()) performSearch()
            }
        }

        binding?.btnSearchVersion?.setOnClickListener {
            showVersionSelectionDialog()
        }

        binding?.btnDrawer?.setOnClickListener {
            val mainActivity = (activity as? MainActivity)
            findNavController().popBackStack()
            mainActivity?.binding?.drawerLayout?.openDrawer(GravityCompat.START)
        }
    }

    private fun showVersionSelectionDialog() {
        val versions = viewModel.availableVersions.value ?: emptyList()
        val versionPairs = versions.filter {
            val up = it.uppercase().trim()
            !up.startsWith("HEAD") && !up.startsWith("TITL") && !up.startsWith("SUB") &&
                    !up.startsWith("PER") && up != "VERSE"
        }.map { it to getFullVersionName(it) }
            .distinctBy { it.second }
            .sortedBy { if (it.first.uppercase() == "MZOV") "" else it.second.lowercase() }

        val names = versionPairs.map { it.second }.toMutableList()
        names.add("Download More...")

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Version")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == names.size - 1) {
                    (requireActivity() as? MainActivity)?.showDownloadableVersionsDialog()
                } else {
                    val original = versionPairs[which].first
                    if (viewModel.currentVersion.value != original) {
                        viewModel.updateVersion(original)
                    }
                }
            }.show()
        (requireActivity() as? MainActivity)?.limitDialogWidth(dialog)
    }

    private fun getVersionDisplayName(v: String): String = when (v.lowercase().trim()) {
        "verse", "mizo go bible", "mgb", "mzov", "pericope", "percope" -> "MzOV"
        "kjv", "kjb" -> "KJV"
        "hindi" -> "HINDI"
        "greek", "greek (grk)" -> "GREEK (GRK)"
        "burmesebible" -> "MYJ"
        "niv" -> "NIV"
        "mzcl" -> "MzCL"
        else -> v.uppercase()
    }

    private fun getFullVersionName(v: String): String = when (v.lowercase().trim()) {
        "verse", "mizo go bible", "mgb", "mzov", "pericope", "percope" -> "Mizo Old Version (MzOV)"
        "kjv", "kjb" -> "King James Version (KJV)"
        "hindi" -> "Hindi Bible (HINDI)"
        "greek", "greek (grk)" -> "Greek Bible (GREEK)"
        "burmesebible" -> "Myanmar Judson (MYJ)"
        "niv" -> "New International Version (NIV)"
        "mzcl" -> "Mizo Common Language (MzCL)"
        else -> v.uppercase()
    }

    private fun setupSwipeToDismiss() {
        var startY = 0f
        binding?.dismissHandle?.setOnTouchListener { _, event ->
            val root = binding?.root ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY; true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - startY
                    if (deltaY < 0) root.translationY = deltaY
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val deltaY = event.rawY - startY
                    if (deltaY < -200) findNavController().popBackStack() else root.animate().translationY(0f)
                        .setDuration(200).start()
                    true
                }

                else -> false
            }
        }
    }

    private fun setupHistory() {
        val history = getHistory()
        historyAdapter = SearchHistoryAdapter(
            history,
            onItemClick = { query ->
                binding?.editSearch?.setText(query)
                binding?.editSearch?.setSelection(query.length)
                performSearch()
            },
            onDeleteClick = { query -> deleteFromHistory(query) }
        )
        binding?.recyclerSearchHistory?.adapter = historyAdapter
        binding?.btnClearHistory?.setOnClickListener { clearAllHistory() }
    }

    private fun showHistory() {
        val history = getHistory()
        if (history.isNotEmpty()) {
            binding?.layoutSearchHistory?.visibility = View.VISIBLE
            binding?.recyclerSearchResults?.visibility = View.GONE
            binding?.emptyState?.layoutEmptyState?.visibility = View.GONE
            historyAdapter.updateHistory(history)
        } else hideHistory()
    }

    private fun hideHistory() {
        binding?.layoutSearchHistory?.visibility = View.GONE
        binding?.recyclerSearchResults?.visibility = View.VISIBLE
    }

    private fun saveToHistory(query: String) {
        if (query.length < 2) return
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val history = getHistory().toMutableList()
        history.remove(query)
        history.add(0, query)
        val limitedHistory = if (history.size > 15) history.take(15) else history
        prefs.edit().putString(HISTORY_KEY, limitedHistory.joinToString(";;;")).apply()
    }

    private fun getHistory(): List<String> {
        val ctx = context ?: return emptyList()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyStr = prefs.getString(HISTORY_KEY, "") ?: ""
        return if (historyStr.isEmpty()) emptyList() else historyStr.split(";;;")
    }

    private fun deleteFromHistory(query: String) {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val history = getHistory().toMutableList()
        history.remove(query)
        prefs.edit().putString(HISTORY_KEY, history.joinToString(";;;")).apply()
        historyAdapter.updateHistory(history)
        if (history.isEmpty()) hideHistory()
    }

    private fun clearAllHistory() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(HISTORY_KEY).apply()
        historyAdapter.updateHistory(emptyList())
        hideHistory()
    }

    private fun showBookSelectionDialog() {
        val ctx = context ?: return
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_book_selection, null)
        val gridView = dialogView.findViewById<GridView>(R.id.grid_books)

        lifecycleScope.launch {
            val version = viewModel.currentVersion.value
            val books = viewModel.repository.getAllBooks(version).first().toMutableList()
            books.add(0, "All Books")

            val adapter = object : ArrayAdapter<String>(ctx, R.layout.item_book_grid, books) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context)
                        .inflate(R.layout.item_book_grid, parent, false)
                    val bookName = getItem(position) ?: ""
                    val card = view.findViewById<MaterialCardView>(R.id.card_book)
                    val textView = view.findViewById<TextView>(R.id.text_book_name)
                    textView.text = bookName
                    val isDarkMode =
                        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    when {
                        bookName == "All Books" -> {
                            card.setCardBackgroundColor(context.getColor(android.R.color.transparent))
                            card.strokeWidth =
                                (1 * context.resources.displayMetrics.density).toInt()
                            card.strokeColor =
                                android.graphics.Color.parseColor(if (isDarkMode) "#43474E" else "#EBE2CF")
                        }

                        isOldTestament(bookName) -> {
                            val color =
                                if (isDarkMode) android.graphics.Color.parseColor("#3D332D") else android.graphics.Color.parseColor(
                                    "#F5EFEB"
                                )
                            card.setCardBackgroundColor(color); card.strokeWidth = 0
                        }

                        isNewTestament(bookName) -> {
                            val color =
                                if (isDarkMode) android.graphics.Color.parseColor("#2D363D") else android.graphics.Color.parseColor(
                                    "#E1E8EF"
                                )
                            card.setCardBackgroundColor(color); card.strokeWidth = 0
                        }

                        else -> {
                            card.setCardBackgroundColor(context.getColor(android.R.color.transparent))
                            card.strokeWidth =
                                (1 * context.resources.displayMetrics.density).toInt()
                            card.strokeColor =
                                android.graphics.Color.parseColor(if (isDarkMode) "#43474E" else "#EBE2CF")
                        }
                    }
                    return view
                }
            }
            gridView.adapter = adapter
            val dialog = AlertDialog.Builder(ctx).setView(dialogView).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            gridView.setOnItemClickListener { _, _, position, _ ->
                selectedBook = books[position]
                if (selectedBook != "All Books") currentFilter = "All"
                binding?.btnSelectBook?.text =
                    if (selectedBook == "All Books") "All book" else selectedBook
                updateFilterUI(); if (viewModel.searchQuery.value.isNotEmpty()) performSearch(); dialog.dismiss()
            }
            dialog.show()
            (requireActivity() as? MainActivity)?.limitDialogWidth(dialog)
        }
    }

    private fun setupFilters() {
        binding?.btnFilterOt?.setOnClickListener {
            currentFilter = if (currentFilter == "OT") "All" else "OT"
            if (currentFilter == "OT") {
                selectedBook = "All Books"; binding?.btnSelectBook?.text = "All book"
            }
            updateFilterUI(); performSearch()
        }
        binding?.btnFilterNt?.setOnClickListener {
            currentFilter = if (currentFilter == "NT") "All" else "NT"
            if (currentFilter == "NT") {
                selectedBook = "All Books"; binding?.btnSelectBook?.text = "All book"
            }
            updateFilterUI(); performSearch()
        }
        updateFilterUI()
    }

    private fun updateFilterUI() {
        val isBookSelected = selectedBook != "All Books"
        binding?.btnFilterOt?.apply {
            isEnabled = !isBookSelected; alpha =
            if (isBookSelected) 0.2f else (if (currentFilter == "OT") 1.0f else 0.5f)
        }
        binding?.btnFilterNt?.apply {
            isEnabled = !isBookSelected; alpha =
            if (isBookSelected) 0.2f else (if (currentFilter == "NT") 1.0f else 0.5f)
        }
        binding?.editSearch?.hint = when {
            isBookSelected -> "$selectedBook chhunga mi chiah thlang rawh"; currentFilter == "OT" -> "Thuthlung Hlui a mi chiah zawng rawh"; currentFilter == "NT" -> "Thuthlung Thar a mi chiah zawng rawh"; else -> "Bible zawng rawh..."
        }
    }

    private fun clearResults() {
        searchJob?.cancel()
        viewModel.searchResults.value = emptyList()
        searchAdapter.submitListWithQuery(emptyList(), "")
        binding?.textSearchCount?.text = "0"
        updateEmptyState(isEmpty = false)
    }

    private fun performSearch() {
        val query = viewModel.searchQuery.value
        if (query.isEmpty()) {
            clearResults(); return
        }
        searchJob?.cancel()
        val version = viewModel.currentVersion.value
        val normalizedQuery = query.normalizeMizo()
        val queryWithoutSpace = normalizedQuery.replace(Regex("[^a-z0-9]"), "")

        if (queryWithoutSpace.isEmpty() && !query.any { it.isDigit() }) return

        val repositoryBookFilter = if (selectedBook != "All Books") selectedBook else null
        val fuzzyPart =
            if (normalizedQuery.length >= 5) normalizedQuery.substring(2) else normalizedQuery

        val bookFilterList = when (currentFilter) {
            "OT" -> getOldTestamentBooks()
            "NT" -> getNewTestamentBooks()
            else -> null
        }

        searchJob = lifecycleScope.launch {
            val referenceResultsFlow: Flow<List<BibleVerse>> = flow {
                val bookPartPattern = "(.+?)"
                val bookChapterVersePattern = Regex("^$bookPartPattern\\s*(\\d+)[.:](\\d+)$")
                val bookChapterPattern = Regex("^$bookPartPattern\\s*(\\d+)$")
                val chapterVersePattern = Regex("^(\\d+)[.:](\\d+)$")
                val chapterOnlyPattern = Regex("^(\\d+)$")
                try {
                    when {
                        bookChapterVersePattern.matches(query) -> {
                            val match = bookChapterVersePattern.find(query)!!
                            val typedBook = match.groupValues[1].trim()
                            emitAll(
                                viewModel.repository.searchByBookChapterVerse(
                                    version,
                                    getMappedBookName(typedBook),
                                    getMappedBookName(typedBook),
                                    match.groupValues[2].toIntOrNull() ?: 1,
                                    match.groupValues[3]
                                )
                            )
                        }

                        bookChapterPattern.matches(query) -> {
                            val match = bookChapterPattern.find(query)!!
                            val typedBook = match.groupValues[1].trim()
                            emitAll(
                                viewModel.repository.searchByBookAndChapter(
                                    version,
                                    getMappedBookName(typedBook),
                                    getMappedBookName(typedBook),
                                    match.groupValues[2].toIntOrNull() ?: 1
                                )
                            )
                        }

                        chapterVersePattern.matches(query) -> {
                            val match = chapterVersePattern.find(query)!!
                            if (selectedBook != "All Books") emitAll(
                                viewModel.repository.searchByBookChapterVerse(
                                    version,
                                    selectedBook,
                                    selectedBook,
                                    match.groupValues[1].toIntOrNull() ?: 1,
                                    match.groupValues[2]
                                )
                            )
                            else emitAll(
                                viewModel.repository.searchByReference(
                                    version,
                                    match.groupValues[1].toIntOrNull() ?: 1,
                                    match.groupValues[2]
                                )
                            )
                        }

                        chapterOnlyPattern.matches(query) -> {
                            val match = chapterOnlyPattern.find(query)!!
                            if (selectedBook != "All Books") emitAll(
                                viewModel.repository.searchByBookAndChapter(
                                    version,
                                    selectedBook,
                                    selectedBook,
                                    match.groupValues[1].toIntOrNull() ?: 1
                                )
                            )
                            else emit(emptyList())
                        }

                        else -> {
                            if (!query.any { it.isDigit() }) {
                                val mapped = getMappedBookName(query)
                                if (mapped.lowercase() == query.lowercase().trim()) emitAll(
                                    viewModel.repository.searchByBookOnly(version, mapped, mapped)
                                )
                                else emit(emptyList())
                            } else emit(emptyList())
                        }
                    }
                } catch (e: Exception) {
                    emit(emptyList())
                }
            }

            val textSearchFlow = if (query.length >= 2 && queryWithoutSpace.isNotEmpty()) {
                if (viewModel.searchFuzzyEnabled.value) {
                    viewModel.repository.searchBroad(
                        version,
                        normalizedQuery,
                        queryWithoutSpace,
                        fuzzyPart,
                        repositoryBookFilter,
                        bookFilterList
                    )
                } else {
                    viewModel.repository.searchBible(
                        version,
                        normalizedQuery,
                        queryWithoutSpace,
                        repositoryBookFilter,
                        bookFilterList
                    )
                }
            } else {
                flowOf(emptyList())
            }

            combine(referenceResultsFlow, textSearchFlow) { refResults, textResults ->
                val ranked =
                    textResults.filter { it.type?.lowercase() !in listOf("pericope", "percope") }
                        .sortedWith(compareByDescending<BibleVerse> {
                            when {
                                it.text?.contains(query, true) == true -> 5000
                                it.normalizedText?.contains(normalizedQuery, true) == true -> 4000
                                it.searchText?.contains(queryWithoutSpace, true) == true -> 3000
                                else -> 100
                            }
                        }.thenBy { it.id })
                var combined = (refResults + ranked).distinctBy { it.id }
                if (selectedBook != "All Books") {
                    combined = combined.filter {
                        it.book?.trim()?.equals(selectedBook.trim(), ignoreCase = true) == true
                    }
                }
                combined
            }
                .flowOn(Dispatchers.Default) // Perform heavy ranking logic in background
                .collectLatest { filtered ->
                    _binding?.let { b ->
                        viewModel.searchResults.value = filtered
                        searchAdapter.submitListWithQuery(filtered, query)
                        b.textSearchCount.text = filtered.size.toString()
                        updateEmptyState(filtered.isEmpty())
                    }
                }
        }
    }

    private fun getMappedBookName(input: String): String {
        val mapping = mapOf(
            "genesis" to "Genesis",
            "exodus" to "Exodus",
            "leviticus" to "Leviticus",
            "numbers" to "Numbers",
            "deuteronomy" to "Deuteronomy",
            "josuai" to "Josua-I",
            "josua" to "Josua-I",
            "joshua" to "Josua-I",
            "roreltute" to "Roreltute",
            "ruthi" to "Ruthi",
            "isamuela" to "I - Samuela",
            "samuela1" to "I - Samuela",
            "1samuela" to "I - Samuela",
            "iisamuela" to "II - Samuela",
            "samuela2" to "II - Samuela",
            "2samuela" to "II - Samuela",
            "ilalte" to "I - Lalte",
            "lalte1" to "I - Lalte",
            "1lalte" to "I - Lalte",
            "iilalte" to "II - Lalte",
            "lalte2" to "II - Lalte",
            "2lalte" to "II - Lalte",
            "ichronicles" to "I - Chronicles",
            "chronicles1" to "I - Chronicles",
            "1chronicles" to "I - Chronicles",
            "iichronicles" to "II - Chronicles",
            "chronicles2" to "II - Chronicles",
            "2chronicles" to "II - Chronicles",
            "ezra" to "EZRA",
            "nehemia" to "Nehemia",
            "estheri" to "Estheri",
            "esther" to "Estheri",
            "joba" to "Joba",
            "job" to "Joba",
            "sam" to "Sam",
            "psalms" to "Sam",
            "psalm" to "Sam",
            "hlate" to "Sam",
            "thufingte" to "Thufingte",
            "proverbs" to "Thufingte",
            "thuhriltu" to "Thuhriltu",
            "ecclesiastes" to "Thuhriltu",
            "hlathlankhawmte" to "Hla Thlan Khawmte",
            "hlathlan" to "Hla Thlan Khawmte",
            "songofsolomon" to "Hla Thlan Khawmte",
            "isaia" to "Isaia",
            "isaiah" to "Isaia",
            "jeremia" to "Jeremia",
            "jeremiah" to "Jeremia",
            "tahhla" to "Ṭah hla",
            "lamentations" to "Ṭah hla",
            "ezekiela" to "Ezekiela",
            "ezekiel" to "Ezekiela",
            "daniela" to "Daniela",
            "daniel" to "Daniela",
            "hosea" to "Hosea",
            "joela" to "Joela",
            "joel" to "Joela",
            "amosa" to "Amosa",
            "amos" to "Amosa",
            "obadia" to "Obadia",
            "obadiah" to "Obadia",
            "jona" to "Jona",
            "jonah" to "Jona",
            "mika" to "Mika",
            "micah" to "Mika",
            "nahuma" to "Nahuma",
            "nahum" to "Nahuma",
            "habakuka" to "Habakuka",
            "habakkuk" to "Habakuka",
            "zephania" to "Zephania",
            "zephaniah" to "Zephania",
            "hagaia" to "Hagaia",
            "haggai" to "Hagaia",
            "zakaria" to "Zakaria",
            "zechariah" to "Zakaria",
            "malakia" to "Malakia",
            "malachi" to "Malakia",
            "matthaia" to "Matthaia",
            "matthew" to "Matthaia",
            "marka" to "Marka",
            "mark" to "Marka",
            "luka" to "Luka",
            "luke" to "Luka",
            "johana" to "Johana",
            "john" to "Johana",
            "tirhkohte" to "Tirhkohte",
            "acts" to "Tirhkohte",
            "rom" to "Rom",
            "romans" to "Rom",
            "1korinth" to "1 Korinth",
            "ikorinth" to "1 Korinth",
            "1corinthians" to "1 Korinth",
            "2korinth" to "2 Korinth",
            "iikorinth" to "2 Korinth",
            "2corinthians" to "2 Korinth",
            "galatia" to "Galatia",
            "galatians" to "Galatia",
            "ephesi" to "Ephesi",
            "ephesians" to "Ephesi",
            "philippi" to "Philippi",
            "philippians" to "Philippi",
            "kolossa" to "Kolossa",
            "colossians" to "Kolossa",
            "1thessalonika" to "1 Thessalonika",
            "ithessalonika" to "1 Thessalonika",
            "1thessalonians" to "1 Thessalonika",
            "2thessalonika" to "2 Thessalonika",
            "iithessalonika" to "2 Thessalonika",
            "2thessalonians" to "2 Thessalonika",
            "1timothea" to "1 Timothea",
            "itimothea" to "1 Timothea",
            "1timothy" to "1 Timothea",
            "2timothea" to "2 Timothea",
            "iitimothea" to "2 Timothea",
            "2timothy" to "2 Timothea",
            "tita" to "Tita",
            "titus" to "Tita",
            "philemona" to "Philemona",
            "philemon" to "Philemona",
            "hebrai" to "Hebrai",
            "hebrews" to "Hebrai",
            "jakoba" to "Jakoba",
            "james" to "Jakoba",
            "1petera" to "1 Petera",
            "ipetera" to "1 Petera",
            "1peter" to "1 Petera",
            "2petera" to "2 Petera",
            "iipetera" to "2 Petera",
            "2peter" to "2 Petera",
            "1johana" to "1 Johana",
            "ijohana" to "1 Johana",
            "1john" to "1 Johana",
            "2johana" to "2 Johana",
            "iijohana" to "2 Johana",
            "2john" to "2 Johana",
            "3johana" to "3 Johana",
            "iiijohana" to "3 Johana",
            "3john" to "3 Johana",
            "juda" to "Juda",
            "jude" to "Juda",
            "thupuan" to "Thupuan",
            "revelation" to "Thupuan"
        )
        val key = input.lowercase().trim().replace("â", "a").replace("ê", "e").replace("î", "i")
            .replace("ô", "o").replace("û", "u").replace("ṭ", "t").replace("ṛ", "r")
            .replace(Regex("[^a-z0-9]"), "")
        return mapping[key] ?: input
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        _binding?.let { b ->
            if (!isEmpty) {
                b.emptyState.layoutEmptyState.visibility = View.GONE; return
            }
            b.emptyState.layoutEmptyState.visibility = View.VISIBLE
            b.emptyState.textEmptyMessage.text =
                "I thil zawn hi a hmuh mai loh tlat... emaw I thu zawn tur version thlak rawh."
            b.emptyState.textEmptySmiley?.visibility = View.VISIBLE
        }
    }

    private fun String.normalizeMizo(): String =
        this.lowercase().replace("â", "a").replace("ê", "e").replace("î", "i").replace("ô", "o")
            .replace("û", "u")

    private fun isOldTestament(book: String): Boolean {
        return getOldTestamentBooks().any { it.equals(book.trim(), ignoreCase = true) }
    }

    private fun isNewTestament(book: String): Boolean {
        return getNewTestamentBooks().any { it.equals(book.trim(), ignoreCase = true) }
    }

    private fun getOldTestamentBooks(): List<String> {
        return listOf(
            "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Josua-I",
            "Roreltute", "Ruthi", "I - Samuela", "II - Samuela", "I - Lalte", "II - Lalte",
            "I - Chronicles", "II - Chronicles", "EZRA", "Nehemia", "Estheri", "Joba",
            "Sam", "Thufingte", "Thuhriltu", "Hla Thlan Khawmte", "Isaia", "Jeremia",
            "Ṭah hla", "Ezekiela", "Daniela", "Hosea", "Joela", "Amosa", "Obadia",
            "Jona", "Mika", "Nahuma", "Habakuka", "Zephania", "Hagaia", "Zakaria", "Malakia"
        )
    }

    private fun getNewTestamentBooks(): List<String> {
        return listOf(
            "Matthaia", "Marka", "Luka", "Johana", "Tirhkohte", "Rom", "1 Korinth",
            "2 Korinth", "Galatia", "Ephesi", "Philippi", "Kolossa", "1 Thessalonika",
            "2 Thessalonika", "1 Timothea", "2 Timothea", "Tita", "Philemona", "Hebrai",
            "Jakoba", "1 Petera", "2 Petera", "1 Johana", "2 Johana", "3 Johana", "Juda",
            "Thupuan"
        )
    }

    override fun onDestroyView() {
        (activity as? AppCompatActivity)?.supportActionBar?.show()
        searchJob?.cancel(); _binding = null; super.onDestroyView()
    }
}
