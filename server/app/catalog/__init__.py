from .bright_stars import (
    BRIGHT_STARS as _LEGACY_BRIGHT_STARS,
)
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
_stellar_migration = ensure_stellar_catalog(
    catalog_service.database
)

# CatalogService.ensure_builtin_bright_stars() remains as the v0.6.2 fallback.
# Once the complete v0.6.3 source is loaded, prevent that legacy seed from
# being re-added by later search/status calls.
if _stellar_migration.get("status") == "ready":
    _LEGACY_BRIGHT_STARS.clear()

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
