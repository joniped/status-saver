package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.R
import com.example.databinding.ActivityMediaViewerBinding
import com.example.model.StatusMedia
import com.example.ui.adapter.MediaPagerAdapter
import com.example.util.ReviewHelper
import com.example.util.StorageHelper
import kotlinx.coroutines.launch

class MediaViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaViewerBinding
    private val mediaList = ArrayList<StatusMedia>()
    private var initialPosition: Int = 0
    private var isFromSavedTab: Boolean = false
    private var isBusiness: Boolean = false

    private var exoPlayer: ExoPlayer? = null
    private lateinit var pagerAdapter: MediaPagerAdapter

    companion object {
        private const val EXTRA_MEDIA_LIST = "extra_media_list"
        private const val EXTRA_INITIAL_POSITION = "extra_initial_position"
        private const val EXTRA_IS_SAVED_TAB = "extra_is_saved_tab"
        private const val EXTRA_IS_BUSINESS = "extra_is_business"

        fun newIntent(
            context: Context,
            mediaList: List<StatusMedia>,
            initialPosition: Int,
            isFromSavedTab: Boolean,
            isBusiness: Boolean
        ): Intent {
            return Intent(context, MediaViewerActivity::class.java).apply {
                putParcelableArrayListExtra(EXTRA_MEDIA_LIST, ArrayList(mediaList))
                putExtra(EXTRA_INITIAL_POSITION, initialPosition)
                putExtra(EXTRA_IS_SAVED_TAB, isFromSavedTab)
                putExtra(EXTRA_IS_BUSINESS, isBusiness)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val density = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.layoutTopBar.updatePadding(top = systemBars.top + (8 * density).toInt())
            binding.cardBottomActions.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + (28 * density).toInt()
            }
            insets
        }

        val incomingList = intent.getParcelableArrayListExtra<StatusMedia>(EXTRA_MEDIA_LIST)
        if (!incomingList.isNullOrEmpty()) {
            mediaList.addAll(incomingList)
        }
        initialPosition = intent.getIntExtra(EXTRA_INITIAL_POSITION, 0)
        isFromSavedTab = intent.getBooleanExtra(EXTRA_IS_SAVED_TAB, false)
        isBusiness = intent.getBooleanExtra(EXTRA_IS_BUSINESS, false)

        if (mediaList.isEmpty()) {
            finish()
            return
        }

        initPlayer()
        setupViewPager()
        setupListeners()
    }

    private fun initPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true
        }
    }

    private fun setupViewPager() {
        pagerAdapter = MediaPagerAdapter(mediaList)
        binding.viewPagerMedia.adapter = pagerAdapter
        binding.viewPagerMedia.offscreenPageLimit = 1

        binding.btnActionDelete.visibility = if (isFromSavedTab) View.VISIBLE else View.GONE

        binding.viewPagerMedia.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateCurrentMedia(position)
            }
        })

        val safePosition = initialPosition.coerceIn(0, mediaList.size - 1)
        binding.viewPagerMedia.setCurrentItem(safePosition, false)
        binding.viewPagerMedia.post {
            updateCurrentMedia(safePosition)
        }
    }

    private fun getCurrentMedia(): StatusMedia? {
        val pos = binding.viewPagerMedia.currentItem
        return if (pos in mediaList.indices) mediaList[pos] else null
    }

    private fun updateCurrentMedia(position: Int) {
        if (position !in mediaList.indices) return
        val currentMedia = mediaList[position]

        // Update Title & Counter
        binding.tvTitle.text = "${position + 1}/${mediaList.size}  •  ${currentMedia.name}"

        // Update Save button text & icon
        if (currentMedia.isSaved) {
            binding.tvActionSaveText.text = getString(R.string.tab_saved)
            binding.ivActionSaveIcon.setImageResource(R.drawable.ic_check)
        } else {
            binding.tvActionSaveText.text = getString(R.string.save)
            binding.ivActionSaveIcon.setImageResource(R.drawable.ic_download)
        }

        // Handle Video Playback
        if (currentMedia.isVideo) {
            binding.viewPagerMedia.post {
                val recyclerView = binding.viewPagerMedia.getChildAt(0) as? RecyclerView
                val holder = recyclerView?.findViewHolderForAdapterPosition(position) as? MediaPagerAdapter.MediaPageViewHolder
                if (holder != null && exoPlayer != null) {
                    holder.getPlayerView().player = exoPlayer
                    val mediaItem = MediaItem.fromUri(currentMedia.uri)
                    exoPlayer?.setMediaItem(mediaItem)
                    exoPlayer?.prepare()
                    exoPlayer?.play()
                }
            }
        } else {
            exoPlayer?.pause()
            exoPlayer?.clearMediaItems()
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnActionSave.setOnClickListener {
            val currentMedia = getCurrentMedia() ?: return@setOnClickListener
            lifecycleScope.launch {
                val ok = StorageHelper.saveStatusToGallery(this@MediaViewerActivity, currentMedia)
                if (ok) {
                    Toast.makeText(this@MediaViewerActivity, R.string.saved_successfully, Toast.LENGTH_SHORT).show()
                    currentMedia.isSaved = true
                    binding.tvActionSaveText.text = getString(R.string.tab_saved)
                    binding.ivActionSaveIcon.setImageResource(R.drawable.ic_check)
                    ReviewHelper.notifyStatusSaved(this@MediaViewerActivity)
                } else {
                    Toast.makeText(this@MediaViewerActivity, R.string.save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnActionRepost.setOnClickListener {
            val currentMedia = getCurrentMedia() ?: return@setOnClickListener
            StorageHelper.repostStatus(this, currentMedia, isBusiness)
        }

        binding.btnActionShare.setOnClickListener {
            val currentMedia = getCurrentMedia() ?: return@setOnClickListener
            StorageHelper.shareStatus(this, currentMedia)
        }

        binding.btnActionDelete.setOnClickListener {
            val currentPos = binding.viewPagerMedia.currentItem
            val currentMedia = getCurrentMedia() ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_desc)
                .setPositiveButton(R.string.delete) { _, _ ->
                    lifecycleScope.launch {
                        val ok = StorageHelper.deleteSavedStatus(this@MediaViewerActivity, currentMedia)
                        if (ok) {
                            Toast.makeText(this@MediaViewerActivity, "Status deleted", Toast.LENGTH_SHORT).show()
                            mediaList.removeAt(currentPos)
                            if (mediaList.isEmpty()) {
                                setResult(RESULT_OK)
                                finish()
                            } else {
                                pagerAdapter.notifyItemRemoved(currentPos)
                                val newPos = currentPos.coerceAtMost(mediaList.size - 1)
                                binding.viewPagerMedia.setCurrentItem(newPos, true)
                                updateCurrentMedia(newPos)
                                setResult(RESULT_OK)
                            }
                        } else {
                            Toast.makeText(this@MediaViewerActivity, "Failed to delete status", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
