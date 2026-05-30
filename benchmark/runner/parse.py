def _ms(seconds):
    return round(seconds * 1000) if seconds is not None else None


def parse_oha(oha: dict) -> dict:
    summary = oha.get("summary", {})
    pct = oha.get("latencyPercentiles", {}) or {}
    return {
        "reqPerSec": summary.get("requestsPerSec"),
        "successRate": summary.get("successRate"),
        "p50Ms": _ms(pct.get("p50")),
        "p90Ms": _ms(pct.get("p90")),
        "p99Ms": _ms(pct.get("p99")),
    }


def _profile_compliant(profiles: list, needle: str):
    for p in profiles:
        name = (p.get("profile") or "").upper().replace(" ", "")
        if needle in name:
            return p.get("isCompliant")
    return None


def parse_validation(resp: dict) -> dict:
    profiles = resp.get("profiles", []) or []
    return {
        "compliant": resp.get("isCompliant"),
        "pdfA": _profile_compliant(profiles, "PDF/A"),
        "pdfUa": _profile_compliant(profiles, "PDF/UA"),
        "errorCount": (resp.get("summary") or {}).get("failedChecks"),
    }
