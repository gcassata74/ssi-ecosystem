import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Capacitor, CapacitorHttp, HttpResponse } from '@capacitor/core';

import { CredentialService } from './credential.service';
import { DidService, StoredDid } from './did.service';
import { KeyService, StoredKeyPair } from './key.service';
import { Oidc4vpService } from './oidc4vp.service';

const mockDid: StoredDid = {
  id: 'did:key:z6Mkh9d1eJ1xJ4Z3gkHg8zM1EXAMPLE',
  encodedDocument: '3KMfExampleBase58Doc',
  document: {
    '@context': ['https://www.w3.org/ns/did/v1'],
    id: 'did:key:z6Mkh9d1eJ1xJ4Z3gkHg8zM1EXAMPLE',
    authentication: ['did:key:z6Mkh9d1eJ1xJ4Z3gkHg8zM1EXAMPLE#key-1'],
    verificationMethod: [
      {
        id: 'did:key:z6Mkh9d1eJ1xJ4Z3gkHg8zM1EXAMPLE#key-1',
        type: 'JsonWebKey2020',
        controller: 'did:key:z6Mkh9d1eJ1xJ4Z3gkHg8zM1EXAMPLE',
        publicKeyJwk: {
          kty: 'EC',
          crv: 'P-256',
          x: 'f83OJ3D2xF4Q-FP4o539iY7Qf2IkyRj939FkFyjX8Ck',
          y: 'x_FEzRu9r1b3SN7hVDCUNCawE1Y4YBbs6k8ZZr68SuE',
        },
      },
    ],
  },
};

const mockKeyPair: StoredKeyPair = {
  publicKey: {
    kty: 'EC',
    crv: 'P-256',
    x: 'f83OJ3D2xF4Q-FP4o539iY7Qf2IkyRj939FkFyjX8Ck',
    y: 'x_FEzRu9r1b3SN7hVDCUNCawE1Y4YBbs6k8ZZr68SuE',
  },
  privateKey: {
    kty: 'EC',
    crv: 'P-256',
    x: 'f83OJ3D2xF4Q-FP4o539iY7Qf2IkyRj939FkFyjX8Ck',
    y: 'x_FEzRu9r1b3SN7hVDCUNCawE1Y4YBbs6k8ZZr68SuE',
    d: 'N18h7KcQ6Y31w0z8qWGP0w_FYlEYwH14r2raXIiQw_c',
  },
};

