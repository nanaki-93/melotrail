"""Serve the static frontend for local development."""

from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import argparse
import os


PROJECT_ROOT = Path(__file__).resolve().parents[1]
STATIC_DIR = PROJECT_ROOT / "src" / "main" / "resources" / "static"


def main() -> None:
    parser = argparse.ArgumentParser(description="AI Music Workstation frontend server")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3000)
    args = parser.parse_args()

    os.chdir(STATIC_DIR)
    server = ThreadingHTTPServer((args.host, args.port), SimpleHTTPRequestHandler)
    print(f"Frontend: http://{args.host}:{args.port}/index.html")
    print(f"Serving:  {STATIC_DIR}")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping frontend server")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
