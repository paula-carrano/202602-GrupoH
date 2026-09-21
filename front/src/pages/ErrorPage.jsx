import { Link } from 'react-router-dom'
import { FaCircleExclamation } from 'react-icons/fa6'

export const ErrorPage = ({ notFound = false }) => (
  <main className="error-page d-flex align-items-center justify-content-center min-vh-100 p-3">
    <div className="card error-page__card shadow-sm text-center w-100">
      <div className="card-body p-4 p-md-5">
        <FaCircleExclamation className="text-danger fs-1 mb-3" aria-hidden="true" />
        <p className="fw-bold mb-2">{notFound ? '404' : 'Error'}</p>
        <h1 className="fs-3 fw-bold mb-3">{notFound ? 'No encontramos esa página' : 'Ups, hubo un problema'}</h1>
        <p className="text-secondary mb-4">{notFound ? 'La dirección que buscaste no existe.' : 'Intentá nuevamente más tarde.'}</p>
        <Link className="btn btn-primary" to="/">Volver al inicio</Link>
      </div>
    </div>
  </main>
)
