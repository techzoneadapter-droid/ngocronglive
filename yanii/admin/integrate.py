from pathlib import Path
import shutil

def replace(path, old, new):
    s=path.read_text(encoding='utf-8-sig')
    if s.count(old)!=1:raise ValueError(f'Expected one admin integration point in {path}: {old[:90]}')
    path.write_text(s.replace(old,new),encoding='utf-8')

def integrate(output):
    root=Path(__file__).parent
    import runpy
    runpy.run_path(str(root.parent/'companion/prepare_assets.py'))['prepare'](output)
    java=output/'server/src/main/java/nro/admin'
    java.mkdir(parents=True,exist_ok=True)
    for name in ('LiveAdmin.java','RewardCatalog.java','OnlineRewards.java','StarterCodes.java','TradeCommit.java'):shutil.copy2(root/name,java/name)
    resources=output/'server/src/main/resources/live-admin'
    resources.mkdir(parents=True,exist_ok=True)
    for name in ['index.html','admin.js','admin.css','schema.sql']:shutil.copy2(root/name,resources/name)
    replace(output/'server/src/main/java/nro/server/ServerManager.java','            activeServerSocket();','            nro.admin.LiveAdmin.start();\n            activeServerSocket();')
    # The upstream maintenance close tries to restart run.bat. Owner stop must stay stopped.
    replace(output/'server/src/main/java/nro/server/ServerManager.java','''        try {
            Process process = Runtime.getRuntime().exec("cmd /c start run.bat");

            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }''','// Owner stop never restarts the server automatically.')
    replace(output/'server/src/main/java/nro/jdbc/daos/GodGK.java','    public static Player loadPlayer(Session session) {\n        try {','    public static Player loadPlayer(Session session) {\n        try {\n            nro.admin.LiveAdmin.beforeLogin(session.userId);')
    replace(output/'server/src/main/java/nro/models/player/NPoint.java','tiemNang *= Manager.RATE_EXP_SERVER;','tiemNang = nro.live.GrowthService.calculate(this.player, tiemNang);')
    replace(output/'server/src/main/java/nro/services/GiftService.java','    public void use(Player player, String code) {','    public void use(Player player, String code) {\n        if (code != null && nro.admin.LiveAdmin.hasCode(code)) {\n            Service.getInstance().sendThongBaoOK(player, nro.admin.LiveAdmin.redeemOnline(player, code));\n            return;\n        }')
    # Do not tell login service the old character is gone until its final save is finished.
    p=output/'server/src/main/java/nro/server/Client.java'
    old='''            if (login != null && login.isConnected()) {
                login.getService().logout(session.userId);
            }
'''
    replace(p,old,'')
    replace(p,'                session.player.dispose();\n            }','                session.player.dispose();\n            }\n'+old)
