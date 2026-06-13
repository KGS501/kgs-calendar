from __future__ import annotations

from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts" / "kgs_play_store_feature_graphic.png"
LOGO = ROOT / "logo" / "logo.png"
W, H = 1024, 500
S = 3


def c(hex_color: str) -> tuple[int, int, int, int]:
    hex_color = hex_color.lstrip("#")
    if len(hex_color) == 6:
        return tuple(int(hex_color[i : i + 2], 16) for i in (0, 2, 4)) + (255,)
    return tuple(int(hex_color[i : i + 2], 16) for i in (0, 2, 4, 6))


def scaled_font(size: int, weight: str = "regular") -> ImageFont.FreeTypeFont:
    candidates = []
    windir = Path("C:/Windows/Fonts")
    if weight == "bold":
        candidates += [windir / "segoeuib.ttf", windir / "arialbd.ttf"]
    elif weight == "semibold":
        candidates += [windir / "seguisb.ttf", windir / "segoeuib.ttf"]
    else:
        candidates += [windir / "segoeui.ttf", windir / "arial.ttf"]
    for path in candidates:
        if path.exists():
            return ImageFont.truetype(str(path), size * S)
    return ImageFont.load_default()


FONT = scaled_font(18)
FONT_SM = scaled_font(13)
FONT_XS = scaled_font(10)
FONT_MD = scaled_font(22, "semibold")
FONT_BIG = scaled_font(42, "semibold")
FONT_BOLD = scaled_font(18, "bold")


def xy(box: Iterable[int]) -> tuple[int, ...]:
    return tuple(int(v * S) for v in box)


def rounded(draw: ImageDraw.ImageDraw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(xy(box), radius=radius * S, fill=fill, outline=outline, width=width * S)


def text(draw: ImageDraw.ImageDraw, pos, value, font=FONT, fill="#2e2725", anchor=None):
    draw.text((pos[0] * S, pos[1] * S), value, font=font, fill=c(fill), anchor=anchor)


def pill(draw, box, fill, outline=None, width=1):
    x1, y1, x2, y2 = box
    rounded(draw, box, (y2 - y1) // 2, c(fill), c(outline) if outline else None, width)


def add_shadow(base: Image.Image, box, radius, blur, offset, alpha):
    shadow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    ox, oy = offset
    sd.rounded_rectangle(xy((box[0] + ox, box[1] + oy, box[2] + ox, box[3] + oy)), radius=radius * S, fill=(25, 25, 25, alpha))
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur * S))
    base.alpha_composite(shadow)


def draw_check(draw, cx, cy, color="#ffffff", width=3):
    pts = [(cx - 6, cy), (cx - 2, cy + 5), (cx + 8, cy - 7)]
    draw.line([(x * S, y * S) for x, y in pts], fill=c(color), width=width * S, joint="curve")


def event_card(draw, box, color, label, sub=None, dotted=False, checked=False, priority=False, muted=False):
    fill = color + ("cc" if muted else "ff")
    rounded(draw, box, 8, c(fill))
    if dotted:
        x1, y1, x2, y2 = box
        for x in range(x1 + 6, x2 - 6, 8):
            draw.line(xy((x, y1, min(x + 4, x2 - 6), y1)), fill=c("#7c655d"), width=S)
            draw.line(xy((x, y2, min(x + 4, x2 - 6), y2)), fill=c("#7c655d"), width=S)
        for y in range(y1 + 6, y2 - 6, 8):
            draw.line(xy((x1, y, x1, min(y + 4, y2 - 6))), fill=c("#7c655d"), width=S)
            draw.line(xy((x2, y, x2, min(y + 4, y2 - 6))), fill=c("#7c655d"), width=S)
    x1, y1, _, _ = box
    if checked:
        draw.ellipse(xy((x1 + 8, y1 + 9, x1 + 24, y1 + 25)), fill=c("#ffffff"))
        draw_check(draw, x1 + 16, y1 + 17, "#78a27f", 2)
        tx = x1 + 31
    else:
        draw.ellipse(xy((x1 + 10, y1 + 11, x1 + 24, y1 + 25)), outline=c("#23201f"), width=2 * S)
        tx = x1 + 31
    if priority:
        for r, a in [(17, 70), (23, 35)]:
            draw.ellipse(xy((x1 + 17 - r, y1 + 18 - r, x1 + 17 + r, y1 + 18 + r)), outline=(255, 159, 10, a), width=2 * S)
    text(draw, (tx, y1 + 8), label, FONT_SM if len(label) > 13 else FONT_BOLD, "#1f1b1a")
    if sub:
        text(draw, (tx, y1 + 27), sub, FONT_XS, "#4f4541")


