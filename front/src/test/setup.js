import '@testing-library/jest-dom/vitest'

// jsdom has no layout engine; give Recharts a measurable chart area.
globalThis.ResizeObserver = class {
  constructor(callback) { this.callback = callback }
  observe(target) { this.callback([{ target, contentRect: { width: 640, height: 280 } }]) }
  unobserve() {}
  disconnect() {}
}
