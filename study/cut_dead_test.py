from PIL import Image, ImageFilter
import numpy as np
from collections import deque

SRC = "study/图/pet_dead.jpg"

def cut(th, sat=40):
    im = Image.open(SRC).convert("RGB")
    a = np.asarray(im).astype(np.int16)
    h, w, _ = a.shape
    mx = a.max(2); mn = a.min(2)
    cand = (mn >= th) & ((mx - mn) <= sat)
    bg = np.zeros((h, w), dtype=bool); q = deque()
    for x in range(w):
        for y in (0, h - 1):
            if cand[y, x] and not bg[y, x]:
                bg[y, x] = True; q.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if cand[y, x] and not bg[y, x]:
                bg[y, x] = True; q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
            ny, nx = y+dy, x+dx
            if 0 <= ny < h and 0 <= nx < w and cand[ny, nx] and not bg[ny, nx]:
                bg[ny, nx] = True; q.append((ny, nx))
    alpha = np.where(bg, 0, 255).astype(np.uint8)
    alpha = Image.fromarray(alpha, "L").filter(ImageFilter.GaussianBlur(0.6))
    rgba = im.convert("RGBA"); rgba.putalpha(alpha)
    bbox = rgba.getbbox()
    if bbox:
        pad = 4
        bbox = (max(0,bbox[0]-pad), max(0,bbox[1]-pad), min(w,bbox[2]+pad), min(h,bbox[3]+pad))
        rgba = rgba.crop(bbox)
    return rgba

canvas = Image.new("RGB", (900, 260), (11, 32, 51))
for i, th in enumerate([180, 190, 200, 210, 220]):
    im = cut(th)
    im = im.resize((160, int(im.height*160/im.width)), Image.LANCZOS)
    canvas.paste(im, (10+i*180, 30), im)
    print(th, im.size)
canvas.save("study/cutouts/dead_thresholds.png")
