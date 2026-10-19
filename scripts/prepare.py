"""Prepare pinned upstream for a LOCAL Windows test. Never modify an existing user's database."""
import argparse
import re
import shutil
import subprocess
from pathlib import Path

PIN = '1732e1f258b69dd812de96d35fd223624b950128'
URL = 'https://github.com/nvt25/Online_nro.git'

def prepare(upstream, output):
    actual = subprocess.check_output(['git', '-C', str(upstream), 'rev-parse', 'HEAD'], text=True).strip()
    if actual != PIN:
        raise ValueError('Upstream commit mismatch: ' + actual)
    if output.exists():
        raise ValueError('Output exists; choose a new build directory (no automatic deletion).')
    server = output / 'server'
    shutil.copytree(upstream / 'NgocRongOnline-Server', server,
                    ignore=shutil.ignore_patterns('build', 'dist', 'log', 'logs', 'nbproject', '*.sql'))
    for p in (server / 'src').rglob('*.java'):
        source = p.read_text(encoding='utf-8-sig')
        # These unused imports refer to Nashorn internals removed from modern JDKs.
        source = re.sub(r'^import jdk\.nashorn\.[^\n]+\n', '', source, flags=re.M)
        p.write_text(source, encoding='utf-8')
    db = server / 'src/server/DBService.java'
    source = db.read_text(encoding='utf-8')
    source = source.replace('"jdbc:mysql://127.0.0.1:3306/server"',
        'System.getenv().getOrDefault("NRO_DB_URL", "jdbc:mysql://127.0.0.1:13306/server")')
    source = source.replace('String user = "root";', 'String user = "nro";')
    source = source.replace('String password = "";',
        'String password = System.getenv("NRO_DB_PASSWORD");')
    db.write_text(source, encoding='utf-8')
    manager = server / 'src/server/Manager.java'
    source = manager.read_text(encoding='utf-8').replace('System.out.println("config: " + key + "-" + value);',
        'System.out.println("config: " + key + "-" + (key.contains("password") ? "[redacted]" : value));')
    source = source.replace('this.loadDataBase();',
        'this.mysql_user = "nro";\n        this.mysql_pass = System.getenv("NRO_DB_PASSWORD");\n        this.loadDataBase();')
    manager.write_text(source, encoding='utf-8')
    # The upstream listener ignores host; force loopback for this local-only package.
    entry = server / 'src/server/Server.java'
    source = entry.read_text(encoding='utf-8').replace('new ServerSocket(Server.manager.port)',
        'new ServerSocket(Server.manager.port, 50, java.net.InetAddress.getByName("127.0.0.1"))')
    entry.write_text(source, encoding='utf-8')
    controller = server / 'src/server/Controller.java'
    source = controller.read_text(encoding='utf-8').replace('Util.log("user " + user + " - pass " + pass);',
        'Util.log("Login attempt received");')
    controller.write_text(source, encoding='utf-8')
    (server / 'ninja.conf').write_text('''# Local Windows test only
host: 127.0.0.1
port: 14445
mysql-host: 127.0.0.1
mysql-port: 13306
mysql-user: nro
mysql-password:
mysql-database: server
version-Data: 98
version-Map: 23
version-Skill: 2
version-Item: 6
''', encoding='utf-8')
    # Save to the JSON columns read by this generation of Player.setup.
    overlay = Path(__file__).resolve().parents[1] / 'server-overlay'
    shutil.copytree(overlay, server / 'src', dirs_exist_ok=True)
    controller = server / 'src/server/Controller.java'
    source = controller.read_text(encoding='utf-8').replace('session.player.timer.cancel();',
        'session.player.timer.cancel();\n            try { real.player.LocalPlayerStore.save(session.player); }\n            catch (Exception e) { System.err.println("PLAYER SAVE FAILED"); e.printStackTrace(); }')
    controller.write_text(source, encoding='utf-8')
    # Keep static game data, remove all upstream account/character/clan records.
    sql = (upstream / 'NgocRongOnline-Server/server.sql').read_text(encoding='utf-8-sig')
    private_tables = ['account', 'clan', 'clan_member', 'clan_message', 'player',
        'player_bag', 'player_body', 'player_box', 'player_point', 'player_skill', 'player_task']
    for table in private_tables:
        sql = re.sub(r'INSERT INTO `' + re.escape(table) + r'`[\s\S]*?;\s*', '', sql)
    # Sparse JSON inventories can exceed the upstream 999-character field limit.
    sql += '\nALTER TABLE player MODIFY itembag MEDIUMTEXT NOT NULL, MODIFY itembox MEDIUMTEXT NOT NULL, MODIFY itembody MEDIUMTEXT NOT NULL, MODIFY skill TEXT NOT NULL;\n'
    (server / 'seed.sql').write_text(sql, encoding='utf-8')
    shutil.copytree(upstream / 'Client/Dragonboy_vn_v230', output / 'client')
    print('Prepared', output, 'at upstream', PIN)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('upstream', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    prepare(args.upstream, args.output)
