package com.dengerin.app

import com.dengerin.app.data.extractor.OkHttpDownloader

/** Akses singleton Downloader tanpa duplikasi instance. */
object OkHttpDownloaderAdapter {
    val downloader: OkHttpDownloader = OkHttpDownloader
}
