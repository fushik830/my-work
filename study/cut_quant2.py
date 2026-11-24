from PIL import Image, ImageFilter, ImageDraw
import numpy as np
from collections import deque

im = Image.open("study/图/pet_waiting.jpg").convert("RGB")
w, h = im.size
q = im.quantize(colors=24, method=Image.Quantize.MEDIANCUT)
pal = q.getpalette()
idx = np.asarray(q)
band = max(4, min(w, h) // 20)
border = np.zeros((h, w), dtype=bool)
border[:band, :] = border[-band:, :] = True
border[:, :band] = border[:, -band:] = True
counts = np.bincount(idx[border].ravel(), minlength=256)
totals = np.bincount(idx.ravel(), minlength=256)
bg_ids = set()
for i in range(256):
    if totals[i] == 0:
        continue
    r, g, b = pal[i*3:i*3+3]
    ratio = counts[i] / max(1, border.sum())
    # 房间是暖色（R 明显高于 B）；蓝发、深蓝裙子、白色围裙都保留
    if ratio > 0.012 and r > b + 8:
        bg_ids.add(i)
        print("bg", i, (r, g, b), round(ratio, 3))
fg = ~np.isin(idx, list(bg_ids))
# 保护脸部/皮肤中心，避免腮红和肤色被误删
protect = Image.new("L", (w, h), 0)
d = ImageDraw.Draw(protect)
d.ellipse((75, 75, 228, 250), fill=255)
protect = np.asarray(protect) > 0
fg |= protect
# 只保留最大连通域
seen = np.zeros((h, w), dtype=bool)
best = []
for yy in range(h):
    for xx in range(w):
        if fg[yy, xx] and not seen[yy, xx]:
            comp = []
            dq = deque([(yy, xx)])
            seen[yy, xx] = True
            while dq:
                y, x = dq.popleft()
                comp.append((y, x))
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < h and 0 <= nx < w and fg[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        dq.append((ny, nx))
            if len(comp) > len(best):
                best = comp
fg = np.zeros((h, w), dtype=bool)
for y, x in best:
    fg[y, x] = True
# 脸部保护要在最大连通域之后再加一次，避免脸被当成独立小块丢掉
fg |= protect
alpha = Image.fromarray(np.where(fg, 255, 0).astype(np.uint8), "L").filter(ImageFilter.GaussianBlur(1.0))
rgba = im.convert("RGBA")
rgba.putalpha(alpha)
bbox = rgba.getbbox()
if bbox:
    pad = 8
    bbox = (max(0, bbox[0]-pad), max(0, bbox[1]-pad), min(w, bbox[2]+pad), min(h, bbox[3]+pad))
    rgba = rgba.crop(bbox)
rgba.thumbnail((512, 512), Image.LANCZOS)
rgba.save("study/cutouts/wait_warm.png")
print("out", rgba.size, "fg", int(fg.sum()), "bgids", len(bg_ids))
