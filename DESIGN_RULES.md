# DESIGN_RULES — Dengerin

Kontrak desain Dengerin Fase 1 + Fase 2. Anggap sebagai token tetap: jangan menambah warna
atau efek baru tanpa mengubah kontrak ini lebih dulu. Bahasa UI: Indonesia santai & jelas.

## Identitas: "Mirip Spotify, Tapi Beda Gaya"

Kita meminjam **struktur dan rasa** Spotify supaya pengguna langsung paham, bukan meniru
visualnya mentah-mentah:

- Header sapaan dinamis, foto profil bulat, dan chip filter cepat.
- Grid pintasan 2 kolom di puncak Beranda.
- Rak horizontal (*carousel*) yang membuat Beranda penuh judul sebelum login.
- Detail playlist dengan sampul besar, `Putar Semua`, `Acak`, lalu daftar lagu.

Ciri khas Dengerin yang **tidak boleh hilang**:

- Instagram Dark Slate `#0C1014`, bukan hitam pekat atau abu kusam.
- Setiap kartu punya border halus `1dp` `#27313D`.
- Mini player adalah *floating pill* dengan jarak `8dp` dari tepi, bukan bilah menempel.
- Nol sampah UI: tanpa banner Premium, tanpa pop-up promosi, tanpa tab podcast/audiobook,
  tanpa glow berlebihan.

## Anchor

- Dark Mode adalah **default**. Tidak memakai dynamic color sistem / wallpaper-derived palette.
- **Satu-satunya pengecualian warna dinamis**: gradien dari warna dominan sampul album di
  layar *Now Playing* (termasuk **Kartu Lirik** dan **Layar Fullscreen Live Lyrics**) dan
  *Detail Playlist*. Warna itu harus diredupkan dan menyatu ke `#151B22` → `#0C1014`
  (fallback lembut memakai token surface, fallback kartu/layar penuh `#243447`).
  Untuk lirik, warna dominan di-*tune*: saturasi tetap hidup, luminance ditekan supaya
  teks putih selalu kontras (minimal rasio 4.5:1). Ini masih bagian dari kontrak *Now
  Playing*, bukan warna dinamis baru.
- Sumber token: `app/src/main/java/com/dengerin/app/core/theme/Color.kt`
- Pembungkus tema: `app/src/main/java/com/dengerin/app/core/theme/Theme.kt`
- Pembaca token di UI: `LocalDengerinColors.current` dan `MaterialTheme.colorScheme`.

## Token warna

| Token | Dark (default) | Light |
| --- | --- | --- |
| background | `#0C1014` | `#FFFFFF` |
| surface / card | `#151B22` | `#F6F8FA` |
| surfaceElevated / input | `#1E2630` | `#EDF1F5` |
| border (1 dp solid) | `#27313D` | `#DCE3EA` |
| textPrimary | `#F5F7FA` | `#0F1419` |
| textSecondary | `#8B98A5` | `#5B6B7A` |
| accent | `#1ED760` | `#1ED760` |
| accentSoftContainer | `#1A1ED760` | `#D8F7E4` |
| accentSoftContent | `#69E899` | `#108A43` |
| error | `#FF6B6B` | `#D32F2F` |

## Aturan aktif

- Aksen utama `#1ED760` (Emerald Green segar). Soft container hanya untuk status aktif
  (lagu sedang diputar, chip tema terpilih, indikator aktif, tombol utama).
- Kartu datar: sudut membulat, border `1 dp`, tanpa elevation/glow/blur berlebihan.
- Ganti tema **wajib instan**, tanpa animasi, tanpa `Crossfade`, tanpa layer penutup
  full-screen. Cukup satu rekomposisi dengan token baru + flip flag system bar via
  `WindowCompat`. `enableEdgeToEdge()` hanya dipanggil sekali saat startup.
- Ikon: vektor Material (`material-icons-extended`). Semua ikon-only `IconButton`
  wajib punya `contentDescription`.
