import os
from pathlib import Path
from PIL import Image

def convert_hald_clut(img_path, dest_path):
    print(f"Converting HaldCLUT: {img_path}")
    img = Image.open(img_path).convert('RGB')
    width, height = img.size

    if width != 512 or height != 512:
        print(f"Skipping {img_path}: Dimensions {width}x{height} not supported (expected 512x512).")
        return

    size = 64
    # HaldCLUT 64 is 8x8 tiles of 64x64 pixels
    tiles_per_row = 8
    
    with open(dest_path, 'w') as f:
        f.write(f'TITLE "{img_path.stem}"\n')
        f.write(f'LUT_3D_SIZE {size}\n')
        f.write('DOMAIN_MIN 0.0 0.0 0.0\n')
        f.write('DOMAIN_MAX 1.0 1.0 1.0\n')

        # .cube format expects: 
        # for B in 0..Size-1:
        #   for G in 0..Size-1:
        #     for R in 0..Size-1:
        
        pixels = img.load()
        
        for b in range(size):
            bx = b % tiles_per_row
            by = b // tiles_per_row
            
            base_x = bx * size
            base_y = by * size
            
            for g in range(size):
                for r in range(size):
                    x = base_x + r
                    y = base_y + g
                    
                    pixel = pixels[x, y]
                    # pixel is (r, g, b) usually 0-255
                    f.write(f'{pixel[0]/255.0:.6f} {pixel[1]/255.0:.6f} {pixel[2]/255.0:.6f}\n')
    print(f"Saved to {dest_path}")

def convert_1d_lut(img_path, dest_path):
    print(f"Converting 1D LUT: {img_path}")
    img = Image.open(img_path).convert('RGB')
    width, height = img.size
    
    # Assuming horizontal strip
    size = width
    if height != 1:
        print(f"Skipping {img_path}: Expected height 1 for 1D LUT, got {height}")
        return

    with open(dest_path, 'w') as f:
        f.write(f'TITLE "{img_path.stem}"\n')
        f.write(f'LUT_1D_SIZE {size}\n')
        f.write('DOMAIN_MIN 0.0 0.0 0.0\n')
        f.write('DOMAIN_MAX 1.0 1.0 1.0\n')
        
        pixels = img.load()
        for i in range(size):
            pixel = pixels[i, 0]
            f.write(f'{pixel[0]/255.0:.6f} {pixel[1]/255.0:.6f} {pixel[2]/255.0:.6f}\n')
    print(f"Saved to {dest_path}")

def main():
    src_dir = Path('/home/tqmane/git/lut-maker/extracted_luts')
    dest_dir = Path('/home/tqmane/git/lut-maker/converted_cubes/extracted_luts')
    dest_dir.mkdir(parents=True, exist_ok=True)
    
    if not src_dir.exists():
        print(f"Source directory {src_dir} does not exist.")
        return

    for img_path in src_dir.rglob('*.png'):
        # Calculate relative path to maintain folder structure
        rel_path = img_path.relative_to(src_dir)
        # Construct destination path
        dest_path = dest_dir / rel_path.with_suffix('.cube')
        
        # Ensure parent directory exists
        dest_path.parent.mkdir(parents=True, exist_ok=True)
        
        img = Image.open(img_path)
        w, h = img.size
        img.close()
        
        if w == 512 and h == 512:
            convert_hald_clut(img_path, dest_path)
        elif w == 256 and h == 1:
            convert_1d_lut(img_path, dest_path)
        else:
            print(f"Skipping {img_path}: Unknown dimensions {w}x{h}")

if __name__ == "__main__":
    main()
