"""
HTTP server that receives commands from the Android app and passes them to mobile-use.
Run: python server.py
Then POST to http://<pi-ip>:8080/execute with JSON {"command": "..."}
"""

import json
import logging
from http.server import HTTPServer, BaseHTTPRequestHandler

from executor import execute

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

HOST = "0.0.0.0"
PORT = 8080


class CommandHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/execute":
            self._send(405, {"error": "Method not allowed"})
            return

        try:
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length)
            data = json.loads(body) if body else {}
            command = data.get("command", "")
        except json.JSONDecodeError as e:
            self._send(400, {"error": f"Invalid JSON: {e}"})
            return

        if not command:
            self._send(400, {"error": "Missing 'command' field"})
            return

        logger.info("Executing: %s", command[:80] + ("..." if len(command) > 80 else ""))
        success, message = execute(command)
        logger.info("Result: success=%s", success)

        status = 200 if success else 500
        self._send(status, {"success": success, "message": message})

    def _send(self, status: int, data: dict):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def log_message(self, format, *args):
        logger.info("%s - %s", self.address_string(), format % args)


def main():
    server = HTTPServer((HOST, PORT), CommandHandler)
    logger.info("Yooni Pi server listening on %s:%d", HOST, PORT)
    server.serve_forever()


if __name__ == "__main__":
    main()