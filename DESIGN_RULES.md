# DESIGN_RULES — Dengerin (Kontrak Fase 5: Spotify Minimal)

Kontrak desain Dengerin, diperbarui dari Fase 1–4 ke **Fase 5**. Anggap sebagai token tetap:
jangan menambah warna atau efek baru tanpa mengubah kontrak ini lebih dulu. Bahasa UI:
Indonesia santai & jelas. `AGENTS.md` mewajibkan kontrak ini dibaca dan diikuti sebelum
pekerjaan UI/arsitektur.

## Identitas

Dengerin memakai **struktur, rasa, dan animasi minimalis Spotify Android**: daftar padat,
kartu tanpa bingkai, hijau aksen, navigasi 4 tab permanen, mini player terkunci di atas
navigasi, dan halaman pemutar penuh layar yang mengalahkan dekorasi.

Aturan mutlak:

- **Dilarang** kata/tombol "Premium" atau "VIP" di mana pun (UI, string, dialog, drawer).
- **Dilarang** banner berlangganan, pop-up promosi, tab podcast/audiobook, glow berlebihan.
- Ganti tema **hanya** tersedia di `Pengaturan → Tampilan & Tema`; tidak ada tombol tema
  di Beranda, Koleksi, mini player, drawer, atau dialog.
- Nol kartu berbingkai tebal: tidak ada `border 1 dp` pada kartu lagu, carousel, chip, atau
  baris pengaturan. Pemisah cukup `Color.White.copy(alpha = 0.08f)`.

## Anchor

- Dark Mode adalah **default**. Tidak memakai dynamic color sistem / wallpaper-derived palette.
- Pengecualian warna dinamis: gradien dari warna dominan sampul album pada **Now Playing**,
  **Kartu Lirik**, **Layar Fullscreen Live Lyrics**, **Detail Playlist**, dan latar adaptif
  **Mini Player**. Warna wajib diredupkan ke `#121212` (dark) atau token surface (light).
  Untuk lirik, luminance ditekan supaya kontras teks putih minimal 4.5:1.
- Sumber token: `app/src/main/java/com/dengerin/app/core/theme/Color.kt`.
- Pembungkus tema: `app/src/main/java/com/dengerin/app/core/theme/Theme.kt`.
- Pembaca token di UI: `LocalDengerinColors.current` dan `MaterialTheme.colorScheme`.
- Hindari warna hardcoded di composable; ambil dari `LocalDengerinColors.current`, kecuali
  konstanta gradien navigasi/adaptif yang memang didefinisikan di sini.

## Token warna

| Token | Dark (default) | Light (Cool Slate) |
| --- | --- | --- |
| background | `#121212` | `#F4F6F8` |
| surface / sheet | `#181818` | `#FFFFFF` |
| surfaceElevated | `#282828` | `#EDF1F5` |
| shortcut | `#2A2A2A` | `#FFFFFF` |
| divider | `Color.White.copy(0.08f)` | `Color.Black.copy(0.08f)` |
| textPrimary | `#FFFFFF` | `#0F172A` |
| textSecondary | `#B3B3B3` | `#64748B` |
| accent | `#1ED760` | `#1DB954` |
| accentSoftContainer | `#1A1ED760` | `#D8F7E4` |
| accentSoftContent | `#69E899` | `#15803D` |
| onAccent | `#000000` | `#000000` |
| navGradientEarly | `Transparent` | `Transparent` |
| navGradientMid | `#EB000000` (alpha .55) | `#FFF4F6F8` |
| navGradientEnd | `#000000` | `#F4F6F8` |
| navActive | `#FFFFFF` | `#0F172A` |
| navInactive | `#B3B3B3` | `#64748B` |
| miniPlayerDark | `#181818` | `#181818` |
| onDarkSurface | `#FFFFFF` | `#FFFFFF` |
| error | `#FF6B6B` | `#D32F2F` |

## Aturan aktif

- Aksen `#1ED760` (dark) / `#1DB954` (light). Hijau hanya untuk status aktif, kontrol
  utama, centang suka, dan titik indikator shuffle/repeat.
- Kartu datar tanpa border dan tanpa elevation: sudut `4.dp` untuk sampul, `8.dp` untuk
  mini player/sheet, `16.dp` untuk sheet besar. Tidak ada glow/blur dekoratif.
- Ikon: vektor Material (`material-icons-extended`). Semua ikon-only `IconButton` wajib
  punya `contentDescription`.
