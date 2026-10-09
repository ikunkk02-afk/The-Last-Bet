"""Reproduce the original 16px bank textures using only Python's standard library."""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/lastbet/textures'

def png(name, pixels):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    raw = b''.join(b'\0' + bytes(sum(row, ())) for row in pixels)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 16, 16, 8, 6, 0, 0, 0))
    data += chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
    path = ROOT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)

def rect(pixels, x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            pixels[y][x] = color

wood = [[(146 + ((x * 7 + y * 3) % 5) * 4, 107 + ((x + y) % 4) * 3, 60, 255) for x in range(16)] for y in range(16)]
for y in (3, 7, 11, 15):
    rect(wood, 0, y, 16, y + 1, (99, 70, 37, 255))
rect(wood, 0, 0, 16, 3, (83, 59, 34, 255))
rect(wood, 4, 5, 12, 13, (59, 48, 29, 255))
rect(wood, 5, 6, 11, 12, (197, 160, 66, 255))
for y, span in ((6, (7, 9)), (7, (6, 10)), (8, (6, 10)), (9, (7, 9)), (10, (7, 9))):
    rect(wood, span[0], y, span[1], y + 1, (39, 166, 102, 255))
png('block/bank_counter_front.png', wood)
top = [[(88 + ((x * 5 + y) % 4) * 5, 62 + ((x + y) % 3) * 3, 36, 255) for x in range(16)] for y in range(16)]
rect(top, 2, 2, 14, 14, (100, 73, 43, 255))
rect(top, 3, 4, 10, 11, (204, 193, 150, 255))
for y in (5, 7, 9):
    rect(top, 4, y, 9, y + 1, (121, 120, 99, 255))
rect(top, 12, 5, 14, 12, (39, 166, 102, 255))
png('block/bank_counter_top.png', top)
card = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]
rect(card, 1, 3, 15, 13, (19, 68, 51, 255))
rect(card, 2, 4, 14, 12, (41, 133, 92, 255))
rect(card, 2, 5, 14, 7, (25, 46, 38, 255))
rect(card, 3, 8, 6, 11, (226, 190, 85, 255))
rect(card, 4, 9, 6, 10, (159, 119, 42, 255))
rect(card, 8, 10, 12, 11, (153, 206, 166, 255))
rect(card, 12, 8, 13, 9, (227, 202, 119, 255))
png('item/bank_card.png', card)
