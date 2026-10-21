"""Fetch only pinned companion sprite fragments; never copy foreign player tables or IDs."""
import concurrent.futures, hashlib, json, shutil, time, urllib.request
from pathlib import Path

def prepare(output):
    root=Path(__file__).parent
    manifest=json.loads((root/'assets.json').read_text())
    target=output/'server/src/main/resources/companion'
    target.mkdir(parents=True,exist_ok=True)
    provenance={}
    def fetch(job):
        zoom,old,new=job
        url=f"https://raw.githubusercontent.com/{manifest['source']}/{manifest['commit']}/resources/normal/image/{zoom}/icon/{old}.png"
        for attempt in range(3):
            try:
                data=urllib.request.urlopen(url,timeout=30).read()
                if not data.startswith(b'\x89PNG\r\n\x1a\n'):raise ValueError('Not a PNG')
                dest=target/str(zoom)/f'{new}.png';dest.parent.mkdir(exist_ok=True);dest.write_bytes(data)
                return str(zoom)+'/'+str(new),hashlib.sha256(data).hexdigest()
            except Exception:
                if attempt==2:raise
                time.sleep(1)
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        for name,digest in pool.map(fetch,[(z,int(o),n)for z in range(1,5)for o,n in manifest['icons'].items()]):provenance[name]=digest
    manifest['sha256']=provenance
    (target/'assets.json').write_text(json.dumps(manifest))
    print('Pinned Zeno assets:',len(provenance),'PNG fragments')