- Teks dan kontrol aktif wajib memakai token eksplisit supaya kontras di dua tema.
- Ganti tema **wajib instan**, tanpa animasi/`Crossfade`/layer penutup. `enableEdgeToEdge()`
  hanya dipanggil sekali saat startup.
- Aksi yang butuh sesi eksternal boleh gagal dengan pesan jelas, tidak boleh crash.

## Layout — Beranda

- Header: avatar profil bulat `32 dp` kiri (buka side drawer) + chip filter
  `Semua / Musik / Playlist / Cache`; chip aktif hijau dengan teks hitam.
- **Akses Cepat** (2 kolom, maksimal 6): tinggi `54.dp`, `RoundedCornerShape(4.dp)`,
  latar `#2A2A2A`, tanpa border. Sampul `54.dp` full-bleed di kiri, judul `12.5.sp`
  SemiBold maks 2 baris di kanan, equalizer hijau mini saat lagu aktif diputar.
- Carousel horizontal: `Sering Kamu Putar`, `Mix Untukmu`, `Baru Diputar`,
  `Hits Indonesia Hari Ini`, `Playlist Kamu` (filter). **Tanpa kartu/border**: sampul
  `148.dp`, sudut `4.dp`, langsung di atas `#121212`; judul `13.5.sp` SemiBold maks 1
  baris; artis `12.sp` `#B3B3B3` maks 2 baris tepat di bawah sampul.

## Layout — Koleksi Kamu

- Header: avatar + `"Koleksi Kamu"` + ikon `Cari` dan `+` (playlist baru).
- Chip filter: `Playlist`, `Lagu Disukai`, `Artis`, `Cache`; baris sort `↕ Terbaru`
  dan toggle tampilan `List / Grid` di kanan.
- `"Lagu Disukai"` selalu pinned paling atas dengan pin hijau kecil dan sampul gradasi
  ungu-putih bergambar hati.
- Riwayat putar, playlist `Spotify / Impor / Lokal`, dialog impor dengan tombol `Tempel`.

## Layout — Cari / Jelajahi

- Kolom cari pill: `surfaceElevated` (dark) / `surface` (light), placeholder
  `"Apa yang ingin kamu dengarkan?"`, avatar sejajar dengan judul "Cari".
- 16 kartu "Jelajahi Semua", 2 kolom, tinggi `100.dp`, sudut `4.dp`, tanpa border;
  warna kartu berasal dari `colorHex` konten sampul genre (bukan token chrome baru).
- Mode aktif: top bar `←` + pill input + `✕`; `imePadding()`; query kosong menampil
  *Pencarian terkini* maks 10 dari DataStore.
- Live search: debounce `350 ms`, `distinctUntilChanged()`, `collectLatest()`, mulai
  pada `query.trim().length >= 2`. Urutan: saran teks (5), kartu Cocok dengan lirik (2),
  pintasan genre, lalu 6–8 lagu. Enter = tutup keyboard + simpan riwayat + 15–20 lagu.
- Header detail genre memakai gradien warna konten → `background`.

## Navigasi bawah & Mini Player

- Empat tab permanen: `Home`, `Cari`, `Koleksi Kamu`, `Buat`. Tab `Buat` membuka
  `ModalBottomSheet` (`Buat Playlist Baru`, `Impor dari Spotify`); bukan halaman.
- Mini player + nav adalah **satu `Column` di `Alignment.BottomCenter`**, bukan
  `Scaffold.bottomBar`. Nav selalu tampil di halaman utama/detail, disembunyikan hanya
  di Now Playing & Pengaturan.
- Urutan modifier overlay: `background(Brush.verticalGradient(...))` lalu
  `windowInsetsPadding(WindowInsets.navigationBars)`. Stop dark:
  `0.00 Transparent → 0.55 #EB000000 → 1.00 #000000`; stop light:
  `0.00 Transparent → 0.55 #FFF4F6F8 → 1.00 #F4F6F8`.
- Bottom nav: `indicatorColor = Color.Transparent`, ikon Filled putih saat aktif,
  Outlined `#B3B3B3` saat pasif, label mengikuti `navActive`/`navInactive`.
  Tap ulang tab aktif: Beranda/Koleksi scroll ke item 0, Cari fokus ke input.
- **Dilarang keras** free-drag, edge dock, morphing capsule, dan state
  `isMiniPlayerMinimized`, `dockedSide`, `expandedOffsetY`, `minimizedOffsetY`,
  `detectDragGestures` yang memindahkan kotak mini player.
