#!/usr/bin/env python3
"""
Gera TODOS os assets das gelatinas e dos blocos de retorno (texturas, modelos, blockstates, itens, lang).

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


# ----------------------------------------------------------------------------- blocos de retorno (wins)
# Um bloco de retorno por fase: a cor vem da gelatina de mesmo índice em TIERS (rosa=fase 1 ... negra=fase 9).
# Wins de cada fase (precisa bater com PAD_WINS em ReapMod.java).
PAD_WINS = [1, 3, 8, 20, 50, 120, 300, 750, 900]
PADS = [(wins, TIERS[i][3], TIERS[i][1].replace("Gelatina ", ""), TIERS[i][2].replace(" Jelly", ""))
        for i, wins in enumerate(PAD_WINS)]


def pad_top_texture(color):
    """Piso quadriculado (2 tons) com grade e 'studs', igual ao bloco de Return do jogo. 32x32, repete sem emenda."""
    c = np.array(color, dtype=np.float32)
    img = np.zeros((32, 32, 4), np.uint8)
    for y in range(32):
        for x in range(32):
            k = 1.0 if ((x // 8) + (y // 8)) % 2 == 0 else 0.84
            if x % 8 == 0 or y % 8 == 0:
                k *= 0.74
            elif x % 4 == 0 or y % 4 == 0:
                k *= 0.90
            elif x % 4 == 1 and y % 4 == 1:
                k *= 1.13
            img[y, x, :3] = np.clip(c * k, 0, 255)
            img[y, x, 3] = 255
    return img


def pad_side_texture(color):
    c = np.array(color, dtype=np.float32) * 0.62
    img = np.zeros((16, 16, 4), np.uint8)
    img[..., :3] = np.clip(c, 0, 255)
    img[..., 3] = 255
    return img


def pad_black_texture():
    rng = np.random.default_rng(5)
    img = np.zeros((16, 16, 4), np.uint8)
    n = rng.integers(0, 7, (16, 16))
    img[..., 0] = 20 + n
    img[..., 1] = 20 + n
    img[..., 2] = 26 + n
    img[..., 3] = 255
    return img


def quad(uv, tex, **kw):
    d = {"uv": uv, "texture": tex}
    d.update(kw)
    return d


def pad_parents():
    blk = "minecraft:block/block"
    models = {}
    models["pad_center"] = {"parent": blk, "elements": [{
        "from": [1, 0, 1], "to": [15, 3, 15],
        "faces": {"up": quad([1, 1, 15, 15], "#top"), "down": quad([1, 1, 15, 15], "#black", cullface="down")}}]}
    models["pad_edge_orange"] = {"parent": blk, "elements": [{
        "from": [1, 0, 0], "to": [15, 3, 1],
        "faces": {"up": quad([1, 0, 15, 1], "#top"), "down": quad([1, 15, 15, 16], "#black", cullface="down")}}]}
    models["pad_edge_black"] = {"parent": blk, "elements": [{
        "from": [1, 0, 0], "to": [15, 3, 1],
        "faces": {"up": quad([1, 0, 15, 1], "#black"), "north": quad([1, 13, 15, 16], "#black"),
                  "down": quad([1, 15, 15, 16], "#black", cullface="down")}}]}
    models["pad_corner_orange"] = {"parent": blk, "elements": [{
        "from": [0, 0, 0], "to": [1, 3, 1],
        "faces": {"up": quad([0, 0, 1, 1], "#top"), "down": quad([0, 15, 1, 16], "#black", cullface="down")}}]}
    models["pad_corner_black"] = {"parent": blk, "elements": [{
        "from": [0, 0, 0], "to": [1, 3, 1],
        "faces": {"up": quad([0, 0, 1, 1], "#black"), "north": quad([15, 13, 16, 16], "#black"),
                  "west": quad([0, 13, 1, 16], "#black"), "down": quad([0, 15, 1, 16], "#black", cullface="down")}}]}
    models["pad_item"] = {"parent": blk, "elements": [
        {"from": [0, 0, 0], "to": [16, 2, 16], "faces": {
            d: quad([0, 0, 16, 16] if d in ("up", "down") else [0, 14, 16, 16], "#black") for d in
            ("up", "down", "north", "south", "east", "west")}},
        {"from": [1, 2, 1], "to": [15, 3, 15], "faces": {
            "up": quad([1, 1, 15, 15], "#top"),
            "north": quad([1, 13, 15, 14], "#side"), "south": quad([1, 13, 15, 14], "#side"),
            "east": quad([1, 13, 15, 14], "#side"), "west": quad([1, 13, 15, 14], "#side")}}]}
    return models


def gen_pads(lang_pt, lang_en):
    tex_dir = os.path.join(ASSETS, "textures", "block")
    Image.fromarray(pad_black_texture(), "RGBA").save(os.path.join(tex_dir, "pad_black.png"))
    for name, m in pad_parents().items():
        write_json(os.path.join(ASSETS, "models", "block", f"{name}.json"), m)

    dirs = ["north", "east", "south", "west"]
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    corners = [("north", "west", 0), ("east", "north", 90), ("south", "east", 180), ("west", "south", 270)]

    for wins, color, cor_pt, cor_en in PADS:
        pid = f"return_{wins}"
        Image.fromarray(pad_top_texture(color), "RGBA").save(os.path.join(tex_dir, f"{pid}.png"))
        Image.fromarray(pad_side_texture(color), "RGBA").save(os.path.join(tex_dir, f"{pid}_side.png"))
        tex = {"top": f"reap:block/{pid}", "side": f"reap:block/{pid}_side",
               "black": "reap:block/pad_black", "particle": f"reap:block/{pid}"}
        for part in ("center", "edge_orange", "edge_black", "corner_orange", "corner_black"):
            write_json(os.path.join(ASSETS, "models", "block", f"{pid}_{part}.json"),
                       {"parent": f"reap:block/pad_{part}", "textures": tex})
        write_json(os.path.join(ASSETS, "models", "block", f"{pid}_item.json"),
                   {"parent": "reap:block/pad_item", "textures": tex})

        parts = [{"apply": {"model": f"reap:block/{pid}_center"}}]
        for d in dirs:
            parts.append({"when": {d: "true"}, "apply": {"model": f"reap:block/{pid}_edge_orange", "y": rot[d]}})
            parts.append({"when": {d: "false"}, "apply": {"model": f"reap:block/{pid}_edge_black", "y": rot[d]}})
        for a, b, y in corners:
            parts.append({"when": {"AND": [{a: "true"}, {b: "true"}]},
                          "apply": {"model": f"reap:block/{pid}_corner_orange", "y": y}})
            parts.append({"when": {"OR": [{a: "false"}, {b: "false"}]},
                          "apply": {"model": f"reap:block/{pid}_corner_black", "y": y}})
        write_json(os.path.join(ASSETS, "blockstates", f"{pid}.json"), {"multipart": parts})
        write_json(os.path.join(ASSETS, "models", "item", f"{pid}.json"), {"parent": f"reap:block/{pid}_item"})
        w = "Win" if wins == 1 else "Wins"
        lang_pt[f"block.reap.{pid}"] = f"Retorno {cor_pt} +{wins} {w}"
        lang_en[f"block.reap.{pid}"] = f"{cor_en} Return Pad +{wins} {w}"


# ----------------------------------------------------------------------------- texturas dos efeitos (pedaços e poças)
def _smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def _g(x, y, cx, cy, sx, sy):
    return np.exp(-(((x - cx) / sx) ** 2 + ((y - cy) / sy) ** 2))


def gen_fx():
    """jblob (pedaço de gelatina 3D) e puddle (poça brilhante): corpo cinza (recebe a cor do tipo) + brilho branco (aditivo)."""
    S = 64
    yy, xx = np.mgrid[0:S, 0:S]
    x = (xx + 0.5) / S * 2 - 1
    y = -((yy + 0.5) / S * 2 - 1)  # y para cima
    L = np.array([-0.45, 0.6, 0.66])
    L = L / np.linalg.norm(L)
    out = os.path.join(ASSETS, "textures", "fx")
    os.makedirs(out, exist_ok=True)

    def save(name, gray, alpha):
        img = np.dstack([np.repeat(gray[..., None], 3, axis=2) * 255, alpha * 255])
        Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(os.path.join(out, name))

    # ---------------- pedaço (esfera translúcida)
    R = 0.92
    r = np.sqrt(x ** 2 + y ** 2)
    u = r / R
    h = np.sqrt(np.clip(1 - u ** 2, 0, 1))
    diff = np.clip((x / R) * L[0] + (y / R) * L[1] + h * L[2], 0, 1)
    gray = 0.50 + 0.20 * h ** 0.6 + 0.22 * diff + 0.16 * _g(x, y, 0.30, -0.33, 0.38, 0.38)
    alpha = (0.80 + 0.18 * (1 - h)) * _smooth(1.0, 0.90, u)
    save("jblob.png", np.clip(gray, 0, 1), alpha)
    spec = (0.75 * _g(x, y, -0.32, 0.36, 0.20, 0.13) + 1.0 * _g(x, y, -0.30, 0.38, 0.06, 0.05)
            + 0.32 * np.exp(-((r - 0.80) / 0.06) ** 2) * np.clip((x - y) / 1.2, 0, 1))
    save("jblob_spec.png", np.ones((S, S)), np.clip(spec, 0, 1) * _smooth(1.0, 0.92, u) * 0.9)

    # ---------------- poça (contorno orgânico + gotinhas ao redor)
    th = np.arctan2(y, x)
    Rth = (0.70 + 0.07 * np.sin(3 * th + 1.1) + 0.05 * np.sin(5 * th + 2.3)
           + 0.035 * np.sin(7 * th + 0.4) + 0.02 * np.sin(11 * th + 1.7))
    up = r / Rth
    hh = np.sqrt(np.clip(1 - up ** 3, 0, 1))
    mask = _smooth(1.0, 0.93, up)
    sats = [(0.86, 0.5, 0.085), (0.84, 2.2, 0.065), (0.88, 3.6, 0.075), (0.82, 5.0, 0.055)]
    sat_spec = np.zeros_like(hh)
    for (rad, ang, sr) in sats:
        cx, cy = rad * np.cos(ang), rad * np.sin(ang)
        d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / sr
        sh = np.sqrt(np.clip(1 - d ** 2, 0, 1))
        hh = np.maximum(hh, sh)
        mask = np.maximum(mask, _smooth(1.0, 0.8, d))
        sat_spec += 0.8 * _g(x, y, cx - sr * 0.3, cy + sr * 0.3, sr * 0.35, sr * 0.3)
    gy, gx = np.gradient(hh)
    k = 0.35 / (2.0 / S)
    nx, ny = -gx * k, gy * k  # y da imagem cresce para baixo
    nz = np.ones_like(nx)
    nrm = np.sqrt(nx ** 2 + ny ** 2 + nz ** 2)
    diff = np.clip((nx * L[0] + ny * L[1] + nz * L[2]) / nrm, 0, 1)
    gray = 0.62 + 0.18 * hh + 0.22 * diff
    alpha = (0.74 + 0.20 * (1 - hh)) * mask
    save("puddle.png", np.clip(gray, 0, 1), alpha)
    spec = (0.55 * _g(x, y, -0.28, 0.30, 0.22, 0.09) + 0.95 * _g(x, y, -0.25, 0.32, 0.05, 0.04)
            + 0.30 * np.exp(-((up - 0.88) / 0.07) ** 2) * np.clip((x - y) / 1.2, 0, 1) + sat_spec)
    save("puddle_spec.png", np.ones((S, S)), np.clip(spec, 0, 1) * mask * (hh > 0.04) * 0.9)


# ----------------------------------------------------------------------------- gelatinas de treino (inquebráveis, dão XP)
# id, nome pt, nome en, cor, rebirths necessários, multiplicador de XP, texto
TRAIN = [
    ("train_starter", "Starter",   (80, 215, 255)),
    ("train_r1",      "Rebirth 1", (90, 220, 80)),
    ("train_r3",      "Rebirth 3", (255, 226, 90)),
    ("train_r5",      "Rebirth 5", (255, 60, 200)),
    ("train_r6",      "Rebirth 6", (190, 238, 255)),
]


def train_plate_texture(color):
    """Placa do chão (5x5 blocos): borda colorida com brilho + piso azul-acinzentado com ondas e azulejos. 64x64."""
    c = np.array(color, dtype=np.float32)
    S = 64
    img = np.zeros((S, S, 4), np.uint8)
    yy, xx = np.mgrid[0:S, 0:S]
    for y in range(S):
        for x in range(S):
            edge = min(x, y, S - 1 - x, S - 1 - y)
            if edge < 2:
                rgb = c * 0.55 + 255 * 0.45          # fio claro externo
            elif edge < 8:
                rgb = c * (1.0 - 0.06 * (edge - 2))   # moldura colorida
            elif edge < 10:
                rgb = c * 0.45                        # sombra interna
            else:
                tile = ((x // 4) + (y // 4)) % 2
                base = np.array([66, 68, 104], np.float32) if tile == 0 else np.array([56, 58, 92], np.float32)
                wave = 0.5 + 0.5 * np.sin((x + 2 * np.sin(y * 0.35)) * 0.45)
                rgb = base * 0.86 + c * (0.07 + 0.10 * wave)  # ondinhas na cor da gelatina
                if x % 4 == 0 or y % 4 == 0:
                    rgb = rgb * 0.92
            img[y, x, :3] = np.clip(rgb, 0, 255)
            img[y, x, 3] = 255
    return img


def gen_train(lang_pt, lang_en):
    """
    Gelatina de treino GRANDE: 1 bloco "controlador" + blocos-parte invisíveis (3x3x3 de colisão).
    O visual (placa 5x5 + gelatina ~3 blocos) é desenhado pelo BlockEntityRenderer a partir do modelo *_big
    (16 px de largura, ampliado 5x no jogo: placa 5 blocos, gelatina 3 de largura e 2,7 de altura).
    """
    tex_dir = os.path.join(ASSETS, "textures", "block")
    blk = "minecraft:block/block"
    faces6 = ("up", "down", "north", "south", "east", "west")
    full = lambda t, **kw: dict({"uv": [0, 0, 16, 16], "texture": t}, **kw)

    write_json(os.path.join(ASSETS, "models", "block", "empty.json"),
               {"textures": {"particle": "reap:block/pad_black"}, "elements": []})
    write_json(os.path.join(ASSETS, "blockstates", "train_part.json"),
               {"variants": {f"layer={i}": {"model": "reap:block/empty"} for i in range(3)}})

    for i, (tid, label, color) in enumerate(TRAIN):
        frames = make_shell(40 + i, color)
        Image.fromarray(np.concatenate(frames, axis=0), "RGBA").save(os.path.join(tex_dir, f"{tid}.png"))
        write_json(os.path.join(tex_dir, f"{tid}.png.mcmeta"), {"animation": {"frametime": 3, "interpolate": True}})
        Image.fromarray(make_core(40 + i, color), "RGBA").save(os.path.join(tex_dir, f"{tid}_core.png"))
        Image.fromarray(train_plate_texture(color), "RGBA").save(os.path.join(tex_dir, f"{tid}_plate.png"))

        big = {"parent": blk, "ambientocclusion": False,
               "textures": {"shell": f"reap:block/{tid}", "core": f"reap:block/{tid}_core",
                            "plate": f"reap:block/{tid}_plate", "dark": "reap:block/pad_black",
                            "particle": f"reap:block/{tid}"},
               "elements": [
                   {"from": [0, 0, 0], "to": [16, 0.4, 16], "faces": {
                       "up": full("#plate"),
                       "north": {"uv": [0, 15, 16, 16], "texture": "#dark"}, "south": {"uv": [0, 15, 16, 16], "texture": "#dark"},
                       "east": {"uv": [0, 15, 16, 16], "texture": "#dark"}, "west": {"uv": [0, 15, 16, 16], "texture": "#dark"}}},
                   {"from": [3.2, 0.4, 3.2], "to": [12.8, 9.04, 12.8], "shade": False,
                    "faces": {d: full("#shell") for d in faces6}},
                   {"from": [4.8, 1.6, 4.8], "to": [11.2, 7.8, 11.2], "shade": False,
                    "faces": {d: full("#core") for d in faces6}},
               ]}
        write_json(os.path.join(ASSETS, "models", "block", f"{tid}_big.json"), big)
        # bloco "visual" escondido (nunca colocado): só fornece o modelo para o renderer
        write_json(os.path.join(ASSETS, "blockstates", f"{tid}_big.json"),
                   {"variants": {"": {"model": f"reap:block/{tid}_big"}}})
        # bloco controlador colocado no mundo: invisível (o renderer desenha tudo)
        write_json(os.path.join(ASSETS, "blockstates", f"{tid}.json"),
                   {"variants": {"": {"model": "reap:block/empty"}}})
        write_json(os.path.join(ASSETS, "models", "item", f"{tid}.json"), {"parent": f"reap:block/{tid}_big"})
        lang_pt[f"block.reap.{tid}"] = f"Gelatina de Treino ({label})"
        lang_en[f"block.reap.{tid}"] = f"Training Jelly ({label})"


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

    gen_pads(lang_pt, lang_en)
    gen_train(lang_pt, lang_en)
    gen_fx()
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
