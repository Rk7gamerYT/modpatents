"""Gera a logo do Mod Patents em pixel art (grade 50x50, exportada em 400x400).

Uso: python art/make_logo.py
Saídas: art/logo.png (400x400, CurseForge/Modrinth)
        src/main/resources/modpatents_logo.png (lista de mods do jogo)
"""
from pathlib import Path

from PIL import Image, ImageColor

GRID = 50
SCALE = 8

WOOD_DARK = "#6b4423"
WOOD = "#8b5a2b"
CELL = "#c9a26b"
CELL_SHADE = "#b08a55"

img = Image.new("RGB", (GRID, GRID), WOOD)
px = img.load()


def rect(x, y, w, h, color):
    for i in range(x, x + w):
        for j in range(y, y + h):
            if 0 <= i < GRID and 0 <= j < GRID:
                px[i, j] = ImageColor.getrgb(color)


def sprite(x, y, rows, palette):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ".":
                px[x + i, y + j] = ImageColor.getrgb(palette[ch])


# moldura de madeira com borda escura
rect(0, 0, GRID, 1, WOOD_DARK)
rect(0, GRID - 1, GRID, 1, WOOD_DARK)
rect(0, 0, 1, GRID, WOOD_DARK)
rect(GRID - 1, 0, 1, GRID, WOOD_DARK)

# grade 3x3: células de 14, bordas e espaços de 2
CELLS = [2, 18, 34]
SIZE = 14
for cx in CELLS:
    for cy in CELLS:
        rect(cx, cy, SIZE, SIZE, CELL)
        rect(cx, cy, SIZE, 1, CELL_SHADE)  # sombra interna em cima
        rect(cx, cy, 1, SIZE, CELL_SHADE)  # e à esquerda

INGOT = [
    "...hhhhhh.",
    "..hbbbbbbb",
    ".bbbbbbbbs",
    "bbbbbbbbs.",
    "ssssssss..",
]
sprite(CELLS[0] + 2, CELLS[0] + 5, INGOT, {"h": "#eef2f4", "b": "#b8c2c9", "s": "#7f8a91"})  # ferro
sprite(CELLS[2] + 2, CELLS[0] + 5, INGOT, {"h": "#fff3a8", "b": "#f0c330", "s": "#a87c12"})  # ouro

GEM = [
    "...oooo...",
    "..ohhllo..",
    ".ohhllllo.",
    "ollllllddo",
    ".olllddo..",
    "..olddo...",
    "...odo....",
    "....o.....",
]
sprite(CELLS[1] + 2, CELLS[1] + 3, GEM, {"o": "#1d5e7a", "h": "#e4fbff", "l": "#5fd0ea", "d": "#2f9bbd"})

REDSTONE = [
    "..r...",
    ".rRr.r",
    "rRRRr.",
    ".rRr..",
    "r..r..",
]
sprite(CELLS[0] + 4, CELLS[2] + 5, REDSTONE, {"r": "#8e1c16", "R": "#e0342a"})

# cadeado dourado sobre o canto inferior direito
PADLOCK = [
    "....kkkkkkkk....",
    "...kmmmmmmmmk...",
    "..kmmkkkkkkmmk..",
    "..kmk......kmk..",
    "..kmk......kmk..",
    "..kmk......kmk..",
    "..kmk......kmk..",
    "kkkkkkkkkkkkkkkk",
    "kYYYYYYYYYYYYYYk",
    "kGGGGGGGGGGGGGGk",
    "kGGGGGGkkGGGGGGk",
    "kGGGGGkkkkGGGGGk",
    "kGGGGGkkkkGGGGGk",
    "kGGGGGGkkGGGGGGk",
    "kGGGGGGkkGGGGGGk",
    "kGGGGGGkkGGGGGGk",
    "kSSSSSSSSSSSSSSk",
    "kSSSSSSSSSSSSSSk",
    "kkkkkkkkkkkkkkkk",
]
sprite(GRID - 2 - 16, GRID - 2 - 19, PADLOCK, {"k": "#2b1d0a", "m": "#9aa3a9", "Y": "#fbe38a", "G": "#e0b030", "S": "#b0831a"})

big = img.resize((GRID * SCALE, GRID * SCALE), Image.NEAREST)
root = Path(__file__).resolve().parent.parent
big.save(root / "art" / "logo.png")
big.save(root / "src" / "main" / "resources" / "modpatents_logo.png")
print("ok")
