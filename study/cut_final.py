from PIL import Image, ImageFilter
import numpy as np
import os

OUT_DIR = "study/Stusy/app/src/main/res/drawable"
CANVAS = 512
MAX = 480
MAP = {
    "pet_normal": "study/cutouts/pet_normal.png",
    "pet_hungry": "study/cutouts/pet_hungry.png",
    "pet_dead": "study/cutouts/pet_dead.png",
    "pet_thinking": "study/cutouts/pet_thinking.png",
    "pet_waiting": "study/cutouts/wait_warm.png",
}

for name, src in MAP.items():
    im = Image.open(src).convert("RGBA")
    # 统一收 1px 边缘，去掉抠图后残留的白边/毛边
    a = im.getchannel("A").filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.45))
    im.putalpha(a)
    alpha = im.getchannel("A")
    bbox = alpha.getbbox()
    if bbox:
        im = im.crop(bbox)
    # 等比例缩放到 480 内（小图也放大，保证各状态人物大小一致），再放到 512 透明画布中央
    scale = min(MAX / im.width, MAX / im.height)
    im = im.resize((max(1, round(im.width * scale)), max(1, round(im.height * scale))), Image.LANCZOS)
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    x = (CANVAS - im.width) // 2
    y = (CANVAS - im.height) // 2
    canvas.alpha_composite(im, (x, y))
    out = os.path.join(OUT_DIR, name + ".png")
    canvas.save(out)
    print(out, im.size)
