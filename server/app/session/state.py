from dataclasses import dataclass
from typing import Optional


@dataclass
class SessionState:
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    altitude: Optional[float] = None
    timestamp: Optional[str] = None
    mount_type: Optional[str] = None

    # Le Pi5 StellarPilot ne possede pas de RTC batterie.
    # Android fournit une ancre UTC, puis monotonic() maintient
    # le temps de session tant que le serveur reste allume.
    client_time_epoch_s: Optional[float] = None
    client_time_monotonic_s: Optional[float] = None
    client_timezone_offset_minutes: Optional[int] = None

    # TIME_UTC publie par le pilote OnStep est un point de consigne INDI :
    # il reste fige a la derniere valeur ecrite et ne doit pas etre traite
    # comme une horloge temps reel. On memorise donc la derniere synchro
    # explicitement ecrite et verifiee par readback pendant cette session.
    mount_time_sync_reference_utc: Optional[str] = None
    mount_time_sync_mount_utc: Optional[str] = None
    mount_time_sync_source: Optional[str] = None
    mount_time_sync_offset_minutes: Optional[int] = None
    mount_time_sync_monotonic_s: Optional[float] = None


state = SessionState()
