<div align="center">

# 🎧 Dengerin

**Dengerin musikmu, tanpa drama.**

Pemutar musik Android yang akrab dipakai, tapi bersih: gelap by default, mini player
terkunci di atas navigasi, dan nol sampah UI. Cari lagu, simpan playlist, baca lirik
langsung—tanpa perlu akun Spotify.

[📥 Unduh APK](https://github.com/mhafizhibrahim/dengerin/releases/latest) ·
[📦 Semua rilis](https://github.com/mhafizhibrahim/dengerin/releases) ·
[⭐ Star repo](https://github.com/mhafizhibrahim/dengerin)

[![Rilis terbaru](https://img.shields.io/github/v/release/mhafizhibrahim/dengerin?style=for-the-badge&color=1ED760)](https://github.com/mhafizhibrahim/dengerin/releases/latest)
[![Total unduhan](https://img.shields.io/github/downloads/mhafizhibrahim/dengerin/total?style=for-the-badge&color=1ED760)](https://github.com/mhafizhibrahim/dengerin/releases)
[![Bintang](https://img.shields.io/github/stars/mhafizhibrahim/dengerin/stargazers)](https://github.com/mhafizhibrahim/dengerin/stargazers)
![Android](https://img.shields.io/badge/Android-8.0%2B-1ED760?style=for-the-badge)
![Stack](https://img.shields.io/badge/Kotlin%20%2B%20Jetpack%20Compose-1ED760?style=for-the-badge)

</div>

---

## Daftar isi

- [Kenapa Dengerin?](#kenapa-dengerin)
- [✨ Fitur](#-fitur)
- [📥 Unduh & pasang](#-unduh--pasang)
- [🔄 Pembaruan otomatis](#-pembaruan-otomatis)
- [📝 Catatan rilis](#-catatan-rilis)
- [🛠 Build dari sumber](#-build-dari-sumber)
- [🧩 Arsitektur singkat](#-arsitektur-singkat)
- [❓ Troubleshooting](#-troubleshooting)
- [🔗 Tautan](#-tautan)

## Kenapa Dengerin?

Dengerin meminjam **struktur** yang sudah terasa akrab—header sapaan, pintasan, rak horizontal,
mini player—tapi dengan identitas sendiri:

- Gelap by default `#121212`, terang `#F4F6F8`; tanpa warna dinamis dari wallpaper.
- Kartu datar tanpa bingkai dan tanpa elevation; pemisah cukup `Color.White.copy(0.08f)`.
- Aksen hijau `#1ED760` (gelap) / `#1DB954` (terang), hanya untuk status aktif dan kontrol utama.
- Mini player terkunci di atas navigasi.
- **Nol sampah UI**: tanpa banner berlangganan, pop-up promosi, tab podcast/audiobook, atau iklan.

Ganti tema langsung berubah, tanpa animasi atau lapisan penutup layar, dan hanya tersedia di
**Pengaturan → Tampilan & Tema**.

## ✨ Fitur

| Fitur | Apa yang kamu dapat |
| --- | --- |
| 🔎 **Cari & jelajah** | Cari lagu, artis, dan playlist dengan filter; riwayat penarian tersimpan, saran langsung muncul, dan hasil sudah melewati pemeriksaan relevansi. |
| 🧑‍🎤 **Artis populer & profil artis** | Artis unggulan mudah ditemukan; halaman artis menampilkan lagu yang benar-benar dimiliki artis tersebut, bukan sekadar kemiripan nama. |
| ▶️ **Putar hemat kuota** | Streaming on-demand. Prefetch lagu berikutnya **mati secara default (0 MB saat install)** dan hanya aktif di Wi-Fi tanpa Data Saver bila diaktifkan eksplisit. |
| 🎧 **Kontrol penuh** | Play/pause, next/previous, shuffle, repeat, seek, serta kontrol dari notifikasi, lockscreen, dan tombol headset lewat Media3 `MediaSessionService`. |
| 🫧 **Mini player** | Terkunci di atas navigasi dan tetap terlihat saat menjelajah aplikasi. |
| 💿 **Sedang Diputar** | Sampul besar, progres, tombol suka, shuffle, repeat, antrean, dan info audio/cache dalam satu layar. |
| 📝 **Live Lyrics** | Lirik sinkron dari LRCLIB/YTM dengan penyesuaian timing `±0.5s`, tampilan karaoke layar penuh, dan animasi ringan 60 FPS (transisi 90 ms). |
| 💚 **Koleksi** | Lagu disukai, riwayat putar, playlist lokal, dan playlist baru dengan satu tombol. |
| 📀 **Detail playlist** | Gradien sampul, cari & sortir di playlist, total durasi, tombol putar bundar, dan indikator shuffle. |
| 🔗 **Impor playlist** | Tempel link playlist Spotify publik atau impor dari akun Spotify—tanpa izin khusus. Sinkronisasi Firestore berjalan saat aplikasi aktif dan dilepas saat background supaya hemat baterai. |
| 🎨 **Identitas baru** | Ikon vektor Dengerin, Splash Screen Android 12+, dan App Shortcuts (Cari Lagu, Lagu Disukai, Putar Acak). |
| 🌗 **Tema instan** | Pilih **Sistem / Gelap / Terang** dari **Pengaturan → Tampilan & Tema**; langsung berubah seketika. |
| 🔄 **GitHub Auto-Updater** | Cek rilis terbaru otomatis saat aplikasi dibuka, plus tombol manual di **Pengaturan → Pembaruan → Periksa Pembaruan**. |
| 👤 **Profil opsional** | Masuk dengan Google untuk level, XP, bingkai, dan title—Beranda & pemutaran tetap penuh tanpa akun. |

> Catatan: lirik berasal dari layanan publik (LRCLIB/YTM). Ketersediaan lirik tergantung lagu.

## 📥 Unduh & pasang

1. Buka halaman [GitHub Releases](https://github.com/mhafizhibrahim/dengerin/releases/latest).
2. Unduh **`Dengerin-v1.3.0.apk`** (atau versi terbaru yang tersedia).
3. Buka file APK di HP dan pilih **Instal**.
4. Minimal Android **8.0 (API 26)**.

### Soal Play Protect

Karena Dengerin didistribusikan langsung sebagai APK—bukan lewat Play Store—Google Play Protect
bisa menampilkan peringatan. Ini wajar. Pilih:

**Detail selengkapnya → Tetap instal → Instal anyway**

APK dirilis dengan kunci release sendiri dan dapat diverifikasi dengan `apksigner verify`.

## 🔄 Pembaruan otomatis

Dengerin mengecek rilis terbaru lewat **GitHub Releases API**, sekali saat aplikasi dibuka, di
background.

- Kalau **sudah versi terbaru**, **sedang offline**, atau GitHub bermasalah → **tidak ada dialog**.
- Kalau ada versi baru → muncul dialog berisi nomor versi, catatan rilis, progres unduh, tombol
  **Perbarui** dan **Nanti**.
- Versi dibandingkan per segmen angka, jadi `1.0.10` dianggap lebih baru dari `1.0.2`.
- Cek manual ada di **Pengaturan → Pembaruan → Periksa Pembaruan**, lengkap dengan versi yang
  sedang terpasang.

### Izin instal

Untuk pemasangan langsung, Dengerin mendaftarkan:

- `android.permission.REQUEST_INSTALL_PACKAGES`
- `FileProvider` dengan authority `com.dengerin.app.provider`, yang hanya membagikan folder
  `cacheDir/updates/`

Saat pembaruan pertama, Android akan meminta izin **Instal aplikasi yang tidak dikenal** untuk
Dengerin. Setelah diizinkan, APK hasil unduhan dibuka lewat installer sistem. **Kamu tetap harus
menekan konfirmasi pasang**—tidak ada instalasi diam-diam. Tidak perlu izin penyimpanan karena
APK hanya disimpan di cache aplikasi sendiri.

## 📝 Catatan rilis

### Checklist rilis

1. Naikkan `versionCode` dan `versionName` di `app/build.gradle.kts`.
2. Perbarui catatan versi di bawah ini dan kalimat "Versi terbaru saat README ini ditulis".
3. Jalankan `.\gradlew.bat testDebugUnitTest lintRelease assembleRelease --no-daemon`.
4. Salin APK menjadi `Dengerin-vX.Y.Z.apk`, lalu commit, tag, dan push ke `origin`.
5. Unggah APK + catatan rilis ke GitHub Release dan pastikan
   `gh api repos/mhafizhibrahim/dengerin/releases/latest --jq .tag_name` mengembalikan tag tersebut.

### v1.3.0

- **Rilis stabil**: `versionCode 5`, rantai migrasi Room `1→2→3→4→5` tanpa fallback destruktif, schema terekspor, dan 58 unit test hijau.
- **Hemat kuota**: prefetch lagu berikutnya mati secara default (0 MB), retry jaringan bertahap 250→500→1000→2000 ms, dan pemutaran on-demand.
- **Hemat baterai**: listener Firestore dilepas saat aplikasi ke background dan dipasang ulang saat foreground tanpa mengulang unggah.
- **Pencarian & artis**: filter hasil, artis populer, dan validasi kepemilikan lagu pada profil artis.
- **Kontrol pemutaran**: notifikasi, lockscreen, dan headset memakai Media3 `MediaSessionService`.
- **Identitas & lirik**: ikon vektor baru, Splash Screen, App Shortcuts, dan lirik sinkron halus.
- **UI lebih bersih**: kartu tanpa bingkai, tombol "Coba lagi" nyata pada error, token warna konsisten di dua tema, dan "Cari lagu" langsung membuka tab Pencarian.
- **Build hijau**: `lintRelease` 0 error dan `testDebugUnitTest` lulus 100%. Toolchain tidak diubah.

Untuk rilis sebelum `v1.3.0`, lihat [halaman Releases](https://github.com/mhafizhibrahim/dengerin/releases).

## 🛠 Build dari sumber

Butuh JDK 17, Android SDK 35, dan Gradle wrapper yang sudah disertakan.

```bash
# Windows
.\gradlew.bat testDebugUnitTest lintRelease assembleRelease --no-daemon

# macOS / Linux
./gradlew testDebugUnitTest lintRelease assembleRelease --no-daemon
```

Hasil: `app/build/outputs/apk/release/app-release.apk`.

Kredensial signing sengaja **tidak** disimpan di repo. Untuk build release dari clone baru:

1. Buat keystore:
   ```bash
   keytool -genkeypair -v -keystore app/dengerin-release.jks -alias dengerin \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Buat `keystore.properties` di root:
   ```properties
   storePassword=password_keystore_kamu
   keyPassword=password_key_kamu
   ```
3. Pastikan `keystore.properties`, `*.jks`, `*.keystore`, dan `local.properties` tidak pernah
   di-`commit` (sudah diabaikan oleh `.gitignore`).

Toolchain dikunci di Kotlin `2.4.0`, KSP `2.3.14`, dan AGP `8.13.2`. R8/minify sengaja
dinonaktifkan supaya `NewPipeExtractor`, `Media3`, `Room`, dan `OkHttp` tidak ter-strip saat
runtime. Ketiganya tidak diubah di `v1.3.0`.

## 🧩 Arsitektur singkat

- **UI**: Jetpack Compose + Material 3. Token warna tetap di `core/theme/Color.kt`, pembungkus di
  `core/theme/Theme.kt`, dibaca lewat `LocalDengerinColors.current`.
- **Navigasi**: `NavHost` dengan rute datar; empat tab (`Beranda`, `Cari`, `Koleksi`, `Buat`)
  memakai state index. Tab `Buat` membuka sheet, bukan halaman.
- **DI**: `AppContainer` manual—tanpa Hilt—supaya build tetap ringan.
- **Pemutar**: Media3 ExoPlayer + `DengerinPlaybackService` (`MediaSessionService` +
  `ForwardingPlayer`) untuk latar belakang, notifikasi, lockscreen, dan headset. `PlayerController`,
  `RetryBackoff`, `AudioCacheManager`, dan `PrefetchPolicy` mengatur antrean, retry, dan kuota.
- **Sumber lagu**: NewPipeExtractor dengan downloader OkHttp terpusat.
- **Data lokal**: Room versi 5 dengan rantai migrasi eksplisit dan schema terekspor; DataStore
  untuk tema, kualitas audio, kuota, dan preferensi sinkronisasi.
- **Sinkronisasi**: `LibraryCloudSync` memasang listener Firestore saat foreground dan melepaskannya
  saat background melalui `ProcessLifecycleOwner`.
- **Lirik**: LRCLIB/YTM dengan timeline berbasis snapshot agar posisi aktif berubah hanya saat
  baris berganti.
- **Updater**: `GitHubUpdateManager` (GitHub Releases API + unduhan APK) lewat facade
  `UpdateChecker` / `ApkDownloader`; nama aset rilis `Dengerin-v<versi>.apk`.

## ❓ Troubleshooting

| Masalah | Solusi |
| --- | --- |
| Play Protect bilang aplikasi berbahaya | Pilih **Detail selengkapnya → Tetap instal → Instal anyway**. Pakai hanya APK dari halaman rilis resmi ini. |
| Install update ditolak *update incompatible* | Ada Dengerin versi lain dengan tanda tangan berbeda. Uninstall yang lama lalu pasang ulang (data lama bisa hilang). |
| *Periksa Pembaruan* gagal | Cek koneksi internet. Cek otomatis sengaja diam bila GitHub tidak bisa dijangkau. |
| Tidak ada suara / playback berhenti | Izinkan notifikasi saat diminta agar layanan pemutaran latar belakang tetap jalan. |
| Lirik tidak muncul | Tidak semua lagu punya lirik di LRCLIB/YTM. Coba lagu lain atau sesuaikan timing di layar lirik penuh. |

## 🔗 Tautan

- 📦 [GitHub Releases — unduh APK](https://github.com/mhafizhibrahim/dengerin/releases/latest)
- 🐛 [Lapor masalah](https://github.com/mhafizhibrahim/dengerin/issues)
- 💻 [Source code](https://github.com/mhafizhibrahim/dengerin)

---

Dibuat dengan ☕ dan hijau `#1ED760`. Versi terbaru saat README ini ditulis: **v1.3.0**.
