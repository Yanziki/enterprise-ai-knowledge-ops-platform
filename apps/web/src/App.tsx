import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { useAuth } from 'react-oidc-context'
import {
  ApiError,
  createAuthenticatedApiClient,
  type AdminSystemSummary,
  type CurrentUser,
  type OrganizationSummary,
} from './api/client'
import { getSystemStatus, type SystemStatus } from './api/system'
import { KnowledgeWorkspace } from './knowledge/KnowledgeWorkspace'
import './styles.css'

type BackendState =
  | { phase: 'loading' }
  | { phase: 'online'; details: SystemStatus }
  | { phase: 'unavailable' }

type IdentityState =
  | { phase: 'loading' }
  | { phase: 'ready'; user: CurrentUser }
  | { phase: 'expired' }
  | { phase: 'forbidden' }
  | { phase: 'unavailable' }

const futureModules = [
  ['Conversations', 'Multi-turn assistance and governed session memory.'],
  ['Workflows', 'Human review of persisted answers and evidence.'],
  ['Audit', 'Append-only review lifecycle events and actor history.'],
] as const

function App() {
  const auth = useAuth()

  if (auth.activeNavigator === 'signinRedirect') {
    return <MessageShell title="Redirecting to secure login…" />
  }

  if (auth.activeNavigator === 'signoutRedirect') {
    return <MessageShell title="Signing out…" />
  }

  if (auth.isLoading) {
    return <MessageShell title="Loading identity session…" />
  }

  if (auth.error) {
    return (
      <MessageShell
        title="Identity provider unavailable"
        copy="The login service could not complete the request. Check Keycloak health and try again."
        actionLabel="Try login again"
        onAction={() => void auth.signinRedirect()}
      />
    )
  }

  if (!auth.isAuthenticated || !auth.user?.access_token) {
    return <PublicLanding onLogin={() => void auth.signinRedirect()} />
  }

  return (
    <ProtectedDashboard
      accessToken={auth.user.access_token}
      onLogin={() => void auth.signinRedirect()}
      onLogout={() =>
        void auth.signoutRedirect({
          post_logout_redirect_uri: window.location.origin,
        })
      }
    />
  )
}

function PublicLanding({ onLogin }: { onLogin: () => void }) {
  return (
    <PageShell milestone="Human review and audit">
      <section className="hero" aria-labelledby="page-title">
        <p className="eyebrow">Private sources. Verifiable provenance.</p>
        <h1 id="page-title">
          Enterprise AI Knowledge &amp; Operations Platform
        </h1>
        <p className="hero-copy">
          Sign in through the local OIDC provider to access private,
          tenant-scoped documents, cited retrieval, and grounded answers.
        </p>
        <button className="primary-action" type="button" onClick={onLogin}>
          Log in with Keycloak
        </button>
        <BackendStatus />
      </section>
      <PlannedModules />
    </PageShell>
  )
}

