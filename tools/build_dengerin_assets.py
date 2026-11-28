#!/usr/bin/env python3
"""Bangun identitas vektor Dengerin dari proporsi eksak dengerin.jpg (baca-saja).

Path dibangun dari geometri matematis murni (M, H, V, L, A, Z), tanpa
contour tracing. Seluruh ukuran diukur langsung dari piksel sumber, lalu
dipetakan ke viewport 108dp dengan bounding box tepat 46x46 dp yang
berpusat di (54, 54) — tanpa offset optik manual.
"""

from __future__ import annotations

import hashlib
import json
import math
import re
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE_NAMES = ("dengerin.jpg", "dengerin.png", "dengerin.jpeg", "dengerin.webp")
GEN_DIR = ROOT / "tools" / "generated"
DRAWABLE_DIR = ROOT / "app" / "src" / "main" / "res" / "drawable"
RES_DIR = ROOT / "app" / "src" / "main" / "res"

MIPMAPS = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

SYMBOL_COLOR = "#141414"
WHITE = "#FFFFFF"
GREEN_LIGHT = "#29DC6B"
GREEN_DEEP = "#1FC25D"
GREEN_SAMPLE = "#24D065"

VIEWPORT = 108.0
SYMBOL_SPAN = 46.0
CENTER = 54.0
TARGET_MIN = CENTER - SYMBOL_SPAN / 2.0  # 31.0

PLAY_CORNER_PX = 11.0  # jari-jari sudut Play (dulu 6 px, kini lebih halus)
SS = 8  # supersampling mipmap


