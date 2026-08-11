class PlateSolverService:
    """POC solver abstraction. Real ASTAP/astrometry.net integration comes next."""

    def solve(self, image: str) -> dict:
        return {
            "status": "simulated",
            "image": image,
            "ra": 0.0,
            "dec": 90.0,
            "orientation_deg": 0.0,
            "solver": "stub",
        }


plate_solver = PlateSolverService()
