import { Injectable, inject } from '@angular/core';

import { SecureStoragePlugin } from 'capacitor-secure-storage-plugin';

import { KeyService } from './key.service';

const STORAGE_KEY = 'wallet-did-registration';

const BASE58_ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';

type DidKeyMaterial = {
  did: string;
  verificationMethodId: string;
};

export interface StoredDid {
  id: string;
  document: DidDocument;
  encodedDocument: string;
}

export interface DidDocument {
  '@context': readonly string[];
  id: string;
  verificationMethod: readonly VerificationMethod[];
  authentication: readonly string[];
}

export interface VerificationMethod {
  id: string;
  type: string;
  controller: string;
  publicKeyJwk: JsonWebKey;
}

@Injectable({ providedIn: 'root' })
export class DidService {
  private readonly keyService = inject(KeyService);

  private initialization?: Promise<StoredDid>;
  private inMemoryFallback?: StoredDid;

  async ensureDid(): Promise<StoredDid> {
    if (!this.initialization) {
      this.initialization = this.loadOrCreateDid();
    }

    return this.initialization;
  }

  private async loadOrCreateDid(): Promise<StoredDid> {
    const stored = await this.readDid();
    if (stored) {
      return stored;
    }

    const keyPair = await this.keyService.ensureKeyPair();
    const registered = this.createDidKeyDocument(keyPair.publicKey);
    await this.saveDid(registered);

    return registered;
  }

  async getEncodedDidDocument(): Promise<string> {
    const stored = await this.ensureDid();
    return stored.encodedDocument;
  }

  private createDidKeyDocument(publicKey: JsonWebKey): StoredDid {
    const { did, verificationMethodId } = this.deriveDidKeyMaterial(publicKey);
    const document = this.buildDidDocument(did, verificationMethodId, publicKey);
    const encodedDocument = this.base58Encode(new TextEncoder().encode(JSON.stringify(document)));

    return { id: did, document, encodedDocument } satisfies StoredDid;
  }

  private buildDidDocument(
    did: string,
    verificationMethodId: string,
    publicKey: JsonWebKey,
  ): DidDocument {
    return {
      '@context': [
        'https://www.w3.org/ns/did/v1',
        'https://w3id.org/security/suites/jws-2020/v1',
      ],
      id: did,
      verificationMethod: [
        {
          id: verificationMethodId,
          type: 'JsonWebKey2020',
          controller: did,
          publicKeyJwk: publicKey,
        },
      ],
      authentication: [verificationMethodId],
    } satisfies DidDocument;
  }

  private async readDid(): Promise<StoredDid | null> {
    const stored = await this.tryReadStoredValue();
    if (!stored) {
      return this.inMemoryFallback ?? null;
    }

    try {
      const parsed: unknown = JSON.parse(stored);
      if (this.isStoredDid(parsed)) {
        this.inMemoryFallback = parsed;
        return parsed;
      }
    } catch (error) {
      console.warn('Stored DID information is not valid JSON, regenerating.', error);
    }

    return null;
  }

  private async saveDid(did: StoredDid): Promise<void> {
    const serialized = JSON.stringify(did);
    const persisted = await this.tryPersistValue(serialized);
    if (!persisted) {
      console.warn('Falling back to in-memory storage for DID information.');
    }

    this.inMemoryFallback = did;
  }

  private async tryPersistValue(value: string): Promise<boolean> {
    try {
      await SecureStoragePlugin.set({ key: STORAGE_KEY, value });
      return true;
    } catch (error) {
      console.warn('Failed to persist DID information to secure storage.', error);
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

      console.warn('Failed to read DID information from secure storage.', error);
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

  private isStoredDid(value: unknown): value is StoredDid {
    if (!value || typeof value !== 'object') {
      return false;
    }

    const maybeDid = value as Partial<StoredDid>;
    return (
      typeof maybeDid.id === 'string' &&
      typeof maybeDid.encodedDocument === 'string' &&
      this.isDidDocument(maybeDid.document)
    );
  }

  private isDidDocument(value: unknown): value is DidDocument {
    if (!value || typeof value !== 'object') {
      return false;
    }

    const maybeDoc = value as Partial<DidDocument>;
    if (!Array.isArray((maybeDoc as DidDocument)['@context'])) {
      return false;
    }

    if (typeof maybeDoc.id !== 'string') {
      return false;
    }

    if (!Array.isArray(maybeDoc.verificationMethod) || maybeDoc.verificationMethod.length === 0) {
      return false;
    }

    const [method] = maybeDoc.verificationMethod;
    return Boolean(
      method &&
        typeof method.id === 'string' &&
        typeof method.type === 'string' &&
        typeof method.controller === 'string' &&
        method.publicKeyJwk,
    );
  }

  private deriveDidKeyMaterial(publicKey: JsonWebKey): DidKeyMaterial {
    if (!publicKey || publicKey.kty !== 'EC' || publicKey.crv !== 'P-256') {
      throw new Error('Only P-256 EC keys are supported for did:key identifiers.');
    }

    if (!publicKey.x || !publicKey.y) {
      throw new Error('Public key is missing required coordinates for did:key derivation.');
    }

    const x = this.base64UrlToUint8Array(publicKey.x);
    const y = this.base64UrlToUint8Array(publicKey.y);

    if (x.length !== 32 || y.length !== 32) {
      throw new Error('Unexpected coordinate length for P-256 public key.');
    }

    const compressed = new Uint8Array(33);
    compressed[0] = this.isOdd(y) ? 0x03 : 0x02;
    compressed.set(x, 1);

    const multicodec = new Uint8Array(compressed.length + 2);
    multicodec[0] = 0x12;
    multicodec[1] = 0x00;
    multicodec.set(compressed, 2);

    const identifier = `did:key:z${this.base58Encode(multicodec)}`;
    return {
      did: identifier,
      verificationMethodId: `${identifier}#key-1`,
    } satisfies DidKeyMaterial;
  }

  private base64UrlToUint8Array(value: string): Uint8Array {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    const padding = normalized.length % 4 === 0 ? '' : '='.repeat(4 - (normalized.length % 4));
    const base64 = normalized + padding;

    if (typeof globalThis.atob !== 'function') {
      throw new Error('Base64 decoding is not supported in this runtime.');
    }

    const binary = globalThis.atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i += 1) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }

  private isOdd(bytes: Uint8Array): boolean {
    if (bytes.length === 0) {
      return false;
    }

    return (bytes[bytes.length - 1] & 1) === 1;
  }

  private base58Encode(data: Uint8Array): string {
    if (data.length === 0) {
      return '';
    }

    let value = 0n;
    for (const byte of data) {
      value = (value << 8n) + BigInt(byte);
    }

    let result = '';
    while (value > 0n) {
      const remainder = Number(value % 58n);
      result = BASE58_ALPHABET[remainder] + result;
      value /= 58n;
    }

    let leadingZeros = 0;
    while (leadingZeros < data.length && data[leadingZeros] === 0) {
      leadingZeros += 1;
    }

    if (!result) {
      result = BASE58_ALPHABET[0];
    }

    return BASE58_ALPHABET[0].repeat(leadingZeros) + result;
  }
}
