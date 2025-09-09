package com.mp3.musicplayer

import android.app.Activity
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mp3.musicplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val musicViewModel: MusicViewModel by viewModels()

    // PERBAIKAN: Tambahkan variabel untuk service binding
    private var musicService: MusicService? = null
    private var isBound = false

    // Service connection untuk bind dengan MusicService
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    // Launcher untuk meminta izin
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // PERBAIKAN: Perintahkan ViewModel untuk memuat lagu SETELAH izin diberikan
                musicViewModel.loadSongs()
            } else {
                showPermissionExplanationDialog()
            }
        }

    // Launcher untuk dialog hapus file
    private val deleteRequestLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                musicViewModel.handleSuccessfulDeletion()
                Toast.makeText(this, getString(R.string.toast_delete_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.toast_delete_failed), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        checkPermissions()
        observeViewModel()
        bindMusicService()
        setupBackPressHandler()
    }

    override fun onDestroy() {
        super.onDestroy()

        // PERBAIKAN: Beritahu service bahwa aplikasi ditutup
        musicService?.onAppClosed()

        // Unbind service
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    // PERBAIKAN: Handle back press dengan cara yang tidak deprecated
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Jika tidak sedang playing, tutup service juga
                if (musicService?.isPlaying?.value != true) {
                    musicService?.onAppClosed()
                }
                finish()
            }
        })
    }

    // PERBAIKAN: Method untuk bind dengan MusicService
    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavView.setupWithNavController(navController)
    }

    private fun checkPermissions() {
        // Hanya cek izin jika playlist di ViewModel kosong, untuk menghindari pengecekan berulang
        if (musicViewModel.playlist.value.isNullOrEmpty()) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(permission)
            }
        }
    }

    private fun observeViewModel() {
        musicViewModel.deleteRequest.observe(this) { event ->
            event.getContentIfNotHandled()?.let { intentSender ->
                deleteRequestLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
        }
    }

    private fun showPermissionExplanationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.permission_dialog_title))
            .setMessage(getString(R.string.permission_dialog_message))
            .setPositiveButton(getString(R.string.permission_dialog_grant)) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                // PERBAIKAN: Variabel 'uri' yang tidak digunakan telah dihapus
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.dialog_button_cancel)) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .show()
    }
}