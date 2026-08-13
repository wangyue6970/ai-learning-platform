import os
import sys
import json

# Keep local OCR models in this project instead of the user's home directory.
# These must be set before PaddleOCR is imported.
os.environ.setdefault("PADDLE_HOME", os.path.join(os.path.dirname(__file__), "cache"))
os.environ.setdefault("PADDLE_PDX_CACHE_HOME", os.environ["PADDLE_HOME"])

from paddleocr import PaddleOCR


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python ocr_image.py <image-path>")

    # The Java backend will read this program's output as UTF-8.
    sys.stdout.reconfigure(encoding="utf-8")

    ocr = PaddleOCR(
        use_doc_orientation_classify=False,
        use_doc_unwarping=False,
        use_textline_orientation=False,
        engine="paddle",
        # Phone photos can be very large. Limiting the long edge keeps CPU
        # inference stable while retaining enough detail for exam text.
        text_det_limit_side_len=1920,
        text_det_limit_type="max",
    )

    texts = []
    for result in ocr.predict(sys.argv[1]):
        texts.extend(result.json["res"]["rec_texts"])

    # Java only reads this tagged JSON line. PaddleOCR diagnostic logs are ignored.
    print("__OCR_RESULT__" + json.dumps({"text": "\n".join(texts)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
