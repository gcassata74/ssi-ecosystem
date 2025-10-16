import { BehaviorSubject, Observable } from 'rxjs';
import type { SsiAuthClient } from '../SsiAuthClient';
import type { AuthStatus, AuthTokens, LoginOptions, LogoutOptions, VerifierPortalOptions } from '../types';
import type { ProvideSsiAuthOptions } from './types';

export class SsiAuthService {
  private readonly statusSubject: BehaviorSubject<AuthStatus>;
  private readonly tokensSubject: BehaviorSubject<AuthTokens | null>;

  constructor(private readonly client: SsiAuthClient, private readonly options: ProvideSsiAuthOptions) {
    this.statusSubject = new BehaviorSubject<AuthStatus>(this.client.authStatus);
    this.tokensSubject = new BehaviorSubject<AuthTokens | null>(this.client.getTokenSnapshot());
    this.client.on('authenticated', (tokens) => {
      this.statusSubject.next('authenticated');
      this.tokensSubject.next(tokens);
    });
    this.client.on('token_refreshed', (tokens) => {
      this.statusSubject.next('authenticated');
      this.tokensSubject.next(tokens);
    });
    this.client.on('logout', () => {
      this.statusSubject.next('unauthenticated');
      this.tokensSubject.next(null);
    });
    this.client.on('token_expired', () => {
      this.statusSubject.next('unauthenticated');
    });
  }

  get authStatus$(): Observable<AuthStatus> {
    return this.statusSubject.asObservable();
  }

  get tokens$(): Observable<AuthTokens | null> {
    return this.tokensSubject.asObservable();
  }

  async initialize(): Promise<boolean> {
    const result = await this.client.init(this.options.initOptions);
    this.statusSubject.next(this.client.authStatus);
    this.tokensSubject.next(this.client.getTokenSnapshot());
    return result;
  }

  login(options?: LoginOptions): Promise<string> {
    return this.client.login(options);
  }

  beginVerifierFlow(options?: VerifierPortalOptions): Promise<string> {
    return this.client.beginVerifierFlow(options);
  }

  logout(options?: LogoutOptions): Promise<void> {
    return this.client.logout(options);
  }

  getAccessToken(): Promise<string | null> {
    return this.client.getAccessToken();
  }

  getIdToken(): string | null {
    return this.client.getIdToken();
  }

  fetchWithAuth(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    return this.client.fetchWithAuth(input, init);
  }

  get clientInstance(): SsiAuthClient {
    return this.client;
  }
}
