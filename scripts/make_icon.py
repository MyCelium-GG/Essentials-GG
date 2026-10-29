"""Essentials GG icon: pixel-art round-bottom flask, faceted coral liquid, blurred sky."""
import math, random, sys
from PIL import Image, ImageDraw, ImageFilter

N, SCALE = 32, 16
OUT = sys.argv[1]

# background: blurred Minecraft-ish sky, clouds, grass hills
random.seed(7)
bg = Image.new('RGB', (512, 512))
d = ImageDraw.Draw(bg)
for y in range(512):
    t = y / 511
    d.line([(0, y), (511, y)], fill=(int(120 + 90 * t), int(170 + 60 * t), int(235 + 15 * t)))
for _ in range(9):
    x, y, w = random.randint(-60, 520), random.randint(10, 300), random.randint(90, 200)
    d.ellipse([x, y, x + w, y + w * 0.45], fill=(238, 244, 252))
d.ellipse([-200, 380, 330, 720], fill=(86, 140, 70))
d.ellipse([180, 400, 760, 760], fill=(70, 122, 58))
bg = bg.filter(ImageFilter.GaussianBlur(14))

OUTLINE = (24, 20, 26, 255)
GLASS = (214, 236, 246, 255); GLASS_D = (170, 204, 222, 255)
HI = (255, 255, 255, 255)
LIQ_L = (255, 112, 112, 255); LIQ = (232, 72, 84, 255); LIQ_D = (190, 48, 66, 255)
BUB = (255, 170, 165, 255)
CORK = (176, 120, 70, 255); CORK_D = (128, 82, 46, 255); CORK_L = (210, 156, 98, 255)
GOLD = (255, 214, 72, 255); GOLD_L = (255, 246, 190, 255)

px = [[None] * N for _ in range(N)]
cx, cy, r = 15.5, 19.5, 9.2
neck = range(13, 19)
def inside(x, y):
    return math.hypot(x - cx, y - cy) <= r or (x in neck and 6 <= y <= 12)
for y in range(N):
    for x in range(N):
        if inside(x, y):
            px[y][x] = GLASS
surface = 16
for y in range(N):
    for x in range(N):
        if px[y][x] and y >= surface and math.hypot(x - cx, y - cy) <= r:
            # two facets split by diagonals meeting at the bottom centre
            a = (x - cx) * 0.55 + (y - surface)
            b = -(x - cx) * 0.55 + (y - surface)
            px[y][x] = LIQ_L if a < 4 and b < 4 else (LIQ if x < cx else LIQ_D)
for x in range(N):
    if px[surface][x]:
        px[surface][x] = LIQ_L
# glass shading on the right, highlight on the left
for y in range(N):
    for x in range(N):
        if px[y][x] == GLASS and x >= 19 and y >= 11:
            px[y][x] = GLASS_D
for (x, y) in [(9, 14), (8, 15), (8, 16), (14, 7), (14, 8), (14, 9), (14, 10)]:
    px[y][x] = HI
for (x, y) in [(12, 20), (18, 22), (15, 24), (19, 19), (11, 23)]:
    px[y][x] = BUB
# lip and cork
for x in range(12, 20):
    px[6][x] = GLASS_D
for y in range(2, 6):
    for x in range(13, 19):
        px[y][x] = CORK_L if y == 2 else (CORK_D if x == 18 else CORK)
# outline every filled pixel's empty 4-neighbours
art = [row[:] for row in px]
for y in range(N):
    for x in range(N):
        if px[y][x] is None and any(
                0 <= x + dx < N and 0 <= y + dy < N and px[y + dy][x + dx]
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            art[y][x] = OUTLINE
# sparkle top-right
for (x, y, c) in [(26, 3, GOLD), (26, 4, GOLD), (26, 5, GOLD_L), (26, 6, GOLD), (26, 7, GOLD),
                  (24, 5, GOLD), (25, 5, GOLD), (27, 5, GOLD), (28, 5, GOLD),
                  (6, 9, GOLD), (5, 10, GOLD), (6, 10, GOLD_L), (7, 10, GOLD), (6, 11, GOLD)]:
    art[y][x] = c

img = Image.new('RGBA', (N, N))
for y in range(N):
    for x in range(N):
        if art[y][x]:
            img.putpixel((x, y), art[y][x])
img = img.resize((N * SCALE, N * SCALE), Image.NEAREST)
out = bg.convert('RGBA')
out.alpha_composite(img)
out.convert('RGB').save(OUT, optimize=True)
