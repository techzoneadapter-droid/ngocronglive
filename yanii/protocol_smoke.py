"""Integration checks for the replacement's own protocol and saved movement."""
import json, socket, struct, sys, time, subprocess
from pathlib import Path

def utf(s):
    b=s.encode('utf-8');return struct.pack('>H',len(b))+b
class Client:
    def __init__(self, username='tester', character='pcprobe'):
        self.username=username;self.character=character
        self.sock=socket.create_connection(('127.0.0.1',14445),timeout=20);self.sock.settimeout(20)
        self.sock.sendall(bytes([229,0,0]));h=self.read(3);assert h[0]==229
        p=self.read(int.from_bytes(h[1:],'big'));self.key=list(p[1:1+p[0]])
        for i in range(1,len(self.key)): self.key[i]^=self.key[i-1]
        self.tx=self.rx=0
    def read(self,n):
        b=bytearray()
        while len(b)<n:
            p=self.sock.recv(n-len(b))
            if not p:raise EOFError('Server disconnected')
            b.extend(p)
        return bytes(b)
    def send(self,code,p=b''):
        b=bytearray(bytes([code&255])+struct.pack('>H',len(p))+p)
        for i in range(len(b)):
            b[i]^=self.key[self.tx];self.tx=(self.tx+1)%len(self.key)
        self.sock.sendall(b)
    def recv(self):
        def byte(extra=False):
            raw=self.read(1)[0]
            if extra:raw=(raw+128)&255
            value=raw^self.key[self.rx];self.rx=(self.rx+1)%len(self.key);return value
        c=byte();c=c if c<128 else c-256
        if c in (-32,-66,-74,11,-67,-87,66): n=byte(True)|(byte(True)<<8)|(byte(True)<<16)
        else:n=(byte()<<8)|byte()
        assert n<16000000
        return c,bytes(byte() for _ in range(n))
    def until(self,predicate):
        for _ in range(180):
            c,p=self.recv()
            if c in (-26,-25): print('Server notice:',p,flush=True)
            if predicate(c,p):return c,p
        raise AssertionError('Expected protocol response missing')
    def login(self,password):
        self.send(-29,bytes([2,4,1,0])+struct.pack('>ii',1024,600)+bytes([1,1])+utf('PC|2.4.0'))
        self.send(-29,bytes([0])+utf(self.username)+utf(password)+utf('2.4.0')+bytes([0]))
        self.until(lambda c,p:c==-28 and p and p[0]==4)
        self.send(-87)
        for k in (6,7,8):self.send(-28,bytes([k]))
        seen=set();parts=set()
        for _ in range(180):
            c,p=self.recv()
            if c==-87:seen.add('data')
            if c==-28 and p:
                seen.add(p[0])
                if p[0]==8:parts.add(p[2])
            if {'data',6,7,8}<=seen and {0,1,2}<=parts:break
        assert {'data',6,7,8}<=seen and {0,1,2}<=parts
        print('PASS: authentication and replacement data/map/skill/item downloads',flush=True)
        self.send(-38)
    def world(self):
        state=False;world=None
        for _ in range(180):
            c,p=self.recv()
            if c==-30 and p and p[0]==0:
                size=int.from_bytes(p[9:11],'big');assert p[11:11+size]==self.character.encode('utf-8')
                state=True
            if c==-24:
                name_size=int.from_bytes(p[5:7],'big');o=7+name_size
                zone=p[o];x,y=struct.unpack('>hh',p[o+1:o+5]);world=(p[0],x,y)
            if state and world:
                self.send(-39)
                print('PASS: player and world map received:',world,flush=True);return world
        raise AssertionError('World entry failed')
def run(root,password):
    c=Client();c.login(password);c.until(lambda code,p:code==2)
    c.send(-28,bytes([2])+utf('pcprobe')+bytes([0,64]))
    mapid,x,y=c.world()
    for delta in (8,16,24):c.send(-7,bytes([0])+struct.pack('>hh',x+delta,y));time.sleep(.15)
    # Query an authoritative persisted location after disconnect; no map switch requested.
    c.sock.close();time.sleep(4)
    db=root/'runtime/mariadb/bin/mariadb.exe'
    result=subprocess.check_output([str(db),'--defaults-file='+str(root/'local-data/client.ini'),'-N','-B','ngocrong','-e',"SELECT data_location FROM player WHERE name='pcprobe'"],text=True)
    location=json.loads(result.strip());assert location[:3]==[x+24,y,mapid],('Movement changed map or was not saved',location,[x+24,y,mapid])
    task_query=[str(db),'--defaults-file='+str(root/'local-data/client.ini'),'-N','-B','ngocrong','-e',"SELECT data_task FROM player WHERE name='pcprobe'"]
    task=json.loads(subprocess.check_output(task_query,text=True).strip())
    assert task == [0,0,0], ('New character skipped the introductory quest',task)
    print('PASS: new character starts at introductory quest 0, step 0',flush=True)
    print('PASS: movement stayed in map and coordinates persisted:',location,flush=True)
    c=Client();c.login(password);newmap,_,_=c.world();assert newmap==mapid;c.sock.close()
    time.sleep(4)
    saved_task=json.loads(subprocess.check_output(task_query,text=True).strip())
    assert saved_task == task, ('Quest progress changed during relogin',task,saved_task)
    print('PASS: introductory quest persisted across relogin',flush=True)
    print('PASS: saved character can log in again',flush=True)
if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8',errors='backslashreplace');sys.stderr.reconfigure(encoding='utf-8',errors='backslashreplace')
    root=Path(__file__).resolve().parent
    try:run(root,json.loads(Path(sys.argv[1]).read_text())['player'])
    except Exception:
        for n in ('server-error.log','server.log','login-error.log','login.log'):
            p=root/'logs'/n
            if p.exists():print(n+':\n'+'\n'.join(p.read_text(encoding='utf-8',errors='replace').splitlines()[-55:]),flush=True)
        raise
