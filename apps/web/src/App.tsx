import { useEffect, useState } from 'react'
import { getSystemStatus, type SystemStatus } from './api/system'
import './styles.css'

type BackendState =
  | { phase: 'loading' }
  | { phase: 'online'; details: SystemStatus }
  | { phase: 'unavailable' }

const futureModules = [
  {
    name: 'Knowledge',
    description: 'Governed sources and citation-ready content.',
  },
  {
    name: 'Conversations',
    description: 'Grounded assistance with inspectable evidence.',
  },
  {
    name: 'Workflows',
    description: 'Human-approved operational actions.',
  },
  {
    name: 'Audit',
    description: 'Traceable access, approvals, and outcomes.',
  },
] as const

function App() {
  const [backend, setBackend] = useState<BackendState>({ phase: 'loading' })

  useEffect(() => {
    const controller = new AbortController()

    getSystemStatus(controller.signal)
      .then((details) => setBackend({ phase: 'online', details }))
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setBackend({ phase: 'unavailable' })
        }
      })

    return () => controller.abort()
  }, [])

  return (
    <div className="site-shell">
      <header className="site-header">
        <a className="brand" href="#top" aria-label="Platform home">
          EAKO
        </a>
        <span className="milestone">Milestone · Foundation</span>
      </header>

      <main id="top">
        <section className="hero" aria-labelledby="page-title">
          <p className="eyebrow">Governed knowledge. Supervised operations.</p>
          <h1 id="page-title">
            Enterprise AI Knowledge &amp; Operations Platform
          </h1>
          <p className="hero-copy">
            A production-oriented engineering foundation for secure, traceable,
            citation-grounded enterprise assistance.
          </p>

          <section
            className="status-panel"
            aria-live="polite"
            aria-label="Backend status"
          >
            <div>
              <span
                className={`status-dot status-dot--${backend.phase}`}
                aria-hidden="true"
              />
              <span className="status-label">
                {backend.phase === 'loading' && 'Checking backend…'}
                {backend.phase === 'online' && 'Backend online'}
                {backend.phase === 'unavailable' && 'Backend unavailable'}
              </span>
            </div>
            <span className="service-version">
              {backend.phase === 'online'
                ? `${backend.details.service} · ${backend.details.version}`
                : 'Service version unavailable'}
            </span>
          </section>
        </section>

        <section className="modules" aria-labelledby="modules-title">
          <div className="section-heading">
            <p className="eyebrow">Planned platform boundaries</p>
            <h2 id="modules-title">Built deliberately, module by module.</h2>
          </div>
          <ul className="module-grid">
            {futureModules.map((module, index) => (
              <li className="module-card" key={module.name}>
                <span className="module-number">0{index + 1}</span>
                <h3>{module.name}</h3>
                <p>{module.description}</p>
                <span className="planned">Planned</span>
              </li>
            ))}
          </ul>
        </section>
      </main>

      <footer>
        <span>Foundation milestone</span>
        <span>No production AI functionality yet</span>
      </footer>
    </div>
  )
}

export default App
