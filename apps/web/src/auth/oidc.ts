import type { AuthProviderProps } from 'react-oidc-context'
import { WebStorageStateStore } from 'oidc-client-ts'

const authority =
  import.meta.env.VITE_OIDC_AUTHORITY ??
  'http://localhost:8082/realms/enterprise-ai'

export const oidcConfig: AuthProviderProps = {
  authority,
  client_id: 'enterprise-ai-web',
  redirect_uri: `${window.location.origin}/`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  response_type: 'code',
  scope: 'openid profile email',
  automaticSilentRenew: false,
  monitorSession: false,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname)
  },
  onSignoutCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname)
  },
}
