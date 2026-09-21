import { FaCircleExclamation } from 'react-icons/fa6'
import { FeedbackAlert } from './FeedbackAlert'

export const ErrorDialog = ({ message, onClose }) => (
  <>
    <div className="modal-backdrop fade show" />
    <div className="modal fade show d-block" role="alertdialog" aria-modal="true" aria-labelledby="error-title" aria-describedby="error-description" tabIndex={-1}>
      <div className="modal-dialog modal-dialog-centered">
        <div className="modal-content shadow-lg">
          <div className="modal-header border-0 pb-0">
            <h2 className="modal-title fs-5 fw-bold d-flex align-items-center gap-2" id="error-title"><FaCircleExclamation className="text-danger" aria-hidden="true" /> UPS, HUBO UN PROBLEMA</h2>
            <button type="button" className="btn-close" aria-label="Cerrar aviso" onClick={onClose} />
          </div>
          <div className="modal-body"><FeedbackAlert id="error-description" message={message} /></div>
          <div className="modal-footer border-0 pt-0"><button type="button" className="btn btn-primary" onClick={onClose}>Cerrar</button></div>
        </div>
      </div>
    </div>
  </>
)
