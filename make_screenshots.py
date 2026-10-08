from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import math

ROOT = Path(__file__).parent
ASSETS = ROOT / "assets"
ASSETS.mkdir(exist_ok=True)

BG = "#10151d"
BAR = "#1b2430"
TEXT = "#e8edf2"
MUTED = "#9fb0c3"
ACCENT = "#69b7ff"
GREEN = "#80d49b"


def font(size, bold=False):
    candidates = [
        "/System/Library/Fonts/SFNSMono.ttf",
        "/System/Library/Fonts/Supplemental/Courier New Bold.ttf" if bold else
        "/System/Library/Fonts/Supplemental/Courier New.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
    ]
    for candidate in candidates:
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            pass
    return ImageFont.load_default()


def render_text(title, subtitle, lines, output, line_numbers=False):
    image = Image.new("RGB", (1600, 1000), BG)
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 1600, 92), fill=BAR)
    for x, color in [(34, "#ff5f57"), (70, "#febc2e"), (106, "#28c840")]:
        draw.ellipse((x, 32, x + 24, 56), fill=color)
    draw.text((155, 25), title, font=font(31, True), fill=TEXT)
    draw.text((155, 58), subtitle, font=font(20), fill=MUTED)

    code_font = font(24)
    y = 120
    for index, raw in enumerate(lines, 1):
        if y > 958:
            break
        raw = raw.rstrip("\n").replace("\t", "    ")
        if line_numbers:
            prefix = f"{index:>3}  "
            draw.text((30, y), prefix, font=code_font, fill=MUTED)
            x = 115
        else:
            x = 38
        color = TEXT
        stripped = raw.strip()
        if stripped.startswith("//") or stripped.startswith("/**") or stripped.startswith("*"):
            color = GREEN
        elif "ERROR" in raw or "error on task" in raw:
            color = "#ff8a80"
        elif "Summary:" in raw or "Saved " in raw:
            color = ACCENT
        draw.text((x, y), raw[:100], font=code_font, fill=color)
        y += 31
    image.save(output, quality=95)


java_lines = (ROOT / "java/src/dataprocessing/TaskQueue.java").read_text().splitlines()[8:48]
java_lines += ["", "// Worker pool and graceful termination"]
java_lines += (ROOT / "java/src/dataprocessing/DataProcessingSystem.java").read_text().splitlines()[26:45]
render_text("Java implementation", "TaskQueue.java and worker pool excerpt", java_lines,
            ASSETS / "java-code.png", True)

go_lines = (ROOT / "go/main.go").read_text().splitlines()[28:66]
go_lines += ["", "// Channel close and WaitGroup establish graceful completion"]
go_lines += (ROOT / "go/main.go").read_text().splitlines()[112:132]
render_text("Go implementation", "Channel queue and synchronized results excerpt", go_lines,
            ASSETS / "go-code.png", True)

render_text("Java sample output", "java/run.sh", (ASSETS / "java-output.txt").read_text().splitlines(),
            ASSETS / "java-output.png")
render_text("Go sample output", "go run .", (ASSETS / "go-output.txt").read_text().splitlines(),
            ASSETS / "go-output.png")


def make_contact_sheet(render_dir):
    pages = sorted(render_dir.glob("page-*.png"), key=lambda p: int(p.stem.split("-")[1]))
    tiles = []
    for index, page in enumerate(pages, 1):
        image = Image.open(page).convert("RGB")
        image.thumbnail((306, 396))
        tile = Image.new("RGB", (326, 436), "white")
        tile.paste(image, ((326 - image.width) // 2, 25))
        ImageDraw.Draw(tile).text((8, 5), str(index), font=font(16, True), fill="black")
        tiles.append(tile)
    if tiles:
        sheet = Image.new("RGB", (326 * 4, 436 * math.ceil(len(tiles) / 4)), "#d2d2d2")
        for index, tile in enumerate(tiles):
            sheet.paste(tile, ((index % 4) * 326, (index // 4) * 436))
        sheet.save(render_dir / "contact-sheet.png")


make_contact_sheet(ROOT / "qa-render")
