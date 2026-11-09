package com.example.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.R
import com.example.databinding.ItemStatusCardBinding
import com.example.model.StatusMedia

class StatusAdapter(
    private val onItemClick: (StatusMedia) -> Unit,
    private val onItemLongClick: (StatusMedia) -> Unit,
    private val onQuickDownloadClick: (StatusMedia) -> Unit
) : ListAdapter<StatusMedia, StatusAdapter.StatusViewHolder>(DiffCallback) {

    private var isMultiSelectMode: Boolean = false
    private var selectedSet: Set<String> = emptySet()

    fun updateSelectionState(multiSelectMode: Boolean, selectedMedia: Set<StatusMedia>) {
        this.isMultiSelectMode = multiSelectMode
        this.selectedSet = selectedMedia.map { it.name }.toSet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val binding = ItemStatusCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StatusViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StatusViewHolder(
        private val binding: ItemStatusCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StatusMedia) {
            val isSelected = selectedSet.contains(item.name)

            // 1. Load thumbnail with Glide
            Glide.with(binding.root.context)
                .load(item.file ?: item.uri)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.ivThumbnail)

            // 2. Video indicator
            binding.badgeVideo.visibility = if (item.isVideo) View.VISIBLE else View.GONE

            // 3. Saved indicator
            binding.badgeSaved.visibility = if (item.isSaved) View.VISIBLE else View.GONE

            // 4. Multi-select UI state
            if (isMultiSelectMode) {
                binding.ivCheckbox.visibility = View.VISIBLE
                if (isSelected) {
                    binding.ivCheckbox.setImageResource(R.drawable.ic_check)
                    binding.ivCheckbox.setBackgroundResource(R.drawable.bg_circle_button)
                    binding.ivCheckbox.backgroundTintList =
                        binding.root.context.getColorStateList(R.color.primary)
                    binding.selectionOverlay.visibility = View.VISIBLE
                } else {
                    binding.ivCheckbox.setImageDrawable(null)
                    binding.ivCheckbox.setBackgroundResource(R.drawable.bg_circle_button)
                    binding.ivCheckbox.backgroundTintList =
                        binding.root.context.getColorStateList(R.color.overlay_black_60)
                    binding.selectionOverlay.visibility = View.GONE
                }
                binding.btnQuickDownload.visibility = View.GONE
            } else {
                binding.ivCheckbox.visibility = View.GONE
                binding.selectionOverlay.visibility = View.GONE
                // Hide download button if already saved
                binding.btnQuickDownload.visibility = if (item.isSaved) View.GONE else View.VISIBLE
            }

            // 5. Click listeners
            binding.root.setOnClickListener {
                if (isMultiSelectMode) {
                    onItemLongClick(item)
                } else {
                    onItemClick(item)
                }
            }

            binding.root.setOnLongClickListener {
                onItemLongClick(item)
                true
            }

            binding.btnQuickDownload.setOnClickListener {
                onQuickDownloadClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<StatusMedia>() {
        override fun areItemsTheSame(oldItem: StatusMedia, newItem: StatusMedia): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: StatusMedia, newItem: StatusMedia): Boolean {
            return oldItem == newItem
        }
    }
}