def find_source() -> Path:
    found = [ROOT / name for name in SOURCE_NAMES if (ROOT / name).is_file()]
    if len(found) != 1:
        raise SystemExit("Harus ada tepat satu file dengerin.* di root: " + str(found))
    return found[0]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def components(mask: np.ndarray, step: int = 2, min_area: int = 40):
    """4-neighbours connected components pada mask yang downsample."""
    h, w = mask.shape
    hh, ww = h // step, w // step
    small = mask[:hh * step, :ww * step].reshape(hh, step, ww, step).mean(axis=(1, 3)) > 0.4
    seen = np.zeros_like(small, dtype=bool)
    result = []
    for y in range(hh):
        for x in range(ww):
            if not small[y, x] or seen[y, x]:
                continue
            queue = deque([(y, x)])
            seen[y, x] = True
            pts = []
            while queue:
                cy, cx = queue.popleft()
                pts.append((cy, cx))
                for ny, nx in ((cy - 1, cx), (cy + 1, cx), (cy, cx - 1), (cy, cx + 1)):
                    if 0 <= ny < hh and 0 <= nx < ww and small[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        queue.append((ny, nx))
            if len(pts) < min_area:
                continue
            py = [p[0] for p in pts]
            px = [p[1] for p in pts]
            result.append({
                "area": len(pts) * step * step,
                "x0": int(min(px) * step),
                "x1": int(max(px) * step + step - 1),
                "y0": int(min(py) * step),
                "y1": int(max(py) * step + step - 1),
            })
    result.sort(key=lambda item: item["area"], reverse=True)
    return result


def radial_runs(mask: np.ndarray, cx: float, cy: float, degree: float,
                r_max: float = 900.0, step: float = 0.5):
    theta = math.radians(degree)
    runs = []
    start = None
    previous = False
    radius = 0.0
    while radius <= r_max:
        x = int(round(cx + radius * math.cos(theta)))
        y = int(round(cy - radius * math.sin(theta)))
        hit = 0 <= x < mask.shape[1] and 0 <= y < mask.shape[0] and bool(mask[y, x])
        if hit and not previous:
            start = radius
        if previous and not hit:
            runs.append((start, radius - step))
        previous = hit
        radius += step
    if previous:
        runs.append((start, radius - step))
    return [run for run in runs if run[1] - run[0] > 12]


def measure(source: Path):
    """Ukur semua proporsi eksak langsung dari piksel. Read-only."""
    array = np.array(Image.open(source).convert("RGB")).astype(np.float32)
    h, w = array.shape[:2]
    red, green_ch, blue = array[:, :, 0], array[:, :, 1], array[:, :, 2]
    greenish = (green_ch > 150) & (green_ch > red + 60) & (green_ch > blue + 40)
    green = np.median(array[greenish], axis=0)
    dark = np.array([20.0, 20.0, 20.0], dtype=np.float32)
    dg = np.linalg.norm(array - green, axis=2)
    dd = np.linalg.norm(array - dark, axis=2)
    mask = (dg / (dg + dd + 1e-6)) > 0.5

    comps = components(mask)
    if len(comps) < 3:
        raise SystemExit("QC gagal: komponen D/wave/Play tidak ditemukan.")
    body, wave_comp, play = comps[0], comps[1], comps[2]

    ys, xs = np.where(mask)
    bbox = {
        "x0": int(xs.min()), "x1": int(xs.max()),
        "y0": int(ys.min()), "y1": int(ys.max()),
    }
    bw = bbox["x1"] - bbox["x0"] + 1
    bh = bbox["y1"] - bbox["y0"] + 1

    # Pusat lengkungan D: dari bbox badan D.
    r_out = (body["y1"] - body["y0"]) / 2.0
    cx = body["x1"] - r_out
    cy = (body["y0"] + body["y1"]) / 2.0

    east = radial_runs(mask, cx, cy, 0)
    west = radial_runs(mask, cx, cy, 180)
    if len(east) < 2 or not west:
        raise SystemExit("QC gagal: radial D/wave tidak terbaca.")

    def nearest(runs, target, which):
        return min(runs, key=lambda run: abs(run[which] - target))[which]

    r_in_raw = nearest(east, 299.0, 0)
    r_out_raw = nearest(east, 464.0, 1)
    stroke = r_out_raw - r_in_raw
    l_in_raw = nearest(west, 242.0, 0)
    l_out_raw = nearest(west, 407.0, 1)
    wave_r_in = nearest(east, 583.0, 0)
    wave_r_out = nearest(east, 696.0, 1)  # dipertahankan persis dari sumber

    # --- Normalisasi minimum ---
    # Source D hampir simetris: rOut=464, lOut=407. Ratakan keduanya ke
    # titik tengah (435.5) agar busur menjadi A(r,r) lingkaran murni dan
    # bbox geometri ter-gambar menjadi persegi. Stroke tetap 165 persis.
    # Wave tetap 696 (deviasi vertikal ~13.5px dari puncak sumber).
    # Deviasi maksimum ~28.5px dari rentang ~1131px = 2.5% — sangat kecil.
    d_radius = (l_out_raw + r_out_raw) / 2.0
    r_out = l_out = d_radius
    r_in = l_in = d_radius - stroke
    hole_cx = cx

    play_h = play["y1"] - play["y0"] + 1
    play_side = float(play_h)                     # sisi segitiga sama sisi
    play_width = play_side * math.sqrt(3.0) / 2.0
    play_left = hole_cx - play_width / 2.0
    play_right = hole_cx + play_width / 2.0
    play_top = cy - play_side / 2.0
    play_bottom = cy + play_side / 2.0

    # Bbox dari geometri yang benar-benar digambar (bukan mask piksel).
    left, right = cx - l_out, cx + wave_r_out
    top, bottom = cy - wave_r_out, cy + r_out
    span = float(int(round(max(right - left, bottom - top))))
    frame_cx, frame_cy = (left + right) / 2.0, (top + bottom) / 2.0
    x0 = int(round(frame_cx - span / 2.0))
    y0 = int(round(frame_cy - span / 2.0))
    bbox = {
        "x0": x0, "x1": x0 + int(span) - 1,
        "y0": y0, "y1": y0 + int(span) - 1,
    }
    bw = bh = span

    measured = {
        "image": [w, h],
        "green": "#" + "".join(f"{int(round(v)):02X}" for v in green[:3]),
        "bbox": bbox,
        "bbox_size": [bw, bh],
        "cx": cx, "cy": cy,
        "r_out": r_out, "r_in": r_in,
        "l_out": l_out, "l_in": l_in,
        "stroke": stroke,
        "wave_r_in": wave_r_in, "wave_r_out": wave_r_out,
        "wave_gap": wave_r_in - d_radius,
        "wave_stroke": wave_r_out - wave_r_in,
        "play_side": play_side,
        "play_width": play_width,
        "play_left": play_left, "play_right": play_right,
        "play_top": play_top, "play_bottom": play_bottom,
        "hole_cx": hole_cx,
        "play_measured_bbox": [play["x0"], play["x1"], play["y0"], play["y1"]],
        "normalization": {
            "d_radius": d_radius,
            "delta_l_out": d_radius - l_out_raw,
            "delta_r_out": d_radius - r_out_raw,
            "delta_l_in": l_in - l_in_raw,
            "delta_r_in": r_in - r_in_raw,
            "max_deviation_pct": round(
                100 * max(abs(d_radius - l_out_raw), abs(d_radius - r_out_raw)) / span, 2),
            "arcs": "circular (rx == ry), uniform scale",
        },
    }
    return measured, mask


class Mapper:
    """Petakan koordinat sumber (relatif bbox) ke viewport, bbox tepat 46x46 di tengah."""

    def __init__(self, measured, viewport: float = VIEWPORT, span: float = SYMBOL_SPAN):
        bw, bh = measured["bbox_size"]
        if abs(bw - bh) > 1e-6:
            raise SystemExit("QC gagal: bbox ternormalisasi harus persegi.")
        # Scaling seragam -> semua busur tetap A(r,r) lingkaran murni.
        self.sx = self.sy = span / bw
        self.tx = (viewport - span) / 2.0
        self.ty = (viewport - span) / 2.0
        self.ox, self.oy = measured["bbox"]["x0"], measured["bbox"]["y0"]

    def x(self, value: float) -> float:
        return self.tx + (value - self.ox) * self.sx

    def y(self, value: float) -> float:
        return self.ty + (value - self.oy) * self.sy

    def rx(self, value: float) -> float:
        return value * self.sx

    def ry(self, value: float) -> float:
        return value * self.sy

    def mapped_bbox(self, measured):
        left = self.x(measured["bbox"]["x0"])
        right = self.x(measured["bbox"]["x1"] + 1)
        top = self.y(measured["bbox"]["y0"])
        bottom = self.y(measured["bbox"]["y1"] + 1)
        return [left, top, right, bottom]


def fmt(value: float) -> str:
    text = f"{value:.4f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def build_path(m: dict, mapper: Mapper) -> str:
    cx, cy = mapper.x(m["cx"]), mapper.y(m["cy"])
    # Scaling seragam -> satu nilai radius untuk sumbu x dan y (lingkaran murni).
    r_out = mapper.rx(m["r_out"])
    r_in = mapper.rx(m["r_in"])
    l_out = mapper.rx(m["l_out"])
    l_in = mapper.rx(m["l_in"])
    w_out = mapper.rx(m["wave_r_out"])
    w_in = mapper.rx(m["wave_r_in"])

    parts = []

    # D luar: sisi kiri lurus (V), siku tajam 90°, setengah lingkaran (A r,r).
    parts += [
        f"M{fmt(cx - l_out)},{fmt(cy - r_out)}",
        f"H{fmt(cx)}",
        f"A{fmt(r_out)},{fmt(r_out)} 0 0 1 {fmt(cx)},{fmt(cy + r_out)}",
        f"H{fmt(cx - l_out)}",
        f"V{fmt(cy - r_out)}",
        "Z",
    ]
    # Lubang D proporsional (evenOdd -> transparan).
    parts += [
        f"M{fmt(cx - l_in)},{fmt(cy - r_in)}",
        f"H{fmt(cx)}",
        f"A{fmt(r_in)},{fmt(r_in)} 0 0 1 {fmt(cx)},{fmt(cy + r_in)}",
        f"H{fmt(cx - l_in)}",
        f"V{fmt(cy - r_in)}",
        "Z",
    ]
    # Gelombang 90°: jam 12 -> jam 3, ujung rata (L), evenOdd -> terisi.
    parts += [
        f"M{fmt(cx)},{fmt(cy - w_out)}",
        f"A{fmt(w_out)},{fmt(w_out)} 0 0 1 {fmt(cx + w_out)},{fmt(cy)}",
        f"L{fmt(cx + w_in)},{fmt(cy)}",
        f"A{fmt(w_in)},{fmt(w_in)} 0 0 0 {fmt(cx)},{fmt(cy - w_in)}",
        "Z",
    ]

    # Play: segitiga sama sisi dengan tiga sudut dibulatkan halus.
    radius = mapper.rx(PLAY_CORNER_PX)
    if radius < 0.2:
        radius = 0.2
    tangent = radius * math.sqrt(3.0)
    vertices = [
        (mapper.x(m["play_left"]), mapper.y(m["play_top"])),
        (mapper.x(m["play_left"]), mapper.y(m["play_bottom"])),
        (mapper.x(m["play_right"]), mapper.y(m["cy"])),
    ]
    corners = []
    for index, vertex in enumerate(vertices):
        previous = vertices[(index - 1) % 3]
        following = vertices[(index + 1) % 3]
        incoming = [vertex[0] - previous[0], vertex[1] - previous[1]]
        outgoing = [following[0] - vertex[0], following[1] - vertex[1]]
        ni = math.hypot(*incoming)
        no = math.hypot(*outgoing)
        incoming = [v / ni for v in incoming]
        outgoing = [v / no for v in outgoing]
        enter = (vertex[0] - incoming[0] * tangent, vertex[1] - incoming[1] * tangent)
        leave = (vertex[0] + outgoing[0] * tangent, vertex[1] + outgoing[1] * tangent)
        corners.append((enter, leave))
    parts.append(f"M{fmt(corners[0][0][0])},{fmt(corners[0][0][1])}")
    for index, (_, leave) in enumerate(corners):
        parts.append(f"A{fmt(radius)},{fmt(radius)} 0 0 1 {fmt(leave[0])},{fmt(leave[1])}")
        next_enter = corners[(index + 1) % 3][0]
        parts.append(f"L{fmt(next_enter[0])},{fmt(next_enter[1])}")
    parts.append("Z")

    return " ".join(parts)


def vector_xml(data: str, color: str, viewport: float) -> str:
    path = (
        '    <path\n'
        f'        android:fillColor="{color}"\n'
        '        android:fillType="evenOdd"\n'
        f'        android:pathData="{data}" />\n'
    )
    if viewport != VIEWPORT:
        # Path dibangun di ruang viewport 108; skalakan dari origin agar
        # tetap berpusat di tengah viewport yang lebih kecil.
        factor = viewport / VIEWPORT
        path = (
            f'    <group android:scaleX="{factor:.9f}" android:scaleY="{factor:.9f}">\n'
            + path
            + '    </group>\n'
        )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{fmt(viewport)}dp"\n'
        f'    android:height="{fmt(viewport)}dp"\n'
        f'    android:viewportWidth="{fmt(viewport)}"\n'
        f'    android:viewportHeight="{fmt(viewport)}">\n'
        + path
        + '</vector>\n'
    )


