"""Exercise tester rules over the encrypted game protocol and real SQL, including online delivery and egg trade."""
import http.cookiejar,json,os,struct,time,urllib.request,uuid

def run(root,sql,protocol):
    jar=http.cookiejar.CookieJar();opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar));csrf=''
    def api(path,body=None):
        req=urllib.request.Request('http://127.0.0.1:14447'+path,data=json.dumps(body).encode() if body is not None else None,headers={'Content-Type':'application/json','X-CSRF':csrf})
        with opener.open(req,timeout=90) as r:return json.load(r)
    csrf=api('/api/login',{'password':os.environ['NRO_ADMIN_PASSWORD']})['csrf']
    def row(col,name='rulespc'):return json.loads(sql("SELECT "+col+" FROM player WHERE name='"+name+"'"))
    def create(user,char):
        api('/api/account/create',{'username':user,'password':'beta-fixture-password'})
        c=protocol.Client(username=user,character=char);c.login('beta-fixture-password');c.until(lambda code,p:code==2);c.send(-28,bytes([2])+protocol.utf(char)+bytes([0,64]));c.world();return c
    def chat(c,text,notice=None):
        c.send(44,protocol.utf(text))
        if notice:return c.until(lambda code,p:notice.encode() in p)[1]
    def code(name,rewards,replace=False):
        api('/api/code/create',{'code':name,'max_uses':100,'expires_ms':int(time.time()*1000)+3600000,'reward':{'kind':'BUNDLE','rewards':rewards},'replace_existing':replace})
    def item(id,quantity=1):return {'kind':'ITEM','item_id':id,'quantity':quantity,'options':[]}
    def currency(kind,n):return {'kind':'CURRENCY','currency':kind,'amount':n,'mode':'ADD'}
    def use(c,id):
        index=next(i for i,v in enumerate(row('items_bag')) if v['temp_id']==id)
        c.send(-43,bytes([0,1,index]))
    def enter(c,index,map_id):
        chat(c,'khuup');c.until(lambda code,p:code==32 and 'Chọn khu luyện'.encode() in p)
        c.send(32,struct.pack('>hB',5,index));_,p=c.until(lambda code,p:code==-24 and p[0]==map_id);c.send(-39);return p
    c=create('rulesuser','rulespc')
    code('TEST',[item(76,10000000000),currency('ruby',5000),{'kind':'POINTS','limit_index':0,'power':1500000,'potential':1000000000,'max_base':False},item(568,2),item(2154),item(2155,2),item(2156)])
    before=row('data_inventory')[0];chat(c,'code TEST','Đã nhận quà ngay')
    assert row('data_inventory')[0]==before+10000000000,'Gold above 9999 or int cap was lost'
    assert row('data_inventory')[2]==5000
    chat(c,'code TEST','đã dùng mã');assert sql("SELECT COUNT(*) FROM live_admin_redemptions WHERE code='TEST'")=='1'
    bag=row('items_bag');assert sum(v['quantity'] for v in bag if v['temp_id']==568)==2 and row('data_mabu_egg')=={}
    chat(c,'buff','Tổng hiện tại x2.0')
    use(c,2155);c.until(lambda code,p:'Đã dùng'.encode() in p);chat(c,'buff','Tổng hiện tại x3.0')
    player_id=sql("SELECT id FROM player WHERE name='rulespc'")
    first=json.loads(sql('SELECT buffs FROM live_player_growth WHERE player_id='+player_id))['2155']
    use(c,2155);c.until(lambda code,p:'Đã dùng'.encode() in p);chat(c,'buff','Tổng hiện tại x3.0')
    second=json.loads(sql('SELECT buffs FROM live_player_growth WHERE player_id='+player_id))['2155'];assert second-first==3600000
    use(c,2156);c.until(lambda code,p:'Đã dùng'.encode() in p);chat(c,'buff','Tổng hiện tại x5.0')
    # A short arbitrary code, immediate progress/currency/item delivery, repeat guard and additive persisted timers.
    enter(c,0,176)
    peer=create('rulespeer','rulespeer');enter(peer,0,176)
    peer_id=int(sql("SELECT id FROM player WHERE name='rulespeer'"))
    c.send(-86,bytes([0])+struct.pack('>i',peer_id));peer.until(lambda code,p:code==-86 and p[0]==0)
    peer.send(-86,bytes([1])+struct.pack('>i',int(player_id)));c.until(lambda code,p:code==-86 and p[0]==1)
    slot=next(i for i,v in enumerate(row('items_bag')) if v['temp_id']==568)
    c.send(-86,bytes([2,slot])+struct.pack('>i',1));c.send(-86,bytes([5]));peer.until(lambda code,p:code==-86 and p[0]==6)
    peer.send(-86,bytes([5]));c.until(lambda code,p:code==-86 and p[0]==6)
    c.send(-86,bytes([7]));c.send(-86,bytes([7]));time.sleep(.2)
    assert sum(v['quantity'] for v in row('items_bag','rulespeer') if v['temp_id']==568)==0,'One party accepted twice and forced a trade'
    peer.send(-86,bytes([7]));c.until(lambda code,p:'Giao dịch thành công'.encode() in p)
    assert sum(v['quantity'] for v in row('items_bag','rulespeer') if v['temp_id']==568)==1
    assert sum(v['quantity'] for v in row('items_bag') if v['temp_id']==568)==1
    for conn,name in [(c,'rulespc'),(peer,'rulespeer')]:
        chat(conn,'code TANTHU8SAO','Đã nhận quà ngay')
        gear=[v for v in row('items_bag',name) if v['temp_id'] in [0,6,12,21,27]]
        assert len(gear)==5 and all([102,8] in v['option'] and [107,8] in v['option'] and [101,40] in v['option'] for v in gear)
        assert not any(v['temp_id'] in [1,2,7,8,22,23,28,29] for v in row('items_bag',name))
    code('TEST',[currency('gold',1)],replace=True)
    chat(c,'code TEST','đã dùng mã');assert sql("SELECT COUNT(*) FROM live_admin_redemptions WHERE code='TEST'")=='1'
    use(c,2154);c.until(lambda code,p:'đã hỏng'.encode() in p);assert row('data_mabu_egg')=={}
    use(c,568);c.until(lambda code,p:'Bắt đầu ấp'.encode() in p);assert row('data_mabu_egg')['species']=='MABU'
    # Paid entry is persisted; re-entry does not charge a second time while the lease is valid.
    before=row('data_inventory')[2];enter(c,1,177);assert row('data_inventory')[2]==before-500
    enter(c,1,177);assert row('data_inventory')[2]==before-500
    settings=api('/api/state')['gameplay'];settings['vip']=[{'id':'VIP_CI','name':'Bill Nhí CI','price':1000,'outfit':[1378,1379,1380],'enabled':True}]
    api('/api/gameplay/settings',{'settings':settings})
    api('/api/reward/preview',{'reward':{'kind':'COMPANION','species':'NORMAL','mode':'PET','replace':False}})
    before=row('data_inventory')[2];chat(c,'muavip VIP_CI','Đã mua đệ');assert row('data_inventory')[2]==before-1000 and row('pet_info')['live_species']=='VIP_CI'
    c.send(-108,bytes([4]));c.until(lambda code,p:code==-42)
    # A malformed large spend is rejected promptly rather than overflowing into free stats.
    c.send(-30,bytes([16,4])+struct.pack('>h',32767));c.until(lambda code,p:'giới hạn'.encode() in p)
    c.sock.close();peer.sock.close();time.sleep(5)
    c=protocol.Client(username='rulesuser',character='rulespc');c.login('beta-fixture-password');c.world();assert row('pet_info')['live_species']=='VIP_CI';chat(c,'buff','Tổng hiện tại x5.0');c.sock.close();time.sleep(5)
    queue_client=create('queueuser','queuepc')
    code('LARGEGIFT',[item(2155) for _ in range(60)])
    chat(queue_client,'code LARGEGIFT','Đã nhận quà ngay')
    queue_user=sql("SELECT account_id FROM player WHERE name='queuepc'")
    pending=lambda:int(sql("SELECT COUNT(*) FROM live_admin_actions WHERE account_id="+queue_user+" AND status='PENDING'"))
    assert pending()>0 and sql("SELECT COUNT(*) FROM live_admin_redemptions WHERE code='LARGEGIFT'")=='1'
    for _ in range(3):
        if pending()==0:break
        count=sum(1 for v in row('items_bag','queuepc') if v['temp_id']==2155)
        for _ in range(count):
            slot=next(i for i,v in enumerate(row('items_bag','queuepc'))if v['temp_id']==2155);queue_client.send(-43,bytes([0,1,slot]));queue_client.until(lambda code,p:'Đã dùng'.encode() in p)
        previous=pending();chat(queue_client,'nhanqua','Đã nhận quà đang chờ');assert pending()<previous
    assert pending()==0
    assert sql("SELECT COUNT(*) FROM live_admin_actions WHERE account_id="+queue_user+" AND status='APPLIED'")=='60'
    chat(queue_client,'nhanqua','Không còn quà');queue_client.sock.close();time.sleep(5)
    # An event training map can be created with existing terrain and a live schedule.
    rule={'map_id':178,'source_map':122,'name':'CI event training','fee_ruby':0,'lease_seconds':3600,'min_power':0,'mob_hp':200000,'exp_extra':100,'pk':False,'enabled':True,'start_ms':0,'end_ms':4102444800000}
    api('/api/training/map',{'rule':rule});assert any(r['map_id']==178 for r in api('/api/state')['training_maps'])
    assert sql("SELECT COUNT(*) FROM live_admin_codes WHERE code IN ('TANTHUVANG','TANTHUNGOC','TANTHUTHOIVANG','TANTHUPHUKIEN','TANTHUBUFF','TANTHUTIEMNANG','TANTHU8SAO')")=='7'
    from playwright.sync_api import sync_playwright,expect
    errors=[]
    with sync_playwright() as pw:
        browser=pw.chromium.launch(headless=True);page=browser.new_page(viewport={'width':1440,'height':1000});page.on('pageerror',lambda e:errors.append(str(e)));page.goto('http://127.0.0.1:14447/')
        page.locator('#login-form input').fill(os.environ['NRO_ADMIN_PASSWORD']);page.locator('#login-form button').click();expect(page.locator('#stat-accounts')).not_to_have_text('—');page.locator('nav [data-tab=resources]').click();page.select_option('#item-select','76');expect(page.locator('#item-form [name=quantity]')).to_have_attribute('max','50000000000');page.locator('nav [data-tab=operations]').click()
        expect(page.locator('#gameplay-form [name=mabu_broken]')).to_have_value('30');page.select_option('#vip-existing','VIP_CI');expect(page.locator('#vip-form [name=head]')).to_have_value('1378');page.screenshot(path=str(root/'logs/gameplay-operations.png'),full_page=True)
        page.locator('nav [data-tab=events]').click();page.locator('#codes-table tr').filter(has_text='TEST').locator('button').first.click();expect(page.locator('#code-form [name=code]')).to_have_value('TEST');expect(page.locator('#code-form [name=replace_existing]')).to_be_checked();expect(page.locator('#bundle-preset')).to_have_value('code:TEST');page.select_option('#training-existing','178');expect(page.locator('#training-form [name=name]')).to_have_value('CI event training');page.screenshot(path=str(root/'logs/training-events.png'),full_page=True)
        page.set_viewport_size({'width':390,'height':844});assert page.evaluate('() => document.documentElement.scrollWidth <= window.innerWidth');assert not errors,errors;browser.close()
    print('PASS: owner gameplay/VIP/training forms in real desktop/mobile browser; no JavaScript errors')
    print('PASS: arbitrary short code; immediate 10-billion gold, items and points; single redemption; same-type timer extension and additive different buffs; intact egg inventory and two-party atomic trade; broken egg refusal; incubation; paid entry lease/re-entry; custom VIP ruby purchase/fusion/relogin; bounded stat spend; event map creation, seven starter codes and resumable 60-item online collection without duplicate delivery')