function ProtectedDashboard({
  accessToken,
  onLogin,
  onLogout,
}: {
  accessToken: string
  onLogin: () => void
  onLogout: () => void
}) {
  const api = useMemo(
    () => createAuthenticatedApiClient(accessToken),
    [accessToken],
  )
  const [identity, setIdentity] = useState<IdentityState>({ phase: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    api
      .getMe(controller.signal)
      .then((user) => setIdentity({ phase: 'ready', user }))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return
        if (error instanceof ApiError && error.status === 401) {
          setIdentity({ phase: 'expired' })
        } else if (error instanceof ApiError && error.status === 403) {
          setIdentity({ phase: 'forbidden' })
        } else {
          setIdentity({ phase: 'unavailable' })
        }
      })
    return () => controller.abort()
  }, [api])

  if (identity.phase === 'loading') {
    return <MessageShell title="Loading protected dashboard…" />
  }

  if (identity.phase === 'expired') {
    return (
      <MessageShell
        title="Your session expired"
        copy="Sign in again to obtain a fresh access token."
        actionLabel="Sign in again"
        onAction={onLogin}
      />
    )
  }

  if (identity.phase === 'forbidden') {
    return (
      <MessageShell
        title="Identity is not provisioned"
        copy="The provider authenticated this subject, but the platform has no authorized application profile."
        actionLabel="Log out"
        onAction={onLogout}
      />
    )
  }

  if (identity.phase === 'unavailable') {
    return (
      <MessageShell
        title="Protected API unavailable"
        copy="The authenticated profile could not be loaded. Check API health and try again."
        actionLabel="Log out"
        onAction={onLogout}
      />
    )
  }

  return (
    <PageShell milestone="Human review and audit" onLogout={onLogout}>
      <main className="dashboard" id="top">
        <section className="dashboard-intro" aria-labelledby="dashboard-title">
          <p className="eyebrow">Protected knowledge operations</p>
          <h1 id="dashboard-title">Welcome, {identity.user.displayName}</h1>
          <p className="hero-copy">
            Identity, workspace access, document bytes, and provenance are all
            enforced by the protected API.
          </p>
        </section>

        <section className="dashboard-grid" aria-label="Authenticated identity">
          <article className="data-card">
            <p className="card-label">Identity</p>
            <h2>{identity.user.displayName}</h2>
            <dl>
              <div>
                <dt>Email</dt>
                <dd>{identity.user.email}</dd>
              </div>
              <div>
                <dt>Platform roles</dt>
                <dd>{identity.user.platformRoles.join(', ') || 'None'}</dd>
              </div>
            </dl>
          </article>

          <MembershipCard user={identity.user} />

          {identity.user.platformRoles.includes('PLATFORM_ADMIN') && (
            <AdminCard api={api} />
          )}
        </section>

        <TenantSummaryCard user={identity.user} api={api} />
        <KnowledgeWorkspace user={identity.user} api={api} />
        <PlannedModules />
      </main>
    </PageShell>
  )
}

function MembershipCard({ user }: { user: CurrentUser }) {
  return (
    <article className="data-card">
      <p className="card-label">Memberships</p>
      <h2>Authorized organizations</h2>
      {user.organizations.length === 0 ? (
        <p>No organization memberships are assigned.</p>
      ) : (
        <ul className="membership-list">
          {user.organizations.map((organization) => (
            <li key={organization.id}>
              <strong>{organization.displayName}</strong>
              <span>
                {organization.role} ·{' '}
                {organization.workspaces
                  .map((item) => item.displayName)
                  .join(', ')}
              </span>
            </li>
          ))}
        </ul>
      )}
    </article>
  )
}

function AdminCard({
  api,
}: {
  api: ReturnType<typeof createAuthenticatedApiClient>
}) {
  const [summary, setSummary] = useState<AdminSystemSummary | null>(null)
  const [denied, setDenied] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    api
      .getAdminSystemSummary(controller.signal)
      .then(setSummary)
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setDenied(error instanceof ApiError && error.status === 403)
        }
      })
    return () => controller.abort()
  }, [api])

  return (
    <article className="data-card data-card--admin">
      <p className="card-label">Platform admin only</p>
      <h2>System summary</h2>
      {summary ? (
        <dl>
          <div>
            <dt>Organizations</dt>
            <dd>{summary.organizationCount}</dd>
          </div>
          <div>
            <dt>Workspaces</dt>
            <dd>{summary.workspaceCount}</dd>
          </div>
          <div>
            <dt>Profiles / memberships</dt>
            <dd>
              {summary.userProfileCount} / {summary.membershipCount}
            </dd>
          </div>
        </dl>
      ) : (
        <p>{denied ? 'Admin access denied.' : 'Loading admin summary…'}</p>
      )}
    </article>
  )
}

