import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type {
  CurrentUser,
  DocumentDetail,
  DocumentPage,
  RetrievalCapabilities,
  RetrievalSearchResponse,
  UploadAccepted,
} from './api/client'
import App from './App'

const useAuthMock = vi.hoisted(() => vi.fn())

vi.mock('react-oidc-context', () => ({ useAuth: useAuthMock }))

const memberProfile: CurrentUser = {
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
}
const memberOrganization = memberProfile.organizations[0]!
const memberWorkspace = memberOrganization.workspaces[0]!

const emptyDocumentPage = {
  content: [],
  page: 0,
  size: 100,
  totalElements: 0,
  totalPages: 0,
} satisfies DocumentPage

const readyVersion = {
  id: '50000000-0000-0000-0000-000000000001',
  documentId: '60000000-0000-0000-0000-000000000001',
  versionNumber: 1,
  originalFilename: 'acme-policy.txt',
  declaredContentType: 'text/plain',
  detectedContentType: 'text/plain',
  byteSize: 128,
  sha256Hex: 'a'.repeat(64),
  parserName: 'JDK UTF-8',
  parserVersion: '21.0.8',
  ingestionStatus: 'READY' as const,
  failureCode: null,
  failureMessage: null,
  createdBySubject: memberProfile.subject,
  createdAt: '2026-08-09T10:00:00Z',
  readyAt: '2026-08-09T10:00:01Z',
  textUnitCount: 1,
}

const readyDocumentPage: DocumentPage = {
  content: [
    {
      id: readyVersion.documentId,
      title: 'Acme synthetic policy',
      status: 'ACTIVE',
      createdBySubject: memberProfile.subject,
      createdAt: readyVersion.createdAt,
      archivedAt: null,
      latestVersion: readyVersion,
    },
  ],
  page: 0,
  size: 100,
  totalElements: 1,
  totalPages: 1,
}

const readyDocumentDetail: DocumentDetail = {
  id: readyVersion.documentId,
  organizationId: memberOrganization.id,
  workspaceId: memberWorkspace.id,
  organizationSlug: 'acme',
  workspaceSlug: 'operations',
  title: 'Acme synthetic policy',
  status: 'ACTIVE',
  createdBySubject: memberProfile.subject,
  createdAt: readyVersion.createdAt,
  updatedAt: readyVersion.readyAt,
  archivedAt: null,
  versions: [readyVersion],
}

const retrievalCapabilities: RetrievalCapabilities = {
  lexicalAvailable: true,
  vectorAvailable: true,
  availableModes: ['AUTO', 'LEXICAL', 'VECTOR', 'HYBRID'],
  autoMode: 'HYBRID',
  embeddingProvider: 'deterministic-smoke',
  embeddingModel: 'hashed-token-v1-test-only',
  embeddingDimension: 64,
}

const retrievalResponse: RetrievalSearchResponse = {
  query: 'expense approval',
  requestedMode: 'AUTO',
  effectiveMode: 'HYBRID',
  topK: 5,
  results: [
    {
      chunkId: '70000000-0000-0000-0000-000000000001',
      rank: 1,
      score: 0.032786,
      lexicalScore: 1.25,
      vectorScore: 0.81,
      citation: {
        documentId: readyVersion.documentId,
        documentTitle: 'Acme synthetic policy',
        documentVersionId: readyVersion.id,
        versionNumber: 1,
        locatorType: 'DOCUMENT',
        locatorValue: 'body',
        startCharacter: 0,
        endCharacter: 57,
        snippet:
          'Travel expenses require manager approval before reimbursement.',
      },
    },
  ],
}

