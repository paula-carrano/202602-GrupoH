import { api } from './api'

export const getPlayers = ({ signal } = {}) => api.get('/players', { signal })
