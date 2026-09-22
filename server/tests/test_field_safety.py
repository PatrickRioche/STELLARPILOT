from __future__ import annotations

import pytest

from app.imaging import field_safety


def test_safe_correction_uses_live_mount_readback(monkeypatch):
    monkeypatch.setattr(
        field_safety.indi_service,
        "mount_status",
        lambda: {
            "status": "tracking",
            "ra": 18.2,
            "dec": 30.0,
            "coordinate_property": "EQUATORIAL_EOD_COORD",
        },
    )
    monkeypatch.setattr(
        field_safety,
        "mount_position_to_j2000",
        lambda **_kwargs: (18.2, 30.0),
    )

    result = field_safety.safe_centering_result(
        None,
        target_ra_hours=18.0,
        target_dec_deg=30.0,
        solve_ra_deg=269.0,
        solve_dec_deg=30.5,
        tolerance_arcsec=30.0,
    )

    # Measured error = +1 deg RA and -0.5 deg DEC relative to target.
    # The correction must be applied to the *current* mount command
    # (18.2 h / 30 deg), not to the catalogue target (18 h / 30 deg).
    assert result["status"] == "correction_required"
    assert result["automatic_correction_blocked"] is False
    assert result["correction_basis"] == (
        "live_mount_readback_j2000_plus_solve_error"
    )
    assert result["correction_ra_hours"] == pytest.approx(
        274.0 / 15.0,
        abs=1e-8,
    )
    assert result["correction_dec_deg"] == pytest.approx(29.5)


def test_safe_correction_blocks_large_move(monkeypatch):
    def unexpected_mount_read():
        raise AssertionError("mount readback must not be queried")

    monkeypatch.setattr(
        field_safety.indi_service,
        "mount_status",
        unexpected_mount_read,
    )

    result = field_safety.safe_centering_result(
        None,
        target_ra_hours=18.0,
        target_dec_deg=30.0,
        solve_ra_deg=260.0,
        solve_dec_deg=30.0,
        tolerance_arcsec=30.0,
    )

    assert result["status"] == "correction_required"
    assert result["correction_ra_hours"] is None
    assert result["correction_dec_deg"] is None
    assert result["automatic_correction_blocked"] is True
    assert result["automatic_correction_reason"].startswith(
        "correction_too_large"
    )


def test_safe_correction_blocks_near_celestial_pole(monkeypatch):
    result = field_safety.safe_centering_result(
        None,
        target_ra_hours=2.0,
        target_dec_deg=85.0,
        solve_ra_deg=29.5,
        solve_dec_deg=84.5,
        tolerance_arcsec=30.0,
    )

    assert result["status"] == "correction_required"
    assert result["correction_ra_hours"] is None
    assert result["correction_dec_deg"] is None
    assert result["automatic_correction_blocked"] is True
    assert result["automatic_correction_reason"] == "near_celestial_pole"


def test_centered_result_never_requests_a_goto(monkeypatch):
    def unexpected_mount_read():
        raise AssertionError("mount readback must not be queried")

    monkeypatch.setattr(
        field_safety.indi_service,
        "mount_status",
        unexpected_mount_read,
    )

    result = field_safety.safe_centering_result(
        None,
        target_ra_hours=18.0,
        target_dec_deg=30.0,
        solve_ra_deg=270.0,
        solve_dec_deg=30.0,
        tolerance_arcsec=30.0,
    )

    assert result["status"] == "centered"
    assert result["correction_ra_hours"] is None
    assert result["correction_dec_deg"] is None
    assert result["automatic_correction_blocked"] is False