- Teks dan kontrol berstatus aktif wajib memakai token eksplisit, bukan warna default
  Material, supaya tetap kontras di dua tema.
- Hindari warna hardcoded di dalam composable; ambil dari `LocalDengerinColors.current`.
- Semua aksi yang butuh sesi Spotify boleh gagal dengan pesan jelas, tidak boleh crash
  dan tidak boleh menutup UI dengan dialog agresif.

## Layout Fase 1

- Navigasi utama: 3 tab — `Beranda`, `Cari`, `Koleksi`.
- Daftar hasil: thumbnail kotak `52 dp`, judul bold maks 1 baris, artis + durasi
  (`3:42`), indikator hijau halus untuk lagu aktif.
- System bar mengikuti mode `Sistem / Gelap / Terang` yang dipilih di tab Koleksi.

## Layout Fase 2

### Beranda

- Header: sapaan sesuai jam (`Selamat Pagi / Siang / Sore / Malam`) + avatar Spotify bulat
  di kanan + chip `Semua / Musik / Playlist / Riwayat` yang benar-benar memfilter section.
- Grid pintasan **2 kolom, maksimal 6 item**: tinggi `56 dp`, `#151B22`, border `1 dp`
  `#27313D`, sudut `10 dp`; sampul kotak menempel di kiri, judul maksimal 2 baris.
- Kartu aksi cepat hanya saat belum login: tipis, `#151B22`, border `#27313D`, dua tombol
  sejajar — `Masuk Spotify` (accent) dan `Impor Link` (outline).
- 5 carousel horizontal: `Baru Saja Diputar`, `Playlist Kamu`, `Sering Kamu Putar`,
  `Hits Indonesia Hari Ini`, `Rekomendasi Santai`. Kartu `140dp × 140dp`, sudut `12 dp`,
  judul bold 1 baris, sekunder berupa artis/jumlah lagu.

### Koleksi

- Kartu status Akun Spotify di atas. Belum login: `Masuk Spotify` + `Impor Link Playlist`.
  Sudah login: avatar, nama, `Sinkronkan`, `Impor Link`, `Keluar`.
- Daftar: `Lagu Disukai`, `Riwayat Putar`, semua playlist dengan badge `Spotify / Impor / Lokal`.
- Tombol `+` untuk playlist lokal baru; dialog impor memakai tombol `Tempel` clipboard.
- Ikon Pengaturan kanan atas untuk ganti tema instan.

### Detail Playlist

- Sampul besar di tengah atas dengan gradien lembut dari warna dominan sampul ke
  `#0C1014` (fallback `#151B22`).
- Judul, jumlah lagu, tombol `Putar Semua` (accent) dan `Acak (Shuffle)` (outline),
  lalu daftar lagu penuh.

### Mini player & Now Playing

- Mini player melayang: jarak `8 dp` kiri/kanan, sudut `14 dp`, `#151B22`, border `1 dp`
  `#27313D`, di atas bottom nav, progress bar hijau `2 dp` di tepi bawah.
- Now Playing: sampul 1:1 sudut `16 dp`, slider seekable, kontrol
  Previous / Play-Pause bundar besar / Next, plus Hati (suka/batal), Shuffle, Repeat.
- Gradien sampul hanya di sini dan Detail Playlist, harus mulus ke background.

### Live Lyrics (bagian dari Now Playing)

- Kartu Lirik muncul di bawah kontrol utama: sudut `16 dp`, border `1 dp` mengikuti token
  border, tinggi `~280 dp`, latar gradien lembut dari warna dominan sampul yang sudah
  diredupkan. Header `Lirik` bold putih + tombol perluas bundar semi-transparan.
- Layar Fullscreen Live Lyrics memakai warna dominan yang sama (edge-to-edge), teks putih,
  daftar lirik rata kiri dengan baris aktif putih penuh, baris lewat `alpha 0.42`, baris
  berikutnya `alpha 0.72`; fade di tepi atas/bawah daftar.
- Kontrol sinkronisasi (`±0.5s`) dan tombol `Kembali ke lirik` memakai surface putih
  semi-transparan, bukan aksen hijau, supaya tidak menambah warna baru.