function authenticatedFetch(
  profile: CurrentUser,
  options: {
    adminSummary?: Record<string, number>
    organizationSummary?: Record<string, string | number>
    documentPage?: DocumentPage
    documentDetail?: DocumentDetail
    uploadAccepted?: UploadAccepted
    retrievalCapabilities?: RetrievalCapabilities
    retrievalResponse?: RetrievalSearchResponse
  } = {},
) {
  return vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = input.toString()
    if (path === '/api/v1/me') return jsonResponse(profile)
    if (path === '/api/v1/admin/system-summary') {
      return jsonResponse(options.adminSummary ?? {}, 200)
    }
    if (path.endsWith('/summary')) {
      return jsonResponse(options.organizationSummary ?? {}, 200)
    }
    if (path.includes('/documents?')) {
      return jsonResponse(options.documentPage ?? emptyDocumentPage, 200)
    }
    if (path.endsWith('/documents') && init?.method === 'POST') {
      return jsonResponse(options.uploadAccepted ?? {}, 202)
    }
    if (path.endsWith('/retrieval/capabilities')) {
      return jsonResponse(
        options.retrievalCapabilities ?? retrievalCapabilities,
        200,
      )
    }
    if (path.endsWith('/retrieval/search') && init?.method === 'POST') {
      return jsonResponse(options.retrievalResponse ?? retrievalResponse, 200)
    }
    if (path.includes('/documents/')) {
      return jsonResponse(options.documentDetail ?? {}, 200)
    }
    return Promise.resolve(new Response(null, { status: 404 }))
  })
}

