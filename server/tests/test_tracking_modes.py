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
