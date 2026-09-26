package com.zoliana.khampat.mizobible.ui.reflow

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.Toast
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
import com.zoliana.khampat.mizobible.data.Pin
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.FragmentReflowBinding
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModel
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModelFactory
import com.zoliana.khampat.mizobible.utils.ThemeHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ReflowFragment : Fragment() {

    private var _binding: FragmentReflowBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    private lateinit var pinAdapter: PinAdapter
    private var actionMode: ActionMode? = null
    private val selectedPins = mutableSetOf<Pin>()

    private var searchQuery = ""
    private var testamentFilter = "ALL" // "ALL", "OT", "NT"
    private var rawPinsList = listOf<Pin>()

    private val expandedGroupIds = mutableSetOf<String>()
    private val manuallyCollapsedGroupIds = mutableSetOf<String>()
    private var hasInitializedExpansion = false

    private data class PinGroup(
        val id: String,
        val dateDisplay: String,
        val pins: List<Pin>,
        val timestamp: Long
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReflowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pinAdapter = PinAdapter(
            onPinClick = { pin ->
                viewModel.updateVersion(pin.version)
                viewModel.updateSelection(pin.book, pin.chapter, pin.verseId, pin.verse)
                findNavController().navigate(R.id.nav_home)
            },
            onLongClick = { pin ->
                if (actionMode == null) {
                    actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(actionModeCallback)
                    toggleSelection(pin)
                }
            },
            onSelectionChange = { pin, _ -> toggleSelection(pin) },
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
            onDateMoreClick = { v, header ->
                val popup = PopupMenu(requireContext(), v)
                popup.menu.add(0, 1, 0, "Delete (${header.count})")
                popup.setOnMenuItemClickListener { menuItem ->
                    if (menuItem.itemId == 1) {
                        showDeleteConfirmationDialog("Date: ${header.dateStr}-a pin zawng zawng (${header.count}) hi delete i duh tak tak em?") {
                            header.pins.forEach { viewModel.deletePin(it) }
                            Toast.makeText(requireContext(), "Pin-te delete a ni e", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                popup.show()
            }
        )

        binding.recyclerviewPins.adapter = pinAdapter

        viewModel.allPins.observe(viewLifecycleOwner) { pins ->
            rawPinsList = pins ?: emptyList()
            applyFilter()
        }

        // Sync Font Settings to Pins
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fontSettings.collectLatest { settings ->
                    pinAdapter.setFontSettings(settings)
                }
            }
        }

        setupSearchAndFilterUI()
        applyTheme()
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
        binding.recyclerviewPins.setBackgroundColor(bgColor)
        val emptyTextColor = if (isDark) Color.parseColor("#A0A2B8") else Color.parseColor("#5A5245")
        binding.emptyState.textEmptyMessage.setTextColor(emptyTextColor)
        updateFilterButtonStates()
        if (::pinAdapter.isInitialized) {
            pinAdapter.notifyDataSetChanged()
        }
    }

    private fun setupSearchAndFilterUI() {
        (activity as? MainActivity)?.setupToolbarSearch("Pin Date Search") { query ->
            searchQuery = query
            applyFilter()
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

    private fun applyFilter() {
        val filtered = rawPinsList.filter { pin ->
            val matchesTestament = when (testamentFilter) {
                "OT" -> isOldTestament(pin.book)
                "NT" -> isNewTestament(pin.book)
                else -> true
            }

            val query = searchQuery.trim().lowercase()
            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(pin.timestamp))
                dateStr.lowercase().contains(query) ||
                        pin.book.lowercase().contains(query) ||
                        "${pin.book} ${pin.chapter}:${pin.verse}".lowercase().contains(query) ||
                        pin.text.lowercase().contains(query) ||
                        pin.version.lowercase().contains(query)
            }

            matchesTestament && matchesQuery
        }

        val sdfDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val sortedList = filtered.sortedByDescending { it.timestamp }

        // Group pins strictly by Date (dd-MM-yyyy)
        val groupMap = linkedMapOf<String, MutableList<Pin>>()
        sortedList.forEach { p ->
            val dateStr = sdfDate.format(Date(p.timestamp))
            groupMap.getOrPut(dateStr) { mutableListOf() }.add(p)
        }

        val todayDateStr = sdfDate.format(Date())

        val groups = mutableListOf<PinGroup>()
        for ((dateStr, list) in groupMap) {
            val first = list.first()
            groups.add(
                PinGroup(
                    id = dateStr,
                    dateDisplay = dateStr,
                    pins = list,
                    timestamp = first.timestamp
                )
            )
        }

        // Expand today's groups by default (unless user manually collapsed them)
        groups.filter { it.dateDisplay == todayDateStr && !manuallyCollapsedGroupIds.contains(it.id) }.forEach {
            expandedGroupIds.add(it.id)
        }

        if (!hasInitializedExpansion) {
            val firstGroupId = groups.firstOrNull()?.id
            if (firstGroupId != null && !manuallyCollapsedGroupIds.contains(firstGroupId)) {
                expandedGroupIds.add(firstGroupId)
            }
            hasInitializedExpansion = true
        }

        if (searchQuery.isNotBlank()) {
            groups.forEach { expandedGroupIds.add(it.id) }
        }

        val treeItems = mutableListOf<PinTreeItem>()
        val groupsCount = groups.size

        groups.forEachIndexed { groupIndex, group ->
            val isExpanded = expandedGroupIds.contains(group.id)
            val isFirstRoot = (groupIndex == 0)
            val isLastRoot = (groupIndex == groupsCount - 1)
            val hasChildren = group.pins.isNotEmpty()

            treeItems.add(
                PinTreeItem.GroupHeader(
                    groupId = group.id,
                    dateStr = group.dateDisplay,
                    count = group.pins.size,
                    pins = group.pins,
                    isExpanded = isExpanded,
                    isFirstRoot = isFirstRoot,
                    isLastRoot = isLastRoot,
                    hasChildrenWhenExpanded = hasChildren
                )
            )

            if (isExpanded) {
                val totalChildren = group.pins.size
                group.pins.forEachIndexed { childIndex, pin ->
                    val isFirstChild = (childIndex == 0)
                    val isLastChild = (childIndex == totalChildren - 1)
                    val parentHasBelowRoot = (groupIndex < groupsCount - 1)

                    treeItems.add(
                        PinTreeItem.PinChild(
                            pin = pin,
                            groupId = group.id,
                            isFirstChild = isFirstChild,
                            isLastChild = isLastChild,
                            parentHasBelowRoot = parentHasBelowRoot
                        )
                    )
                }
            }
        }

        pinAdapter.submitList(treeItems)
        updateEmptyState(rawPinsList.isEmpty())
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

    private fun toggleSelection(pin: Pin) {
        if (selectedPins.contains(pin)) selectedPins.remove(pin) else selectedPins.add(pin)
        pinAdapter.toggleSelection(pin.verseId)
        if (selectedPins.isEmpty()) {
            actionMode?.finish()
        } else {
            actionMode?.title = "${selectedPins.size} selected"
            pinAdapter.setSelectionMode(true)
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_bookmark_selection, menu)
            pinAdapter.setSelectionMode(true)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_delete_selected -> {
                    showDeleteConfirmationDialog("He Pin ${selectedPins.size} hi i delete duh tak tak em?") {
                        selectedPins.forEach { viewModel.deletePin(it) }
                        mode.finish()
                    }
                    true
                }
                R.id.action_select_all -> {
                    val allPins = viewModel.allPins.value ?: return true
                    selectedPins.clear()
                    selectedPins.addAll(allPins)
                    pinAdapter.selectAll(allPins)
                    mode.title = "${selectedPins.size} selected"
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            pinAdapter.setSelectionMode(false)
            selectedPins.clear()
            actionMode = null
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.emptyState.textEmptyMessage.text =
            if (searchQuery.isNotEmpty() || testamentFilter != "ALL") "Chutiang Pin a awm lo" else "Pin a la awm lo"
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
