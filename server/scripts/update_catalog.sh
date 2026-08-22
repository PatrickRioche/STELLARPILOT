#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OPENNGC_COMMIT="36cb178"

echo "=== StellarPilot catalogue update ==="
echo "OpenNGC commit: $OPENNGC_COMMIT"
echo

mkdir -p data/openngc

echo "[1/4] Téléchargement OpenNGC"

curl -fL \
  "https://raw.githubusercontent.com/mattiaverga/OpenNGC/${OPENNGC_COMMIT}/database_files/NGC.csv" \
  -o data/openngc/NGC.csv

curl -fL \
  "https://raw.githubusercontent.com/mattiaverga/OpenNGC/${OPENNGC_COMMIT}/database_files/addendum.csv" \
  -o data/openngc/addendum.csv

echo
echo "[2/4] Construction SQLite"

python3 scripts/import_openngc.py

echo
echo "[3/4] Enrichissement français"

python3 scripts/enrich_catalog_fr.py

echo
echo "[4/4] Vérification"

python3 - <<'PY'
from app.catalog.service import catalog_service

status = catalog_service.status()

print("Status :", status["status"])
print("Objects:", status["object_count"])

for query in (
    "M31",
    "Tourbillon",
    "Trognon de pomme",
    "Pléiades",
):
    result = catalog_service.search(
        query,
        limit=1,
    )

    print(
        query,
        "=>",
        result["objects"][0]["name"]
        if result["objects"]
        else "NOT FOUND",
    )
PY

echo
echo "Catalogue StellarPilot mis à jour."
