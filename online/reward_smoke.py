"""Integration tests invoked against the isolated live Linux CI fixture."""
import concurrent.futures
import http.cookiejar
import json
import os
import time
import urllib.error
import urllib.request
import uuid


def run(root, sql, protocol):
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    csrf = ''
    def call(path, body=None, expected=200):
        req = urllib.request.Request('http://127.0.0.1:14447'+path,
            data=json.dumps(body).encode() if body is not None else None,
            headers={'Content-Type':'application/json','X-CSRF':csrf} if body is not None else {})
        try:
            with opener.open(req,timeout=90) as r: status, data = r.status, json.load(r)
        except urllib.error.HTTPError as e: status, data = e.code, json.load(e)
        assert status == expected, (path, status, data)
        return data
    csrf = call('/api/login', {'password':os.environ['NRO_ADMIN_PASSWORD']})['csrf']
    state = call('/api/state')
    user = next(a['id'] for a in state['accounts'] if a['username']=='tester')
    other = next(a['id'] for a in state['accounts'] if a['username']=='tester2')
    assert state['reward_caps']['upgrade']==7 and state['reward_caps']['stars']==8
    assert len(state['items']) > 2000 and len(state['power_limits']) == 12
    max_limit = state['power_limits'][-1]
    all_reward = {'kind':'BUNDLE','selection':{'mode':'all','quantity':1,'upgrade':0,'stars':0}}
    all_preview = call('/api/reward/preview', {'reward':all_reward})
    assert all_preview['count']==len(state['items'])
    assert len({r.get('item_id',r.get('currency')) for r in all_preview['reward']['rewards']})==len(state['items'])
    floor=call('/api/reward/preview',{'reward':{'kind':'BUNDLE','gem':state['reward_caps']['gem'],'currency_mode':'AT_LEAST'}})
    assert floor['reward']['rewards'][0]['mode']=='AT_LEAST'
    call('/api/reward/preview', {'reward':{'kind':'BUNDLE','gem':-1}},400)
    call('/api/reward/preview', {'reward':{'kind':'BUNDLE','selection':{'mode':'selected','ids':[32767]}}},400)
    call('/api/reward/preview', {'reward':{'kind':'BUNDLE','selection':{'mode':'selected','ids':[0],'upgrade':8}}},400)
    call('/api/reward/preview', {'reward':{'kind':'POINTS','limit_index':0,'power':max_limit['power'],'potential':0}},400)
    gear=next(i for i in state['items'] if 0<=i['type']<=4 and i['base_options'] and not any(o[0]==72 for o in i['base_options']))
    upgraded=call('/api/reward/preview',{'reward':{'kind':'BUNDLE','selection':{'mode':'selected','ids':[gear['id']],'upgrade':7,'stars':8}}})['reward']['rewards'][0]
    assert [72,7] in upgraded['options'] and [107,8] in upgraded['options']
    reward={'kind':'BUNDLE','gem':123,'power':1500000,'potential':1000000000,'limit_index':max_limit['index'],'max_base':True,'selection':{'mode':'selected','ids':[0,1],'quantity':1,'upgrade':0,'stars':0}}
    package_id=str(uuid.uuid4())
    call('/api/package/save',{'request_id':package_id,'name':'CI reusable bundle','reward':reward})
    saved={'kind':'BUNDLE','package_id':package_id}
    assert call('/api/reward/preview',{'reward':saved})['count']==4
    code={'code':'LIVECIBUNDLE','max_uses':5,'expires_ms':int(time.time()*1000)+3600000,'reward':saved,'restricted':True,'allowed_accounts':[user]}
    call('/api/code/create',code)
    call('/api/code/create',dict(code,code='LIVECIEMPTY',allowed_accounts=[]),400)
    call('/api/code/redeem',{'account_id':other,'code':code['code']},400)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        def attempt(_):
            try: return call('/api/code/redeem',{'account_id':user,'code':code['code']})
            except AssertionError as e:
                assert '400' in str(e)
                return None
        results=list(pool.map(attempt,range(2)))
    assert sum(r is not None for r in results)==1
    assert sql("SELECT used FROM live_admin_codes WHERE code='LIVECIBUNDLE'")=='1'
    # Queue duplicate direct bundles: one parent and exactly two children.
    direct={'request_id':str(uuid.uuid4()),'account_id':other,'reason':'CI idempotent','reward':{'kind':'BUNDLE','gem':2,'ruby':3}}
    call('/api/grant',direct);assert call('/api/grant',direct)['duplicate']
    assert sql("SELECT COUNT(*) FROM live_admin_actions WHERE id LIKE '"+direct['request_id']+"_%'")=='2'
    changed=dict(direct,reward={'kind':'BUNDLE','gem':4});call('/api/grant',changed,400)
    before=json.loads(sql("SELECT data_inventory FROM player WHERE name='pcprobe'"))
    box=json.loads(sql("SELECT items_box FROM player WHERE name='pcprobe'"))
    filled=[{'temp_id':13,'quantity':1,'option':[],'create_time':int(time.time()*1000)} for _ in box]
    def write_box(value):
        value=json.dumps(value,separators=(',',':')).replace("'","''")
        sql("UPDATE player SET items_box='"+value+"' WHERE name='pcprobe'")
    write_box(filled)
    def relog():
        c=protocol.Client();c.login('beta-fixture-password');c.world();c.sock.close();time.sleep(5)
    relog()
    money=json.loads(sql("SELECT data_inventory FROM player WHERE name='pcprobe'"))
    points=json.loads(sql("SELECT data_point FROM player WHERE name='pcprobe'"))
    assert money[1]==before[1]+123
    assert points[11]==1500000 and points[8]>=1000000000 and points[4]==max_limit['index']
    assert points[12]==max_limit['hp'] and points[10]==max_limit['damage']
    assert sql(f"SELECT COUNT(*) FROM live_admin_actions WHERE account_id={user} AND status='PENDING'")=='2'
    for expected in (1,0):
        box=json.loads(sql("SELECT items_box FROM player WHERE name='pcprobe'"));box[0]={'temp_id':-1};write_box(box);relog()
        assert sql(f"SELECT COUNT(*) FROM live_admin_actions WHERE account_id={user} AND status='PENDING'")==str(expected)
    relog()
    assert json.loads(sql("SELECT data_inventory FROM player WHERE name='pcprobe'"))==money
    assert sql("SELECT is_admin FROM account WHERE id="+str(user))=='0'
    assert sql("SELECT COUNT(*) FROM live_admin_redemptions WHERE code='LIVECIBUNDLE'")=='1'
    print('PASS: full catalog, package save/load, real levels, upgrade/stars, private codes, concurrent redemption, idempotency, power/potential/base caps, full-box resume, no duplicate currency and no admin escalation')
    from playwright.sync_api import sync_playwright, expect
    errors=[]
    with sync_playwright() as p:
        browser=p.chromium.launch(headless=True)
        page=browser.new_page(viewport={'width':1440,'height':1000})
        page.on('pageerror',lambda e: errors.append(str(e)))
        page.goto('http://127.0.0.1:14447/')
        page.locator('#login-form input').fill(os.environ['NRO_ADMIN_PASSWORD']);page.locator('#login-form button').click()
        page.locator('#app').wait_for(state='visible');expect(page.locator('#stat-accounts')).not_to_have_text('—')
        page.locator('nav [data-tab=events]').click()
        page.select_option('#bundle-preset','starter')
        page.locator('#bundle-preview').click()
        expect(page.locator('#bundle-preview-result')).to_contain_text('phần thưởng')
        page.select_option('#bundle-preset','admin')
        assert page.locator('#code-form [name=scope]').input_value()=='restricted'
        assert page.locator('#bundle-companion').input_value()=='MABU'
        page.select_option('#bundle-preset','custom');page.select_option('#bundle-group','healing')
        page.locator('#bundle-select-filtered').click()
        assert page.locator('#bundle-items input:checked').count()>0
        page.screenshot(path=str(root/'logs/reward-builder.png'),full_page=True)
        page.set_viewport_size({'width':390,'height':844})
        assert page.evaluate('() => document.documentElement.scrollWidth <= window.innerWidth')
        assert not errors,errors
        browser.close()
    print('PASS: real browser presets, scope, item selection, preview and mobile layout; no JavaScript errors')
