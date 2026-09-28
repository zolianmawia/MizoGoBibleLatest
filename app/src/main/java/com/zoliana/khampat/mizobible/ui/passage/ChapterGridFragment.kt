package com.zoliana.khampat.mizobible.ui.passage

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.zoliana.khampat.mizobible.R
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.FragmentChapterGridBinding
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModel
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModelFactory
import com.zoliana.khampat.mizobible.utils.ThemeHelper
import kotlinx.coroutines.launch

class ChapterGridFragment : Fragment() {

    private var _binding: FragmentChapterGridBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDao = bibleDb.bibleDao(), userDao = userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    private val args: ChapterGridFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChapterGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }

        binding.toolbar.title = args.bookName
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        applyTheme()
        loadChapters()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    fun applyTheme() {
        val ctx = context ?: return
        val toolbarColor = ThemeHelper.getEffectiveToolbarColor(ctx)
        val fontColor = ThemeHelper.getEffectiveFontColor(ctx)
        val iconColor = ThemeHelper.getEffectiveIconColor(ctx)
        val tbTextColor = ThemeHelper.getContrastingTextColor(toolbarColor, fontColor)
        val tbIconColor = iconColor?.let { ThemeHelper.getContrastingTextColor(toolbarColor, it) } ?: tbTextColor

        binding.root.setBackgroundColor(toolbarColor)
        binding.toolbar.setBackgroundColor(toolbarColor)
        binding.toolbar.setTitleTextColor(tbTextColor)
        binding.toolbar.navigationIcon?.setTint(tbIconColor)
    }

    private fun loadChapters() {
        viewLifecycleOwner.lifecycleScope.launch {
            val chapterCount = viewModel.repository.getChapterCountSync(viewModel.currentVersion.value, args.bookName) ?: 0
            if (chapterCount > 0) {
                val chapters = (1..chapterCount).map { it.toString() }
                val adapter = object : ArrayAdapter<String>(requireContext(), R.layout.item_chapter_grid, R.id.text_chapter, chapters) {
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
                binding.gridViewChapters.adapter = adapter

                binding.gridViewChapters.setOnItemClickListener { _, _, position, _ ->
                    val selectedChapter = chapters[position].toInt()
                    val action = ChapterGridFragmentDirections.actionChapterGridFragmentToVerseGridFragment(args.bookName, selectedChapter)
                    findNavController().navigate(action)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
