#!/usr/bin/env python3
"""
OPPO .bin LUT to .cube converter with genre categorization.
Handles .MS-LUT format binary files and converts to Adobe .cube format.
"""

import os
import struct
import re
from pathlib import Path
from collections import defaultdict

# LUT genre categorization patterns
GENRE_PATTERNS = {
    "Ricoh GR": [r"^gr\.", r"gr\.bw", r"gr\.hi", r"gr\.nega", r"gr\.posi"],
    "Hasselblad Master": [r"radiance", r"serenity", r"emerald"],  # 放射輝度、静けさ、エメラルド
    "Fujifilm": [r"fuji", r"type_fuji", r"provia", r"velvia", r"astia", r"acros", r"eterna", r"chrome"],
    "Kodak Film": [r"kodak", r"800t", r"delta400"],
    "Cinematic (Movie)": [r"moneyball", r"inception", r"cyberpunk", r"interstellar", r"neon", r"city"],
    "Instagram Filters": [r"^ins", r"insclarendon", r"insjuno", r"insvalencia"],
    "OPPO Original": [r"^oplus", r"^oppo", r"^opc_"],
    "Black & White": [r"b-w", r"blackandwhite", r"mono", r"grayscale"],
    "Portrait": [r"portrait", r"pp1", r"pp2", r"pp3", r"v02"],
    "Landscape": [r"landscape", r"v01", r"mountains", r"island", r"lake", r"beach", r"desert", r"forest", r"senlin"],
    "Food": [r"food", r"v03", r"gourmet", r"meiwei"],
    "Night": [r"night", r"v04", r"moonlight"],
    "Warm Tones": [r"warm", r"cola", r"candy", r"sweet", r"gold"],
    "Cool Tones": [r"cold", r"cool", r"azure", r"blue"],
    "Vintage/Retro": [r"old", r"vintage", r"retro", r"drjw", r"ccd"],
    "HDR/Video": [r"hdr", r"log_video", r"dolby", r"bt2020", r"bt709", r"p3_"],
    "App Filters": [r"b612", r"beautyplus", r"faceapp", r"snapseed", r"sweetsnap", r"youcam"],
    "Artistic": [r"morandi", r"texture", r"vivid"],
    "Japanese Style": [r"japan", r"jiari", r"bowu", r"yuanqi", r"qiuri", r"lvtu"],
    "Golden Touch": [r"gt-", r"glow", r"rosy", r"steaming"],
}


def categorize_lut(filename: str) -> str:
    """Categorize a LUT file into a genre based on filename patterns."""
    basename = filename.lower()
    
    for genre, patterns in GENRE_PATTERNS.items():
        for pattern in patterns:
            if re.search(pattern, basename, re.IGNORECASE):
                return genre
    
    return "Uncategorized"


def parse_ms_lut_header(data: bytes) -> dict:
    """Parse .MS-LUT header and return metadata."""
    # Check magic number
    if not data.startswith(b'.MS-LUT '):
        return None
    
    # Header structure (observed):
    # Offset 0x00: Magic ".MS-LUT " (8 bytes)
    # Offset 0x08: Version (4 bytes, little-endian)
    # Offset 0x0C: LUT size hint (4 bytes, little-endian)  
    # Offset 0x18: Channel count (4 bytes)
    # Offset 0x20: Offset to LUT params (8 bytes)
    # Offset 0x28: Offset to LUT data (8 bytes)
    
    version = struct.unpack('<I', data[8:12])[0]
    lut_info = struct.unpack('<I', data[12:16])[0]
    
    # Extract data offset from header
    data_offset = 0xB0  # Default for v2
    if len(data) >= 0x30:
        param_offset = struct.unpack('<Q', data[0x20:0x28])[0]
        data_offset_field = struct.unpack('<Q', data[0x28:0x30])[0]
        if data_offset_field > 0 and data_offset_field < len(data):
            data_offset = data_offset_field
    
    file_size = len(data)
    
    # Calculate LUT size from remaining data
    remaining = file_size - data_offset
    
    # Try different LUT sizes to find the best match
    for size in [32, 33, 17, 16, 21, 25, 20]:
        expected_rgb = size ** 3 * 3
        expected_rgba = size ** 3 * 4
        if remaining == expected_rgb or remaining == expected_rgba:
            channels = 3 if remaining == expected_rgb else 4
            return {
                'version': version,
                'lut_size': size,
                'file_size': file_size,
                'data_offset': data_offset,
                'channels': channels,
            }
    
    # Special case: check for non-standard sizes with tolerance
    for size in [20, 17, 21, 25]:
        for ch in [3, 4]:
            expected = size ** 3 * ch
            if abs(remaining - expected) <= 4:
                return {
                    'version': version,
                    'lut_size': size,
                    'file_size': file_size,
                    'data_offset': data_offset,
                    'channels': ch,
                }
    
    # Fallback: guess from file size
    if file_size <= 14900:
        lut_size = 17
    elif file_size <= 17000:
        lut_size = 16
    elif file_size <= 28000:
        lut_size = 21
    elif file_size <= 100000:
        lut_size = 32  # 98480 bytes = 0xB0 header + 32^3*3
    elif file_size <= 135000:
        lut_size = 32
    else:
        lut_size = 33
    
    return {
        'version': version,
        'lut_size': lut_size,
        'file_size': file_size,
        'data_offset': data_offset,
        'channels': 3,
    }


