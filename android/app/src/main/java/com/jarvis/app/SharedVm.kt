package com.jarvis.app

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Process-wide VM owner: MainActivity and WakeHudActivity share one JarvisViewModel. */
object SharedVmOwner : ViewModelStoreOwner {
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore = store
}

/** The single shared JarvisViewModel for the whole process. */
fun sharedJarvisVm(app: Application): JarvisViewModel =
    ViewModelProvider(SharedVmOwner, JarvisVmFactory(app))[JarvisViewModel::class.java]
