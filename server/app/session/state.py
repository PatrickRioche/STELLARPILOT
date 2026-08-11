from dataclasses import dataclass
from typing import Optional


@dataclass
class SessionState:
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    altitude: Optional[float] = None
    timestamp: Optional[str] = None
    mount_type: Optional[str] = None


state = SessionState()
