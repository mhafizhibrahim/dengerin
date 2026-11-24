package com.dengerin.app

import android.app.Application
import com.dengerin.app.core.AppContainer
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class DengerinApplication : Application() {

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
    }
}
