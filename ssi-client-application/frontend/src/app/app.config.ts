import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideSsiAuth } from '@ssi/issuer-auth-client/angular';

const fallbackOrigin = 'http://localhost:4200';
const appOrigin = typeof window !== 'undefined' && window.location?.origin
  ? window.location.origin.replace(/\/$/, '')
  : fallbackOrigin;
const redirectUri = `${appOrigin}`;
const dynamicClientId = redirectUri;

export const issuerBaseUrl = 'https://izylife-issuer.eu.ngrok.io';
export const verifierBaseUrl = 'https://izylife-verifier.eu.ngrok.io';
export const keycloakRealm = 'izylife';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(withInterceptorsFromDi()),
    provideSsiAuth({
      config: {
        baseUrl: verifierBaseUrl,
        clientId: dynamicClientId,
        redirectUri,
        portalPath: '/verifier',
        portalParams: {
          client_id_scheme: 'redirect_uri'
        },
        scopes: ['openid', 'profile', 'email'],
        refreshSkewMs: 60_000
      },
      initOptions: {
        onLoad: 'check-sso',
        restoreOriginalUri: true
      },
      includeHttpInterceptor: true
    })
  ]
};
