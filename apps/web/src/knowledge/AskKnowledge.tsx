import { useState, type FormEvent } from 'react'
import {
  ApiError,
  type AnswerResponse,
  type AuthenticatedApiClient,
  type RetrievalMode,
} from '../api/client'

export function AskKnowledge({
  api,
  organizationSlug,
  workspaceSlug,
}: {
  api: AuthenticatedApiClient
  organizationSlug: string
  workspaceSlug: string
}) {
  const [question, setQuestion] = useState('')
  const [mode, setMode] = useState<RetrievalMode>('AUTO')
  const [topK, setTopK] = useState(5)
  const [response, setResponse] = useState<AnswerResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!question.trim() || !organizationSlug || !workspaceSlug) return
    setLoading(true)
    setError(null)
    setResponse(null)
    try {
      setResponse(
        await api.answer(
          organizationSlug,
          workspaceSlug,
          question.trim(),
          mode,
          topK,
        ),
      )
    } catch (requestError) {
      setError(answerError(requestError))
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="ask-panel" aria-labelledby="ask-title">
      <div className="ask-heading">
        <div>
          <p className="card-label">Grounded single-turn answer</p>
          <h3 id="ask-title">Ask Knowledge</h3>
          <p>
            Answers use only authorized current evidence. Citation aliases are
            validated and provenance is reconstructed by the server.
          </p>
        </div>
        <span className="grounding-badge">Server-validated citations</span>
      </div>

      <form className="ask-form" onSubmit={(event) => void submit(event)}>
        <div className="ask-question-field">
          <label htmlFor="knowledge-question">Question</label>
          <textarea
            id="knowledge-question"
            value={question}
            rows={3}
            maxLength={2000}
            placeholder="What does the approved policy say?"
            onChange={(event) => setQuestion(event.target.value)}
          />
        </div>
        <div>
          <label htmlFor="answer-retrieval-mode">Retrieval</label>
          <select
            id="answer-retrieval-mode"
            value={mode}
            onChange={(event) => setMode(event.target.value as RetrievalMode)}
          >
            {(['AUTO', 'LEXICAL', 'VECTOR', 'HYBRID'] as RetrievalMode[]).map(
              (value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ),
            )}
          </select>
        </div>
        <div>
          <label htmlFor="answer-evidence-count">Evidence</label>
          <select
            id="answer-evidence-count"
            value={topK}
            onChange={(event) => setTopK(Number(event.target.value))}
          >
            {[3, 5, 8].map((value) => (
              <option key={value} value={value}>
                {value} chunks
              </option>
            ))}
          </select>
        </div>
        <button
          className="primary-action"
          type="submit"
          disabled={!question.trim() || loading}
        >
          {loading ? 'Generating…' : 'Generate grounded answer'}
        </button>
      </form>

      {error && (
        <p className="error-message" role="alert">
          {error}
        </p>
      )}

      {response && (
        <article
          className={`answer-result answer-result--${response.status.toLowerCase()}`}
          aria-live="polite"
        >
          <div className="answer-result-heading">
            <div>
              <span className="answer-status">{response.status}</span>
              <h4>
                {response.status === 'ANSWERED'
                  ? 'Grounded answer'
                  : 'Insufficient evidence'}
              </h4>
            </div>
            <span>
              {response.effectiveRetrievalMode} · {response.provider}
            </span>
          </div>
          <p className="answer-text">{response.answer}</p>
          {response.citations.length > 0 && (
            <ol className="answer-citations">
              {response.citations.map((citation) => (
                <li key={citation.citationId}>
                  <div>
                    <strong>{citation.citationId}</strong>
                    <span>{citation.documentTitle}</span>
                  </div>
                  <blockquote>{citation.snippet}</blockquote>
                  <p>
                    v{citation.versionNumber} ·{' '}
                    {citation.locatorType.toLowerCase()} {citation.locatorValue}{' '}
                    · offsets {citation.startCharacter}–{citation.endCharacter}
                  </p>
                </li>
              ))}
            </ol>
          )}
          <p className="answer-metadata">
            {response.retrievedChunkCount} authorized chunks ·{' '}
            {response.contextCharacters} context characters · request{' '}
            {shortId(response.requestId)}
          </p>
        </article>
      )}
    </section>
  )
}

function answerError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 403)
      return 'Grounded answers are not permitted for this role.'
    if (error.code === 'ANSWER_PROVIDER_UNAVAILABLE')
      return 'Answer generation is not configured. Cited search remains available.'
    return error.message
  }
  return 'Grounded answer generation is unavailable.'
}

function shortId(id: string) {
  return `${id.slice(0, 8)}…${id.slice(-4)}`
}
