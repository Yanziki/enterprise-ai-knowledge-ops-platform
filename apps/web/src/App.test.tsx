import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

const useAuthMock = vi.hoisted(() => vi.fn())

vi.mock('react-oidc-context', () => ({ useAuth: useAuthMock }))

const memberProfile = {
  subject: '00000000-0000-0000-0000-000000000002',
  email: 'member@example.com',
  displayName: 'Acme Member',
  platformRoles: ['MEMBER'],
  organizations: [
    {
      id: '10000000-0000-0000-0000-000000000001',
      slug: 'acme',
      displayName: 'Acme Corporation',
      role: 'MEMBER',
      workspaces: [
        {
          id: '20000000-0000-0000-0000-000000000001',
          slug: 'operations',
          displayName: 'Acme Operations',
        },
      ],
    },
  ],
} as const

afterEach(() => {
  vi.unstubAllGlobals()
  useAuthMock.mockReset()
})

function configureAuth(
  overrides: Record<string, unknown> = {},
): Record<string, ReturnType<typeof vi.fn>> {
  const actions = {
    signinRedirect: vi.fn().mockResolvedValue(undefined),
    signoutRedirect: vi.fn().mockResolvedValue(undefined),
  }
  useAuthMock.mockReturnValue({
    activeNavigator: undefined,
    isLoading: false,
    error: null,
    isAuthenticated: false,
    user: null,
    ...actions,
    ...overrides,
  })
  return actions
}

describe('App identity states', () => {
  it('shows a clear login flow for an unauthenticated user', async () => {
    const actions = configureAuth()
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise(() => undefined)),
    )

    render(<App />)
    await userEvent.click(
      screen.getByRole('button', { name: 'Log in with Keycloak' }),
    )

    expect(
      screen.getByText('Milestone · Identity & tenancy'),
    ).toBeInTheDocument()
    expect(actions.signinRedirect).toHaveBeenCalledOnce()
    expect(
      screen.getByRole('heading', { name: 'Knowledge' }),
    ).toBeInTheDocument()
  })

  it('shows identity loading and provider error states', async () => {
    configureAuth({ isLoading: true })
    const { rerender } = render(<App />)
    expect(screen.getByText('Loading identity session…')).toBeInTheDocument()

    const actions = configureAuth({
      isLoading: false,
      error: new Error('provider offline'),
    })
    rerender(<App />)
    expect(
      screen.getByText('Identity provider unavailable'),
    ).toBeInTheDocument()
    await userEvent.click(
      screen.getByRole('button', { name: 'Try login again' }),
    )
    expect(actions.signinRedirect).toHaveBeenCalledOnce()
  })

  it('renders protected member data and hides the admin-only card', async () => {
    configureAuth({
      isAuthenticated: true,
      user: { access_token: 'synthetic-member-token' },
    })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(memberProfile), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    render(<App />)

    expect(await screen.findByText('Welcome, Acme Member')).toBeInTheDocument()
    expect(screen.getByText('member@example.com')).toBeInTheDocument()
    expect(screen.getAllByText('Acme Corporation')).toHaveLength(2)
    expect(screen.getByText(/MEMBER · Acme Operations/)).toBeInTheDocument()
    expect(screen.queryByText('System summary')).not.toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/me',
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer synthetic-member-token',
        }),
      }),
    )
  })

  it('loads the admin-only card only for a platform admin', async () => {
    configureAuth({
      isAuthenticated: true,
      user: { access_token: 'synthetic-admin-token' },
    })
    const adminProfile = {
      ...memberProfile,
      email: 'admin@example.com',
      displayName: 'Platform Admin',
      platformRoles: ['PLATFORM_ADMIN'],
    }
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(
          new Response(JSON.stringify(adminProfile), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
        .mockResolvedValueOnce(
          new Response(
            JSON.stringify({
              organizationCount: 2,
              workspaceCount: 2,
              userProfileCount: 3,
              membershipCount: 3,
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        ),
    )

    render(<App />)

    expect(await screen.findByText('System summary')).toBeInTheDocument()
    expect(await screen.findByText('3 / 3')).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/admin/system-summary',
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer synthetic-admin-token',
        }),
      }),
    )
  })

  it('builds the tenant selector from me and requests the selected summary', async () => {
    configureAuth({
      isAuthenticated: true,
      user: { access_token: 'synthetic-member-token' },
    })
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(
          new Response(JSON.stringify(memberProfile), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        )
        .mockResolvedValueOnce(
          new Response(
            JSON.stringify({
              id: memberProfile.organizations[0].id,
              slug: 'acme',
              displayName: 'Acme Corporation',
              workspaceCount: 1,
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        ),
    )

    render(<App />)
    expect(
      await screen.findByRole('option', { name: 'Acme Corporation' }),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('option', { name: 'Globex Corporation' }),
    ).not.toBeInTheDocument()

    await userEvent.click(
      screen.getByRole('button', { name: 'Request summary' }),
    )

    expect(
      await screen.findByText('Acme Corporation: 1 workspace'),
    ).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/organizations/acme/summary',
      expect.any(Object),
    )
  })

  it('handles API 401 and 403 without exposing token contents', async () => {
    const actions = configureAuth({
      isAuthenticated: true,
      user: { access_token: 'secret-token-value' },
    })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(null, { status: 401 })),
    )
    const { rerender } = render(<App />)
    expect(await screen.findByText('Your session expired')).toBeInTheDocument()
    expect(screen.queryByText('secret-token-value')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Sign in again' }))
    expect(actions.signinRedirect).toHaveBeenCalledOnce()

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(null, { status: 403 })),
    )
    configureAuth({
      isAuthenticated: true,
      user: { access_token: 'different-secret-token-value' },
    })
    rerender(<App />)
    expect(
      await screen.findByText('Identity is not provisioned'),
    ).toBeInTheDocument()
  })

  it('invokes OIDC logout from the protected dashboard', async () => {
    const actions = configureAuth({
      isAuthenticated: true,
      user: { access_token: 'synthetic-member-token' },
    })
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(memberProfile), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )
    render(<App />)

    await userEvent.click(
      await screen.findByRole('button', { name: 'Log out' }),
    )

    expect(actions.signoutRedirect).toHaveBeenCalledWith({
      post_logout_redirect_uri: window.location.origin,
    })
  })
})