## Fase 3 — Profil Opsional (Login Google, Level & Kosmetik)

Login Google **opsional**. Beranda, pemutaran, pencarian, dan koleksi tetap penuh tanpa
akun. Profil hanya dibuka lewat ikon di kanan atas Beranda.

### Pengecualian efek

Selain gradien sampul pada *Now Playing* / *Detail Playlist*, **satu-satunya tempat yang
boleh memakai gradient+sweep berputar dan glow adalah bingkai avatar serta pita title di
layar Profil** (dan pratinjau kecil bingkai di Beranda). Glow dibuat dari concentric stroke
dengan alpha rendah, bukan blur berlebih, dan tidak boleh bocor ke kartu/header lain.

### Token kosmetik profil

Semua warna di bawah didefinisikan di `core/theme/Color.kt` sebagai
`DengerinColors.cosmetics`, dibaca lewat `LocalDengerinColors.current`. Nilainya sama di
tema gelap dan terang supaya identitas level konsisten.

| Token kosmetik | Hex | Dipakai |
| --- | --- | --- |
| emerald | `#1ED760` | Bingkai Lv. 3, Music Lover, Playlist Maker (aksen utama) |
| cyberBlue / cyberCyan | `#0088FF` / `#00F0FF` | Bingkai Lv. 7, Audiophile |
| amethyst / magenta | `#9D4EDD` / `#FF2A85` | Bingkai Lv. 12, Heavy Rotation |
| gold / amber | `#FFD700` / `#FF7B00` | Bingkai Lv. 20, Music Legend |
| cosmicViolet / cosmicRose / cosmicTeal | `#9D4EDD` / `#FF2A85` / `#00F0FF` | Bingkai Lv. 35, Maestro |
| twilightOrange / twilightPurple | `#FF9F45` / `#9D4EDD` | Chill Vibes |
| nightBlue / nightIndigo | `#0B3D91` / `#1B2A6B` | Night Owl |
| slate | `#27313D` | New Listener & frame default (memakai token `border`) |
| creatorBlack / creatorGold / creatorNeon | `#05080B` / `#8A6A12` / `#0B7F3B` | Title eksklusif `creator_dev` |

Progress XP, lencana aktif, dan tombol utama tetap memakai `accent #1ED760`. Teks di atas
gradien kosmetik wajib putih (`textPrimary` di tema gelap) atau kontras dengan token
`onAccent` lokal pada komponen ribbon.

## Fase 4 — Developer & Testing Panel

Akses khusus hanya untuk `ibrahimhafiz840@gmail.com` (pembandingan case-insensitive). Panel
tidak boleh dirender, dibuka, atau dipanggil lewat repository untuk email lain. Pemeriksaan UI
hanya kenyamanan; aturan Firestore tetap menjadi pagar akhir.

### Lencana DEV & Title Creator

- Lencana `DEV` memakai ikon `</>`, teks `DEV`, dan pill gradien `emerald → cyberCyan →
  emerald`. Teks memakai `background` supaya kontras di atas neon.
- Lencana muncul di samping pita nama pada kartu profil dan sebagai badge ringkas di avatar
  Beranda. Tidak boleh dipakai sebagai dekorasi akun lain.
- Title eksklusif `creator_dev` ("Creator") hanya masuk katalog dan hanya bisa dipakai akun
  developer. Gradiennya memakai token `creatorBlack`, `creatorGold`, dan `creatorNeon` agar
  tetap terbaca dengan teks putih.

### Panel testing

Kartu **Developer Panel** memakai border `accent #1ED760`, tidak elevation, dan hanya muncul
di layar Profil milik developer. Dua aksinya: ubah level diri sendiri (simpan ke cache lokal
lalu `users/{uid}`) dan ubah `totalXp` pengguna lain dari hasil pencarian `users`. Input level
dibatasi `1–999`; konversi memakai `xpForLevel(level) = (level - 1)² × 40`.

