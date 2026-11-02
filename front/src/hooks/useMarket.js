import { createContext, useContext } from 'react'
export const MarketContext = createContext(null)
export const useMarket = () => useContext(MarketContext)
