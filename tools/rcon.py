"""Minimal RCON client for scripting the dev server (run-server/server.properties enables it).

Usage: python tools/rcon.py "command one" "command two" ...
"""
import socket
import struct
import sys

HOST, PORT, PASSWORD = "127.0.0.1", 25575, "zoocraft-dev"


def packet(request_id, kind, body):
    data = struct.pack("<ii", request_id, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(data)) + data


def read(sock):
    length = struct.unpack("<i", sock.recv(4))[0]
    data = b""
    while len(data) < length:
        data += sock.recv(length - len(data))
    request_id, kind = struct.unpack("<ii", data[:8])
    return request_id, data[8:-2].decode("utf-8", "replace")


def main(commands):
    with socket.create_connection((HOST, PORT), timeout=10) as sock:
        sock.sendall(packet(1, 3, PASSWORD))
        if read(sock)[0] == -1:
            sys.exit("RCON login failed")
        for i, command in enumerate(commands, start=2):
            sock.sendall(packet(i, 2, command))
            print(f"> {command}\n{read(sock)[1]}")


if __name__ == "__main__":
    main(sys.argv[1:])
