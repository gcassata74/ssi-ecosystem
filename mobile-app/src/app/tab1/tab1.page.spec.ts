import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Tab1Page } from './tab1.page';
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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Tab1Page],
      providers: [{ provide: Oidc4vpService, useValue: oidc4vpStub }],
    }).compileComponents();

    fixture = TestBed.createComponent(Tab1Page);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
