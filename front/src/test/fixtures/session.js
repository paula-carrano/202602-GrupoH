// SOLO PRUEBA: claims del contrato JwtTokenProvider, firma ficticia no verificable.
export const tokenFor = (claims = {}) => {
  const payload = { sub: 'usuario', userId: 1, exp: Math.floor(Date.now() / 1000) + 3600, ...claims }
  const encoded = btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(payload))))
    .replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
  return `eyJhbGciOiJIUzI1NiJ9.${encoded}.firma-solo-prueba`
}
export const sessionFor = id => ({ token: tokenFor({ userId: id }), username: `usuario${id}`, expiresAt: Date.now() + 3600_000 })