def render_mipmap(size: int, m: dict, mapper: Mapper, *, round_icon: bool) -> Image.Image:
    n = size * SS
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    t = (xx + yy) / (2.0 * max(1, n - 1))
    start = np.array([0x29, 0xDC, 0x6B], dtype=np.float32)
    end = np.array([0x1F, 0xC2, 0x5D], dtype=np.float32)
    gradient = np.clip(start[None, None, :] * (1 - t[:, :, None]) +
                       end[None, None, :] * t[:, :, None], 0, 255).astype(np.uint8)
    background = Image.fromarray(gradient, "RGB").convert("RGBA")
    background = background.resize((size, size), Image.Resampling.LANCZOS)

    scale = size / VIEWPORT
    cx, cy = mapper.x(m["cx"]), mapper.y(m["cy"])

    def new_layer():
        image = Image.new("L", (n, n), 0)
        return image, ImageDraw.Draw(image)

    def pixel(point):
        return (point[0] * scale * SS, point[1] * scale * SS)

    def ring_shape(rx, ry, left):
        pts = [(cx - left, cy - ry), (cx, cy - ry)]
        pts += [
            (cx + rx * math.cos(math.radians(-90 + 180 * i / 96)),
             cy + ry * math.sin(math.radians(-90 + 180 * i / 96)))
            for i in range(97)
        ]
        pts.append((cx - left, cy + ry))
        return [pixel(point) for point in pts]

    def wave_shape():
        pts = [
            (cx + mapper.rx(m["wave_r_out"]) * math.cos(math.radians(-90 + 90 * i / 48)),
             cy + mapper.ry(m["wave_r_out"]) * math.sin(math.radians(-90 + 90 * i / 48)))
            for i in range(49)
        ]
        pts += [
            (cx + mapper.rx(m["wave_r_in"]) * math.cos(math.radians(-90 * i / 48)),
             cy + mapper.ry(m["wave_r_in"]) * math.sin(math.radians(-90 * i / 48)))
            for i in range(49)
        ]
        return [pixel(point) for point in pts]

    def play_shape():
        radius = mapper.rx(PLAY_CORNER_PX)
        tangent = radius * math.sqrt(3.0)
        vertices = [
            (mapper.x(m["play_left"]), mapper.y(m["play_top"])),
            (mapper.x(m["play_left"]), mapper.y(m["play_bottom"])),
            (mapper.x(m["play_right"]), mapper.y(m["cy"])),
        ]
        corners = []
        for index, vertex in enumerate(vertices):
            previous = vertices[(index - 1) % 3]
            following = vertices[(index + 1) % 3]
            incoming = [vertex[0] - previous[0], vertex[1] - previous[1]]
            outgoing = [following[0] - vertex[0], following[1] - vertex[1]]
            ni, no = math.hypot(*incoming), math.hypot(*outgoing)
            incoming = [v / ni for v in incoming]
            outgoing = [v / no for v in outgoing]
            enter = (vertex[0] - incoming[0] * tangent, vertex[1] - incoming[1] * tangent)
            leave = (vertex[0] + outgoing[0] * tangent, vertex[1] + outgoing[1] * tangent)
            corners.append((enter, vertex, leave))
        pts = []
        for index, (enter, vertex, leave) in enumerate(corners):
            pts.append(corners[index - 1][2])
            pts.append(enter)
            pts.append(vertex)
            pts.append(leave)
        return [pixel(point) for point in pts]

    # evenOdd: (D luar XOR lubang) OR wave OR Play.
    outer, odraw = new_layer()
    odraw.polygon(ring_shape(mapper.rx(m["r_out"]), mapper.ry(m["r_out"]),
                             mapper.rx(m["l_out"])), fill=255)
    hole, hdraw = new_layer()
    hdraw.polygon(ring_shape(mapper.rx(m["r_in"]), mapper.ry(m["r_in"]),
                             mapper.rx(m["l_in"])), fill=255)
    wave, wdraw = new_layer()
    wdraw.polygon(wave_shape(), fill=255)
    play, pdraw = new_layer()
    pdraw.polygon(play_shape(), fill=255)

    combined = np.bitwise_xor(np.array(outer), np.array(hole))
    combined = np.bitwise_or(combined, np.array(wave))
    combined = np.bitwise_or(combined, np.array(play))
    mask = Image.fromarray(combined).resize((size, size), Image.Resampling.LANCZOS)

    symbol = Image.new("RGBA", (size, size), (20, 20, 20, 0))
    symbol.putalpha(mask)
    result = Image.alpha_composite(background, symbol)
    if round_icon:
        circle = Image.new("L", (n, n), 0)
        ImageDraw.Draw(circle).ellipse((0, 0, n - 1, n - 1), fill=255)
        result.putalpha(circle.resize((size, size), Image.Resampling.LANCZOS))
    return result


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")


