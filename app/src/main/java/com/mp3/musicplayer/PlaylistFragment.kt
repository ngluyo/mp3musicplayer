package com.mp3.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.mp3.musicplayer.databinding.FragmentPlaylistBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistFragment : Fragment(R.layout.fragment_playlist) {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!
    private lateinit var songAdapter: SongAdapter

    private val deleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MusicService.ACTION_SONG_DELETED) {
                val deletedUriString = intent.getStringExtra(MusicService.EXTRA_DELETED_SONG_URI)
                if (deletedUriString != null) {
                    val currentList = songAdapter.currentList.toMutableList()
                    currentList.removeAll { it.uri.toString() == deletedUriString }
                    songAdapter.submitList(currentList)
                    Toast.makeText(requireContext(), "Lagu dihapus dari daftar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(deleteReceiver, IntentFilter(MusicService.ACTION_SONG_DELETED))
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(deleteReceiver)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlaylistBinding.bind(view)

        setupRecyclerView()
        loadSongsFromSelectedFolder()
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter { song ->
            val clickedIndex = songAdapter.currentList.indexOf(song)
            (activity as? MainActivity)?.playSongs(songAdapter.currentList, clickedIndex)
        }
        binding.recyclerViewPlaylist.apply {
            adapter = songAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun loadSongsFromSelectedFolder() {
        val folderUri = getMusicFolderUri()
        if (folderUri == null) {
            // Handle case where folder is not selected, maybe navigate back or show a message
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val songList = mutableListOf<Song>()
            val contentResolver = requireContext().contentResolver
            val retriever = MediaMetadataRetriever()

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri))
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)

            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idColumn)
                    val name = cursor.getString(nameColumn)
                    val mimeType = cursor.getString(mimeColumn)

                    if (mimeType == "audio/mpeg" && name.endsWith(".mp3", true)) {
                        val songUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        try {
                            retriever.setDataSource(requireContext(), songUri)
                            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: name
                            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            songList.add(Song(System.currentTimeMillis(), title, artist, duration, songUri))
                        } catch (e: Exception) {
                            // Could not read metadata, add with filename as title
                            songList.add(Song(System.currentTimeMillis(), name, "Unknown Artist", 0L, songUri))
                            e.printStackTrace()
                        }
                    }
                }
            }
            retriever.release()

            withContext(Dispatchers.Main) {
                songAdapter.submitList(songList)
            }
        }
    }

    private fun getMusicFolderUri(): Uri? {
        val sharedPreferences = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val uriString = sharedPreferences.getString("music_folder_uri", null)
        return if (uriString != null) Uri.parse(uriString) else null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}