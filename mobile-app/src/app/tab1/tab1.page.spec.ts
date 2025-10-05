import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Tab1Page } from './tab1.page';
import { Oidc4vcService } from '../services/oidc4vc.service';
import { Oidc4vpService } from '../services/oidc4vp.service';

describe('Tab1Page', () => {
  let component: Tab1Page;
  let fixture: ComponentFixture<Tab1Page>;
  const oidc4vpStub = {
    isOidc4vpUri: jasmine.createSpy('isOidc4vpUri').and.returnValue(false),
    submitPresentationFromUri: jasmine
      .createSpy('submitPresentationFromUri')
      .and.resolveTo({ responseUri: '', credentialCount: 0, request: { responseUri: '' } }),
  } satisfies Partial<Oidc4vpService>;
  const oidc4vcStub = {
    isOidc4vcUri: jasmine.createSpy('isOidc4vcUri').and.returnValue(false),
    acceptCredentialOfferFromUri: jasmine.createSpy('acceptCredentialOfferFromUri').and.resolveTo({
      issuer: '',
      credentialCount: 0,
      offer: {},
      credentials: [],
    }),
  } satisfies Partial<Oidc4vcService>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Tab1Page],
      providers: [
        { provide: Oidc4vpService, useValue: oidc4vpStub },
        { provide: Oidc4vcService, useValue: oidc4vcStub },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Tab1Page);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('delegates credential offers to the OIDC4VC service', async () => {
    const handler = component as unknown as {
      processScanResult(data: string): Promise<void>;
    };

    oidc4vpStub.isOidc4vpUri.and.returnValue(false);
    oidc4vcStub.isOidc4vcUri.and.returnValue(true);

    await handler.processScanResult('openid-credential-offer://?credential_offer={}');

    expect(oidc4vcStub.acceptCredentialOfferFromUri).toHaveBeenCalledWith(
      'openid-credential-offer://?credential_offer={}',
    );
  });
});