function jsonResponse(value: unknown, status = 200) {
  return Promise.resolve(
    new Response(JSON.stringify(value), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
}

function expectBearerCall(path: string, accessToken: string) {
  const call = vi
    .mocked(fetch)
    .mock.calls.find(([request]) => request.toString() === path)
  expect(call).toBeDefined()
  expect(new Headers(call?.[1]?.headers).get('Authorization')).toBe(
    `Bearer ${accessToken}`,
  )
}

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
      screen.getByText('Milestone · Document ingestion & provenance'),
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
    vi.stubGlobal('fetch', authenticatedFetch(memberProfile))

    render(<App />)

    expect(await screen.findByText('Welcome, Acme Member')).toBeInTheDocument()
    expect(screen.getByText('member@example.com')).toBeInTheDocument()
    expect(screen.getAllByText('Acme Corporation')).toHaveLength(3)
    expect(screen.getByText(/MEMBER · Acme Operations/)).toBeInTheDocument()
    expect(screen.queryByText('System summary')).not.toBeInTheDocument()
    expectBearerCall('/api/v1/me', 'synthetic-member-token')
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
      authenticatedFetch(adminProfile, {
        adminSummary: {
          organizationCount: 2,
          workspaceCount: 2,
          userProfileCount: 3,
          membershipCount: 3,
        },
      }),
    )

    render(<App />)

    expect(await screen.findByText('System summary')).toBeInTheDocument()
    expect(await screen.findByText('3 / 3')).toBeInTheDocument()
    expectBearerCall('/api/v1/admin/system-summary', 'synthetic-admin-token')
  })

  it('builds the tenant selector from me and requests the selected summary', async () => {
    configureAuth({
      isAuthenticated: true,
      user: { access_token: 'synthetic-member-token' },
    })
    vi.stubGlobal(
      'fetch',
      authenticatedFetch(memberProfile, {
        organizationSummary: {
          id: memberOrganization.id,
          slug: 'acme',
          displayName: 'Acme Corporation',
          workspaceCount: 1,
        },
      }),
    )

    render(<App />)
    expect(
      await screen.findAllByRole('option', { name: 'Acme Corporation' }),
    ).toHaveLength(2)
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

  it('gives a member document provenance and download controls without mutation controls', async () => {
    configureAuth({
      isAuthenticated: true,
      user: { access_token: 'synthetic-member-token' },
    })
    vi.stubGlobal(
      'fetch',
      authenticatedFetch(memberProfile, {
        documentPage: readyDocumentPage,
        documentDetail: readyDocumentDetail,
      }),
    )

    render(<App />)

    expect(
      await screen.findByRole('heading', { name: 'Knowledge workspace' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('Acme synthetic policy')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Upload document' }),
    ).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'View detail' }))
    expect(await screen.findByText('a'.repeat(64))).toBeInTheDocument()
    expect(screen.getByText('JDK UTF-8 · 21.0.8')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Archive document' }),
    ).not.toBeInTheDocument()
  })

  it('runs authorized retrieval and renders bounded citation provenance', async () => {
    configureAuth({
      isAuthenticated: true,
      user: { access_token: 'synthetic-member-token' },
    })
    vi.stubGlobal(
      'fetch',
      authenticatedFetch(memberProfile, {
        documentPage: readyDocumentPage,
        retrievalResponse,
      }),
    )

    render(<App />)
    expect(
      await screen.findByRole('heading', { name: 'Search indexed knowledge' }),
    ).toBeInTheDocument()
    expect(await screen.findByText('AUTO uses hybrid')).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'VECTOR' })).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Query'), 'expense approval')
    await userEvent.click(
      screen.getByRole('button', { name: 'Search knowledge' }),
    )

    expect(
      await screen.findByText(
        'Travel expenses require manager approval before reimbursement.',
      ),
    ).toBeInTheDocument()
    expect(screen.getAllByText('v1')).toHaveLength(2)
    expect(screen.getByText('document · body')).toBeInTheDocument()
    expect(screen.getByText('0–57')).toBeInTheDocument()
    const searchCall = vi
      .mocked(fetch)
      .mock.calls.find(([path]) =>
        path.toString().endsWith('/retrieval/search'),
      )
    expect(searchCall?.[1]?.method).toBe('POST')
    expect(searchCall?.[1]?.body).toBe(
      JSON.stringify({ query: 'expense approval', mode: 'AUTO', topK: 5 }),
    )
  })

  it('shows tenant-admin upload validation and accepted processing state', async () => {
    const adminProfile: CurrentUser = {
      ...memberProfile,
      displayName: 'Acme Tenant Admin',
      email: 'admin@example.com',
      platformRoles: ['TENANT_ADMIN'],
      organizations: [{ ...memberOrganization, role: 'TENANT_ADMIN' }],
    }
    const accepted: UploadAccepted = {
      documentId: readyVersion.documentId,
      versionId: readyVersion.id,
      versionNumber: 1,
      ingestionStatus: 'QUEUED',
      sha256Hex: readyVersion.sha256Hex,
      byteSize: readyVersion.byteSize,
    }
    configureAuth({
      isAuthenticated: true,
      user: { access_token: 'synthetic-admin-token' },
    })
    vi.stubGlobal(
      'fetch',
      authenticatedFetch(adminProfile, {
        documentDetail: readyDocumentDetail,
        uploadAccepted: accepted,
      }),
    )
    const user = userEvent.setup({ applyAccept: false })

    render(<App />)
    const input = await screen.findByLabelText('Original file')
    await user.upload(
      input,
      new File(['not supported'], 'sample.html', { type: 'text/html' }),
    )
    expect(
      screen.getByText('Choose a PDF, TXT, or Markdown file.'),
    ).toBeInTheDocument()

    await user.upload(
      input,
      new File(['synthetic policy'], 'policy.txt', { type: 'text/plain' }),
    )
    await user.click(screen.getByRole('button', { name: 'Upload document' }))
    expect(
      await screen.findByText('Accepted · version 1 · QUEUED'),
    ).toBeInTheDocument()
    expect(
      await screen.findByRole('button', { name: 'Archive document' }),
    ).toBeInTheDocument()
    const uploadCall = vi
      .mocked(fetch)
      .mock.calls.find(
        ([path, init]) =>
          path.toString().endsWith('/documents') && init?.method === 'POST',
      )
    expect(uploadCall?.[1]?.body).toBeInstanceOf(FormData)
  })

  it('keeps auditor access metadata-only in the Knowledge workspace', async () => {
    const auditorProfile: CurrentUser = {
      ...memberProfile,
      displayName: 'Acme Auditor',
      platformRoles: ['AUDITOR'],
      organizations: [{ ...memberOrganization, role: 'AUDITOR' }],
    }
    configureAuth({
      isAuthenticated: true,
      user: { access_token: 'synthetic-auditor-token' },
    })
    vi.stubGlobal(
      'fetch',
      authenticatedFetch(auditorProfile, { documentPage: readyDocumentPage }),
    )

    render(<App />)
    expect(await screen.findByText('Acme synthetic policy')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Download' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Upload document' }),
    ).not.toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: 'Search indexed knowledge' }),
    ).not.toBeInTheDocument()
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
    vi.stubGlobal('fetch', authenticatedFetch(memberProfile))
    render(<App />)

    await userEvent.click(
      await screen.findByRole('button', { name: 'Log out' }),
    )

    expect(actions.signoutRedirect).toHaveBeenCalledWith({
      post_logout_redirect_uri: window.location.origin,
    })
  })
})
