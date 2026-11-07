#!/usr/bin/env python3
"""
Gera TODOS os assets das gelatinas (texturas animadas, modelos, blockstates, itens, lang).

    pip install pillow numpy
    python3 tools/gen_assets.py

Para mudar cores / nomes / quantidade de tiers edite TIERS aqui E o array TYPES em ReapMod.java
(os ids precisam ser iguais).  Rode o script de novo e recompile.
"""
import json
import os

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "src", "main", "resources", "assets", "reap"))

N = 64          # resolução da textura (modelos usam UV 0..16, então qualquer potência de 2 funciona)
FRAMES = 8      # frames da animação do brilho da gelatina

# id, nome pt, nome en, cor do corpo (RGB)
TIERS = [
    ("goo",        "Gelatina Rosa",     "Pink Jelly",   (255, 84, 188)),
    ("goo_red",    "Gelatina Vermelha", "Red Jelly",    (232, 36, 58)),
    ("goo_orange", "Gelatina Laranja",  "Orange Jelly", (255, 138, 28)),
    ("goo_yellow", "Gelatina Amarela",  "Yellow Jelly", (255, 214, 40)),
    ("goo_green",  "Gelatina Verde",    "Green Jelly",  (64, 214, 72)),
    ("goo_cyan",   "Gelatina Ciano",    "Cyan Jelly",   (44, 214, 236)),
    ("goo_blue",   "Gelatina Azul",     "Blue Jelly",   (58, 98, 255)),
    ("goo_purple", "Gelatina Roxa",     "Purple Jelly", (148, 66, 234)),
    ("goo_black",  "Gelatina Negra",    "Black Jelly",  (52, 46, 84)),
]

# quantas células (de 4x4x4 = 64) sobram em cada estágio de "mordida" (0 = bloco inteiro)
KEEP = [64, 54, 46, 38, 30, 22, 14, 8]
STAGES = len(KEEP)


# ----------------------------------------------------------------------------- texturas
def tile_noise(seed, beta=2.4):
    """Ruído 0..1 que repete nas bordas (sem emenda entre blocos)."""
    rng = np.random.default_rng(seed)
    f = np.fft.fftfreq(N)
    fx, fy = np.meshgrid(f, f)
    r = np.sqrt(fx ** 2 + fy ** 2)
    r[0, 0] = 1.0
    amp = r ** (-beta / 2.0)
    amp[0, 0] = 0.0
    spec = amp * (rng.standard_normal((N, N)) + 1j * rng.standard_normal((N, N)))
    n = np.real(np.fft.ifft2(spec))
    n = (n - n.min()) / (n.max() - n.min() + 1e-9)
    return n


U, V = np.meshgrid(np.arange(N) / N, np.arange(N) / N)  # U = x, V = y
TAU = 2 * np.pi


def gauss(cx, cy, sx, sy):
    return np.exp(-(((U - cx) / sx) ** 2 + ((V - cy) / sy) ** 2))


def lerp(a, b, t):
    return a + (b - a) * t


