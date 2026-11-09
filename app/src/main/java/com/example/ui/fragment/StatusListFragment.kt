package com.example.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.MainActivity
import com.example.R
import com.example.databinding.FragmentStatusListBinding
import com.example.model.StatusMedia
import com.example.ui.MediaViewerActivity
import com.example.ui.adapter.StatusAdapter
import com.example.util.ReviewHelper
import com.example.util.StorageHelper
import com.example.viewmodel.StatusViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class StatusListFragment : Fragment() {

    private var _binding: FragmentStatusListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatusViewModel by activityViewModels()
    private lateinit var adapter: StatusAdapter
    private var tabIndex: Int = 0

    companion object {
        private const val ARG_TAB_INDEX = "arg_tab_index"

        fun newInstance(tabIndex: Int): StatusListFragment {
            val fragment = StatusListFragment()
            val args = Bundle().apply {
                putInt(ARG_TAB_INDEX, tabIndex)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabIndex = arguments?.getInt(ARG_TAB_INDEX) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        val spanCount = if (resources.configuration.screenWidthDp >= 600) 4 else 3
        binding.rvStatuses.layoutManager = GridLayoutManager(requireContext(), spanCount)

        adapter = StatusAdapter(
            onItemClick = { media ->
                val currentList = adapter.currentList
                val position = currentList.indexOfFirst { it.name == media.name }.coerceAtLeast(0)
                val intent = MediaViewerActivity.newIntent(
                    requireContext(),
                    currentList,
                    initialPosition = position,
                    isFromSavedTab = (tabIndex == 2),
                    isBusiness = viewModel.isBusiness.value
                )
                startActivity(intent)
            },
            onItemLongClick = { media ->
                viewModel.toggleSelection(media)
            },
            onQuickDownloadClick = { media ->
                viewModel.saveSingle(media) { success ->
                    if (success) {
                        Toast.makeText(requireContext(), R.string.saved_successfully, Toast.LENGTH_SHORT).show()
                        activity?.let { ReviewHelper.notifyStatusSaved(it) }
                    } else {
                        Toast.makeText(requireContext(), R.string.save_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        binding.rvStatuses.adapter = adapter
    }

    private fun setupListeners() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadStatuses()
        }

        binding.btnGrantPermission.setOnClickListener {
            (activity as? MainActivity)?.requestFolderPermission()
        }

        binding.btnEmptyAction.setOnClickListener {
            StorageHelper.openWhatsApp(requireContext(), viewModel.isBusiness.value)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Determine which list to observe based on tabIndex
                val flowToList = when (tabIndex) {
                    0 -> viewModel.imagesList
                    1 -> viewModel.videosList
                    else -> viewModel.savedList
                }

                launch {
                    flowToList.collect { mediaList ->
                        adapter.submitList(mediaList)
                        updateUiState(mediaList)
                    }
                }

                launch {
                    viewModel.isLoading.collect { loading ->
                        binding.swipeRefresh.isRefreshing = loading
                        binding.progressBar.visibility = if (loading && adapter.itemCount == 0) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.isPermissionGranted.collect { _ ->
                        updateUiState(adapter.currentList)
                    }
                }

                launch {
                    combine(viewModel.isMultiSelectMode, viewModel.selectedItems) { multiMode, selected ->
                        Pair(multiMode, selected)
                    }.collect { (multiMode, selected) ->
                        adapter.updateSelectionState(multiMode, selected)
                    }
                }
            }
        }
    }

    private fun updateUiState(currentList: List<StatusMedia>) {
        val hasPermission = viewModel.isPermissionGranted.value
        val isSavedTab = (tabIndex == 2)

        if (!hasPermission && !isSavedTab) {
            binding.layoutPermission.visibility = View.VISIBLE
            binding.layoutEmpty.visibility = View.GONE
            binding.rvStatuses.visibility = View.GONE
        } else {
            binding.layoutPermission.visibility = View.GONE
            if (currentList.isEmpty() && !viewModel.isLoading.value) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvStatuses.visibility = View.GONE

                if (isSavedTab) {
                    binding.tvEmptyTitle.setText(R.string.no_saved_title)
                    binding.tvEmptyDesc.setText(R.string.no_saved_desc)
                    binding.btnEmptyAction.visibility = View.GONE
                } else {
                    binding.tvEmptyTitle.setText(R.string.no_statuses_title)
                    binding.tvEmptyDesc.setText(R.string.no_statuses_desc)
                    binding.btnEmptyAction.visibility = View.VISIBLE
                    binding.btnEmptyAction.setText(
                        if (viewModel.isBusiness.value) R.string.open_business else R.string.open_whatsapp
                    )
                }
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.rvStatuses.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
