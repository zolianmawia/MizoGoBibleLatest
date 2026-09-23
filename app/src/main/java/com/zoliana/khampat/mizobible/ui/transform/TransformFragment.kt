package com.zoliana.khampat.mizobible.ui.transform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.DragEvent
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.firebase.auth.FirebaseAuth
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.BibleVerse
import com.zoliana.khampat.mizobible.data.Bookmark
import com.zoliana.khampat.mizobible.data.MembershipType
import com.zoliana.khampat.mizobible.data.Note
import com.zoliana.khampat.mizobible.data.Pin
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.DialogBookmarkBinding
import com.zoliana.khampat.mizobible.databinding.DialogNoteBinding
import com.zoliana.khampat.mizobible.databinding.FragmentTransformBinding
import com.zoliana.khampat.mizobible.databinding.LayoutPinPopupBinding
import com.zoliana.khampat.mizobible.databinding.LayoutVerseActionsPopupBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class TransformFragment : Fragment() {

    private var _binding: FragmentTransformBinding? = null
    private val binding get() = _binding

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    private var bibleAdapter: BibleAdapter? = null
    private var splitBibleAdapter: BibleAdapter? = null
    private val selectedVersesList = mutableListOf<BibleVerse>()
    private var actionPopup: PopupWindow? = null
    private var pinSelectionPopup: PopupWindow? = null

    private var readingTimerJob: Job? = null
    private var isBottomBarVisible = true

    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var autoHideJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTransformBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding?.layoutBibleContainer?.let { container ->
            ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updatePadding(bottom = systemBars.bottom)
                insets
            }
        }

        showBottomBarAndResetTimer()

        bibleAdapter = BibleAdapter { verse, anchor -> handleVerseSelection(verse, anchor, false) }
        binding?.recyclerviewBible?.adapter = bibleAdapter
        splitBibleAdapter =
            BibleAdapter { verse, anchor -> handleVerseSelection(verse, anchor, true) }
        binding?.recyclerviewBibleSplit?.adapter = splitBibleAdapter

        setupSplitResizeLogic()
        setupPinchAndSwipeGestures()
        setupDragAndDrop()

        // Sync local data with adapters
        viewModel.allPins.observe(viewLifecycleOwner) { pins ->
            bibleAdapter?.setPins(pins)
            splitBibleAdapter?.setPins(pins)
        }
        viewModel.allBookmarks.observe(viewLifecycleOwner) { bookmarks ->
            bibleAdapter?.setBookmarks(bookmarks)
            splitBibleAdapter?.setBookmarks(bookmarks)
        }
        viewModel.allNotes.observe(viewLifecycleOwner) { notes ->
            bibleAdapter?.setNotes(notes)
            splitBibleAdapter?.setNotes(notes)
        }

        // Observers for settings
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.membershipType.collectLatest { type ->
                    bibleAdapter?.setMembership(type)
                    splitBibleAdapter?.setMembership(type)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isEyeProtectionEnabled.collectLatest { enabled ->
                    binding?.eyeProtectionOverlay?.visibility =
                        if (enabled) View.VISIBLE else View.GONE
                }
            }
        }

        // Highlight leh Scroll logic
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.highlightVerseId,
                    viewModel.highlightVerseNumber
                ) { id: Int?, verseNum: String? ->
                    id to verseNum
                }.collectLatest { (id, verseNum) ->
                    if (id == null && verseNum == null) {
                        bibleAdapter?.setHighlight(null, null)
                        splitBibleAdapter?.setHighlight(null, null)
                        return@collectLatest
                    }

                    bibleAdapter?.setHighlight(id, verseNum)
                    splitBibleAdapter?.setHighlight(id, verseNum)

                    delay(600)

                    val list = bibleAdapter?.currentList ?: emptyList()
                    fun getVNum(v: String?): Int? =
                        v?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()

                    var pos = list.indexOfFirst { it.id == id && id != 0 }
                    if (pos == -1 && verseNum != null) {
                        val target = getVNum(verseNum)
                        pos = list.indexOfFirst { getVNum(it.verse) == target }
                    }

                    if (pos != -1) {
                        (binding?.recyclerviewBible?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                            pos,
                            0
                        )
                        (binding?.recyclerviewBibleSplit?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                            pos,
                            0
                        )
                    }

                    delay(4000)
                    if (viewModel.highlightVerseId.value == id && viewModel.highlightVerseNumber.value == verseNum) {
                        viewModel.clearHighlight()
                    }
                }
            }
        }

        viewModel.bibleVerses.observe(viewLifecycleOwner) { verses ->
            binding?.progressBar?.visibility =
                if (verses.isNullOrEmpty()) View.VISIBLE else View.GONE
            binding?.recyclerviewBible?.visibility =
                if (verses.isNullOrEmpty()) View.GONE else View.VISIBLE

            bibleAdapter?.submitList(verses) {
                if (viewModel.isInitialLoad && verses.isNotEmpty()) {
                    val lastV = viewModel.lastVerse.value
                    val pos = verses.indexOfFirst { it.verse == lastV }.takeIf { it != -1 } ?: 0
                    (binding?.recyclerviewBible?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                        pos,
                        0
                    )
                    viewModel.isInitialLoad = false
                }
            }
        }

        viewModel.splitBibleVerses.observe(viewLifecycleOwner) { verses ->
            splitBibleAdapter?.submitList(verses)
        }

        // View Mode observers
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isSplitMode.collectLatest { isSplit ->
                    binding?.recyclerviewBibleSplit?.visibility =
                        if (isSplit) View.VISIBLE else View.GONE
                    binding?.splitDividerContainer?.visibility =
                        if (isSplit) View.VISIBLE else View.GONE
                    updateBiblePaddings()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isVerticalSplit.collectLatest { isVertical ->
                    updateSplitLayout(isVertical)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sidePadding.collectLatest { padding ->
                    bibleAdapter?.setSidePadding(padding)
                    splitBibleAdapter?.setSidePadding(padding)
                }
            }
        }

        // Chapter title animation on scroll
        binding?.appBar?.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            val range = appBarLayout.totalScrollRange
            if (range == 0) return@OnOffsetChangedListener
            val percentage = abs(verticalOffset).toFloat() / range.toFloat()
            binding?.textChapterTitle?.scaleX = 1f - (percentage * 0.7f)
            binding?.textChapterTitle?.scaleY = 1f - (percentage * 0.7f)
            binding?.textChapterTitle?.translationY =
                -(range + 15 * resources.displayMetrics.density) * percentage
            binding?.textChapterTitle?.translationX =
                -(resources.displayMetrics.widthPixels / 3.2f) * percentage

            val toolbarTitle =
                (requireActivity() as? MainActivity)?.binding?.appBarMain?.textToolbarBibleRef
            if (abs(verticalOffset) >= range) {
                toolbarTitle?.text =
                    "${viewModel.currentBook.value} ${viewModel.currentChapter.value}"
                toolbarTitle?.alpha = 1f
                binding?.textChapterTitle?.alpha = 0f
            } else {
                toolbarTitle?.text = viewModel.currentBook.value
                toolbarTitle?.alpha = percentage
                binding?.textChapterTitle?.alpha = 1f
            }
        })

        // Logic initializations
        setupScrollHiding()
        setupSyncScroll()
        setupLastVerseTracker()

        // Font settings observer
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fontSettings.collectLatest {
                    bibleAdapter?.setFontSettings(it)
                    splitBibleAdapter?.setFontSettings(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentChapter.collectLatest {
                    binding?.textChapterTitle?.text = it.toString()
                    showBottomBarAndResetTimer()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentBook.collectLatest {
                    showBottomBarAndResetTimer()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.keepScreenOn.collectLatest { keepOn ->
                    if (keepOn) requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    private fun setupLastVerseTracker() {
        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val firstPos = layoutManager.findFirstVisibleItemPosition()
                if (firstPos != RecyclerView.NO_POSITION) {
                    val verse = bibleAdapter?.currentList?.getOrNull(firstPos)
                    if (verse != null && !viewModel.isInitialLoad) {
                        viewModel.lastVerse.value = verse.verse ?: "1"
                    }
                }
            }
        }
        binding?.recyclerviewBible?.addOnScrollListener(listener)
    }

    private fun setupPinchAndSwipeGestures() {
        val swipeDetector =
            GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
                private val SWIPE_THRESHOLD = 80
                private val SWIPE_VELOCITY_THRESHOLD = 100

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false
                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y

                    if (Math.abs(diffX) > Math.abs(diffY) &&
                        Math.abs(diffX) > SWIPE_THRESHOLD &&
                        Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                    ) {
                        if (diffX < 0) {
                            goToChapter(1)
                        } else {
                            goToChapter(-1)
                        }
                        return true
                    }
                    return false
                }
            })

        scaleGestureDetector = ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val currentSize = viewModel.fontSettings.value.fontSize
                    val newSize = currentSize * detector.scaleFactor
                    viewModel.updateFontSize(newSize)
                    return true
                }
            })

        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        var initialX = 0f
        var initialY = 0f
        var isHorizontalSwipe = false

        val itemTouchListener = object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                scaleGestureDetector.onTouchEvent(e)

                when (e.actionMasked) {
                    ACTION_DOWN -> {
                        initialX = e.x
                        initialY = e.y
                        isHorizontalSwipe = false
                        swipeDetector.onTouchEvent(e)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = Math.abs(e.x - initialX)
                        val dy = Math.abs(e.y - initialY)
                        // If moving mostly horizontally and passed the threshold, intercept the touch!
                        if (!isHorizontalSwipe && dx > touchSlop && dx > dy * 1.5f) {
                            isHorizontalSwipe = true
                            rv.parent.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }

                if (e.actionMasked == ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
                    showBottomBarAndResetTimer()
                }

                if (isHorizontalSwipe) {
                    swipeDetector.onTouchEvent(e)
                    return true // Block RecyclerView scrolling and clicking
                }

                return scaleGestureDetector.isInProgress
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                scaleGestureDetector.onTouchEvent(e)
                if (isHorizontalSwipe) {
                    swipeDetector.onTouchEvent(e)
                }
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    isHorizontalSwipe = false
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        }

        binding?.recyclerviewBible?.addOnItemTouchListener(itemTouchListener)
        binding?.recyclerviewBibleSplit?.addOnItemTouchListener(itemTouchListener)
    }

    override fun onResume() {
        super.onResume()
        startReadingTimer()
    }

    override fun onPause() {
        super.onPause()
        stopReadingTimer()
    }

    private fun startReadingTimer() {
        readingTimerJob?.cancel()
        readingTimerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(10000)
                viewModel.logVerseRead()
                viewModel.addReadingTime(10)
            }
        }
    }

    private fun stopReadingTimer() {
        readingTimerJob?.cancel()
        readingTimerJob = null
    }

    private fun updateSplitLayout(isVertical: Boolean) {
        val container = binding?.layoutBibleContainer ?: return
        val dividerContainer = binding?.splitDividerContainer ?: return
        val dividerLine = binding?.splitDividerLine ?: return
        val splitHandle = binding?.splitHandle ?: return

        container.orientation = if (isVertical) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL

        binding?.textHandleIcon?.animate()?.rotation(if (isVertical) 0f else 90f)?.setDuration(400)
            ?.start()

        val dividerSize = (14 * resources.displayMetrics.density).toInt()
        val lineWidth = (16 * resources.displayMetrics.density).toInt()
        val handleSize = (16 * resources.displayMetrics.density).toInt() //icon

        dividerContainer.layoutParams = if (isVertical) {
            LinearLayout.LayoutParams(dividerSize, ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dividerSize)
        }
        dividerLine.layoutParams = if (isVertical) {
            FrameLayout.LayoutParams(lineWidth, ViewGroup.LayoutParams.MATCH_PARENT)
                .apply { gravity = Gravity.CENTER_HORIZONTAL }
        } else {
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, lineWidth)
                .apply { gravity = Gravity.CENTER_VERTICAL }
        }
        splitHandle.layoutParams = FrameLayout.LayoutParams(handleSize, handleSize).apply {
            gravity = Gravity.CENTER
        }
        updateBiblePaddings()
        setBibleWeights(1f, 1f, isVertical)
    }

    private fun updateBiblePaddings() {
        val isVertical = viewModel.isVerticalSplit.value
        val isSplit = viewModel.isSplitMode.value
        val density = resources.displayMetrics.density

        // Apply a constant padding. With the WindowInsets padding we added, we don't need large padding here.
        val bottomNavPadding = (16 * density).toInt()

        val rv1 = binding?.recyclerviewBible ?: return
        val rv2 = binding?.recyclerviewBibleSplit ?: return

        rv1.clipToPadding = true
        rv2.clipToPadding = true

        val left = rv1.paddingLeft
        val right = rv1.paddingRight
        val top = rv1.paddingTop

        val rv1Bottom = if (isSplit && !isVertical) (12 * density).toInt() else bottomNavPadding
        rv1.setPadding(left, top, right, rv1Bottom)

        val left2 = rv2.paddingLeft
        val right2 = rv2.paddingRight
        val rv2Top = if (isSplit && !isVertical) (16 * density).toInt() else 0

        rv2.setPadding(left2, rv2Top, right2, bottomNavPadding)
    }

    private fun setupSplitResizeLogic() {
        var startX = 0f
        var startY = 0f
        var isDragging = false
        binding?.splitDividerContainer?.setOnTouchListener { _, event ->
            when (event.action) {
                ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isDragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.rawX - startX)
                    val dy = abs(event.rawY - startY)
                    if (dx > 10 || dy > 10) isDragging = true
                    if (isDragging) {
                        val container =
                            binding?.layoutBibleContainer ?: return@setOnTouchListener false
                        val loc = IntArray(2)
                        container.getLocationOnScreen(loc)
                        if (viewModel.isVerticalSplit.value) {
                            val percent = (event.rawX - loc[0]) / container.width.toFloat()
                            setBibleWeights(
                                percent.coerceIn(0.15f, 0.85f),
                                (1f - percent).coerceIn(0.15f, 0.85f),
                                true
                            )
                        } else {
                            val percent = (event.rawY - loc[1]) / container.height.toFloat()
                            setBibleWeights(
                                percent.coerceIn(0.15f, 0.85f),
                                (1f - percent).coerceIn(0.15f, 0.85f),
                                false
                            )
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        viewModel.setSplitOrientation(!viewModel.isVerticalSplit.value)
                    }
                }
            }
            true
        }
    }

    private fun setBibleWeights(w1: Float, w2: Float, isVertical: Boolean) {
        val rv1 = binding?.recyclerviewBible ?: return
        val rv2 = binding?.recyclerviewBibleSplit ?: return
        val lp1 = rv1.layoutParams as LinearLayout.LayoutParams
        val lp2 = rv2.layoutParams as LinearLayout.LayoutParams
        lp1.width = if (isVertical) 0 else ViewGroup.LayoutParams.MATCH_PARENT
        lp1.height = if (isVertical) ViewGroup.LayoutParams.MATCH_PARENT else 0
        lp1.weight = w1
        lp2.width = if (isVertical) 0 else ViewGroup.LayoutParams.MATCH_PARENT
        lp2.height = if (isVertical) ViewGroup.LayoutParams.MATCH_PARENT else 0
        lp2.weight = w2
        rv1.layoutParams = lp1
        rv2.layoutParams = lp2
    }

    private fun showBottomBarAndResetTimer(durationMs: Long = 3000L) {
        val mainActivity = activity as? MainActivity ?: return
        val bottomContainer = mainActivity.binding.appBarMain.bottomContainer

        if (!isBottomBarVisible) {
            isBottomBarVisible = true
            val transition = androidx.transition.Slide(Gravity.BOTTOM)
            transition.duration = 200
            androidx.transition.TransitionManager.beginDelayedTransition(
                bottomContainer.parent as ViewGroup,
                transition
            )
            bottomContainer.visibility = View.VISIBLE
        }

        autoHideJob?.cancel()
        autoHideJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(durationMs)
            hideBottomBar()
        }
    }

    private fun hideBottomBar() {
        val mainActivity = activity as? MainActivity ?: return
        val bottomContainer = mainActivity.binding.appBarMain.bottomContainer

        if (isBottomBarVisible) {
            isBottomBarVisible = false
            val transition = androidx.transition.Slide(Gravity.BOTTOM)
            transition.duration = 250
            androidx.transition.TransitionManager.beginDelayedTransition(
                bottomContainer.parent as ViewGroup,
                transition
            )
            bottomContainer.visibility = View.GONE
        }
    }

    private fun setupScrollHiding() {
        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dx != 0 || dy != 0) {
                    showBottomBarAndResetTimer()
                }
            }
        }
        binding?.recyclerviewBible?.addOnScrollListener(listener)
        binding?.recyclerviewBibleSplit?.addOnScrollListener(listener)
    }

    private fun setupSyncScroll() {
        val rv1 = binding?.recyclerviewBible ?: return
        val rv2 = binding?.recyclerviewBibleSplit ?: return

        val scrollListener = object : RecyclerView.OnScrollListener() {
            private var isSyncing = false
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (isSyncing) return
                if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                    isSyncing = true
                    val other = if (recyclerView == rv1) rv2 else rv1
                    other.scrollBy(dx, dy)
                    isSyncing = false
                }
            }
        }
        rv1.addOnScrollListener(scrollListener)
        rv2.addOnScrollListener(scrollListener)
    }

    fun goToChapter(delta: Int) {
        showBottomBarAndResetTimer()
        viewLifecycleOwner.lifecycleScope.launch {
            val version = viewModel.currentVersion.value
            val books = viewModel.repository.getAllBooks(version).first()
            val chapter = viewModel.currentChapter.value + delta
            if (chapter < 1) {
                val idx = books.indexOf(viewModel.currentBook.value)
                if (idx > 0) {
                    val prev = books[idx - 1]
                    val count = viewModel.repository.getChapterCount(version, prev).first()
                    viewModel.updateSelection(prev, count ?: 1)
                }
            } else {
                val max = viewModel.repository.getChapterCount(version, viewModel.currentBook.value)
                    .first() ?: 0
                if (chapter > max) {
                    val idx = books.indexOf(viewModel.currentBook.value)
                    if (idx < books.size - 1) viewModel.updateSelection(books[idx + 1], 1)
                } else viewModel.currentChapter.value = chapter
            }
        }
    }

    private fun handleVerseSelection(verse: BibleVerse, anchor: View, isSplit: Boolean) {
        val existing = selectedVersesList.find { it.id == verse.id }
        if (existing != null) {
            selectedVersesList.remove(existing)
            bibleAdapter?.setSelection(verse.id ?: 0, false)
            splitBibleAdapter?.setSelection(verse.id ?: 0, false)
        } else {
            selectedVersesList.add(verse)
            bibleAdapter?.setSelection(verse.id ?: 0, true)
            splitBibleAdapter?.setSelection(verse.id ?: 0, true)
        }
        if (selectedVersesList.isEmpty()) actionPopup?.dismiss() else showVerseActionPopup()
    }

    private fun showVerseActionPopup() {
        if (actionPopup?.isShowing == true) actionPopup?.dismiss()
        val popupBinding = LayoutVerseActionsPopupBinding.inflate(layoutInflater)
        actionPopup = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        )

        popupBinding.actionCopy.setOnClickListener {
            copySelectedVerses()
            clearSelection()
            actionPopup?.dismiss()
        }
        popupBinding.actionShare.setOnClickListener {
            shareSelectedVerses()
            clearSelection()
            actionPopup?.dismiss()
        }
        popupBinding.actionBookmark.setOnClickListener {
            bookmarkSelectedVerses()
            clearSelection()
            actionPopup?.dismiss()
        }
        popupBinding.actionNote.setOnClickListener {
            showNoteDialog(selectedVersesList.toList())
            clearSelection()
            actionPopup?.dismiss()
        }
        popupBinding.actionClear.setOnClickListener {
            clearSelection()
            actionPopup?.dismiss()
        }

        actionPopup?.elevation = 30f
        binding?.root?.let {
            actionPopup?.showAtLocation(it, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 300)
        }
    }

    private fun clearSelection() {
        bibleAdapter?.clearSelection()
        splitBibleAdapter?.clearSelection()
        selectedVersesList.clear()
    }

    private fun copySelectedVerses() {
        val ctx = context ?: return
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val finalContent = formatBibleSelection()
        clipboard.setPrimaryClip(ClipData.newPlainText("Bible", finalContent))
        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareSelectedVerses() {
        val finalContent = formatBibleSelection()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, finalContent)
        }
        startActivity(Intent.createChooser(intent, "Share Verse"))
    }

    private fun formatBibleSelection(): String {
        if (selectedVersesList.isEmpty()) return ""
        selectedVersesList.sortBy { it.id }

        val textContent = StringBuilder("\"")
        selectedVersesList.forEachIndexed { index, it ->
            val vNum = it.verse ?: "0"
            val text = it.text?.trim() ?: ""
            if (vNum != "0" && vNum != "1") textContent.append(vNum)
            textContent.append(text)
            if (index < selectedVersesList.size - 1) textContent.append(" ")
        }
        textContent.append("\"")

        if (!viewModel.copyIncludeReference.value) return textContent.toString()

        val reference = getFormattedReference()
        return if (viewModel.copyReferenceAtBottom.value) {
            val indent = " ".repeat(maxOf(0, 40 - reference.length))
            "$textContent\n\n$indent $reference"
        } else {
            "$reference\n$textContent"
        }
    }

    private fun getFormattedReference(): String {
        if (selectedVersesList.isEmpty()) return ""
        selectedVersesList.sortBy { it.id }
        val v =
            viewModel.currentVersion.value.let { if (it.lowercase() == "verse" || it.lowercase() == "mgb") "MzOV" else it }
        val real = selectedVersesList.filter { it.verse != "0" }
        val m = real.firstOrNull() ?: selectedVersesList.first()
        return if (real.isEmpty()) "${m.book} ${m.chapter} ($v)"
        else if (real.size == 1) "${m.book} ${m.chapter}:${real.first().verse} ($v)"
        else "${m.book} ${m.chapter}:${real.first().verse}-${real.last().verse} ($v)"
    }

    private fun ensureLogin(action: () -> Unit) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            context?.let {
                val dialog = AlertDialog.Builder(it)
                    .setTitle("Login Ngai")
                    .setMessage("Pin leh Bookmark hmang tur hian login hmasak a ngai e.")
                    .setPositiveButton("Awle") { _, _ -> findNavController().navigate(R.id.nav_you) }
                    .setNegativeButton("Aih", null)
                    .create()
                dialog.show()
                (activity as? MainActivity)?.limitDialogWidth(dialog)
            }
        } else action()
    }

    private fun showNoteDialog(verses: List<BibleVerse>) = ensureLogin {
        val noteBinding = DialogNoteBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(noteBinding.root).create()
        var selectedColor = "#FF5252"
        val colorViews = listOf(
            noteBinding.colorRed to "#FF5252",
            noteBinding.colorBlue to "#448AFF",
            noteBinding.colorGreen to "#4CAF50",
            noteBinding.colorYellow to "#FFD740",
            noteBinding.colorPurple to "#9C27B0"
        )
        colorViews.forEach { (view, color) ->
            view.setOnClickListener {
                lifecycleScope.launch {
                    val type = viewModel.membershipType.first()
                    if (type == MembershipType.FREE) {
                        Toast.makeText(
                            requireContext(),
                            "Member chauhvin color an thlang thei",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        selectedColor = color
                        colorViews.forEach { it.first.strokeColor = Color.TRANSPARENT }
                        view.strokeColor = Color.BLACK
                    }
                }
            }
        }
        noteBinding.btnCancelNote.setOnClickListener { dialog.dismiss() }
        noteBinding.btnSaveNote.setOnClickListener {
            val text = noteBinding.editNoteText.text.toString()
            verses.forEach {
                viewModel.saveNote(
                    Note(
                        verseId = it.id ?: 0,
                        book = it.book ?: "",
                        chapter = it.chapter ?: 0,
                        verse = it.verse ?: "0",
                        text = text,
                        bibleText = it.text ?: "",
                        color = selectedColor,
                        version = viewModel.currentVersion.value
                    )
                )
            }
            dialog.dismiss()
        }
        dialog.show()
        (activity as? MainActivity)?.limitDialogWidth(dialog)
    }

    private fun showBookmarkDialog(verses: List<BibleVerse>) = ensureLogin {
        val bookmarkBinding = DialogBookmarkBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(bookmarkBinding.root).create()
        if (verses.size == 1) bookmarkBinding.textBookmarkReference.text =
            "${verses[0].book} ${verses[0].chapter}:${verses[0].verse}" else bookmarkBinding.textBookmarkReference.text =
            "${verses.size} Verses Selected"
        var selectedColor = "#ffe7ad"
        val colorViews = listOf(
            bookmarkBinding.colorRed to "#ff9999",
            bookmarkBinding.colorBlue to "#88b4fc",
            bookmarkBinding.colorGreen to "#b3ffb6",
            bookmarkBinding.colorYellow to "#ffeeb0",
            bookmarkBinding.colorPurple to "#f0a4fc"
        )
        colorViews.forEach { (view, color) ->
            view.setOnClickListener {
                lifecycleScope.launch {
                    val type = viewModel.membershipType.first()
                    if (type == MembershipType.FREE) {
                        Toast.makeText(
                            requireContext(),
                            "Member chauhvin color an thlang thei",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        selectedColor = color
                        colorViews.forEach { it.first.strokeColor = Color.TRANSPARENT }
                        view.strokeColor = Color.BLACK
                    }
                }
            }
        }
        bookmarkBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        bookmarkBinding.btnSave.setOnClickListener {
            val note = bookmarkBinding.editBookmarkNote.text.toString()
            verses.forEach {
                viewModel.toggleBookmark(
                    Bookmark(
                        verseId = it.id ?: 0,
                        book = it.book ?: "",
                        chapter = it.chapter ?: 0,
                        verse = it.verse ?: "0",
                        text = it.text ?: "",
                        version = viewModel.currentVersion.value,
                        note = note,
                        color = selectedColor
                    )
                )
            }
            dialog.dismiss()
        }
        dialog.show()
        (activity as? MainActivity)?.limitDialogWidth(dialog)
    }

    private fun bookmarkSelectedVerses() = showBookmarkDialog(selectedVersesList.toList())

    fun showPinSelectionPopup(anchorView: View) = ensureLogin {
        val popupBinding = LayoutPinPopupBinding.inflate(layoutInflater)
        val window = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.isOutsideTouchable = true
        pinSelectionPopup = window

        fun getColorHex(viewId: Int): String {
            return when (viewId) {
                popupBinding.pinRed.id -> "#FF5252"
                popupBinding.pinBlue.id -> "#448AFF"
                popupBinding.pinGreen.id -> "#4CAF50"
                popupBinding.pinYellow.id -> "#FFD740"
                popupBinding.pinPurple.id -> "#E040FB"
                else -> "#FFD740"
            }
        }

        val dragStartListener = View.OnLongClickListener { v ->
            val colorStr = getColorHex(v.id)
            val data = ClipData.newPlainText("color", colorStr)
            val shadow = View.DragShadowBuilder(v)
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_OPAQUE else 0
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) v.startDragAndDrop(
                data,
                shadow,
                colorStr,
                flags
            ) else @Suppress("DEPRECATION") v.startDrag(data, shadow, colorStr, 0)
            if (started) window.dismiss()
            true
        }

        val colorClickListener = View.OnClickListener { v ->
            val colorStr = getColorHex(v.id)
            if (selectedVersesList.isNotEmpty()) {
                selectedVersesList.forEach { verse ->
                    viewModel.addPin(
                        Pin(
                            verseId = verse.id ?: 0,
                            book = verse.book ?: "",
                            chapter = verse.chapter ?: 0,
                            verse = verse.verse ?: "0",
                            text = verse.text ?: "",
                            color = colorStr,
                            version = viewModel.currentVersion.value
                        )
                    )
                }
                selectedVersesList.clear()
                bibleAdapter?.clearSelection()
                splitBibleAdapter?.clearSelection()
                actionPopup?.dismiss()
                Toast.makeText(requireContext(), "Pinned!", Toast.LENGTH_SHORT).show()
                window.dismiss()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Verse-ah Pin hi hnuhlut (drag) rawh",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val colorViews = listOf(
            popupBinding.pinRed,
            popupBinding.pinBlue,
            popupBinding.pinGreen,
            popupBinding.pinYellow,
            popupBinding.pinPurple
        )

        colorViews.forEach { view ->
            view.setOnClickListener(colorClickListener)
            view.setOnLongClickListener(dragStartListener)
        }

        popupBinding.btnOpenPinList.setOnClickListener {
            window.dismiss()
            findNavController().navigate(R.id.nav_pin)
        }

        val mainActivity = activity as? MainActivity
        val bottomContainer = mainActivity?.binding?.appBarMain?.bottomContainer
        val density = resources.displayMetrics.density
        val yOffset = if (bottomContainer != null && bottomContainer.height > 0) {
            bottomContainer.height + (8 * density).toInt()
        } else {
            (140 * density).toInt()
        }

        binding?.root?.let {
            window.showAtLocation(
                it,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                0,
                yOffset
            )
        }
    }

    private fun setupDragAndDrop() {
        val dropListener = View.OnDragListener { v, event ->
            val rv = v as? RecyclerView ?: return@OnDragListener false
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DROP -> {
                    val colorStr =
                        if (event.clipData != null && event.clipData.itemCount > 0) event.clipData.getItemAt(
                            0
                        ).text.toString() else event.localState as? String ?: "#FFD740"
                    val x = event.x
                    val y = event.y
                    var child =
                        rv.findChildViewUnder(x, y) ?: rv.findChildViewUnder(rv.width / 2f, y)
                    if (child == null) {
                        for (i in 0 until rv.childCount) {
                            val c = rv.getChildAt(i)
                            if (y >= c.top && y <= c.bottom) {
                                child = c; break
                            }
                        }
                    }
                    if (child != null) {
                        val position = rv.getChildAdapterPosition(child)
                        if (position != RecyclerView.NO_POSITION) {
                            val adapter = rv.adapter as? BibleAdapter
                            val verse = adapter?.currentList?.get(position)
                            if (verse != null && verse.verse != "0") {
                                viewModel.addPin(
                                    Pin(
                                        verseId = verse.id ?: 0,
                                        book = verse.book ?: "",
                                        chapter = verse.chapter ?: 0,
                                        verse = verse.verse ?: "0",
                                        text = verse.text ?: "",
                                        color = colorStr,
                                        version = viewModel.currentVersion.value
                                    )
                                )
                                Toast.makeText(requireContext(), "Pinned!", Toast.LENGTH_SHORT)
                                    .show()
                                pinSelectionPopup?.dismiss()
                                return@OnDragListener true
                            }
                        }
                    }
                    false
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    pinSelectionPopup?.dismiss()
                    true
                }

                else -> true
            }
        }
        binding?.recyclerviewBible?.setOnDragListener(dropListener)
        binding?.recyclerviewBibleSplit?.setOnDragListener(dropListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionPopup?.dismiss()
        pinSelectionPopup?.dismiss()
        _binding = null
    }
}
