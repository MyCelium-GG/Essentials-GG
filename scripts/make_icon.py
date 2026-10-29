"""Essentials GG icon: a 16x16 Minecraft-style healing potion on a blurred sky.

Original pixel art in the vanilla style (not Mojang's texture). Usage: make_icon.py OUT.png
"""
import random, sys
from PIL import Image, ImageDraw, ImageFilter

SPRITE = [
    "................",
    "................",
    "......ccck......",
    ".....gCCCkg.....",
    "......g..m......",
    "......g..m......",
    ".....g....m.....",
    "....gwRRRRDm....",
    "...gwBRRRRRDm...",
    "...gBBRRRRDDm...",
    "...gBRRRRRwDm...",
    "...mRRRRRDDXm...",
    "....mDRRDDXm....",
    ".....mmmmmm.....",
    "................",
    "................",
]
COLORS = {
    'c': (214, 116, 70), 'C': (196, 98, 58), 'k': (150, 66, 40),
    'g': (196, 224, 242), 'w': (232, 244, 252), 'm': (104, 152, 204),
    'B': (255, 44, 44), 'R': (204, 26, 38), 'D': (156, 16, 30), 'X': (106, 10, 22),
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
big = sprite.resize((512, 512), Image.NEAREST)
# soft dark halo so the pale glass edge stands out against the sky
halo = Image.new('RGBA', (512, 512), (20, 24, 40, 0))
halo.putalpha(big.getchannel('A').point(lambda a: 150 if a else 0))
bg.alpha_composite(halo.filter(ImageFilter.GaussianBlur(18)))
bg.alpha_composite(big)
bg.convert('RGB').save(sys.argv[1], optimize=True)
