# Aset identitas Dengerin

Semua aset peluncur dibangun dari proporsi eksak `dengerin.jpg` di root
proyek. File sumber **tidak** dipindah atau diubah. Path vektor dibangun dari
**geometri matematis murni** (`M`, `H`, `V`, `L`, `A`, `Z`) tanpa contour
tracing, lalu dipetakan ke viewport 108dp dengan bounding box tepat
**46×46dp** yang berpusat di **(54, 54)** — tanpa offset optik manual.

```powershell
python tools/build_dengerin_assets.py
```

Skrip mengukur langsung dari piksel (read-only): tepi kiri/atas/bawah D,
ketebalan batang, pusat lengkungan `(cx, cy)`, `rInner`/`rOuter`, celah dan
ketebalan gelombang 90°, serta ukuran/posisi segitiga Play. Semua hasil
pengukuran disimpan di `tools/generated/dengerin-assets.json`.

Aset yang dihasilkan:

- `app/src/main/res/drawable/ic_dengerin_mark.xml` (viewport 108dp)
- `ic_launcher_foreground.xml` + `ic_launcher_monochrome.xml` (46dp,centering sempurna)
- `ic_launcher_background.xml` (gradasi 315° `#29DC6B` → `#1FC25D`)
- `ic_dengerin_splash.xml`, `ic_splash_logo.xml`, `ic_notification_dengerin.xml`
- 10 PNG mipmap (48/72/96/144/192 px, square + round)
- `tools/generated/dengerin-mark.svg` dan `dengerin-assets.json`

`android:fillType="evenOdd"` dipakai pada semua path sehingga lubang D
transparan dan Play tetap terisi. Splash memakai latar gelap `#121212`
dengan `ic_splash_logo` bermerek.
