/*
 * SSI Client Library
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

export type AuthStatus = 'unauthenticated' | 'authenticated' | 'refreshing';

export interface SsiEndpointsConfig {
  authorization?: string;
  token?: string;
  refresh?: string;
  userInfo?: string;
  endSession?: string;
}

export interface SsiClientConfig {
  baseUrl: string;
  clientId: string;
  redirectUri: string;
  postLogoutRedirectUri?: string;
  scopes?: string[];
  audience?: string;
  endpoints?: SsiEndpointsConfig;
  portalPath?: string;
  portalParams?: Record<string, string>;
  usePkce?: boolean;
  storageKey?: string;
  refreshTokens?: boolean;
  refreshSkewMs?: number;
}

export interface SsiInitOptions {
  onLoad?: 'login-required' | 'check-sso' | 'none';
  restoreOriginalUri?: boolean;
  currentUrl?: string;
}

export interface LoginOptions {
  redirectUri?: string;
  prompt?: 'login' | 'none';
  state?: string;
  extraParams?: Record<string, string>;
}

export interface VerifierPortalOptions {
  redirectUri?: string;
  portalPath?: string;
  prompt?: 'login' | 'none';
  state?: string;
  extraParams?: Record<string, string>;
}

export interface LogoutOptions {
  redirectUri?: string;
  federated?: boolean;
  customParams?: Record<string, string>;
}

export interface AuthTokens {
  accessToken: string;
  expiresAt: number;
  refreshToken?: string;
  idToken?: string;
  tokenType?: string;
  scope?: string;
}

export interface TokenResponse {
  access_token: string;
  expires_in: number;
  refresh_expires_in?: number;
  refresh_token?: string;
  id_token?: string;
  token_type?: string;
  scope?: string;
}

export type AuthEventType = 'authenticated' | 'token_refreshed' | 'token_expired' | 'logout' | 'error';

export type AuthEventPayload<T extends AuthEventType> =
  T extends 'authenticated'
    ? AuthTokens
    : T extends 'token_refreshed'
    ? AuthTokens
    : T extends 'token_expired'
    ? void
    : T extends 'logout'
    ? void
    : T extends 'error'
    ? { error: unknown }
    : never;

export interface AuthEventListener<T extends AuthEventType> {
  (payload: AuthEventPayload<T>): void;
}

export interface AuthEventEmitter {
  on<T extends AuthEventType>(event: T, listener: AuthEventListener<T>): () => void;
  off<T extends AuthEventType>(event: T, listener: AuthEventListener<T>): void;
  emit<T extends AuthEventType>(event: T, payload: AuthEventPayload<T>): void;
}

export interface StorageAdapter {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export interface AuthClientDeps {
  fetch?: typeof fetch;
  storage?: StorageAdapter;
  sessionStorage?: StorageAdapter;
  window?: Window;
  crypto?: Crypto;
  now?: () => number;
}
