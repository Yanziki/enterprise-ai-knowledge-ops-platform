import { useEffect, useState, type FormEvent } from 'react'
import {
  ApiError,
  type AuthenticatedApiClient,
  type RetrievalCapabilities,
  type RetrievalMode,
  type RetrievalSearchResponse,
} from '../api/client'

export function KnowledgeSearch({
  api,
  organizationSlug,
  workspaceSlug,
}: {
  api: AuthenticatedApiClient
  organizationSlug: string
  workspaceSlug: string
}) {
  const [capabilities, setCapabilities] =
    useState<RetrievalCapabilities | null>(null)
  const [query, setQuery] = useState('')
  const [mode, setMode] = useState<RetrievalMode>('AUTO')
  const [topK, setTopK] = useState(5)
  const [response, setResponse] = useState<RetrievalSearchResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!organizationSlug || !workspaceSlug) return
    const controller = new AbortController()
    setCapabilities(null)
    setResponse(null)
    setError(null)
    api
      .getRetrievalCapabilities(
        organizationSlug,
        workspaceSlug,
        controller.signal,
      )
      .then(setCapabilities)
      .catch((requestError: unknown) => {
        if (!(
          requestError instanceof DOMException &&
          requestError.name === 'AbortError'
        )) {
          setError(
            searchError(requestError, 'Search capabilities are unavailable.'),
          )
        }
      })
    return () => controller.abort()
  }, [api, organizationSlug, workspaceSlug])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!query.trim() || !capabilities) return
    setLoading(true)
    setError(null)
    setResponse(null)
    try {
      setResponse(
        await api.search(
          organizationSlug,
          workspaceSlug,
          query.trim(),
          mode,
          topK,
        ),
      )
    } catch (requestError) {
      setError(searchError(requestError, 'Knowledge search failed.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="search-panel" aria-labelledby="search-title">
      <div className="search-heading">
        <div>
          <p className="card-label">Tenant-authorized retrieval</p>
          <h3 id="search-title">Search indexed knowledge</h3>
          <p>
            Ranked chunks include the exact document, version, locator, and
            source offsets used to produce every result.
          </p>
        </div>
        {capabilities && (
          <div className="capability-note" role="status">
            <strong>AUTO uses {capabilities.autoMode.toLowerCase()}</strong>
            <span>
              {capabilities.vectorAvailable
                ? `${capabilities.embeddingProvider} · ${capabilities.embeddingDimension} dimensions`
                : 'Vector retrieval is not configured for this workspace.'}
            </span>
          </div>
        )}
      </div>

      <form className="search-form" onSubmit={(event) => void submit(event)}>
        <div className="search-query-field">
          <label htmlFor="knowledge-query">Query</label>
          <input
            id="knowledge-query"
            value={query}
            maxLength={2000}
            placeholder="e.g. travel expense approval"
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
        <div>
          <label htmlFor="retrieval-mode">Mode</label>
          <select
            id="retrieval-mode"
            value={mode}
            disabled={!capabilities}
            onChange={(event) => setMode(event.target.value as RetrievalMode)}
          >
            {(capabilities?.availableModes ?? ['AUTO', 'LEXICAL']).map(
              (availableMode) => (
                <option key={availableMode} value={availableMode}>
                  {availableMode}
                </option>
              ),
            )}
          </select>
        </div>
        <div>
          <label htmlFor="result-count">Results</label>
          <select
            id="result-count"
            value={topK}
            onChange={(event) => setTopK(Number(event.target.value))}
          >
            {[3, 5, 10, 20].map((count) => (
              <option key={count} value={count}>
                {count}
              </option>
            ))}
          </select>
        </div>
        <button
          className="primary-action"
          type="submit"
          disabled={!capabilities || !query.trim() || loading}
        >
          {loading ? 'Searching…' : 'Search knowledge'}
        </button>
      </form>

      {error && (
        <p className="error-message" role="alert">
          {error}
        </p>
      )}
      {response && (
        <div className="search-results" aria-live="polite">
          <div className="search-summary">
            <strong>
              {response.results.length} result
              {response.results.length === 1 ? '' : 's'}
            </strong>
            <span>
              Requested {response.requestedMode} · used {response.effectiveMode}
            </span>
          </div>
          {response.results.length === 0 ? (
            <p className="empty-state">
              No authorized indexed content matched this query.
            </p>
          ) : (
            <ol className="result-list">
              {response.results.map((result) => (
                <li key={result.chunkId}>
                  <div className="result-heading">
                    <div>
                      <span className="result-rank">#{result.rank}</span>
                      <h4>{result.citation.documentTitle}</h4>
                    </div>
                    <code>{result.score.toFixed(6)}</code>
                  </div>
                  <blockquote>{result.citation.snippet}</blockquote>
                  <dl className="citation-grid">
                    <div>
                      <dt>Version</dt>
                      <dd>v{result.citation.versionNumber}</dd>
                    </div>
                    <div>
                      <dt>Locator</dt>
                      <dd>
                        {result.citation.locatorType.toLowerCase()} ·{' '}
                        {result.citation.locatorValue}
                      </dd>
                    </div>
                    <div>
                      <dt>Offsets</dt>
                      <dd>
                        {result.citation.startCharacter}–
                        {result.citation.endCharacter}
                      </dd>
                    </div>
                    <div>
                      <dt>Document / version IDs</dt>
                      <dd>
                        <code>
                          {shortId(result.citation.documentId)} /{' '}
                          {shortId(result.citation.documentVersionId)}
                        </code>
                      </dd>
                    </div>
                  </dl>
                </li>
              ))}
            </ol>
          )}
        </div>
      )}
    </section>
  )
}

function searchError(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    if (error.status === 403)
      return 'Search content is not permitted for this role.'
    return error.message
  }
  return fallback
}

function shortId(id: string) {
  return `${id.slice(0, 8)}…${id.slice(-4)}`
}
