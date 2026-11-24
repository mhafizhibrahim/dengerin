package com.dengerin.app.core

/** Header umum yang dipakai baik oleh extractor maupun oleh pemutar. */
object StreamHeaders {

    /** UA standar NewPipeExtractor (Firefox) untuk request halaman/player. */
    const val NEW_PIPE_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

    /** UA klien InnerTube ANDROID_VR yang dipakai fallback ekstraksi audio. */
    const val ANDROID_VR_USER_AGENT =
        "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
            "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

    /** UA klien InnerTube IOS sebagai fallback kedua. */
    const val IOS_USER_AGENT =
        "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3 like Mac OS X;)"

    val YOUTUBE_HEADERS = mapOf(
        "Referer" to "https://www.youtube.com/",
        "Origin" to "https://www.youtube.com",
    )
}
