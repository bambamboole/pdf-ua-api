import json
import os
import subprocess
import sys
import time
from pathlib import Path

import httpx

from parse import parse_oha, parse_validation

ENGINES = {
    "pdf-ua-api": {
        "base": os.environ.get("PDF_UA_API", "http://pdf-ua-api:8080"),
        "kind": "json",
    },
    "weasyprint": {
        "base": os.environ.get("WEASYPRINT", "http://weasyprint:8080"),
        "kind": "json",
    },
    "gotenberg-chromium": {
        "base": os.environ.get("GOTENBERG", "http://gotenberg:3000"),
        "kind": "multipart",
        "endpoint": "/forms/chromium/convert/html",
        "filename": "index.html",
        "fields": {
            "preferCssPageSize": "true",
            "printBackground": "true",
        },
    },
    "gotenberg-libreoffice": {
        "base": os.environ.get("GOTENBERG", "http://gotenberg:3000"),
        "kind": "multipart",
        "endpoint": "/forms/libreoffice/convert",
        "filename": "document.html",
        "fields": {},
    },
}
VALIDATOR = os.environ.get("PDF_UA_API", "http://pdf-ua-api:8080")
CONCURRENCY = int(os.environ.get("CONCURRENCY", "20"))
DURATION = os.environ.get("DURATION", "30s")
WARMUP = int(os.environ.get("WARMUP", "50"))
RUN_DATE = os.environ.get("RUN_DATE", "unknown")
PDF_UA_EMBED_COLOR_PROFILE = os.environ.get("PDF_UA_EMBED_COLOR_PROFILE", "false").lower() == "true"
CORPUS = Path("/corpus")
RESULTS = Path("/results")
DOCS = ["small", "medium", "large"]
MULTIPART_BOUNDARY = "----pdfUaBenchmarkBoundary"


def wait_healthy(base: str, timeout: int = 120) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            if httpx.get(f"{base}/health", timeout=5).status_code == 200:
                return
        except httpx.HTTPError:
            pass
        time.sleep(2)
    raise RuntimeError(f"service never became healthy: {base}")


def engine_version(base: str) -> str:
    try:
        return httpx.get(f"{base}/health", timeout=5).json().get("weasyprintVersion", "n/a")
    except (httpx.HTTPError, ValueError):
        return "n/a"


def gotenberg_version(base: str) -> str:
    try:
        return httpx.get(f"{base}/version", timeout=5).text.strip()
    except httpx.HTTPError:
        return "n/a"


def warmup_json(base: str, payload: bytes) -> None:
    with httpx.Client(timeout=60) as c:
        for _ in range(WARMUP):
            c.post(f"{base}/convert", content=payload,
                   headers={"Content-Type": "application/json"})


def warmup_multipart(base: str, endpoint: str, payload: bytes, content_type: str) -> None:
    with httpx.Client(timeout=60) as c:
        for _ in range(WARMUP):
            c.post(
                f"{base}{endpoint}",
                content=payload,
                headers={"Content-Type": content_type},
            )


def run_oha_json(base: str, payload_file: Path) -> dict:
    cmd = [
        "oha", "--no-tui", "--json", "-z", DURATION, "-c", str(CONCURRENCY),
        "-m", "POST", "-T", "application/json", "-D", str(payload_file),
        f"{base}/convert",
    ]
    out = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return parse_oha(json.loads(out.stdout))


def run_oha_multipart(
    base: str,
    endpoint: str,
    payload_file: Path,
    content_type: str,
) -> dict:
    cmd = [
        "oha", "--no-tui", "--json", "-z", DURATION, "-c", str(CONCURRENCY),
        "-m", "POST", "-T", content_type, "-D", str(payload_file),
        f"{base}{endpoint}",
    ]
    out = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return parse_oha(json.loads(out.stdout))


def convert_once_json(base: str, payload: bytes) -> bytes:
    r = httpx.post(f"{base}/convert", content=payload,
                   headers={"Content-Type": "application/json"}, timeout=120)
    r.raise_for_status()
    return r.content


def convert_once_multipart(base: str, endpoint: str, payload: bytes, content_type: str) -> bytes:
    r = httpx.post(
        f"{base}{endpoint}",
        content=payload,
        headers={"Content-Type": content_type},
        timeout=120,
    )
    r.raise_for_status()
    return r.content


def payload_for(engine: str, html: str) -> bytes:
    payload = {"html": html}
    if engine == "pdf-ua-api":
        payload["embedColorProfile"] = PDF_UA_EMBED_COLOR_PROFILE
    return json.dumps(payload).encode()


def multipart_payload_for(html: str, filename: str, fields: dict[str, str]) -> tuple[bytes, str]:
    content_type = f"multipart/form-data; boundary={MULTIPART_BOUNDARY}"
    parts = [
        (
            f'Content-Disposition: form-data; name="files"; filename="{filename}"\r\n'
            "Content-Type: text/html\r\n\r\n"
            f"{html}\r\n"
        ),
    ]
    for key, value in fields.items():
        parts.append(f'Content-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n')

    body = "".join(f"--{MULTIPART_BOUNDARY}\r\n{part}" for part in parts)
    body += f"--{MULTIPART_BOUNDARY}--\r\n"
    return body.encode(), content_type


def validate(pdf: bytes) -> dict:
    r = httpx.post(f"{VALIDATOR}/validate", content=pdf,
                   headers={"Content-Type": "application/pdf"}, timeout=120)
    r.raise_for_status()
    return parse_validation(r.json())


def main() -> int:
    for engine in ENGINES.values():
        wait_healthy(engine["base"])

    results = []
    for doc in DOCS:
        html = (CORPUS / f"{doc}.html").read_text()

        for engine, config in ENGINES.items():
            base = config["base"]
            print(f"==> {engine} / {doc}", file=sys.stderr)
            if config["kind"] == "multipart":
                payload, content_type = multipart_payload_for(
                    html,
                    config["filename"],
                    config["fields"],
                )
                payload_file = Path(f"/tmp/{doc}-{engine}.multipart")
                payload_file.write_bytes(payload)
                warmup_multipart(base, config["endpoint"], payload, content_type)
                latency = run_oha_multipart(base, config["endpoint"], payload_file, content_type)
                pdf = convert_once_multipart(base, config["endpoint"], payload, content_type)
            else:
                payload = payload_for(engine, html)
                payload_file = Path(f"/tmp/{doc}-{engine}.json")
                payload_file.write_bytes(payload)
                warmup_json(base, payload)
                latency = run_oha_json(base, payload_file)
                pdf = convert_once_json(base, payload)
            compliance = validate(pdf)
            results.append({
                "doc": doc, "engine": engine,
                **latency,
                **compliance,
                "sizeBytes": len(pdf),
            })

    RESULTS.mkdir(parents=True, exist_ok=True)
    artifact = {
        "meta": {
            "date": RUN_DATE,
            "concurrency": CONCURRENCY,
            "duration": DURATION,
            "warmupRequests": WARMUP,
            "pdfUaEmbedColorProfile": PDF_UA_EMBED_COLOR_PROFILE,
            "weasyprintVersion": engine_version(ENGINES["weasyprint"]["base"]),
            "gotenbergVersion": gotenberg_version(ENGINES["gotenberg-chromium"]["base"]),
        },
        "results": results,
    }
    (RESULTS / "latest.json").write_text(json.dumps(artifact, indent=2) + "\n")
    print("wrote /results/latest.json", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
