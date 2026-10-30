"""UI smoke test with explicitly simulated API data; does not validate a real backend.
Run with Vite on 127.0.0.1:5173 and Python Playwright + Chrome installed.
"""
import base64
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path
from playwright.sync_api import sync_playwright

now = datetime.now(timezone.utc)
stamp = lambda days: (now - timedelta(days=days)).strftime('%Y-%m-%dT12:00:00Z')
player = dict(id=7, firstName='Jugador', lastName='SOLO PRUEBA', currentTeam='Equipo', league='Liga', active=True)
position = dict(playerId=7, playerName='Jugador SOLO PRUEBA', tokensQuantity=2, averageBuyPrice=2, currentQuote=3, totalCurrentValue=6, totalCostBasis=4, pnlNominal=2)
portfolio = dict(userId=1, creditsBalance=96, totalCurrentValue=6, totalInvestedCost=4, totalPnLNominal=2, positions=[position])
transactions = [dict(id=2, timestamp=stamp(1), operationType='COMPRA', playerId=7, playerName=position['playerName'], quantity=2, unitPrice=2, totalAmount=4, balanceBefore=100, balanceAfter=96, tokensBefore=0, tokensAfter=2), dict(id=1, timestamp=stamp(2), operationType='RECARGA', playerId=None, totalAmount=100, balanceBefore=0, balanceAfter=100)]
quotes = [dict(quoteId=2, timestamp=stamp(0).replace('T12:', 'T00:'), price=3, strategyVersion=1), dict(quoteId=1, timestamp=stamp(2), price=2, strategyVersion=1)]
def paged(rows):
    return dict(content=rows, pageNumber=0, pageSize=100, totalElements=len(rows), totalPages=1, last=True)
def route_api(route):
    url = route.request.url.split('/api', 1)[1]
    if url == '/alerts/stream':
        return route.fulfill(status=403, json={})
    if url == '/alerts': data = []
    elif url == '/players': data = [player]
    elif url == '/players/7': data = player
    elif url.startswith('/users/1/portfolio'): data = portfolio
    elif url.startswith('/users/1/transactions'): data = paged(transactions)
    elif url.startswith('/players/7/quotes'):
        data = dict(price=3, currency='CRD', strategyName='BALANCED', strategyVersion=1) if 'date=' in url else paged(quotes)
    elif url == '/players/ranking': data = dict(strategyCode='BALANCED', strategyVersion=1, ranking=[dict(rank=1, playerId=7, firstName=player['firstName'], lastName=player['lastName'], currentQuote=3)])
    else: raise AssertionError(f'Unexpected request {url}')
    route.fulfill(json=data)

out = Path('artifacts/market-ui')
out.mkdir(parents=True, exist_ok=True)
claims = base64.urlsafe_b64encode(json.dumps(dict(userId=1, exp=int(now.timestamp()) + 3600)).encode()).decode().rstrip('=')
session = dict(token=f'header.{claims}.test-signature', username='SOLO PRUEBA', expiresAt=int(now.timestamp() * 1000) + 3600000)
results = []
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [360, 1280]:
        page = browser.new_page(viewport=dict(width=width, height=1000))
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.route('**/api/**', route_api)
        page.goto('http://127.0.0.1:5173/login')
        page.evaluate('(session) => localStorage.setItem("football-market-session", JSON.stringify(session))', session)
        page.goto('http://127.0.0.1:5173/home')
        try:
            page.get_by_role('combobox', name='Jugador', exact=True).select_option('7', timeout=10000)
        except Exception:
            page.screenshot(path=str(out / 'failure.png'), full_page=True)
            print(json.dumps(dict(errors=errors, body=page.locator('body').inner_text())))
            raise
        page.locator('.recharts-surface').nth(2).wait_for()
        assert page.locator('.recharts-line-curve').count() >= 3
        assert page.locator('.recharts-pie-sector').count() >= 1
        page.screenshot(path=str(out / f'home-{width}.png'), full_page=True)
        for name in ['Portfolio', 'Historial', 'Ranking', 'Alertas', 'Jugadores']:
            page.get_by_role('navigation').get_by_role('link', name=name, exact=True).click()
            page.get_by_role('heading', level=1).wait_for()
            assert not page.evaluate('document.documentElement.scrollWidth > innerWidth'), name
        page.get_by_role('button', name='Ver perfil de Jugador SOLO PRUEBA').click()
        page.get_by_role('button', name='Comprar tokens', exact=True).click()
        page.get_by_label('Cantidad de tokens').fill('1')
        page.get_by_role('checkbox').check()
        page.get_by_role('button', name='Confirmar operación').wait_for()
        assert page.get_by_role('button', name='Confirmar operación').is_enabled()
        page.screenshot(path=str(out / f'order-{width}.png'), full_page=True)
        assert not errors, errors
        results.append(dict(width=width, charts='rendered', navigation='passed', order='ready', browserErrors=errors))
        page.close()
    browser.close()
print(json.dumps(results))
