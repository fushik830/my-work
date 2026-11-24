from PIL import Image, ImageFilter
import numpy as np
from collections import deque
import os

SRC = "study/图"
OUT = "study/cutouts"

def edge_cut(name, edge_th=36, keep_largest=True):
    im = Image.open(os.path.join(SRC, name)).convert("RGB")
    w, h = im.size
    gray = im.convert("L")
    edges = np.asarray(gray.filter(ImageFilter.FIND_EDGES)).astype(np.int16)
    # 同时把非常暗的线也当成边界，避免线条被穿透
    lum = np.asarray(gray).astype(np.int16)
    barrier = (edges > edge_th) | (lum < 45)
    # 边缘膨胀 1 像素，堵住 JPEG 小缺口
    b = barrier.copy()
    b[1:, :] |= barrier[:-1, :]
    b[:-1, :] |= barrier[1:, :]
    b[:, 1:] |= barrier[:, :-1]
    b[:, :-1] |= barrier[:, 1:]
    # FIND_EDGES 会在图片最外圈产生假边缘，种子从最外圈强行进入
    b[:2, :] = False
    b[-2:, :] = False
    b[:, :2] = False
    b[:, -2:] = False
    bg = np.zeros((h, w), dtype=bool)
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            if not b[y, x] and not bg[y, x]:
                bg[y, x] = True
                q.append((y, x))
    for y in range(h):
        for x in (0, w - 1):
            if not b[y, x] and not bg[y, x]:
                bg[y, x] = True
                q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < h and 0 <= nx < w and not b[ny, nx] and not bg[ny, nx]:
                bg[ny, nx] = True
                q.append((ny, nx))
    fg = ~bg
    if keep_largest:
        # 连通域，保留最大前景块
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
    alpha = np.where(fg, 255, 0).astype(np.uint8)
    alpha = Image.fromarray(alpha, "L").filter(ImageFilter.GaussianBlur(1.0))
    rgba = im.convert("RGBA")
    rgba.putalpha(alpha)
    bbox = rgba.getbbox()
    if bbox:
        pad = 10
        bbox = (max(0, bbox[0] - pad), max(0, bbox[1] - pad),
                min(w, bbox[2] + pad), min(h, bbox[3] + pad))
        rgba = rgba.crop(bbox)
    rgba.thumbnail((512, 512), Image.LANCZOS)
    out = os.path.join(OUT, "wait_edge.png")
    rgba.save(out)
    print(out, rgba.size, "fg px", int(fg.sum()))

edge_cut("pet_waiting.jpg")
