import { afterEach, expect, it, vi } from 'vitest'
import { readEvents, connectAlerts } from '../services/alertStream'
afterEach(() => vi.useRealTimers())
it('procesa UTF-8, CRLF, comentarios y mensajes fragmentados', async () => {
  const bytes = new TextEncoder().encode(':ping\r\nevent: init\r\ndata: {}\r\n\r\nevent: price-alert\r\ndata: {"playerName":"Julián",\r\ndata: "alertId":1}\r\n\r\n')
  const stream = new ReadableStream({ start(controller) { for (const byte of bytes) controller.enqueue(new Uint8Array([byte])); controller.close() } })
  const events = []
  await readEvents(stream, e => events.push(e), new AbortController().signal)
  expect(events.map(e => e.event)).toEqual(['init', 'price-alert'])
  expect(JSON.parse(events[1].data)).toEqual({ playerName: 'Julián', alertId: 1 })
})
it('no publica eventos luego de cancelar la sesión', async () => {
  const abort = new AbortController(), event = vi.fn()
  const stream = new ReadableStream({ start(c) { c.enqueue(new TextEncoder().encode('event: price-alert\ndata: {}\n\n')); abort.abort() } })
  await readEvents(stream, event, abort.signal)
  expect(event).not.toHaveBeenCalled()
})
it('reconecta con Bearer y deja de hacerlo al cerrar la sesión', async () => {
  vi.useFakeTimers()
  const fetcher = vi.fn().mockRejectedValue(new Error('offline')), status = vi.fn()
  const stop = connectAlerts({ token: 'test-token', fetcher, onStatus: status, onConnected: vi.fn(), onEvent: vi.fn(), onUnauthorized: vi.fn() })
  await vi.advanceTimersByTimeAsync(1000)
  expect(fetcher).toHaveBeenCalledTimes(2)
  expect(fetcher.mock.calls[0][0]).toBe('/api/alerts/stream')
  expect(fetcher.mock.calls[0][1].headers.Authorization).toBe('Bearer test-token')
  stop(); await vi.advanceTimersByTimeAsync(60000)
  expect(fetcher).toHaveBeenCalledTimes(2)
  expect(fetcher.mock.calls[0][1].signal.aborted).toBe(true)
})
it('un 401 termina la suscripción y cierra sesión sin reintentar', async () => {
  vi.useFakeTimers()
  const unauthorized = vi.fn(), fetcher = vi.fn().mockResolvedValue({ status: 401 })
  const stop = connectAlerts({ token: 'test', fetcher, onStatus: vi.fn(), onConnected: vi.fn(), onEvent: vi.fn(), onUnauthorized: unauthorized })
  await vi.advanceTimersByTimeAsync(60000)
  expect(unauthorized).toHaveBeenCalledOnce(); expect(fetcher).toHaveBeenCalledOnce(); stop()
})
