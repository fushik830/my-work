from PIL import Image, ImageFilter
import numpy as np
from collections import deque
import os

SRC = "study/图/pet_waiting.jpg"
OUT = "study/cutouts/wait_quant.png"

im = Image.open(SRC).convert("RGB")
w, h = im.size
q = im.quantize(colors=20, method=Image.Quantize.MEDIANCUT)
pal = q.getpalette()
idx = np.asarray(q)
# border band
band = max(4, min(w, h) // 20)
border = np.zeros((h, w), dtype=bool)
border[:band, :] = True
border[-band:, :] = True
border[:, :band] = True
border[:, -band:] = True
counts = np.bincount(idx[border].ravel(), minlength=256)
totals = np.bincount(idx.ravel(), minlength=256)
# 背景色：外圈出现次数 >= 外圈像素的 1.2%，并且不是极少
bg_ids = set()
for i in range(256):
    if totals[i] == 0: continue
    ratio = counts[i] / max(1, border.sum())
    if ratio > 0.012:
        bg_ids.add(i)
        c = tuple(pal[i*3:i*3+3])
        print(i, c, totals[i], round(ratio, 3))
fg = ~np.isin(idx, list(bg_ids))
# 去掉碎小前景，保留最大连通域
seen = np.zeros((h, w), dtype=bool)
best = []
for yy in range(h):
    for xx in range(w):
        if fg[yy, xx] and not seen[yy, xx]:
            comp=[]; dq=deque([(yy,xx)]); seen[yy,xx]=True
            while dq:
                y,x=dq.popleft(); comp.append((y,x))
                for dy,dx in ((1,0),(-1,0),(0,1),(0,-1)):
                    ny,nx=y+dy,x+dx
                    if 0<=ny<h and 0<=nx<w and fg[ny,nx] and not seen[ny,nx]:
                        seen[ny,nx]=True; dq.append((ny,nx))
            if len(comp)>len(best): best=comp
fg = np.zeros((h,w),dtype=bool)
for y,x in best: fg[y,x]=True
alpha = Image.fromarray(np.where(fg,255,0).astype(np.uint8), 'L').filter(ImageFilter.GaussianBlur(1.2))
rgba = im.convert('RGBA'); rgba.putalpha(alpha)
bbox=rgba.getbbox()
if bbox:
    pad=8; bbox=(max(0,bbox[0]-pad),max(0,bbox[1]-pad),min(w,bbox[2]+pad),min(h,bbox[3]+pad))
    rgba=rgba.crop(bbox)
rgba.save(OUT)
print(OUT, rgba.size, 'fg', int(fg.sum()), 'bgids', len(bg_ids))
