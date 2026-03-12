import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { OnboardingQr, OnboardingService } from '../services/onboarding.service';

@Component({
  selector: 'app-onboarding-page',
  templateUrl: './onboarding-page.component.html',
  styleUrls: ['./onboarding-page.component.css']
})
export class OnboardingPageComponent implements OnInit, OnDestroy {
  loading = true;
  error?: string;
  qr?: OnboardingQr;
  pageTitle = 'Izylife Verifier Portal';
  private updatesSub?: Subscription;
  spidLoginUrl?: string;

  constructor(
    private readonly onboardingService: OnboardingService,
    private readonly route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.updatePageTitle();

    this.updatesSub = this.onboardingService.updates().subscribe(update => {
      this.qr = update;
      this.loading = false;
      this.error = update.errorMessage ?? undefined;
      this.spidLoginUrl = this.normalizeSpidLoginUrl(update.actionUrl);
    });

    this.loadVerifierQr();
  }

  ngOnDestroy(): void {
    this.updatesSub?.unsubscribe();
  }

  get isIssuerStep(): boolean {
    const step = this.qr?.step;
    return step === 'ISSUER_QR' || step === 'ISSUER_SPID_PROMPT';
  }

  get isSpidPrompt(): boolean {
    return this.qr?.step === 'ISSUER_SPID_PROMPT';
  }

  get credentialSubjectEntries(): Array<{ key: string; value: unknown }> {
    const subject = this.qr?.credentialPreview?.subject;
    if (!subject) {
      return [];
    }
    return Object.entries(subject).map(([key, value]) => ({ key, value }));
  }

  retry(): void {
    this.loadVerifierQr();
  }

  private loadVerifierQr(): void {
    this.loading = true;
    this.error = undefined;
    this.onboardingService.fetchCurrent().subscribe({
      next: qr => {
        this.qr = qr;
        this.loading = false;
        this.error = qr.errorMessage ?? undefined;
        this.spidLoginUrl = this.normalizeSpidLoginUrl(qr.actionUrl);
      },
      error: () => {
        this.error = 'Unable to load the verifiable presentation request. Please try again.';
        this.loading = false;
      }
    });
  }

  private updatePageTitle(): void {
    const view = this.route.snapshot.data['view'];
    this.pageTitle = view === 'issuer' ? 'Izylife Issuer Portal' : 'Izylife Verifier Portal';
  }

  private normalizeSpidLoginUrl(actionUrl?: string): string | undefined {
    if (!actionUrl) {
      return undefined;
    }

    const trimmed = actionUrl.trim();
    return trimmed.length > 0 ? trimmed : undefined;
  }
}
