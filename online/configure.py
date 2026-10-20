"""Write a fresh beta configuration. Passwords come from root-only environment."""
import argparse
import os
import re
from pathlib import Path

def configure(root, host, port=3306):
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9.-]{0,252}', host):
        raise ValueError('Invalid public host')
    password = os.environ['NRO_DB_PASSWORD']
    if not re.fullmatch('[a-f0-9]{48}', password):
        raise ValueError('DB password must be 24 random hex bytes')
    database = 'nrolive_beta'
    max_players = int(os.environ.get('NRO_MAX_PLAYERS', '20'))
    if not 1 <= max_players <= 50:
        raise ValueError('Beta cap must be between 1 and 50')
    (root / 'local-data').mkdir(exist_ok=True)
    (root / 'logs').mkdir(exist_ok=True)
    game = f'''server.db.ip=127.0.0.1
server.db.port={port}
server.db.name={database}
server.db.us=nrolive
server.db.pw={password}
server.db.max=4
server.sv=1
server.sv1=NgocRongLive:{host}:14445:0,0,0
server.port=14445
server.debug=false
server.waitlogin=1
server.maxperip=5
server.maxplayer={max_players}
server.expserver=3
server.name=NgocRongLive-Beta
server.domain={host}
login.host=127.0.0.1
server.event=0
server.activeKey=false
api.port=8181
api.key={password}
'''
    (root / 'server/resources/config/server.properties').write_text(game)
    (root / 'login/server.ini').write_text(f'''server.port=3105
db.port={port}
db.host=127.0.0.1
db.user=nrolive
db.password={password}
db.name={database}
db.driver=com.mysql.cj.jdbc.Driver
admin.mode=0
wait.login=0
''')
    defaults = root / 'local-data/client.ini'
    defaults.write_text(f'[client]\nuser=nrolive\npassword={password}\nhost=127.0.0.1\nport={port}\nprotocol=tcp\n')
    defaults.chmod(0o600)

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('root', type=Path)
    p.add_argument('host')
    p.add_argument('--db-port', type=int, default=3306)
    args = p.parse_args()
    configure(args.root.resolve(), args.host, args.db_port)
