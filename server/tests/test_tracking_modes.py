import pytest

from app.indi.service import IndiService


@pytest.mark.parametrize(
    ("mode", "element"),
    [
        ("sidereal", "TRACK_SIDEREAL"),
        ("solar", "TRACK_SOLAR"),
        ("lunar", "TRACK_LUNAR"),
    ],
)
def test_standard_indi_tracking_mode_mapping(
    mode,
    element,
):
    normalized, indi_element = (
        IndiService.tracking_mode_element(mode)
    )

    assert normalized == mode
    assert indi_element == element


def test_invalid_tracking_mode_is_rejected():
    with pytest.raises(ValueError):
        IndiService.tracking_mode_element(
            "planetary"
        )


def test_tracking_mode_uses_standard_indi_setprop_syntax(
    monkeypatch,
):
    calls = []

    class Result:
        def __init__(
            self,
            returncode=0,
            stdout="",
            stderr="",
        ):
            self.returncode = returncode
            self.stdout = stdout
            self.stderr = stderr

    def fake_run(args, **kwargs):
        calls.append(args)

        if args[0] == "indi_setprop":
            return Result(returncode=0)

        if args[0] == "indi_getprop":
            return Result(
                returncode=0,
                stdout=(
                    "LX200 OnStep.TELESCOPE_TRACK_MODE."
                    "TRACK_SIDEREAL=Off\n"
                    "LX200 OnStep.TELESCOPE_TRACK_MODE."
                    "TRACK_SOLAR=On\n"
                    "LX200 OnStep.TELESCOPE_TRACK_MODE."
                    "TRACK_LUNAR=Off\n"
                ),
            )

        raise AssertionError(
            f"Commande inattendue: {args}"
        )

    monkeypatch.setattr(
        "app.indi.service.subprocess.run",
        fake_run,
    )

    service = IndiService()

    assert service.set_tracking_mode(
        "LX200 OnStep",
        "solar",
    ) == "solar"

    set_calls = [
        call
        for call in calls
        if call[0] == "indi_setprop"
    ]

    assert len(set_calls) == 1
    assert "-s" not in set_calls[0]
    assert set_calls[0][-1] == (
        "LX200 OnStep.TELESCOPE_TRACK_MODE."
        "TRACK_SOLAR=On"
    )
