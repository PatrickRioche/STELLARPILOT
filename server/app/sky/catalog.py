from __future__ import annotations

from collections.abc import Iterator
from typing import Any

from app.catalog.bright_stars import (
    BRIGHT_STARS as FALLBACK_BRIGHT_STARS,
)
from app.catalog.service import catalog_service
from app.catalog.stellar_migration import (
    bright_stars_from_database,
)


class _BrightStarsProxy:
    """Expose alignment stars from the unified SQLite catalogue.

    v0.6.3 prefers the packaged stellar catalogue. The original v0.6.2
    31-star list remains a safe fallback when the full source package is not
    present (notably some isolated unit-test environments).
    """

    @staticmethod
    def _rows() -> list[dict[str, Any]]:
        rows = bright_stars_from_database(
            catalog_service.database,
            max_magnitude=2.5,
        )
        return rows or FALLBACK_BRIGHT_STARS

    def __iter__(self) -> Iterator[dict[str, Any]]:
        return iter(self._rows())

    def __len__(self) -> int:
        return len(self._rows())


BRIGHT_STARS = _BrightStarsProxy()

__all__ = ["BRIGHT_STARS"]
