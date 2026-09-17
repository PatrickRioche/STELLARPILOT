from __future__ import annotations

import subprocess
import threading
from time import perf_counter
from typing import Any


_lock = threading.Lock()
_cancelled_sessions: set[str] = set()
_active_session: str | None = None


def begin_solve(session_id: str) -> bool:
    """Reserve the single local astrometry slot for one capture session."""
    global _active_session
    with _lock:
        if _active_session is not None and _active_session != session_id:
            return False
        _active_session = session_id
        _cancelled_sessions.discard(session_id)
        return True


def request_cancel(session_id: str) -> bool:
    """Cancel only the StellarPilot solve that owns the active slot."""
    with _lock:
        if _active_session != session_id:
            return False
        _cancelled_sessions.add(session_id)

    # One StellarPilot solve is allowed at a time. Terminating solve-field here
    # therefore cannot kill a second StellarPilot solve. The cancellation flag
    # prevents the next fallback strategy from starting.
    try:
        subprocess.run(
            ["pkill", "-TERM", "-f", "solve-field"],
            capture_output=True,
            text=True,
            timeout=3,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        pass
    return True


def is_cancelled(session_id: str) -> bool:
    with _lock:
        return session_id in _cancelled_sessions


def finish_solve(session_id: str) -> None:
    global _active_session
    with _lock:
        _cancelled_sessions.discard(session_id)
        if _active_session == session_id:
            _active_session = None


def solve_robust_cancellable(
    session_id: str,
    solver: Any,
    image: str,
    ra_hint: float | None = None,
    dec_hint: float | None = None,
    expected_scale_arcsec: float = 1.218,
) -> dict[str, Any]:
    """Run the field-tested three-strategy plate solve with cancellation.

    Field tests on 2026-09-17 proved that downsample=2 changes the Uranus-C
    RAW16 frame from repeated multi-minute failures to a ~2.7 s solve. Every
    strategy therefore keeps downsample=2, and only one solve may run at once.
    """
    if not begin_solve(session_id):
        return {
            "status": "busy",
            "solver": "astrometry.net",
            "image": image,
            "strategy": "single_flight",
            "attempts": [],
            "total_duration_s": 0.0,
            "detail": "Une autre astrométrie StellarPilot est déjà en cours",
        }

    attempts: list[dict[str, Any]] = []
    total_start = perf_counter()

    try:
        if not callable(getattr(solver, "solve", None)):
            result = solver.solve_robust(
                image,
                ra_hint=ra_hint,
                dec_hint=dec_hint,
            )
            if is_cancelled(session_id):
                return _cancelled_result(image, attempts, total_start)
            return result

        narrow_low = expected_scale_arcsec * 0.74
        narrow_high = expected_scale_arcsec * 1.24

        strategies = [
            {
                "name": "scale_narrow_position",
                "scale_low": narrow_low,
                "scale_high": narrow_high,
                "radius": 8.0,
                "timeout": 15,
                "use_position": True,
            },
            {
                "name": "scale_narrow_blind",
                "scale_low": narrow_low,
                "scale_high": narrow_high,
                "radius": None,
                "timeout": 30,
                "use_position": False,
            },
            {
                "name": "scale_wide_blind",
                "scale_low": 0.50,
                "scale_high": 2.50,
                "radius": None,
                "timeout": 30,
                "use_position": False,
            },
        ]

        for strategy in strategies:
            if is_cancelled(session_id):
                return _cancelled_result(image, attempts, total_start)

            use_position = (
                bool(strategy["use_position"])
                and ra_hint is not None
                and dec_hint is not None
            )

            if strategy["use_position"] and not use_position:
                continue

            attempt_start = perf_counter()
            result = solver.solve(
                image=image,
                ra_hint=ra_hint if use_position else None,
                dec_hint=dec_hint if use_position else None,
                radius_deg=strategy["radius"] if use_position else None,
                downsample=2,
                scale_low_arcsec=strategy["scale_low"],
                scale_high_arcsec=strategy["scale_high"],
                timeout_s=strategy["timeout"],
            )

            attempts.append(
                {
                    "strategy": strategy["name"],
                    "status": result.get("status"),
                    "duration_s": round(perf_counter() - attempt_start, 3),
                    "position_hint_used": use_position,
                }
            )

            if is_cancelled(session_id):
                return _cancelled_result(image, attempts, total_start)

            if result.get("status") == "solved":
                result["strategy"] = strategy["name"]
                result["attempts"] = attempts
                result["total_duration_s"] = round(
                    perf_counter() - total_start,
                    3,
                )
                return result

            if result.get("status") in {"error", "busy", "cancelled"}:
                result["strategy"] = strategy["name"]
                result["attempts"] = attempts
                result["total_duration_s"] = round(
                    perf_counter() - total_start,
                    3,
                )
                return result

        return {
            "status": "unsolved",
            "solver": "astrometry.net",
            "image": image,
            "strategy": "exhausted",
            "attempts": attempts,
            "total_duration_s": round(perf_counter() - total_start, 3),
            "detail": "Toutes les stratégies ont échoué",
        }
    finally:
        finish_solve(session_id)


def _cancelled_result(
    image: str,
    attempts: list[dict[str, Any]],
    total_start: float,
) -> dict[str, Any]:
    return {
        "status": "cancelled",
        "solver": "astrometry.net",
        "image": image,
        "strategy": "cancelled",
        "attempts": attempts,
        "total_duration_s": round(perf_counter() - total_start, 3),
        "detail": "Résolution astrométrique arrêtée par l'utilisateur",
    }
