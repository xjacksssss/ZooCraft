"""RCON client for the perf harness. Vanilla RCON reads one packet per socket read, so commands go one at a time and
replies that span several packets are read until the socket goes quiet."""
import socket
import struct

HOST, PORT, PASSWORD = "127.0.0.1", 25575, "zoocraft-dev"


class Rcon:
    def __init__(self, host=HOST, port=PORT, password=PASSWORD):
        self.sock = socket.create_connection((host, port), timeout=60)
        self.next_id = 1
        self._send(3, password)
        if self._read_packet()[0] == -1:
            raise RuntimeError("RCON login failed")

    def _send(self, kind, body):
        self.next_id += 1
        data = struct.pack("<ii", self.next_id, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)

    def _exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise EOFError("RCON connection closed")
            buf += chunk
        return buf

    def _read_packet(self):
        length = struct.unpack("<i", self._exact(4))[0]
        data = self._exact(length)
        request_id = struct.unpack("<i", data[:4])[0]
        return request_id, data[8:-2].decode("utf-8", "replace")

    def command(self, body):
        """Runs one command and returns its full reply."""
        self._send(2, body)
        request_id, text = self._read_packet()
        parts = [text]
        if len(text.encode("utf-8")) >= 4000:
            # Possibly split: read further packets until none arrive for half a second.
            self.sock.settimeout(0.5)
            try:
                while True:
                    parts.append(self._read_packet()[1])
            except socket.timeout:
                pass
            finally:
                self.sock.settimeout(60)
        return "".join(parts)