def make_shell(idx, body):
    body = np.array(body, dtype=np.float32)
    n1 = tile_noise(100 + idx, beta=3.2)
    n2 = tile_noise(200 + idx, beta=3.6)
    n3 = tile_noise(300 + idx, beta=3.0)
    rng = np.random.default_rng(900 + idx)

    # brilho especular fixo (gelatina lisa e brilhante)
    spec = 0.95 * gauss(0.28, 0.24, 0.085, 0.055) + 0.55 * gauss(0.74, 0.76, 0.035, 0.03)
    spec += 0.25 * gauss(0.62, 0.18, 0.18, 0.03)

    # bolhinhas minúsculas presas na gelatina
    bub_ring = np.zeros((N, N), np.float32)
    bub_fill = np.zeros((N, N), np.float32)
    for _ in range(4):
        cx, cy = rng.uniform(0.1, 0.9, 2)
        r = rng.uniform(0.014, 0.034)
        d = np.sqrt((U - cx) ** 2 + (V - cy) ** 2)
        bub_ring += np.exp(-((d - r) / 0.0055) ** 2)
        bub_fill += np.exp(-(d / (r * 0.8)) ** 2) * 0.12
        bub_ring += 0.8 * np.exp(-(((U - (cx - r * 0.4)) / 0.006) ** 2 + ((V - (cy - r * 0.4)) / 0.006) ** 2))

    # borda mais densa/brilhante (efeito fresnel de vidro)
    e = np.minimum.reduce([U, 1 - U, V, 1 - V])
    rim = np.clip(1 - e / 0.075, 0, 1) ** 1.6

    frames = []
    for f in range(FRAMES):
        ph = TAU * f / FRAMES
        # linhas de luz ondulantes (cáusticas), em loop perfeito
        a = TAU * (1 * U + 1 * V) + 0.9 * (n1 - 0.5) * TAU + ph
        b = TAU * (-1 * U + 1 * V) + 0.9 * (n2 - 0.5) * TAU - ph
        fld = np.sin(a) + np.sin(b)
        line = np.exp(-(fld / 0.34) ** 2)
        hl = (line ** 1.3) * (0.15 + 0.85 * n3 ** 2) * 0.42

        shade = 0.80 + 0.36 * n1
        rgb = body[None, None, :] * shade[..., None]
        rgb = lerp(rgb, rgb * 0.62, (n2[..., None] - 0.5).clip(0, 1) * 0.9)  # manchas mais escuras
        glow = np.clip(hl + spec + 0.5 * bub_ring + bub_fill, 0, 1)[..., None]
        rgb = rgb + (255.0 - rgb) * glow * 0.80
        rgb = rgb + (255.0 - rgb) * rim[..., None] * 0.30

        alpha = 166 + 36 * n1 + 70 * glow[..., 0] + 50 * rim + 30 * np.clip(bub_ring, 0, 1)
        img = np.dstack([np.clip(rgb, 0, 255), np.clip(alpha, 0, 238)]).astype(np.uint8)
        frames.append(img)
    return frames


def make_core(idx, body):
    body = np.array(body, dtype=np.float32)
    n1 = tile_noise(400 + idx)
    n2 = tile_noise(500 + idx, beta=3.0)
    core_glow = gauss(0.5, 0.5, 0.33, 0.33)
    rgb = body[None, None, :] * (0.50 + 0.38 * n1[..., None])
    rgb = rgb + (255.0 - rgb) * (0.22 * core_glow[..., None] + 0.12 * (n2[..., None] ** 3))
    alpha = np.full((N, N), 212.0) + 20 * n2
    return np.dstack([np.clip(rgb, 0, 255), np.clip(alpha, 0, 255)]).astype(np.uint8)


# ----------------------------------------------------------------------------- modelos
def cell_order():
    """Ordem em que as células 4x4x4 somem: de fora pra dentro, o topo primeiro (a gelatina "afunda")."""
    rng = np.random.default_rng(7)
    cells = [(i, j, k) for i in range(4) for j in range(4) for k in range(4)]

    def score(c):
        i, j, k = c
        d = np.sqrt((i - 1.5) ** 2 + (k - 1.5) ** 2 + 0.6 * (j - 1.5) ** 2)
        return d + 0.35 * (j - 1.5) + rng.uniform(0, 0.95)

    return sorted(cells, key=score, reverse=True)  # primeiro da lista = primeiro a ser comido


ORDER = cell_order()


def kept_cells(stage):
    removed = set(ORDER[: 64 - KEEP[stage]])
    return {c for c in ORDER if c not in removed}


FACES = {
    # nome: (dx, dy, dz)
    "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0),
    "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0),
}


def face_uv(face, i, j, k):
    x0, y0, z0 = 4 * i, 4 * j, 4 * k
    if face == "up":
        return [x0, z0, x0 + 4, z0 + 4]
    if face == "down":
        return [x0, 16 - z0 - 4, x0 + 4, 16 - z0]
    if face == "north":
        return [16 - x0 - 4, 16 - y0 - 4, 16 - x0, 16 - y0]
    if face == "south":
        return [x0, 16 - y0 - 4, x0 + 4, 16 - y0]
    if face == "west":
        return [z0, 16 - y0 - 4, z0 + 4, 16 - y0]
    return [16 - z0 - 4, 16 - y0 - 4, 16 - z0, 16 - y0]  # east


