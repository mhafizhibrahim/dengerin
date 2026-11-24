package com.dengerin.app.data.extractor

import com.dengerin.app.core.StreamHeaders
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Downloader resmi NewPipeExtractor berbasis OkHttp. */
object OkHttpDownloader : Downloader() {

    private const val DEFAULT_CONTENT_TYPE = "application/octet-stream"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: ExtractorRequest): ExtractorResponse {
        val headersBuilder = Headers.Builder()

        // Teruskan SEMUA header, termasuk nilai ganda (Content-Type,
        // X-YouTube-Client-Name, X-YouTube-Client-Version, Origin, dll).
        request.headers().forEach { (name, values) ->
            values.forEach { value -> headersBuilder.add(name, value) }
        }

        if (headersBuilder["User-Agent"].isNullOrEmpty()) {
            headersBuilder["User-Agent"] = StreamHeaders.NEW_PIPE_USER_AGENT
        }
        applyConsentCookie(headersBuilder)

        val contentType = headersBuilder["Content-Type"]
            ?.takeIf { it.isNotBlank() }
            ?.toMediaTypeOrNull()
            ?: DEFAULT_CONTENT_TYPE.toMediaTypeOrNull()!!

        val body = request.dataToSend()?.toRequestBody(contentType)
        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .headers(headersBuilder.build())

        when (request.httpMethod().uppercase(Locale.ROOT)) {
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody(contentType))
            "PUT" -> builder.put(body ?: ByteArray(0).toRequestBody(contentType))
            "PATCH" -> builder.patch(body ?: ByteArray(0).toRequestBody(contentType))
            "DELETE" -> if (body != null) builder.delete(body) else builder.delete()
            "HEAD" -> builder.head()
            else -> builder.get()
        }

        client.newCall(builder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (response.code == 429 || isBotCheck(response.code, responseBody)) {
                throw ReCaptchaException(
                    "YouTube meminta verifikasi (HTTP ${response.code}).",
                    request.url(),
                )
            }
            return ExtractorResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                responseBody,
                response.request.url.toString(),
            )
        }
    }

    private fun applyConsentCookie(headers: Headers.Builder) {
        val current = headers["Cookie"]
        val updated = when {
            current.isNullOrEmpty() -> "SOCS=CAI"
            current.contains("SOCS=", ignoreCase = true) -> current
            else -> "$current; SOCS=CAI"
        }
        headers["Cookie"] = updated
    }

    private fun isBotCheck(code: Int, body: String): Boolean =
        code == 403 && (
            body.contains("Sign in to confirm", ignoreCase = true) ||
                body.contains("reCAPTCHA", ignoreCase = true) ||
                body.contains("not a bot", ignoreCase = true)
            )
}
