"""Writes the 16x16 item textures. Usage: python tools/make_item_textures.py"""
import os
import struct
import zlib

T = (0, 0, 0, 0)
BROWN = (110, 74, 40, 255)
DARK = (70, 46, 24, 255)
PALETTES = {
    # flag colours: main, shade
    "boundary_tool": ((70, 180, 60, 255), (40, 120, 35, 255)),
    "designation_tool": ((60, 130, 220, 255), (35, 80, 160, 255)),
}


def draw(main, shade, kind):
    px = [[T] * 16 for _ in range(16)]
    # Diagonal handle from bottom-left to the top-right area.
    for i in range(11):
        x, y = 2 + i, 14 - i
        px[y][x] = BROWN
        if y + 1 < 16:
            px[y + 1][x] = DARK
    if kind == "boundary_tool":
        # Pennant flag hanging from the top of the handle.
        for row in range(5):
            for col in range(6 - row):
                px[2 + row][8 - col] = main if col < 5 - row else shade
    else:
        # Rectangular tag with a hole.
        for y in range(1, 7):
            for x in range(7, 14):
                px[y][x] = shade if y in (1, 6) or x in (7, 13) else main
        px[3][10] = T
    return px


def png(pixels):
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *p) for p in row) for row in pixels)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


GREEN = (52, 120, 64, 255)
GREEN_DARK = (34, 84, 44, 255)
GREEN_LIGHT = (90, 160, 96, 255)
INSIDE = (30, 30, 30, 255)


def bin_side():
    """Painted metal with vertical ribs and a light rim along the top."""
    px = [[GREEN] * 16 for _ in range(16)]
    for y in range(16):
        for x in (2, 6, 10, 14):
            px[y][x] = GREEN_DARK
    for x in range(16):
        px[0][x] = GREEN_LIGHT
        px[1][x] = GREEN_LIGHT
        px[15][x] = GREEN_DARK
    return px


def bin_inside():
    px = [[INSIDE] * 16 for _ in range(16)]
    for i in range(16):
        px[i][i] = (40, 40, 40, 255)
    return px


def bin_rubbish():
    """A jumble of wrappers and paper seen from above."""
    colours = [(200, 60, 50, 255), (230, 220, 200, 255), (70, 110, 200, 255), (220, 180, 60, 255), (120, 90, 60, 255)]
    px = [[colours[(x * 7 + y * 13 + (x * y) % 5) % len(colours)] for x in range(16)] for y in range(16)]
    return px


def rubbish(kind):
    px = [[T] * 16 for _ in range(16)]
    if kind == "wrapper":
        # A crumpled red and white sweet wrapper.
        for y in range(5, 11):
            for x in range(4, 12):
                px[y][x] = (200, 50, 45, 255) if (x + y) % 3 else (240, 240, 235, 255)
        for y in (6, 7, 8, 9):
            px[y][3] = (200, 50, 45, 255)
            px[y][12] = (200, 50, 45, 255)
    elif kind == "cup":
        # A paper cup on its side.
        for x in range(3, 13):
            top, bottom = 5 + (x - 3) // 4, 11 - (x - 3) // 4
            for y in range(top, bottom + 1):
                px[y][x] = (235, 235, 230, 255) if x < 11 else (200, 200, 195, 255)
            px[top][x] = (60, 120, 200, 255)
    else:
        # A folded sheet of paper.
        for y in range(4, 12):
            for x in range(4, 12):
                px[y][x] = (245, 240, 225, 255) if x + y < 17 else (215, 210, 195, 255)
        for x in range(5, 11):
            px[6][x] = (150, 150, 160, 255)
            px[8][x] = (150, 150, 160, 255)
    return px


def write(folder, name, pixels):
    root = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "zoocraft", "textures", folder)
    os.makedirs(root, exist_ok=True)
    with open(os.path.join(root, name + ".png"), "wb") as f:
        f.write(png(pixels))
    print("wrote", folder + "/" + name)


def main():
    for name, (main_c, shade) in PALETTES.items():
        write("item", name, draw(main_c, shade, name))
    write("block", "rubbish_bin_side", bin_side())
    write("block", "rubbish_bin_inside", bin_inside())
    write("block", "rubbish_bin_rubbish", bin_rubbish())
    for kind in ("wrapper", "cup", "paper"):
        write("item", "rubbish_" + kind, rubbish(kind))


if __name__ == "__main__":
    main()
