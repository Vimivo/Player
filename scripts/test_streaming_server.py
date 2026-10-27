#!/usr/bin/env python3
"""
Lightweight local test streaming server for Vimivo QA & verification.
Serves:
  - HTTP files and byte ranges
  - HLS playlists (.m3u8) and TS video segments (.ts)
  - MPEG-DASH manifest (.mpd) and init/media segments (.m4s)
  - Token-authenticated endpoints (/stream?token=secret)
  - Mock RTSP server (OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN) on a configurable port.

Usage:
  python3 scripts/test_streaming_server.py [--port 8088] [--rtsp-port 8554] [--media-dir testmedia/pack]
"""

import argparse
import http.server
import os
import socket
import socketserver
import sys
import threading
import time
from urllib.parse import parse_qs, urlparse


class StreamingHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Range, Authorization")
        self.send_header("Access-Control-Expose-Headers", "Content-Range, Content-Length, Accept-Ranges")
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)

        # Token-authenticated stream test
        if parsed.path.startswith("/protected/"):
            token = params.get("token", [""])[0]
            if token != "vimivo_secret_token_123":
                self.send_response(401)
                self.send_header("Content-Type", "text/plain")
                self.end_headers()
                self.wfile.write(b"401 Unauthorized: Invalid or missing token\n")
                return
            # Strip protected prefix to serve underlying file
            self.path = parsed.path.replace("/protected", "")

        super().do_GET()


class MockRTSPServer:
    """Minimal single-threaded mock RTSP server to satisfy client probe/handshake tests."""

    def __init__(self, host="127.0.0.1", port=8554):
        self.host = host
        self.port = port
        self.sock = None
        self.running = False
        self.thread = None

    def start(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind((self.host, self.port))
        self.sock.listen(5)
        self.running = True
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def stop(self):
        self.running = False
        if self.sock:
            try:
                self.sock.close()
            except Exception:
                pass

    def _run(self):
        while self.running:
            try:
                client, _ = self.sock.accept()
                threading.Thread(target=self._handle_client, args=(client,), daemon=True).start()
            except Exception:
                break

    def _handle_client(self, client):
        client.settimeout(5.0)
        try:
            buf = b""
            while self.running:
                data = client.recv(2048)
                if not data:
                    break
                buf += data
                while b"\r\n\r\n" in buf:
                    req_line, rest = buf.split(b"\r\n\r\n", 1)
                    buf = rest
                    lines = req_line.decode("utf-8", errors="replace").split("\r\n")
                    if not lines or not lines[0]:
                        continue
                    tokens = lines[0].split()
                    method = tokens[0] if tokens else "OPTIONS"
                    cseq = "1"
                    for line in lines[1:]:
                        if line.lower().startswith("cseq:"):
                            cseq = line.split(":", 1)[1].strip()

                    if method == "OPTIONS":
                        resp = (
                            f"RTSP/1.0 200 OK\r\n"
                            f"CSeq: {cseq}\r\n"
                            f"Public: OPTIONS, DESCRIBE, SETUP, TEARDOWN, PLAY\r\n\r\n"
                        )
                    elif method == "DESCRIBE":
                        sdp = (
                            "v=0\r\n"
                            "o=- 0 0 IN IP4 127.0.0.1\r\n"
                            "s=Vimivo Test RTSP Stream\r\n"
                            "t=0 0\r\n"
                            "m=video 0 RTP/AVP 96\r\n"
                            "a=rtpmap:96 H264/90000\r\n"
                            "a=control:track1\r\n"
                        )
                        resp = (
                            f"RTSP/1.0 200 OK\r\n"
                            f"CSeq: {cseq}\r\n"
                            f"Content-Type: application/sdp\r\n"
                            f"Content-Length: {len(sdp)}\r\n\r\n"
                            f"{sdp}"
                        )
                    elif method == "SETUP":
                        resp = (
                            f"RTSP/1.0 200 OK\r\n"
                            f"CSeq: {cseq}\r\n"
                            f"Session: VIMIVOTESTSESSION42;timeout=60\r\n"
                            f"Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n\r\n"
                        )
                    elif method == "PLAY":
                        resp = (
                            f"RTSP/1.0 200 OK\r\n"
                            f"CSeq: {cseq}\r\n"
                            f"Session: VIMIVOTESTSESSION42\r\n"
                            f"Range: npt=0.000-\r\n\r\n"
                        )
                    elif method == "TEARDOWN":
                        resp = (
                            f"RTSP/1.0 200 OK\r\n"
                            f"CSeq: {cseq}\r\n"
                            f"Session: VIMIVOTESTSESSION42\r\n\r\n"
                        )
                        client.sendall(resp.encode("utf-8"))
                        break
                    else:
                        resp = f"RTSP/1.0 200 OK\r\nCSeq: {cseq}\r\n\r\n"
                    client.sendall(resp.encode("utf-8"))
        except Exception:
            pass
        finally:
            try:
                client.close()
            except Exception:
                pass


def main():
    parser = argparse.ArgumentParser(description="Vimivo QA Local Streaming Test Server")
    parser.add_argument("--port", type=int, default=8088, help="HTTP/HLS/DASH port (default: 8088)")
    parser.add_argument("--rtsp-port", type=int, default=8554, help="Mock RTSP port (default: 8554)")
    parser.add_argument("--media-dir", default="testmedia/pack", help="Directory with media assets")
    parser.add_argument("--daemon", action="store_true", help="Run indefinitely in background")
    args = parser.parse_args()

    media_dir = os.path.abspath(args.media_dir)
    if not os.path.isdir(media_dir):
        print(f"Error: media directory '{media_dir}' does not exist", file=sys.stderr)
        sys.exit(1)

    os.chdir(media_dir)

    httpd = socketserver.TCPServer(("", args.port), StreamingHTTPRequestHandler)
    rtsp = MockRTSPServer(port=args.rtsp_port)
    rtsp.start()

    print(f"Vimivo Test Streaming Server started:")
    print(f"  HTTP: http://127.0.0.1:{args.port}/")
    print(f"  HLS:  http://127.0.0.1:{args.port}/hls/master.m3u8")
    print(f"  DASH: http://127.0.0.1:{args.port}/dash/manifest.mpd")
    print(f"  Auth: http://127.0.0.1:{args.port}/protected/h264.mp4?token=vimivo_secret_token_123")
    print(f"  RTSP: rtsp://127.0.0.1:{args.rtsp_port}/live")
    sys.stdout.flush()

    try:
        if args.daemon:
            httpd.serve_forever()
        else:
            # Run for 10 seconds unless interrupted
            t = threading.Thread(target=httpd.serve_forever, daemon=True)
            t.start()
            time.sleep(10)
    except KeyboardInterrupt:
        pass
    finally:
        httpd.shutdown()
        rtsp.stop()


if __name__ == "__main__":
    main()
