import { FaArrowLeft, FaArrowRight } from 'react-icons/fa6'

export const Pagination = ({ page, pageSize, total, onPageChange }) => {
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const first = total ? (page - 1) * pageSize + 1 : 0
  const last = Math.min(page * pageSize, total)

  return <div className="catalog-footer d-flex flex-wrap align-items-center justify-content-between gap-3 mt-auto pt-3">
    <span>Mostrando {first}–{last} de {total} jugadores</span>
    <div className="d-flex gap-2">
      <button type="button" className="btn btn-outline-primary btn-sm d-inline-flex align-items-center gap-2" disabled={page === 1} onClick={() => onPageChange(page - 1)}><FaArrowLeft aria-hidden="true" /> Anterior</button>
      <button type="button" className="btn btn-primary btn-sm d-inline-flex align-items-center gap-2" disabled={page >= totalPages} onClick={() => onPageChange(page + 1)}>Siguiente <FaArrowRight aria-hidden="true" /></button>
    </div>
  </div>
}
