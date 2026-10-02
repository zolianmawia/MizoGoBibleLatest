package com.zoliana.khampat.mizobible.ui.passage

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.zoliana.khampat.mizobible.databinding.FragmentSelectPassageBinding
import com.zoliana.khampat.mizobible.utils.ThemeHelper

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
        binding.appBarLayout.stateListAnimator = null
        binding.appBarLayout.outlineProvider = null
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

        applyTheme()
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
        val isDark = ThemeHelper.isColorDark(toolbarColor)

        binding.root.setBackgroundColor(toolbarColor)
        binding.appBarLayout.setBackgroundColor(toolbarColor)
        binding.toolbar.setBackgroundColor(toolbarColor)
        binding.toolbar.setTitleTextColor(tbTextColor)
        binding.toolbar.navigationIcon?.setTint(tbIconColor)

        binding.tabLayout.setBackgroundColor(toolbarColor)
        binding.tabLayout.setSelectedTabIndicatorColor(primaryColor)
        binding.tabLayout.setTabTextColors(
            ColorUtils.setAlphaComponent(tbTextColor, 160),
            primaryColor
        )

        binding.cardSearch.setCardBackgroundColor(cardColor)
        binding.cardSearch.strokeColor = if (isDark) Color.parseColor("#30FFFFFF") else ColorUtils.setAlphaComponent(primaryColor, 50)
        binding.cardSearch.strokeWidth = (1 * resources.displayMetrics.density).toInt()

        binding.viewPager.setBackgroundColor(toolbarColor)

        val searchPlate = binding.searchView.findViewById<android.widget.EditText>(androidx.appcompat.R.id.search_src_text)
        val searchTextColor = ThemeHelper.getContrastingTextColor(cardColor, fontColor)
        val searchIconColor = iconColor?.let { ThemeHelper.getContrastingTextColor(cardColor, it) } ?: searchTextColor
        searchPlate?.setTextColor(searchTextColor)
        searchPlate?.setHintTextColor(ColorUtils.setAlphaComponent(searchTextColor, 140))
        val searchClose = binding.searchView.findViewById<android.widget.ImageView>(androidx.appcompat.R.id.search_close_btn)
        searchClose?.setColorFilter(searchIconColor)
        val searchMag = binding.searchView.findViewById<android.widget.ImageView>(androidx.appcompat.R.id.search_mag_icon)
        searchMag?.setColorFilter(searchIconColor)

        childFragmentManager.fragments.forEach { f ->
            if (f is BookListFragment) {
                f.applyTheme()
            }
        }
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
