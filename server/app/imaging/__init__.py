"""Imaging package initialization.

Import Bahtinov routes when the imaging package is loaded by app.main.  Keeping
this registration here avoids coupling the focus analyzer to plate-solving
quality logic while preserving the existing main.py route layout.
"""

from app.imaging import bahtinov as _bahtinov_routes  # noqa: F401,E402