describe('Oidc4vpService', () => {
  let service: Oidc4vpService;
  let httpMock: HttpTestingController;
  let isNativePlatformSpy: jasmine.Spy<() => boolean>;
  let restoreCrypto: (() => void) | undefined;
  const ensureDidSpy = jasmine.createSpy('ensureDid').and.resolveTo(mockDid);
  const defaultCredentials = [{ id: 'urn:vc:1' }];
  const listVerifiableCredentialsSpy = jasmine
    .createSpy('listVerifiableCredentials')
    .and.resolveTo(defaultCredentials);
  const ensureKeyPairSpy = jasmine.createSpy('ensureKeyPair').and.resolveTo(mockKeyPair);
  const didServiceStub = { ensureDid: ensureDidSpy } satisfies Partial<DidService>;
  const credentialServiceStub = {
    listVerifiableCredentials: listVerifiableCredentialsSpy,
  } satisfies Partial<CredentialService>;
  const keyServiceStub = { ensureKeyPair: ensureKeyPairSpy } satisfies Partial<KeyService>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        Oidc4vpService,
        { provide: DidService, useValue: didServiceStub },
        { provide: CredentialService, useValue: credentialServiceStub },
        { provide: KeyService, useValue: keyServiceStub },
      ],
    });

    isNativePlatformSpy = spyOn(Capacitor, 'isNativePlatform').and.returnValue(false);
    const cryptoStubs = stubCrypto();
    restoreCrypto = cryptoStubs.restore;
    service = TestBed.inject(Oidc4vpService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    ensureDidSpy.calls.reset();
    listVerifiableCredentialsSpy.calls.reset();
    listVerifiableCredentialsSpy.and.resolveTo(defaultCredentials);
    ensureKeyPairSpy.calls.reset();
    isNativePlatformSpy.and.returnValue(false);
    isNativePlatformSpy.calls.reset();
    restoreCrypto?.();
    restoreCrypto = undefined;
  });

  it('detects supported OIDC4VP URIs', () => {
    expect(service.isOidc4vpUri('openid4vp://?response_type=vp_token')).toBeTrue();
    expect(service.isOidc4vpUri('openid://?request_uri=https://example.com')).toBeTrue();
    expect(service.isOidc4vpUri('https://example.com')).toBeFalse();
  });

  it('fetches request object and submits a VP response', async () => {
    const responseUri = 'https://portal.example/response';
    const requestUri = 'https://portal.example/request';
    const requestPromise = service.submitPresentationFromUri(
      `openid4vp://?request_uri=${encodeURIComponent(requestUri)}`,
    );

    const getRequest = httpMock.expectOne(requestUri);
    expect(getRequest.request.method).toBe('GET');
    getRequest.flush({
      response_uri: responseUri,
      client_id: 'portal-client',
      client_id_scheme: 'redirect_uri',
      response_mode: 'direct_post',
      state: 'xyz',
      nonce: '123',
      presentation_definition: {
        id: 'presentation-def',
        input_descriptors: [{ id: 'descriptor-1' }],
      },
    });

    const postRequest = httpMock.expectOne(responseUri);
    expect(postRequest.request.method).toBe('POST');
    expect(postRequest.request.headers.get('Content-Type')).toBe(
      'application/x-www-form-urlencoded',
    );

    const formBody = postRequest.request.body as string;
    const params = new URLSearchParams(formBody);

    const vpTokenRaw = params.get('vp_token');
    expect(vpTokenRaw).toBeTruthy();
    const decoded = decodeJwt(vpTokenRaw!);
    expect(decoded.header['typ']).toBe('JWT');
    expect(decoded.header['kid']).toBe(mockDid.document.verificationMethod[0].id);
    expect(decoded.payload['iss']).toBe(mockDid.id);
    expect(decoded.payload['aud']).toBe('portal-client');
    expect(decoded.payload['nonce']).toBe('123');
    expect(typeof decoded.payload['iat']).toBe('number');
    expect(typeof decoded.payload['exp']).toBe('number');
    const vp = decoded.payload['vp'] as Record<string, unknown>;
    expect(vp['holder']).toBe(mockDid.id);
    const credentials = vp['verifiableCredential'] as unknown[];
    expect(Array.isArray(credentials)).toBeTrue();
    expect(credentials.length).toBe(1);
    const proof = vp['proof'] as Record<string, string>;
    expect(proof).toBeTruthy();
    expect(proof['type']).toBe('JsonWebSignature2020');
    expect(proof['proofPurpose']).toBe('authentication');
    expect(proof['verificationMethod']).toBe(
      mockDid.document.verificationMethod[0].id,
    );
    expect(proof['created']).toBeTruthy();
    expect(proof['jws']).toMatch(/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/u);
    expect(proof['challenge']).toBe('123');
    expect(proof['domain']).toBe('portal-client');
    expect(params.get('state')).toBe('xyz');
    expect(params.has('nonce')).toBeFalse();
    const submissionRaw = params.get('presentation_submission');
    expect(submissionRaw).toBeTruthy();
    const submission = JSON.parse(submissionRaw!) as Record<string, unknown>;
    expect(submission['definition_id']).toBe('presentation-def');
    const descriptorMap = submission['descriptor_map'] as Array<Record<string, unknown>>;
    expect(Array.isArray(descriptorMap)).toBeTrue();
    expect(descriptorMap[0]?.['path']).toBe('$');
    expect(descriptorMap[0]?.['format']).toBe('jwt_vp');

    postRequest.flush('ok');
    const result = await requestPromise;

    expect(result.responseUri).toBe(responseUri);
    expect(result.credentialCount).toBe(1);
    expect(result.presentationSubmission.definition_id).toBe('presentation-def');
    expect(ensureDidSpy).toHaveBeenCalled();
    expect(listVerifiableCredentialsSpy).toHaveBeenCalled();
    expect(ensureKeyPairSpy).toHaveBeenCalled();
  });

  it('rejects when no credentials satisfy the definition', async () => {
    listVerifiableCredentialsSpy.and.resolveTo([]);

    const responseUri = 'https://portal.example/no-credentials-response';
    const definition = {
      id: 'definition-no-credentials',
      input_descriptors: [{ id: 'descriptor-none' }],
    } satisfies Record<string, unknown>;
    const raw =
      `openid4vp://?response_uri=${encodeURIComponent(responseUri)}` +
      `&presentation_definition=${encodeURIComponent(JSON.stringify(definition))}`;

    await expectAsync(service.submitPresentationFromUri(raw)).toBeRejectedWithError(
      'Wallet does not store any verifiable credentials for presentation.',
    );
  });

  it('uses CapacitorHttp when running on a native platform', async () => {
    const nativeResponseUri = 'https://native.example/submit';
    const requestUri = 'https://native.example/request';
    isNativePlatformSpy.and.returnValue(true);
    const getSpy = spyOn(CapacitorHttp, 'get').and.callFake(async (options) => {
      if (options.url === requestUri) {
        return {
          data: {
            response_uri: nativeResponseUri,
            presentation_definition: {
              id: 'presentation-native',
              input_descriptors: [{ id: 'native-descriptor' }],
            },
          },
          status: 200,
          headers: {},
          url: requestUri,
        } satisfies HttpResponse;
      }

      return { data: {}, status: 200, headers: {}, url: options.url } satisfies HttpResponse;
    });

    const postSpy = spyOn(CapacitorHttp, 'post').and.resolveTo({
      data: {},
      status: 200,
      headers: {},
      url: nativeResponseUri,
    } satisfies HttpResponse);

    const resultPromise = service.submitPresentationFromUri(
      `openid4vp://?request_uri=${encodeURIComponent(requestUri)}`,
    );

    httpMock.expectNone(() => true);

    await expectAsync(resultPromise).toBeResolvedTo(
      jasmine.objectContaining({ responseUri: nativeResponseUri }),
    );

    expect(getSpy).toHaveBeenCalledWith(jasmine.objectContaining({ url: requestUri }));
    expect(postSpy).toHaveBeenCalledWith(
      jasmine.objectContaining({
        url: nativeResponseUri,
        headers: jasmine.objectContaining({
          'Content-Type': 'application/x-www-form-urlencoded',
        }),
        data: jasmine.stringMatching(/^vp_token=/u),
      }),
    );

    isNativePlatformSpy.and.returnValue(false);
    getSpy.and.callThrough();
    postSpy.and.callThrough();
  });

  it('parses inline request objects embedded in request_uri', async () => {
    const responseUri = 'https://portal.example/inline-response';
    const inlineRequestObject = {
      response_uri: responseUri,
      client_id: 'portal-client-inline',
      response_mode: 'direct_post',
      state: 'inline-state',
      nonce: 'inline-nonce',
      presentation_definition: {
        id: 'presentation-inline',
        input_descriptors: [{ id: 'descriptor-inline' }],
      },
    } satisfies Record<string, unknown>;

    const requestPromise = service.submitPresentationFromUri(
      `openid4vp://?request_uri=${encodeURIComponent(JSON.stringify(inlineRequestObject))}`,
    );

    httpMock.expectNone((req) => req.method === 'GET');

    const postRequest = httpMock.expectOne(responseUri);
    expect(postRequest.request.method).toBe('POST');
    expect(postRequest.request.headers.get('Content-Type')).toBe(
      'application/x-www-form-urlencoded',
    );
    postRequest.flush('ok');

    const result = await requestPromise;

    expect(result.responseUri).toBe(responseUri);
    expect(result.request.clientId).toBe('portal-client-inline');
    expect(result.request.state).toBe('inline-state');
    expect(result.request.nonce).toBe('inline-nonce');
    expect(result.request.presentationDefinition).toEqual({
      id: 'presentation-inline',
      input_descriptors: [{ id: 'descriptor-inline' }],
    });
  });

  it('parses parameters from openid URIs without a query delimiter', async () => {
    const clientId = 'https://verifier.example/oidc4vp/responses';
    const requestUri = 'https://verifier.example/oidc4vp/request';
    const raw =
      `openid://client_id=${encodeURIComponent(clientId)}` +
      `&client_id_scheme=redirect_uri` +
      `&request_uri=${encodeURIComponent(requestUri)}` +
      `&response_type=vp_token` +
      `&response_mode=direct_post` +
      `&state=state-123` +
      `&nonce=nonce-123` +
      `&presentation_definition_uri=${encodeURIComponent('https://verifier.example/definitions/credential.json')}`;

    const requestPromise = service.submitPresentationFromUri(raw);

    const getRequest = httpMock.expectOne(requestUri);
    expect(getRequest.request.method).toBe('GET');
    getRequest.flush({
      presentation_definition: {
        id: 'definition-123',
        input_descriptors: [{ id: 'descriptor-a' }],
      },
    });

    const postRequest = httpMock.expectOne(clientId);
    expect(postRequest.request.method).toBe('POST');
    expect(postRequest.request.headers.get('Content-Type')).toBe(
      'application/x-www-form-urlencoded',
    );
    const formBody = new URLSearchParams(postRequest.request.body as string);
    expect(formBody.get('state')).toBe('state-123');
    expect(formBody.has('nonce')).toBeFalse();

    postRequest.flush('ok');

    const result = await requestPromise;
    expect(result.request.clientId).toBe(clientId);
    expect(result.request.responseMode).toBe('direct_post');
    expect(result.request.state).toBe('state-123');
    expect(result.request.nonce).toBe('nonce-123');
    expect(result.responseUri).toBe(clientId);
  });

  it('supports direct_post mode using client_id as the response endpoint', async () => {
    const responseUri =
      'https://mica-semicivilized-heavily.ngrok-free.dev/oidc4vp/responses';
    const definitionUri =
      'https://mica-semicivilized-heavily.ngrok-free.dev/definitions/staff-credential.json';

    const requestPromise = service.submitPresentationFromUri(
      `openid://?client_id=${encodeURIComponent(responseUri)}&client_id_scheme=redirect_uri&response_mode=direct_post&scope=openid&nonce=demo-nonce&state=demo-state&presentation_definition_uri=${encodeURIComponent(definitionUri)}`,
    );

    const definitionRequest = httpMock.expectOne(definitionUri);
    expect(definitionRequest.request.method).toBe('GET');
    definitionRequest.flush({
      id: 'staff-credential',
      input_descriptors: [{ id: 'staff' }],
    });

    const postRequest = httpMock.expectOne(responseUri);
    expect(postRequest.request.method).toBe('POST');
    expect(postRequest.request.headers.get('Content-Type')).toBe(
      'application/x-www-form-urlencoded',
    );
    const formBody = new URLSearchParams(postRequest.request.body as string);
    const vpTokenRaw = formBody.get('vp_token');
    expect(vpTokenRaw).toBeTruthy();
    const decoded = decodeJwt(vpTokenRaw!);
    expect(decoded.payload['nonce']).toBe('demo-nonce');
    const vp = decoded.payload['vp'] as Record<string, unknown>;
    expect(decoded.payload['iss']).toBe(mockDid.id);
    expect(decoded.payload['aud']).toBe(responseUri);
    expect(vp['holder']).toBe(mockDid.id);
    expect(vp['proof']).toBeTruthy();
    expect(formBody.get('state')).toBe('demo-state');
    expect(formBody.has('nonce')).toBeFalse();
    const submissionRaw = formBody.get('presentation_submission');
    expect(submissionRaw).toBeTruthy();
    const submission = JSON.parse(submissionRaw!) as Record<string, unknown>;
    expect(submission['definition_id']).toBe('staff-credential');

    postRequest.flush('ok');
    const result = await requestPromise;

    expect(result.responseUri).toBe(responseUri);
    expect(result.request.responseMode).toBe('direct_post');
    expect(result.request.presentationDefinition).toEqual({
      id: 'staff-credential',
      input_descriptors: [{ id: 'staff' }],
    });
    expect(ensureKeyPairSpy).toHaveBeenCalled();
  });

  it('parses inline presentation definitions supplied via presentation_definition_uri', async () => {
    const responseUri = 'https://portal.example/inline-definition-response';
    const definition = {
      id: 'inline-definition',
      input_descriptors: [{ id: 'descriptor-inline-uri' }],
    } satisfies Record<string, unknown>;
    const raw =
      `openid4vp://?response_uri=${encodeURIComponent(responseUri)}` +
      `&presentation_definition_uri=${encodeURIComponent(JSON.stringify(definition))}`;

    const requestPromise = service.submitPresentationFromUri(raw);

    httpMock.expectNone((req) => req.method === 'GET');

    const postRequest = httpMock.expectOne(responseUri);
    expect(postRequest.request.method).toBe('POST');
    expect(postRequest.request.headers.get('Content-Type')).toBe('application/json');
    postRequest.flush({ status: 'ok' });

    const result = await requestPromise;

    expect(result.responseUri).toBe(responseUri);
    expect(result.request.presentationDefinition).toEqual(definition);
  });

  it('parses inline presentation definitions supplied via presentation_definition', async () => {
    const responseUri = 'https://portal.example/query-definition-response';
    const definition = {
      id: 'inline-query-definition',
      input_descriptors: [{ id: 'descriptor-inline-query' }],
    } satisfies Record<string, unknown>;
    const raw =
      `openid4vp://?response_uri=${encodeURIComponent(responseUri)}` +
      `&presentation_definition=${encodeURIComponent(JSON.stringify(definition))}`;

    const requestPromise = service.submitPresentationFromUri(raw);

    httpMock.expectNone((req) => req.method === 'GET');

    const postRequest = httpMock.expectOne(responseUri);
    expect(postRequest.request.method).toBe('POST');
    expect(postRequest.request.headers.get('Content-Type')).toBe('application/json');
    postRequest.flush({ status: 'ok' });

    const result = await requestPromise;

    expect(result.responseUri).toBe(responseUri);
    expect(result.request.presentationDefinition).toEqual(definition);
  });

  it('parses base64 data URLs in presentation_definition_uri', async () => {
    const responseUri = 'https://portal.example/base64-response';
    const definition = {
      id: 'data-url-definition',
      input_descriptors: [{ id: 'descriptor-data-url' }],
    } satisfies Record<string, unknown>;
    const base64Definition = btoa(JSON.stringify(definition));
    const dataUrl = `data:application/json;base64,${base64Definition}`;
    const raw =
      `openid://?response_uri=${encodeURIComponent(responseUri)}` +
      `&presentation_definition_uri=${encodeURIComponent(dataUrl)}`;

    const requestPromise = service.submitPresentationFromUri(raw);

    httpMock.expectNone((req) => req.method === 'GET');

    const postRequest = httpMock.expectOne(responseUri);
    expect(postRequest.request.method).toBe('POST');
    expect(postRequest.request.headers.get('Content-Type')).toBe('application/json');
    postRequest.flush({ status: 'ok' });

    const result = await requestPromise;

    expect(result.responseUri).toBe(responseUri);
    expect(result.request.presentationDefinition).toEqual(definition);
  });

  it('throws when response_uri is missing', async () => {
    await expectAsync(service.submitPresentationFromUri('openid4vp://?state=abc')).toBeRejectedWithError(
      'OIDC4VP request does not include a response_uri.',
    );
  });
});

