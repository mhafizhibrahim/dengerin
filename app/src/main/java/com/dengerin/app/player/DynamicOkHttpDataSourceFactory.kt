package com.dengerin.app.player

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient

/** DataSource.Factory yang membaca UA/header terbaru dari [StreamHeaderState]. */
class DynamicOkHttpDataSourceFactory(
    private val callFactory: OkHttpClient,
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val current = StreamHeaderState.current()
        return OkHttpDataSource.Factory(callFactory)
            .setUserAgent(current.userAgent)
            .setDefaultRequestProperties(current.headers)
            .createDataSource()
    }
}
