from .constellations import (
    CONSTELLATIONS_FR,
    constellation_fr,
)
from .service import (
    CatalogService,
    catalog_service,
)
from .stellar_migration import (
    bright_stars_from_database,
    ensure_stellar_catalog,
    stellar_catalog_status,
)
from .types import (
    normalize_object_type,
)

# The Raspberry Pi keeps catalog.sqlite3 in persistent data. When the v0.6.3
# update kit contains the pinned stellar sources, enrich that existing database
# in place. Without the packaged sources (for example a minimal local unit
# test), the v0.6.2 built-in bright-star fallback remains available.
ensure_stellar_catalog(
    catalog_service.database
)

__all__ = [
    "CONSTELLATIONS_FR",
    "constellation_fr",
    "CatalogService",
    "catalog_service",
    "bright_stars_from_database",
    "ensure_stellar_catalog",
    "stellar_catalog_status",
    "normalize_object_type",
]
