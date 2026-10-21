"""SOLO PRUEBA: navegador local con catálogo y sesión simulados, sin backend."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

out = Path(__file__).parent
player = dict(id=7, firstName='Jugador', lastName='SOLO PRUEBA', currentTeam='Club de prueba', league='Liga de prueba', nationality='Argentina', position='Delantero', birthDate='1990-01-01')
results = []
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [360, 768, 1280]:
        page = browser.new_page(viewport={'width': width, 'height': 900})
        page.route('**/api/v1/players', lambda r: r.fulfill(json=[player]))
        page.route('**/api/v1/players/7', lambda r: r.fulfill(json=player))
        page.goto('http://127.0.0.1:5173/login')
        page.screenshot(path=str(out / f'login-{width}.png'), full_page=True)
        page.evaluate("localStorage.setItem('football-market-session', JSON.stringify({token:'solo.prueba.firma',username:'SOLO PRUEBA',expiresAt:Date.now()+3600000}))")
        for route in ['home', 'players', 'portfolio', 'transactions', 'ranking']:
            page.goto('http://127.0.0.1:5173/' + route)
            page.locator('h1').wait_for()
            if route == 'players': page.get_by_role('button', name='Ver perfil de Jugador SOLO PRUEBA').wait_for()
            results.append(dict(width=width, route=route, overflow=page.evaluate('document.documentElement.scrollWidth > innerWidth')))
            page.screenshot(path=str(out / f'{route}-{width}.png'), full_page=True)
            if route == 'players':
                opener = page.get_by_role('button', name='Ver perfil de Jugador SOLO PRUEBA')
                opener.focus()
                page.keyboard.press('Enter')
                page.get_by_role('dialog').wait_for()
                page.screenshot(path=str(out / f'profile-{width}.png'), full_page=True)
                page.keyboard.press('Shift+Tab')
                trapped = page.evaluate("!!document.activeElement.closest('dialog')")
                page.keyboard.press('Escape')
                results.append(dict(width=width, modalTrap=trapped, restored=opener.evaluate('(el)=>el===document.activeElement')))
        page.get_by_role('link', name='Inicio', exact=True).click()
        page.wait_for_function("document.activeElement.tagName === 'H1'")
        results.append(dict(width=width, routeFocus=page.evaluate('document.activeElement.tagName')))
        page.get_by_role('button', name='Salir').click()
        page.get_by_role('heading', name='Iniciar sesión').wait_for()
        results.append(dict(width=width, logoutFocus=page.evaluate('document.activeElement.tagName')))
        page.evaluate("localStorage.setItem('football-market-session', JSON.stringify({token:'solo.prueba.firma',username:'SOLO PRUEBA',expiresAt:Date.now()+3600000}))")
        page.route('**/api/v1/players', lambda r: r.fulfill(status=500, json={}))
        page.goto('http://127.0.0.1:5173/players')
        page.get_by_role('alertdialog').wait_for()
        page.wait_for_function("!!document.activeElement.closest('[role=alertdialog]')")
        page.screenshot(path=str(out / f'error-{width}.png'), full_page=True)
        page.keyboard.press('Escape')
        assert page.get_by_role('alertdialog').count() == 0
        page.route('**/api/v1/players', lambda r: r.fulfill(json=[]))
        page.get_by_role('button', name='Reintentar', exact=True).click()
        page.get_by_text('No se encontraron jugadores.').wait_for()
        page.screenshot(path=str(out / f'empty-{width}.png'), full_page=True)
        results.append(dict(width=width, errorEscapeRetryEmpty=True))
        page.route('**/api/v1/players', lambda r: r.fulfill(json=[player]))
        page.reload()
        page.get_by_role('button', name='Ver perfil de Jugador SOLO PRUEBA').click()
        page.get_by_role('dialog').wait_for()
        page.evaluate("localStorage.removeItem('football-market-session'); window.dispatchEvent(new StorageEvent('storage', {key:'football-market-session'}))")
        page.wait_for_function("location.pathname === '/login' && document.activeElement.tagName === 'H1'")
        assert page.get_by_role('dialog').count() == 0
        results.append(dict(width=width, sessionLossFocus='H1'))
        page.close()
    print(browser.version)
    browser.close()
(out / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf8')
print(json.dumps(results, indent=2))
