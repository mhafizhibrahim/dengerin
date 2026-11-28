package com.dengerin.app

import android.app.Application
import android.os.StrictMode
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.dengerin.app.core.AppContainer
import com.dengerin.app.data.update.GitHubUpdateManager
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class DengerinApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Inisialisasi NewPipeExtractor tepat sekali, sebelum repository dipakai.
        // Lokalisasi dan negara konten dipakai bersama supaya request konsisten.
        NewPipe.init(
            OkHttpDownloaderAdapter.downloader,
            Localization("id", "ID"),
            ContentCountry("ID"),
        )
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.libraryCloudSync.resumeListeners()
            }

            override fun onStop(owner: LifecycleOwner) {
                container.libraryCloudSync.pauseListeners()
            }
        })
        GitHubUpdateManager.initialize(this)
        // Pasang AFTER startup init yang memang perlu disk sekali (Room/DataStore/Coil).
        // Sejak titik ini, setiap disk/network di main thread langsung muncul di Logcat.
        if (BuildConfig.DEBUG) enableStrictMode()
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }

    /** Coil memakai satu instance ini untuk semua AsyncImage & ImageLoader.execute. */
    override fun newImageLoader(): ImageLoader = container.imageLoader
}
