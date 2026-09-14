from __future__ import annotations

import argparse
import json
from pathlib import Path
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE_DIR = ROOT / "catalog_sources"
MANIFEST_NAME = "SOURCE_MANIFEST.json"


def fetch_sources(source_dir: Path = DEFAULT_SOURCE_DIR) -> list[Path]:
    source_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = source_dir / MANIFEST_NAME

    manifest = json.loads(
        manifest_path.read_text(encoding="utf-8")
    )

    downloaded: list[Path] = []

    for source in manifest.get("sources", []):
        filename = source["file"]
        url = source["url"]
        destination = source_dir / filename

        request = Request(
            url,
            headers={
                "User-Agent": "StellarPilot-catalog-builder/0.6.3"
            },
        )

        with urlopen(request, timeout=90) as response:
            payload = response.read()

        if not payload:
            raise RuntimeError(
                f"Empty stellar source downloaded: {url}"
            )

        destination.write_bytes(payload)
        downloaded.append(destination)
        print(
            f"[StellarPilot] {filename}: {len(payload)} bytes"
        )

    return downloaded


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Fetch pinned StellarPilot stellar catalogue sources."
    )
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=DEFAULT_SOURCE_DIR,
    )
    args = parser.parse_args()

    fetched = fetch_sources(args.source_dir)
    print(
        f"[StellarPilot] stellar sources ready: {len(fetched)} files"
    )


if __name__ == "__main__":
    main()