function TenantSummaryCard({
  user,
  api,
}: {
  user: CurrentUser
  api: ReturnType<typeof createAuthenticatedApiClient>
}) {
  const [selectedSlug, setSelectedSlug] = useState(
    user.organizations.at(0)?.slug ?? '',
  )
  const [summary, setSummary] = useState<OrganizationSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  const requestSummary = async () => {
    if (!selectedSlug) return
    setSummary(null)
    setError(null)
    try {
      setSummary(await api.getOrganizationSummary(selectedSlug))
    } catch (requestError) {
      setError(
        requestError instanceof ApiError && requestError.status === 403
          ? 'Organization access denied by the API.'
          : 'Organization summary is unavailable.',
      )
    }
  }

  return (
    <section className="tenant-panel" aria-labelledby="tenant-title">
      <div>
        <p className="eyebrow">Tenant-isolation demonstration</p>
        <h2 id="tenant-title">Request an authorized organization summary</h2>
        <p>
          Options come only from <code>/api/v1/me</code>. The backend
          independently verifies access for every request.
        </p>
      </div>
      {user.organizations.length > 0 ? (
        <div className="tenant-controls">
          <label htmlFor="organization">Organization</label>
          <select
            id="organization"
            value={selectedSlug}
            onChange={(event) => {
              setSelectedSlug(event.target.value)
              setSummary(null)
              setError(null)
            }}
          >
            {user.organizations.map((organization) => (
              <option key={organization.id} value={organization.slug}>
                {organization.displayName}
              </option>
            ))}
          </select>
          <button
            className="secondary-action"
            type="button"
            onClick={requestSummary}
          >
            Request summary
          </button>
          {summary && (
            <p className="result" role="status">
              {summary.displayName}: {summary.workspaceCount} workspace
              {summary.workspaceCount === 1 ? '' : 's'}
            </p>
          )}
          {error && (
            <p className="error-message" role="alert">
              {error}
            </p>
          )}
        </div>
      ) : (
        <p>
          No tenant can be selected because this identity has no memberships.
        </p>
      )}
    </section>
  )
}

function BackendStatus() {
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
  )
}

function PlannedModules() {
  return (
    <section className="modules" aria-labelledby="modules-title">
      <div className="section-heading">
        <p className="eyebrow">Platform boundaries</p>
        <h2 id="modules-title">Grounded answers now support human review.</h2>
      </div>
      <ul className="module-grid">
        <li className="module-card module-card--active">
          <span className="module-number">01</span>
          <h3>Knowledge</h3>
          <p>
            Governed originals, cited retrieval, and server-validated answers.
          </p>
          <span className="planned">Active milestone</span>
        </li>
        {futureModules.map(([name, description], index) => (
          <li
            className={`module-card ${name === 'Conversations' ? '' : 'module-card--active'}`}
            key={name}
          >
            <span className="module-number">0{index + 2}</span>
            <h3>{name}</h3>
            <p>{description}</p>
            <span className="planned">
              {name === 'Conversations' ? 'Planned' : 'Active milestone'}
            </span>
          </li>
        ))}
      </ul>
    </section>
  )
}

function PageShell({
  milestone,
  onLogout,
  children,
}: {
  milestone: string
  onLogout?: () => void
  children: ReactNode
}) {
  return (
    <div className="site-shell">
      <header className="site-header">
        <a className="brand" href="#top" aria-label="Platform home">
          EAKO
        </a>
        <div className="header-actions">
          <span className="milestone">Milestone · {milestone}</span>
          {onLogout && (
            <button className="text-action" type="button" onClick={onLogout}>
              Log out
            </button>
          )}
        </div>
      </header>
      {children}
      <footer>
        <span>
          Private ingestion, grounded answers, and governed human review
        </span>
        <span>
          Review decisions only · no chat, agents, or automated actions
        </span>
      </footer>
    </div>
  )
}

function MessageShell({
  title,
  copy,
  actionLabel,
  onAction,
}: {
  title: string
  copy?: string
  actionLabel?: string
  onAction?: () => void
}) {
  return (
    <PageShell milestone="Human review and audit">
      <main className="message-panel">
        <p className="eyebrow">Identity boundary</p>
        <h1>{title}</h1>
        {copy && <p className="hero-copy">{copy}</p>}
        {actionLabel && onAction && (
          <button className="primary-action" type="button" onClick={onAction}>
            {actionLabel}
          </button>
        )}
      </main>
    </PageShell>
  )
}

export default App
