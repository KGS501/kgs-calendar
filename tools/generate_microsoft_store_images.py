"""Builds the Microsoft Store images from the app captures in docs/microsoft-store/app-captures.

Each image is a page of HTML that headless Chrome turns into a PNG. The look follows the Google Play
images: a pale blue background with soft waves, a two-tone headline, and the app in a frame.

    python tools/generate_microsoft_store_images.py
"""

from __future__ import annotations

import argparse
import base64
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / "docs" / "microsoft-store"
CAPTURES = STORE / "app-captures"
SCREENSHOTS = STORE / "screenshots"
ART = STORE / "art"
LOGO = ROOT / "desktopApp" / "src" / "jvmMain" / "resources" / "kgs_logo.png"

CHROME_PATHS = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
]

# The capture holds the window with its invisible resize border, which is cut off here.
CAPTURE_LEFT, CAPTURE_TOP, CAPTURE_WIDTH, CAPTURE_HEIGHT = 8, 1, 1859, 1028

BASE_CSS = """
* { box-sizing: border-box; margin: 0; padding: 0; }
html, body { width: 100%; height: 100%; overflow: hidden; }
body {
  position: relative; color: #0f1b2d;
  font-family: "Segoe UI Variable Display", "Segoe UI", sans-serif;
  background: linear-gradient(170deg, #f2f7ff 0%, #e6f0ff 55%, #d9e8ff 100%);
}
.waves { position: absolute; inset: 0; width: 100%; height: 100%; }
.abs { position: absolute; }
h1 { font-weight: 700; letter-spacing: -0.02em; line-height: 1.08; }
.blue { color: #0b57c7; }
.sub { color: #566579; font-weight: 400; line-height: 1.3; }
.center { text-align: center; left: 0; right: 0; }
.window {
  position: absolute; overflow: hidden; background: #edf4ff; border-radius: 16px;
  box-shadow: 0 34px 80px rgba(22, 56, 120, 0.30), 0 6px 20px rgba(22, 56, 120, 0.16);
  outline: 1px solid rgba(22, 56, 120, 0.14);
}
.window img, .crop img { position: absolute; display: block; max-width: none; }
.crop {
  position: absolute; overflow: hidden; background: #fff;
  box-shadow: 0 26px 60px rgba(22, 56, 120, 0.30), 0 4px 14px rgba(22, 56, 120, 0.16);
}
.key {
  display: inline-block; padding: 14px 26px; margin-right: 14px; border-radius: 16px;
  background: #fff; color: #0b57c7; font-size: 34px; font-weight: 600;
  box-shadow: 0 8px 0 #c6d9f5, 0 16px 26px rgba(22, 56, 120, 0.18);
}
.chip {
  display: inline-block; padding: 14px 30px; margin: 0 8px; border-radius: 40px;
  background: #fff; color: #0b57c7; font-size: 30px; font-weight: 600;
  box-shadow: 0 10px 24px rgba(22, 56, 120, 0.14);
}
"""


def data_uri(path: Path) -> str:
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")


def waves(width: int, height: int) -> str:
    """The soft blue curves from the corners, as on the other stores."""
    w, h = width, height
    return f"""
    <svg class="waves" viewBox="0 0 {w} {h}" preserveAspectRatio="none" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="a" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#b9d4fb"/><stop offset="1" stop-color="#8db6f4"/>
        </linearGradient>
      </defs>
      <path fill="url(#a)" opacity="0.75"
        d="M0 {h * 0.46} C {w * 0.10} {h * 0.62}, {w * 0.20} {h * 0.84}, {w * 0.42} {h} L0 {h} Z"/>
      <path fill="#a9caf8" opacity="0.70"
        d="M{w} {h * 0.62} C {w * 0.86} {h * 0.70}, {w * 0.74} {h * 0.84}, {w * 0.60} {h} L{w} {h} Z"/>
      <path fill="#7ea9ee" opacity="0.45"
        d="M0 {h * 0.80} C {w * 0.06} {h * 0.86}, {w * 0.14} {h * 0.93}, {w * 0.24} {h} L0 {h} Z"/>
    </svg>"""


def window(capture: str, left: int, top: int, width: int, style: str = "") -> str:
    """The app's window at [width], cut from a capture."""
    scale = width / CAPTURE_WIDTH
    height = round(CAPTURE_HEIGHT * scale)
    image_width = round(1875 * scale)
    image_left = -round(CAPTURE_LEFT * scale)
    image_top = -round(CAPTURE_TOP * scale)
    return (
        f'<div class="window" style="left:{left}px;top:{top}px;width:{width}px;height:{height}px;{style}">'
        f'<img src="{data_uri(CAPTURES / (capture + ".png"))}" '
        f'style="width:{image_width}px;left:{image_left}px;top:{image_top}px"></div>'
    )


