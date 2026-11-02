import { useEffect, useRef } from 'react'

export const useDialogFocus = (open, onClose) => {
  const ref = useRef(null)
  const close = useRef(onClose)
  useEffect(() => { close.current = onClose }, [onClose])
  useEffect(() => {
    if (!open || !ref.current) return
    const previous = document.activeElement
    const dialog = ref.current
    const controls = () => [...dialog.querySelectorAll('button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), [tabindex="0"]')]
    const first = controls()[0] || dialog
    first.focus()
    const keydown = event => {
      if (event.key === 'Escape') { event.preventDefault(); close.current?.(); return }
      if (event.key !== 'Tab') return
      const items = controls()
      const start = items[0] || dialog
      const last = items.at(-1) || dialog
      if (event.shiftKey && (document.activeElement === start || document.activeElement === dialog)) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); start.focus() }
    }
    dialog.addEventListener('keydown', keydown)
    return () => { dialog.removeEventListener('keydown', keydown); if (previous?.isConnected) previous.focus() }
  }, [open])
  return ref
}
