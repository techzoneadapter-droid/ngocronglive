"""Exercise the actual socket handshake and authentication (not just an open port)."""
import json
import socket
import struct
import sys


def utf(value):
    data = value.encode('utf-8')
    return struct.pack('>H', len(data)) + data


def run(credentials_file):
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
            code, _ = receive()
            if code == 2:
                print('PASS: handshake and fresh test-account authentication reached character creation')
                return
        raise AssertionError('Authentication did not reach character creation')


if __name__ == '__main__':
    run(sys.argv[1])
