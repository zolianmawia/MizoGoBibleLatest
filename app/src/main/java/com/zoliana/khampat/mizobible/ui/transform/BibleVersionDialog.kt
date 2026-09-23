package com.zoliana.khampat.mizobible.ui.transform

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zoliana.khampat.mizobible.MainActivity
import com.zoliana.khampat.mizobible.databinding.DialogBibleVersionsBinding
import kotlinx.coroutines.launch

class BibleVersionDialog : BottomSheetDialogFragment() {

    private var _binding: DialogBibleVersionsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TransformViewModel by activityViewModels()
    private lateinit var adapter: BibleVersionAdapter

    private val downloadableVersions = listOf(
        Triple("Mizo Go Bible OV (MzOV)", "MzOV", "1fiPvRdX6iIgJVsoW_F4BwuKgzbNbt9Ws"),
        Triple("Mizo Common Language (MzCL)", "MzCL", "1RB7XcHiepfteHl5x74K45BEQKpa1ugnM"),
        Triple("King James Version (KJV)", "KJV", "1Z7DABZGPPWu2wqo7OlFwwqGWJzIcVWvk"),
        Triple("New International Version (NIV)", "NIV", "1bnDLmjP9EUJBYn1yTrGHvNb_QsAIovgo"),
        Triple("HINDI (HIN)", "HIN", "1Z0faONDmNJaiF8sa3XDehkhETn3Be1Zz"),
        Triple("Myanmar Judson (MYJ)", "MYJ", "1VI9J7hpS-GykLmqQZB05tL2Aks9jSz5K"),
        Triple("GREEK (GRK)", "GREEK (GRK)", "1m5Sye8s0DSqXdORBHnR2T8Jl8wp_vHa_")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogBibleVersionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet =
                dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout?
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true

                val width = resources.displayMetrics.widthPixels
                val tabletWidth = (600 * resources.displayMetrics.density).toInt()
                if (width > tabletWidth) {
                    val params = it.layoutParams
                    params.width = tabletWidth
                    it.layoutParams = params
                }
            }
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handle bottom insets for Edge-to-Edge
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }

        setupRecyclerView()
        loadVersions()

        binding.btnDeleteSelected.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isNotEmpty()) {
                val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Versions")
                    .setMessage("${selected.size} versions hi i delete duh tak tak em?")
                    .setPositiveButton("Aw") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            selected.forEach { item ->
                                viewModel.repository.deleteVersion(item.code)
                                requireContext().getSharedPreferences(
                                    "bible_prefs",
                                    Context.MODE_PRIVATE
                                )
                                    .edit().putBoolean("imported_${item.code}", false).apply()
                            }
                            adapter.isDeleteMode = false
                            loadVersions()
                            Toast.makeText(requireContext(), "Versions deleted", Toast.LENGTH_SHORT)
                                .show()
                            binding.btnDeleteSelected.visibility = View.GONE
                        }
                    }
                    .setNegativeButton("Aih", null)
                    .show()
                (activity as? MainActivity)?.limitDialogWidth(dialog)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = BibleVersionAdapter(
            onActionClick = { item ->
                (activity as? MainActivity)?.let { main ->
                    val driveUrl =
                        "https://drive.google.com/uc?export=download&id=${item.driveId}"
                    main.downloadBibleVersion(driveUrl, item.code)
                    dismiss()
                }

            },
            onSelectionChanged = {
                binding.btnDeleteSelected.visibility =
                    if (adapter.isDeleteMode && adapter.getSelectedItems()
                            .isNotEmpty()
                    ) View.VISIBLE else View.GONE
            }
        )
        binding.rvVersions.layoutManager = LinearLayoutManager(context)
        binding.rvVersions.adapter = adapter
    }

    private fun loadVersions() {
        val prefs = requireContext().getSharedPreferences("bible_prefs", Context.MODE_PRIVATE)
        val remoteVersions = (activity as? MainActivity)?.remoteVersions

        val items = downloadableVersions.map { (title, code, driveId) ->
            val isDownloaded = prefs.getBoolean("imported_$code", false)
            val localVer = prefs.getInt("version_$code", 1)

            val remoteVer = if (remoteVersions?.containsKey(code) == true) {
                val vData = remoteVersions[code]
                if (vData is Map<*, *>) {
                    (vData["version"] as? Number)?.toInt() ?: 0
                } else {
                    (vData as? Number)?.toInt() ?: 0
                }
            } else if (code == "MzOV" || code == "MGB") {
                (remoteVersions?.get("latest_version") as? Number)?.toInt() ?: 0
            } else 0

            BibleVersionItem(title, code, driveId, localVer, remoteVer, isDownloaded)
        }
        adapter.submitList(items)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
