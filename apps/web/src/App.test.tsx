import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('renders the foundation landing page and future modules', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise(() => undefined)),
    )

    render(<App />)

    expect(
      screen.getByRole('heading', {
        name: 'Enterprise AI Knowledge & Operations Platform',
      }),
    ).toBeInTheDocument()
    expect(screen.getByText('Milestone · Foundation')).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Knowledge' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Conversations' }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { name: 'Workflows' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Audit' })).toBeInTheDocument()
  })

  it('shows a loading state while checking backend connectivity', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise(() => undefined)),
    )

    render(<App />)

    expect(screen.getByText('Checking backend…')).toBeInTheDocument()
    expect(screen.getByText('Service version unavailable')).toBeInTheDocument()
  })

  it('shows the real service details when the backend is online', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            status: 'UP',
            service: 'enterprise-ai-api',
            version: '0.1.0-SNAPSHOT',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    )

    render(<App />)

    expect(await screen.findByText('Backend online')).toBeInTheDocument()
    expect(
      screen.getByText('enterprise-ai-api · 0.1.0-SNAPSHOT'),
    ).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/system/status',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    )
  })

  it('shows a clear state when the backend is unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))

    render(<App />)

    expect(await screen.findByText('Backend unavailable')).toBeInTheDocument()
    expect(screen.getByText('Service version unavailable')).toBeInTheDocument()
  })
})
