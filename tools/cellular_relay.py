#!/usr/bin/env python3
"""Minimal MeshHood cellular relay for LAN testing.

Stores neighborhood-encrypted mesh wire frames and forwards them to polling
gateways. Run on a PC reachable from the gateway phone (same Wi‑Fi or USB
reverse port forward).

  python tools/cellular_relay.py --host 0.0.0.0 --port 8765 --token demo

Gateway relay URL example: http://192.168.1.50:8765
Optional auth token must match --token.
"""
from __future__ import annotations

import argparse
import base64
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Any
from urllib.parse import parse_qs, urlparse

LOCK = threading.Lock()
FRAMES: list[dict[str, Any]] = []
AUTH_TOKEN = ""


def check_auth(headers) -> bool:
    if not AUTH_TOKEN:
        return True
    auth = headers.get("Authorization", "")
    return auth == f"Bearer {AUTH_TOKEN}"


class RelayHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt: str, *args) -> None:
        print(f"[relay] {self.address_string()} {fmt % args}", flush=True)

    def _read_json(self) -> dict | None:
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0:
            return None
        raw = self.rfile.read(length)
        try:
            return json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError:
            return None

    def _json_response(self, code: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self) -> None:
        if not check_auth(self.headers):
            self._json_response(401, {"error": "unauthorized"})
            return
        path = urlparse(self.path).path
        if path != "/v1/push":
            self._json_response(404, {"error": "not found"})
            return
        data = self._read_json()
        if not data:
            self._json_response(400, {"error": "bad json"})
            return
        device_id = str(data.get("deviceId", ""))
        frames = data.get("frames") or []
        stored = 0
        now = int(time.time() * 1000)
        with LOCK:
            for payload_b64 in frames:
                if not isinstance(payload_b64, str) or not payload_b64:
                    continue
                FRAMES.append(
                    {
                        "deviceId": device_id,
                        "payload": payload_b64,
                        "ts": now,
                        "id": f"{device_id}-{now}-{stored}",
                    }
                )
                stored += 1
                now += 1
            while len(FRAMES) > 5000:
                FRAMES.pop(0)
        self._json_response(200, {"stored": stored})

    def do_GET(self) -> None:
        if not check_auth(self.headers):
            self._json_response(401, {"error": "unauthorized"})
            return
        parsed = urlparse(self.path)
        if parsed.path != "/v1/pull":
            self._json_response(404, {"error": "not found"})
            return
        qs = parse_qs(parsed.query)
        since = int((qs.get("since") or ["0"])[0])
        limit = min(int((qs.get("limit") or ["64"])[0]), 256)
        device_id = (qs.get("deviceId") or [""])[0]
        with LOCK:
            candidates = [
                f for f in FRAMES
                if f["ts"] > since and f.get("deviceId") != device_id
            ]
            out = candidates[:limit]
        self._json_response(200, {"frames": out})


def main() -> None:
    global AUTH_TOKEN
    parser = argparse.ArgumentParser(description="MeshHood cellular relay (dev)")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--token", default="", help="Optional Bearer token")
    args = parser.parse_args()
    AUTH_TOKEN = args.token.strip()
    server = HTTPServer((args.host, args.port), RelayHandler)
    print(f"MeshHood relay listening on http://{args.host}:{args.port}", flush=True)
    if AUTH_TOKEN:
        print("Auth: Bearer token required", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.", flush=True)


if __name__ == "__main__":
    main()
