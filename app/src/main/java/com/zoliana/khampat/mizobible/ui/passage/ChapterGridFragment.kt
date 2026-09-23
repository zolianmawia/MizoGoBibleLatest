package com.zoliana.khampat.mizobible.ui.passage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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

        loadChapters()
    }

    private fun loadChapters() {
        viewLifecycleOwner.lifecycleScope.launch {
            val chapterCount = viewModel.repository.getChapterCountSync(viewModel.currentVersion.value, args.bookName) ?: 0
            if (chapterCount > 0) {
                val chapters = (1..chapterCount).map { it.toString() }
                val adapter = ArrayAdapter(requireContext(), R.layout.item_chapter_grid, R.id.text_chapter, chapters)
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
