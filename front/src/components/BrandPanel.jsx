import { FaFutbol } from 'react-icons/fa6'

export const BrandPanel = () => (
  <aside className="brand-panel col-md-5 d-flex flex-column align-items-center justify-content-center text-center text-white" aria-label="Football Market Platform">
    <div className="brand-panel__center d-flex flex-column align-items-center gap-3">
      <FaFutbol className="brand-panel__ball" aria-hidden="true" />
      <strong>FOOTBALL MARKET<br />PLATFORM</strong>
      <span>Conectando el talento con el mercado</span>
    </div>
  </aside>
)
