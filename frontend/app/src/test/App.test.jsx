import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import App from '../App'

describe('App', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('shows loading state initially', () => {
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise(() => {}))
    render(<App />)
    expect(screen.getByText(/loading\.\.\./i)).toBeInTheDocument()
  })

  it('displays version from backend', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ version: '1.2.3', bg_color: 'green' }),
    })
    render(<App />)
    await waitFor(() =>
      expect(screen.getByText(/gitops demo app - 1\.2\.3/i)).toBeInTheDocument()
    )
  })

  it('applies bg_color from backend to the page background', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ version: '1.2.3', bg_color: 'blue' }),
    })
    const { container } = render(<App />)
    await waitFor(() =>
      expect(screen.getByText(/gitops demo app - 1\.2\.3/i)).toBeInTheDocument()
    )
    expect(container.querySelector('.page').style.backgroundColor).toBe('blue')
  })

  it('shows unavailable when fetch fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('network error'))
    render(<App />)
    await waitFor(() =>
      expect(screen.getByText(/gitops demo app - unavailable/i)).toBeInTheDocument()
    )
  })

  it('shows unavailable when response is not ok', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: false, status: 503 })
    render(<App />)
    await waitFor(() =>
      expect(screen.getByText(/gitops demo app - unavailable/i)).toBeInTheDocument()
    )
  })
})
