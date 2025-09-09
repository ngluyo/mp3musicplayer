package com.mp3.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.app.Service
import android.content.Intent
import android.content.IntentSender
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.File
import kotlin.random.Random

class MusicService : Service(), MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener {

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var progressRunnable: Runnable

    private var playlist = mutableListOf<Song>()
    private var originalPlaylist = mutableListOf<Song>()
    private var currentSongIndex = -1
    private var isShuffleEnabled = false
    private var isRepeatEnabled = false

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _currentPosition = MutableLiveData<Int>()
    val currentPosition: LiveData<Int> = _currentPosition

    private val _shuffleEnabled = MutableLiveData<Boolean>()
    val shuffleEnabled: LiveData<Boolean> = _shuffleEnabled

    private val _repeatEnabled = MutableLiveData<Boolean>()
    val repeatEnabled: LiveData<Boolean> = _repeatEnabled

    private val _playlistChanged = MutableLiveData<Boolean>()
    val playlistChanged: LiveData<Boolean> = _playlistChanged

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "MusicPlayerChannel"
        const val ACTION_PLAY_PAUSE = "com.mp3.musicplayer.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.mp3.musicplayer.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.mp3.musicplayer.ACTION_PREVIOUS"
        const val ACTION_PLAY_SONG_AT_INDEX = "com.mp3.musicplayer.ACTION_PLAY_AT_INDEX"
        const val EXTRA_SONG_INDEX = "EXTRA_SONG_INDEX"
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        initializeMediaPlayer()
        createNotificationChannel()
        // PERBAIKAN: Inisialisasi LiveData values
        _isPlaying.postValue(false)
        _currentSong.postValue(null)
        _shuffleEnabled.postValue(false)
        _repeatEnabled.postValue(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_SONG_AT_INDEX -> {
                val index = intent.getIntExtra(EXTRA_SONG_INDEX, -1)
                if (index != -1) {
                    playSongAtIndex(index)
                }
            }
            ACTION_PLAY_PAUSE -> playPause()
            ACTION_NEXT -> playNextSong()
            ACTION_PREVIOUS -> playPreviousSong()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // PERBAIKAN: Cleanup yang lebih aman
        handler.removeCallbacks(progressRunnable)
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            reset()
            release()
        }
        mediaPlayer = null

