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

export type DocumentLifecycle = 'ACTIVE' | 'ARCHIVED'
export type IngestionStatus =
  'STORED' | 'QUEUED' | 'PROCESSING' | 'READY' | 'FAILED'

export interface DocumentVersion {
  id: string
  documentId: string
  versionNumber: number
  originalFilename: string
  declaredContentType: string
  detectedContentType: string
  byteSize: number
  sha256Hex: string
  parserName: string | null
  parserVersion: string | null
  ingestionStatus: IngestionStatus
  failureCode: string | null
  failureMessage: string | null
  createdBySubject: string
  createdAt: string
  readyAt: string | null
  textUnitCount: number
}

export interface DocumentSummary {
  id: string
  title: string
  status: DocumentLifecycle
  createdBySubject: string
  createdAt: string
  archivedAt: string | null
  latestVersion: DocumentVersion
}

export interface DocumentPage {
  content: DocumentSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface DocumentDetail {
  id: string
  organizationId: string
  workspaceId: string
  organizationSlug: string
  workspaceSlug: string
  title: string
  status: DocumentLifecycle
  createdBySubject: string
  createdAt: string
  updatedAt: string
  archivedAt: string | null
  versions: DocumentVersion[]
}

export interface UploadAccepted {
  documentId: string
  versionId: string
  versionNumber: number
  ingestionStatus: IngestionStatus
  sha256Hex: string
  byteSize: number
}

export type RetrievalMode = 'AUTO' | 'LEXICAL' | 'VECTOR' | 'HYBRID'

export interface RetrievalCapabilities {
  lexicalAvailable: boolean
  vectorAvailable: boolean
  availableModes: RetrievalMode[]
  autoMode: 'LEXICAL' | 'HYBRID'
  embeddingProvider: string | null
  embeddingModel: string | null
  embeddingDimension: number | null
}

export interface RetrievalCitation {
  documentId: string
  documentTitle: string
  documentVersionId: string
  versionNumber: number
  locatorType: 'PAGE' | 'DOCUMENT'
  locatorValue: string
  startCharacter: number
  endCharacter: number
  snippet: string
}

export interface RetrievalSearchResult {
  chunkId: string
  rank: number
  score: number
  lexicalScore: number | null
  vectorScore: number | null
  citation: RetrievalCitation
}

export interface RetrievalSearchResponse {
  query: string
  requestedMode: RetrievalMode
  effectiveMode: Exclude<RetrievalMode, 'AUTO'>
  topK: number
  results: RetrievalSearchResult[]
}

export type AnswerStatus = 'ANSWERED' | 'INSUFFICIENT_EVIDENCE'

export interface AnswerCitation extends RetrievalCitation {
  citationId: string
  chunkId: string
}

export interface AnswerResponse {
  requestId: string
  status: AnswerStatus
  answer: string
  requestedRetrievalMode: RetrievalMode
  effectiveRetrievalMode: Exclude<RetrievalMode, 'AUTO'>
  retrievedChunkCount: number
  contextCharacters: number
  provider: string
  model: string
  citations: AnswerCitation[]
}

export type ReviewReason =
  'INSUFFICIENT_EVIDENCE' | 'USER_ESCALATION' | 'REVIEW_REQUESTED'
export type ReviewStatus = 'OPEN' | 'IN_REVIEW' | 'RESOLVED' | 'DISMISSED'
export type ReviewResolution =
  | 'EVIDENCE_CONFIRMED'
  | 'KNOWLEDGE_GAP'
  | 'DOCUMENT_UPDATE_REQUIRED'
  | 'QUESTION_OUT_OF_SCOPE'
  | 'OTHER'

export interface ReviewEvidence {
  citationId: string
  rank: number
  cited: boolean
  chunkId: string
  documentId: string
  documentTitle: string
  documentVersionId: string
  versionNumber: number
  locatorType: 'PAGE' | 'DOCUMENT'
  locatorValue: string
  startCharacter: number
  endCharacter: number
  excerpt: string
}

export interface ReviewAuditEvent {
  id: string
  eventType:
    | 'REVIEW_CASE_CREATED'
    | 'REVIEW_CASE_CLAIMED'
    | 'REVIEW_CASE_RESOLVED'
    | 'REVIEW_CASE_DISMISSED'
  actorSubject: string
  actorDisplayName: string
  occurredAt: string
  metadata: Record<string, unknown>
}

export interface ReviewCaseSummary {
  id: string
  answerId: string
  reason: ReviewReason
  status: ReviewStatus
  questionPreview: string
  createdBySubject: string
  createdByDisplayName: string
  assignedToSubject: string | null
  assignedToDisplayName: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface ReviewCasePage {
  content: ReviewCaseSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ReviewCaseDetail extends ReviewCaseSummary {
  requestNote: string | null
  question: string
  answerStatus: AnswerStatus
  answer: string
  requestedRetrievalMode: RetrievalMode
  effectiveRetrievalMode: Exclude<RetrievalMode, 'AUTO'>
  retrievedChunkCount: number
  contextCharacters: number
  resolution: ReviewResolution | null
  reviewerNote: string | null
  resolvedBySubject: string | null
  resolvedByDisplayName: string | null
  resolvedAt: string | null
  evidence: ReviewEvidence[]
  auditEvents: ReviewAuditEvent[]
}

export interface ReviewCaseFilters {
  status?: ReviewStatus
  reason?: ReviewReason
  assignedToMe?: boolean
  unassigned?: boolean
  createdByMe?: boolean
  page?: number
  size?: number
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code?: string,
    safeMessage?: string,
  ) {
    super(safeMessage ?? `API request failed with HTTP ${status}`)
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
  listDocuments(
    organizationSlug: string,
    workspaceSlug: string,
    status: DocumentLifecycle,
    signal?: AbortSignal,
  ): Promise<DocumentPage>
  getDocument(
    organizationSlug: string,
    workspaceSlug: string,
    documentId: string,
    signal?: AbortSignal,
  ): Promise<DocumentDetail>
  uploadDocument(
    organizationSlug: string,
    workspaceSlug: string,
    file: File,
    title?: string,
  ): Promise<UploadAccepted>
  uploadVersion(
    organizationSlug: string,
    workspaceSlug: string,
    documentId: string,
    file: File,
  ): Promise<UploadAccepted>
  archiveDocument(
    organizationSlug: string,
    workspaceSlug: string,
    documentId: string,
  ): Promise<DocumentDetail>
  retryIngestion(
    organizationSlug: string,
    workspaceSlug: string,
    documentId: string,
    versionId: string,
  ): Promise<DocumentVersion>
  downloadDocument(
    organizationSlug: string,
    workspaceSlug: string,
    documentId: string,
    versionId: string,
  ): Promise<Blob>
  getRetrievalCapabilities(
    organizationSlug: string,
    workspaceSlug: string,
    signal?: AbortSignal,
  ): Promise<RetrievalCapabilities>
  search(
    organizationSlug: string,
    workspaceSlug: string,
    query: string,
    mode: RetrievalMode,
    topK: number,
  ): Promise<RetrievalSearchResponse>
  answer(
    organizationSlug: string,
    workspaceSlug: string,
    question: string,
    retrievalMode: RetrievalMode,
    retrievalTopK: number,
  ): Promise<AnswerResponse>
  createReviewCase(
    organizationSlug: string,
    workspaceSlug: string,
    answerId: string,
    reason: ReviewReason,
    note?: string,
  ): Promise<ReviewCaseDetail>
  listReviewCases(
    organizationSlug: string,
    workspaceSlug: string,
    filters?: ReviewCaseFilters,
    signal?: AbortSignal,
  ): Promise<ReviewCasePage>
  getReviewCase(
    organizationSlug: string,
    workspaceSlug: string,
    reviewCaseId: string,
    signal?: AbortSignal,
  ): Promise<ReviewCaseDetail>
  claimReviewCase(
    organizationSlug: string,
    workspaceSlug: string,
    reviewCaseId: string,
  ): Promise<ReviewCaseDetail>
  resolveReviewCase(
    organizationSlug: string,
    workspaceSlug: string,
    reviewCaseId: string,
    resolution: ReviewResolution,
    reviewerNote: string,
    version: number,
  ): Promise<ReviewCaseDetail>
  dismissReviewCase(
    organizationSlug: string,
    workspaceSlug: string,
    reviewCaseId: string,
    reviewerNote: string,
    version: number,
  ): Promise<ReviewCaseDetail>
}

export function createAuthenticatedApiClient(
  accessToken: string,
): AuthenticatedApiClient {
  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    headers.set('Authorization', `Bearer ${accessToken}`)
    const response = await fetch(path, { ...init, headers })

    if (!response.ok) {
      let errorBody: { code?: string; message?: string } = {}
      try {
        errorBody = (await response.json()) as typeof errorBody
      } catch {
        // Authentication handlers may intentionally return an empty body.
      }
      throw new ApiError(response.status, errorBody.code, errorBody.message)
    }

    return (await response.json()) as T
  }

  const documentBase = (organizationSlug: string, workspaceSlug: string) =>
    `/api/v1/organizations/${encodeURIComponent(organizationSlug)}/workspaces/${encodeURIComponent(workspaceSlug)}/documents`
  const retrievalBase = (organizationSlug: string, workspaceSlug: string) =>
    `/api/v1/organizations/${encodeURIComponent(organizationSlug)}/workspaces/${encodeURIComponent(workspaceSlug)}/retrieval`
  const answerBase = (organizationSlug: string, workspaceSlug: string) =>
    `/api/v1/organizations/${encodeURIComponent(organizationSlug)}/workspaces/${encodeURIComponent(workspaceSlug)}/answers`
  const reviewBase = (organizationSlug: string, workspaceSlug: string) =>
    `/api/v1/organizations/${encodeURIComponent(organizationSlug)}/workspaces/${encodeURIComponent(workspaceSlug)}/review-cases`

  return {
    getMe: (signal) => request<CurrentUser>('/api/v1/me', { signal }),
    getAdminSystemSummary: (signal) =>
      request<AdminSystemSummary>('/api/v1/admin/system-summary', { signal }),
    getOrganizationSummary: (organizationSlug, signal) =>
      request<OrganizationSummary>(
        `/api/v1/organizations/${encodeURIComponent(organizationSlug)}/summary`,
        { signal },
      ),
    listDocuments: (organizationSlug, workspaceSlug, status, signal) =>
      request<DocumentPage>(
        `${documentBase(organizationSlug, workspaceSlug)}?status=${status}&page=0&size=100`,
        { signal },
      ),
    getDocument: (organizationSlug, workspaceSlug, documentId, signal) =>
      request<DocumentDetail>(
        `${documentBase(organizationSlug, workspaceSlug)}/${encodeURIComponent(documentId)}`,
        { signal },
      ),
    uploadDocument: (organizationSlug, workspaceSlug, file, title) => {
      const form = new FormData()
      form.set('file', file)
      if (title?.trim()) form.set('title', title.trim())
      return request<UploadAccepted>(
        documentBase(organizationSlug, workspaceSlug),
        { method: 'POST', body: form },
      )
    },
    uploadVersion: (organizationSlug, workspaceSlug, documentId, file) => {
      const form = new FormData()
      form.set('file', file)
      return request<UploadAccepted>(
        `${documentBase(organizationSlug, workspaceSlug)}/${encodeURIComponent(documentId)}/versions`,
        { method: 'POST', body: form },
      )
    },
    archiveDocument: (organizationSlug, workspaceSlug, documentId) =>
      request<DocumentDetail>(
        `${documentBase(organizationSlug, workspaceSlug)}/${encodeURIComponent(documentId)}/archive`,
        { method: 'POST' },
      ),
    retryIngestion: (organizationSlug, workspaceSlug, documentId, versionId) =>
      request<DocumentVersion>(
        `${documentBase(organizationSlug, workspaceSlug)}/${encodeURIComponent(documentId)}/versions/${encodeURIComponent(versionId)}/retry`,
        { method: 'POST' },
      ),
    downloadDocument: async (
      organizationSlug,
      workspaceSlug,
      documentId,
      versionId,
    ) => {
      const response = await fetch(
        `${documentBase(organizationSlug, workspaceSlug)}/${encodeURIComponent(documentId)}/versions/${encodeURIComponent(versionId)}/download`,
        {
          headers: { Authorization: `Bearer ${accessToken}` },
        },
      )
      if (!response.ok) throw new ApiError(response.status)
      return response.blob()
    },
    getRetrievalCapabilities: (organizationSlug, workspaceSlug, signal) =>
      request<RetrievalCapabilities>(
        `${retrievalBase(organizationSlug, workspaceSlug)}/capabilities`,
        { signal },
      ),
    search: (organizationSlug, workspaceSlug, query, mode, topK) =>
      request<RetrievalSearchResponse>(
        `${retrievalBase(organizationSlug, workspaceSlug)}/search`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ query, mode, topK }),
        },
      ),
    answer: (
      organizationSlug,
      workspaceSlug,
      question,
      retrievalMode,
      retrievalTopK,
    ) =>
      request<AnswerResponse>(answerBase(organizationSlug, workspaceSlug), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question, retrievalMode, retrievalTopK }),
      }),
    createReviewCase: (
      organizationSlug,
      workspaceSlug,
      answerId,
      reason,
      note,
    ) =>
      request<ReviewCaseDetail>(
        `${answerBase(organizationSlug, workspaceSlug)}/${encodeURIComponent(answerId)}/review-case`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ reason, note: note?.trim() || null }),
        },
      ),
    listReviewCases: (
      organizationSlug,
      workspaceSlug,
      filters = {},
      signal,
    ) => {
      const parameters = new URLSearchParams({
        page: String(filters.page ?? 0),
        size: String(filters.size ?? 20),
      })
      if (filters.status) parameters.set('status', filters.status)
      if (filters.reason) parameters.set('reason', filters.reason)
      if (filters.assignedToMe) parameters.set('assignedToMe', 'true')
      if (filters.unassigned) parameters.set('unassigned', 'true')
      if (filters.createdByMe) parameters.set('createdByMe', 'true')
      return request<ReviewCasePage>(
        `${reviewBase(organizationSlug, workspaceSlug)}?${parameters}`,
        { signal },
      )
    },
    getReviewCase: (organizationSlug, workspaceSlug, reviewCaseId, signal) =>
      request<ReviewCaseDetail>(
        `${reviewBase(organizationSlug, workspaceSlug)}/${encodeURIComponent(reviewCaseId)}`,
        { signal },
      ),
    claimReviewCase: (organizationSlug, workspaceSlug, reviewCaseId) =>
      request<ReviewCaseDetail>(
        `${reviewBase(organizationSlug, workspaceSlug)}/${encodeURIComponent(reviewCaseId)}/claim`,
        { method: 'POST' },
      ),
    resolveReviewCase: (
      organizationSlug,
      workspaceSlug,
      reviewCaseId,
      resolution,
      reviewerNote,
      version,
    ) =>
      request<ReviewCaseDetail>(
        `${reviewBase(organizationSlug, workspaceSlug)}/${encodeURIComponent(reviewCaseId)}/resolve`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ resolution, reviewerNote, version }),
        },
      ),
    dismissReviewCase: (
      organizationSlug,
      workspaceSlug,
      reviewCaseId,
      reviewerNote,
      version,
    ) =>
      request<ReviewCaseDetail>(
        `${reviewBase(organizationSlug, workspaceSlug)}/${encodeURIComponent(reviewCaseId)}/dismiss`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ reviewerNote, version }),
        },
      ),
  }
}
