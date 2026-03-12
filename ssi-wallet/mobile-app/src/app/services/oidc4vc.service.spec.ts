import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Capacitor } from '@capacitor/core';

import { CredentialService } from './credential.service';
import { Oidc4vcService } from './oidc4vc.service';

describe('Oidc4vcService', () => {
  let service: Oidc4vcService;
  let httpMock: HttpTestingController;
  let isNativePlatformSpy: jasmine.Spy<() => boolean>;

  const addVerifiableCredentialsSpy = jasmine
    .createSpy('addVerifiableCredentials')
    .and.resolveTo(undefined);
  const credentialServiceStub = {
    listVerifiableCredentials: jasmine.createSpy('listVerifiableCredentials').and.resolveTo([]),
    addVerifiableCredentials: addVerifiableCredentialsSpy,
  } satisfies Partial<CredentialService>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [{ provide: CredentialService, useValue: credentialServiceStub }, Oidc4vcService],
    });

    isNativePlatformSpy = spyOn(Capacitor, 'isNativePlatform').and.returnValue(false);
    service = TestBed.inject(Oidc4vcService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    addVerifiableCredentialsSpy.calls.reset();
    credentialServiceStub.listVerifiableCredentials.calls.reset();
    isNativePlatformSpy.calls.reset();
    isNativePlatformSpy.and.returnValue(false);
  });

  it('detects OIDC4VC credential offer URIs', () => {
    expect(service.isOidc4vcUri('openid-credential-offer://?credential_offer={}')).toBeTrue();
    expect(service.isOidc4vcUri('https://issuer.example?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer')).toBeTrue();
    expect(service.isOidc4vcUri('https://example.com')).toBeFalse();
  });

  it('redeems a pre-authorized credential offer and stores the credential', async () => {
    const offer = {
      credential_issuer: 'https://issuer.example',
      credentials: [
        {
          format: 'ldp_vc',
          credential_definition: {
            type: ['VerifiableCredential', 'ExampleStaffCredential'],
          },
        },
      ],
      grants: {
        'urn:ietf:params:oauth:grant-type:pre-authorized_code': {
          'pre-authorized_code': 'code-123',
          user_pin_required: false,
        },
      },
    } satisfies Record<string, unknown>;

    const raw =
      `openid-credential-offer://?credential_offer=${encodeURIComponent(JSON.stringify(offer))}`;

    const issuancePromise = service.acceptCredentialOfferFromUri(raw);

    const metadataRequest = httpMock.expectOne('https://issuer.example/.well-known/openid-credential-issuer');
    expect(metadataRequest.request.method).toBe('GET');
    metadataRequest.flush({
      credential_endpoint: 'https://issuer.example/credential',
      token_endpoint: 'https://issuer.example/token',
    });

    const tokenRequest = httpMock.expectOne('https://issuer.example/token');
    expect(tokenRequest.request.method).toBe('POST');
    expect(tokenRequest.request.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    expect(tokenRequest.request.body).toBe(
      'grant_type=urn:ietf:params:oauth:grant-type:pre-authorized_code&pre-authorized_code=code-123',
    );
    tokenRequest.flush({ access_token: 'access-token', token_type: 'Bearer' });

    const credentialRequest = httpMock.expectOne('https://issuer.example/credential');
    expect(credentialRequest.request.method).toBe('POST');
    expect(credentialRequest.request.headers.get('Authorization')).toBe('Bearer access-token');
    expect(credentialRequest.request.headers.get('Content-Type')).toBe('application/json');
    expect(credentialRequest.request.body).toEqual({
      format: 'ldp_vc',
      credential_definition: {
        type: ['VerifiableCredential', 'ExampleStaffCredential'],
      },
    });
    credentialRequest.flush({
      format: 'ldp_vc',
      credential: { id: 'urn:vc:example', type: ['VerifiableCredential', 'ExampleStaffCredential'] },
    });

    const portalNotificationRequest = httpMock.expectOne(
      'https://issuer.example/onboarding/issuer/credentials-received',
    );
    expect(addVerifiableCredentialsSpy).toHaveBeenCalledTimes(1);
    expect(portalNotificationRequest.request.method).toBe('POST');
    expect(portalNotificationRequest.request.headers.get('Content-Type')).toBe('application/json');
    expect(portalNotificationRequest.request.body).toEqual(
      jasmine.objectContaining({ credentialCount: 1 }),
    );
    expect(typeof portalNotificationRequest.request.body.walletDid).toBe('string');
    portalNotificationRequest.flush({});

    const result = await issuancePromise;

    expect(result.issuer).toBe('https://issuer.example');
    expect(result.credentialCount).toBe(1);
    expect(result.credentials.length).toBe(1);
    expect(result.credentials[0]).toEqual({
      id: 'urn:vc:example',
      type: ['VerifiableCredential', 'ExampleStaffCredential'],
    });
    expect(addVerifiableCredentialsSpy).toHaveBeenCalledWith([
      jasmine.objectContaining({ id: 'urn:vc:example' }),
    ]);
  });
});
