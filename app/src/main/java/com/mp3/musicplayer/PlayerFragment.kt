package com.mp3.musicplayer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.ListenableFuture
import com.mp3.musicplayer.databinding.FragmentPlayerBinding
import java.util.concurrent.TimeUnit

class PlayerFragment : Fragment(R.layout.fragment_player) {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private lateinit var mediaControllerFuture: ListenableFuture<MediaController>
    private val mediaController: MediaController? get() = if (mediaControllerFuture.isDone) mediaControllerFuture.get() else null

    private val handler = Handler(Looper.getMainLooper())
    private var progressUpdateRunnable: Runnable? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemChanged(mediaItem: MediaItem?, reason: Int) {
            updateCurrentSongUI(mediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseButton(isPlaying)
            if (isPlaying) {
                startProgressUpdate()
            } else {
                stopProgressUpdate()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlayerBinding.bind(view)
        mediaControllerFuture = (requireActivity() as MainActivity).mediaControllerFuture

        mediaControllerFuture.addListener({
            setupPlayer()
            setupClickListeners()
        }, requireContext().mainExecutor)
    }

    private fun setupPlayer() {
        mediaController?.addListener(playerListener)
        updatePlayPauseButton(mediaController?.isPlaying ?: false)
        updateCurrentSongUI(mediaController?.currentMediaItem)

        // Immediately update progress
        val currentPosition = mediaController?.currentPosition ?: 0
        val duration = mediaController?.duration ?: 0
        binding.seekbarProgress.max = duration.toInt()
        binding.seekbarProgress.progress = currentPosition.toInt()
        binding.textCurrentTime.text = formatDuration(currentPosition)
        binding.textTotalTime.text = formatDuration(duration)

        if (mediaController?.isPlaying == true) {
            startProgressUpdate()
        }
    }

    private fun setupClickListeners() {
        binding.buttonPlayPause.setOnClickListener {
            mediaController?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        }
        binding.buttonNext.setOnClickListener { mediaController?.seekToNextMediaItem() }
        binding.buttonPrevious.setOnClickListener { mediaController?.seekToPreviousMediaItem() }

        binding.buttonDelete.setOnClickListener {
            val sessionCommand = SessionCommand("DELETE_CURRENT_SONG", Bundle.EMPTY)
            mediaController?.sendCustomCommand(sessionCommand, Bundle.EMPTY)
        }

        binding.seekbarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaController?.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        binding.buttonPlayPause.setImageResource(iconRes)
    }

    private fun updateCurrentSongUI(mediaItem: MediaItem?) {
        if (mediaItem?.mediaMetadata != null) {
            binding.textSongTitle.text = mediaItem.mediaMetadata.title
            binding.textSongArtist.text = mediaItem.mediaMetadata.artist
            val duration = mediaController?.duration ?: 0
            binding.seekbarProgress.max = duration.toInt()
            binding.textTotalTime.text = formatDuration(duration)
            binding.imageAlbumArt.setImageResource(R.drawable.ic_music_note)
        }
    }

    private fun startProgressUpdate() {
        progressUpdateRunnable = Runnable {
            mediaController?.let {
                val currentPosition = it.currentPosition
                binding.seekbarProgress.progress = currentPosition.toInt()
                binding.textCurrentTime.text = formatDuration(currentPosition)
                handler.postDelayed(progressUpdateRunnable!!, 1000)
            }
        }
        handler.post(progressUpdateRunnable!!)
    }

    private fun stopProgressUpdate() {
        progressUpdateRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun formatDuration(duration: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaController?.removeListener(playerListener)
        stopProgressUpdate()
        _binding = null
    }
}