def crop(capture: str, box: tuple[int, int, int, int], left: int, top: int, scale: float, radius: int, style: str = "") -> str:
    """A part of a capture lifted out of the window, like the task card on the other stores."""
    x, y, w, h = box
    return (
        f'<div class="crop" style="left:{left}px;top:{top}px;width:{round(w * scale)}px;height:{round(h * scale)}px;'
        f'border-radius:{radius}px;{style}">'
        f'<img src="{data_uri(CAPTURES / (capture + ".png"))}" '
        f'style="width:{round(1875 * scale)}px;left:{-round(x * scale)}px;top:{-round(y * scale)}px"></div>'
    )


def task_card(title: str, detail: str, color: str, ink: str, left: int, top: int, width: int, height: int, tilt: float, glow: bool = False) -> str:
    """A task as the app draws it, redrawn large enough to stay sharp."""
    ring = max(4, round(height * 0.05))
    size = round(height * 0.36)
    halo = f"0 0 0 {round(height * 0.07)}px {color}40," if glow else ""
    return (
        f'<div class="abs" style="left:{left}px;top:{top}px;width:{width}px;height:{height}px;background:{color};color:{ink};'
        f'border-radius:{round(height * 0.34)}px;transform:rotate({tilt}deg);'
        f'box-shadow:{halo}0 24px 50px rgba(22,56,120,.32);display:flex;align-items:center;padding-left:{round(height * 0.3)}px">'
        f'<div style="width:{size}px;height:{size}px;border:{ring}px solid {ink};border-radius:50%;flex:none"></div>'
        f'<div style="margin-left:{round(height * 0.3)}px"><div style="font-size:{round(height * 0.3)}px;font-weight:600;line-height:1.2">{title}</div>'
        f'<div style="font-size:{round(height * 0.2)}px;opacity:.85;line-height:1.3">{detail}</div></div></div>'
    )


def logo(left: int, top: int, size: int) -> str:
    return f'<img class="abs" src="{data_uri(LOGO)}" style="left:{left}px;top:{top}px;width:{size}px;height:{size}px">'


def page(width: int, height: int, body: str) -> str:
    return (
        f"<!doctype html><html><head><meta charset='utf-8'><style>{BASE_CSS}</style></head>"
        f"<body>{waves(width, height)}{body}</body></html>"
    )


def overview() -> str:
    return page(1920, 1080, f"""
      {logo(84, 268, 150)}
      <h1 class="abs" style="left:90px;top:436px;font-size:76px">KGS Calendar</h1>
      <div class="abs sub" style="left:92px;top:538px;font-size:40px">Plan. Organize. Achieve.</div>
      <div class="abs sub" style="left:92px;top:610px;width:500px;font-size:30px">
        Your events and tasks,<br>side by side.</div>
      {window("week-tasks", 640, 196, 1236)}
    """)


def month() -> str:
    return page(1920, 1080, f"""
      <h1 class="abs center" style="top:44px;font-size:84px">See the Big Picture.
        <span class="blue">Plan Your Month.</span></h1>
      <div class="abs center sub" style="top:150px;font-size:38px">Month and year views keep everything in focus.</div>
      {window("month", 190, 234, 1540)}
    """)


def tasks() -> str:
    return page(1920, 1080, f"""
      {window("week-tasks", -520, 96, 1640)}
      <h1 class="abs" style="left:1190px;top:296px;font-size:92px"><span class="blue">Full Task</span><br>Support.</h1>
      <div class="abs sub" style="left:1194px;top:516px;width:640px;font-size:38px">
        Seamlessly sync with <b class="blue" style="font-weight:600">CalDAV tasks</b>,
        with priorities and overdue at a glance.</div>
      {task_card("Submit expense report", "Wed, Nov 4, All day", "#dc2f23", "#fff", 1196, 716, 640, 136, -2.4, glow=True)}
    """)


def views() -> str:
    return page(1920, 1080, f"""
      <h1 class="abs center" style="top:40px;font-size:84px">View <span class="blue">Events/Tasks</span>
        the way you want.</h1>
      {window("year", 60, 238, 1140, "transform:rotate(-2.6deg)")}
      {window("agenda-tasks", 770, 318, 1100, "transform:rotate(2.2deg)")}
    """)


def appearance() -> str:
    return page(1920, 1080, f"""
      <div style="position:absolute;inset:0;perspective:2400px">
        {window("week-tasks-dark", -500, 197, 1240, "transform:rotateY(14deg);transform-origin:left center")}
        {window("week-tasks", 1180, 197, 1240, "transform:rotateY(-14deg);transform-origin:right center")}
      </div>
      <h1 class="abs center" style="top:398px;font-size:76px">
        <span class="blue">Light mode</span><br>and<br>dark mode</h1>
    """)


