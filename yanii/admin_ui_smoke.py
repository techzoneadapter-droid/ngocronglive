"""Real browser smoke test against the running Windows owner console (CI only)."""
import base64,os
from pathlib import Path
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parent
errors=[]
with sync_playwright() as p:
    browser=p.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(viewport={'width':1280,'height':900},device_scale_factor=1)
    page.on('pageerror',lambda error:errors.append(str(error)))
    page.goto('http://127.0.0.1:14447/')
    page.locator('#login-form input').fill(os.environ['NRO_ADMIN_PASSWORD'])
    page.locator('#login-form button').click()
    page.locator('#app').wait_for(state='visible')
    page.wait_for_function("document.querySelector('#stat-accounts').textContent !== '—'")
    assert page.locator('#stat-accounts').inner_text()=='2'
    for tab in ['players','resources','events','orders','history','operations','overview']:
        page.locator('nav button[data-tab="'+tab+'"]').click()
        assert page.locator('[data-panel="'+tab+'"]').is_visible()
    assert page.locator('#recent table').count()==1
    preview=root/'logs/admin-preview.jpg'
    page.screenshot(path=str(preview),type='jpeg',quality=40)
    print('ADMIN_PREVIEW_BASE64_BEGIN',flush=True)
    print(base64.b64encode(preview.read_bytes()).decode(),flush=True)
    print('ADMIN_PREVIEW_BASE64_END',flush=True)
    page.set_viewport_size({'width':390,'height':844})
    assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth')
    page.locator('#logout').click()
    page.locator('#login').wait_for(state='visible')
    assert not errors,errors
    browser.close()
print('PASS: real Edge browser login, data rendering, navigation, mobile width, logout and no JavaScript exceptions',flush=True)
