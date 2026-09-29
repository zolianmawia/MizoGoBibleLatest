package com.zoliana.khampat.mizobible.ui.passage

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.BibleVerse
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.FragmentVerseGridBinding
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModel
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModelFactory
import com.zoliana.khampat.mizobible.utils.ThemeHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class VerseGridFragment : Fragment() {

    private var _binding: FragmentVerseGridBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    private val args: VerseGridFragmentArgs by navArgs()
    private var selectedVerse: BibleVerse? = null
    private var verseDetailsJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVerseGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handle edge-to-edge insets
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.toolbar.title = "${args.bookName} Bung ${args.chapter}"
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        applyTheme()
        loadVerses()
        setupBottomCardListeners()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    fun applyTheme() {
        val ctx = context ?: return
        val toolbarColor = ThemeHelper.getEffectiveToolbarColor(ctx)
        val cardColor = ThemeHelper.getEffectiveCardColor(ctx) ?: toolbarColor
        val fontColor = ThemeHelper.getEffectiveFontColor(ctx)
        val iconColor = ThemeHelper.getEffectiveIconColor(ctx)
        val primaryColor = ThemeHelper.getPrimaryColor(ctx)
        val tbTextColor = ThemeHelper.getContrastingTextColor(toolbarColor, fontColor)
        val tbIconColor = iconColor?.let { ThemeHelper.getContrastingTextColor(toolbarColor, it) } ?: tbTextColor
        val cardTextColor = ThemeHelper.getContrastingTextColor(cardColor, fontColor)

        binding.root.setBackgroundColor(toolbarColor)
        binding.appBarLayout.setBackgroundColor(toolbarColor)
        binding.toolbar.setBackgroundColor(toolbarColor)
        binding.toolbar.setTitleTextColor(tbTextColor)
        binding.toolbar.navigationIcon?.setTint(tbIconColor)
        binding.gridViewVerses.setBackgroundColor(toolbarColor)

        binding.cardSelectedVerse.setCardBackgroundColor(cardColor)
        binding.textSelectedVerseContent.setTextColor(cardTextColor)
        binding.btnGoToVerse.backgroundTintList = ColorStateList.valueOf(primaryColor)
        binding.btnGoToVerse.setTextColor(Color.WHITE)
    }

    private fun loadVerses() {
        viewLifecycleOwner.lifecycleScope.launch {
            val verseCount = viewModel.repository.getVerseCountSync(
                viewModel.currentVersion.value,
                args.bookName,
                args.chapter
            )
            if (verseCount > 0) {
                val verses = (1..verseCount).map { it.toString() }
                val adapter = object : ArrayAdapter<String>(requireContext(), R.layout.item_chapter_grid, R.id.text_chapter, verses) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent)
                        val ctx = parent.context
                        val toolbarColor = ThemeHelper.getEffectiveToolbarColor(ctx)
                        val cardColor = ThemeHelper.getEffectiveCardColor(ctx) ?: toolbarColor
                        val primaryColor = ThemeHelper.getPrimaryColor(ctx)
                        val fontColor = ThemeHelper.getEffectiveFontColor(ctx)
                        val textColor = ThemeHelper.getContrastingTextColor(cardColor, fontColor)
                        val isDark = ThemeHelper.isColorDark(toolbarColor)

                        val textView = view.findViewById<TextView>(R.id.text_chapter)
                        textView.setTextColor(textColor)

                        val boxDrawable = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 16f * ctx.resources.displayMetrics.density
                            setColor(cardColor)
                            setStroke(
                                (1.5f * ctx.resources.displayMetrics.density).toInt(),
                                if (isDark) ColorUtils.setAlphaComponent(primaryColor, 180) else primaryColor
                            )
                        }
                        textView.background = boxDrawable
                        return view
                    }
                }
                binding.gridViewVerses.adapter = adapter

                binding.gridViewVerses.setOnItemClickListener { _, _, position, _ ->
                    val selectedVerseNumber = verses[position]
                    showVerseDetails(selectedVerseNumber)
                }
            }
        }
    }

    private fun showVerseDetails(verseNumber: String) {
        verseDetailsJob?.cancel()
        verseDetailsJob = viewLifecycleOwner.lifecycleScope.launch {
            val verse = viewModel.repository.getVersesSync(
                viewModel.currentVersion.value,
                args.bookName,
                args.chapter
            )
                .find { it.verse == verseNumber }

            selectedVerse = verse
            if (verse != null) {
                binding.cardSelectedVerse.visibility = View.VISIBLE
                binding.textSelectedVerseContent.text = verse.text

                val verseId = verse.id ?: 0
                val version = viewModel.currentVersion.value

                combine(
                    viewModel.repository.getBookmark(verseId, version),
                    viewModel.repository.getPin(verseId, version)
                ) { bookmark, pin ->
                    // Update bookmark icon
                    if (bookmark != null) {
                        binding.btnBookmark.visibility = View.VISIBLE
                        binding.btnBookmark.setImageResource(R.drawable.ic_bookmark_24)
                        binding.btnBookmark.setColorFilter(ContextCompat.getColor(requireContext(), R.color.bookmark_color))
                    } else {
                        binding.btnBookmark.visibility = View.GONE
                    }

                    // Update pin icon
                    if (pin != null) {
                        binding.btnPin.visibility = View.VISIBLE
                        binding.btnPin.setImageResource(R.drawable.ic_pin_24)
                        try {
                            binding.btnPin.setColorFilter(Color.parseColor(pin.color))
                        } catch (e: Exception) {
                            binding.btnPin.setColorFilter(ContextCompat.getColor(requireContext(), R.color.pin_color))
                        }
                    } else {
                        binding.btnPin.visibility = View.GONE
                    }
                }.collect()
            } else {
                binding.cardSelectedVerse.visibility = View.GONE
            }
        }
    }

    private fun setupBottomCardListeners() {
        binding.btnGoToVerse.setOnClickListener {
            selectedVerse?.let {
                if (it.book != null && it.chapter != null) {
                    viewModel.updateSelection(it.book!!, it.chapter!!, it.id ?: 0)
                    findNavController().popBackStack(R.id.nav_home, false)
                }
            }
        }

        binding.btnBookmark.setOnClickListener {
            // Handle bookmarking logic
        }

        binding.btnPin.setOnClickListener {
            // Handle pinning logic
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
