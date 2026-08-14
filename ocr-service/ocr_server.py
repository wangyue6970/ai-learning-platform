import json
import os
import sys
import tempfile
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

# Keep local OCR models in this project instead of the user's home directory.
# These must be set before PaddleOCR is imported.
os.environ.setdefault("PADDLE_HOME", os.path.join(os.path.dirname(__file__), "cache"))
os.environ.setdefault("PADDLE_PDX_CACHE_HOME", os.environ["PADDLE_HOME"])

from paddleocr import PaddleOCR


def create_ocr():
    return PaddleOCR(
        use_doc_orientation_classify=False,
        use_doc_unwarping=False,
        use_textline_orientation=False,
        engine="paddle",
        # Phone photos can be very large. Limiting the long edge keeps CPU
        # inference stable while retaining enough detail for exam text.
        text_det_limit_side_len=1920,
        text_det_limit_type="max",
    )


# This is intentionally created once when the server starts. Every later image
# reuses the same model already held in memory.
OCR = create_ocr()


def recognize_image(image_path):
    texts = []
    for result in OCR.predict(str(image_path)):
        texts.extend(result.json["res"]["rec_texts"])
    return "\n".join(texts).strip()


class OcrRequestHandler(BaseHTTPRequestHandler):
    max_image_bytes = 20 * 1024 * 1024

    def do_GET(self):
        if self.path != "/health":
            self.send_json(404, {"error": "Not found"})
            return
        self.send_json(200, {"status": "ok"})

    def do_POST(self):
        if self.path != "/recognize":
            self.send_json(404, {"error": "Not found"})
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            content_length = 0

        if content_length <= 0:
            self.send_json(400, {"error": "未收到图片内容"})
            return
        if content_length > self.max_image_bytes:
            self.send_json(413, {"error": "图片超过 20MB 限制"})
            return

        suffix = self.headers.get("X-Image-Suffix", ".jpg")
        if suffix.lower() not in {".jpg", ".jpeg", ".png", ".webp", ".bmp"}:
            suffix = ".jpg"

        temp_path = None
        try:
            with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as temp_file:
                temp_file.write(self.rfile.read(content_length))
                temp_path = Path(temp_file.name)

            text = recognize_image(temp_path)
            if not text:
                self.send_json(422, {"error": "图片未识别出可用文字"})
                return
            self.send_json(200, {"text": text})
        except Exception as exception:
            self.send_json(500, {"error": f"图片识别失败：{exception}"})
        finally:
            if temp_path:
                temp_path.unlink(missing_ok=True)

    def send_json(self, status_code, body):
        payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format_string, *args):
        # Java's backend terminal is already enough for useful startup errors.
        return


def main():
    port = int(sys.argv[1]) if len(sys.argv) == 2 else 8765
    server = HTTPServer(("127.0.0.1", port), OcrRequestHandler)
    server.serve_forever()


if __name__ == "__main__":
    main()
