package com.mp3.musicplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.NavHostFragment
import com.google.common.util.concurrent.ListenableFuture
import android.content.ComponentName
import com.google.common.util.concurrent.MoreExecutors
import com.mp3.musicplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaControllerFuture: ListenableFuture<MediaController>

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                val contentResolver = applicationContext.contentResolver
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                saveMusicFolderUri(uri)
                recreate()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val savedUri = getMusicFolderUri()
        if (savedUri == null) {
            binding.selectFolderButton.isVisible = true
            binding.navHostFragment.isVisible = false
        } else {
            binding.selectFolderButton.isVisible = false
            binding.navHostFragment.isVisible = true
        }

        binding.selectFolderButton.setOnClickListener {
            folderPickerLauncher.launch(null)
        }
    }

    override fun onStart() {
        super.onStart()
        initializeMediaController()
    }

    override fun onStop() {
        super.onStop()
        releaseMediaController()
    }

    fun playSongs(songs: List<Song>, startIndex: Int) {
        if (mediaControllerFuture.isDone) {
            val mediaController = mediaControllerFuture.get()
            val mediaItems = songs.map { song ->
                MediaItem.Builder()
                    .setUri(song.uri)
                    .setMediaId(song.uri.toString()) // Use URI as a unique ID
                    .setMediaMetadata(
                        MediaItem.MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .build()
                    )
                    .build()
            }
            mediaController.setMediaItems(mediaItems, startIndex, 0)
            mediaController.prepare()
            mediaController.play()

            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            navHostFragment.navController.navigate(R.id.action_playlist_to_player)
        }
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture.addListener(
            {
                // Controller is ready
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun releaseMediaController() {
        MediaController.releaseFuture(mediaControllerFuture)
    }

    private fun saveMusicFolderUri(uri: Uri) {
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("music_folder_uri", uri.toString()).apply()
    }

    private fun getMusicFolderUri(): Uri? {
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val uriString = sharedPreferences.getString("music_folder_uri", null)
        return if (uriString != null) Uri.parse(uriString) else null
    }
}