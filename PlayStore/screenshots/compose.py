#!/usr/bin/env python3
"""Compose Google Play marketing screenshots from raw emulator captures.

usage: compose.py [device ...]   (phone, tablet, feature; default: all)

"feature" renders every slide as a 1024x500 landscape banner — the size of
Play's feature graphic — with the headline beside the phone capture, plus
00_feature.png (icon, name and tagline) meant for the feature-graphic slot.

Same slides and layout as the iOS App Store set
(~/Workspace/iOS/ReaderApp/AppStore/screenshots/compose.py). Canvas sizes
follow Google Play's rules: each side 320–3840 px and the long side at most
twice the short one.
"""
import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

S = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(S, "raw")
ONLY = sys.argv[1:]
FONT = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
BOLD, REG = 6, 0

# (shot, headline_ko, sub_ko, headline_en, sub_en, bg, fg, subfg)
SLIDES = [
    ("library",
     "내 책은 내 기기에서", "EPUB · PDF · TXT · Markdown, 실제 표지 그대로",
     "Your books, your way", "EPUB · PDF · TXT · Markdown with real covers",
     "#1C2433", "#FFFFFF", "#B7C2D6"),
    ("reader",
     "원본 그대로 읽는다", "내장 서체와 삽화까지 제작자가 의도한 모습으로",
     "Read it as it was made", "Embedded fonts and illustrations, exactly as designed",
     "#F3ECD8", "#2B2118", "#6E5B48"),
    ("appearance",
     "내 눈에 맞게", "여섯 가지 테마, 글꼴·크기·줄 간격까지 자유롭게",
     "Make it yours", "Six themes, plus font, size and line spacing",
     "#2E3D2F", "#FFFFFF", "#BFD3BE"),
    ("tts",
     "읽지 말고 들으세요", "한국어·영어 음성, 속도 조절과 취침 타이머",
     "Listen instead", "Korean and English voices, speed control, sleep timer",
     "#F7E9EC", "#3A1F27", "#7C5560"),
    ("highlight_menu",
     "밑줄 긋듯 하이라이트", "네 가지 색으로 표시하고 목록에서 바로 이동",
     "Highlight what matters", "Four colors, and jump back from the list any time",
     "#FFF4D6", "#3A2E12", "#7A6534"),
    ("bookmarks",
     "읽던 자리를 놓치지 않게", "북마크와 목차, 읽던 위치는 자동으로 저장",
     "Never lose your place", "Bookmarks, contents, and automatic resume",
     "#12203A", "#FFFFFF", "#AFC2E8"),
    ("korean_dark",
     "한글도 완벽하게", "EUC-KR·CP949 텍스트 자동 인식, 눈 편한 다크 모드",
     "Korean, done right", "Legacy encodings detected, easy-on-the-eyes dark mode",
     "#1A1A1E", "#FFFFFF", "#A9A9B4"),
    ("cloud",
     "클라우드에서 바로 가져오기", "Dropbox · Google Drive · OneDrive, 읽기 전용 권한만",
     "Straight from the cloud", "Dropbox · Google Drive · OneDrive, read-only access",
     "#E8F0FB", "#14243D", "#4F6584"),
    ("stats",
     "읽은 만큼 쌓이는 기록", "총 독서 시간과 연속으로 읽은 날",
     "Track your reading", "Total time read and your day streak",
     "#EAF7EF", "#12301E", "#3F6B51"),
]

DEVICES = {
    # canvas size, screenshot width, top of screenshot, headline size, sub size,
    # bezel, corner radius, headline top
    "phone":  dict(canvas=(1440, 2880), shot_w=1160, shot_top=450, h1=100, h2=48,
                   bezel=24, radius=96, text_top=140),
    "tablet": dict(canvas=(1600, 2560), shot_w=1340, shot_top=470, h1=100, h2=48,
                   bezel=26, radius=64, text_top=140),
}


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius=radius, fill=255)
    return m


def fit_text(draw, text, index, size, max_w):
    while size > 20:
        f = ImageFont.truetype(FONT, size, index=index)
        if draw.textlength(text, font=f) <= max_w:
            return f
        size -= 2
    return ImageFont.truetype(FONT, size, index=index)


def compose(device, lang, slide, n):
    d = DEVICES[device]
    shot, h_ko, s_ko, h_en, s_en, bg, fg, subfg = slide
    head, sub = (h_ko, s_ko) if lang == "ko" else (h_en, s_en)
    W, H = d["canvas"]
    canvas = Image.new("RGB", (W, H), bg)
    draw = ImageDraw.Draw(canvas)

    # Headline + subtitle, centered
    f1 = fit_text(draw, head, BOLD, d["h1"], W - 160)
    f2 = fit_text(draw, sub, REG, d["h2"], W - 160)
    y = d["text_top"]
    draw.text(((W - draw.textlength(head, font=f1)) / 2, y), head, font=f1, fill=fg)
    y += int(d["h1"] * 1.35)
    draw.text(((W - draw.textlength(sub, font=f2)) / 2, y), sub, font=f2, fill=subfg)

    # Screenshot with bezel, rounded corners and soft shadow; it runs off the
    # bottom edge of the canvas.
    src = Image.open(os.path.join(SHOTS, f"{device}_{shot}.png")).convert("RGB")
    sw = d["shot_w"]
    sh = int(src.height * sw / src.width)
    src = src.resize((sw, sh), Image.LANCZOS)
    b, r = d["bezel"], d["radius"]
    frame = Image.new("RGB", (sw + 2 * b, sh + 2 * b), "#0B0B0D")
    frame.paste(src, (b, b), rounded_mask(src.size, r - b))
    frame_mask = rounded_mask(frame.size, r)
    x = (W - frame.width) // 2
    y = d["shot_top"]

    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    layer = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    layer.paste((0, 0, 0, 110), (0, 0), frame_mask)
    shadow.paste(layer, (x, y + 30), layer)
    shadow = shadow.filter(ImageFilter.GaussianBlur(40))
    canvas = Image.alpha_composite(canvas.convert("RGBA"), shadow).convert("RGB")
    canvas.paste(frame, (x, y), frame_mask)

    out_dir = os.path.join(S, lang, device)
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, f"{n:02d}_{shot}.png")
    canvas.save(path, optimize=True)
    return path


