package com.dengerin.app.data.quality

import com.dengerin.app.core.network.NetworkMonitor
import com.dengerin.app.core.network.NetworkState
import com.dengerin.app.data.preferences.AudioQuality
import com.dengerin.app.data.preferences.PlaybackPreferences
import com.dengerin.app.data.preferences.isDataSaverActive
import com.dengerin.app.domain.model.AudioStreamInfo

enum class QualityProfile {
    LOW,
    NORMAL,
    HIGH,
    VERY_HIGH;

    /** Satu tingkat lebih rendah untuk auto-adjust saat bandwidth jelek. */
    fun stepDown(): QualityProfile = when (this) {
        VERY_HIGH -> HIGH
        HIGH -> NORMAL
        NORMAL -> LOW
        LOW -> LOW
    }
}

data class QualityDecision(
    val profile: QualityProfile,
    val adjustedDown: Boolean,
)

/**
 * Menentukan profil bitrate dari preferensi + jaringan, lalu memilih stream audio
 * dari kandidat yang sudah filtered. Tidak pernah menyentuh video stream.
 */
class AudioQualityEngine(
    private val preferences: PlaybackPreferences,
    private val networkMonitor: NetworkMonitor,
) {

    fun decide(): QualityDecision {
        val settings = preferences.settings.value
        val network = networkMonitor.current()

        val dataSaver = settings.isDataSaverActive(network)
        val requested = when {
            dataSaver && network.isCellular -> AudioQuality.LOW
            network.isCellular -> settings.cellularQuality
            network.isWifiLike -> settings.wifiQuality
            else -> settings.cellularQuality
        }

        val base = when (requested) {
            AudioQuality.AUTO -> if (network.isCellular) QualityProfile.NORMAL else QualityProfile.HIGH
            AudioQuality.LOW -> QualityProfile.LOW
            AudioQuality.NORMAL -> QualityProfile.NORMAL
            AudioQuality.HIGH -> QualityProfile.HIGH
            AudioQuality.VERY_HIGH -> QualityProfile.VERY_HIGH
        }

        val adjusted = settings.autoAdjustQuality &&
            network.isLowBandwidth &&
            base != QualityProfile.LOW

        return QualityDecision(
            profile = if (adjusted) base.stepDown() else base,
            adjustedDown = adjusted,
        )
    }

    fun select(candidates: List<AudioStreamInfo>, decision: QualityDecision): AudioStreamInfo {
        require(candidates.isNotEmpty()) { "Tidak ada kandidat stream audio." }

        // Opus selalu didahulukan. M4A/AAC hanya dipakai bila Opus tidak tersedia
        // atau tidak memenuhi batas bitrate profil yang diminta.
        val opus = candidates.filter(::isOpus)
        val m4a = candidates.filter(::isM4A)
        val others = candidates.filterNot { isOpus(it) || isM4A(it) }

        val chosen = when (decision.profile) {
            QualityProfile.LOW -> firstNotNull(
                { opus.filter { it.inBitrate(35_000, 50_000) }.minByOrNull { it.bitrateBps } },
                { opus.minByOrNull { it.bitrateBps } },
                { m4a.filter { it.bitrateBps <= 50_000 }.minByOrNull { it.bitrateBps } },
                { others.minByOrNull { it.bitrateBps } },
                { candidates.minByOrNull { it.bitrateBps } },
            )

            QualityProfile.NORMAL -> firstNotNull(
                { opus.filter { it.inBitrate(64_000, 96_000) }.maxByOrNull { it.bitrateBps } },
                { opus.filter { it.inBitrate(64_000, 110_000) }.maxByOrNull { it.bitrateBps } },
                { opus.filter { it.bitrateBps <= 110_000 }.maxByOrNull { it.bitrateBps } },
                { m4a.filter { it.bitrateBps <= 110_000 }.maxByOrNull { it.bitrateBps } },
                { candidates.filter { it.bitrateBps <= 110_000 }.maxByOrNull { it.bitrateBps } },
                { opus.minByOrNull { it.bitrateBps } },
                { m4a.minByOrNull { it.bitrateBps } },
                { candidates.minByOrNull { it.bitrateBps } },
            )

            QualityProfile.HIGH -> firstNotNull(
                { opus.filter { it.inBitrate(128_000, 160_000) }.maxByOrNull { it.bitrateBps } },
                { opus.minByOrNull { kotlin.math.abs(it.bitrateBps - 160_000) } },
                { m4a.filter { it.inBitrate(128_000, 160_000) }.maxByOrNull { it.bitrateBps } },
                { candidates.maxByOrNull { it.bitrateBps } },
            )

            QualityProfile.VERY_HIGH -> firstNotNull(
                { opus.maxByOrNull { it.bitrateBps } },
                { m4a.maxByOrNull { it.bitrateBps } },
                { others.maxByOrNull { it.bitrateBps } },
            )
        }

        return requireNotNull(chosen) { "Tidak ada stream audio yang bisa diputar." }
    }

    private fun firstNotNull(vararg attempts: () -> AudioStreamInfo?): AudioStreamInfo? {
        for (attempt in attempts) {
            val result = attempt()
            if (result != null) return result
        }
        return null
    }

    private fun AudioStreamInfo.inBitrate(min: Int, max: Int): Boolean =
        bitrateBps in min..max

    private fun isOpus(stream: AudioStreamInfo): Boolean =
        stream.formatName.contains("opus", ignoreCase = true) ||
            stream.mimeType.startsWith("audio/webm", ignoreCase = true)

    private fun isM4A(stream: AudioStreamInfo): Boolean =
        stream.mimeType.startsWith("audio/mp4", ignoreCase = true) ||
            stream.formatName.contains("m4a", ignoreCase = true) ||
            stream.formatName.contains("aac", ignoreCase = true)

    /** Dipakai UI untuk memberi tahu apakah profil otomatis sedang diturunkan. */
    fun describe(network: NetworkState = networkMonitor.current()): String = when {
        network.isCellular && preferences.settings.value.isDataSaverActive(network) -> "Hemat · Data Seluler"
        network.isCellular -> "Seluler"
        network.isWifiLike -> "Wi-Fi"
        else -> "Jaringan terbatas"
    }
}
