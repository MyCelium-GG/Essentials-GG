"""Essentials GG icon: a blurred Minecraft-style sky with clouds and hills.

Usage: make_icon.py OUT.png
"""
import random, sys
from PIL import Image, ImageDraw, ImageFilter

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
bg = bg.filter(ImageFilter.GaussianBlur(14))

bg.save(sys.argv[1], optimize=True)
