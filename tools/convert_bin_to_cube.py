#!/usr/bin/env python3
"""
Convert the binary LUTs in this repository into .cube text files.

Supported formats:
1) Meishe ".MS-LUT " containers (uint8 RGB payload, optional channel-order flag)
2) Raw RGBA8 volumes (size must be a perfect cube of texels, alpha ignored)

Outputs are written under ./converted_cubes/ mirroring the source folder
structure, with filenames ending in .cube.
"""
from __future__ import annotations

import math
import struct
from pathlib import Path
from typing import Iterable, Tuple

ROOT = Path(__file__).resolve().parent
OUT_ROOT = ROOT / "converted_cubes"
MS_MAGIC = b".MS-LUT "


def read_ms_lut(data: bytes) -> Tuple[int, str, memoryview]:
    """Parse a .MS-LUT file, returning (size, order, raw_rgb_bytes)."""
    if len(data) < 48 or not data.startswith(MS_MAGIC):
        raise ValueError("missing .MS-LUT header")

    # Header layout we care about (little endian uint32 values):
    # [0] magic part 1, [1] magic part 2, [2] version, [3] cube size,
    # [4] unused, [5] channels, [6] order flag, [7] unused,
    # [8] axis offset, [9] unused, [10] data offset, [11] unused
    header = struct.unpack_from("<12I", data, 0)
    size = header[3]
    order_flag = header[6]
    axis_offset = header[8]
    data_offset = header[10]

    expected_axis_end = axis_offset + size * 4
    expected_data_len = size * size * size * 3

    if len(data) < expected_axis_end:
        raise ValueError("axis block truncated")
    if len(data) < data_offset + expected_data_len:
        raise ValueError("payload truncated")

    # order_flag: 1 -> RGB order, 0 -> BGR order in the payload
    order = "rgb" if order_flag else "bgr"
    payload = memoryview(data)[data_offset : data_offset + expected_data_len]
    return size, order, payload


def read_raw_rgba(data: bytes) -> Tuple[int, str, memoryview]:
    """Parse a raw RGBA8 volume, returning (size, order, raw_bytes)."""
    if len(data) % 4 != 0:
        raise ValueError("byte length not divisible by 4 for RGBA data")

    texel_count = len(data) // 4
    size = round(texel_count ** (1.0 / 3.0))
    if size * size * size != texel_count:
        raise ValueError("byte length is not a perfect cube of RGBA texels")

    # These payloads appear to already be in RGB order with red fastest.
    return size, "rgba", memoryview(data)


def iter_values(size: int, payload: memoryview, order: str) -> Iterable[Tuple[float, float, float]]:
    """
    Yield normalized RGB triples in .cube order (blue outer, green middle, red inner).
    order: "rgb" -> payload index = r + g*N + b*N*N (3 bytes per texel)
           "bgr" -> payload index = b + g*N + r*N*N (3 bytes per texel)
           "rgba"-> payload index = r + g*N + b*N*N (4 bytes per texel, ignores alpha)
    """
    n2 = size * size
    inv_255 = 1.0 / 255.0
    if order == "rgb":
        for b in range(size):
            for g in range(size):
                base = (g * size + b * n2) * 3  # start of r=0 for this g,b
                for r in range(size):
                    idx = base + r * 3
                    yield (
                        payload[idx] * inv_255,
                        payload[idx + 1] * inv_255,
                        payload[idx + 2] * inv_255,
                    )
    elif order == "bgr":
        for b in range(size):
            for g in range(size):
                base = (b + g * size) * 3
                for r in range(size):
                    idx = base + r * n2 * 3
                    yield (
                        payload[idx] * inv_255,
                        payload[idx + 1] * inv_255,
                        payload[idx + 2] * inv_255,
                    )
    elif order == "rgba":
        for b in range(size):
            for g in range(size):
                base = (g * size + b * n2) * 4
                for r in range(size):
                    idx = base + r * 4
                    yield (
                        payload[idx] * inv_255,
                        payload[idx + 1] * inv_255,
                        payload[idx + 2] * inv_255,
                    )
    else:
        raise ValueError(f"unsupported order: {order}")


def cube_path_for(src: Path) -> Path:
    """Build the destination .cube path under OUT_ROOT, mirroring the tree."""
    rel = src.relative_to(ROOT)
    name = rel.name
    if name.endswith(".rgb.bin"):
        stem = name[:-8]  # drop ".rgb.bin"
    elif name.endswith(".bin"):
        stem = name[:-4]  # drop ".bin"
    else:
        stem = name
    if stem.lower().endswith(".cube"):
        stem = stem[: -len(".cube")]
    return OUT_ROOT / rel.with_name(f"{stem}.cube")


def convert_file(path: Path) -> Tuple[Path, int, str]:
    data = path.read_bytes()
    try:
        size, order, payload = read_ms_lut(data)
        source_type = "ms-lut"
    except ValueError:
        size, order, payload = read_raw_rgba(data)
        source_type = "rgba"

    dest = cube_path_for(path)
    dest.parent.mkdir(parents=True, exist_ok=True)

    with dest.open("w", encoding="ascii") as fh:
        fh.write(f'TITLE "{path.name}"\n')
        fh.write(f"LUT_3D_SIZE {size}\n")
        fh.write("DOMAIN_MIN 0 0 0\n")
        fh.write("DOMAIN_MAX 1 1 1\n")
        for r, g, b in iter_values(size, payload, order):
            fh.write(f"{r:.6f} {g:.6f} {b:.6f}\n")

    return dest, size, source_type


def main() -> None:
    OUT_ROOT.mkdir(exist_ok=True)
    bin_files = sorted(ROOT.rglob("*.bin"))
    if not bin_files:
        print("No .bin files found.")
        return

    successes = 0
    for path in bin_files:
        try:
            dest, size, source = convert_file(path)
        except Exception as exc:  # noqa: BLE001
            print(f"[FAIL] {path}: {exc}")
            continue
        successes += 1
        print(f"[OK] {path} -> {dest} ({source}, size={size})")

    print(f"Converted {successes}/{len(bin_files)} files into {OUT_ROOT}")


if __name__ == "__main__":
    main()
