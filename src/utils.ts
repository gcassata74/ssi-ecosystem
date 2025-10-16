import type { AuthTokens, StorageAdapter } from './types';

let memoryStore: Record<string, string> = {};

export const DEFAULT_STORAGE_KEY = 'ssi_auth_tokens';
export const DEFAULT_SESSION_KEY = 'ssi_auth_session';

export function createDefaultStorage(): StorageAdapter {
  if (typeof window !== 'undefined' && window.localStorage) {
    return window.localStorage;
  }
  return {
    getItem: (key: string) => memoryStore[key] ?? null,
    setItem: (key: string, value: string) => {
      memoryStore[key] = value;
    },
    removeItem: (key: string) => {
      delete memoryStore[key];
    }
  } satisfies StorageAdapter;
}

export function createDefaultSessionStorage(): StorageAdapter {
  if (typeof window !== 'undefined' && window.sessionStorage) {
    return window.sessionStorage;
  }
  return {
    getItem: (key: string) => memoryStore[key] ?? null,
    setItem: (key: string, value: string) => {
      memoryStore[key] = value;
    },
    removeItem: (key: string) => {
      delete memoryStore[key];
    }
  } satisfies StorageAdapter;
}

export interface PersistedTokens {
  tokens: AuthTokens;
  storedAt: number;
}

export function persistTokens(storage: StorageAdapter, key: string, tokens: AuthTokens, now: () => number): void {
  const payload: PersistedTokens = {
    tokens,
    storedAt: now()
  };
  storage.setItem(key, JSON.stringify(payload));
}

export function readTokens(storage: StorageAdapter, key: string): AuthTokens | null {
  const raw = storage.getItem(key);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as PersistedTokens;
    return parsed.tokens;
  } catch (error) {
    return null;
  }
}

export function dropTokens(storage: StorageAdapter, key: string): void {
  storage.removeItem(key);
}

export function buildUrl(baseUrl: string, path: string | undefined, searchParams: Record<string, string | undefined>): string {
  const url = new URL(path ?? '', baseUrl);
  const params = new URLSearchParams();
  Object.entries(searchParams).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      params.set(key, value);
    }
  });
  url.search = params.toString();
  return url.toString();
}

export function parseQueryFromUrl(url: string): URLSearchParams {
  try {
    const { searchParams } = new URL(url, 'resolve://');
    return new URLSearchParams(searchParams);
  } catch (error) {
    return new URLSearchParams();
  }
}

export async function createRandomString(length = 43, crypto?: Crypto): Promise<string> {
  const cryptoImpl = await resolveCrypto(crypto);
  const random = new Uint8Array(length);
  cryptoImpl.getRandomValues(random);
  return bufferToBase64Url(random.buffer).slice(0, length);
}

export async function createPkcePair(crypto?: Crypto): Promise<{ verifier: string; challenge: string }>
export async function createPkcePair(length: number, crypto?: Crypto): Promise<{ verifier: string; challenge: string }>
export async function createPkcePair(lengthOrCrypto?: number | Crypto, maybeCrypto?: Crypto): Promise<{ verifier: string; challenge: string }> {
  const length = typeof lengthOrCrypto === 'number' ? lengthOrCrypto : 96;
  const crypto = (typeof lengthOrCrypto === 'number' ? maybeCrypto : lengthOrCrypto) ?? maybeCrypto;
  const verifier = await createRandomString(length, crypto);
  const challenge = await pkceChallengeFromVerifier(verifier, crypto);
  return { verifier, challenge };
}

async function pkceChallengeFromVerifier(verifier: string, crypto?: Crypto): Promise<string> {
  const cryptoImpl = await resolveCrypto(crypto);
  const data = new TextEncoder().encode(verifier);
  const digest = await cryptoImpl.subtle.digest('SHA-256', data);
  return bufferToBase64Url(digest);
}

function bufferToBase64Url(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  const base64 = universalBtoa(bytesToBinary(bytes));
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function bytesToBinary(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i]);
  }
  return binary;
}

const BASE64_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';

function universalBtoa(binary: string): string {
  if (typeof btoa === 'function') {
    return btoa(binary);
  }
  let result = '';
  let i = 0;
  while (i < binary.length) {
    const c1 = binary.charCodeAt(i++);
    const c2 = binary.charCodeAt(i++);
    const c3 = binary.charCodeAt(i++);

    const e1 = c1 >> 2;
    const e2 = ((c1 & 3) << 4) | (c2 >> 4);
    const e3 = isNaN(c2) ? 64 : (((c2 & 15) << 2) | (c3 >> 6));
    const e4 = isNaN(c3) ? 64 : (c3 & 63);

    result +=
      BASE64_CHARS.charAt(e1) +
      BASE64_CHARS.charAt(e2) +
      BASE64_CHARS.charAt(e3) +
      BASE64_CHARS.charAt(e4);
  }
  return result;
}

async function resolveCrypto(crypto?: Crypto): Promise<Crypto> {
  if (crypto) return crypto;
  if (typeof globalThis !== 'undefined' && globalThis.crypto) {
    return globalThis.crypto as Crypto;
  }
  try {
    const { webcrypto } = await import('crypto');
    return webcrypto as unknown as Crypto;
  } catch (error) {
    throw new Error('No Web Crypto implementation is available; provide a Crypto polyfill through the client configuration.');
  }
}

export function decodeJwtPayload(token: string): Record<string, unknown> | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length < 2) return null;
  try {
    const payload = parts[1]
      .replace(/-/g, '+')
      .replace(/_/g, '/');
    const padded = payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '=');
    const binary = universalAtob(padded);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) {
      bytes[i] = binary.charCodeAt(i);
    }
    const decoder = new TextDecoder();
    return JSON.parse(decoder.decode(bytes));
  } catch (error) {
    return null;
  }
}

function universalAtob(base64: string): string {
  if (typeof atob === 'function') {
    return atob(base64);
  }
  let result = '';
  let buffer = 0;
  let bits = 0;
  for (let i = 0; i < base64.length; i += 1) {
    const c = base64.charAt(i);
    if (c === '=') break;
    const idx = BASE64_CHARS.indexOf(c);
    if (idx === -1) continue;
    buffer = (buffer << 6) | idx;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      result += String.fromCharCode((buffer >> bits) & 0xff);
    }
  }
  return result;
}

export function isTokenExpired(tokens: AuthTokens, skewMs = 30000, now: () => number = () => Date.now()): boolean {
  return now() + skewMs >= tokens.expiresAt;
}
