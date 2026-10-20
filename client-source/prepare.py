"""Materialize pinned Unity resources and overlay reviewed source; no game execution."""
import argparse
import shutil
import subprocess
from pathlib import Path

PIN = '26cad142d657449bd27f694f0214a43c9382036d'
URL = 'https://github.com/duphatminh/NgocRongOnline_Clone.git'

def prepare(root, cache, output):
    if output.exists():
        raise ValueError('Output exists. Choose a fresh directory; existing files are never deleted.')
    if not cache.exists():
        subprocess.run(['git', 'clone', '--filter=blob:none', '--no-checkout', URL, str(cache)], check=True)
    subprocess.run(['git', '-C', str(cache), 'cat-file', '-e', PIN + '^{commit}'], check=True)
    subprocess.run(['git', '-C', str(cache), 'sparse-checkout', 'set', 'NRO_UI'], check=True)
    subprocess.run(['git', '-C', str(cache), 'checkout', '--detach', PIN], check=True)
    actual = subprocess.check_output(['git', '-C', str(cache), 'rev-parse', 'HEAD'], text=True).strip()
    if actual != PIN:
        raise ValueError('Unexpected upstream revision')
    shutil.copytree(cache / 'NRO_UI', output)
    for part in ('Assets/Scripts', 'Assets/Scenes', 'Assets/Editor', 'Packages', 'ProjectSettings'):
        source = root / part
        if source.exists():
            shutil.copytree(source, output / part, dirs_exist_ok=True)
    # Isolate cache/credentials/configuration from original clients.
    print('Unity source prepared at', output)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    prepare(Path(__file__).resolve().parent, args.cache.resolve(), args.output.resolve())
