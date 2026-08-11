class IndiService:
    """POC abstraction. Replace stubs with a real INDI client in the next lot."""

    def list_devices(self) -> list[dict]:
        return [
            {"name": "POC Camera", "type": "camera", "connected": False},
            {"name": "POC Mount", "type": "mount", "connected": False},
        ]

    def capture(self, exposure_s: float) -> dict:
        return {
            "status": "simulated",
            "exposure_s": exposure_s,
            "image": "captures/poc_capture_0001.fits",
        }

    def goto(self, ra: float, dec: float) -> dict:
        return {"status": "simulated", "ra": ra, "dec": dec}


indi_service = IndiService()
