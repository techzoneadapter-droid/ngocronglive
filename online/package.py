import argparse
import hashlib
import json
import shutil
import tarfile
from pathlib import Path
from client_host import patch

def package(base, output, host, commit):
    output.mkdir(parents=True, exist_ok=False)
    server = output / 'NgocRongLive-Linux-beta'
    client = output / 'NgocRongLive-PC-online'
    server.mkdir()
    client.mkdir()
    shutil.copytree(base / 'server', server / 'server', ignore=shutil.ignore_patterns('src', 'target', '.idea', 'log', 'logs', '*.xml', '*.bat'))
    (server / 'login').mkdir()
    shutil.copy2(base / 'login/app.jar', server / 'login/app.jar')
    # Never ship upstream connection credentials or generated CI credentials.
    (server / 'server/resources/config/server.properties').write_text('# Written by install.sh\n')
    for name in ('install.sh', 'configure.py', 'status.sh', 'stop.sh', 'backup.sh'):
        shutil.copy2(Path(__file__).parent / name, server / name)
    shutil.copytree(base / 'client', client / 'client')
    digest = patch(client / 'client/NroYanii_Data/il2cpp_data/Metadata/global-metadata.dat', host)
    for name in ('PLAY-ONLINE.cmd', 'REPORT.cmd', 'report.ps1'):
        shutil.copy2(Path(__file__).parent / name, client / name)
    info = {'commit': commit, 'host': host, 'game_port': 14445, 'bootstrap_port': 14446,
            'client': 'tested supplied Yanii binary, online bootstrap patch',
            'source_client_f10': False, 'metadata_sha256': digest}
    for folder in (server, client):
        (folder / 'build-info.json').write_text(json.dumps(info, indent=2))
    (client / 'README.txt').write_text('Run PLAY-ONLINE.cmd. No Java/database needed on tester PCs.\nRun REPORT.cmd to collect logs and open the GitHub issue form. Inspect before uploading.\nThis beta uses the supplied Yanii client; the new F10 source client is not included.\nServer: ' + host + '\n')
    with tarfile.open(output / 'NgocRongLive-Linux-beta.tar.gz', 'w:gz') as tar:
        tar.add(server, arcname=server.name)
    shutil.make_archive(str(output / 'NgocRongLive-PC-online'), 'zip', client)
    for file in sorted(output.glob('*.*')):
        if file.is_file(): print(file.name, file.stat().st_size, hashlib.sha256(file.read_bytes()).hexdigest())

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('base', type=Path)
    p.add_argument('output', type=Path)
    p.add_argument('host')
    p.add_argument('commit')
    args = p.parse_args()
    package(args.base, args.output, args.host, args.commit)
