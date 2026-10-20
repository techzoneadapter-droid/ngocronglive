"""Run only against the isolated CI database created by launch.ps1 -SmokeTest."""
import concurrent.futures, http.cookiejar, json, os, subprocess, time, urllib.request, urllib.error, uuid
from pathlib import Path
from protocol_smoke import Client
root=Path(__file__).resolve().parent
jar=http.cookiejar.CookieJar()
opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
base='http://127.0.0.1:14447'
csrf=''
def call(path,body=None,expected=200,token=None):
    headers={}
    if body is not None:headers={'Content-Type':'application/json','X-CSRF':csrf if token is None else token}
    request=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
    try:
        with opener.open(request,timeout=30) as response:status=response.status;data=json.load(response)
    except urllib.error.HTTPError as e:status=e.code;data=json.load(e)
    assert status==expected,(path,status,data)
    return data

def sql(query):
    binary=root/'runtime/mariadb/bin/mariadb.exe'
    return subprocess.check_output([str(binary),'--defaults-file='+str(root/'local-data/client.ini'),'-N','-B','ngocrong','-e',query],text=True).strip()

def money():return json.loads(sql("SELECT data_inventory FROM player WHERE name='pcprobe'"))
def reward(amount):return dict(kind='CURRENCY',currency='gem',amount=amount)
def run():
    global csrf
    call('/api/state',expected=401)
    csrf=call('/api/login',{'password':os.environ['NRO_ADMIN_PASSWORD']})['csrf']
    state=call('/api/state');account=next(a for a in state['accounts'] if a['username']=='tester');uid=account['id'];before=money();account_money=sql(f'SELECT vnd,tongnap FROM account WHERE id={uid}')
    call('/api/grant',dict(request_id=str(uuid.uuid4()),account_id=uid,reward=reward(999),reason='CSRF rejection'),expected=403,token='invalid')
    user='ciowner';password='CIaccount-test-123'
    call('/api/account/create',dict(username=user,password=password));assert any(a['username']==user for a in call('/api/state')['accounts'])
    grant=dict(request_id=str(uuid.uuid4()),account_id=uid,reward=reward(123),reason='CI duplicate retry')
    call('/api/grant',grant);assert call('/api/grant',grant)['duplicate']
    changed=dict(grant,reward=reward(999));call('/api/grant',changed,expected=400)
    call('/api/grant',dict(request_id=str(uuid.uuid4()),account_id=uid,reward=dict(kind='ITEM',item_id=32767,quantity=1,options=[]),reason='invalid item'),expected=400)
    call('/api/grant',dict(request_id=str(uuid.uuid4()),account_id=uid,reward=dict(kind='ITEM',item_id=0,quantity=1,options=[[47,10]]),reason='CI item'))
    rejected=call('/api/grant',dict(request_id=str(uuid.uuid4()),account_id=uid,reward=reward(-1000000000),reason='CI insufficient funds'))
    order=call('/api/order/create',dict(request_id=str(uuid.uuid4()),account_id=uid,amount_vnd=10000,reward=reward(77)))
    call('/api/order/confirm',dict(id=order['id']));assert call('/api/order/confirm',dict(id=order['id']))['duplicate']
    now=int(time.time()*1000)
    event=dict(request_id=str(uuid.uuid4()),title='CI exp',start_ms=now-1000,end_ms=now+3600000,multiplier=2,enabled=True)
    call('/api/event',event);assert call('/api/state')['exp_multiplier']==2
    event['enabled']=False;call('/api/event',event);assert call('/api/state')['exp_multiplier']==1
    call('/api/code/create',dict(code='LIVECITEST',max_uses=1,expires_ms=now+3600000,reward=reward(9)))
    call('/api/code/redeem',dict(account_id=uid,code='LIVECITEST'));call('/api/code/redeem',dict(account_id=uid,code='LIVECITEST'),expected=400)
    other=next(a['id'] for a in call('/api/state')['accounts'] if a['username']==user)
    call('/api/code/redeem',dict(account_id=other,code='LIVECITEST'),expected=400)
    call('/api/account/ban',dict(account_id=other,ban=True,reason='CI ban'));assert sql(f'SELECT ban FROM account WHERE id={other}')=='1'
    call('/api/account/ban',dict(account_id=other,ban=False,reason='CI unban'))
    call('/api/account/password',dict(account_id=other,password='CInew-pass-456',reason='CI reset'))
    call('/api/announcement',dict(message='CI owner console test'))
    assert money()==before,'Queued rewards changed live/offline data before login'
    credentials=json.loads((root/'local-data/credentials.json').read_text())
    c=Client();c.login(credentials['player']);c.world();c.sock.close();time.sleep(4)
    after=money();assert after[1]==before[1]+123+77+9,(before,after)
    assert sql("SELECT COUNT(*) FROM live_admin_actions WHERE status='APPLIED'")=='4'
    assert sql("SELECT COUNT(*) FROM live_admin_actions WHERE status='REJECTED'")=='1'
    box=json.loads(sql("SELECT items_box FROM player WHERE name='pcprobe'"));assert any(i['temp_id']==0 and i['quantity']==1 and [47,10] in i['option'] for i in box)
    c=Client();c.login(credentials['player']);c.world();c.sock.close();time.sleep(4)
    assert money()==after,'Rewards duplicated on next login'
    assert sql(f'SELECT vnd,tongnap FROM account WHERE id={uid}')==account_money,'Test order modified real money columns'
    state=call('/api/state');assert all('password' not in a for a in state['accounts']);assert all(password not in a['detail'] for a in state['audit'])
    call('/api/logout',{});call('/api/state',expected=401)
    print('PASS: admin auth/CSRF, accounts, queued currency/items, retry deduplication, test payments, giftcode limits, XP scheduling, persistence and no double delivery',flush=True)
if __name__=='__main__':run()
