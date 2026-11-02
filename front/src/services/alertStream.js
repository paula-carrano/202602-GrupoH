import { api } from './api'

// Streaming parser: chunks can split UTF-8 characters, CRLFs and event boundaries.
export async function readEvents(body, onEvent, signal) {
  const reader = body.getReader(), decoder = new TextDecoder()
  let buffer = '', name = 'message', data = []
  const line = text => {
    if (!text) {
      if (data.length && !signal.aborted) onEvent({ event: name, data: data.join('\n') })
      name = 'message'; data = []; return
    }
    if (text.startsWith(':')) return
    const colon = text.indexOf(':')
    const field = colon < 0 ? text : text.slice(0, colon)
    const value = colon < 0 ? '' : text.slice(colon + 1).replace(/^ /, '')
    if (field === 'event') name = value
    if (field === 'data') data.push(value)
  }
  const cancel = () => { void reader.cancel().catch(() => {}) }
  signal.addEventListener('abort', cancel, { once: true })
  try {
    while (!signal.aborted) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let end
      while ((end = buffer.indexOf('\n')) >= 0) {
        line(buffer.slice(0, end).replace(/\r$/, '')); buffer = buffer.slice(end + 1)
      }
      if (buffer.length > 1048576) throw new Error('Evento demasiado grande')
    }
  } finally {
    signal.removeEventListener('abort', cancel)
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}

export function connectAlerts({ token, onEvent, onStatus, onConnected, onUnauthorized, fetcher = fetch }) {
  const controller = new AbortController()
  let timer, attempts = 0, stopped = false
  const run = async () => {
    try {
      onStatus('Conectando notificaciones…')
      const url = api.getUri({ url: '/alerts/stream' })
      const response = await fetcher(url, { headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' }, signal: controller.signal })
      if (stopped) return
      if (response.status === 401) { stopped = true; onUnauthorized(); return }
      if (response.status === 403) { stopped = true; onStatus('Sin permiso para recibir notificaciones.'); return }
      if (!response.ok || !response.body || !response.headers.get('content-type')?.includes('text/event-stream')) throw new Error('Stream no disponible')
      onStatus('Notificaciones conectadas')
      onConnected()
      await readEvents(response.body, event => {
        attempts = 0
        if (event.event === 'price-alert') {
          try { onEvent(JSON.parse(event.data)) } catch { /* Malformed events do not break the stream. */ }
        }
      }, controller.signal)
    } catch { /* Retry only the read-only subscription. */ }
    if (!stopped) {
      onStatus('Notificaciones desconectadas. Reconectando…')
      timer = setTimeout(run, Math.min(1000 * 2 ** attempts++, 30000))
    }
  }
  void run()
  return () => { stopped = true; clearTimeout(timer); controller.abort() }
}
