"""Patch the pinned supplied IL2CPP client; never accept an unknown binary."""
import base64
import hashlib
import struct
from pathlib import Path

def patch_client(client):
    client = Path(client)
    meta = client / 'NroYanii_Data/il2cpp_data/Metadata/global-metadata.dat'
    data = bytearray(meta.read_bytes())
    if hashlib.sha256(data).hexdigest() != '7b9298043fbbad87096e8fb317c727176f376fb5fbdcc9029b4fab9e6a2d3bd1':
        raise ValueError('Unknown client metadata')
    _, _, table, size, strings = struct.unpack_from('<5I', data)
    replacements = 0
    for entry in range(table, table + size, 8):
        length, index = struct.unpack_from('<II', data, entry)
        value = bytes(data[strings + index:strings + index + length]).decode('utf8')
        new = None
        if value == 'Lines/Colored Blended':
            # Original shader was stripped from this player. Use bundled 2D shader.
            new = 'Sprites/Default'
        elif value.startswith('㵷㵞㵨㵆'):
            decoded = base64.b64decode(''.join(chr(ord(c)-0x3d16) for c in value)).decode()
            if decoded != 'https://raw.githubusercontent.com/xungdzvc/xunglordNro/main/IPLOCAL':
                raise ValueError('Unexpected bootstrap URL')
            encoded = base64.b64encode(b'http://127.0.0.1:14446/IPLOCAL').decode()
            new = ''.join(chr(ord(c)+0x3d16) for c in encoded)
        if new is not None:
            raw = new.encode()
            if len(raw) > length:
                raise ValueError('Replacement exceeds string allocation')
            data[strings + index:strings + index + length] = raw + bytes(length-len(raw))
            struct.pack_into('<I', data, entry, len(raw))
            replacements += 1
    if replacements != 2:
        raise ValueError('Client string patch count mismatch')
    meta.write_bytes(data)
    assembly = client / 'GameAssembly.dll'
    code = bytearray(assembly.read_bytes())
    if hashlib.sha256(code).hexdigest() != 'ee52be52575b2f9c764318152776aafc9ab89a71e77f971aaa65a147270dab1f':
        raise ValueError('Unknown GameAssembly')
    # ModFunc.LoadGame calls InfoMe.addInfo before Info is initialized.
    # Omit only this optional startup notification, preserving all initialization.
    offset = 0x3e0b26
    if code[offset:offset+5] != bytes.fromhex('e875d4fdff'):
        raise ValueError('Startup notification call changed')
    code[offset:offset+5] = b'\x90'*5
    assembly.write_bytes(code)
