import type {
  AuthClientDeps,
  AuthEventListener,
  AuthEventPayload,
  AuthEventType,
  AuthStatus,
  AuthTokens,
  LoginOptions,
  LogoutOptions,
  SsiClientConfig,
  SsiInitOptions,
  TokenResponse,
  StorageAdapter,
  VerifierPortalOptions
} from './types';
import {
  DEFAULT_SESSION_KEY,
  DEFAULT_STORAGE_KEY,
  buildUrl,
  createDefaultSessionStorage,
  createDefaultStorage,
  createPkcePair,
  createRandomString,
  dropTokens,
  isTokenExpired,
  parseQueryFromUrl,
  persistTokens,
  readTokens
} from './utils';

interface PkceSessionPayload {
  verifier: string;
  state: string;
  redirectUri: string;
  originalUri?: string;
  createdAt: number;
}

const DEFAULT_REFRESH_SKEW_MS = 60_000;

export class SsiAuthClient {
  private readonly config: SsiClientConfig;
  private readonly fetchFn: typeof fetch;
  private readonly storage: StorageAdapter;
  private readonly sessionStorage: StorageAdapter;
  private readonly storageKey: string;
  private readonly sessionKey: string;
  private readonly refreshSkewMs: number;
  private status: AuthStatus = 'unauthenticated';
  private tokens: AuthTokens | null = null;
  private listeners = new Map<AuthEventType, Set<AuthEventListener<AuthEventType>>>();
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(config: SsiClientConfig, private readonly deps: AuthClientDeps = {}) {
    this.config = {
      usePkce: true,
      scopes: ['openid', 'profile', 'email'],
      refreshTokens: true,
      ...config
    };
    const requestedSkew = this.config.refreshSkewMs;
    this.refreshSkewMs =
      typeof requestedSkew === 'number' && Number.isFinite(requestedSkew)
        ? Math.max(0, requestedSkew)
        : DEFAULT_REFRESH_SKEW_MS;
    if (!this.config.baseUrl) {
      throw new Error('Missing baseUrl in SsiClientConfig.');
    }
    if (!this.config.clientId) {
      throw new Error('Missing clientId in SsiClientConfig.');
    }
    if (!this.config.redirectUri) {
      throw new Error('Missing redirectUri in SsiClientConfig.');
    }

    const fetchImpl = deps.fetch ?? (typeof globalThis !== 'undefined' && 'fetch' in globalThis ? globalThis.fetch.bind(globalThis) : undefined);
    if (!fetchImpl) {
      throw new Error('No fetch implementation available. Provide one via the dependencies argument.');
    }
    this.fetchFn = fetchImpl;

    this.storage = this.deps.storage ?? createDefaultStorage();
    this.sessionStorage = this.deps.sessionStorage ?? createDefaultSessionStorage();
    this.storageKey = `${this.config.storageKey ?? DEFAULT_STORAGE_KEY}`;
    this.sessionKey = `${this.storageKey}:${DEFAULT_SESSION_KEY}`;
  }

  get authStatus(): AuthStatus {
    return this.status;
  }

  get hasRefreshToken(): boolean {
    return Boolean(this.tokens?.refreshToken);
  }

  async init(options: SsiInitOptions = {}): Promise<boolean> {
    const initMode = options.onLoad ?? 'check-sso';
    const currentUrl = options.currentUrl ?? this.currentUrl();

    if (currentUrl) {
      const handledRedirect = await this.handlePossibleRedirect(currentUrl, options.restoreOriginalUri ?? true);
      if (handledRedirect) {
        return this.isAuthenticated();
      }
    }

    if (!this.tokens) {
      const stored = readTokens(this.storage, this.storageKey);
      if (stored) {
        if (isTokenExpired(stored, 0, this.now)) {
          if (this.config.refreshTokens && stored.refreshToken) {
            try {
              await this.refreshToken(stored.refreshToken, true);
            } catch (error) {
              this.clearTokens();
              this.emit('error', { error });
            }
          } else {
            this.clearTokens();
          }
        } else {
          this.setTokens(stored);
        }
      }
    }

    if (initMode === 'login-required' && !this.isAuthenticated()) {
      await this.login();
      return false;
    }

    if (initMode === 'check-sso' && this.config.refreshTokens && this.tokens?.refreshToken && isTokenExpired(this.tokens, this.refreshSkewMs, this.now)) {
      try {
        await this.refreshToken(this.tokens.refreshToken, true);
      } catch (error) {
        this.emit('error', { error });
      }
    }

    return this.isAuthenticated();
  }

  async login(options: LoginOptions = {}): Promise<string> {
    const { state, redirectUri, pkce } = await this.prepareAuthorizationContext(options.redirectUri, options.state);

    const authorizeUrl = buildUrl(this.config.baseUrl, this.config.endpoints?.authorization ?? '/oauth2/authorize', {
      response_type: 'code',
      client_id: this.config.clientId,
      redirect_uri: redirectUri,
      scope: this.buildScope(),
      state,
      code_challenge: pkce?.challenge,
      code_challenge_method: pkce ? 'S256' : undefined,
      prompt: options.prompt,
      audience: this.config.audience,
      ...options.extraParams
    });
    return this.navigate(authorizeUrl);
  }