def desktop() -> str:
    return page(1920, 1080, f"""
      <h1 class="abs" style="left:90px;top:236px;font-size:92px">Made for<br><span class="blue">your desktop.</span></h1>
      <div class="abs sub" style="left:94px;top:462px;width:640px;font-size:36px">
        Shortcuts you can change, right-click menus, and reminders from the notification area.</div>
      <div class="abs" style="left:94px;top:690px"><span class="key">Ctrl+N</span><span class="key">T</span><span class="key">M</span><span class="key">Y</span></div>
      {crop("shortcuts", (772, 44, 1095, 972), 838, 76, 0.96, 30, "background:#edf4ff")}
    """)


def privacy() -> str:
    return page(1920, 1080, f"""
      <h1 class="abs center" style="top:44px;font-size:84px">Your calendars. <span class="blue">Your servers.</span></h1>
      <div class="abs center" style="top:164px">
        <span class="chip">Nextcloud</span><span class="chip">CalDAV</span><span class="chip">Just your PC</span>
        <span class="chip">No ads</span><span class="chip">No tracking</span><span class="chip">No account</span>
      </div>
      {window("week", 80, 290, 1420)}
      {crop("event", (537, 97, 800, 550), 1090, 400, 0.98, 34)}
    """)


def hero() -> str:
    """16:9 art for the top of the listing. Microsoft asks for it not to carry the app's name."""
    return page(1920, 1080, f"""
      {window("year", 110, 84, 1180, "transform:rotate(-3deg)")}
      {window("week-tasks", 660, 338, 1180, "transform:rotate(2.4deg)")}
      {logo(1560, 84, 220)}
    """)


def poster() -> str:
    """9:16 art, which Windows uses as the app's main logo in the Store."""
    return page(720, 1080, f"""
      {logo(250, 76, 220)}
      <h1 class="abs center" style="top:318px;font-size:80px">KGS Calendar</h1>
      <div class="abs center sub" style="top:416px;font-size:34px">Plan. Organize. Achieve.</div>
      {task_card("Submit expense report", "Wed, Nov 4, All day", "#dc2f23", "#fff", 60, 514, 600, 124, -2.2, glow=True)}
      {task_card("Plan vacation route", "Inbox", "#1b6b5d", "#fff", 44, 672, 632, 124, 1.5)}
      {task_card("Book train tickets", "Mon, Nov 9, All day", "#ffd84d", "#1b1b1b", 60, 830, 600, 124, -1.3)}
    """)


IMAGES = [
    (SCREENSHOTS / "1-overview.png", overview, 1920, 1080, 4 / 3),
    (SCREENSHOTS / "2-month.png", month, 1920, 1080, 4 / 3),
    (SCREENSHOTS / "3-tasks.png", tasks, 1920, 1080, 4 / 3),
    (SCREENSHOTS / "4-views.png", views, 1920, 1080, 4 / 3),
    (SCREENSHOTS / "5-light-dark.png", appearance, 1920, 1080, 4 / 3),
    (SCREENSHOTS / "6-desktop.png", desktop, 1920, 1080, 4 / 3),
    (SCREENSHOTS / "7-your-servers.png", privacy, 1920, 1080, 4 / 3),
    (ART / "hero-3840x2160.png", hero, 1920, 1080, 2),
    (ART / "poster-1440x2160.png", poster, 720, 1080, 2),
]


def chrome() -> str:
    found = shutil.which("chrome") or next((p for p in CHROME_PATHS if Path(p).exists()), None)
    if not found:
        raise SystemExit("Chrome or Edge is needed to draw the images")
    return found


def render(browser: str, html: str, target: Path, width: int, height: int, scale: float, work: Path) -> None:
    source = work / (target.stem + ".html")
    source.write_text(html, encoding="utf-8")
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            browser, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
            f"--user-data-dir={work / 'profile'}", f"--force-device-scale-factor={scale}",
            f"--window-size={width},{height}", "--virtual-time-budget=4000",
            f"--screenshot={target}", source.as_uri(),
        ],
        check=True, capture_output=True, timeout=120,
    )
    print(target.relative_to(ROOT))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("names", nargs="*", help="Only images whose file name contains one of these")
    names = parser.parse_args().names
    browser = chrome()
    with tempfile.TemporaryDirectory() as folder:
        for target, build, width, height, scale in IMAGES:
            if not names or any(name in target.name for name in names):
                render(browser, build(), target, width, height, scale, Path(folder))


if __name__ == "__main__":
    main()
