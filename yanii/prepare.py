import argparse, json, re, shutil, subprocess
from pathlib import Path
PIN='a443509f203eb21b7f39bb5a5234df4b1ddcfbea'
def prepare(upstream, output):
    if subprocess.check_output(['git','-C',str(upstream),'rev-parse','HEAD'],text=True).strip()!=PIN: raise ValueError('Wrong upstream revision')
    if output.exists(): raise ValueError('Output already exists')
    output.mkdir(parents=True)
    for src,dest in [('Server','server'),('Login','login'),('AppLocal','client')]:
        shutil.copytree(upstream/src,output/dest,ignore=shutil.ignore_patterns('target','log','logs','*.jar','run.bat','*.mp4'))
    # Unity's native player resolves <executable>_Data next to its executable.
    if (output/'client/Data').exists():
        (output/'client/Data').rename(output/'client/NroYanii_Data')
    from client_patch import patch_client
    patch_client(output/'client')
    for java in output.rglob('*.java'):
        java.write_text(java.read_text(encoding='utf-8-sig'), encoding='utf-8')
    shutil.copy2(upstream/'LICENSE',output/'UPSTREAM-LICENSE')
    # Record the supplied executable identity; companion metadata/DLL are patched.
    import hashlib
    (output/'provenance.json').write_text(json.dumps({'upstream':'https://github.com/yanii7905/Nro_Yanii','commit':PIN,'client_sha256':hashlib.sha256((output/'client/NroYanii.exe').read_bytes()).hexdigest()},indent=2))
    p=output/'server/src/main/java/nro/data/DataGame.java'
    s=p.read_text(encoding='utf-8-sig').replace('writeUTF(LINK_IP_PORT + ",0,0")', 'writeUTF(LINK_IP_PORT)')
    p.write_text(s,encoding='utf-8')
    p=output/'server/src/main/java/nro/server/Manager.java'
    s=p.read_text(encoding='utf-8-sig').replace('DataGame.LINK_IP_PORT = linkServer.substring(0, linkServer.length() - 1);', 'DataGame.LINK_IP_PORT = nro.LocalBootstrap.SERVER_LIST;')
    p.write_text(s,encoding='utf-8')
    p=output/'server/src/main/java/nro/server/ServerManager.java'
    s=p.read_text(encoding='utf-8-sig').replace('ServerBroadcaster.startBroadcasting', 'nro.ServerBroadcaster.startBroadcasting').replace('new ServerSocket(PORT)','new ServerSocket(PORT, 50, java.net.InetAddress.getLoopbackAddress())')
    s=s.replace('new Thread(AutoMaintenance.gI(), "Thread Bảo Trì Định Kỳ").start();','// Local package has no scheduled maintenance or idle busy loop.')
    s=s.replace('while (true) {\n                String line = sc.nextLine();','while (sc.hasNextLine()) {\n                String line = sc.nextLine();')
    # LAN broadcaster kept for supplied client's discovery; game TCP stays loopback.
    s=s.replace('public static void main(String[] args) {', 'public static void main(String[] args) {\n        nro.LocalBootstrap.start();')
    p.write_text(s,encoding='utf-8')
    shutil.copy2(Path(__file__).with_name('LocalBootstrap.java'), output/'server/src/main/java/nro/LocalBootstrap.java')
    p=output/'login/src/main/java/server/Server.java';s=p.read_text(encoding='utf-8-sig').replace('new ServerSocket(this.config.getListen())','new ServerSocket(this.config.getListen(), 50, java.net.InetAddress.getLoopbackAddress())').replace('while (true) {\n                String line = sc.nextLine();','while (sc.hasNextLine()) {\n                String line = sc.nextLine();');p.write_text(s,encoding='utf-8')
    p=output/'login/src/main/java/server/Config.java';s=p.read_text(encoding='utf-8-sig').replace('String.format("Config - %s: %s", t, u)','String.format("Config - %s: %s", t, t.toString().contains("password") ? "[redacted]" : u)');p.write_text(s,encoding='utf-8')
    p=output/'login/src/main/java/model/User.java';s=p.read_text(encoding='utf-8-sig');s=re.sub(r'rs.getInt\("account\.([^"\n]+)"\)',r'rs.getInt("\1")',s);p.write_text(s,encoding='utf-8')
    p=output/'server/src/main/java/nro/server/io/MessageSender.java'
    s=p.read_text(encoding='utf-8-sig').replace('import java.util.ArrayList;', 'import java.util.concurrent.ConcurrentLinkedQueue;').replace('ArrayList<Message>', 'ConcurrentLinkedQueue<Message>').replace('new ArrayList<>()', 'new ConcurrentLinkedQueue<>()').replace('sendingMessage.remove(0)', 'sendingMessage.poll()').replace('public void doSendMessage(Message msg)', 'public synchronized void doSendMessage(Message msg)')
    p.write_text(s,encoding='utf-8')
    p=output/'server/src/main/java/nro/noti/NotiManager.java'
    s=p.read_text(encoding='utf-8-sig').replace('Service.getInstance().sendThongBaoFromAdmin(player, alert.content);', 'if (alert != null) Service.getInstance().sendThongBaoFromAdmin(player, alert.content);')
    p.write_text(s,encoding='utf-8')
    # Ship static content only; do not redistribute upstream accounts/history/player saves.
    sql=(upstream/'Tutorial/Database/ngocrong.sql').read_text(encoding='utf-8-sig')
    keep={'attribute_server','attribute_template','cai_trang','caption','collection_book','event','flag_bag','head_avatar','img_by_name','intrinsic','item_option_template','item_shop','item_shop_option','item_template','map_template','mini_pet','mob_template','nclass','npc_template','skill','skill_template','shop','shop_tab','task_main_template','task_sub_template','side_task_template','radar_template','mount','pet','pet_template','task_template','achivements','global_data','part','pet_follow','power_limit','small_version','tab_shop','type_map'}
    sql=re.sub(r'INSERT INTO `([^`]+)`[\s\S]*?;\s*',lambda m:m.group(0) if m.group(1) in keep else '',sql)
    (output/'server/seed.sql').write_text(sql,encoding='utf-8')
    # Reduce pools to a local test workload; no gameplay/protocol rewrites.
    p=output/'server/src/main/java/nro/jdbc/DBHika.java';s=p.read_text(encoding='utf-8-sig').replace('setMinimumIdle(20)','setMinimumIdle(2)').replace('setMaximumPoolSize(100)','setMaximumPoolSize(10)');p.write_text(s,encoding='utf-8')
    print('Prepared Yanii at',PIN)
if __name__=='__main__':
    a=argparse.ArgumentParser();a.add_argument('upstream',type=Path);a.add_argument('output',type=Path);n=a.parse_args();prepare(n.upstream,n.output)
