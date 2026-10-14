"""Writes flat-floor structure templates used by ZooCraft GameTests.

Usage: python tools/make_test_structure.py
Writes src/main/resources/data/zoocraft/structure/<name>.nbt for each template below.
"""
import gzip
import os
import struct

DATA_VERSION = 3955  # Minecraft 1.21.1

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def _name(name):
    raw = name.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def _payload(tag, value):
    if tag == TAG_INT:
        return struct.pack(">i", value)
    if tag == TAG_STRING:
        return _name(value)
    if tag == TAG_LIST:
        elem_tag, items = value
        out = struct.pack(">bi", elem_tag if items else TAG_END, len(items))
        return out + b"".join(_payload(elem_tag, i) for i in items)
    if tag == TAG_COMPOUND:
        out = b""
        for key, (t, v) in value.items():
            out += struct.pack(">b", t) + _name(key) + _payload(t, v)
        return out + struct.pack(">b", TAG_END)
    raise ValueError(tag)


def floor_template(size_x, size_y, size_z, floor_block):
    palette = [{"Name": (TAG_STRING, floor_block)}]
    blocks = []
    for x in range(size_x):
        for z in range(size_z):
            blocks.append({
                "pos": (TAG_LIST, (TAG_INT, [x, 0, z])),
                "state": (TAG_INT, 0),
            })
    return {
        "DataVersion": (TAG_INT, DATA_VERSION),
        "size": (TAG_LIST, (TAG_INT, [size_x, size_y, size_z])),
        "palette": (TAG_LIST, (TAG_COMPOUND, palette)),
        "blocks": (TAG_LIST, (TAG_COMPOUND, blocks)),
        "entities": (TAG_LIST, (TAG_COMPOUND, [])),
    }


TEMPLATES = {
    "zoo_test_floor": (32, 6, 32, "minecraft:grass_block"),
    "zoo_test_floor_large": (48, 6, 48, "minecraft:grass_block"),
}


def main():
    root = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                        "data", "zoocraft", "structure")
    os.makedirs(root, exist_ok=True)
    for name, (sx, sy, sz, block) in TEMPLATES.items():
        data = struct.pack(">b", TAG_COMPOUND) + _name("") + _payload(TAG_COMPOUND, floor_template(sx, sy, sz, block))
        with gzip.open(os.path.join(root, name + ".nbt"), "wb") as f:
            f.write(data)
        print("wrote", name)


if __name__ == "__main__":
    main()
