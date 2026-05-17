import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { OnboardingQr, OnboardingService } from '../services/onboarding.service';

@Component({
  selector: 'app-issuer-page',
  templateUrl: './issuer-page.component.html',
  styleUrls: ['./issuer-page.component.css']
})
export class IssuerPageComponent implements OnInit, OnDestroy {
  loading = true;
  error?: string;
  qr?: OnboardingQr;
  private updatesSub?: Subscription;
  spidLoginUrl?: string;

  constructor(private readonly onboardingService: OnboardingService) {}

  ngOnInit(): void {
    this.loadIssuerQr();

    this.updatesSub = this.onboardingService.updates().subscribe(update => {
      if (!update) {
        return;
      }
      if (update.step === 'ISSUER_QR' || update.step === 'ISSUER_SPID_PROMPT') {
        this.qr = update;
        this.loading = false;
        this.error = undefined;
        this.spidLoginUrl = this.normalizeSpidLoginUrl(update.actionUrl);
      }
    });
  }

  ngOnDestroy(): void {
    this.updatesSub?.unsubscribe();
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
    this.loadIssuerQr();
  }

  private loadIssuerQr(): void {
    this.loading = true;
    this.error = undefined;
    this.onboardingService.fetchIssuer().subscribe({
      next: qr => {
        this.qr = qr;
        this.loading = false;
        this.spidLoginUrl = this.normalizeSpidLoginUrl(qr.actionUrl);
      },
      error: () => {
        this.error = 'Unable to load the issuer onboarding resources. Please try again.';
        this.loading = false;
      }
    });
  }

  private normalizeSpidLoginUrl(actionUrl?: string): string | undefined {
    if (!actionUrl) {
      return undefined;
    }

    const trimmed = actionUrl.trim();
    return trimmed.length > 0 ? trimmed : undefined;
  }
}
