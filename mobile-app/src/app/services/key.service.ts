import { Injectable } from '@angular/core';

import { SecureStoragePlugin } from 'capacitor-secure-storage-plugin';

const STORAGE_KEY = 'device-key-pair';

export interface StoredKeyPair {
  publicKey: JsonWebKey;
  privateKey: JsonWebKey;
}

@Injectable({ providedIn: 'root' })
export class KeyService {
  private initialization?: Promise<StoredKeyPair>;
  private inMemoryFallback?: StoredKeyPair;

  async ensureKeyPair(): Promise<StoredKeyPair> {
    if (!this.initialization) {
      this.initialization = this.loadOrCreateKeyPair();
    }

    return this.initialization;
  }

  private async loadOrCreateKeyPair(): Promise<StoredKeyPair> {
    const existing = await this.readKeyPair();
    if (existing) {
      return existing;
    }

    const generated = await this.generateKeyPair();
    await this.saveKeyPair(generated);

    return generated;
  }

  private async readKeyPair(): Promise<StoredKeyPair | null> {
    const stored = await this.tryReadStoredValue();
    if (!stored) {
      return this.inMemoryFallback ?? null;
    }

    try {
      const parsed: unknown = JSON.parse(stored);
      if (this.isStoredKeyPair(parsed)) {
        this.inMemoryFallback = parsed;
        return parsed;
      }
    } catch (error) {
      console.warn('Stored key pair is not valid JSON, regenerating.', error);
    }

    return null;
  }

  private async saveKeyPair(keyPair: StoredKeyPair): Promise<void> {
    const serialized = JSON.stringify(keyPair);
    const persisted = await this.tryPersistValue(serialized);
    if (!persisted) {
      console.warn('Falling back to in-memory storage for device key pair.');
    }

    this.inMemoryFallback = keyPair;
  }

  private async tryPersistValue(value: string): Promise<boolean> {
    try {
      await SecureStoragePlugin.set({ key: STORAGE_KEY, value });
      return true;
    } catch (error) {
      console.warn('Failed to persist key pair to secure storage.', error);
      return false;
    }
  }

  private async tryReadStoredValue(): Promise<string | null> {
    try {
      const { value } = await SecureStoragePlugin.get({ key: STORAGE_KEY });
      return value;
    } catch (error) {
      if (this.isMissingKeyError(error)) {
        return null;
      }

      console.warn('Failed to read key pair from secure storage.', error);
      return null;
    }
  }

  private isMissingKeyError(error: unknown): boolean {
    if (!error) {
      return false;
    }

    if (typeof error === 'string') {
      return error.toLowerCase().includes('does not exist');
    }

    if (error instanceof Error) {
      return error.message.toLowerCase().includes('does not exist');
    }

    return false;
  }

  private async generateKeyPair(): Promise<StoredKeyPair> {
    const subtle = this.getSubtleOrThrow();
    const cryptoKeyPair = await subtle.generateKey(
      {
        name: 'ECDSA',
        namedCurve: 'P-256',
      },
      true,
      ['sign', 'verify'],
    );

    const publicKeyJwk = await subtle.exportKey('jwk', cryptoKeyPair.publicKey);
    const privateKeyJwk = await subtle.exportKey('jwk', cryptoKeyPair.privateKey);

    return {
      publicKey: publicKeyJwk,
      privateKey: privateKeyJwk,
    } satisfies StoredKeyPair;
  }

  private getSubtleOrThrow(): SubtleCrypto {
    const subtle = globalThis.crypto?.subtle;
    if (!subtle) {
      throw new Error('WebCrypto subtle API is not available on this platform.');
    }

    return subtle;
  }

  private isStoredKeyPair(value: unknown): value is StoredKeyPair {
    if (!value || typeof value !== 'object') {
      return false;
    }

    const maybeKeyPair = value as Partial<StoredKeyPair>;
    return Boolean(maybeKeyPair.publicKey && maybeKeyPair.privateKey);
  }
}
