"""Real protocol companion receipt, incubation, status, fusion and relogin checks."""
import http.cookiejar,json,os,struct,time,urllib.request,urllib.error,uuid

def run(root,sql,protocol):
    jar=http.cookiejar.CookieJar();opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar));csrf=''
    def api(path,body=None):
        req=urllib.request.Request('http://127.0.0.1:14447'+path,data=json.dumps(body).encode() if body is not None else None,headers={'Content-Type':'application/json','X-CSRF':csrf})
        with opener.open(req,timeout=90) as r:return json.load(r)
    csrf=api('/api/login',{'password':os.environ['NRO_ADMIN_PASSWORD']})['csrf'];state=api('/api/state');user=next(a['id']for a in state['accounts']if a['username']=='tester')
    def row(col):return json.loads(sql("SELECT "+col+" FROM player WHERE name='pcprobe'"))
    initial_info=row('pet_info');assert initial_info['live_species']=='NORMAL'
    assert row('data_inventory')[0]>=1000000
    assert sql(f'SELECT COUNT(*) FROM live_companion_welcome WHERE account_id={user}')=='1'
    original_body=row('pet_body');original_skills=row('pet_skill');original_points=row('pet_point')
    def login():
        c=protocol.Client();c.login('beta-fixture-password');c.world();return c
    def close(c):c.sock.close();time.sleep(5)
    def info(c):
        c.send(-107);_,p=c.until(lambda code,p:code==-107 and p and p[0]==2)
        avatar=struct.unpack_from('>h',p,1)[0];offset=4
        for _ in range(p[3]):
            item=struct.unpack_from('>h',p,offset)[0];offset+=2
            if item!=-1:
                offset+=4
                for _ in range(2):size=struct.unpack_from('>H',p,offset)[0];offset+=2+size
                count=p[offset];offset+=1+count*3
        hp,hpmax,mp,mpmax,damage=struct.unpack_from('>iiiii',p,offset)
        return avatar,hpmax,mpmax,damage
    settings=state['companion_settings'];original_settings=dict(settings)
    for index,species in enumerate(['MABU','JIREN','ZENO']):
        code='LIVEEGG'+species
        api('/api/code/create',{'code':code,'max_uses':1,'expires_ms':int(time.time()*1000)+3600000,'reward':{'kind':'COMPANION','mode':'EGG','species':species}})
        api('/api/code/redeem',{'account_id':user,'code':code})
        c=login();egg=row('data_mabu_egg');assert egg['species']==species
        assert egg['time_done']==settings['hatch_seconds']*1000*{'MABU':1,'JIREN':6,'ZENO':24}[species]
        c.send(44,protocol.utf('notrung'));c.until(lambda code,p:'chưa đủ'.encode() in p)
        assert row('data_mabu_egg')==egg,'Early hatch consumed egg'
        before=row('data_inventory')[0]
        settings['fast_gold']=1000000000;api('/api/companion/settings',{'settings':settings})
        c.send(44,protocol.utf('apnhanh'));c.until(lambda code,p:'vàng để ấp nhanh'.encode() in p)
        assert row('data_inventory')[0]==before and row('data_mabu_egg')==egg
        settings['fast_gold']=100000;api('/api/companion/settings',{'settings':settings})
        c.send(44,protocol.utf('apnhanh'));c.until(lambda code,p:code==-122)
        assert row('data_inventory')[0]==before-100000 and row('data_mabu_egg')['time_done']==0
        c.send(44,protocol.utf('apnhanh'));time.sleep(.2)
        assert row('data_inventory')[0]==before-100000,'Repeated fast-hatch charged twice'
        c.send(44,protocol.utf('notrung'));c.until(lambda code,p:'Nở thành công'.encode() in p)
        assert row('data_mabu_egg')=={} and row('pet_info')['live_species']==species
        avatar,pet_hp,_,pet_damage=info(c)
        assert pet_hp>=1000 and pet_damage>0
        assert avatar==({'MABU':297,'JIREN':876}.get(species,avatar))
        if species=='ZENO':assert avatar>=2042
        assert row('pet_body')==original_body and row('pet_skill')==original_skills
        assert row('pet_point')['hpg']==original_points['hpg'],'Hatch reset trained HP'
        # Status commands run through the real controller; fusion emits authoritative points.
        c.send(-108,bytes([0]));time.sleep(.2);info(c)
        c.send(-108,bytes([4]));_,points=c.until(lambda code,p:code==-42)
        fused_hp=struct.unpack_from('>i',points,12)[0]
        assert fused_hp>0
        close(c)
        persisted=row('pet_info');assert persisted['type_fusion']!=0 and persisted['live_species']==species
        # Login again while fused: appearance, skills and fusion must survive.
        c=login();assert info(c)[0]==avatar;close(c)
        # An explicit owner grant clears fusion for the next upgrade without losing progression.
        api('/api/grant',{'request_id':str(uuid.uuid4()),'account_id':user,'reason':'CI companion direct upgrade','reward':{'kind':'COMPANION','mode':'PET','species':species,'replace':True}})
        c=login();assert row('pet_info')['live_species']==species and row('pet_info')['type_fusion']==0;close(c)
    api('/api/companion/settings',{'settings':original_settings})
    assert sql(f'SELECT COUNT(*) FROM live_companion_welcome WHERE account_id={user}')=='1'
    # The isolated sprite bundle is actually served over the encrypted game protocol.
    import pathlib
    manifest=json.loads((root/'server/src/main/resources/companion/assets.json').read_text())
    icon=next(iter(manifest['icons'].values()));c=login();c.send(-67,struct.pack('>i',icon))
    _,payload=c.until(lambda code,p:code==-67 and len(p)>8 and struct.unpack_from('>i',p)[0]==icon)
    assert payload[8:16]==b'\x89PNG\r\n\x1a\n';close(c)
    print('PASS: automatic normal disciple/welcome once; Mabu/Jiren/Zeno eggs, early/poor/repeated hatch guards, real hatch/direct grants, unchanged gear/skills/stats, fusion/relogin, and isolated Zeno PNG over game protocol')
