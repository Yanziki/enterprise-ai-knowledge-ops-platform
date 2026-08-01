export interface SystemStatus {
  status: 'UP'
  service: string
  version: string
}

export async function getSystemStatus(
  signal?: AbortSignal,
): Promise<SystemStatus> {
  const response = await fetch('/api/v1/system/status', {
    headers: { Accept: 'application/json' },
    signal,
  })

  if (!response.ok) {
    throw new Error(
      `Backend status request failed with HTTP ${response.status}`,
    )
  }

  return (await response.json()) as SystemStatus
}
