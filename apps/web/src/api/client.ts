export interface WorkspaceMembership {
  id: string
  slug: string
  displayName: string
}

export interface OrganizationMembership {
  id: string
  slug: string
  displayName: string
  role: 'TENANT_ADMIN' | 'MEMBER' | 'AUDITOR'
  workspaces: WorkspaceMembership[]
}

export interface CurrentUser {
  subject: string
  email: string
  displayName: string
  platformRoles: string[]
  organizations: OrganizationMembership[]
}

export interface AdminSystemSummary {
  organizationCount: number
  workspaceCount: number
  userProfileCount: number
  membershipCount: number
}

export interface OrganizationSummary {
  id: string
  slug: string
  displayName: string
  workspaceCount: number
}

export class ApiError extends Error {
  constructor(public readonly status: number) {
    super(`API request failed with HTTP ${status}`)
    this.name = 'ApiError'
  }
}

export interface AuthenticatedApiClient {
  getMe(signal?: AbortSignal): Promise<CurrentUser>
  getAdminSystemSummary(signal?: AbortSignal): Promise<AdminSystemSummary>
  getOrganizationSummary(
    organizationSlug: string,
    signal?: AbortSignal,
  ): Promise<OrganizationSummary>
}

export function createAuthenticatedApiClient(
  accessToken: string,
): AuthenticatedApiClient {
  async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
    const response = await fetch(path, {
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      signal,
    })

    if (!response.ok) {
      throw new ApiError(response.status)
    }

    return (await response.json()) as T
  }

  return {
    getMe: (signal) => get<CurrentUser>('/api/v1/me', signal),
    getAdminSystemSummary: (signal) =>
      get<AdminSystemSummary>('/api/v1/admin/system-summary', signal),
    getOrganizationSummary: (organizationSlug, signal) =>
      get<OrganizationSummary>(
        `/api/v1/organizations/${encodeURIComponent(organizationSlug)}/summary`,
        signal,
      ),
  }
}
