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
  production: false,
  keycloakUrl: 'http://localhost:8180',
  keycloakClientId: 'ssi-issuer-spa',
  spid: {
    entityId: verifierEndpoint ? `${verifierEndpoint}/spid` : undefined,
    authBaseUrl: 'https://demo.spid.gov.it/samlsso',
    providerSlug: 'validator'
  }
};
