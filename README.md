# Dengerin

[![Versi rilis](https://img.shields.io/github/v/release/mhafizhibrahim/dengerin)](https://github.com/mhafizhibrahim/dengerin/releases/latest)

Pemutar musik Android dengan rasa yang akrab, tapi tanpa sampah UI: gelap by default,
mini player melayang, dan tanpa banner promosi. Lagu dicari dan diputar langsung dari
sumber publik—tanpa perlu akun Spotify.

## Fitur

- Putar lagu dari hasil pencarian, dengan kontrol *play/pause*, *next/previous*, *shuffle*,
  *repeat*, dan slider *seek*.
- Mini player melayang di atas navigasi; layar **Sedang Diputar** dengan sampul, progres,
  suka, dan mode putar.
- **Live Lyrics**: kartu lirik sinkron di layar putar, bisa diperbesar ke layar penuh,
  lengkap dengan penyesuaian timing `±0.5s`.
- **Koleksi**: lagu disukai, riwayat putar, playlist lokal, dan impor playlist Spotify publik
  lewat link—tanpa login.
- Tampilan instan: pilih **Sistem / Gelap / Terang** dari tab Koleksi.
- Pengecek pembaruan otomatis dari GitHub Releases, dengan unduh APK langsung dan
  progres di dialog. Versi terpasang dan tombol **Cek Pembaruan** ada di tab **Koleksi**.

## Unduh

Ambil APK terbaru dari halaman [GitHub Releases](https://github.com/mhafizhibrahim/dengerin/releases/latest).
Minimal Android 8.0 (API 26).

Saat membuka APK pertama kali, Android atau Google Play Protect bisa menampilkan peringatan.
Pilih **Detail selengkapnya** → **Tetap instal** untuk melanjutkan. Ini wajar karena aplikasi
didistribusikan langsung sebagai APK, bukan lewat Play Store.

Untuk pembaruan dalam aplikasi, Dengerin meminta izin **Instal aplikasi yang tidak dikenal**
sekali pada Android 8+. Setelah diizinkan, APK unduhan dari GitHub akan dibuka lewat installer
sistem; konfirmasi pasang tetap dilakukan oleh kamu.

## Soal pembaruan

Aplikasi mengecek rilis terbaru sekali saat dibuka, di background. Kalau sedang offline, sudah
versi terbaru, atau GitHub sedang bermasalah, tidak ada dialog apa pun. Versi dibandingkan per
segmen angka, jadi `1.0.10` dianggap lebih baru dari `1.0.2`.

Cek manual bisa lewat **Koleksi → Cek Pembaruan**. Dialog menampilkan nomor versi, catatan
rilis, progres unduh, lalu tombol **Perbarui** dan **Nanti**.

## Build dari sumber

```bash
./gradlew assembleRelease
```

Kredensial signing dibaca dari `keystore.properties` di root (tidak ikut di-commit).
Konfigurasi r8/minify mengikuti pola proyek Sisihin: dimatikan supaya NewPipeExtractor,
Media3, Room, dan OkHttp tidak ter-strip saat runtime.