def main() -> None:
    source = find_source()
    measured, _ = measure(source)
    mapper = Mapper(measured)
    data = build_path(measured, mapper)

    allowed = set("MHVLAZ0123456789.,- ")
    if any(char not in allowed for char in data):
        raise SystemExit("QC gagal: path mengandung perintah di luar M/H/V/L/A/Z.")

    # QC centering: bbox harus tepat [31, 31, 77, 77].
    mapped = mapper.mapped_bbox(measured)
    target = [CENTER - SYMBOL_SPAN / 2] * 2 + [CENTER + SYMBOL_SPAN / 2] * 2
    if any(abs(a - b) > 1e-6 for a, b in zip(mapped, target)):
        raise SystemExit(f"QC gagal: bbox {mapped} tidak terpusat di {target}.")

    # QC lingkaran: setiap A harus rx == ry (tidak ada elips hasil scaling).
    for rx, ry in re.findall(r"A(-?[\d.]+),(-?[\d.]+)", data):
        if abs(float(rx) - float(ry)) > 1e-6:
            raise SystemExit(f"QC gagal: busur elips ditemukan (rx={rx}, ry={ry}).")

    files = {
        "ic_dengerin_mark.xml": vector_xml(data, SYMBOL_COLOR, VIEWPORT),
        "ic_launcher_foreground.xml": vector_xml(data, SYMBOL_COLOR, VIEWPORT),
        "ic_launcher_monochrome.xml": vector_xml(data, WHITE, VIEWPORT),
        "ic_dengerin_splash.xml": vector_xml(data, SYMBOL_COLOR, VIEWPORT),
        "ic_splash_logo.xml": vector_xml(data, GREEN_SAMPLE, VIEWPORT),
        "ic_notification_dengerin.xml": vector_xml(data, WHITE, 24.0),
    }
    for name, content in files.items():
        write_text(DRAWABLE_DIR / name, content)

    write_text(
        DRAWABLE_DIR / "ic_launcher_background.xml",
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<shape xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:shape="rectangle">\n'
        '    <gradient\n'
        '        android:angle="315"\n'
        f'        android:startColor="{GREEN_LIGHT}"\n'
        f'        android:endColor="{GREEN_DEEP}"\n'
        '        android:type="linear" />\n'
        '</shape>\n',
    )

    write_text(
        GEN_DIR / "dengerin-mark.svg",
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" '
        'viewBox="0 0 108 108">\n'
        f'  <path fill="{SYMBOL_COLOR}" fill-rule="evenodd" d="{data}" />\n'
        '</svg>\n',
    )

    for folder, size in MIPMAPS.items():
        target = RES_DIR / folder
        target.mkdir(parents=True, exist_ok=True)
        render_mipmap(size, measured, mapper, round_icon=False).save(
            target / "ic_launcher.png", optimize=True
        )
        render_mipmap(size, measured, mapper, round_icon=True).save(
            target / "ic_launcher_round.png", optimize=True
        )

    meta = {
        "source": source.name,
        "source_sha256": sha256(source),
        "mode": "pure-geometry (M/H/V/L/A/Z), exact pixel measurement, centered 46dp",
        "measured": {key: (round(value, 3) if isinstance(value, float) else value)
                     for key, value in measured.items()},
        "centering": {
            "viewport": VIEWPORT,
            "symbol_span_dp": SYMBOL_SPAN,
            "center": [CENTER, CENTER],
            "bbox_108": [round(v, 3) for v in mapper.mapped_bbox(measured)],
            "optical_offset": [0, 0],
        },
        "colors": {
            "symbol": SYMBOL_COLOR,
            "green_sample": GREEN_SAMPLE,
            "green_light": GREEN_LIGHT,
            "green_deep": GREEN_DEEP,
        },
        "path_data_108": data,
    }
    write_text(GEN_DIR / "dengerin-assets.json", json.dumps(meta, indent=2) + "\n")

    bbox = mapper.mapped_bbox(measured)
    print(f"source={source.name} sha256={sha256(source)[:16]}... green={measured['green']}")
    print(f"measured: cx={measured['cx']:.1f} cy={measured['cy']:.1f} "
          f"rOut={measured['r_out']:.1f} rIn={measured['r_in']:.1f} "
          f"stroke={measured['stroke']:.1f}")
    print(f"wave: {measured['wave_r_in']:.1f}..{measured['wave_r_out']:.1f} "
          f"gap={measured['wave_gap']:.1f}")
    print(f"viewport bbox 108: {[round(v, 3) for v in bbox]} (target 31..77)")
    print("generated: 6 vectors + background + 10 mipmaps + SVG/JSON")


if __name__ == "__main__":
    main()
