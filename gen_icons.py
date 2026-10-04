"""GZMTU View 品牌图标生成：SVG 母版 → 各密度 PNG（方形启动器图标 + 登录页横幅）。

流程：Edge headless 光栅化 `UI/icon/*.svg`（透明底需 --default-background-color=00000000）
→ 预乘 alpha → **无负瓣**面积缩放 → 反预乘 + 母版色域钳制 → 写入 res 各密度目录。

★ 为什么不用 LANCZOS：母版只有三色 #3a8dcc / #b8e2f5 / #fff，且恒满足 R<=G<=B。
LANCZOS 的负瓣在高对比字标边缘做过冲，蓝↔白处 G 通道最先穿到 0，反预乘后得到
「蓝 + 无绿」= 品红/紫，即真机上的紫边与异色边（实测旧产物 mdpi 前景有 12.9%
像素 R>G，而白底不透明的 ic_launcher 为 0%）。BOX 面积核权重全为正，预乘空间里
的正权平均必然是母版三色的凸组合 ⇒ 结构上不可能产生越界色。
产物映射见 README「图标与横幅」一节，勿手改 res 内 PNG。
"""
import os
import shutil
import subprocess
import sys
import time

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "UI", "icon")
RES = os.path.join(ROOT, "app", "src", "main", "res")
TMP = os.path.join(ROOT, ".tmp_icon")


def _find_edge():
    """Edge 可执行文件自动探测：环境变量优先，再试两套安装目录的常见路径。"""
    env = os.environ.get("GMUVIEW_EDGE")
    cands = ([env] if env else []) + [
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    ]
    for c in cands:
        if c and os.path.exists(c):
            return c
    sys.exit("找不到 Microsoft Edge（SVG 光栅化依赖它）。"
             "请安装 Edge，或设置环境变量 GMUVIEW_EDGE 指向 msedge.exe。")


EDGE = _find_edge()

# 母版：ASCII 键 → 规格。视口取 viewBox 的整数倍（2px/单位），且比例必须与母版一致
# ——Edge 按 `meet` 把 SVG 塞进视口，视口比例不符会留出透明边。
# recolor 只改光栅化用的临时副本，不动 UI/icon 下的设计母版。
MASTERS = {
    "full": {"svg": "icon.svg", "vp": (2048, 2048)},                 # 标准整图（纯白底）
    "fg": {"svg": "icon无背景.svg", "vp": (2048, 2048)},              # 透明底前景字标
    "bg": {"svg": "icon纯背景.svg", "vp": (2048, 2048)},              # 纯色背景
    # 横幅 viewBox 1240.36×547.25。浅色版的手写体「view」原本是 #b8e2f5，
    # 在浅色页面基色 LightPageBase(#E9ECF2) 上只有 1.16:1，等于看不见
    # （深色版同一色在 DarkSurface 上有 13.3:1，无需处理）。品牌三色里唯一能
    # 在浅底站住的只有 #3a8dcc（3.03:1），所以浅色版压成单色蓝。
    "banner_light": {"svg": "登录页横幅浅色模式.svg", "vp": (2480, 1094),
                     "recolor": {"#b8e2f5": "#3a8dcc"}},
    "banner_dark": {"svg": "登录页横幅深色模式.svg", "vp": (2480, 1094)},
}

# 母版三色任意凸组合的通道差上限（蓝 #3a8dcc 最宽：B-G=63、G-R=83）
MAX_B_MINUS_G = 63
MAX_G_MINUS_R = 83

# 密度 → (legacy launcher px, adaptive 108dp px)
DENSITY = {
    "mdpi": (48, 108),
    "hdpi": (72, 162),
    "xhdpi": (96, 216),
    "xxhdpi": (144, 324),
    "xxxhdpi": (192, 432),
}

# 横幅母版 viewBox（宽×高）——产物高度由它算出，别手抄比例
BANNER_VIEW = (1240.36, 547.25)
# 位图宽度取 640：登录页版式限宽 130dp × 最高密度 4 ≈ 520px，留 1.2 倍余量。
# nodpi 只定「1px = 1dp」的标称尺寸，Compose 仍按布局缩放，所以位图只需够锐，不必等于画布。
# （曾用 1240×547：两张 69KB、解码 2.7MB，纯属浪费）
BANNER_PX = (640, round(640 * BANNER_VIEW[1] / BANNER_VIEW[0]))


