"""Exercise the actual socket handshake and authentication (not just an open port)."""
import io
import json
import socket
import struct
import sys
import time
from pathlib import Path


def utf(value):
    data = value.encode('utf-8')
    return struct.pack('>H', len(data)) + data


def run(credentials_file, existing=False):
    credentials = json.load(open(credentials_file, encoding='ascii'))
    with socket.create_connection(('127.0.0.1', 14445), timeout=8) as sock:
        sock.settimeout(8)
        def read_exact(count):
            result = bytearray()
            while len(result) < count:
                part = sock.recv(count - len(result))
                if not part:
                    raise AssertionError('Server closed the protocol connection')
                result.extend(part)
            return bytes(result)
        sock.sendall(bytes([229, 0, 0]))  # -27, unencrypted handshake
        header = read_exact(3)
        assert header[0] == 229, 'Unexpected handshake opcode'
        payload = read_exact(int.from_bytes(header[1:], 'big'))
        assert payload and 0 < payload[0] <= len(payload) - 1
        key = list(payload[1:1 + payload[0]])
        for i in range(1, len(key)):
            key[i] ^= key[i - 1]
        tx = 0
        rx = 0
        def encrypted(data):
            nonlocal tx
            result = bytearray()
            for value in data:
                result.append(value ^ key[tx])
                tx = (tx + 1) % len(key)
            return bytes(result)
        def read_byte():
            nonlocal rx
            value = read_exact(1)[0] ^ key[rx]
            rx = (rx + 1) % len(key)
            return value
        def receive():
            code = read_byte()
            signed = code if code < 128 else code - 256
            if signed in (-32, -66, -74, 11, -67, -87, 66):
                # Extended server framing adds -128 AFTER encryption.
                size_bytes = []
                nonlocal rx
                for _ in range(3):
                    raw = (read_exact(1)[0] + 128) & 255
                    size_bytes.append(raw ^ key[rx])
                    rx = (rx + 1) % len(key)
                size = int.from_bytes(bytes(size_bytes), 'little')
            else:
                size = (read_byte() << 8) | read_byte()
            if size > 16_000_000:
                raise AssertionError('Invalid packet size')
            data = bytes(read_byte() for _ in range(size))
            return signed, data
        def send(code, data):
            sock.sendall(encrypted(bytes([code & 255]) + struct.pack('>H', len(data)) + data))
        send(-29, bytes([2, 4, 1, 0]) + struct.pack('>ii', 1024, 600) + bytes([1, 1]) + utf('Windows local test'))
        send(-29, bytes([0]) + utf('tester') + utf(credentials['player']) + utf('2.3.0') + bytes([0]))
        for _ in range(30):
            code, login_data = receive()
            if (not existing and code == 2) or (existing and code == -28 and login_data and login_data[0] == 4):
                print('PASS: handshake and authentication reached ' + ('existing character' if existing else 'character creation'))
                # A clean client downloads templates before a character exists.
                send(-87, b'')
                for request in (6, 7, 8):
                    send(-28, bytes([request]))
                expected = {(-87, 98), (-28, 6), (-28, 7), (-28, 8)}
                item_parts = set()
                for _ in range(30):
                    asset_code, asset_data = receive()
                    if asset_data:
                        expected.discard((asset_code, asset_data[0]))
                        if asset_code == -28 and asset_data[0] == 8:
                            item_parts.add(asset_data[2])
                    if not expected and item_parts == {0, 1, 2, 100}:
                        break
                assert not expected and item_parts == {0, 1, 2, 100}, 'Bootstrap templates missing before character creation'
                print('PASS: clean client receives data/map/skills/all item parts before a player exists')
                send(-28, bytes([13]))  # clientOk before character creation must be harmless
                if not existing:
                    send(-28, bytes([2]) + utf('pcprobe') + bytes([0, 64]))
                player_loaded = False
                map_loaded = False
                for _ in range(70):
                    created_code, created_data = receive()
                    if created_code == -28 and created_data and created_data[0] == 4:
                        send(-28, bytes([13]))  # Cached templates are ready after version negotiation.
                    if created_code == -24:
                        map_loaded = True
                    if created_code == -30 and created_data and created_data[0] == 0:
                        state = io.BytesIO(created_data)
                        def number(fmt):
                            size = struct.calcsize(fmt)
                            return struct.unpack(fmt, state.read(size))[0]
                        assert number('>B') == 0
                        assert number('>i') > 0
                        state.read(4)  # task, gender, head
                        name = state.read(number('>H')).decode('utf-8')
                        assert name == 'pcprobe'
                        state.read(2)  # PK fields
                        assert number('>q') == 1200
                        state.read(5)  # HP/MP buffs, class
                        skills = number('>B')
                        assert skills >= 1
                        state.read(skills * 2)
                        assert number('>q') == 20000, 'Windows client expects 64-bit gold'
                        assert number('>i') == 0, 'Locked gems field mismatch'
                        assert number('>i') == 20, 'Gems field mismatch'
                        assert number('>B') == 6, 'Inventory framing mismatch'
                        print('PASS: new character and player-state layout match the Windows client (gold, gems, starter skill, body slots)')
                        player_loaded = True
                    if player_loaded and map_loaded:
                        send(-28, bytes([10]))
                        for _ in range(30):
                            map_code, map_data = receive()
                            if map_code == -28 and map_data and map_data[0] == 10:
                                width, height = map_data[1:3]
                                assert width and height and len(map_data) >= 3 + width * height
                                print('PASS: clientOk delivers player, world map and requested tile template')
                                return
                        raise AssertionError('World map tile template not delivered')
                raise AssertionError('Character creation did not deliver player state')
        raise AssertionError('Authentication did not reach character creation')


if __name__ == '__main__':
    try:
        run(sys.argv[1])
        time.sleep(1)
        run(sys.argv[1], existing=True)
        print('PASS: existing character can log out and re-enter the world')
    except Exception:
        for name in ('server-error.log', 'server.log'):
            path = Path(__file__).resolve().parent / 'logs' / name
            if path.exists():
                print(name + ':\n' + '\n'.join(path.read_text(encoding='utf-8', errors='replace').splitlines()[-65:]), flush=True)
        raise