def extract_lut_data(data: bytes, header: dict) -> list:
    """Extract RGB values from binary LUT data using header info."""
    lut_size = header.get('lut_size', 17)
    data_offset = header.get('data_offset', 0xB0)
    channels = header.get('channels', 3)
    
    # Start from the data offset
    lut_data = data[data_offset:]
    
    entries = []
    num_entries = lut_size ** 3
    
    for i in range(num_entries):
        offset = i * channels
        if offset + 3 > len(lut_data):
            break
        
        r = lut_data[offset] / 255.0
        g = lut_data[offset + 1] / 255.0
        b = lut_data[offset + 2] / 255.0
        
        entries.append((r, g, b))
    
    return entries


def extract_float_lut(data: bytes, lut_size: int) -> list:
    """Extract LUT data stored as IEEE 754 floats."""
    entries = []
    num_entries = lut_size ** 3
    
    for i in range(num_entries):
        offset = i * 12  # 3 floats * 4 bytes
        if offset + 12 > len(data):
            break
        
        r = struct.unpack('<f', data[offset:offset+4])[0]
        g = struct.unpack('<f', data[offset+4:offset+8])[0]
        b = struct.unpack('<f', data[offset+8:offset+12])[0]
        
        # Clamp values
        r = max(0.0, min(1.0, r))
        g = max(0.0, min(1.0, g))
        b = max(0.0, min(1.0, b))
        
        entries.append((r, g, b))
    
    return entries


def extract_byte_lut(data: bytes, lut_size: int, channels: int) -> list:
    """Extract LUT data stored as bytes (0-255)."""
    entries = []
    num_entries = lut_size ** 3
    
    for i in range(num_entries):
        offset = i * channels
        if offset + 3 > len(data):
            break
        
        r = data[offset] / 255.0
        g = data[offset + 1] / 255.0
        b = data[offset + 2] / 255.0
        
        entries.append((r, g, b))
    
    return entries


def extract_byte_lut_auto(data: bytes, lut_size: int) -> list:
    """Auto-detect offset and extract byte LUT."""
    num_entries = lut_size ** 3
    expected_size = num_entries * 3
    
    # Try to find start of LUT data by looking for patterns
    for offset in range(0, min(512, len(data) - expected_size), 4):
        # Check if data at this offset looks like valid LUT
        sample_entries = []
        valid = True
        
        for i in range(min(10, num_entries)):
            pos = offset + i * 3
            if pos + 3 > len(data):
                valid = False
                break
            
            r, g, b = data[pos], data[pos+1], data[pos+2]
            sample_entries.append((r, g, b))
        
        if valid and len(sample_entries) >= 10:
            # Check if values seem reasonable for a LUT
            return extract_byte_lut(data[offset:], lut_size, 3)
    
    # Fallback with common header size
    return extract_byte_lut(data[0x30:], lut_size, 3)


def write_cube_file(entries: list, lut_size: int, output_path: Path, title: str):
    """Write LUT entries to a .cube file."""
    with open(output_path, 'w') as f:
        f.write(f'TITLE "{title}"\n')
        f.write(f'LUT_3D_SIZE {lut_size}\n')
        f.write(f'DOMAIN_MIN 0.0 0.0 0.0\n')
        f.write(f'DOMAIN_MAX 1.0 1.0 1.0\n')
        f.write('\n')
        
        for r, g, b in entries:
            f.write(f'{r:.6f} {g:.6f} {b:.6f}\n')


