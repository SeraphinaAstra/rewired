#!/usr/bin/env python3
"""Generate bluestone textures by tinting vanilla redstone textures blue."""
import os
from PIL import Image

OUTPUT_DIR = "src/main/resources/assets/rewired/textures"
os.makedirs(f"{OUTPUT_DIR}/block", exist_ok=True)

def make_blue(red, green, blue, alpha=255):
    """Shift a color toward blue: keep some green, max out blue, reduce red."""
    r = int(red * 0.3)
    g = int(green * 0.6)
    b = min(255, int(blue * 0.8 + 60))
    return (r, g, b, alpha)

def tint_image(src_path, dst_path, color_transform):
    """Tint an image using the color transform function."""
    if not os.path.exists(src_path):
        print(f"Skip missing: {src_path}")
        return
    
    img = Image.open(src_path).convert("RGBA")
    pixels = img.load()
    w, h = img.size
    
    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            nr, ng, nb, na = color_transform(r, g, b, a)
            pixels[x, y] = (nr, ng, nb, na)
    
    img.save(dst_path)
    print(f"Created: {dst_path}")

def make_wire_dot():
    """Create bluestone wire dot texture (blue-tinted redstone dust dot)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = img.load()
    cx, cy = 8, 8
    for y in range(16):
        for x in range(16):
            dx = (x - cx) ** 2
            dy = (y - cy) ** 2
            dist = (dx + dy) ** 0.5
            if dist < 5:
                power = 1.0 - dist / 5.0
                r = int(power * 40)
                g = int(power * 80 + 20)
                b = int(power * 200 + 55)
                a = int(power * 255)
                pixels[x, y] = (r, g, b, a)
    img.save(f"{OUTPUT_DIR}/block/bluestone_dust_dot.png")
    print("Created: bluestone_dust_dot.png")

def make_wire_line():
    """Create bluestone wire line texture (blue-tinted redstone dust line)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = img.load()
    cx = 8
    for y in range(16):
        for x in range(16):
            dx = abs(x - cx)
            if dx < 2:
                power = 1.0 - dx / 2.0
                r = int(power * 40)
                g = int(power * 80 + 20)
                b = int(power * 200 + 55)
                a = int(power * 255)
                pixels[x, y] = (r, g, b, a)
    img.save(f"{OUTPUT_DIR}/block/bluestone_dust_line.png")
    print("Created: bluestone_dust_line.png")

def make_wire_overlay():
    """Create bluestone wire overlay texture."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = img.load()
    cx = 8
    for y in range(16):
        for x in range(16):
            dx = abs(x - cx)
            if dx < 1:
                pixels[x, y] = (30, 60, 180, 180)
    img.save(f"{OUTPUT_DIR}/block/bluestone_dust_overlay.png")
    print("Created: bluestone_dust_overlay.png")

def make_torch():
    """Create bluestone torch texture (blue-tinted redstone torch)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = img.load()
    
    for y in range(16):
        for x in range(16):
            cx, cy = 8, 12
            dx = x - cx
            dy = y - cy
            dist = (dx * dx + dy * dy) ** 0.5
            
            if dist < 4:
                power = 1.0 - dist / 4.0
                r = int(power * 30)
                g = int(power * 70 + 10)
                b = int(power * 220 + 35)
                a = int(power * 255)
                pixels[x, y] = (r, g, b, a)
            elif y < 12 and abs(x - 8) < 1:
                pixels[x, y] = (20, 40, 120, 255)
            elif y < 13 and abs(x - 8) < 2:
                pixels[x, y] = (25, 50, 140, 255)
    
    img.save(f"{OUTPUT_DIR}/block/bluestone_torch.png")
    print("Created: bluestone_torch.png")

def make_torch_off():
    """Create unlit bluestone torch texture (greyish)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = img.load()
    
    for y in range(16):
        for x in range(16):
            cx, cy = 8, 12
            dx = x - cx
            dy = y - cy
            dist = (dx * dx + dy * dy) ** 0.5
            
            if dist < 4:
                power = 1.0 - dist / 4.0
                r = int(power * 60)
                g = int(power * 60)
                b = int(power * 80)
                a = int(power * 255)
                pixels[x, y] = (r, g, b, a)
            elif y < 12 and abs(x - 8) < 1:
                pixels[x, y] = (50, 50, 70, 255)
            elif y < 13 and abs(x - 8) < 2:
                pixels[x, y] = (55, 55, 75, 255)
    
    img.save(f"{OUTPUT_DIR}/block/bluestone_torch_off.png")
    print("Created: bluestone_torch_off.png")

def make_repeater():
    """Create bluestone repeater texture."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = img.load()
    
    for y in range(16):
        for x in range(16):
            if y < 2 or y > 13:
                continue
            dx = abs(x - 8)
            if dx < 4:
                r = int(30 + (1.0 - dx/4.0) * 20)
                g = int(60 + (1.0 - dx/4.0) * 40)
                b = int(140 + (1.0 - dx/4.0) * 80)
                a = 255
                pixels[x, y] = (r, g, b, a)
            elif y > 6 and y < 10 and x > 6 and x < 10:
                r, g, b, a = 40, 80, 180, 255
                pixels[x, y] = (r, g, b, a)
    
    img.save(f"{OUTPUT_DIR}/block/bluestone_repeater.png")
    print("Created: bluestone_repeater.png")

def make_comparator():
    """Create bluestone comparator texture."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = img.load()
    
    for y in range(16):
        for x in range(16):
            if y < 2 or y > 13:
                continue
            dx = abs(x - 8)
            if dx < 5:
                r = int(25 + (1.0 - dx/5.0) * 20)
                g = int(55 + (1.0 - dx/5.0) * 40)
                b = int(130 + (1.0 - dx/5.0) * 90)
                a = 255
                pixels[x, y] = (r, g, b, a)
            elif y > 5 and y < 11 and x > 5 and x < 11:
                if abs(x - 8) < 2 and abs(y - 8) < 2:
                    pixels[x, y] = (35, 75, 200, 255)
                elif abs(x - 8) < 3 and abs(y - 8) < 3:
                    pixels[x, y] = (30, 65, 170, 255)
    
    img.save(f"{OUTPUT_DIR}/block/bluestone_comparator.png")
    print("Created: bluestone_comparator.png")

def make_bridge():
    """Create bluestone bridge texture (blue block with arrows)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pixels = img.load()
    
    for y in range(16):
        for x in range(16):
            if y < 2 or y > 13 or x < 2 or x > 13:
                continue
            r, g, b, a = 40, 80, 200, 255
            pixels[x, y] = (r, g, b, a)
            
            if x == 8 and y > 3 and y < 12:
                pixels[x, y] = (100, 150, 255, 255)
            if y == 8 and x > 3 and x < 12:
                pixels[x, y] = (100, 150, 255, 255)
    
    img.save(f"{OUTPUT_DIR}/block/bluestone_bridge.png")
    print("Created: bluestone_bridge.png")

if __name__ == "__main__":
    make_wire_dot()
    make_wire_line()
    make_wire_overlay()
    make_torch()
    make_torch_off()
    make_repeater()
    make_comparator()
    make_bridge()
    print("\nAll textures generated!")