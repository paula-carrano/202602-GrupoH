import { AlertForm, AlertsTable, FinancialPanel } from '../components'
import { useState } from 'react'
import { useMarket } from '../hooks/useMarket'
import { useMutation } from '../hooks/useMutation'
import { useSessionRequest } from '../hooks/useSessionRequest'
import { getPlayers } from '../services/players'
import { createAlert, deleteAlert } from '../services/market'

import { requestState } from '../utils/financial'

export function AlertsPage() {
  const m = useMarket(), mutation = useMutation(m.onLogout)
  const [deleting, setDeleting] = useState(null), [message, setMessage] = useState('')
  const catalog = useSessionRequest({ session: m.session, enabled: m.allowed, requestKey: 'alert-players', request: getPlayers, onUnauthorized: m.onLogout })
  const create = async values => {
    const response = await mutation.run(options => createAlert(values, options))
    if (response) { setMessage('Alerta creada.'); m.refreshAlerts() }
    return Boolean(response)
  }
  const remove = async () => {
    if (!deleting || mutation.busy || mutation.uncertain) return
    const response = await mutation.run(options => deleteAlert(deleting, options))
    if (response) { setDeleting(null); setMessage('Alerta eliminada.'); m.refreshAlerts() }
  }
  const state = requestState(m.alerts)
  return <>
    <h1 className="h5">Alertas</h1><output className="d-block mb-3">{m.streamStatus || 'Notificaciones no disponibles sin una sesión identificada.'}</output>
    {message && <output className="d-block mb-3">{message}</output>}
    <AlertForm
      catalog={catalog}
      allowed={m.allowed}
      busy={mutation.busy}
      uncertain={mutation.uncertain}
      onCreate={create}
    />
    {mutation.error && <p role="alert">{mutation.error}</p>}
    {mutation.uncertain && <p>Actualizá el listado para comprobar el resultado antes de volver a crear o eliminar una alerta. <button type="button" onClick={m.refreshAlerts}>Actualizar alertas</button></p>}
    {deleting && <div className="alert alert-warning">¿Eliminar la alerta seleccionada? <button type="button" className="btn btn-danger" disabled={mutation.busy || mutation.uncertain} onClick={remove}>Confirmar eliminación</button> <button type="button" className="btn btn-secondary" disabled={mutation.busy} onClick={() => setDeleting(null)}>Cancelar</button></div>}
    <FinancialPanel title="Mis alertas" state={state === 'ready' && !m.alerts.data?.length ? 'empty' : state} error={m.alerts.error} onRetry={m.alerts.retry} emptyText="Todavía no creaste alertas.">
      <AlertsTable
        alerts={m.alerts.data || []}
        deletingDisabled={mutation.busy || mutation.uncertain}
        onSelectPlayer={m.selectPlayer}
        onDelete={setDeleting}
        onOrder={m.openOrder}
      />
    </FinancialPanel>
  </>
}
