import React from 'react'
import { createRoot } from 'react-dom/client'
import './style.css'

function App() {
  return (
    <main className="page">
      <section className="welcome">
        <span className="eyebrow">Grupo H</span>
        <h1>Mercado de Jugadores</h1>
        <p>El frontend del proyecto está listo para empezar.</p>
      </section>
    </main>
  )
}

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
