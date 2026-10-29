"""Essentials GG icon: a 16x16 Minecraft-style healing potion on a blurred sky.

Original pixel art (not Mojang's texture). Usage: make_icon.py OUT.png
"""
import random, sys
from PIL import Image, ImageDraw, ImageFilter

SPRITE = [
    "................",
    "......oooo......",
    ".....occcco.....",
    ".....oCCCCo.....",
    "......owGo......",
    "......owGo......",
    ".....owggGo.....",
    "....owggggGo....",
    "...owlllllllo...",
    "..owrrrrrrrrRo..",
    "..orrbrrrrrRRo..",
    "..olrrrrrbRRRo..",
    "...olrrrrRRRo...",
    "....oRRRRRRo....",
    ".....oooooo.....",
    "................",
]
COLORS = {
    'o': (34, 26, 38), 'c': (176, 124, 74), 'C': (124, 84, 48),
    'w': (255, 255, 255), 'g': (206, 232, 246), 'G': (152, 192, 216),
    'l': (255, 110, 110), 'r': (232, 40, 50), 'R': (172, 22, 40), 'b': (255, 160, 160),
}

random.seed(7)
bg = Image.new('RGB', (512, 512))
d = ImageDraw.Draw(bg)
for y in range(512):
    t = y / 511
    d.line([(0, y), (511, y)], fill=(int(116 + 96 * t), int(166 + 66 * t), int(236 + 14 * t)))
for _ in range(9):
    x, y, w = random.randint(-60, 520), random.randint(10, 300), random.randint(90, 200)
    d.ellipse([x, y, x + w, y + w * 0.45], fill=(238, 244, 252))
d.ellipse([-200, 380, 330, 720], fill=(86, 140, 70))
d.ellipse([180, 400, 760, 760], fill=(70, 122, 58))
bg = bg.filter(ImageFilter.GaussianBlur(14)).convert('RGBA')

sprite = Image.new('RGBA', (16, 16))
for y, row in enumerate(SPRITE):
    for x, ch in enumerate(row):
        if ch != '.':
            sprite.putpixel((x, y), COLORS[ch] + (255,))
bg.alpha_composite(sprite.resize((512, 512), Image.NEAREST))
bg.convert('RGB').save(sys.argv[1], optimize=True)
