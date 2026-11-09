package com.example.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.databinding.ItemMediaPageBinding
import com.example.model.StatusMedia

class MediaPagerAdapter(
    private val mediaList: List<StatusMedia>
) : RecyclerView.Adapter<MediaPagerAdapter.MediaPageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaPageViewHolder {
        val binding = ItemMediaPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MediaPageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaPageViewHolder, position: Int) {
        holder.bind(mediaList[position])
    }

    override fun getItemCount(): Int = mediaList.size

    inner class MediaPageViewHolder(
        val binding: ItemMediaPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StatusMedia) {
            if (item.isVideo) {
                binding.ivImagePage.visibility = View.GONE
                binding.playerViewPage.visibility = View.VISIBLE
            } else {
                binding.playerViewPage.visibility = View.GONE
                binding.playerViewPage.player = null
                binding.ivImagePage.visibility = View.VISIBLE

                Glide.with(binding.root.context)
                    .load(item.file ?: item.uri)
                    .fitCenter()
                    .into(binding.ivImagePage)
            }
        }

        fun getPlayerView(): PlayerView {
            return binding.playerViewPage
        }
    }
}
