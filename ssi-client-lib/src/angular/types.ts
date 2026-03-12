import type { AuthClientDeps, SsiClientConfig, SsiInitOptions } from '../types';

export interface ProvideSsiAuthOptions {
  config: SsiClientConfig;
  initOptions?: SsiInitOptions;
  includeHttpInterceptor?: boolean;
  interceptorHeader?: string;
  clientDeps?: AuthClientDeps;
}
