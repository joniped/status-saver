package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.example.ads.UnityAdsManager
import com.example.databinding.ActivityMainBinding
import com.example.databinding.DialogHowToUseBinding
import com.example.databinding.DialogLegalInfoBinding
import com.example.ui.fragment.StatusListFragment
import com.example.ui.onboarding.OnboardingActivity
import com.example.util.ReviewHelper
import com.example.util.StorageHelper
import com.example.viewmodel.StatusViewModel
import com.google.android.material.tabs.TabLayoutMediator
import com.unity3d.services.banners.BannerView
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: StatusViewModel by viewModels()

    private var bannerView: BannerView? = null

    private val folderPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                StorageHelper.takePersistablePermission(
                    this,
                    uri,
                    result.data,
                    viewModel.isBusiness.value
                )
                viewModel.loadStatuses()
                Toast.makeText(this, "Permission granted successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val statusBarGreen = ContextCompat.getColor(this, R.color.primary_dark)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(statusBarGreen),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        if (!OnboardingActivity.isOnboardingCompleted(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set matching toolbar green status bar color with white text/icons
        window.statusBarColor = statusBarGreen
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        // Handle WindowInsets for Edge-to-Edge so green status bar covers the top area seamlessly
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.statusBarScrim.updateLayoutParams {
                height = systemBars.top
            }
            binding.bannerAdContainer.updatePadding(bottom = systemBars.bottom)
            insets
        }

        setupToolbarAndToggle()
        setupViewPagerAndTabs()
        setupMultiSelectBar()
        setupBannerAd()
        setupBackHandler()

        observeViewModel()

        // Initial load
        viewModel.loadStatuses()
    }

    private fun setupToolbarAndToggle() {
        binding.toolbar.inflateMenu(R.menu.main_menu)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_direct_share -> {
                    ReviewHelper.directShareApp(this)
                    true
                }
                R.id.action_theme_toggle -> {
                    toggleTheme()
                    true
                }
                R.id.action_how_to_use -> {
                    showHowToUseDialog()
                    true
                }
                R.id.action_privacy_policy -> {
                    showLegalDialog(
                        getString(R.string.privacy_policy_title),
                        getString(R.string.privacy_policy_content)
                    )
                    true
                }
                R.id.action_terms -> {
                    showLegalDialog(
                        getString(R.string.terms_of_service_title),
                        getString(R.string.terms_of_service_content)
                    )
                    true
                }
                else -> false
            }
        }

        binding.toggleAppType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_toggle_whatsapp -> viewModel.setBusiness(false)
                    R.id.btn_toggle_business -> viewModel.setBusiness(true)
                }
            }
        }
    }

    private fun toggleTheme() {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val targetMode = if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(targetMode)
    }

    private fun showHowToUseDialog() {
        val dialogBinding = DialogHowToUseBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialogBinding.btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showLegalDialog(title: String, content: String) {
        val dialogBinding = DialogLegalInfoBinding.inflate(layoutInflater)
        dialogBinding.tvDialogTitle.text = title
        dialogBinding.tvDialogContent.text = content
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialogBinding.btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun setupViewPagerAndTabs() {
        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 3
            override fun createFragment(position: Int): Fragment {
                return StatusListFragment.newInstance(position)
            }
        }

        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 2

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.setText(R.string.tab_images)
                    tab.setIcon(R.drawable.ic_image)
                }
                1 -> {
                    tab.setText(R.string.tab_videos)
                    tab.setIcon(R.drawable.ic_video)
                }
                2 -> {
                    tab.setText(R.string.tab_saved)
                    tab.setIcon(R.drawable.ic_download)
                }
            }
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (viewModel.isMultiSelectMode.value) {
                    viewModel.clearSelection()
                }
            }
        })
    }

    private fun setupMultiSelectBar() {
        binding.btnClearSelection.setOnClickListener {
            viewModel.clearSelection()
        }

        binding.btnSelectAll.setOnClickListener {
            viewModel.selectAll(binding.viewPager.currentItem)
        }

        binding.btnBatchSave.setOnClickListener {
            viewModel.batchSave { savedCount, total ->
                Toast.makeText(
                    this,
                    "Saved $savedCount of $total statuses to Gallery",
                    Toast.LENGTH_SHORT
                ).show()
                if (savedCount > 0) {
                    ReviewHelper.notifyStatusSaved(this)
                }
            }
        }

        binding.btnBatchShare.setOnClickListener {
            viewModel.batchShare()
        }

        binding.btnBatchDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_desc)
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewModel.batchDeleteSaved { count ->
                        Toast.makeText(this, "Deleted $count statuses", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun setupBannerAd() {
        // Create and center sticky 320x50 Unity BannerView
        try {
            binding.bannerAdContainer.visibility = View.GONE
            bannerView = UnityAdsManager.createBanner(
                this,
                onLoaded = {
                    binding.bannerAdContainer.visibility = View.VISIBLE
                },
                onFailed = { _ ->
                    binding.bannerAdContainer.visibility = View.GONE
                }
            )
            binding.bannerAdContainer.removeAllViews()
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            binding.bannerAdContainer.addView(bannerView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            binding.bannerAdContainer.visibility = View.GONE
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.isMultiSelectMode.value) {
                    viewModel.clearSelection()
                } else {
                    showExitDialog()
                }
            }
        })
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.exit_dialog_title)
            .setMessage(R.string.exit_dialog_message)
            .setPositiveButton(R.string.exit) { _, _ ->
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.share_app) { _, _ ->
                ReviewHelper.directShareApp(this)
            }
            .show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.isMultiSelectMode, viewModel.selectedItems) { multiMode, selected ->
                        Pair(multiMode, selected)
                    }.collect { (multiMode, selected) ->
                        if (multiMode) {
                            binding.toolbar.visibility = View.GONE
                            binding.layoutSelectionBar.visibility = View.VISIBLE
                            binding.layoutBatchActions.visibility = View.VISIBLE
                            binding.tvSelectionCount.text = getString(R.string.selected_count, selected.size)

                            // Show delete only if on the Saved tab (index 2)
                            val isSavedTab = (binding.viewPager.currentItem == 2)
                            binding.btnBatchDelete.visibility = if (isSavedTab) View.VISIBLE else View.GONE
                            binding.btnBatchSave.visibility = if (isSavedTab) View.GONE else View.VISIBLE
                        } else {
                            binding.toolbar.visibility = View.VISIBLE
                            binding.layoutSelectionBar.visibility = View.GONE
                            binding.layoutBatchActions.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    fun requestFolderPermission() {
        try {
            val intent = StorageHelper.getOpenDocumentTreeIntent(this, viewModel.isBusiness.value)
            folderPermissionLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open folder picker: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerView?.destroy()
        bannerView = null
    }
}