function decodeJwt(token: string): {
  header: Record<string, unknown>;
  payload: Record<string, unknown>;
} {
  const [headerSegment, payloadSegment] = token.split('.');
  if (!headerSegment || !payloadSegment) {
    throw new Error('Invalid JWT');
  }

  return {
    header: JSON.parse(base64UrlDecode(headerSegment)) as Record<string, unknown>,
    payload: JSON.parse(base64UrlDecode(payloadSegment)) as Record<string, unknown>,
  };
}

function base64UrlDecode(value: string): string {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const padding = normalized.length % 4 === 0 ? '' : '='.repeat(4 - (normalized.length % 4));
  return atob(normalized + padding);
}

function stubCrypto(): {
  restore: () => void;
} {
  const originalDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'crypto');

  const importKeySpy = jasmine.createSpy('importKey').and.resolveTo({} as CryptoKey);
  const signSpy = jasmine
    .createSpy('sign')
    .and.resolveTo(new Uint8Array([1, 2, 3, 4]).buffer);
  const randomUuidSpy = jasmine.createSpy('randomUUID').and.returnValue('stub-uuid');
  const getRandomValuesSpy = jasmine
    .createSpy('getRandomValues')
    .and.callFake(<T extends ArrayBufferView>(array: T) => array);

  const cryptoStub = {
    subtle: {
      importKey: importKeySpy,
      sign: signSpy,
    } as unknown as SubtleCrypto,
    randomUUID: randomUuidSpy,
    getRandomValues: getRandomValuesSpy,
  } satisfies Partial<Crypto>;

  Object.defineProperty(globalThis, 'crypto', {
    configurable: true,
    enumerable: true,
    value: cryptoStub as Crypto,
  });

  return {
    restore: () => {
      if (originalDescriptor) {
        Object.defineProperty(globalThis, 'crypto', originalDescriptor);
      } else {
        delete (globalThis as { crypto?: Crypto }).crypto;
      }
    },
  };
}
