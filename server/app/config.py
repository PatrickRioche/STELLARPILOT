import os


def get_mode() -> str:
    mode = os.getenv("STELLARPILOT_MODE", "simulation").strip().lower()
    return "device" if mode == "device" else "simulation"
