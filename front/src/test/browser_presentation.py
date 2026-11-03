"""Presentation smoke checks with simulated API data; never changes the database."""
import base64
import json
from datetime import datetime, timezone
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

now = datetime.now(timezone.utc)
stamp = now.isoformat().replace('+00:00', 'Z')
player = dict(id=7, firstName='Unai', lastName='Marrero', active=True, league='PD')
position = dict(playerId=7, playerName='Unai Marrero', tokensQuantity=2, averageBuyPrice=2, currentQuote=3, totalCurrentValue=6, totalCostBasis=4, pnlNominal=2)
claims = base64.urlsafe_b64encode(json.dumps(dict(userId=1, exp=int(now.timestamp()) + 3600)).encode()).decode().rstrip('=')
session = dict(token=f'header.{claims}.test', username='inversor', expiresAt=int(now.timestamp()*1000)+3600000)
out = Path('artifacts/presentation-ui')
out.mkdir(parents=True, exist_ok=True)

def paged(rows, page=0, size=100):
    return dict(content=rows[page*size:(page+1)*size], pageNumber=page, pageSize=size, totalElements=len(rows), totalPages=(len(rows)+size-1)//size, last=(page+1)*size >= len(rows))

with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    for width in [390, 1280, 1920]:
        state = dict(mode='large', balance=15000, transactions=[])
        errors = []
        page = browser.new_page(viewport=dict(width=width, height=1000))
        page.on('pageerror', lambda e: errors.append(str(e)))
        def route_api(route):
            from urllib.parse import urlparse, parse_qs
            parsed = urlparse(route.request.url)
            url = parsed.path.removeprefix('/api')
            params = parse_qs(parsed.query)
            if url == '/alerts/stream':
                return route.fulfill(status=403, json={})
            if url == '/orders/buy':
                state['balance'] -= 3
                state['transactions'].insert(0, dict(id=2, timestamp=stamp, operationType='COMPRA', playerId=7, playerName='Unai Marrero', quantity=1, unitPrice=3, totalAmount=3, balanceBefore=15000, balanceAfter=14997, tokensBefore=2, tokensAfter=3, strategyVersion=1))
                return route.fulfill(json=dict(transactionId=2, totalAmount=3, balanceAfter=14997, unitPrice=3, tokensAfter=3))
            if url == '/alerts': data = []
            elif url == '/players': data = [player]
            elif url == '/players/7': data = player
            elif url.endswith('/portfolio'):
                positions = ([dict(position, playerId=i+1, playerName=f'Jugador {i+1}') for i in range(2649)] if state['mode'] == 'large' else [] if state['mode'] == 'empty' else [dict(position, tokensQuantity=3 if state['transactions'] else 2, totalCurrentValue=9 if state['transactions'] else 6)])
                data = dict(userId=1, creditsBalance=state['balance'], positions=positions, totalCurrentValue=sum(x['totalCurrentValue'] for x in positions), totalInvestedCost=0, totalPnLNominal=0)
            elif url.endswith('/transactions'): data = paged(state['transactions'], int(params.get('page', [0])[0]), int(params.get('size', [100])[0]))
            elif url.endswith('/quotes'): data = dict(price=3, currency='CRD', strategyName='OFFENSIVE', strategyVersion=1) if 'date' in params else paged([dict(quoteId=1, timestamp=stamp, price=3, strategyVersion=1)])
            else: raise AssertionError(url)
            route.fulfill(json=data)
        page.route('**/api/**', route_api)
        page.goto('http://127.0.0.1:5174/login')
        page.evaluate('(s) => localStorage.setItem("football-market-session", JSON.stringify(s))', session)
        page.goto('http://127.0.0.1:5174/home')
        expect(page.get_by_text('Seleccioná un jugador para ver su evolución', exact=True)).to_be_visible()
        expect(page.locator('.player-avatar-large')).to_have_text('IN')
        expect(page.locator('.allocation-legend li')).to_have_count(11)
        expect(page.locator('.recharts-pie-sector')).to_have_count(11)
        if width < 1200:
            page.get_by_role('combobox', name='Jugador', exact=True).select_option('7')
        else:
            page.locator('.investment-player-list button').first.click()
        expect(page.locator('[aria-label="Gráfico de cotizaciones"] svg[role="application"]')).to_be_visible()
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        assert page.locator('.allocation-legend').evaluate('(el) => {const p=el.closest(".financial-panel").getBoundingClientRect(), r=el.getBoundingClientRect(); return r.top>=p.top && r.bottom<=p.bottom}')
        page.screenshot(path=str(out / f'home-{width}.png'), full_page=True)
        state['mode'] = 'empty'
        page.goto('http://127.0.0.1:5174/portfolio')
        expect(page.get_by_text('No tenés posiciones en cartera.', exact=True)).to_be_visible()
        expect(page.get_by_text('Todavía no hay operaciones para reconstruir tu patrimonio.', exact=True)).to_be_visible()
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
        assert page.locator('.dashboard-page').evaluate('(el) => [...document.querySelectorAll(".financial-panel")].every(p => p.getBoundingClientRect().bottom <= el.getBoundingClientRect().bottom)')
        page.screenshot(path=str(out / f'portfolio-{width}.png'), full_page=True)
        state['mode'] = 'normal'
        page.goto('http://127.0.0.1:5174/home')
        if width < 1200: page.get_by_role('combobox', name='Jugador', exact=True).select_option('7')
        else: page.locator('.investment-player-list button').first.click()
        page.get_by_role('button', name='Comprar tokens', exact=True).click()
        page.get_by_label('Cantidad de tokens').fill('1')
        page.get_by_role('checkbox').check()
        page.get_by_role('button', name='Confirmar operación').click()
        expect(page.get_by_text('Operación confirmada', exact=True)).to_be_visible()
        page.get_by_role('button', name='Cerrar operación').click()
        page.get_by_role('navigation', name='Navegación principal').get_by_role('link', name='Historial', exact=True).click()
        page.get_by_text('Ver detalle de operación 2', exact=True).click()
        expect(page.locator('details')).to_contain_text('14997.0000')
        page.screenshot(path=str(out / f'transactions-{width}.png'), full_page=True)
        page.get_by_role('navigation', name='Navegación principal').get_by_role('link', name='Portfolio', exact=True).click()
        expect(page.get_by_text('14997', exact=True)).to_be_visible()
        assert not errors, errors
        print(json.dumps(dict(width=width, charts='passed', containment='passed', audit='passed', mutationRefresh='passed')))
        page.close()
    browser.close()