def convert_bin_to_cube(bin_path: Path, output_dir: Path) -> tuple:
    """Convert a .bin file to .cube format. Returns (success, genre, output_path)."""
    try:
        with open(bin_path, 'rb') as f:
            data = f.read()
        
        # Check if already a .cube file (text format)
        if data[:20].isascii():
            try:
                text_start = data[:500].decode('ascii', errors='ignore')
                if 'LUT_3D_SIZE' in text_start or 'TITLE' in text_start:
                    # Already a cube file, just copy it
                    output_path = output_dir / f"{bin_path.stem}.cube"
                    with open(output_path, 'wb') as f:
                        f.write(data)
                    genre = categorize_lut(bin_path.stem)
                    return True, genre, output_path
            except:
                pass
        
        # Parse .MS-LUT header
        header = parse_ms_lut_header(data)
        
        if header is None:
            # Not .MS-LUT format - might be raw LUT data
            # Try to infer size from file size
            file_size = len(data)
            if file_size == 131072:  # 32^3 * 4 (RGBA)
                lut_size = 32
            elif file_size == 98304:  # 32^3 * 3 (RGB)
                lut_size = 32
            elif file_size == 107991:  # ~33^3 * 3
                lut_size = 33
            elif file_size == 16384:  # 16^3 * 4 (RGBA)
                lut_size = 16
            else:
                lut_size = 17  # Default guess
            
            entries = extract_byte_lut(data, lut_size, 3 if file_size % 4 != 0 else 4)
        else:
            lut_size = header['lut_size']
            entries = extract_lut_data(data, header)
        
        if not entries or len(entries) < lut_size ** 3 * 0.9:
            print(f"  Warning: Could not extract enough entries from {bin_path.name}")
            return False, None, None
        
        # Ensure we have exactly lut_size^3 entries
        expected = lut_size ** 3
        if len(entries) > expected:
            entries = entries[:expected]
        elif len(entries) < expected:
            # Pad with identity values if needed
            while len(entries) < expected:
                idx = len(entries)
                b = idx // (lut_size * lut_size)
                g = (idx // lut_size) % lut_size
                r = idx % lut_size
                entries.append((r / (lut_size-1), g / (lut_size-1), b / (lut_size-1)))
        
        # Determine genre
        genre = categorize_lut(bin_path.stem)
        
        # Create output path
        output_path = output_dir / f"{bin_path.stem}.cube"
        
        # Write cube file
        write_cube_file(entries, lut_size, output_path, bin_path.stem)
        
        return True, genre, output_path
        
    except Exception as e:
        print(f"  Error converting {bin_path.name}: {e}")
        return False, None, None


def process_directory(input_dir: Path, output_base: Path):
    """Process all .bin files in directory and subdirectories."""
    # Find all .bin files and files without extension (raw LUT data)
    bin_files = []
    
    for root, dirs, files in os.walk(input_dir):
        for f in files:
            filepath = Path(root) / f
            # Skip already-converted .cube files
            if f.endswith('.cube'):
                continue
            # Include .bin files and files that look like raw LUT data
            if f.endswith('.bin') or '.' not in f:
                # Skip very small files and known non-LUT files
                if filepath.stat().st_size > 1000:
                    bin_files.append(filepath)
    
    print(f"Found {len(bin_files)} files to process")
    
    # Track results by genre
    results_by_genre = defaultdict(list)
    failed = []
    
    # Create output directory
    output_base.mkdir(parents=True, exist_ok=True)
    
    for bin_file in sorted(bin_files):
        print(f"Processing: {bin_file.name}")
        success, genre, output_path = convert_bin_to_cube(bin_file, output_base)
        
        if success:
            results_by_genre[genre].append({
                'original': bin_file,
                'converted': output_path,
            })
            print(f"  -> {output_path.name} [{genre}]")
        else:
            failed.append(bin_file)
    
    return results_by_genre, failed


def organize_by_genre(results_by_genre: dict, output_base: Path):
    """Organize converted files into genre folders."""
    organized_dir = output_base.parent / "organized_luts"
    organized_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"\n{'='*60}")
    print("Organizing LUTs by Genre")
    print('='*60)
    
    for genre, files in sorted(results_by_genre.items()):
        # Create genre folder
        genre_dir = organized_dir / genre.replace("/", "-").replace("(", "").replace(")", "")
        genre_dir.mkdir(parents=True, exist_ok=True)
        
        print(f"\n{genre}: {len(files)} files")
        
        for item in files:
            src = item['converted']
            dst = genre_dir / src.name
            
            # Copy file to organized folder
            import shutil
            shutil.copy2(src, dst)
            print(f"  - {src.name}")
    
    return organized_dir


def main():
    import sys
    
    # Define paths
    script_dir = Path(__file__).parent
    input_dir = script_dir / "OPPO"
    output_base = script_dir / "converted_oppo_cubes"
    
    if not input_dir.exists():
        print(f"Error: Input directory not found: {input_dir}")
        sys.exit(1)
    
    print(f"{'='*60}")
    print("OPPO .bin to .cube LUT Converter")
    print('='*60)
    print(f"Input:  {input_dir}")
    print(f"Output: {output_base}")
    print()
    
    # Process all files
    results_by_genre, failed = process_directory(input_dir, output_base)
    
    # Organize by genre
    organized_dir = organize_by_genre(results_by_genre, output_base)
    
    # Print summary
    print(f"\n{'='*60}")
    print("SUMMARY")
    print('='*60)
    
    total_converted = sum(len(files) for files in results_by_genre.values())
    print(f"Total converted: {total_converted}")
    print(f"Failed: {len(failed)}")
    print(f"\nOrganized folder: {organized_dir}")
    
    print(f"\nGenre breakdown:")
    for genre, files in sorted(results_by_genre.items(), key=lambda x: -len(x[1])):
        print(f"  {genre}: {len(files)}")
    
    if failed:
        print(f"\nFailed files:")
        for f in failed:
            print(f"  - {f.name}")


if __name__ == "__main__":
    main()