- Mini player terkunci: tinggi `56.dp`, margin horizontal `8.dp`, margin bawah `6.dp`,
  sudut `8.dp`, latar `lerp(dominantColor, Color(0xFF181818), 0.65f)` dengan
  `animateColorAsState(tween(350))`. Isi: sampul `40.dp` sudut `4.dp` → judul
  (`basicMarquee`) + artis → tombol `+`/`CheckCircle` hijau → Play/Pause `28.dp`.
  Progress putih `2.dp` menempel di tepi paling bawah kartu.
  Tap = buka Now Playing. Swipe kiri/kanan **hanya pada area teks judul** = next/prev
  dengan animasi geser teks; kotak tidak ikut bergerak.

## Now Playing, lirik, antrean

- Masuk dari bawah: `slideInVertically(tween(280, FastOutSlowInEasing))`; drag-turun
  dengan `translationY` + sudut atas `24.dp`; lepas `> 25%` tinggi layar = tutup mulus.
  Tutup juga lewat panah `⌄`.
- Background: gradien vertikal warna dominan sampul → `#121212`.
- Top bar: `⌄` kiri; tengah 2 baris `"MEMUTAR DARI..."` `10.sp` uppercase + nama
  sumber `12.sp` bold; `⋮` kanan.
- Sampul: `HorizontalPager`, kotak sudut `8.dp`, swipe = lagu sebelumnya/berikutnya.
- Judul `22.sp` bold (`basicMarquee`), artis `15.sp` `#B3B3B3`, tombol `+`/centang hijau.
  Seekbar tipis putih membesar saat disentuh; waktu berjalan & tersisa, tap = toggle
  negatif.
- Kontrol: Shuffle (titik hijau saat aktif), Previous, Play/Pause bundar putih `64.dp`,
  Next, Repeat/Repeat One (titik hijau saat aktif).
- Baris utilitas: kiri indikator `Cache` / `Opus Audio`; kanan `Bagikan` dan `Antrean`.
- Kartu lirik: muncul hanya jika ada lirik, latar adaptif pekat, preview 4 baris +
  `"Perluas"` → layar penuh karaoke LRCLIB/YTM. Posisi dibaca via provider snapshot-backed;
  `derivedStateOf` + binary search hanya berubah saat baris aktif pindah, dan teks waktu
  diperbarui 1 Hz. Latar gradien album dianimasikan `animateColorAsState(tween(700))`
  hanya saat lagu berganti. Baris aktif di-anchor di **34% atas** viewport (`contentPadding`
  atas `24.dp`, bawah `max(55% viewport, LocalBottomClearance.current)`). Scroll memakai
  `animateScrollBy` + `tween(400, FastOutSlowInEasing)` untuk perpindahan `<= 5` baris yang
  terlihat; `animateScrollToItem` untuk `> 5` baris atau item belum terlihat; `scrollToItem`
  untuk lompatan `> 15` baris. Auto-scroll jeda `2.5 dtk` setelah sentuhan manual dilepas.
- Tipografi lirik: putih di atas gradien album yang diredupkan, tanpa hijau. Semua baris
  memakai `#FFFFFF` Bold `21.sp`/`29.sp` tetap; penekanan hanya lewat `graphicsLayer`:
  alpha `1.0` aktif / `0.66` mendatang / `0.38` lewat, scale `1.035` aktif / `1.0` lainnya,
  `tween(280, FastOutSlowInEasing)`, `transformOrigin(0f, 0.5f)`. Jarak `14.dp`. Indikator
  jeda instrumental berupa 3 titik berdenyut dengan tinggi tetap di baris `♪` (bukan item
  terpisah); alpha pulsing titik diajukan terpisah dari alpha penekanan baris. Edge-fade
  memakai dua overlay gradien biasa: `24.dp` atas (`fadeColor → Transparent`) dan `36.dp`
  bawah (`Transparent → fadeColor`), dengan `fadeColor = lerp(albumColor, miniPlayerDark,
  0.45f)`. **Dilarang** `CompositingStrategy.Offscreen`, `BlendMode.DstIn`, dan
  `Modifier.blur()` di belakang daftar lirik.
- `⋮` pada lagu: `ModalBottomSheet` `#282828` berisi header sampul/judul/artis dan menu
  *Tambahkan ke Lagu Disukai, Tambahkan ke playlist, Tambahkan ke antrean, Putar
  berikutnya, Bagikan, Info Audio/Cache*.
- `QueueSheet`: bagian *Sedang diputar* dan *Berikutnya di antrean* dengan hapus/ubah urutan.

## Animasi (ringan, 60/120 fps)

