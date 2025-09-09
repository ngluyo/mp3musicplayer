package com.mp3.musicplayer

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.mp3.musicplayer.databinding.FragmentPlayerBinding
import java.util.concurrent.TimeUnit

class PlayerFragment : Fragment(R.layout.fragment_player) {

    // ViewModel yang di-share dengan Activity dan fragment lain
    private val musicViewModel: MusicViewModel by activityViewModels()

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlayerBinding.bind(view)

        setupClickListeners()
        setupObservers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Mencegah memory leak
    }

    private fun setupClickListeners() {
        binding.buttonPlayPause.setOnClickListener { musicViewModel.playPause() }
        binding.buttonNext.setOnClickListener { musicViewModel.playNextSong() }
        binding.buttonPrevious.setOnClickListener { musicViewModel.playPreviousSong() }
        binding.buttonShuffle.setOnClickListener { musicViewModel.toggleShuffle() }
        binding.buttonRepeat.setOnClickListener { musicViewModel.toggleRepeat() }
        binding.buttonDelete.setOnClickListener {
            // Kita akan tangani dialog dari MainActivity untuk menjaga Fragment tetap simpel
            // Di sini kita hanya perlu memanggil fungsi ViewModel
            musicViewModel.deleteCurrentSong()
        }

        binding.seekbarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicViewModel.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupObservers() {
        musicViewModel.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            updatePlayPauseButton(isPlaying)
        }
        musicViewModel.currentSong.observe(viewLifecycleOwner) { song ->
            updateCurrentSongUI(song)
        }
        musicViewModel.currentPosition.observe(viewLifecycleOwner) { position ->
            updateProgress(position)
        }
        musicViewModel.shuffleEnabled.observe(viewLifecycleOwner) { isEnabled ->
            updateShuffleButton(isEnabled)
        }
        musicViewModel.repeatEnabled.observe(viewLifecycleOwner) { isEnabled ->
            updateRepeatButton(isEnabled)
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        binding.buttonPlayPause.setImageResource(iconRes)
    }

    private fun updateCurrentSongUI(song: Song?) {
        if (song != null) {
            binding.textSongTitle.text = song.title
            binding.textSongArtist.text = song.artist
            binding.seekbarProgress.max = song.duration.toInt()
            binding.textTotalTime.text = formatDuration(song.duration)
            // Di aplikasi nyata, gunakan library seperti Glide atau Coil untuk memuat gambar
            // binding.imageAlbumArt.load(song.albumArtUri) { error(R.drawable.ic_music_note) }
            binding.imageAlbumArt.setImageResource(R.drawable.ic_music_note)
        } else {
            binding.textSongTitle.text = getString(R.string.default_song_title)
            binding.textSongArtist.text = getString(R.string.default_song_artist)
            binding.imageAlbumArt.setImageResource(R.drawable.ic_music_note)
            binding.seekbarProgress.progress = 0
            binding.seekbarProgress.max = 100
            binding.textCurrentTime.text = getString(R.string.default_time)
            binding.textTotalTime.text = getString(R.string.default_time)
        }
    }

    private fun updateProgress(position: Int) {
        binding.seekbarProgress.progress = position
        binding.textCurrentTime.text = formatDuration(position.toLong())
    }

    private fun updateShuffleButton(isEnabled: Boolean) {
        binding.buttonShuffle.alpha = if (isEnabled) 1.0f else 0.5f
    }

    private fun updateRepeatButton(isEnabled: Boolean) {
        binding.buttonRepeat.alpha = if (isEnabled) 1.0f else 0.5f
    }

    private fun formatDuration(duration: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}