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


def main():
    out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "zoocraft", "textures", "item")
    os.makedirs(out, exist_ok=True)
    for name, (main_c, shade) in PALETTES.items():
        with open(os.path.join(out, name + ".png"), "wb") as f:
            f.write(png(draw(main_c, shade, name)))
        print("wrote", name)


if __name__ == "__main__":
    main()
