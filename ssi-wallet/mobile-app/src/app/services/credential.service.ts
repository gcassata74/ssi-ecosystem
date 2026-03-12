import { Injectable } from '@angular/core';

import { SecureStoragePlugin } from 'capacitor-secure-storage-plugin';

const STORAGE_KEY = 'wallet-verifiable-credentials';

@Injectable({ providedIn: 'root' })
export class CredentialService {
  private initialization?: Promise<unknown[]>;
  private cache: unknown[] = [];

  async listVerifiableCredentials(): Promise<unknown[]> {
    const stored = await this.ensureLoaded();
    return [...stored];
  }

  async addVerifiableCredential(credential: unknown): Promise<void> {
    if (credential === undefined) {
      return;
    }

    await this.addVerifiableCredentials([credential]);
  }

  async addVerifiableCredentials(credentials: unknown[]): Promise<void> {
    if (!credentials || credentials.length === 0) {
      return;
    }

    const stored = await this.ensureLoaded();
    stored.push(...credentials);
    await this.persist(stored);
  }

  async replaceVerifiableCredentials(credentials: unknown[]): Promise<void> {
    const normalized = Array.isArray(credentials) ? credentials.slice() : [];
    this.cache = normalized;
    await this.persist(this.cache);
  }

  private async ensureLoaded(): Promise<unknown[]> {
    if (!this.initialization) {
      this.initialization = this.loadFromStorage();
    }

    const loaded = await this.initialization;
    this.cache = loaded;
    return this.cache;
  }

  private async loadFromStorage(): Promise<unknown[]> {
    const stored = await this.tryReadStoredValue();
    if (!stored) {
      return [];
    }

    try {
      const parsed = JSON.parse(stored) as unknown;
      if (Array.isArray(parsed)) {
        return parsed;
      }

      console.warn('Stored credential list is not an array, resetting.');
      return [];
    } catch (error) {
      console.warn('Failed to parse stored credential list, resetting.', error);
      return [];
    }
  }

  private async persist(credentials: unknown[]): Promise<void> {
    this.cache = credentials.slice();
    const serialized = JSON.stringify(this.cache);
    const persisted = await this.tryPersistValue(serialized);
    if (!persisted) {
      console.warn('Falling back to in-memory credential storage.');
    }

    this.initialization = Promise.resolve(this.cache);
  }

  private async tryPersistValue(value: string): Promise<boolean> {
    try {
      await SecureStoragePlugin.set({ key: STORAGE_KEY, value });
      return true;
    } catch (error) {
      console.warn('Failed to persist credentials to secure storage.', error);
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

      console.warn('Failed to read credentials from secure storage.', error);
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

    if (typeof error === 'object' && error && 'message' in error) {
      const message = String((error as { message?: unknown }).message ?? '').toLowerCase();
      return message.includes('does not exist');
    }

    return false;
  }
}
