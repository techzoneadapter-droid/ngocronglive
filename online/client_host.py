"""Set the online bootstrap endpoint in the exact tested, patched Yanii metadata."""
import argparse
import base64
import hashlib
import re
import struct
from pathlib import Path

BASE_HASH = '0cb7251c35b9f189febf6a40eee87fee8179db0d437f7dddd1a0bacc75bdf14d'

def patch(path, host):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9.-]{0,252}', host):
        raise ValueError('Invalid host')
    data = bytearray(path.read_bytes())
    if hashlib.sha256(data).hexdigest() != BASE_HASH:
        raise ValueError('Unknown metadata; refusing to patch a different client')
    _, _, table, size, strings = struct.unpack_from('<5I', data)
    found = 0
    for entry in range(table, table + size, 8):
        length, index = struct.unpack_from('<II', data, entry)
        value = bytes(data[strings + index:strings + index + length]).decode('utf8')
        try:
            decoded = base64.b64decode(''.join(chr(ord(c)-0x3d16) for c in value), validate=True).decode()
        except (ValueError, UnicodeError):
            continue
        if decoded != 'http://127.0.0.1:14446/IPLOCAL':
            continue
        encoded = base64.b64encode(f'http://{host}:14446/IPLOCAL'.encode()).decode()
        replacement = ''.join(chr(ord(c)+0x3d16) for c in encoded).encode()
        # Exact allocation of the original pinned upstream URL, whose tail was zeroed by client_patch.py.
        original_url = b'https://raw.githubusercontent.com/xungdzvc/xunglordNro/main/IPLOCAL'
        capacity = len(base64.b64encode(original_url)) * 3
        if any(data[strings+index+length:strings+index+capacity]):
            raise ValueError('Bootstrap padding changed')
        if len(replacement) > capacity:
            raise ValueError('Host too long for metadata slot')
        data[strings+index:strings+index+capacity] = replacement + bytes(capacity-len(replacement))
        struct.pack_into('<I', data, entry, len(replacement))
        found += 1
    if found != 1:
        raise ValueError(f'Expected one bootstrap string, found {found}')
    path.write_bytes(data)
    return hashlib.sha256(data).hexdigest()

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('metadata', type=Path)
    parser.add_argument('host')
    args = parser.parse_args()
    print('Online metadata SHA256:', patch(args.metadata, args.host))