  async beginVerifierFlow(options: VerifierPortalOptions = {}): Promise<string> {
    const { state, redirectUri, pkce } = await this.prepareAuthorizationContext(options.redirectUri, options.state);
    const portalPath = options.portalPath ?? this.config.portalPath ?? '/';
    const params = {
      response_type: 'code',
      client_id: this.config.clientId,
      redirect_uri: redirectUri,
      scope: this.buildScope(),
      state,
      code_challenge: pkce?.challenge,
      code_challenge_method: pkce ? 'S256' : undefined,
      prompt: options.prompt,
      ...this.config.portalParams,
      ...options.extraParams
    };

    const portalUrl = buildUrl(this.config.baseUrl, portalPath, params);
    return this.navigate(portalUrl);
  }

  async logout(options: LogoutOptions = {}): Promise<void> {
    const redirectUri = options.redirectUri ?? this.config.postLogoutRedirectUri ?? this.config.redirectUri;
    const endSessionPath = this.config.endpoints?.endSession ?? '/oauth2/logout';
    const idToken = this.tokens?.idToken;

    this.clearTokens();
    this.emit('logout', undefined);

    if (!endSessionPath) {
      return;
    }

    const url = buildUrl(this.config.baseUrl, endSessionPath, {
      post_logout_redirect_uri: redirectUri,
      id_token_hint: idToken,
      client_id: this.config.clientId,
      federated: options.federated ? 'true' : undefined,
      ...options.customParams
    });

    if (this.deps.window?.location) {
      this.deps.window.location.assign(url);
    }
  }

  isAuthenticated(): boolean {
    return Boolean(this.tokens && !isTokenExpired(this.tokens, 0, this.now));
  }

  async getAccessToken(): Promise<string | null> {
    if (!this.tokens) return null;
    if (!isTokenExpired(this.tokens, this.refreshSkewMs, this.now)) {
      return this.tokens.accessToken;
    }
    if (this.config.refreshTokens && this.tokens.refreshToken) {
      try {
        await this.refreshToken(this.tokens.refreshToken, false);
        return this.tokens?.accessToken ?? null;
      } catch (error) {
        this.emit('error', { error });
        return null;
      }
    }
    return null;
  }

  getIdToken(): string | null {
    return this.tokens?.idToken ?? null;
  }

  getTokenSnapshot(): AuthTokens | null {
    return this.tokens ? { ...this.tokens } : null;
  }

  on<T extends AuthEventType>(event: T, listener: AuthEventListener<T>): () => void {
    const listeners = this.listeners.get(event) ?? new Set();
    listeners.add(listener as AuthEventListener<AuthEventType>);
    this.listeners.set(event, listeners);
    return () => this.off(event, listener);
  }

  off<T extends AuthEventType>(event: T, listener: AuthEventListener<T>): void {
    const listeners = this.listeners.get(event);
    if (!listeners) return;
    listeners.delete(listener as AuthEventListener<AuthEventType>);
  }

  emit<T extends AuthEventType>(event: T, payload: AuthEventPayload<T>): void {
    const listeners = this.listeners.get(event);
    if (!listeners) return;
    listeners.forEach((listener) => {
      try {
        (listener as AuthEventListener<T>)(payload);
      } catch (error) {
        // Individual listener failures should not impact the publisher.
      }
    });
  }

  async fetchWithAuth(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
    const token = await this.getAccessToken();
    const headers = new Headers(init.headers ?? {});
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    return this.fetchFn(input, { ...init, headers });
  }

  async loadUserInfo<T extends Record<string, unknown> = Record<string, unknown>>(): Promise<T | null> {
    const endpoint = this.config.endpoints?.userInfo ?? '/oauth2/userinfo';
    const token = await this.getAccessToken();
    if (!token) return null;
    const response = await this.fetchFn(new URL(endpoint, this.config.baseUrl), {
      headers: {
        Authorization: `Bearer ${token}`
      }
    });
    if (!response.ok) {
      throw new Error(`UserInfo request failed with status ${response.status}`);
    }
    return (await response.json()) as T;
  }

  private async refreshToken(refreshToken: string, silent: boolean): Promise<void> {
    if (!this.config.refreshTokens) {
      throw new Error('Refresh token flow disabled.');
    }
    this.status = 'refreshing';
    const payload = await this.requestToken({
      grant_type: 'refresh_token',
      refresh_token: refreshToken,
      client_id: this.config.clientId
    });
    const tokens = this.toAuthTokens(payload);
    this.setTokens(tokens);
    this.emit(silent ? 'token_refreshed' : 'authenticated', tokens);
  }

