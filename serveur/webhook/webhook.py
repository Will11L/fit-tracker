"""GitHub webhook -> Pi auto-deploy (T3.1).

Listens on 127.0.0.1:8001 for POST /webhook/deploy from GitHub.
Verifies HMAC-SHA256 signature, checks ref == refs/heads/main, then
launches deploy.sh in background (non-blocking, survives webhook restart).

Caddy reverse-proxies the public path /webhook/deploy on
<public-dns> to this loopback service.

Secret loaded from env var GITHUB_WEBHOOK_SECRET (set via systemd
EnvironmentFile=/home/william/.config/sportapi-webhook.env, perms 600).

Logs go to stdout -> systemd journal (journalctl -u sportapi-webhook).
"""

import hmac
import json
import os
import subprocess
import sys
from hashlib import sha256
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

HOST = "127.0.0.1"
PORT = 8001
PATH = "/webhook/deploy"
DEPLOY_SCRIPT = Path(__file__).resolve().parent.parent / "deploy.sh"


def log(msg: str) -> None:
    print(msg, flush=True)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        log(f"http {self.address_string()} {fmt % args}")

    def _reply(self, code: int, msg: str) -> None:
        self.send_response(code)
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(msg.encode())

    def do_POST(self):
        if self.path != PATH:
            return self._reply(404, "not found")

        secret = os.environ.get("GITHUB_WEBHOOK_SECRET")
        if not secret:
            log("ERROR: GITHUB_WEBHOOK_SECRET not set")
            return self._reply(500, "server misconfigured")

        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)

        sig_header = self.headers.get("X-Hub-Signature-256", "")
        expected = "sha256=" + hmac.new(secret.encode(), body, sha256).hexdigest()
        if not hmac.compare_digest(sig_header, expected):
            log(f"reject: bad signature (got {sig_header[:20]}...)")
            return self._reply(403, "bad signature")

        event = self.headers.get("X-GitHub-Event", "")
        if event == "ping":
            log("ping ok")
            return self._reply(200, "pong")
        if event != "push":
            log(f"ignore event={event}")
            return self._reply(200, "ignored")

        try:
            payload = json.loads(body)
        except json.JSONDecodeError:
            return self._reply(400, "bad json")

        ref = payload.get("ref", "")
        if ref != "refs/heads/main":
            log(f"ignore ref={ref}")
            return self._reply(200, "ignored")

        commit = payload.get("after", "?")[:7]
        log(f"deploy: launching {DEPLOY_SCRIPT.name} for commit {commit}")
        subprocess.Popen(
            ["bash", str(DEPLOY_SCRIPT)],
            cwd=str(DEPLOY_SCRIPT.parent),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            start_new_session=True,
        )
        return self._reply(200, "deploy launched")


def main() -> int:
    if not DEPLOY_SCRIPT.is_file():
        log(f"FATAL: deploy script not found at {DEPLOY_SCRIPT}")
        return 1
    server = HTTPServer((HOST, PORT), Handler)
    log(f"listening on {HOST}:{PORT}{PATH}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        log("shutdown")
    return 0


if __name__ == "__main__":
    sys.exit(main())
