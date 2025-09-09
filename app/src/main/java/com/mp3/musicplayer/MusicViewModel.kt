package com.mp3.musicplayer

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    // LiveData untuk UI
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

    private val _playlist = MutableLiveData<List<Song>>()
    val playlist: LiveData<List<Song>> = _playlist

    // LiveData untuk event sekali jalan
    val deleteRequest = MutableLiveData<Event<IntentSender>>()

    // Koneksi ke Service
    private var musicService: MusicService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            setupObserversFromService()

            // Jika playlist sudah dimuat, kirim ke service
            _playlist.value?.let {
                musicService?.setPlaylist(it)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            musicService = null
        }
    }

    init {
        // Ikat service saat ViewModel dibuat
        Intent(application, MusicService::class.java).also { intent ->
            application.startService(intent)
            application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        loadSongs()
    }

    // Pindahkan observer ke ViewModel
    private fun setupObserversFromService() {
        musicService?.currentSong?.observeForever { song -> _currentSong.postValue(song) }
        musicService?.isPlaying?.observeForever { playing -> _isPlaying.postValue(playing) }
        musicService?.currentPosition?.observeForever { pos -> _currentPosition.postValue(pos) }
        musicService?.shuffleEnabled?.observeForever { enabled -> _shuffleEnabled.postValue(enabled) }
        musicService?.repeatEnabled?.observeForever { enabled -> _repeatEnabled.postValue(enabled) }
        musicService?.playlistChanged?.observeForever {
            // Ambil playlist terbaru dari service
            _playlist.postValue(musicService?.getPlaylist())
        }
    }

    // Aksi Kontrol yang akan dipanggil oleh UI
    fun playSongAtIndex(index: Int) {
        // Kita tidak perlu memeriksa `isBound` lagi. Cukup kirim perintah.
        val intent = Intent(getApplication(), MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY_SONG_AT_INDEX
            putExtra(MusicService.EXTRA_SONG_INDEX, index)
        }
        getApplication<Application>().startService(intent)
    }

    fun playPause() {
        musicService?.playPause()
    }

    fun playNextSong() {
        musicService?.playNextSong()
    }

    fun playPreviousSong() {
        musicService?.playPreviousSong()
    }

    fun seekTo(position: Int) {
        musicService?.seekTo(position)
    }

    fun toggleShuffle() {
        musicService?.toggleShuffle()
    }

    fun toggleRepeat() {
        musicService?.toggleRepeat()
    }

    fun deleteCurrentSong() {
        val intentSender = musicService?.deleteCurrentSong()
        if (intentSender != null) {
            deleteRequest.postValue(Event(intentSender))
        }
    }

    fun handleSuccessfulDeletion() {
        musicService?.handleSuccessfulDeletion()
    }

    // Logika pemindaian lagu dipindahkan ke sini
    fun loadSongs() {
        viewModelScope.launch {
            val songs = withContext(Dispatchers.IO) { scanMusicFiles() }
            _playlist.postValue(songs)
            if (isBound) {
                musicService?.setPlaylist(songs)
            }
        }
    }

    private fun scanMusicFiles(): List<Song> {
        val songList = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        getApplication<Application>().contentResolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC")
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn))
                    val albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), cursor.getLong(albumIdColumn))
                    songList.add(Song(
                        id = cursor.getLong(idColumn),
                        title = cursor.getString(titleColumn),
                        artist = cursor.getString(artistColumn) ?: "Unknown",
                        album = cursor.getString(albumColumn) ?: "Unknown",
                        duration = cursor.getLong(durationColumn),
                        path = cursor.getString(pathColumn),
                        uri = uri,
                        albumArtUri = albumArtUri
                    ))
                }
            }
        return songList
    }

    override fun onCleared() {
        super.onCleared()
        // Lepaskan ikatan service saat ViewModel dihancurkan
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
    }
}