def rasterize(key, spec):
    """Edge headless 光栅化 SVG → TMP 下 ASCII 名 PNG（规避 file:// 中文路径不稳）。"""
    ascii_svg = os.path.join(TMP, f"{key}.svg")
    with open(os.path.join(SRC, spec["svg"]), encoding="utf-8") as f:
        text = f.read()
    for old, new in spec.get("recolor", {}).items():
        text = text.replace(old, new)
    with open(ascii_svg, "w", encoding="utf-8") as f:
        f.write(text)
    vw, vh = spec["vp"]
    out_png = os.path.join(TMP, f"{key}.png")
    if os.path.exists(out_png):
        os.remove(out_png)
    url = "file:///" + ascii_svg.replace("\\", "/")
    subprocess.run(
        [
            EDGE,
            "--headless=old",
            "--disable-gpu",
            "--no-sandbox",
            "--force-color-profile=srgb",
            "--default-background-color=00000000",
            f"--user-data-dir={os.path.join(TMP, 'edge')}",
            f"--window-size={vw},{vh}",
            f"--screenshot={out_png}",
            url,
        ],
        check=True,
        capture_output=True,
    )
    # Edge 进程返回时文件常常还没落盘：轮询等待
    for _ in range(100):
        if os.path.exists(out_png) and os.path.getsize(out_png) > 0:
            return out_png
        time.sleep(0.1)
    raise RuntimeError(f"rasterize timeout: {key} ({spec['svg']})")


def to_premultiplied(path):
    """母版 PNG → float32 预乘数组（0..1）。预乘是「正权平均 = 凸组合」成立的前提。"""
    arr = np.asarray(Image.open(path).convert("RGBA")).astype(np.float32) / 255.0
    arr[..., :3] *= arr[..., 3:4]
    return arr


def shrink(premul, w, h):
    """无负瓣面积缩放：BOX 整数折半到任一轴不足 2 倍目标，最后一次 BOX 落到精确尺寸。"""
    img = Image.fromarray((np.clip(premul, 0.0, 1.0) * 255.0).round().astype(np.uint8), "RGBA")
    while img.width // 2 >= w and img.height // 2 >= h:
        img = img.reduce(2)  # 2×2 盒式平均，等价 mipmap 层，无振铃
    if img.size != (w, h):
        img = img.resize((w, h), Image.BOX)
    return np.asarray(img).astype(np.float32) / 255.0


def to_straight(arr):
    """预乘 → 直 store alpha；反预乘放大出的噪声用母版色域钳住（R<=G<=B、通道差有顶）。"""
    a = np.clip(arr[..., 3:4], 0.0, 1.0)
    opaque = a > 1 / 255.0
    ch = [np.where(opaque, c / np.maximum(a, 1e-6), 0.0) for c in (arr[..., 0:1], arr[..., 1:2], arr[..., 2:3])]
    r = np.clip(ch[0], 0.0, 1.0)
    g = np.clip(np.maximum(ch[1], r), 0.0, 1.0)
    g = np.minimum(g, r + MAX_G_MINUS_R / 255.0)
    b = np.clip(np.maximum(ch[2], g), 0.0, 1.0)
    b = np.minimum(b, g + MAX_B_MINUS_G / 255.0)
    out = np.clip(np.concatenate([r, g, b, a], axis=-1), 0.0, 1.0)
    return (out * 255.0).round().astype(np.uint8)


def save(src_path, dst_path, w, h):
    rgba = to_straight(shrink(to_premultiplied(src_path), w, h))
    opaque = bool((rgba[..., 3] == 255).all())  # 全不透明就别存 alpha 通道（ic_launcher/纯背景）
    img = Image.fromarray(rgba[..., :3] if opaque else rgba, "RGB" if opaque else "RGBA")
    os.makedirs(os.path.dirname(dst_path), exist_ok=True)
    img.save(dst_path, "PNG", optimize=True)
    print(f"{w}x{h}  {'RGB ' if opaque else 'RGBA'}  {os.path.relpath(dst_path, ROOT)}")


def main():
    os.makedirs(TMP, exist_ok=True)
    try:
        masters = {key: rasterize(key, spec) for key, spec in MASTERS.items()}
        full, fg, bg = (masters[k] for k in ("full", "fg", "bg"))
        for dens, (launch_px, adapt_px) in DENSITY.items():
            save(full, os.path.join(RES, f"mipmap-{dens}", "ic_launcher.png"), launch_px, launch_px)
            save(fg, os.path.join(RES, f"drawable-{dens}", "ic_launcher_foreground.png"), adapt_px, adapt_px)
            save(bg, os.path.join(RES, f"drawable-{dens}", "ic_launcher_background.png"), adapt_px, adapt_px)
        # 登录页横幅：nodpi 一份像素画两个主题，靠 -night 限定符跟随系统深浅色
        for key, dir_name in (("banner_light", "drawable-nodpi"), ("banner_dark", "drawable-night-nodpi")):
            save(masters[key], os.path.join(RES, dir_name, "login_banner.png"), *BANNER_PX)
        print("done")
    finally:
        shutil.rmtree(TMP, ignore_errors=True)


if __name__ == "__main__":
    main()
