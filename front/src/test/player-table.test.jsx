import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { PlayerTable } from '../components/PlayerTable'

it('muestra N/D para datos personales que el proveedor no informa', () => {
  render(<PlayerTable hasResults players={[{ id: 1, firstName: 'Nombre completo', lastName: '',
    birthDate: null, nationality: null, position: null, currentTeam: 'Equipo', league: 'PL' }]} />)
  expect(screen.getByRole('cell', { name: 'Nombre completo' })).toBeInTheDocument()
  expect(screen.getAllByText('N/D')).toHaveLength(3)
})
