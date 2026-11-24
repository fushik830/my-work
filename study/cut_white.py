from PIL import Image, ImageFilter
import numpy as np
from collections import deque
import os

SRC = "study/图"
OUT = "study/cutouts"
os.makedirs(OUT, exist_ok=True)

def cut_white(name, white_min=225, sat_max=28):
    im = Image.open(os.path.join(SRC, name)).convert("RGB")
    a = np.asarray(im).astype(np.int16)
    h, w, _ = a.shape
    mx = a.max(2)
    mn = a.min(2)
    cand = (mn >= white_min) & ((mx - mn) <= sat_max)
    bg = np.zeros((h, w), dtype=bool)
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            if cand[y, x] and not bg[y, x]:
                bg[y, x] = True
                q.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if cand[y, x] and not bg[y, x]:
                bg[y, x] = True
                q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and cand[ny, nx] and not bg[ny, nx]:
                bg[ny, nx] = True
                q.append((ny, nx))
    alpha = np.where(bg, 0, 255).astype(np.uint8)
    alpha_img = Image.fromarray(alpha, "L").filter(ImageFilter.GaussianBlur(0.7))
    rgba = im.convert("RGBA")
    rgba.putalpha(alpha_img)
    bbox = rgba.getbbox()
    if bbox:
        pad = 8
        bbox = (max(0, bbox[0] - pad), max(0, bbox[1] - pad),
                min(w, bbox[2] + pad), min(h, bbox[3] + pad))
        rgba = rgba.crop(bbox)
    # 统一到 512 以内，保持比例
    rgba.thumbnail((512, 512), Image.LANCZOS)
    out = os.path.join(OUT, name.replace(".jpg", ".png"))
    rgba.save(out)
    print(out, rgba.size)

for n in ["pet_normal.jpg", "pet_hungry.jpg", "pet_dead.jpg", "pet_thinking.jpg", "pet_angry.jpg"]:
    cut_white(n)