def draw_phone(base: Image.Image):
    add_shadow(base, (78, 132, 946, 488), 44, 18, (0, 10), 50)
    draw = ImageDraw.Draw(base)

    rounded(draw, (80, 128, 944, 488), 46, c("#1c1c1f"))
    rounded(draw, (94, 142, 930, 474), 35, c("#f8ebe5"))
    rounded(draw, (499, 143, 525, 474), 10, c("#202024"))
    rounded(draw, (506, 152, 518, 464), 6, c("#2a2a2e"))
    draw.line(xy((511, 154, 511, 463)), fill=c("#515159"), width=S)
    draw.ellipse(xy((898, 158, 908, 168)), fill=c("#28282d"))

    # App header
    text(draw, (126, 172), "Mai", FONT_MD, "#322a27")
    text(draw, (183, 174), "^", FONT_MD, "#322a27")
    text(draw, (820, 173), "3D", FONT_SM, "#6c5b53")
    pill(draw, (853, 166, 902, 189), "#2f5aea")
    text(draw, (877, 178), "Sync", FONT_XS, "#ffffff", "mm")

    # Month strip and full-day abstraction
    months = [("März", False), ("Apr.", False), ("Mai", True), ("Juni", False), ("Juli", False)]
    x = 126
    for m, selected in months:
        pill(draw, (x, 207, x + 73, 236), "#ffd7c2" if selected else "#fff7f2")
        text(draw, (x + 36, 222), m, FONT_SM, "#302927", "mm")
        x += 82

    text(draw, (554, 205), "Ganztägig", FONT_SM, "#6b5a52")
    pill(draw, (632, 201, 908, 218), "#e7d9d2")
    pill(draw, (640, 204, 742, 215), "#2f5aea")
    pill(draw, (748, 204, 810, 215), "#73aa96")
    pill(draw, (816, 204, 898, 215), "#f7c84b")
    text(draw, (642, 225), "Abstraktion: 7 volle Tage gebündelt", FONT_XS, "#7a6a62")

    # Day labels
    for x, day, num, selected in [(146, "Mi", "27", False), (354, "Do", "28", True), (738, "Fr", "29", False)]:
        text(draw, (x, 261), day, FONT_SM, "#8a5a42" if selected else "#443934", "mm")
        if selected:
            draw.ellipse(xy((x - 22, 267, x + 22, 311)), fill=c("#a65d2b"))
            text(draw, (x, 289), num, FONT_MD, "#ffffff", "mm")
        else:
            text(draw, (x, 289), num, FONT_MD, "#302927", "mm")

    # Grid
    col_x = [108, 274, 548, 714]
    row_y = [320, 369, 418, 467]
    for i in range(3):
        for j in range(3):
            rounded(draw, (col_x[i], row_y[j], col_x[i + 1] - 10, row_y[j + 1] - 7), 8, c("#fffaf7"))
    for idx, hour in enumerate(["09:00", "10:00", "11:00", "12:00"]):
        text(draw, (93, 324 + idx * 51), hour, FONT_XS, "#6a5d57", "ra")
    pill(draw, (240, 360, 321, 381), "#5c5653")
    text(draw, (280, 370), "10:28", FONT_XS, "#ffffff", "mm")
    draw.line(xy((322, 371, 708, 371)), fill=c("#69615d"), width=S)

    # Calendar content
    event_card(draw, (118, 382, 216, 443), "#2d8738", "hfhr", "hrh")
    event_card(draw, (220, 443, 328, 482), "#73aa96", "zstwz")
    event_card(draw, (282, 332, 468, 370), "#2d8738", "start", priority=True)
    event_card(draw, (284, 443, 502, 482), "#73aa96", "Klavier Caspar")
    event_card(draw, (718, 332, 918, 424), "#f8c948", "nextcloud", priority=True)
    event_card(draw, (556, 384, 704, 433), "#ffb13b", "Vorläufig", "Termin", dotted=True, muted=True)
    event_card(draw, (118, 449, 216, 486), "#9bc39b", "Untitled", checked=True, muted=True)
    event_card(draw, (556, 434, 704, 482), "#d8e4d6", "Review", "erledigt", checked=True)


def main() -> None:
    base = Image.new("RGBA", (W * S, H * S), c("#fff9f5"))
    draw = ImageDraw.Draw(base)

    # Minimal warm background with a subtle brand-colored lower accent.
    for y in range(H * S):
        t = y / (H * S)
        r = int(255 * (1 - t) + 245 * t)
        g = int(249 * (1 - t) + 236 * t)
        b = int(245 * (1 - t) + 230 * t)
        draw.line((0, y, W * S, y), fill=(r, g, b, 255))
    draw.rounded_rectangle(xy((-120, 358, 1140, 655)), radius=70 * S, fill=c("#eaf0ff"))
    draw.rounded_rectangle(xy((65, 372, 960, 556)), radius=46 * S, fill=c("#f6e4db"))

    # Brand lockup
    logo = Image.open(LOGO).convert("RGBA")
    logo = logo.resize((66 * S, 66 * S), Image.Resampling.LANCZOS)
    base.alpha_composite(logo, (306 * S, 32 * S))
    text(draw, (386, 58), "KGS Calendar", FONT_BIG, "#1f2328")
    text(draw, (389, 101), "minimal 3D day planning", FONT_SM, "#6c737f")

    draw_phone(base)

    out = base.resize((W, H), Image.Resampling.LANCZOS).convert("RGB")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    out.save(OUT, optimize=True)
    print(OUT)


if __name__ == "__main__":
    main()
