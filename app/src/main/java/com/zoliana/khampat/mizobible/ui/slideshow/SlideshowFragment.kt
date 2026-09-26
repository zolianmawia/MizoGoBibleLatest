package com.zoliana.khampat.mizobible.ui.slideshow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.Bookmark
import com.zoliana.khampat.mizobible.data.MembershipType
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.DialogBookmarkActionsBinding
import com.zoliana.khampat.mizobible.databinding.DialogBookmarkBinding
import com.zoliana.khampat.mizobible.databinding.FragmentSlideshowBinding
import com.zoliana.khampat.mizobible.ui.common.TitleSuggestionAdapter
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModel
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModelFactory
import com.zoliana.khampat.mizobible.utils.ThemeHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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

    private var searchQuery = ""
    private var testamentFilter = "ALL" // "ALL", "OT", "NT"
    private var rawBookmarksList = listOf<Bookmark>()

    private val expandedGroupIds = mutableSetOf<String>()
    private val manuallyCollapsedGroupIds = mutableSetOf<String>()
    private var hasInitializedExpansion = false

    private data class BookmarkGroup(
        val id: String,
        val dateDisplay: String,
        val title: String,
        val bookmarks: List<Bookmark>,
        val timestamp: Long
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSlideshowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bookmarkAdapter = BookmarkAdapter(
            onBookmarkClick = { bookmark ->
                // Navigate into normal Bible verse screen and scroll to verse
                viewModel.updateVersion(bookmark.version)
                viewModel.updateSelection(bookmark.book, bookmark.chapter, bookmark.verseId, bookmark.verse)
                val popped = findNavController().popBackStack(R.id.nav_home, false)
                if (!popped) {
                    findNavController().navigate(R.id.nav_home)
                }
            },
            onLongClick = { item ->
                showBookmarkActionPopup(item)
            },
            onSelectionChange = { item, isSelected ->
                toggleSelection(item)
            },
            onGroupToggle = { groupId ->
                if (expandedGroupIds.contains(groupId)) {
                    expandedGroupIds.remove(groupId)
                    manuallyCollapsedGroupIds.add(groupId)
                } else {
                    expandedGroupIds.add(groupId)
                    manuallyCollapsedGroupIds.remove(groupId)
                }
                applyFilter()
            },
            onEditTitleClick = { groupHeader ->
                showEditGroupTitleDialog(groupHeader)
            },
            onEditBookmarkClick = { item ->
                showEditBookmarkDialog(item)
            },
            onDateMoreClick = { v, header ->
                val popup = PopupMenu(requireContext(), v)
                popup.menu.add(0, 1, 0, "Edit Title")
                popup.menu.add(0, 2, 1, "Delete (${header.count})")
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> showEditGroupTitleDialog(header)
                        2 -> showDeleteConfirmationDialog("Date: ${header.dateStr}-a bookmark zawng zawng (${header.count}) hi delete i duh tak tak em?") {
                            header.groupBookmarks.forEach { viewModel.deleteBookmark(it) }
                            Toast.makeText(requireContext(), "Bookmark-te delete a ni e", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                popup.show()
            },
            onVerseMoreClick = { v, item ->
                val popup = PopupMenu(requireContext(), v)
                popup.menu.add(0, 1, 0, "Edit Title")
                popup.menu.add(0, 2, 1, "Change Color")
                popup.menu.add(0, 3, 2, "Copy")
                popup.menu.add(0, 4, 3, "Share")
                popup.menu.add(0, 5, 4, "Delete Bookmark")
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> showEditBookmarkDialog(item)
                        2 -> showBookmarkActionPopup(item)
                        3 -> {
                            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = "${item.displayReference}\n" + item.bookmarks.joinToString(" ") { "${it.verse}. ${it.text.trim()}" }
                            clipboard.setPrimaryClip(ClipData.newPlainText("Bible Verse", clipText))
                            Toast.makeText(requireContext(), "Chang copy a ni e", Toast.LENGTH_SHORT).show()
                        }
                        4 -> {
                            val shareText = "${item.displayReference}\n" + item.bookmarks.joinToString(" ") { "${it.verse}. ${it.text.trim()}" }
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            startActivity(Intent.createChooser(sendIntent, "Share Bible Verse"))
                        }
                        5 -> {
                            showDeleteConfirmationDialog("${item.displayReference} bookmark hi delete i duh tak tak em?") {
                                item.bookmarks.forEach { viewModel.deleteBookmark(it) }
                                Toast.makeText(requireContext(), "Bookmark delete a ni e", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    true
                }
                popup.show()
            }
        )

        binding.recyclerviewBookmarks.adapter = bookmarkAdapter

        setupSearchAndFilterUI()
        applyTheme()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fontSettings.collectLatest { settings ->
                    bookmarkAdapter.setFontSettings(settings)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.repository.getAllBookmarks().collectLatest { bookmarks ->
                    rawBookmarksList = bookmarks
                    applyFilter()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    fun applyTheme() {
        val ctx = context ?: return
        val bgColor = ThemeHelper.getEffectiveBackgroundColor(ctx)
        val isDark = ThemeHelper.isColorDark(bgColor)
        binding.root.setBackgroundColor(bgColor)
        binding.recyclerviewBookmarks.setBackgroundColor(bgColor)
        val emptyTextColor = if (isDark) Color.parseColor("#A0A2B8") else Color.parseColor("#5A5245")
        binding.emptyState.textEmptyMessage.setTextColor(emptyTextColor)
        updateFilterButtonsUI()
        if (::bookmarkAdapter.isInitialized) {
            bookmarkAdapter.notifyDataSetChanged()
        }
    }

    private fun setupSearchAndFilterUI() {
        (activity as? MainActivity)?.setupToolbarSearch("Bookmark Title Search") { query ->
            searchQuery = query
            applyFilter()
        }

        binding.btnFilterAll.setOnClickListener {
            testamentFilter = "ALL"
            updateFilterButtonsUI()
            applyFilter()
        }

        binding.btnFilterOt.setOnClickListener {
            testamentFilter = "OT"
            updateFilterButtonsUI()
            applyFilter()
        }

        binding.btnFilterNt.setOnClickListener {
            testamentFilter = "NT"
            updateFilterButtonsUI()
            applyFilter()
        }

        updateFilterButtonsUI()
    }

    private fun updateFilterButtonsUI() {
        val context = context ?: return
        val density = resources.displayMetrics.density
        val bgColor = ThemeHelper.getEffectiveBackgroundColor(context)
        val isDark = ThemeHelper.isColorDark(bgColor)
        val primaryColor = ThemeHelper.APP_COLORS.find {
            it.id.equals(ThemeHelper.getSelectedAppColor(context), ignoreCase = true)
        }?.colorInt ?: ContextCompat.getColor(context, R.color.bible_blue)

        val isSepia = ThemeHelper.getCurrentThemeMode(context) == ThemeHelper.ThemeMode.SEPIA

        fun createChipDrawable(isSelected: Boolean): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                if (isSelected) {
                    setColor(primaryColor)
                } else {
                    if (isDark) {
                        setColor(Color.parseColor("#2E2F45"))
                        setStroke((1 * density).toInt(), Color.parseColor("#44FFFFFF"))
                    } else if (isSepia) {
                        setColor(Color.parseColor("#E5D9C0"))
                        setStroke((1 * density).toInt(), Color.parseColor("#DACFB9"))
                    } else {
                        setColor(Color.parseColor("#ECE5D8"))
                        setStroke((1 * density).toInt(), Color.parseColor("#DACFB9"))
                    }
                }
            }
        }

        val selectedTextColor = if (ThemeHelper.isColorDark(primaryColor)) Color.WHITE else Color.parseColor("#1A1A1A")
        val fontColor = ThemeHelper.getEffectiveFontColor(context)
        val unselectedTextColor = fontColor ?: if (isDark) Color.parseColor("#D3D4F2") else Color.parseColor("#5A5245")

        val isAll = (testamentFilter == "ALL")
        binding.btnFilterAll.background = createChipDrawable(isAll)
        binding.btnFilterAll.setTextColor(if (isAll) selectedTextColor else unselectedTextColor)
        binding.btnFilterAll.typeface = if (isAll) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

        val isOt = (testamentFilter == "OT")
        binding.btnFilterOt.background = createChipDrawable(isOt)
        binding.btnFilterOt.setTextColor(if (isOt) selectedTextColor else unselectedTextColor)
        binding.btnFilterOt.typeface = if (isOt) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

        val isNt = (testamentFilter == "NT")
        binding.btnFilterNt.background = createChipDrawable(isNt)
        binding.btnFilterNt.setTextColor(if (isNt) selectedTextColor else unselectedTextColor)
        binding.btnFilterNt.typeface = if (isNt) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun normalizeMizo(text: String?): String {
        if (text == null) return ""
        return text.lowercase()
            .replace("â", "a")
            .replace("ê", "e")
            .replace("î", "i")
            .replace("ô", "o")
            .replace("û", "u")
            .replace("ṭ", "t")
            .replace("ṛ", "r")
    }

    private fun parseVerseNumber(verseStr: String): Int {
        return verseStr.trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 0
    }

    private fun formatVerseRangeReference(book: String, chapter: Int, verses: List<Bookmark>): String {
        if (verses.isEmpty()) return "$book $chapter"
        if (verses.size == 1) return "$book $chapter:${verses.first().verse}"

        val sorted = verses.sortedBy { parseVerseNumber(it.verse) }
        val firstV = sorted.first().verse
        val lastV = sorted.last().verse
        return if (firstV == lastV) {
            "$book $chapter:$firstV"
        } else {
            "$book $chapter:$firstV-$lastV"
        }
    }

    private fun groupBookmarksIntoJoinedChildren(
        bookmarks: List<Bookmark>,
        groupId: String
    ): List<BookmarkTreeItem.VerseChild> {
        if (bookmarks.isEmpty()) return emptyList()

        // Cluster by Book, Chapter, Version, Color, and Title
        val clusters = bookmarks.groupBy { b ->
            "${b.book}__${b.chapter}__${b.version}__${b.color.trim().lowercase()}__${b.title.trim()}"
        }

        val joinedList = mutableListOf<BookmarkTreeItem.VerseChild>()

        for ((_, cluster) in clusters) {
            val sorted = cluster.sortedBy { parseVerseNumber(it.verse) }

            var currentChunk = mutableListOf<Bookmark>()
            for (b in sorted) {
                if (currentChunk.isEmpty()) {
                    currentChunk.add(b)
                } else {
                    val prev = currentChunk.last()
                    val prevNum = parseVerseNumber(prev.verse)
                    val currNum = parseVerseNumber(b.verse)
                    val isContiguous = (currNum == prevNum + 1)
                    val isSameBatch = Math.abs(b.timestamp - prev.timestamp) <= 5000L

                    // Merge contiguous verses or verses from the exact same bookmark action
                    if (isContiguous || isSameBatch) {
                        currentChunk.add(b)
                    } else {
                        val ref = formatVerseRangeReference(currentChunk.first().book, currentChunk.first().chapter, currentChunk)
                        joinedList.add(
                            BookmarkTreeItem.VerseChild(
                                bookmarks = currentChunk.toList(),
                                displayReference = ref,
                                groupId = groupId
                            )
                        )
                        currentChunk = mutableListOf(b)
                    }
                }
            }
            if (currentChunk.isNotEmpty()) {
                val ref = formatVerseRangeReference(currentChunk.first().book, currentChunk.first().chapter, currentChunk)
                joinedList.add(
                    BookmarkTreeItem.VerseChild(
                        bookmarks = currentChunk.toList(),
                        displayReference = ref,
                        groupId = groupId
                    )
                )
            }
        }

        // Sort joined entries by latest timestamp first
        return joinedList.sortedByDescending { it.primaryBookmark.timestamp }
    }

    private fun applyFilter() {
        val filtered = rawBookmarksList.filter { bookmark ->
            val matchesTestament = when (testamentFilter) {
                "OT" -> isOldTestament(bookmark.book)
                "NT" -> isNewTestament(bookmark.book)
                else -> true
            }

            val query = searchQuery.trim()
            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                val normQuery = normalizeMizo(query)
                val normTitle = normalizeMizo(bookmark.title)
                normTitle.contains(normQuery) ||
                        bookmark.title.lowercase().contains(query.lowercase()) ||
                        (bookmark.title.isBlank() && ("untitled".contains(query.lowercase()) || "title nei lo".contains(normQuery)))
            }

            matchesTestament && matchesQuery
        }

        val sdfDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val sortedList = filtered.sortedByDescending { it.timestamp }

        // Group bookmarks by Date: Date: 24-09-2026 (18)
        val groupMap = linkedMapOf<String, MutableList<Bookmark>>()
        sortedList.forEach { b ->
            val dateStr = sdfDate.format(Date(b.timestamp))
            groupMap.getOrPut(dateStr) { mutableListOf() }.add(b)
        }

        val todayDateStr = sdfDate.format(Date())

        val groups = mutableListOf<BookmarkGroup>()
        for ((dateStr, list) in groupMap) {
            groups.add(
                BookmarkGroup(
                    id = dateStr,
                    dateDisplay = dateStr,
                    title = "",
                    bookmarks = list,
                    timestamp = list.maxOfOrNull { it.timestamp } ?: 0L
                )
            )
        }

        // Sort groups: latest activity first
        groups.sortByDescending { it.timestamp }

        // Expand today's groups by default (unless user manually collapsed them)
        groups.filter { it.dateDisplay == todayDateStr && !manuallyCollapsedGroupIds.contains(it.id) }.forEach {
            expandedGroupIds.add(it.id)
        }

        // Expand the first group initially if today has no bookmarks
        if (!hasInitializedExpansion) {
            if (expandedGroupIds.isEmpty() && groups.isNotEmpty()) {
                expandedGroupIds.add(groups.first().id)
            }
            hasInitializedExpansion = true
        }

        // Expand any group matching an active search query
        if (searchQuery.isNotBlank()) {
            groups.forEach { expandedGroupIds.add(it.id) }
        }

        val treeItems = mutableListOf<BookmarkTreeItem>()
        val groupsCount = groups.size

        groups.forEachIndexed { groupIndex, group ->
            val isExpanded = expandedGroupIds.contains(group.id)
            val isFirstRoot = (groupIndex == 0)
            val isLastRoot = (groupIndex == groupsCount - 1)
            val hasChildren = group.bookmarks.isNotEmpty()

            treeItems.add(
                BookmarkTreeItem.GroupHeader(
                    groupId = group.id,
                    dateStr = group.dateDisplay,
                    count = group.bookmarks.size,
                    title = group.title,
                    isExpanded = isExpanded,
                    isFirstRoot = isFirstRoot,
                    isLastRoot = isLastRoot,
                    hasChildrenWhenExpanded = hasChildren,
                    groupBookmarks = group.bookmarks
                )
            )

            if (isExpanded) {
                val joinedChildren = groupBookmarksIntoJoinedChildren(group.bookmarks, group.id)
                val totalChildren = joinedChildren.size
                joinedChildren.forEachIndexed { childIndex, child ->
                    val isFirstChild = (childIndex == 0)
                    val isLastChild = (childIndex == totalChildren - 1)
                    val parentHasBelowRoot = (groupIndex < groupsCount - 1)

                    treeItems.add(
                        child.copy(
                            isFirstChild = isFirstChild,
                            isLastChild = isLastChild,
                            parentHasBelowRoot = parentHasBelowRoot
                        )
                    )
                }
            }
        }

        bookmarkAdapter.submitList(treeItems)
        updateEmptyState(rawBookmarksList.isEmpty())
    }

    private fun showBookmarkActionPopup(item: BookmarkTreeItem.VerseChild) {
        val popupBinding = DialogBookmarkActionsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(popupBinding.root).create()
        ThemeHelper.applyThemeToView(requireContext(), popupBinding.root)

        popupBinding.textActionReference.text = item.displayReference
        val snippet = if (item.bookmarks.size == 1) {
            item.primaryBookmark.text
        } else {
            item.bookmarks.joinToString(" ") { "${BookmarkAdapter.toSuperscript(it.verse)} ${it.text.trim()}" }
        }
        popupBinding.textActionSnippet.text = snippet

        var currentColor = if (item.color.isNotEmpty()) item.color else ThemeHelper.BOOKMARK_YELLOW
        val colorViews = listOf(
            popupBinding.colorYellow to ThemeHelper.BOOKMARK_YELLOW,
            popupBinding.colorGreen to ThemeHelper.BOOKMARK_GREEN,
            popupBinding.colorBlue to ThemeHelper.BOOKMARK_BLUE,
            popupBinding.colorRed to ThemeHelper.BOOKMARK_RED,
            popupBinding.colorPurple to ThemeHelper.BOOKMARK_PURPLE
        )

        fun updateColorSelection(selected: String) {
            currentColor = selected
            colorViews.forEach { (view, hex) ->
                val matches = hex.equals(selected, ignoreCase = true) ||
                    (selected.equals("#FF5252", ignoreCase = true) && hex == ThemeHelper.BOOKMARK_RED) ||
                    (selected.equals("#EF9A9A", ignoreCase = true) && hex == ThemeHelper.BOOKMARK_RED) ||
                    (selected.equals("#448AFF", ignoreCase = true) && hex == ThemeHelper.BOOKMARK_BLUE) ||
                    (selected.equals("#90CAF9", ignoreCase = true) && hex == ThemeHelper.BOOKMARK_BLUE) ||
                    (selected.equals("#4CAF50", ignoreCase = true) && hex == ThemeHelper.BOOKMARK_GREEN) ||
                    (selected.equals("#A5D6A7", ignoreCase = true) && hex == ThemeHelper.BOOKMARK_GREEN) ||
                    (selected.equals("#FFD740", ignoreCase = true) && hex == ThemeHelper.BOOKMARK_YELLOW) ||
                    (selected.equals("#FFE082", ignoreCase = true) && hex == ThemeHelper.BOOKMARK_YELLOW) ||
                    (selected.equals("#9C27B0", ignoreCase = true) && hex == ThemeHelper.BOOKMARK_PURPLE) ||
                    (selected.equals("#CE93D8", ignoreCase = true) && hex == ThemeHelper.BOOKMARK_PURPLE)
                view.strokeColor = if (matches) Color.BLACK else Color.TRANSPARENT
            }
        }
        updateColorSelection(currentColor)

        colorViews.forEach { (view, hex) ->
            view.setOnClickListener {
                lifecycleScope.launch {
                    val type = viewModel.membershipType.first()
                    if (type == MembershipType.FREE) {
                        Toast.makeText(requireContext(), "Member chauhvin color an thlang thei", Toast.LENGTH_SHORT).show()
                    } else {
                        updateColorSelection(hex)
                        item.bookmarks.forEach { b ->
                            val updated = b.copy(color = hex)
                            viewModel.toggleBookmark(updated)
                        }
                        Toast.makeText(requireContext(), "Bookmark rawng thlak a ni e", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        }

        popupBinding.btnEditBookmark.setOnClickListener {
            dialog.dismiss()
            showEditBookmarkDialog(item)
        }

        popupBinding.btnDeleteBookmark.setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmationDialog("${item.displayReference} bookmark hi delete i duh tak tak em?") {
                item.bookmarks.forEach { viewModel.deleteBookmark(it) }
                Toast.makeText(requireContext(), "Bookmark delete a ni e", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
        (activity as? MainActivity)?.limitDialogWidth(dialog)
    }

    private fun showEditGroupTitleDialog(header: BookmarkTreeItem.GroupHeader) {
        val dialogBinding = DialogBookmarkBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogBinding.root).create()
        ThemeHelper.applyThemeToView(requireContext(), dialogBinding.root)

        dialogBinding.textBookmarkReference.text = "Date: ${header.dateStr} (${header.count})"
        dialogBinding.editBookmarkTitle.setText(header.title)
        dialogBinding.editBookmarkNote.setText(header.groupBookmarks.firstOrNull()?.note ?: "")

        // Load previous bookmark titles for Dropdown Suggestions
        lifecycleScope.launch {
            val allBookmarks = viewModel.repository.userDao.getAllBookmarksSync()
            val titles = allBookmarks.mapNotNull { it.title.trim().takeIf { t -> t.isNotEmpty() } }.distinct()
            if (titles.isNotEmpty()) {
                val titleAdapter = TitleSuggestionAdapter(requireContext(), titles)
                dialogBinding.editBookmarkTitle.setAdapter(titleAdapter)
            }
        }
        dialogBinding.layoutBookmarkTitle.setEndIconOnClickListener {
            dialogBinding.editBookmarkTitle.showAllSuggestions()
        }

        var selectedColor = header.groupBookmarks.firstOrNull()?.color?.takeIf { it.isNotBlank() } ?: ThemeHelper.BOOKMARK_YELLOW
        val colorViews = listOf(
            dialogBinding.colorYellow to ThemeHelper.BOOKMARK_YELLOW,
            dialogBinding.colorGreen to ThemeHelper.BOOKMARK_GREEN,
            dialogBinding.colorBlue to ThemeHelper.BOOKMARK_BLUE,
            dialogBinding.colorRed to ThemeHelper.BOOKMARK_RED,
            dialogBinding.colorPurple to ThemeHelper.BOOKMARK_PURPLE
        )
        fun updateSwatchSelection(colorHex: String) {
            selectedColor = colorHex
            colorViews.forEach { pair ->
                val matches = pair.second.equals(colorHex, ignoreCase = true) ||
                    (colorHex.equals("#FF5252", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_RED) ||
                    (colorHex.equals("#EF9A9A", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_RED) ||
                    (colorHex.equals("#448AFF", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_BLUE) ||
                    (colorHex.equals("#90CAF9", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_BLUE) ||
                    (colorHex.equals("#4CAF50", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_GREEN) ||
                    (colorHex.equals("#A5D6A7", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_GREEN) ||
                    (colorHex.equals("#FFD740", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_YELLOW) ||
                    (colorHex.equals("#FFE082", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_YELLOW) ||
                    (colorHex.equals("#9C27B0", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_PURPLE) ||
                    (colorHex.equals("#CE93D8", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_PURPLE)
                pair.first.strokeColor = if (matches) Color.BLACK else Color.TRANSPARENT
            }
        }
        updateSwatchSelection(selectedColor)
        colorViews.forEach { (view, color) ->
            view.setOnClickListener {
                updateSwatchSelection(color)
            }
        }

        dialogBinding.btnOpenAllBookmarks.visibility = View.GONE
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val newTitle = dialogBinding.editBookmarkTitle.text.toString().trim()
            val newNote = dialogBinding.editBookmarkNote.text.toString().trim()
            header.groupBookmarks.forEach { b ->
                val updated = b.copy(title = newTitle, note = newNote, color = selectedColor)
                viewModel.toggleBookmark(updated)
            }
            dialog.dismiss()
        }
        dialog.show()
        (activity as? MainActivity)?.limitDialogWidth(dialog)
    }

    private fun showEditBookmarkDialog(item: BookmarkTreeItem.VerseChild) {
        val dialogBinding = DialogBookmarkBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogBinding.root).create()
        ThemeHelper.applyThemeToView(requireContext(), dialogBinding.root)
        dialogBinding.textBookmarkReference.text = item.displayReference
        dialogBinding.editBookmarkTitle.setText(item.title)
        dialogBinding.editBookmarkNote.setText(item.note)

        // Load previous bookmark titles for Dropdown Suggestions
        lifecycleScope.launch {
            val allBookmarks = viewModel.repository.userDao.getAllBookmarksSync()
            val titles = allBookmarks.mapNotNull { it.title.trim().takeIf { t -> t.isNotEmpty() } }.distinct()
            if (titles.isNotEmpty()) {
                val titleAdapter = TitleSuggestionAdapter(requireContext(), titles)
                dialogBinding.editBookmarkTitle.setAdapter(titleAdapter)
            }
        }
        dialogBinding.layoutBookmarkTitle.setEndIconOnClickListener {
            dialogBinding.editBookmarkTitle.showAllSuggestions()
        }

        var selectedColor = if (item.color.isNotEmpty()) item.color else ThemeHelper.BOOKMARK_YELLOW

        val colorViews = listOf(
            dialogBinding.colorYellow to ThemeHelper.BOOKMARK_YELLOW,
            dialogBinding.colorGreen to ThemeHelper.BOOKMARK_GREEN,
            dialogBinding.colorBlue to ThemeHelper.BOOKMARK_BLUE,
            dialogBinding.colorRed to ThemeHelper.BOOKMARK_RED,
            dialogBinding.colorPurple to ThemeHelper.BOOKMARK_PURPLE
        )
        fun updateSwatchSelection(colorHex: String) {
            selectedColor = colorHex
            colorViews.forEach { pair ->
                val matches = pair.second.equals(colorHex, ignoreCase = true) ||
                    (colorHex.equals("#FF5252", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_RED) ||
                    (colorHex.equals("#EF9A9A", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_RED) ||
                    (colorHex.equals("#448AFF", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_BLUE) ||
                    (colorHex.equals("#90CAF9", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_BLUE) ||
                    (colorHex.equals("#4CAF50", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_GREEN) ||
                    (colorHex.equals("#A5D6A7", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_GREEN) ||
                    (colorHex.equals("#FFD740", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_YELLOW) ||
                    (colorHex.equals("#FFE082", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_YELLOW) ||
                    (colorHex.equals("#9C27B0", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_PURPLE) ||
                    (colorHex.equals("#CE93D8", ignoreCase = true) && pair.second == ThemeHelper.BOOKMARK_PURPLE)
                pair.first.strokeColor = if (matches) Color.BLACK else Color.TRANSPARENT
            }
        }
        updateSwatchSelection(selectedColor)
        colorViews.forEach { (view, color) ->
            view.setOnClickListener {
                updateSwatchSelection(color)
            }
        }

        dialogBinding.btnOpenAllBookmarks.visibility = View.GONE
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val newTitle = dialogBinding.editBookmarkTitle.text.toString().trim()
            val newNote = dialogBinding.editBookmarkNote.text.toString().trim()
            item.bookmarks.forEach { b ->
                val updated = b.copy(title = newTitle, note = newNote, color = selectedColor)
                viewModel.toggleBookmark(updated)
            }
            dialog.dismiss()
        }
        dialog.show()
        (activity as? MainActivity)?.limitDialogWidth(dialog)
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

    private fun toggleSelection(item: BookmarkTreeItem.VerseChild) {
        val allSelected = item.bookmarks.all { selectedBookmarks.contains(it) }
        if (allSelected) {
            selectedBookmarks.removeAll(item.bookmarks.toSet())
        } else {
            selectedBookmarks.addAll(item.bookmarks)
        }
        bookmarkAdapter.toggleSelection(item)
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
                    selectedBookmarks.clear()
                    selectedBookmarks.addAll(rawBookmarksList)
                    val verseItems = bookmarkAdapter.currentList.filterIsInstance<BookmarkTreeItem.VerseChild>()
                    bookmarkAdapter.selectAll(verseItems)
                    mode.title = "${selectedBookmarks.size} selected"
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            bookmarkAdapter.setSelectionMode(false)
            selectedBookmarks.clear()
            actionMode = null
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.emptyState.textEmptyMessage.text =
            if (searchQuery.isNotEmpty()) "He Title pu Bookmark a awm lo"
            else if (testamentFilter != "ALL") "Chutiang Bookmark a awm lo"
            else "Bookmark a la awm lo"
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
        (activity as? MainActivity)?.clearToolbarSearch()
        super.onDestroyView()
        _binding = null
    }
}
