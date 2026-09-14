from types import SimpleNamespace

import pytest

from app.indi.service import IndiService


MOUNT = "LX200 OnStep"


def test_explicit_goto_helper_unparks_and_verifies(monkeypatch):
    service = IndiService()
    outputs = iter([
        f"{MOUNT}.TELESCOPE_PARK.PARK=On\n"
        f"{MOUNT}.TELESCOPE_PARK.UNPARK=Off",
        f"{MOUNT}.TELESCOPE_PARK.PARK=Off\n"
        f"{MOUNT}.TELESCOPE_PARK.UNPARK=On",
    ])
    commands = []

    monkeypatch.setattr(service, "_park_output", lambda _mount: next(outputs))
    monkeypatch.setattr("app.indi.service.time.sleep", lambda _delay: None)

    def fake_run(command, **_kwargs):
        commands.append(command)
        return SimpleNamespace(returncode=0, stdout="", stderr="")

    monkeypatch.setattr("app.indi.service.subprocess.run", fake_run)

    result = service.ensure_unparked(MOUNT)

    assert result == {
        "status": "ready",
        "supported": True,
        "changed": True,
        "parked_before": True,
        "detail": None,
    }
    assert any(
        f"{MOUNT}.TELESCOPE_PARK.UNPARK=On" in command
        for command in commands
    )


def test_explicit_goto_helper_does_nothing_when_already_unparked(monkeypatch):
    service = IndiService()
    monkeypatch.setattr(
        service,
        "_park_output",
        lambda _mount: (
            f"{MOUNT}.TELESCOPE_PARK.PARK=Off\n"
            f"{MOUNT}.TELESCOPE_PARK.UNPARK=On"
        ),
    )

    def unexpected_run(*_args, **_kwargs):
        raise AssertionError("indi_setprop must not be called")

    monkeypatch.setattr("app.indi.service.subprocess.run", unexpected_run)

    result = service.ensure_unparked(MOUNT)

    assert result["status"] == "ready"
    assert result["changed"] is False
    assert result["parked_before"] is False


def test_mount_without_park_property_keeps_legacy_behavior(monkeypatch):
    service = IndiService()
    monkeypatch.setattr(service, "_park_output", lambda _mount: "")

    def unexpected_run(*_args, **_kwargs):
        raise AssertionError("indi_setprop must not be called")

    monkeypatch.setattr("app.indi.service.subprocess.run", unexpected_run)

    result = service.ensure_unparked(MOUNT)

    assert result["status"] == "unavailable"
    assert result["supported"] is False
    assert result["changed"] is False


def test_unpark_must_be_confirmed_by_readback(monkeypatch):
    service = IndiService()
    outputs = iter([
        f"{MOUNT}.TELESCOPE_PARK.PARK=On\n"
        f"{MOUNT}.TELESCOPE_PARK.UNPARK=Off",
        f"{MOUNT}.TELESCOPE_PARK.PARK=On\n"
        f"{MOUNT}.TELESCOPE_PARK.UNPARK=Off",
        f"{MOUNT}.TELESCOPE_PARK.PARK=On\n"
        f"{MOUNT}.TELESCOPE_PARK.UNPARK=Off",
        f"{MOUNT}.TELESCOPE_PARK.PARK=On\n"
        f"{MOUNT}.TELESCOPE_PARK.UNPARK=Off",
        f"{MOUNT}.TELESCOPE_PARK.PARK=On\n"
        f"{MOUNT}.TELESCOPE_PARK.UNPARK=Off",
    ])

    monkeypatch.setattr(service, "_park_output", lambda _mount: next(outputs))
    monkeypatch.setattr("app.indi.service.time.sleep", lambda _delay: None)
    monkeypatch.setattr(
        "app.indi.service.subprocess.run",
        lambda *_args, **_kwargs: SimpleNamespace(
            returncode=0,
            stdout="",
            stderr="",
        ),
    )

    with pytest.raises(RuntimeError, match="readback INDI"):
        service.ensure_unparked(MOUNT)
