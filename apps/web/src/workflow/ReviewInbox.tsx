import { useCallback, useEffect, useState } from 'react'
import {
  ApiError,
  type AuthenticatedApiClient,
  type CurrentUser,
  type ReviewCaseDetail,
  type ReviewCaseSummary,
  type ReviewReason,
  type ReviewResolution,
  type ReviewStatus,
} from '../api/client'

const statuses: Array<ReviewStatus | ''> = [
  '',
  'OPEN',
  'IN_REVIEW',
  'RESOLVED',
  'DISMISSED',
]
const reasons: Array<ReviewReason | ''> = [
  '',
  'INSUFFICIENT_EVIDENCE',
  'USER_ESCALATION',
  'REVIEW_REQUESTED',
]
const resolutions: ReviewResolution[] = [
  'EVIDENCE_CONFIRMED',
  'KNOWLEDGE_GAP',
  'DOCUMENT_UPDATE_REQUIRED',
  'QUESTION_OUT_OF_SCOPE',
  'OTHER',
]

export function ReviewInbox({
  api,
  user,
  organizationSlug,
  workspaceSlug,
  canManage,
  reloadKey,
}: {
  api: AuthenticatedApiClient
  user: CurrentUser
  organizationSlug: string
  workspaceSlug: string
  canManage: boolean
  reloadKey: number
}) {
  const [cases, setCases] = useState<ReviewCaseSummary[]>([])
  const [status, setStatus] = useState<ReviewStatus | ''>('')
  const [reason, setReason] = useState<ReviewReason | ''>('')
  const [assignedToMe, setAssignedToMe] = useState(false)
  const [unassigned, setUnassigned] = useState(false)
  const [createdByMe, setCreatedByMe] = useState(!canManage)
  const [detail, setDetail] = useState<ReviewCaseDetail | null>(null)
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [resolution, setResolution] =
    useState<ReviewResolution>('EVIDENCE_CONFIRMED')
  const [reviewerNote, setReviewerNote] = useState('')

  const loadCases = useCallback(
    async (signal?: AbortSignal) => {
      if (!organizationSlug || !workspaceSlug) return
      setLoading(true)
      setMessage(null)
      try {
        const page = await api.listReviewCases(
          organizationSlug,
          workspaceSlug,
          {
            status: status || undefined,
            reason: reason || undefined,
            assignedToMe,
            unassigned,
            createdByMe,
            size: 50,
          },
          signal,
        )
        setCases(page.content)
      } catch (error) {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setMessage('The review inbox is unavailable.')
        }
      } finally {
        if (!signal?.aborted) setLoading(false)
      }
    },
    [
      api,
      assignedToMe,
      createdByMe,
      organizationSlug,
      reason,
      status,
      unassigned,
      workspaceSlug,
    ],
  )

  useEffect(() => {
    const controller = new AbortController()
    void loadCases(controller.signal)
    return () => controller.abort()
  }, [loadCases, reloadKey])

  const openDetail = async (id: string) => {
    setMessage(null)
    try {
      setDetail(await api.getReviewCase(organizationSlug, workspaceSlug, id))
    } catch {
      setMessage('The review case could not be loaded.')
    }
  }

  const applyAction = async (action: 'claim' | 'resolve' | 'dismiss') => {
    if (!detail) return
    setMessage(null)
    try {
      const updated =
        action === 'claim'
          ? await api.claimReviewCase(
              organizationSlug,
              workspaceSlug,
              detail.id,
            )
          : action === 'resolve'
            ? await api.resolveReviewCase(
                organizationSlug,
                workspaceSlug,
                detail.id,
                resolution,
                reviewerNote,
                detail.version,
              )
            : await api.dismissReviewCase(
                organizationSlug,
                workspaceSlug,
                detail.id,
                reviewerNote,
                detail.version,
              )
      setDetail(updated)
      setReviewerNote('')
      await loadCases()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        try {
          setDetail(
            await api.getReviewCase(organizationSlug, workspaceSlug, detail.id),
          )
          await loadCases()
          setMessage(
            'This case changed in another session. The latest state has been loaded.',
          )
        } catch {
          setMessage(
            'This case changed in another session. Refresh it before retrying.',
          )
        }
      } else {
        setMessage('The review action was rejected.')
      }
    }
  }

  const assignedToCurrentUser =
    detail?.assignedToSubject === user.subject && detail.status === 'IN_REVIEW'

  return (
    <section className="review-inbox" aria-labelledby="review-inbox-title">
      <div className="review-inbox-heading">
        <div>
          <p className="eyebrow">Human-in-the-loop governance</p>
          <h3 id="review-inbox-title">Review Inbox</h3>
          <p>
            Inspect the original answer and its persisted evidence. Reviews do
            not rerun retrieval or generation.
          </p>
        </div>
        <button
          className="text-action"
          type="button"
          onClick={() => void loadCases()}
        >
          Refresh inbox
        </button>
      </div>

      <div className="review-filters">
        <label>
          Status
          <select
            value={status}
            onChange={(event) => setStatus(event.target.value as ReviewStatus)}
          >
            {statuses.map((value) => (
              <option key={value || 'ALL'} value={value}>
                {value || 'ALL'}
              </option>
            ))}
          </select>
        </label>
        <label>
          Reason
          <select
            value={reason}
            onChange={(event) => setReason(event.target.value as ReviewReason)}
          >
            {reasons.map((value) => (
              <option key={value || 'ALL'} value={value}>
                {value || 'ALL'}
              </option>
            ))}
          </select>
        </label>
        {canManage && (
          <>
            <FilterToggle
              label="Assigned to me"
              checked={assignedToMe}
              onChange={setAssignedToMe}
            />
            <FilterToggle
              label="Unassigned"
              checked={unassigned}
              onChange={setUnassigned}
            />
          </>
        )}
        <FilterToggle
          label="Created by me"
          checked={createdByMe}
          onChange={setCreatedByMe}
        />
      </div>

      {loading && <p role="status">Loading review cases…</p>}
      {message && (
        <p className="error-message" role="alert">
          {message}
        </p>
      )}
      {!loading && cases.length === 0 && (
        <p className="empty-state">No review cases match these filters.</p>
      )}
      {cases.length > 0 && (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Question</th>
                <th>Reason</th>
                <th>Status</th>
                <th>Requester</th>
                <th>Reviewer</th>
                <th>Created</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {cases.map((reviewCase) => (
                <tr key={reviewCase.id}>
                  <td>{reviewCase.questionPreview}</td>
                  <td>{humanize(reviewCase.reason)}</td>
                  <td>
                    <span className="status-badge">{reviewCase.status}</span>
                  </td>
                  <td>{reviewCase.createdByDisplayName}</td>
                  <td>{reviewCase.assignedToDisplayName ?? 'Unassigned'}</td>
                  <td>{formatTime(reviewCase.createdAt)}</td>
                  <td>
                    <button
                      className="text-action"
                      type="button"
                      onClick={() => void openDetail(reviewCase.id)}
                    >
                      Open review
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {detail && (
        <article
          className="review-detail"
          aria-labelledby="review-detail-title"
        >
          <div className="review-inbox-heading">
            <div>
              <p className="card-label">{humanize(detail.reason)}</p>
              <h4 id="review-detail-title">Review case</h4>
              <p>
                {detail.status} · requested by {detail.createdByDisplayName}
              </p>
            </div>
            <button
              className="text-action"
              type="button"
              onClick={() => setDetail(null)}
            >
              Close review
            </button>
          </div>
          <section className="review-snapshot">
            <h5>Original question</h5>
            <p>{detail.question}</p>
            <h5>Persisted answer</h5>
            <p>
              <strong>Answer status:</strong> {humanize(detail.answerStatus)}
            </p>
            <p>{detail.answer}</p>
            {detail.requestNote && (
              <p>
                <strong>Requester note:</strong> {detail.requestNote}
              </p>
            )}
          </section>
          <section>
            <h5>Evidence snapshot</h5>
            {detail.evidence.length === 0 ? (
              <p>No evidence was eligible for this answer.</p>
            ) : (
              <ol className="answer-citations">
                {detail.evidence.map((evidence) => (
                  <li key={evidence.citationId}>
                    <strong>{evidence.citationId}</strong>{' '}
                    {evidence.documentTitle} · v{evidence.versionNumber}
                    <blockquote>{evidence.excerpt}</blockquote>
                    <p>
                      {evidence.locatorType.toLowerCase()}{' '}
                      {evidence.locatorValue} · offsets{' '}
                      {evidence.startCharacter}–{evidence.endCharacter}
                    </p>
                  </li>
                ))}
              </ol>
            )}
          </section>

          {canManage && detail.status === 'OPEN' && (
            <div className="review-actions">
              <button
                className="primary-action"
                type="button"
                onClick={() => void applyAction('claim')}
              >
                Claim review
              </button>
              <button
                className="text-action"
                type="button"
                onClick={() => void applyAction('dismiss')}
              >
                Dismiss unassigned case
              </button>
            </div>
          )}
          {canManage && assignedToCurrentUser && (
            <div className="review-decision">
              <label>
                Resolution
                <select
                  value={resolution}
                  onChange={(event) =>
                    setResolution(event.target.value as ReviewResolution)
                  }
                >
                  {resolutions.map((value) => (
                    <option key={value} value={value}>
                      {humanize(value)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Reviewer note
                <textarea
                  rows={3}
                  maxLength={2000}
                  value={reviewerNote}
                  onChange={(event) => setReviewerNote(event.target.value)}
                />
              </label>
              <div className="row-actions">
                <button
                  className="primary-action"
                  type="button"
                  onClick={() => void applyAction('resolve')}
                >
                  Resolve review
                </button>
                <button
                  className="danger-action"
                  type="button"
                  onClick={() => void applyAction('dismiss')}
                >
                  Dismiss review
                </button>
              </div>
            </div>
          )}

          <section className="audit-timeline" aria-label="Audit timeline">
            <h5>Append-only audit timeline</h5>
            <ol>
              {detail.auditEvents.map((event) => (
                <li key={event.id}>
                  <strong>{humanize(event.eventType)}</strong>
                  <span>
                    {event.actorDisplayName} · {formatTime(event.occurredAt)}
                    {typeof event.metadata.resolution === 'string'
                      ? ` · ${humanize(event.metadata.resolution)}`
                      : ''}
                  </span>
                </li>
              ))}
            </ol>
          </section>
        </article>
      )}
    </section>
  )
}

function FilterToggle({
  label,
  checked,
  onChange,
}: {
  label: string
  checked: boolean
  onChange: (checked: boolean) => void
}) {
  return (
    <label className="review-toggle">
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
      />
      {label}
    </label>
  )
}

function humanize(value: string) {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part[0]?.toUpperCase() + part.slice(1))
    .join(' ')
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}
