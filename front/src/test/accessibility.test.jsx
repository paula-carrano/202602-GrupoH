import { ErrorDialog, PlayerTable } from '../components'
import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

it('el aviso de error recibe foco, cicla Tab, cierra con Escape y restaura el disparador', async () => {
  const user = userEvent.setup()
  const Example = () => {
    const [open, setOpen] = useState(false)
    return <><button onClick={() => setOpen(true)}>Abrir aviso</button>{open && <ErrorDialog message="Error de prueba" onClose={() => setOpen(false)} />}</>
  }
  render(<Example />)
  const opener = screen.getByRole('button', { name: 'Abrir aviso' })
  await user.click(opener)
  expect(screen.getByRole('button', { name: 'Cerrar aviso' })).toHaveFocus()
  await user.tab({ shift: true })
  expect(screen.getByRole('button', { name: 'Cerrar', exact: true })).toHaveFocus()
  await user.tab()
  expect(screen.getByRole('button', { name: 'Cerrar aviso' })).toHaveFocus()
  await user.keyboard('{Escape}')
  expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  expect(opener).toHaveFocus()
})

it('identifica la tabla desplazable y anuncia resultados vacíos', () => {
  render(<PlayerTable players={[]} hasResults={false} />)
  expect(screen.getByRole('table', { name: 'Catálogo de jugadores' })).toBeInTheDocument()
  expect(screen.getByRole('region', { name: 'Catálogo de jugadores' })).toHaveAttribute('tabindex', '0')
  expect(screen.getByRole('status')).toHaveTextContent('No se encontraron jugadores.')
})