def core_element():
    f = {d: {"uv": [0, 0, 16, 16], "texture": "#core"} for d in FACES}
    return {"from": [5, 5, 5], "to": [11, 11, 11], "shade": False, "faces": f}


def stage_model(stage):
    elements = []
    if stage == 0:
        faces = {d: {"uv": [0, 0, 16, 16], "texture": "#shell", "cullface": d} for d in FACES}
        elements.append({"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "faces": faces})
    else:
        keep = kept_cells(stage)
        for (i, j, k) in sorted(keep):
            faces = {}
            for name, (dx, dy, dz) in FACES.items():
                ni, nj, nk = i + dx, j + dy, k + dz
                outside = not (0 <= ni < 4 and 0 <= nj < 4 and 0 <= nk < 4)
                if not outside and (ni, nj, nk) in keep:
                    continue  # face interna: não desenha
                face = {"uv": face_uv(name, i, j, k), "texture": "#shell"}
                if outside:
                    face["cullface"] = name
                faces[name] = face
            if faces:
                elements.append({"from": [4 * i, 4 * j, 4 * k], "to": [4 * i + 4, 4 * j + 4, 4 * k + 4],
                                 "shade": False, "faces": faces})
    elements.append(core_element())
    return {"parent": "minecraft:block/block", "ambientocclusion": False, "elements": elements}


# ----------------------------------------------------------------------------- escrita
def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, ensure_ascii=False, separators=(",", ":"))


def main():
    tex_dir = os.path.join(ASSETS, "textures", "block")
    os.makedirs(tex_dir, exist_ok=True)

    for s in range(STAGES):
        write_json(os.path.join(ASSETS, "models", "block", f"jelly_bites_{s}.json"), stage_model(s))

    lang_pt, lang_en = {}, {}
    sheet = []
    for idx, (bid, pt, en, color) in enumerate(TIERS):
        frames = make_shell(idx, color)
        strip = np.concatenate(frames, axis=0)
        Image.fromarray(strip, "RGBA").save(os.path.join(tex_dir, f"{bid}.png"))
        write_json(os.path.join(tex_dir, f"{bid}.png.mcmeta"), {"animation": {"frametime": 3, "interpolate": True}})
        Image.fromarray(make_core(idx, color), "RGBA").save(os.path.join(tex_dir, f"{bid}_core.png"))
        sheet.append(frames[0])

        variants = {}
        for s in range(STAGES):
            write_json(os.path.join(ASSETS, "models", "block", f"{bid}_bites_{s}.json"), {
                "parent": f"reap:block/jelly_bites_{s}",
                "textures": {"shell": f"reap:block/{bid}", "core": f"reap:block/{bid}_core",
                             "particle": f"reap:block/{bid}"},
            })
            variants[f"bites={s}"] = {"model": f"reap:block/{bid}_bites_{s}"}
        write_json(os.path.join(ASSETS, "blockstates", f"{bid}.json"), {"variants": variants})
        write_json(os.path.join(ASSETS, "models", "item", f"{bid}.json"), {"parent": f"reap:block/{bid}_bites_0"})
        lang_pt[f"block.reap.{bid}"] = pt
        lang_en[f"block.reap.{bid}"] = en

    write_json(os.path.join(ASSETS, "lang", "pt_br.json"), lang_pt)
    write_json(os.path.join(ASSETS, "lang", "en_us.json"), lang_en)

    # prévia (fora do mod): frame 0 de cada gelatina sobre um fundo verde
    pad = 4
    W = len(sheet) * (N + pad) + pad
    prev = Image.new("RGBA", (W, N + 2 * pad), (40, 180, 60, 255))
    for i, fr in enumerate(sheet):
        prev.alpha_composite(Image.fromarray(fr, "RGBA"), (pad + i * (N + pad), pad))
    prev.resize((prev.width * 2, prev.height * 2), Image.NEAREST).save(os.path.join(HERE, "preview_textures.png"))
    print("ok:", len(TIERS), "gelatinas,", STAGES, "estágios cada")


if __name__ == "__main__":
    main()
