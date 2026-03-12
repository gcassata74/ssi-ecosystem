import { APP_INITIALIZER, EnvironmentProviders, Provider, makeEnvironmentProviders } from '@angular/core';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { SsiAuthClient } from '../SsiAuthClient';
import type { AuthClientDeps } from '../types';
import { SsiAuthInterceptor } from './interceptor';
import { SsiAuthService } from './service';
import { SSI_AUTH_CLIENT, SSI_AUTH_OPTIONS } from './tokens';
import type { ProvideSsiAuthOptions } from './types';

export { SsiAuthService } from './service';
export { SsiAuthInterceptor } from './interceptor';
export type { ProvideSsiAuthOptions } from './types';

export function provideSsiAuth(options: ProvideSsiAuthOptions): EnvironmentProviders {
  const providers: Provider[] = [
    { provide: SSI_AUTH_OPTIONS, useValue: options },
    {
      provide: SSI_AUTH_CLIENT,
      useFactory: () => new SsiAuthClient(options.config, mergeClientDeps(options.clientDeps))
    },
    {
      provide: SsiAuthService,
      useFactory: (client: SsiAuthClient, opts: ProvideSsiAuthOptions) => new SsiAuthService(client, opts),
      deps: [SSI_AUTH_CLIENT, SSI_AUTH_OPTIONS]
    },
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: (service: SsiAuthService) => () => service.initialize(),
      deps: [SsiAuthService]
    }
  ];

  if (options.includeHttpInterceptor) {
    providers.push({
      provide: HTTP_INTERCEPTORS,
      useFactory: (service: SsiAuthService, opts: ProvideSsiAuthOptions) => new SsiAuthInterceptor(service, opts),
      deps: [SsiAuthService, SSI_AUTH_OPTIONS],
      multi: true
    });
  }

  return makeEnvironmentProviders(providers);
}

function mergeClientDeps(customDeps?: AuthClientDeps): AuthClientDeps {
  const windowRef = typeof window !== 'undefined' ? window : undefined;
  const fetchRef = typeof fetch !== 'undefined' ? fetch.bind(globalThis) : undefined;
  return {
    window: customDeps?.window ?? windowRef,
    fetch: customDeps?.fetch ?? fetchRef,
    storage: customDeps?.storage,
    sessionStorage: customDeps?.sessionStorage,
    crypto: customDeps?.crypto,
    now: customDeps?.now
  };
}
