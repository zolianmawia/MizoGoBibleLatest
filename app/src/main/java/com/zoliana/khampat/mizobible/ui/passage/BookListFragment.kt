package com.zoliana.khampat.mizobible.ui.passage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.zoliana.khampat.mizobible.data.BibleDatabase
import com.zoliana.khampat.mizobible.data.BibleRepository
import com.zoliana.khampat.mizobible.data.UserDatabase
import com.zoliana.khampat.mizobible.databinding.FragmentBookListBinding
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModel
import com.zoliana.khampat.mizobible.ui.transform.TransformViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// Sealed class to represent different items in our list
sealed class PassageListItem {
    data class Header(val title: String) : PassageListItem()
    data class Book(val name: String) : PassageListItem()
}

class BookListFragment : Fragment() {

    private var _binding: FragmentBookListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransformViewModel by activityViewModels {
        val bibleDb = BibleDatabase.getDatabase(requireContext())
        val userDb = UserDatabase.getDatabase(requireContext())
        val repository = BibleRepository(bibleDb.bibleDao(), userDb.userDao())
        TransformViewModelFactory(repository, requireActivity().application)
    }

    private lateinit var bookAdapter: BookRecyclerAdapter
    private var isOldTestament: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isOldTestament = it.getBoolean(ARG_IS_OLD_TESTAMENT)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadBooks()
    }

    private fun setupRecyclerView() {
        bookAdapter = BookRecyclerAdapter { bookName ->
            val action = SelectPassageFragmentDirections.actionSelectPassageFragmentToChapterGridFragment(bookName)
            findNavController().navigate(action)
        }
        binding.recyclerViewBooks.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = bookAdapter
        }
    }

    private fun loadBooks() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.repository.getAllBooks(viewModel.currentVersion.value).collectLatest { allBooks ->
                val flatList = mutableListOf<PassageListItem>()
                if (isOldTestament) {
                    val books = allBooks.take(39)
                    flatList.addAll(createCategorizedList(books, true))
                } else {
                    val books = allBooks.drop(39)
                    flatList.addAll(createCategorizedList(books, false))
                }
                bookAdapter.submitList(flatList)
            }
        }
    }

    private fun createCategorizedList(books: List<String>, isOld: Boolean): List<PassageListItem> {
        val list = mutableListOf<PassageListItem>()
        val categories = if (isOld) getOldTestamentCategories() else getNewTestamentCategories()
        var bookIndex = 0
        
        categories.forEach { (categoryName, bookCount) ->
            if (bookIndex < books.size) {
                val booksInCategory = books.drop(bookIndex).take(bookCount)
                if (booksInCategory.isNotEmpty()) {
                    list.add(PassageListItem.Header(categoryName))
                    booksInCategory.forEach { bookName ->
                        list.add(PassageListItem.Book(bookName))
                    }
                    bookIndex += bookCount
                }
            }
        }
        return list
    }

    private fun getOldTestamentCategories(): List<Pair<String, Int>> {
        return listOf(
            "Pentateuch (Mosia Lehkhabu 5)" to 5,
            "History (Chanchin Bu)" to 12,
            "Poetry & Wisdom (Hla)" to 5,
            "Major Prophets (Zawlnei Lian)" to 5,
            "Minor Prophets (Zawlnei Tê)" to 12
        )
    }

    private fun getNewTestamentCategories(): List<Pair<String, Int>> {
        return listOf(
            "Gospels (Chanchin Tha Bu)" to 4,
            "History (Tirhkohte Thiltih)" to 1,
            "Pauline Epistles (Paula Lehkhathawn)" to 13,
            "General Epistles (Lehkhathawn)" to 8,
            "Prophecy (Hrilhlawkna Bu)" to 1
        )
    }

    fun filterBooks(query: String?) {
        bookAdapter.filter(query)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_IS_OLD_TESTAMENT = "is_old_testament"

        fun newInstance(isOldTestament: Boolean) =
            BookListFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_OLD_TESTAMENT, isOldTestament)
                }
            }
    }
}
