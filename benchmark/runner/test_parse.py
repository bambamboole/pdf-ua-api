from parse import parse_oha, parse_validation


def test_parse_oha_extracts_throughput_and_percentiles():
    oha = {
        "summary": {"requestsPerSec": 42.5, "successRate": 1.0},
        "latencyPercentiles": {"p50": 0.41, "p90": 0.60, "p99": 0.72},
    }
    out = parse_oha(oha)
    assert out["reqPerSec"] == 42.5
    assert out["successRate"] == 1.0
    assert out["p50Ms"] == 410
    assert out["p90Ms"] == 600
    assert out["p99Ms"] == 720


def test_parse_oha_tolerates_missing_percentiles():
    out = parse_oha({"summary": {"requestsPerSec": 1.0, "successRate": 0.5}})
    assert out["reqPerSec"] == 1.0
    assert out["p99Ms"] is None


def test_parse_validation_reads_overall_and_profiles():
    resp = {
        "isCompliant": True,
        "summary": {"failedChecks": 0},
        "profiles": [
            {"profile": "PDF/A-3A", "isCompliant": True},
            {"profile": "PDF/UA-1", "isCompliant": True},
        ],
    }
    out = parse_validation(resp)
    assert out["compliant"] is True
    assert out["pdfA"] is True
    assert out["pdfUa"] is True
    assert out["errorCount"] == 0


def test_parse_validation_detects_failure():
    resp = {
        "isCompliant": False,
        "summary": {"failedChecks": 7},
        "profiles": [
            {"profile": "PDF/A-3A", "isCompliant": True},
            {"profile": "PDF/UA-1", "isCompliant": False},
        ],
    }
    out = parse_validation(resp)
    assert out["compliant"] is False
    assert out["pdfA"] is True
    assert out["pdfUa"] is False
    assert out["errorCount"] == 7
