package com.mp3.musicplayer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.mp3.musicplayer.databinding.FragmentPlaylistBinding

class PlaylistFragment : Fragment(R.layout.fragment_playlist) {

    private val musicViewModel: MusicViewModel by activityViewModels()
    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    private lateinit var songAdapter: SongAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlaylistBinding.bind(view)

        setupRecyclerView()
        setupObservers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter { song ->
            // Saat sebuah lagu di klik, perintahkan ViewModel untuk memutarnya
            musicViewModel.playSongAtIndex(songAdapter.currentList.indexOf(song))
        }
        binding.recyclerViewPlaylist.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupObservers() {
        // Amati perubahan pada daftar lagu
        musicViewModel.playlist.observe(viewLifecycleOwner) { playlist ->
            songAdapter.submitList(playlist)
        }

        // Amati perubahan pada lagu yang sedang diputar untuk menyorot item di daftar
        musicViewModel.currentSong.observe(viewLifecycleOwner) { currentSong ->
            songAdapter.updatePlayingState(currentSong?.id ?: -1)
        }
    }
}