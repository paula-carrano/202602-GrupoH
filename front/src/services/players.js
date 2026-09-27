import { api } from './api'

export const getPlayers = ({ signal } = {}) => api.get('/players', { signal })
export const getPlayer = (id, { signal } = {}) => api.get(`/players/${id}`, { signal })
