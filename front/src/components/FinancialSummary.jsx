import { FinancialPanel, financialValue } from './FinancialPanel'

export const FinancialSummary = ({ data = {}, ...props }) => <FinancialPanel title="Resumen financiero" {...props}>
  <dl className="row mb-0">{[
    ['availableBalance', 'Saldo disponible'], ['currentPortfolioValue', 'Valor actual de cartera'],
    ['acquisitionCost', 'Costo de adquisición'], ['totalWealth', 'Patrimonio total'], ['profitOrLoss', 'Ganancia/pérdida (PnL)'],
  ].map(([key, label]) => <div className="col-sm-6 col-xl-4 mb-3" key={key}><dt>{label} (créditos)</dt><dd>{financialValue(data[key])}</dd></div>)}</dl>
</FinancialPanel>
