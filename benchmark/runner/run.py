import json
import os
import subprocess
import sys
import time
from pathlib import Path

import httpx

from parse import parse_oha, parse_validation

ENGINES = {
    "pdf-ua-api": os.environ.get("PDF_UA_API", "http://pdf-ua-api:8080"),
    "weasyprint": os.environ.get("WEASYPRINT", "http://weasyprint:8080"),
}
VALIDATOR = os.environ.get("PDF_UA_API", "http://pdf-ua-api:8080")
CONCURRENCY = int(os.environ.get("CONCURRENCY", "20"))
DURATION = os.environ.get("DURATION", "30s")
WARMUP = int(os.environ.get("WARMUP", "50"))
RUN_DATE = os.environ.get("RUN_DATE", "unknown")
CORPUS = Path("/corpus")
RESULTS = Path("/results")
DOCS = ["small", "medium", "large"]


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


def warmup(base: str, payload: bytes) -> None:
    with httpx.Client(timeout=60) as c:
        for _ in range(WARMUP):
            c.post(f"{base}/convert", content=payload,
                   headers={"Content-Type": "application/json"})


def run_oha(base: str, payload_file: Path) -> dict:
    cmd = [
        "oha", "--no-tui", "--json", "-z", DURATION, "-c", str(CONCURRENCY),
        "-m", "POST", "-T", "application/json", "-D", str(payload_file),
        f"{base}/convert",
    ]
    out = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return parse_oha(json.loads(out.stdout))


def convert_once(base: str, payload: bytes) -> bytes:
    r = httpx.post(f"{base}/convert", content=payload,
                   headers={"Content-Type": "application/json"}, timeout=120)
    r.raise_for_status()
    return r.content


def validate(pdf: bytes) -> dict:
    r = httpx.post(f"{VALIDATOR}/validate", content=pdf,
                   headers={"Content-Type": "application/pdf"}, timeout=120)
    r.raise_for_status()
    return parse_validation(r.json())


def main() -> int:
    for base in ENGINES.values():
        wait_healthy(base)

    results = []
    for doc in DOCS:
        html = (CORPUS / f"{doc}.html").read_text()
        payload = json.dumps({"html": html}).encode()
        payload_file = Path(f"/tmp/{doc}.json")
        payload_file.write_bytes(payload)

        for engine, base in ENGINES.items():
            print(f"==> {engine} / {doc}", file=sys.stderr)
            warmup(base, payload)
            latency = run_oha(base, payload_file)
            pdf = convert_once(base, payload)
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
            "weasyprintVersion": engine_version(ENGINES["weasyprint"]),
        },
        "results": results,
    }
    (RESULTS / "latest.json").write_text(json.dumps(artifact, indent=2) + "\n")
    print("wrote /results/latest.json", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
