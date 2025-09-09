package com.mp3.musicplayer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mp3.musicplayer.databinding.ListItemSongBinding
import java.util.concurrent.TimeUnit

// UBAH: Sekarang menggunakan ListAdapter untuk performa yang lebih baik
class SongAdapter(private val onItemClicked: (Song) -> Unit) :
    ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    private var currentPlayingSongId: Long = -1

    @SuppressLint("NotifyDataSetChanged")
    fun updatePlayingState(playingSongId: Long) {
        currentPlayingSongId = playingSongId
        // Kita masih butuh notifyDataSetChanged di sini untuk menggambar ulang semua item
        // saat status lagu yang diputar berubah, agar highlight-nya pindah.
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding =
            ListItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = getItem(position) // UBAH: Gunakan getItem() dari ListAdapter
        holder.bind(song)
    }

    inner class SongViewHolder(private val binding: ListItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onItemClicked(getItem(bindingAdapterPosition))
                }
            }
        }

        fun bind(song: Song) {
            binding.textItemTitle.text = song.title
            binding.textItemArtist.text = song.artist

            val minutes = TimeUnit.MILLISECONDS.toMinutes(song.duration)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(song.duration) -
                    TimeUnit.MINUTES.toSeconds(minutes)
            binding.textItemDuration.text = String.format("%02d:%02d", minutes, seconds)

            if (song.id == currentPlayingSongId) {
                binding.imagePlayingIndicator.visibility = View.VISIBLE
                binding.textItemTitle.setTextColor(binding.root.context.getColor(R.color.md_theme_primary))
            } else {
                binding.imagePlayingIndicator.visibility = View.INVISIBLE
                binding.textItemTitle.setTextColor(binding.root.context.getColor(R.color.md_theme_onSurface))
            }
        }
    }
}

// KELAS BARU: Diperlukan oleh ListAdapter untuk membandingkan item
class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
    override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
        return oldItem == newItem
    }
}