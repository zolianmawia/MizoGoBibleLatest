package com.zoliana.khampat.mizobible.ui.passage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.zoliana.khampat.mizobible.databinding.FragmentSelectPassageBinding

class SelectPassageFragment : Fragment() {

    private var _binding: FragmentSelectPassageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectPassageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handle system insets for status bar (top) and navigation bar (bottom)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Setup Toolbar
        binding.toolbar.setNavigationOnClickListener {
            // Handle close button click, e.g., dismiss the fragment/dialog
            parentFragmentManager.popBackStack()
        }

        // Setup ViewPager and TabLayout
        val pagerAdapter = TestamentPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "Thuthlung Hlui" else "Thuthlung Thar"
        }.attach()

        // Setup SearchView
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Pass the search query to the current fragment in the ViewPager
                val currentFragment = childFragmentManager.findFragmentByTag("f" + binding.viewPager.currentItem)
                if (currentFragment is BookListFragment) {
                    currentFragment.filterBooks(newText)
                }
                return true
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class TestamentPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            // Return a new instance of BookListFragment for each tab
            return BookListFragment.newInstance(position == 0) // true for Old Testament, false for New
        }
    }
}
