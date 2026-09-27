import { BrandPanel } from './BrandPanel'

export const AuthLayout = ({ icon: Icon, title, intro, children, footer }) => (
  <main className="auth-page d-flex align-items-center justify-content-center min-vh-100 p-3 p-md-4">
    <div className="auth-shell row g-0 w-100 overflow-hidden rounded shadow-sm">
      <BrandPanel />
      <section className="auth-form-panel col-md-7 d-flex flex-column justify-content-center">
        <div className="d-flex align-items-center justify-content-center gap-3 text-center auth-heading">
          <Icon aria-hidden="true" />
          <h1 className="mb-0">{title}</h1>
        </div>
        <p className="auth-intro text-center mb-4 mt-3">{intro}</p>
        {children}
        <p className="auth-switch text-center small mb-0 mt-4">{footer}</p>
      </section>
    </div>
  </main>
)