        // Reset semua state
        _isPlaying.postValue(false)
        _currentSong.postValue(null)
        currentSongIndex = -1
    }

    override fun onPrepared(mp: MediaPlayer?) {
        _isPlaying.postValue(true)
        mp?.start()
        startProgressUpdate()
        updateNotification()
    }

    override fun onCompletion(mp: MediaPlayer?) {
        stopProgressUpdate()
        if (isRepeatEnabled) {
            playSongAtIndex(currentSongIndex)
        } else {
            playNextSong()
        }
    }

    // Override onTaskRemoved untuk handle saat app di-swipe dari recent apps
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Jika tidak sedang playing, stop service
        if (_isPlaying.value != true) {
            stopServiceCompletely()
        }
        // Jika sedang playing, biarkan service tetap jalan dengan notifikasi
    }

    fun setPlaylist(songs: List<Song>) {
        originalPlaylist.assign(songs)
        playlist.assign(songs)
        currentSongIndex = if (playlist.isEmpty()) -1 else 0
        _playlistChanged.postValue(true)
    }

    fun getPlaylist(): List<Song> {
        return playlist
    }

    fun playSongAtIndex(index: Int) {
        if (index in playlist.indices) {
            currentSongIndex = index
            val song = playlist[currentSongIndex]
            _currentSong.postValue(song)
            prepareSong(song)
        }
    }

    fun playPause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            _isPlaying.postValue(false)
            stopProgressUpdate()

            // PERBAIKAN: Stop foreground dan buat notifikasi bisa ditutup saat pause
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }

            // Update notifikasi dengan ongoing = false agar bisa ditutup
            updateNotificationPausedState()

        } else {
            if (currentSongIndex == -1 && playlist.isNotEmpty()) {
                currentSongIndex = 0
                playSongAtIndex(currentSongIndex)
            } else if (currentSongIndex != -1) {
                mediaPlayer?.start()
                _isPlaying.postValue(true)
                startProgressUpdate()
                // Jadikan foreground lagi saat play
                startForeground(NOTIFICATION_ID, createNotification(currentSong.value))
            }
        }
    }

    fun playNextSong() {
        if (playlist.isEmpty()) return
        currentSongIndex = if (isShuffleEnabled) {
            Random.nextInt(playlist.size)
        } else {
            (currentSongIndex + 1) % playlist.size
        }
        playSongAtIndex(currentSongIndex)
    }

    fun playPreviousSong() {
        if (playlist.isEmpty()) return
        currentSongIndex = when {
            isShuffleEnabled -> Random.nextInt(playlist.size)
            currentSongIndex - 1 < 0 -> playlist.size - 1
            else -> currentSongIndex - 1
        }
        playSongAtIndex(currentSongIndex)
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        _shuffleEnabled.postValue(isShuffleEnabled)
        if (isShuffleEnabled) {
            val current = playlist.getOrNull(currentSongIndex)
            playlist.shuffle()
            current?.let { currentSongIndex = playlist.indexOf(it) }
        } else {
            val current = playlist.getOrNull(currentSongIndex)
            playlist.assign(originalPlaylist)
            current?.let { currentSongIndex = playlist.indexOf(it) }
        }
        _playlistChanged.postValue(true)
    }

    fun toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled
        _repeatEnabled.postValue(isRepeatEnabled)
    }

    fun deleteCurrentSong(): IntentSender? {
        if (currentSongIndex == -1) return null
        val songToDelete = playlist[currentSongIndex]
        try {
            contentResolver.delete(songToDelete.uri, null, null)
            handleSuccessfulDeletion()
            return null
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rse = e as? RecoverableSecurityException ?: throw e
                return rse.userAction.actionIntent.intentSender
            }
            return null
        }
    }

    fun handleSuccessfulDeletion() {
        if (currentSongIndex == -1) return
        val removedIndex = currentSongIndex
        val songToDelete = playlist[currentSongIndex]

        originalPlaylist.remove(songToDelete)
        playlist.removeAt(removedIndex)

        if (playlist.isEmpty()) {
            stopPlayback()
        } else {
            currentSongIndex = if (removedIndex >= playlist.size) 0 else removedIndex
            playSongAtIndex(currentSongIndex)
        }
        _playlistChanged.postValue(true)
    }

    // PERBAIKAN: Method untuk handle saat aplikasi ditutup
    fun onAppClosed() {
        // Jika sedang tidak playing, stop service
        if (_isPlaying.value != true) {
            stopServiceCompletely()
        }
    }

    // PERBAIKAN: Method untuk stop service complete
    private fun stopServiceCompletely() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            reset()
            release()
        }
        mediaPlayer = null

        _isPlaying.postValue(false)
        _currentSong.postValue(null)
        currentSongIndex = -1

        // Stop foreground dan hapus notifikasi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        // Stop service
        stopSelf()
    }

    private fun stopPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.reset()
        _isPlaying.postValue(false)
        _currentSong.postValue(null)
        currentSongIndex = -1

        // Stop service completely saat tidak ada lagu
        stopServiceCompletely()
    }

    private fun initializeMediaPlayer() {
        // PERBAIKAN: Pastikan MediaPlayer direset jika sudah ada
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener(this@MusicService)
            setOnCompletionListener(this@MusicService)
            setOnErrorListener { mp, what, extra ->
                // PERBAIKAN: Handle error MediaPlayer
                android.util.Log.e("MusicService", "MediaPlayer error: what=$what, extra=$extra")
                // Reset MediaPlayer jika error
                try {
                    mp.reset()
                } catch (e: Exception) {
                    android.util.Log.e("MusicService", "Error resetting MediaPlayer", e)
                }
                false // Return false untuk menunjukkan error tidak di-handle sepenuhnya
            }
        }
    }

    private fun prepareSong(song: Song) {
        mediaPlayer?.apply {
            reset()
            try {
                setDataSource(applicationContext, song.uri)
                prepareAsync()
                startForeground(NOTIFICATION_ID, createNotification(song))
            } catch (e: Exception) {
                // PERBAIKAN: Handle error dengan lebih baik
                android.util.Log.e("MusicService", "Error preparing song: ${song.title}", e)
                // Coba reinitialize MediaPlayer jika error
                initializeMediaPlayer()
                _isPlaying.postValue(false)
            }
        } ?: run {
            // PERBAIKAN: Jika MediaPlayer null, reinitialize
            android.util.Log.w("MusicService", "MediaPlayer is null, reinitializing...")
            initializeMediaPlayer()
            // Coba lagi setelah reinitialize
            mediaPlayer?.apply {
                reset()
                try {
                    setDataSource(applicationContext, song.uri)
                    prepareAsync()
                    startForeground(NOTIFICATION_ID, createNotification(song))
                } catch (e: Exception) {
                    android.util.Log.e("MusicService", "Error preparing song after reinit: ${song.title}", e)
                    _isPlaying.postValue(false)
                }
            }
        }
    }

    private fun startProgressUpdate() {
        progressRunnable = Runnable {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    _currentPosition.postValue(it.currentPosition)
                    handler.postDelayed(progressRunnable, 1000)
                }
            }
        }
        handler.post(progressRunnable)
    }

    private fun stopProgressUpdate() {
        handler.removeCallbacks(progressRunnable)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Music Player", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(song: Song?): Notification {
        val currentSong = song ?: _currentSong.value
        val playPauseIcon = if (isPlaying.value == true) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentSong?.title ?: "No song playing")
            .setContentText(currentSong?.artist ?: "MP3 Music Player")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_skip_previous, "Previous", createActionIntent(ACTION_PREVIOUS))
            .addAction(playPauseIcon, "Play/Pause", createActionIntent(ACTION_PLAY_PAUSE))
            .addAction(R.drawable.ic_skip_next, "Next", createActionIntent(ACTION_NEXT))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying.value == true)
            .build()
    }

    // PERBAIKAN: Method baru untuk notifikasi saat pause
    private fun updateNotificationPausedState() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = createPausedNotification(_currentSong.value)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // PERBAIKAN: Notifikasi khusus untuk state pause (bisa ditutup)
    private fun createPausedNotification(song: Song?): Notification {
        val currentSong = song ?: _currentSong.value
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentSong?.title ?: "No song playing")
            .setContentText("Paused • ${currentSong?.artist ?: "MP3 Music Player"}")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_skip_previous, "Previous", createActionIntent(ACTION_PREVIOUS))
            .addAction(R.drawable.ic_play_arrow, "Play", createActionIntent(ACTION_PLAY_PAUSE))
            .addAction(R.drawable.ic_skip_next, "Next", createActionIntent(ACTION_NEXT))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(false) // PENTING: false agar bisa ditutup saat pause
            .setAutoCancel(true) // Bisa ditutup dengan swipe
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(_currentSong.value))
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).setAction(action)
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun <T> MutableList<T>.assign(newList: List<T>) {
        clear()
        addAll(newList)
    }
}