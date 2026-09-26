export const formatBirthDate = birthDate => {
  if (!birthDate) return 'N/D'
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(birthDate)
  return match ? `${match[3]}-${match[2]}-${match[1]}` : birthDate
}
