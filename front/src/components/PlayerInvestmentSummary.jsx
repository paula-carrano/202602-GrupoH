import { FinancialPanel, financialValue } from './FinancialPanel'

export const ProfitOrLoss = ({ value }) => value == null ? 'No disponible' : <span className={value > 0 ? 'text-success' : value < 0 ? 'text-danger' : ''}>{value > 0 ? `Ganancia: +${value}` : value < 0 ? `Pérdida: ${value}` : 'Sin ganancia ni pérdida: 0'}</span>

export const PlayerInvestmentSummary = ({ state = 'unavailable', position = {}, onBuy, onSell, ...feedback }) => <>
  <FinancialPanel title="Mi inversión" state={state} emptyText="No tenés tokens de este jugador." {...feedback}>
    <dl>{[
      ['tokenQuantity', 'Cantidad de tokens'], ['averagePurchasePrice', 'Precio promedio de compra (créditos)'],
      ['acquisitionCost', 'Costo de adquisición (créditos)'], ['currentValue', 'Valor actual (créditos)'],
    ].map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{financialValue(position[key])}</dd></div>)}
      <dt>Ganancia/pérdida (créditos)</dt><dd><ProfitOrLoss value={position.profitOrLoss} /></dd>
    </dl>
  </FinancialPanel>
  <div className="d-flex flex-wrap gap-2 mt-2">
    <button className="btn btn-primary" disabled={!onBuy} onClick={onBuy}>Comprar tokens</button>
    <button className="btn btn-outline-primary" disabled={!onSell || state !== 'ready' || !(position.tokenQuantity > 0)} onClick={onSell}>Vender tokens</button>
  </div>
  {!onBuy && <p className="small mt-2">Las operaciones todavía no están disponibles.</p>}
</>
