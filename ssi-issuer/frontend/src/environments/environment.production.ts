function resolveVerifierEndpoint(): string | undefined {
  const globalCandidate = (globalThis as { APP_VERIFIER_ENDPOINT?: unknown }).APP_VERIFIER_ENDPOINT;
  if (typeof globalCandidate === 'string' && globalCandidate.trim().length > 0) {
    return globalCandidate.trim().replace(/\/$/, '');
  }

  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin.replace(/\/$/, '');
  }

  return undefined;
}

const verifierEndpoint = resolveVerifierEndpoint();

export const environment = {
  production: true,
  keycloakUrl: (globalThis as { APP_KEYCLOAK_URL?: string }).APP_KEYCLOAK_URL ?? 'http://localhost:8180',
  keycloakClientId: (globalThis as { APP_KEYCLOAK_CLIENT_ID?: string }).APP_KEYCLOAK_CLIENT_ID ?? 'ssi-issuer-spa',
  spid: {
    entityId: verifierEndpoint ? `${verifierEndpoint}/spid` : undefined,
    authBaseUrl: 'https://spid.demo.gov.it/auth/login',
    providerSlug: 'validator'
  }
};
