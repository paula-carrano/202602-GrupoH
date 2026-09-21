import { Link } from 'react-router-dom'
import { FaCircleExclamation } from 'react-icons/fa6'

export function ErrorPage({ notFound = false }) {
  return <main className="error-page"><div className="error-page__card"><FaCircleExclamation aria-hidden="true" /><span>{notFound ? '404' : 'Error'}</span><h1>{notFound ? 'No encontramos esa página' : 'Ups, hubo un problema'}</h1><p>{notFound ? 'La dirección que buscaste no existe.' : 'Intentá nuevamente más tarde.'}</p><Link className="btn btn-primary" to="/">Volver al inicio</Link></div></main>
}
