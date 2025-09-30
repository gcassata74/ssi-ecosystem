import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

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
  const ensureDidSpy = jasmine.createSpy('ensureDid').and.resolveTo(mockDid);
  const listVerifiableCredentialsSpy = jasmine
    .createSpy('listVerifiableCredentials')
    .and.resolveTo([{ id: 'urn:vc:1' }]);
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

    service = TestBed.inject(Oidc4vpService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    ensureDidSpy.calls.reset();
    listVerifiableCredentialsSpy.calls.reset();
    ensureKeyPairSpy.calls.reset();
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
      presentation_definition: { id: 'presentation-def' },
    });

    const postRequest = httpMock.expectOne(responseUri);
    expect(postRequest.request.method).toBe('POST');
    const body = postRequest.request.body as Record<string, unknown>;
    expect(body).toBeTruthy();
    expect(body['vp_token']).toBeTruthy();
    const vp = body['vp_token'] as Record<string, unknown>;
    expect(vp['holderDidDocumentBase58']).toBe(mockDid.encodedDocument);
    expect(vp['holder']).toBe(mockDid.id);
    expect(Array.isArray(vp['verifiableCredential'])).toBeTrue();
    expect((vp['verifiableCredential'] as unknown[]).length).toBe(1);
    const proof = vp['proof'] as Record<string, string>;
    expect(proof).toBeTruthy();
    expect(proof.type).toBe('JsonWebSignature2020');
    expect(proof.proofPurpose).toBe('authentication');
    expect(proof.verificationMethod).toBe(
      mockDid.document.verificationMethod[0].id,
    );
    expect(proof.created).toBeTruthy();
    expect(proof.jws).toMatch(/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/u);
    expect(body['credential_count']).toBe(1);
    expect(body['state']).toBe('xyz');
    expect(body['nonce']).toBe('123');
    expect(body['client_id']).toBe('portal-client');
    expect(body['presentation_submission']).toBeTruthy();

    postRequest.flush({ status: 'ok' });
    const result = await requestPromise;

    expect(result.responseUri).toBe(responseUri);
    expect(result.credentialCount).toBe(1);
    expect(ensureDidSpy).toHaveBeenCalled();
    expect(listVerifiableCredentialsSpy).toHaveBeenCalled();
    expect(ensureKeyPairSpy).toHaveBeenCalled();
  });

  it('parses inline request objects embedded in request_uri', async () => {
    const responseUri = 'https://portal.example/inline-response';
    const inlineRequestObject = {
      response_uri: responseUri,
      client_id: 'portal-client-inline',
      response_mode: 'direct_post',
      state: 'inline-state',
      nonce: 'inline-nonce',
      presentation_definition: { id: 'presentation-inline' },
    } satisfies Record<string, unknown>;

    const requestPromise = service.submitPresentationFromUri(
      `openid4vp://?request_uri=${encodeURIComponent(JSON.stringify(inlineRequestObject))}`,
    );

    httpMock.expectNone((req) => req.method === 'GET');

    const postRequest = httpMock.expectOne(responseUri);
    expect(postRequest.request.method).toBe('POST');
    postRequest.flush({ status: 'ok' });

    const result = await requestPromise;

    expect(result.responseUri).toBe(responseUri);
    expect(result.request.clientId).toBe('portal-client-inline');
    expect(result.request.state).toBe('inline-state');
    expect(result.request.nonce).toBe('inline-nonce');
    expect(result.request.presentationDefinition).toEqual({ id: 'presentation-inline' });
  });

  it('supports direct_post mode using client_id as the response endpoint', async () => {
    const responseUri = 'https://verifier.izylife.example.org/callback';
    const definitionUri =
      'https://verifier.izylife.example.org/definitions/staff-credential.json';

    const requestPromise = service.submitPresentationFromUri(
      `openid://?client_id=${encodeURIComponent(responseUri)}&client_id_scheme=redirect_uri&response_mode=direct_post&scope=openid&nonce=demo-nonce&state=demo-state&presentation_definition_uri=${encodeURIComponent(definitionUri)}`,
    );

    const definitionRequest = httpMock.expectOne(definitionUri);
    expect(definitionRequest.request.method).toBe('GET');
    definitionRequest.flush({ id: 'staff-credential', input_descriptors: [] });

    const postRequest = httpMock.expectOne(responseUri);
    expect(postRequest.request.method).toBe('POST');
    const vp = (postRequest.request.body as { vp_token?: Record<string, unknown> }).vp_token;
    expect(vp).toBeTruthy();
    expect(vp?.holder).toBe(mockDid.id);
    expect(vp?.proof).toBeTruthy();

    postRequest.flush({ status: 'ok' });
    const result = await requestPromise;

    expect(result.responseUri).toBe(responseUri);
    expect(result.request.responseMode).toBe('direct_post');
    expect(result.request.presentationDefinition).toEqual({
      id: 'staff-credential',
      input_descriptors: [],
    });
    expect(ensureKeyPairSpy).toHaveBeenCalled();
  });

  it('throws when response_uri is missing', async () => {
    await expectAsync(service.submitPresentationFromUri('openid4vp://?state=abc')).toBeRejectedWithError(
      'OIDC4VP request does not include a response_uri.',
    );
  });
});