  private async handlePossibleRedirect(url: string, restoreOriginalUri: boolean): Promise<boolean> {
    const query = parseQueryFromUrl(url);
    const code = query.get('code');
    const state = query.get('state');
    const error = query.get('error');

    if (error) {
      this.emit('error', { error: new Error(error) });
      this.clearSession();
      this.stripOAuthParams();
      return false;
    }

    if (!code) return false;

    const session = this.readSession();
    if (!session || state !== session.state) {
      this.emit('error', { error: new Error('Invalid authentication state received. Please retry the login flow.') });
      this.clearSession();
      this.stripOAuthParams();
      return false;
    }

    try {
      const payload = await this.requestToken({
        grant_type: 'authorization_code',
        code,
        client_id: this.config.clientId,
        redirect_uri: session.redirectUri,
        code_verifier: session.verifier || undefined
      });
      const tokens = this.toAuthTokens(payload);
      this.setTokens(tokens);
      this.emit('authenticated', tokens);
      if (restoreOriginalUri && session.originalUri && this.deps.window?.history?.replaceState) {
        const title = this.deps.window.document?.title ?? '';
        this.deps.window.history.replaceState({}, title, session.originalUri);
      } else {
        this.stripOAuthParams();
      }
      return true;
    } catch (error) {
      this.emit('error', { error });
      return false;
    } finally {
      this.clearSession();
    }
  }

  private requestInitBody(params: Record<string, string | undefined>): URLSearchParams {
    const body = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        body.set(key, value);
      }
    });
    return body;
  }

  private async requestToken(params: Record<string, string | undefined>): Promise<TokenResponse> {
    const endpoint = this.config.endpoints?.token ?? '/oauth2/token';
    const url = new URL(endpoint, this.config.baseUrl);
    const response = await this.fetchFn(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: this.requestInitBody(params)
    });

    if (!response.ok) {
      const message = `Token endpoint returned ${response.status}`;
      throw new Error(message);
    }

    return (await response.json()) as TokenResponse;
  }

  private toAuthTokens(response: TokenResponse): AuthTokens {
    const now = this.now();
    const expiresIn = response.expires_in ?? 0;
    return {
      accessToken: response.access_token,
      expiresAt: now + expiresIn * 1000,
      refreshToken: response.refresh_token,
      idToken: response.id_token,
      tokenType: response.token_type,
      scope: response.scope
    };
  }

  private setTokens(tokens: AuthTokens | null): void {
    if (tokens) {
      this.tokens = tokens;
      this.status = 'authenticated';
      persistTokens(this.storage, this.storageKey, tokens, this.now);
      this.scheduleRefresh();
    } else {
      this.clearTokens();
    }
  }

  private clearTokens(): void {
    this.tokens = null;
    this.status = 'unauthenticated';
    dropTokens(this.storage, this.storageKey);
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
  }

  private scheduleRefresh(): void {
    if (!this.config.refreshTokens || !this.tokens?.refreshToken) {
      return;
    }
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
    }
    const now = this.now();
    const delay = Math.max(this.tokens.expiresAt - now - this.refreshSkewMs, 5_000);
    this.refreshTimer = setTimeout(() => {
      if (!this.tokens?.refreshToken) return;
      this.refreshToken(this.tokens.refreshToken, true).catch((error) => {
        this.emit('error', { error });
        this.emit('token_expired', undefined);
      });
    }, delay);
  }

  private readSession(): PkceSessionPayload | null {
    const raw = this.sessionStorage.getItem(this.sessionKey);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as PkceSessionPayload;
    } catch (error) {
      return null;
    }
  }

  private clearSession(): void {
    this.sessionStorage.removeItem(this.sessionKey);
  }

  private stripOAuthParams(): void {
    if (!this.deps.window?.location || !this.deps.window.history?.replaceState) return;
    const url = new URL(this.deps.window.location.href);
    url.searchParams.delete('code');
    url.searchParams.delete('state');
    url.searchParams.delete('session_state');
    url.search = url.searchParams.toString();
    const title = this.deps.window.document?.title ?? '';
    this.deps.window.history.replaceState({}, title, url.toString());
  }

  private buildScope(): string {
    return this.config.scopes?.join(' ') ?? 'openid';
  }

  private now = (): number => {
    return this.deps.now ? this.deps.now() : Date.now();
  };

  private currentUrl(): string | null {
    if (this.deps.window?.location?.href) {
      return this.deps.window.location.href;
    }
    return null;
  }

  private async prepareAuthorizationContext(redirect?: string, requestedState?: string): Promise<{
    state: string;
    redirectUri: string;
    pkce?: { verifier: string; challenge: string };
  }> {
    const state = requestedState ?? (await createRandomString(16, this.deps.crypto));
    const redirectUri = redirect ?? this.config.redirectUri;
    const originalUri = this.currentUrl() ?? undefined;

    let pkce: { verifier: string; challenge: string } | undefined;
    if (this.config.usePkce) {
      pkce = await createPkcePair(this.deps.crypto);
    }

    const sessionPayload: PkceSessionPayload = {
      verifier: pkce?.verifier ?? '',
      state,
      redirectUri,
      originalUri,
      createdAt: Date.now()
    };

    this.sessionStorage.setItem(this.sessionKey, JSON.stringify(sessionPayload));

    return { state, redirectUri, pkce };
  }

  private navigate(url: string): string {
    if (this.deps.window?.location) {
      this.deps.window.location.assign(url);
    }
    return url;
  }
}
