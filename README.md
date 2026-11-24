<div align="center">

# 🎧 Dengerin

**Dengerin musikmu, tanpa drama.**

Pemutar musik Android yang akrab dipakai, tapi bersih: gelap by default, mini player
melayang, dan nol sampah UI. Cari lagu, simpan playlist, baca lirik langsung—tanpa perlu
akun Spotify.

[📥 Unduh APK](https://github.com/mhafizhibrahim/dengerin/releases/latest) ·
[📦 Semua rilis](https://github.com/mhafizhibrahim/dengerin/releases) ·
[⭐ Star repo](https://github.com/mhafizhibrahim/dengerin)

[![Rilis terbaru](https://img.shields.io/github/v/release/mhafizhibrahim/dengerin?style=for-the-badge&color=1ED760)](https://github.com/mhafizhibrahim/dengerin/releases/latest)
[![Total unduhan](https://img.shields.io/github/downloads/mhafizhibrahim/dengerin/total?style=for-the-badge&color=1ED760)](https://github.com/mhafizhibrahim/dengerin/releases)
[![Bintang](https://img.shields.io/github/stars/mhafizhibrahim/dengerin?style=for-the-badge&color=1ED760)](https://github.com/mhafizhibrahim/dengerin/stargazers)
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

- **Instagram Dark Slate** `#0C1014`, bukan hitam pekat atau abu kusam.
- Kartu datar dengan border halus `1dp #27313D`, tanpa elevation/glow berlebihan.
- Aksen hijau segar `#1ED760`, dipakai untuk status aktif dan tombol utama.
- Mini player berupa *floating pill* dengan jarak `8dp` dari tepi.
- **Nol sampah UI**: tanpa banner Premium, pop-up promosi, tab podcast/audiobook, atau iklan.

Ganti tema langsung berubah, tanpa animasi atau layer penutup layar.

## ✨ Fitur

| Fitur | Apa yang kamu dapat |
| --- | --- |
| 🔎 **Cari & jelajah** | Cari lagu/artis, riwayat 5 kata kunci, 8 kategori musik, dan rak hashtag—semuanya langsung mencari lagu nyata. |
| ➕ **Tambah ke playlist** | Dari hasil cari, detail playlist, Now Playing, atau mini player: buat playlist baru, simpan ke *Lagu Disukai*, atau kelola playlist lokal tanpa duplikat. |
| 🎲 **True shuffle** | Acak seluruh antrean tanpa memotong lagu yang sedang berjalan; next/previous dan auto-advance mengikuti urutan acak. |
| 🧑‍🎤 **Artis populer** | Rak avatar bulat di Beranda dari riwayat & playlist kamu, dilengkapi artis favorit; ketuk untuk langsung cari lagunya. |
| ▶️ **Kontrol penuh** | Play/pause, next/previous, shuffle, repeat, dan slider *seek* yang bisa digeser. |
| 🫧 **Mini player** | Melayang di atas navigasi, tetap terlihat saat kamu jelajah aplikasi. |
| 💿 **Sedang Diputar** | Sampul besar, progres, tombol suka, shuffle, dan repeat dalam satu layar. |
| 📝 **Live Lyrics** | Kartu lirik sinkron di bawah kontrol, bisa diperbesar ke layar penuh, lengkap dengan penyesuaian timing `±0.5s`. |
| 💚 **Koleksi** | Lagu disukai, riwayat putar, playlist lokal, dan playlist baru dengan satu tombol. |
| 📀 **Detail playlist** | Gradien sampul, cari & sortir di playlist, total durasi, tombol putar bundar, dan indikator shuffle. |
| 🔗 **Impor Spotify** | Tempel link playlist publik—tanpa login, tanpa izin khusus. |
| 🌗 **Tema instan** | Pilih **Sistem / Gelap / Terang** dari tab Koleksi; langsung berubah seketika. |
| 🔄 **Auto-update** | Cek rilis baru dari GitHub saat aplikasi dibuka, plus tombol **Cek Pembaruan** manual. |

> Catatan: lirik diambil dari layanan lirik publik (Lrclib). Ketersediaan lirik tergantung lagu.

## 📥 Unduh & pasang

1. Buka halaman [GitHub Releases](https://github.com/mhafizhibrahim/dengerin/releases/latest).
2. Unduh **`Dengerin-v1.1.1.apk`** (atau versi terbaru yang tersedia).
3. Buka file APK di HP dan pilih **Instal**.
4. Minimal Android **8.0 (API 26)**.

### Soal Play Protect

Karena Dengerin didistribusikan langsung sebagai APK—bukan lewat Play Store—Google Play Protect
bisa menampilkan peringatan. Ini wajar. Pilih:

**Detail selengkapnya → Tetap instal → Instal anyway**

APK yang dirilis sudah ditandatangani dengan kunci release sendiri dan bisa diverifikasi dengan
`apksigner verify`.

## 🔄 Pembaruan otomatis

Dengerin mengecek rilis terbaru lewat **GitHub Releases API**, sekali saat aplikasi dibuka, di
background.

- Kalau **sudah versi terbaru**, **sedang offline**, atau GitHub bermasalah → **tidak ada dialog**.
- Kalau ada versi baru → muncul dialog berisi nomor versi, catatan rilis, progres unduh, tombol
  **Perbarui** dan **Nanti**.
- Versi dibandingkan per segmen angka, jadi `1.0.10` dianggap lebih baru dari `1.0.2`.
- Cek manual ada di **Koleksi → Cek Pembaruan**, lengkap dengan versi yang sedang terpasang.

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

Setiap rilis baru dilakukan berurutan supaya updater dan dokumen tetap sinkron:

1. Naikkan `versionCode` dan `versionName` di `app/build.gradle.kts`.
2. Tambahkan catatan versi di bawah ini (versi terbaru paling atas) dan perbarui kalimat
   "Versi terbaru saat README ini ditulis" di bagian paling bawah.
3. `commit` + `push` ke `main`.
4. Build `assembleRelease`, lalu unggah APK dengan nama `Dengerin-vX.Y.Z.apk` ke GitHub Release.
5. Pastikan `gh api repos/mhafizhibrahim/dengerin/releases/latest --jq .tag_name`
   mengembalikan tag rilis tersebut.

### v1.1.1

- **Fix bugs**: scroll cepat di playlist 100 lagu tidak lagi *force close*. Key `LazyColumn` sekarang unik per indeks dan thumbnail didekode maksimal 144 px tanpa crossfade.
- **Fix bugs**: auto-next saat Shuffle aktif kini benar-benar berputar. Antrean (`originalQueue` / `activeQueue` / `currentIndex`) dikelola penuh di `PlayerController`, termasuk `REPEAT_ONE`, wrap `REPEAT_ALL`, dan skip 3 kali bila ekstraksi gagal.
- **Fix bugs**: thumbnail YouTube HD langsung tampil sejak impor. 10 lagu pertama disiapkan lebih dulu dengan progres di dialog, sisanya menyusul di worker latar belakang; cover playlist tidak lagi dipinjam sebagai sampul trek.

### v1.1.0

- Layar **Cari** tidak lagi bertabrakan dengan status bar; search bar 52dp dengan border hijau saat fokus.
- Mode jelajah: riwayat penarian, 8 kategori musik, dan rak hashtag yang langsung mencari lagu.
- Bottom sheet **Tambah ke playlist** tersedia di hasil cari, detail playlist, Now Playing, dan mini player.
- Detail playlist bergaya Spotify: gradien sampul, cari & sortir, total durasi, play bundar, dan shuffle dengan indikator.
- True shuffle: unduhan acak Fisher-Yates langsung diputar, sisa antrean diacak tanpa memotong lagu aktif.
- Rak **Artis Untukmu** dengan avatar bulat; ketuk untuk membuka hasil pencarian artis tersebut.

### v1.0.0

- Rilis awal: pemutar latar belakang, lirik sinkron, koleksi lokal, impor playlist Spotify, dan auto-updater GitHub Releases.

## 🛠 Build dari sumber

Butuh JDK 17, Android SDK 35, dan Gradle wrapper yang sudah disertakan.

```bash
# Windows
.\gradlew.bat assembleRelease

# macOS / Linux
./gradlew assembleRelease
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

R8/minify sengaja dimatikan, mengikuti konfigurasi proyek Sisihin yang sudah stabil, supaya
`NewPipeExtractor`, `Media3`, `Room`, dan `OkHttp` tidak ter-strip saat runtime.

## 🧩 Arsitektur singkat

- **UI**: Jetpack Compose + Material 3, satu `DengerinTheme` dengan token warna tetap di
  `core/theme/Color.kt`.
- **Navigasi**: `NavHost` dengan rute datar; tiga tab utama (`Beranda`, `Cari`, `Koleksi`)
  memakai state index, bukan rute terpisah.
- **DI**: `AppContainer` manual—tanpa Hilt—supaya build tetap ringan.
- **Pemutar**: Media3 ExoPlayer + `MusicPlaybackService` untuk pemutaran latar belakang.
- **Sumber lagu**: NewPipeExtractor dengan downloader OkHttp khusus.
- **Data lokal**: Room untuk lagu disukai, riwayat, dan playlist; DataStore untuk preferensi tema.
- **Updater**: `UpdateChecker` + `ApkDownloader`, keduanya memakai API bawaan Android sehingga
  tidak menambah dependency baru.

## ❓ Troubleshooting

| Masalah | Solusi |
| --- | --- |
| Play Protect bilang aplikasi berbahaya | Pilih **Detail selengkapnya → Tetap instal**. Pakai hanya APK dari halaman rilis resmi ini. |
| Install update ditolak *update incompatible* | Ada Dengerin versi lain dengan tanda tangan berbeda. Uninstall yang lama lalu pasang ulang (data lama bisa hilang). |
| *Cek Pembaruan* gagal | Cek koneksi internet. Cek otomatis sengaja diam bila GitHub tidak bisa dijangkau. |
| Tidak ada suara / playback berhenti | Izinkan notifikasi saat diminta agar layanan pemutaran latar belakang tetap jalan. |
| Lirik tidak muncul | Tidak semua lagu punya lirik di Lrclib. Coba lagu lain atau sesuaikan timing di layar lirik penuh. |

## 🔗 Tautan

- 📦 [GitHub Releases — unduh APK](https://github.com/mhafizhibrahim/dengerin/releases/latest)
- 🐛 [Lapor masalah](https://github.com/mhafizhibrahim/dengerin/issues)
- 💻 [Source code](https://github.com/mhafizhibrahim/dengerin)

---

Dibuat dengan ☕ dan hijau `#1ED760`. Versi terbaru saat README ini ditulis: **v1.1.1**.
