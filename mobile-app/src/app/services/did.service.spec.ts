import { TestBed } from '@angular/core/testing';

import { SecureStoragePlugin } from 'capacitor-secure-storage-plugin';

import { DidService, StoredDid } from './did.service';
import { KeyService, StoredKeyPair } from './key.service';

describe('DidService', () => {
  let service: DidService;
  let keyServiceStub: jasmine.SpyObj<KeyService>;
  const storage: Record<string, string> = {};

  const mockStoredKeyPair: StoredKeyPair = {
    publicKey: {
      kty: 'EC',
      crv: 'P-256',
      x: 'f83OJ3D2xF4Q-FP4o539iY7Qf2IkyRj939FkFyjX8Ck',
      y: 'x_FEzRu9r1b3SN7hVDCUNCawE1Y4YBbs6k8ZZr68SuE',
    },
    privateKey: {},
  } as StoredKeyPair;

  beforeEach(() => {
    // Reset the in-memory secure storage between tests.
    for (const key of Object.keys(storage)) {
      delete storage[key];
    }

    spyOn(SecureStoragePlugin, 'get').and.callFake(async ({ key }) => {
      const value = storage[key];
      if (value === undefined) {
        throw { message: 'Key does not exist' };
      }

      return { value };
    });

    spyOn(SecureStoragePlugin, 'set').and.callFake(async ({ key, value }) => {
      storage[key] = value;
    });

    keyServiceStub = jasmine.createSpyObj<KeyService>('KeyService', ['ensureKeyPair']);
    keyServiceStub.ensureKeyPair.and.callFake(async () => mockStoredKeyPair);

    TestBed.configureTestingModule({
      providers: [{ provide: KeyService, useValue: keyServiceStub }, DidService],
    });

    service = TestBed.inject(DidService);
  });

  it('creates and stores a did:key document with a base58 encoded copy', async () => {
    const result = await service.ensureDid();

    expect(result.id).toMatch(/^did:key:z[1-9A-HJ-NP-Za-km-z]+$/);
    expect(result.document.id).toBe(result.id);
    expect(result.encodedDocument.length).toBeGreaterThan(0);

    const utils = service as unknown as {
      base58Encode(value: Uint8Array): string;
    };
    const encoder = new TextEncoder();
    const expected = utils.base58Encode(encoder.encode(JSON.stringify(result.document)));
    expect(result.encodedDocument).toBe(expected);

    const storedRaw = storage['wallet-did-registration'];
    expect(storedRaw).toBeTruthy();

    const parsed: StoredDid = JSON.parse(storedRaw!);
    expect(parsed.id).toBe(result.id);
    expect(parsed.encodedDocument).toBe(result.encodedDocument);
  });

  it('reuses the stored DID document without regenerating keys', async () => {
    const first = await service.ensureDid();
    const second = await service.ensureDid();

    expect(second).toEqual(first);
    expect(keyServiceStub.ensureKeyPair).toHaveBeenCalledTimes(1);
  });

  it('provides direct access to the base58 encoded DID document', async () => {
    const stored = await service.ensureDid();
    const encoded = await service.getEncodedDidDocument();

    expect(encoded).toBe(stored.encodedDocument);
  });
});
