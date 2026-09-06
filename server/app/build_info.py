import json
from pathlib import Path

BUILD_INFO_PATH = (
    Path(__file__).resolve().parents[1]
    / "BUILD_INFO.json"
)

def read_build_info() -> dict:
    try:
        return json.loads(
            BUILD_INFO_PATH.read_text(
                encoding="utf-8"
            )
        )
    except Exception:
        return {
            "service": "stellarpilot-server",
            "version": "unknown",
            "build_timestamp": None,
            "git_sha": None,
            "branch": None,
            "dirty": None,
        }
