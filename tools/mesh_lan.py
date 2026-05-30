"""LAN transport helpers — matches LanTransport.kt (length-prefixed TCP + mDNS)."""

from __future__ import annotations

import socket
import struct
import threading
import time
from typing import Callable

try:
    from zeroconf import ServiceBrowser, Zeroconf
except ImportError:
    Zeroconf = None  # type: ignore

SERVICE_TYPE = "_meshhood._tcp.local."


def discover_phones(timeout: float = 12.0) -> list[tuple[str, int, str]]:
    """Return [(host, port, service_name), ...] for MeshHood LAN adverts."""
    if Zeroconf is None:
        print("  (install zeroconf: pip install zeroconf)", flush=True)
        return []

    found: list[tuple[str, int, str]] = []
    lock = threading.Lock()

    class Listener:
        def add_service(self, zc: Zeroconf, type_: str, name: str) -> None:
            info = zc.get_service_info(type_, name, timeout=4000)
            if not info or not info.addresses:
                return
            host = socket.inet_ntoa(info.addresses[0])
            with lock:
                found.append((host, info.port, name))

        def remove_service(self, zc: Zeroconf, type_: str, name: str) -> None:
            pass

        def update_service(self, zc: Zeroconf, type_: str, name: str) -> None:
            pass

    zc = Zeroconf()
    ServiceBrowser(zc, SERVICE_TYPE, Listener())
    time.sleep(timeout)
    zc.close()
    return found


def send_frame(host: str, port: int, payload: bytes) -> None:
    with socket.create_connection((host, port), timeout=8) as sock:
        sock.sendall(struct.pack(">I", len(payload)) + payload)


def listen_frames(
    host: str,
    port: int,
    on_frame: Callable[[bytes], None],
    duration: float = 8.0,
) -> None:
    """Connect and read length-prefixed frames for [duration] seconds."""
    stop = threading.Event()

    def reader(sock: socket.socket) -> None:
        try:
            inp = sock.makefile("rb")
            deadline = time.time() + duration
            while not stop.is_set() and time.time() < deadline:
                header = inp.read(4)
                if len(header) < 4:
                    break
                (length,) = struct.unpack(">I", header)
                if length <= 0 or length > 65536:
                    break
                data = inp.read(length)
                if len(data) < length:
                    break
                on_frame(data)
        except OSError:
            pass

    with socket.create_connection((host, port), timeout=8) as sock:
        t = threading.Thread(target=reader, args=(sock,), daemon=True)
        t.start()
        time.sleep(duration)
        stop.set()
        t.join(timeout=1.0)
