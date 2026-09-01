from __future__ import annotations

from io import BytesIO
from pathlib import Path
from typing import Any

import numpy as np
from astropy.io import fits
from PIL import Image


_BAYER_MATRICES = {
    "RGGB": (("R", "G"), ("G", "B")),
    "BGGR": (("B", "G"), ("G", "R")),
    "GRBG": (("G", "R"), ("B", "G")),
    "GBRG": (("G", "B"), ("R", "G")),
}


def _load_fits(path: Path) -> tuple[np.ndarray, fits.Header]:
    with fits.open(path, memmap=False) as hdul:
        data = np.asarray(hdul[0].data)
        header = hdul[0].header.copy()

    data = np.squeeze(data)
    if data.ndim != 2:
        raise ValueError(f"FITS does not contain a usable 2D image: {data.shape}")

    data = data.astype(np.float32, copy=False)
    if not np.isfinite(data).any():
        raise ValueError("Image contains no finite pixels")

    return data, header


def _effective_bayer_matrix(
    pattern: str,
    x_offset: int,
    y_offset: int,
):
    matrix = _BAYER_MATRICES.get(pattern)
    if matrix is None:
        return None

    return tuple(
        tuple(
            matrix[(row + y_offset) % 2][(col + x_offset) % 2]
            for col in range(2)
        )
        for row in range(2)
    )


def render_fits_preview(
    path: str | Path,
    *,
    max_width: int = 1600,
    jpeg_quality: int = 92,
) -> tuple[bytes, dict[str, Any]]:
    """Render the canonical StellarPilot JPEG preview for one FITS frame.

    Assistant 3, Capture, live stack and Galleries must all pass through this
    renderer so the same FITS produces the same visual result everywhere.
    The scientific FITS is never modified.
    """
    source = Path(path)
    data, header = _load_fits(source)

    # Keep even dimensions when a Bayer matrix is present.
    height = data.shape[0] - data.shape[0] % 2
    width = data.shape[1] - data.shape[1] % 2
    data = data[:height, :width]

    finite = data[np.isfinite(data)]
    background = float(np.median(finite))
    mad = float(np.median(np.abs(finite - background)))
    noise = 1.4826 * mad

    if noise <= 0.0:
        p16, p84 = np.percentile(finite, [16.0, 84.0])
        noise = max(float((p84 - p16) / 2.0), 1.0)

    high_percentile = float(np.percentile(finite, 99.8))
    black = background - 0.5 * noise
    white = max(high_percentile, background + 8.0 * noise)

    if white <= black:
        black = float(finite.min())
        white = float(finite.max())
    if white <= black:
        white = black + 1.0

    def stretch(channel: np.ndarray) -> np.ndarray:
        value = np.clip((channel - black) / (white - black), 0.0, 1.0)
        strength = 8.0
        return np.arcsinh(value * strength) / np.arcsinh(strength)

    bayer_pattern = str(header.get("BAYERPAT", "")).strip().upper()
    x_offset = int(header.get("XBAYROFF", 0) or 0)
    y_offset = int(header.get("YBAYROFF", 0) or 0)
    matrix = _effective_bayer_matrix(
        bayer_pattern,
        x_offset=x_offset,
        y_offset=y_offset,
    )

    if matrix is None:
        mono = stretch(data)
        rgb = np.stack((mono, mono, mono), axis=-1)
    else:
        channels: dict[str, list[np.ndarray]] = {
            "R": [],
            "G": [],
            "B": [],
        }
        for row in range(2):
            for col in range(2):
                channels[matrix[row][col]].append(data[row::2, col::2])

        red = channels["R"][0]
        green = sum(channels["G"]) / len(channels["G"])
        blue = channels["B"][0]

        # Neutralise the Bayer colour cast of the sky background while
        # keeping one global black/white stretch for the three channels.
        red_background = float(np.median(red[np.isfinite(red)]))
        green_background = float(np.median(green[np.isfinite(green)]))
        blue_background = float(np.median(blue[np.isfinite(blue)]))
        neutral_background = float(
            np.median([red_background, green_background, blue_background])
        )

        red = red + neutral_background - red_background
        green = green + neutral_background - green_background
        blue = blue + neutral_background - blue_background

        rgb = np.stack(
            (stretch(red), stretch(green), stretch(blue)),
            axis=-1,
        )

        luminance = (
            0.2126 * rgb[..., 0]
            + 0.7152 * rgb[..., 1]
            + 0.0722 * rgb[..., 2]
        )
        saturation = 1.08
        rgb = luminance[..., None] + saturation * (rgb - luminance[..., None])

    rgb = np.clip(rgb, 0.0, 1.0)
    image = Image.fromarray((rgb * 255.0).astype(np.uint8))

    if image.width > max_width:
        new_height = round(image.height * max_width / image.width)
        image = image.resize(
            (max_width, new_height),
            Image.Resampling.LANCZOS,
        )

    buffer = BytesIO()
    image.save(
        buffer,
        format="JPEG",
        quality=jpeg_quality,
        optimize=True,
    )

    p99 = float(np.percentile(finite, 99.0))
    contrast_sigma = max(
        0.0,
        (p99 - background) / max(noise, 1.0e-6),
    )
    if contrast_sigma < 3.0:
        preview_status = "uniform"
    elif contrast_sigma < 6.0:
        preview_status = "weak-signal"
    else:
        preview_status = "structured"

    diagnostics = {
        "preview_status": preview_status,
        "bayer": bayer_pattern or "NONE",
        "x_bayer_offset": x_offset,
        "y_bayer_offset": y_offset,
        "background": background,
        "noise": noise,
        "contrast_sigma": contrast_sigma,
        "width": image.width,
        "height": image.height,
        "renderer": "stellar-unified-v1",
    }
    return buffer.getvalue(), diagnostics


def preview_headers(
    source_name: str,
    diagnostics: dict[str, Any],
) -> dict[str, str]:
    return {
        "Cache-Control": "no-store",
        "X-StellarPilot-Source": source_name,
        "X-StellarPilot-Bayer": str(diagnostics.get("bayer", "NONE")),
        "X-StellarPilot-Bayer-X-Offset": str(
            diagnostics.get("x_bayer_offset", 0)
        ),
        "X-StellarPilot-Bayer-Y-Offset": str(
            diagnostics.get("y_bayer_offset", 0)
        ),
        "X-StellarPilot-Preview": str(
            diagnostics.get("renderer", "stellar-unified-v1")
        ),
        "X-StellarPilot-Preview-Status": str(
            diagnostics.get("preview_status", "unknown")
        ),
        "X-StellarPilot-Preview-Background": (
            f"{float(diagnostics.get('background', 0.0)):.3f}"
        ),
        "X-StellarPilot-Preview-Noise": (
            f"{float(diagnostics.get('noise', 0.0)):.3f}"
        ),
        "X-StellarPilot-Preview-Contrast": (
            f"{float(diagnostics.get('contrast_sigma', 0.0)):.3f}"
        ),
    }
