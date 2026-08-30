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


state = SessionState()
