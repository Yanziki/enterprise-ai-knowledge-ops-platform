import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type FormEvent,
} from 'react'
import {
  ApiError,
  type AuthenticatedApiClient,
  type CurrentUser,
  type DocumentDetail,
  type DocumentLifecycle,
  type DocumentSummary,
  type DocumentVersion,
} from '../api/client'
import { KnowledgeSearch } from './KnowledgeSearch'

const MAX_FILE_BYTES = 20 * 1024 * 1024
const SUPPORTED_EXTENSIONS = ['.pdf', '.txt', '.md', '.markdown']

type RequestState =
  | { phase: 'idle' }
  | { phase: 'uploading'; message: string }
  | { phase: 'accepted'; message: string }
  | { phase: 'error'; message: string }

export function KnowledgeWorkspace({
  user,
  api,
}: {
  user: CurrentUser
  api: AuthenticatedApiClient
}) {
  const [organizationSlug, setOrganizationSlug] = useState(
    user.organizations.at(0)?.slug ?? '',
  )
  const selectedOrganization = useMemo(
    () =>
      user.organizations.find(
        (organization) => organization.slug === organizationSlug,
      ),
    [organizationSlug, user.organizations],
  )
  const [workspaceSlug, setWorkspaceSlug] = useState(
    selectedOrganization?.workspaces.at(0)?.slug ?? '',
  )
  const [lifecycle, setLifecycle] = useState<DocumentLifecycle>('ACTIVE')
  const [documents, setDocuments] = useState<DocumentSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [detail, setDetail] = useState<DocumentDetail | null>(null)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [uploadState, setUploadState] = useState<RequestState>({
    phase: 'idle',
  })
  const [versionFile, setVersionFile] = useState<File | null>(null)
  const [actionState, setActionState] = useState<RequestState>({
    phase: 'idle',
  })

  useEffect(() => {
    setWorkspaceSlug(selectedOrganization?.workspaces.at(0)?.slug ?? '')
    setDetail(null)
  }, [selectedOrganization])

  const canManage =
    user.platformRoles.includes('PLATFORM_ADMIN') ||
    (user.platformRoles.includes('TENANT_ADMIN') &&
      selectedOrganization?.role === 'TENANT_ADMIN')
  const canDownload =
    canManage ||
    (user.platformRoles.includes('MEMBER') &&
      selectedOrganization?.role === 'MEMBER')
  const canSearch =
    canManage ||
    (user.platformRoles.includes('MEMBER') &&
      selectedOrganization?.role === 'MEMBER')

  const loadDocuments = useCallback(
    async (signal?: AbortSignal) => {
      if (!organizationSlug || !workspaceSlug) {
        setDocuments([])
        return
      }
      setLoading(true)
      setError(null)
      try {
        const page = await api.listDocuments(
          organizationSlug,
          workspaceSlug,
          lifecycle,
          signal,
        )
        setDocuments(page.content)
      } catch (requestError) {
        if (!(
          requestError instanceof DOMException &&
          requestError.name === 'AbortError'
        )) {
          setError(errorMessage(requestError, 'Document list is unavailable.'))
        }
      } finally {
        if (!signal?.aborted) setLoading(false)
      }
    },
    [api, lifecycle, organizationSlug, workspaceSlug],
  )

  const loadDetail = useCallback(
    async (documentId: string, signal?: AbortSignal) => {
      if (!organizationSlug || !workspaceSlug) return
      try {
        setDetail(
          await api.getDocument(
            organizationSlug,
            workspaceSlug,
            documentId,
            signal,
          ),
        )
      } catch (requestError) {
        if (!(
          requestError instanceof DOMException &&
          requestError.name === 'AbortError'
        )) {
          setError(
            errorMessage(requestError, 'Document detail is unavailable.'),
          )
        }
      }
    },
    [api, organizationSlug, workspaceSlug],
  )

  useEffect(() => {
    const controller = new AbortController()
    void loadDocuments(controller.signal)
    return () => controller.abort()
  }, [loadDocuments])

  useEffect(() => {
    const needsPolling =
      documents.some((document) =>
        ['STORED', 'QUEUED', 'PROCESSING'].includes(
          document.latestVersion.ingestionStatus,
        ),
      ) ||
      detail?.versions.some((version) =>
        ['STORED', 'QUEUED', 'PROCESSING'].includes(version.ingestionStatus),
      )
    if (!needsPolling) return

    const interval = window.setInterval(() => {
      void loadDocuments()
      if (detail) void loadDetail(detail.id)
    }, 1500)
    return () => window.clearInterval(interval)
  }, [detail, documents, loadDetail, loadDocuments])

  const selectFile = (file: File | null, forVersion = false) => {
    const validationError = validateFile(file)
    const nextState: RequestState = validationError
      ? { phase: 'error', message: validationError }
      : { phase: 'idle' }
    if (forVersion) {
      setVersionFile(validationError ? null : file)
      setActionState(nextState)
    } else {
      setSelectedFile(validationError ? null : file)
      setUploadState(nextState)
    }
  }

  const upload = async (event: FormEvent) => {
    event.preventDefault()
    if (!selectedFile || !organizationSlug || !workspaceSlug) return
    setUploadState({ phase: 'uploading', message: 'Uploading bytes…' })
    try {
      const accepted = await api.uploadDocument(
        organizationSlug,
        workspaceSlug,
        selectedFile,
        title,
      )
      setUploadState({
        phase: 'accepted',
        message: `Accepted · version ${accepted.versionNumber} · ${accepted.ingestionStatus}`,
      })
      setSelectedFile(null)
      setTitle('')
      await loadDocuments()
      await loadDetail(accepted.documentId)
    } catch (requestError) {
      setUploadState({
        phase: 'error',
        message: errorMessage(requestError, 'Upload failed.'),
      })
    }
  }

  const uploadVersion = async () => {
    if (!versionFile || !detail) return
    setActionState({ phase: 'uploading', message: 'Uploading new version…' })
    try {
      const accepted = await api.uploadVersion(
        organizationSlug,
        workspaceSlug,
        detail.id,
        versionFile,
      )
      setActionState({
        phase: 'accepted',
        message: `Version ${accepted.versionNumber} accepted · ${accepted.ingestionStatus}`,
      })
      setVersionFile(null)
      await loadDocuments()
      await loadDetail(detail.id)
    } catch (requestError) {
      setActionState({
        phase: 'error',
        message: errorMessage(requestError, 'New version was rejected.'),
      })
    }
  }

  const archive = async () => {
    if (!detail) return
    setActionState({ phase: 'uploading', message: 'Archiving document…' })
    try {
      const archived = await api.archiveDocument(
        organizationSlug,
        workspaceSlug,
        detail.id,
      )
      setDetail(archived)
      setActionState({ phase: 'accepted', message: 'Document archived.' })
      await loadDocuments()
    } catch (requestError) {
      setActionState({
        phase: 'error',
        message: errorMessage(requestError, 'Archive failed.'),
      })
    }
  }

  const retry = async (version: DocumentVersion) => {
    if (!detail) return
    setActionState({ phase: 'uploading', message: 'Retry queued…' })
    try {
      await api.retryIngestion(
        organizationSlug,
        workspaceSlug,
        detail.id,
        version.id,
      )
      setActionState({ phase: 'accepted', message: 'Retry accepted.' })
      await loadDocuments()
      await loadDetail(detail.id)
    } catch (requestError) {
      setActionState({
        phase: 'error',
        message: errorMessage(requestError, 'Retry was rejected.'),
      })
    }
  }

  const download = async (documentId: string, version: DocumentVersion) => {
    setActionState({ phase: 'uploading', message: 'Preparing download…' })
    try {
      const blob = await api.downloadDocument(
        organizationSlug,
        workspaceSlug,
        documentId,
        version.id,
      )
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = version.originalFilename
      anchor.click()
      URL.revokeObjectURL(url)
      setActionState({ phase: 'accepted', message: 'Download authorized.' })
    } catch (requestError) {
      setActionState({
        phase: 'error',
        message: errorMessage(requestError, 'Download was denied.'),
      })
    }
  }

  return (
    <section className="knowledge" aria-labelledby="knowledge-title">
      <div className="knowledge-heading">
        <div>
          <p className="eyebrow">Governed knowledge boundary</p>
          <h2 id="knowledge-title">Knowledge workspace</h2>
          <p>
            Private originals, immutable provenance, and durable asynchronous
            extraction feed tenant-authorized lexical and vector retrieval.
          </p>
        </div>
        <div className="knowledge-selectors">
          <label htmlFor="knowledge-organization">Organization</label>
          <select
            id="knowledge-organization"
            value={organizationSlug}
            onChange={(event) => setOrganizationSlug(event.target.value)}
          >
            {user.organizations.map((organization) => (
              <option key={organization.id} value={organization.slug}>
                {organization.displayName}
              </option>
            ))}
          </select>
          <label htmlFor="knowledge-workspace">Workspace</label>
          <select
            id="knowledge-workspace"
            value={workspaceSlug}
            onChange={(event) => {
              setWorkspaceSlug(event.target.value)
              setDetail(null)
            }}
          >
            {selectedOrganization?.workspaces.map((workspace) => (
              <option key={workspace.id} value={workspace.slug}>
                {workspace.displayName}
              </option>
            ))}
          </select>
          <label htmlFor="document-lifecycle">Lifecycle</label>
          <select
            id="document-lifecycle"
            value={lifecycle}
            onChange={(event) => {
              setLifecycle(event.target.value as DocumentLifecycle)
              setDetail(null)
            }}
          >
            <option value="ACTIVE">Active</option>
            <option value="ARCHIVED">Archived</option>
          </select>
        </div>
      </div>

      {canManage && (
        <form className="upload-panel" onSubmit={(event) => void upload(event)}>
          <div>
            <p className="card-label">Tenant administrator action</p>
            <h3>Upload a document</h3>
            <p>PDF, TXT, or Markdown · maximum 20 MiB</p>
          </div>
          <div className="upload-field">
            <label htmlFor="document-title">Title (optional)</label>
            <input
              id="document-title"
              value={title}
              maxLength={255}
              onChange={(event) => setTitle(event.target.value)}
            />
          </div>
          <div className="upload-field">
            <label htmlFor="document-file">Original file</label>
            <input
              id="document-file"
              type="file"
              accept=".pdf,.txt,.md,.markdown,application/pdf,text/plain,text/markdown"
              onChange={(event) =>
                selectFile(event.target.files?.item(0) ?? null)
              }
            />
            {selectedFile && (
              <p className="selected-file">
                {selectedFile.name} · {formatBytes(selectedFile.size)}
              </p>
            )}
          </div>
          <button
            className="primary-action"
            type="submit"
            disabled={!selectedFile || uploadState.phase === 'uploading'}
          >
            Upload document
          </button>
          <RequestMessage state={uploadState} />
        </form>
      )}

      {canSearch && (
        <KnowledgeSearch
          api={api}
          organizationSlug={organizationSlug}
          workspaceSlug={workspaceSlug}
        />
      )}

      <div className="document-panel">
        <div className="document-panel-heading">
          <div>
            <p className="card-label">Authorized scope</p>
            <h3>{lifecycle === 'ACTIVE' ? 'Active' : 'Archived'} documents</h3>
          </div>
          <button
            className="text-action"
            type="button"
            onClick={() => void loadDocuments()}
          >
            Refresh
          </button>
        </div>
        {loading && <p role="status">Loading documents…</p>}
        {error && (
          <p className="error-message" role="alert">
            {error}
          </p>
        )}
        {!loading && !error && documents.length === 0 && (
          <p className="empty-state">No documents exist in this scope.</p>
        )}
        {documents.length > 0 && (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Document</th>
                  <th>Version</th>
                  <th>Size</th>
                  <th>SHA-256</th>
                  <th>Status</th>
                  <th>Uploaded</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {documents.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <strong>{item.title}</strong>
                      <span>{item.latestVersion.originalFilename}</span>
                    </td>
                    <td>v{item.latestVersion.versionNumber}</td>
                    <td>{formatBytes(item.latestVersion.byteSize)}</td>
                    <td>
                      <code>{shortHash(item.latestVersion.sha256Hex)}</code>
                    </td>
                    <td>
                      <StatusBadge
                        status={item.latestVersion.ingestionStatus}
                      />
                    </td>
                    <td>{formatTime(item.createdAt)}</td>
                    <td className="row-actions">
                      <button
                        className="text-action"
                        type="button"
                        onClick={() => void loadDetail(item.id)}
                      >
                        View detail
                      </button>
                      {canDownload && (
                        <button
                          className="text-action"
                          type="button"
                          onClick={() =>
                            void download(item.id, item.latestVersion)
                          }
                        >
                          Download
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {detail && (
        <DocumentDetailPanel
          detail={detail}
          canManage={canManage}
          canDownload={canDownload}
          versionFile={versionFile}
          actionState={actionState}
          onVersionFile={(file) => selectFile(file, true)}
          onUploadVersion={() => void uploadVersion()}
          onArchive={() => void archive()}
          onRetry={(version) => void retry(version)}
          onDownload={(version) => void download(detail.id, version)}
          onClose={() => setDetail(null)}
        />
      )}
    </section>
  )
}

function DocumentDetailPanel({
  detail,
  canManage,
  canDownload,
  versionFile,
  actionState,
  onVersionFile,
  onUploadVersion,
  onArchive,
  onRetry,
  onDownload,
  onClose,
}: {
  detail: DocumentDetail
  canManage: boolean
  canDownload: boolean
  versionFile: File | null
  actionState: RequestState
  onVersionFile: (file: File | null) => void
  onUploadVersion: () => void
  onArchive: () => void
  onRetry: (version: DocumentVersion) => void
  onDownload: (version: DocumentVersion) => void
  onClose: () => void
}) {
  return (
    <section
      className="document-detail"
      aria-labelledby="document-detail-title"
    >
      <div className="document-panel-heading">
        <div>
          <p className="card-label">Document provenance</p>
          <h3 id="document-detail-title">{detail.title}</h3>
          <p>
            {detail.organizationSlug} / {detail.workspaceSlug} · {detail.status}
          </p>
        </div>
        <button className="text-action" type="button" onClick={onClose}>
          Close detail
        </button>
      </div>
      <dl className="document-identity">
        <div>
          <dt>Logical document ID</dt>
          <dd>
            <code>{detail.id}</code>
          </dd>
        </div>
        <div>
          <dt>Created by</dt>
          <dd>{detail.createdBySubject}</dd>
        </div>
        <div>
          <dt>Created</dt>
          <dd>{formatTime(detail.createdAt)}</dd>
        </div>
      </dl>

      {canManage && detail.status === 'ACTIVE' && (
        <div className="detail-actions">
          <label htmlFor="version-file">New immutable version</label>
          <input
            id="version-file"
            type="file"
            accept=".pdf,.txt,.md,.markdown,application/pdf,text/plain,text/markdown"
            onChange={(event) =>
              onVersionFile(event.target.files?.item(0) ?? null)
            }
          />
          <button
            className="secondary-action"
            type="button"
            disabled={!versionFile || actionState.phase === 'uploading'}
            onClick={onUploadVersion}
          >
            Upload new version
          </button>
          <button className="danger-action" type="button" onClick={onArchive}>
            Archive document
          </button>
        </div>
      )}
      <RequestMessage state={actionState} />

      <div className="version-list">
        {detail.versions.map((version) => (
          <article className="version-card" key={version.id}>
            <div className="version-heading">
              <div>
                <p className="card-label">Version {version.versionNumber}</p>
                <h4>{version.originalFilename}</h4>
              </div>
              <StatusBadge status={version.ingestionStatus} />
            </div>
            <dl>
              <div>
                <dt>SHA-256</dt>
                <dd>
                  <code className="full-hash">{version.sha256Hex}</code>
                </dd>
              </div>
              <div>
                <dt>Bytes</dt>
                <dd>{formatBytes(version.byteSize)}</dd>
              </div>
              <div>
                <dt>Declared / detected MIME</dt>
                <dd>
                  {version.declaredContentType} / {version.detectedContentType}
                </dd>
              </div>
              <div>
                <dt>Uploader</dt>
                <dd>{version.createdBySubject}</dd>
              </div>
              <div>
                <dt>Parser</dt>
                <dd>
                  {version.parserName
                    ? `${version.parserName} · ${version.parserVersion}`
                    : 'Pending'}
                </dd>
              </div>
              <div>
                <dt>Created / ready</dt>
                <dd>
                  {formatTime(version.createdAt)} /{' '}
                  {formatTime(version.readyAt)}
                </dd>
              </div>
              <div>
                <dt>Normalized units</dt>
                <dd>{version.textUnitCount}</dd>
              </div>
            </dl>
            {version.failureCode && (
              <p className="failure-detail" role="alert">
                {version.failureCode}: {version.failureMessage}
              </p>
            )}
            <div className="row-actions">
              {canDownload && (
                <button
                  className="text-action"
                  type="button"
                  onClick={() => onDownload(version)}
                >
                  Download original
                </button>
              )}
              {canManage && version.ingestionStatus === 'FAILED' && (
                <button
                  className="text-action"
                  type="button"
                  onClick={() => onRetry(version)}
                >
                  Retry ingestion
                </button>
              )}
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

function StatusBadge({ status }: { status: string }) {
  return (
    <span className={`status-badge status-badge--${status.toLowerCase()}`}>
      {status}
    </span>
  )
}

function RequestMessage({ state }: { state: RequestState }) {
  if (state.phase === 'idle') return null
  return (
    <p
      className={state.phase === 'error' ? 'error-message' : 'result'}
      role={state.phase === 'error' ? 'alert' : 'status'}
    >
      {state.message}
    </p>
  )
}

function validateFile(file: File | null): string | null {
  if (!file) return null
  const lowerName = file.name.toLowerCase()
  if (
    !SUPPORTED_EXTENSIONS.some((extension) => lowerName.endsWith(extension))
  ) {
    return 'Choose a PDF, TXT, or Markdown file.'
  }
  if (file.size === 0) return 'The selected file is empty.'
  if (file.size > MAX_FILE_BYTES) return 'The selected file exceeds 20 MiB.'
  return null
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.message
  return fallback
}

function shortHash(hash: string): string {
  return `${hash.slice(0, 10)}…${hash.slice(-6)}`
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MiB`
}

function formatTime(value: string | null): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}
