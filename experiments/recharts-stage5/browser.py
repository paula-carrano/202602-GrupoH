"""Chrome real sobre build preview aislado. Requiere Playwright Python ya instalado."""
import json
import platform
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

root = Path(__file__).parent
out = root / 'evidence'
out.mkdir(exist_ok=True)
results = []
errors = []
api_requests = []
with sync_playwright() as p:
    browser = p.chromium.launch(channel='chrome', headless=True)
    page = browser.new_page(viewport={'width': 1280, 'height': 900})
    page.on('pageerror', lambda err: errors.append(str(err)))
    page.on('request', lambda req: api_requests.append(req.url) if '/api/' in req.url else None)
    page.goto('http://127.0.0.1:5175/__lab')
    expect(page.get_by_role('heading', name='Recharts — SOLO PRUEBA TÉCNICA')).to_be_visible()
    for width in [360, 768, 1280]:
        page.set_viewport_size({'width': width, 'height': 900})
        for case in ['complete', 'empty', 'nulls', 'incomplete', 'zero', 'single']:
            page.get_by_label('Escenario', exact=True).select_option(case)
            if case not in ['empty', 'nulls']:
                expect(page.locator('[data-testid=line] svg.recharts-surface[role=application]')).to_be_visible()
                page.wait_for_function("Math.abs(document.querySelector('[data-testid=line] svg[role=application]').getBoundingClientRect().width - document.querySelector('[data-testid=line]').clientWidth) < 2")
            else:
                assert page.locator('[data-testid=line]').count() == 0
            if case in ['empty', 'nulls', 'incomplete', 'zero']:
                assert page.locator('[data-testid=pie]').count() == 0
            assert not page.evaluate('document.documentElement.scrollWidth > innerWidth'), (width, case)
            table = page.get_by_role('table').first
            if case == 'complete':
                expect(table).to_contain_text('90,1234')
                chart = page.locator('[data-testid=line] svg.recharts-surface[role=application]')
                chart.focus()
                page.keyboard.press('ArrowRight')
                expect(page.locator('.lab-tooltip')).to_be_visible()
                expect(page.locator('.lab-tooltip')).to_contain_text('SIMULADA-v1')
                first = page.locator('.lab-tooltip').inner_text()
                page.keyboard.press('ArrowRight')
                expect(page.locator('.lab-tooltip')).to_contain_text('SIMULADA-v2')
                second = page.locator('.lab-tooltip').inner_text()
                # Registrar datos reales del comportamiento, sin asumir que la dona usa las mismas teclas.
                pie = page.locator('[data-testid=pie] svg.recharts-surface[role=application]')
                pie.focus()
                page.keyboard.press('ArrowRight')
                pie_tip = page.locator('[data-testid=pie] .recharts-tooltip-wrapper').evaluate('(el) => getComputedStyle(el).visibility')
                page.keyboard.press('Tab')
                page.keyboard.press('ArrowRight')
                pie_inner_tip = page.locator('[data-testid=pie] .recharts-tooltip-wrapper').evaluate('(el) => getComputedStyle(el).visibility')
                page.keyboard.press('Tab')
                leaves_pie = page.evaluate("!document.activeElement.closest('[data-testid=pie]')")
                assert leaves_pie
                # Punto dentro del sector azul, usando su centro y radios reales del SVG.
                # A 360px la dona está debajo del viewport; desplazar antes de hover.
                page.locator('[data-testid=pie]').scroll_into_view_if_needed()
                target = page.locator('[data-testid=pie] .recharts-sector').first.evaluate('''el => {
                    const p = el.getPointAtLength(el.getTotalLength() * 0.15);
                    const c = el.getBBox(); p.x += (c.x+c.width/2-p.x)*0.05; p.y += (c.y+c.height/2-p.y)*0.05;
                    const v=p.matrixTransform(el.getScreenCTM()); return {x:v.x,y:v.y};
                }''')
                page.mouse.move(target['x'], target['y'])
                expect(page.locator('[data-testid=pie] .recharts-tooltip-wrapper')).to_be_visible()
                pie_hover = page.locator('[data-testid=pie] .recharts-tooltip-wrapper').inner_text()
                results.append({'width': width, 'lineKeyboardFirst': first, 'lineKeyboardNext': second, 'pieTooltipAfterArrow': pie_tip, 'pieInnerTooltipAfterArrow': pie_inner_tip, 'pieHover': pie_hover, 'twoTabsLeavePie': leaves_pie})
                page.screenshot(path=str(out / f'complete-{width}.png'), full_page=True, animations='disabled')
                (out / f'accessible-tree-{width}.txt').write_text(page.locator('main').aria_snapshot(), encoding='utf8')
            if case == 'incomplete':
                expect(table).to_contain_text('No disponible')
                page.screenshot(path=str(out / f'incomplete-{width}.png'), full_page=True, animations='disabled')
            if case in ['zero', 'single']:
                assert page.locator('[data-testid=line] .recharts-line-dot').count() == (6 if case == 'zero' else 2)
            assert page.locator('path[d*="NaN"]').count() == 0
            results.append({'width': width, 'case': case, 'pageOverflow': False, 'lineCharts': page.locator('[data-testid=line]').count(), 'pieCharts': page.locator('[data-testid=pie]').count(), 'tables': page.get_by_role('table').count()})
    result = {'browser': browser.version, 'platform': platform.platform(), 'cpu': platform.processor(), 'fixtures': 'SOLO PRUEBA; 3 puntos y 2 posiciones', 'results': results, 'pageErrors': errors, 'apiRequests': api_requests, 'screenReader': 'No ejecutado; árbol accesible no equivale a lector de pantalla'}
    (out / 'browser-results.json').write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding='utf8')
    print(json.dumps(result, indent=2, ensure_ascii=False))
    browser.close()
assert not errors, errors
assert not api_requests, api_requests

