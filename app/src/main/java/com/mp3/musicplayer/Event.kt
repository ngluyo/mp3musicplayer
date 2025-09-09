package com.mp3.musicplayer

// Kelas ini membungkus sebuah konten dan mencegahnya digunakan lebih dari sekali.
// Berguna untuk event seperti menampilkan Toast atau Dialog dari ViewModel.
open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set // Hanya bisa diubah dari dalam kelas ini

    // Mengembalikan konten dan menandainya sebagai sudah ditangani
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    // Mengintip konten tanpa menandainya
    fun peekContent(): T = content
}