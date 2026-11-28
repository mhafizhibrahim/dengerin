# Dengerin v1.3.0

Rilis stabil dengan fokus pada kuota, kestabilan pemutaran, dan kerapian tampilan.
Toolchain Kotlin/KSP/AGP, R8/minify, dan konfigurasi signing tidak diubah.

## Yang Baru di v1.3.0

- **Kontrol Notifikasi, Lockscreen & Earphone MediaSession** — pemutaran latar belakang memakai
  Media3 `MediaSessionService` + `ForwardingPlayer`, sehingga tombol notifikasi, lockscreen, dan
  headset tetap aktif.
- **Perombakan Pencarian & Validasi Profil Artis ala Spotify** — filter hasil, artis populer, dan
  pemeriksaan kepemilikan lagu supaya profil artis hanya menampilkan trek yang benar-benar miliknya.
- **Ikon Vektor Baru + Splash Screen + App Shortcuts** — identitas Dengerin memakai mark vektor,
  Splash Screen Android 12+, dan pintasan Cari Lagu, Lagu Disukai, serta Putar Acak.
- **Lirik Sinkron & Optimasi Performa** — lirik LRCLIB/YTM dengan timeline berbasis snapshot,
  animasi ringan 60 FPS dengan transisi 90 ms, dan tampilan karaoke layar penuh.

## Perbaikan & Peningkatan

- **Prefetch default 0 MB** — lagu berikutnya tidak diunduh otomatis saat install; aktif hanya di
  Wi-Fi tanpa Data Saver, dengan ruang cache cukup, dan bila diaktifkan eksplisit.
- **Firestore hemat baterai** — listener dilepas saat aplikasi ke background dan dipasang ulang saat
  foreground tanpa mengulang unggah atau drain.
- **Retry otomatis** — kegagalan jaringan/timeout memakai backoff bertahap 250→500→1000→2000 ms.
- **Migrasi database aman** — rantai Room `1→2→3→4→5` tanpa fallback destruktif, schema terekspor;
  upgrade dari `v1.2.0` menjaga data lokal.
- **Tombol & keselarasan UI** — tombol "Coba lagi" nyata pada error katalog/artis/playlist,
  kartu tanpa bingkai, token warna konsisten di tema gelap dan terang, serta "Cari lagu" langsung
  membuka tab Pencarian.

## Informasi Berkas & Verifikasi

| Item | Nilai |
| --- | --- |
| Berkas | `Dengerin-v1.3.0.apk` |
| Ukuran | 24.582.823 byte |
| `minSdk` | 26 (Android 8.0) |
| `versionCode` | 5 |
| `versionName` | 1.3.0 |
| SHA-256 | `E4C919F8798E61E31B1F5BB3913B1D51948E0830ECCB14C803B309963FE9763B` |
| Sertifikat penandatangan (SHA-256) | `f59a98b4ccf6e32f10e888e07e8974a0b20e97798fef115ab4bce5b55919ce28` |
| `apksigner verify --print-certs` | Lolos, sama dengan keystore rilis `app/dengerin-release.jks` alias `dengerin` |
| `testDebugUnitTest` | 58 test, 0 failure, 0 error |
| `lintRelease` | 0 error (80 warning bawaan) |
| `assembleRelease` | Berhasil |

## Pemasangan

- Pasang di atas `v1.2.0` tanpa uninstall agar data lokal tetap ada.
- Tanda tangan identik dengan rilis sebelumnya, jadi pembaruan tidak ditolak *update incompatible*.
- Play Protect mungkin menampilkan peringatan karena APK didistribusikan langsung: pilih
  **Detail selengkapnya → Tetap instal → Instal anyway**.