- `spotifyPressScale()`: `graphicsLayer { scaleX/scaleY = 0.97f }`, `tween(90)`,
  boleh dipakai pada kartu, chip, baris lagu, tombol; tidak menahan gestur scroll.
- Equalizer: 3 batang hijau mini pada lagu aktif; Lottie Compose untuk mikro-interaksi
  (`+` → centang `#1ED760`, indikator equalizer), hindari efek GPU berat.
- Enter mini player: `slideInVertically { it } + fadeIn(tween(200))`.
- Crossfade tab Home/Cari/Koleksi: `fadeIn(tween(150)) + fadeOut(tween(120))`.
- Semua animasi memakai `graphicsLayer`/`animate*AsState`; hindari recomposisi seluruh list.

## Pengaturan & Lisensi Pihak Ketiga

- Settings minimalis datar: grup dengan header tipis, baris bersih dengan ikon halus +
  `ChevronRight`, `Switch` hijau `#1ED760`, tanpa kartu menumpuk.
- `Pengaturan → Tentang Dengerin`: info Versi Aplikasi, pengecek update GitHub, lalu
  baris **"Lisensi pihak ketiga"** (subtitle *"Perangkat lunak sumber terbuka yang
  digunakan di Dengerin"*).
- `ThirdPartyLicensesScreen`: data asli hasil pemindaian APK via
  `Libs.Builder().withContext(context).build()` (AboutLibraries), ditambah entri layanan
  eksternal **LRCLIB Open Lyrics API** dan **GitHub Releases API**. Top bar `←` + judul +
  kolom filter nama. Daftar urut abjad: nama `15.sp` SemiBold, organization `12.sp`
  `#B3B3B3`, badge lisensi + versi. Detail: nama, versi, tautan repo/web (buka browser),
  dan teks lisensi lengkap yang bisa di-scroll.

## Ruang bawah otomatis (`LocalBottomClearance`)

`CompositionLocalProvider(LocalBottomClearance)` dipasang di root dan diteruskan ke
seluruh layar. Nilai sudah termasuk `WindowInsets.navigationBars`:

| Jenis layar | Mini player aktif | Idle |
| --- | --- | --- |
| Dengan Bottom Nav | `160.dp` | `96.dp` |
| Tanpa Bottom Nav (Settings, sub-halaman, Tentang, Lisensi) | `104.dp` | `36.dp` |

Wajib diterapkan pada **setiap** `LazyColumn`, `LazyVerticalGrid`, dan
`Column.verticalScroll` di seluruh aplikasi via
`contentPadding = PaddingValues(bottom = LocalBottomClearance.current)` atau
`Spacer(Modifier.height(LocalBottomClearance.current))`, termasuk halaman Pengaturan
dan Lisensi Pihak Ketiga. Baris terakhir tidak boleh terpotong mini player.

## Fase 3 — Profil Opsional (tetap berlaku)

Login Google opsional. Beranda, pemutaran, pencarian, koleksi penuh tanpa akun. Profil
dibuka lewat avatar kiri atas / side drawer. Gradien + sweep berputar + glow hanya
boleh pada bingkai avatar dan pita title di layar Profil (serta pratinjau kecil di
Beranda), dengan concentric stroke alpha rendah. Token kosmetik (`DengerinColors.cosmetics`,
sama di dua tema): emerald `#1ED760`, cyberBlue `#0088FF`, cyberCyan `#00F0FF`,
amethyst `#9D4EDD`, magenta `#FF2A85`, gold `#FFD700`, amber `#FF7B00`,
cosmicViolet `#9D4EDD`, cosmicRose `#FF2A85`, cosmicTeal `#00F0FF`,
twilightOrange `#FF9F45`, twilightPurple `#9D4EDD`, nightBlue `#0B3D91`,
nightIndigo `#1B2A6B`, slate `#27313D`, creatorBlack `#05080B`,
creatorGold `#8A6A12`, creatorNeon `#0B7F3B`. Progress XP & tombol utama tetap `#1ED760`.

## Fase 4 — Developer & Testing Panel (tetap berlaku)

Akses khusus `ibrahimhafiz840@gmail.com` (case-insensitive); panel tidak dirender untuk
email lain. Lencana `DEV`: ikon `</>`, pill gradien emerald→cyberCyan→emerald, teks
memakai `background`. Title `creator_dev` ("Creator") hanya untuk akun developer.
Panel testing di Profil developer: ubah level sendiri (cache lokal + `users/{uid}`)
dan `totalXp` pengguna lain dari pencarian `users`; level `1–999`,
`xpForLevel(level) = (level - 1)² × 40`.
