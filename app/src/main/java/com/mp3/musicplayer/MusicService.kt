package com.mp3.musicplayer

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class MusicService : MediaLibraryService() {

    companion object {
        const val ACTION_SONG_DELETED = "com.ngluyo.deleteplayer.SONG_DELETED"
        const val EXTRA_DELETED_SONG_URI = "EXTRA_DELETED_SONG_URI"
    }

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession

    // The callback for MediaLibrarySession
    private val mediaLibrarySessionCallback = object : MediaLibrarySession.Callback {
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == "DELETE_CURRENT_SONG") {
                handleDeleteCurrentSong()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun handleDeleteCurrentSong() {
        val currentMediaItem = player.currentMediaItem ?: return
        val itemIndexToDelete = player.currentMediaItemIndex
        val title = currentMediaItem.mediaMetadata.title ?: "Lagu"

        // Run deletion on a background thread
        Thread {
            try {
                val uriToDelete = currentMediaItem.mediaId.toUri()
                val rowsDeleted = contentResolver.delete(uriToDelete, null, null)
                if (rowsDeleted > 0) {
                    // Send a broadcast to notify the UI
                    val intent = Intent(ACTION_SONG_DELETED).apply {
                        putExtra(EXTRA_DELETED_SONG_URI, uriToDelete.toString())
                    }
                    LocalBroadcastManager.getInstance(this@MusicService).sendBroadcast(intent)
                }
            } catch (e: Exception) {
                // Handle potential errors
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "Gagal menghapus '${title}'", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }.start()

        // Remove from player playlist on the main thread
        Handler(Looper.getMainLooper()).post {
            player.removeMediaItem(itemIndexToDelete)
            // ExoPlayer will automatically handle moving to the next track
            // if the current one is removed.
        }
    }

    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        initializeSession()
    }

    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, mediaLibrarySessionCallback)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        player.release()
        mediaLibrarySession.release()
        super.onDestroy()
    }
}