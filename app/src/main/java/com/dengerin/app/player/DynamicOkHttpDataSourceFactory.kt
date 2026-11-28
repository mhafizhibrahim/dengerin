@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.dengerin.app.player

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient

/** DataSource.Factory yang membaca UA/header terbaru dari [StreamHeaderState]. */
class DynamicOkHttpDataSourceFactory(
    private val callFactory: OkHttpClient,
    private val transferListener: TransferListener? = null,
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val current = StreamHeaderState.current()
        val factory = OkHttpDataSource.Factory(callFactory)
            .setUserAgent(current.userAgent)
            .setDefaultRequestProperties(current.headers)
        if (transferListener != null) factory.setTransferListener(transferListener)
        return factory.createDataSource()
    }
}