FEATURE = (1024, 500)
ICON = os.path.join(os.path.dirname(S), "icon_512.png")
TAGLINE = {"ko": "원본 그대로 읽고, 음성으로 듣는 이북 리더", "en": "Read your books your way — or listen"}


def wrap(draw, text, font, max_w):
    lines, cur = [], ""
    for word in text.split(" "):
        trial = (cur + " " + word).strip()
        if cur and draw.textlength(trial, font=font) > max_w:
            lines.append(cur)
            cur = word
        else:
            cur = trial
    lines.append(cur)
    return lines


def phone_on(canvas, shot, right=70, top=46, width=300):
    """Pastes the framed phone capture at the right edge, running off the bottom."""
    W, H = canvas.size
    src = Image.open(os.path.join(SHOTS, f"phone_{shot}.png")).convert("RGB")
    sh = int(src.height * width / src.width)
    src = src.resize((width, sh), Image.LANCZOS)
    b, r = 7, 30
    frame = Image.new("RGB", (width + 2 * b, sh + 2 * b), "#0B0B0D")
    frame.paste(src, (b, b), rounded_mask(src.size, r - b))
    mask = rounded_mask(frame.size, r)
    x = W - right - frame.width
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    layer = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    layer.paste((0, 0, 0, 110), (0, 0), mask)
    shadow.paste(layer, (x, top + 12), layer)
    shadow = shadow.filter(ImageFilter.GaussianBlur(16))
    canvas = Image.alpha_composite(canvas.convert("RGBA"), shadow).convert("RGB")
    canvas.paste(frame, (x, top), mask)
    return canvas, x


def compose_feature(lang, slide, n):
    shot, h_ko, s_ko, h_en, s_en, bg, fg, subfg = slide
    head, sub = (h_ko, s_ko) if lang == "ko" else (h_en, s_en)
    canvas, x = phone_on(Image.new("RGB", FEATURE, bg), shot)
    draw = ImageDraw.Draw(canvas)
    left, max_w = 64, x - 64 - 48
    f1 = fit_text(draw, head, BOLD, 60, max_w)
    f2 = ImageFont.truetype(FONT, 25, index=REG)
    lines = wrap(draw, sub, f2, max_w)
    gap, lh = 22, 36
    y = (FEATURE[1] - (f1.size + gap + lh * len(lines))) // 2 - 6
    draw.text((left, y), head, font=f1, fill=fg)
    y += f1.size + gap
    for line in lines:
        draw.text((left, y), line, font=f2, fill=subfg)
        y += lh
    return save_feature(canvas, lang, f"{n:02d}_{shot}.png")


def compose_feature_cover(lang):
    """Icon, app name and tagline beside the library — for the feature-graphic slot."""
    bg, fg, subfg = "#1C2433", "#FFFFFF", "#B7C2D6"
    canvas, x = phone_on(Image.new("RGB", FEATURE, bg), "library")
    draw = ImageDraw.Draw(canvas)
    left, max_w = 64, x - 64 - 48
    icon = Image.open(ICON).convert("RGB").resize((104, 104), Image.LANCZOS)
    f1 = ImageFont.truetype(FONT, 76, index=BOLD)
    f2 = ImageFont.truetype(FONT, 27, index=REG)
    lines = wrap(draw, TAGLINE[lang], f2, max_w)
    y = (FEATURE[1] - (104 + 26 + 76 + 22 + 38 * len(lines))) // 2 - 4
    canvas.paste(icon, (left, y), rounded_mask(icon.size, 24))
    y += 104 + 26
    draw.text((left, y), "Booklip", font=f1, fill=fg)
    y += 76 + 22
    for line in lines:
        draw.text((left, y), line, font=f2, fill=subfg)
        y += 38
    return save_feature(canvas, lang, "00_feature.png")


def save_feature(canvas, lang, name):
    out_dir = os.path.join(S, lang, "feature")
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, name)
    # Play wants a 24-bit image with no alpha channel here.
    canvas.convert("RGB").save(path, optimize=True)
    return path


for device in DEVICES:
    if ONLY and device not in ONLY:
        continue
    for lang in ("ko", "en"):
        for i, slide in enumerate(SLIDES, 1):
            print(os.path.relpath(compose(device, lang, slide, i), S))

if not ONLY or "feature" in ONLY:
    for lang in ("ko", "en"):
        print(os.path.relpath(compose_feature_cover(lang), S))
        for i, slide in enumerate(SLIDES, 1):
            print(os.path.relpath(compose_feature(lang, slide, i), S))
