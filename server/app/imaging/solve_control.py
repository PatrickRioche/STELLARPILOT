from __future__ import annotations

import subprocess
import threading
from time import perf_counter
from typing import Any


_lock = threading.Lock()
_cancelled_sessions: set[str] = set()


def begin_solve(session_id: str) -> None:
    with _lock:
        _cancelled_sessions.discard(session_id)


def request_cancel(session_id: str) -> None:
    with _lock:
        _cancelled_sessions.add(session_id)

    # StellarPilot uses one local astrometry.net solve at a time during the
    # field workflow. Terminate the active solve-field process so the blocking
    # subprocess call returns immediately; the cancellation flag prevents the
    # next fallback strategy from starting.
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


def is_cancelled(session_id: str) -> bool:
    with _lock:
        return session_id in _cancelled_sessions


def finish_solve(session_id: str) -> None:
    with _lock:
        _cancelled_sessions.discard(session_id)


def solve_robust_cancellable(
    session_id: str,
    solver: Any,
    image: str,
    ra_hint: float | None = None,
    dec_hint: float | None = None,
    expected_scale_arcsec: float = 1.218,
) -> dict[str, Any]:
    """Run plate solving with explicit field-test cancellation support.

    Production ``PlateSolverService`` exposes ``solve`` and therefore uses the
    cancellable three-strategy sequence below. Unit-test or alternate solver
    doubles that expose only ``solve_robust`` retain the historical contract;
    this keeps the centering boundary independently testable without coupling
    tests to astrometry.net process-management internals.
    """
    begin_solve(session_id)
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
                "timeout": 20,
                "use_position": True,
            },
            {
                "name": "scale_narrow_blind",
                "scale_low": narrow_low,
                "scale_high": narrow_high,
                "radius": None,
                "timeout": 90,
                "use_position": False,
            },
            {
                "name": "scale_wide_blind",
                "scale_low": 0.50,
                "scale_high": 2.50,
                "radius": None,
                "timeout": 120,
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

            if result.get("status") == "error":
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
