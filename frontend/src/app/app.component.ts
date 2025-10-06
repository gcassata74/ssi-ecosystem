import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { OnboardingService, OnboardingQr } from './services/onboarding.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  loading = true;
  error?: string;
  qr?: OnboardingQr;
  private updatesSub?: Subscription;

  constructor(private readonly onboardingService: OnboardingService) {}

  ngOnInit(): void {
    this.updatesSub = this.onboardingService.updates().subscribe(update => {
      this.qr = update;
      this.loading = false;
      this.error = undefined;
    });

    this.onboardingService.connect();
    this.loadQr();
  }

  ngOnDestroy(): void {
    this.updatesSub?.unsubscribe();
    this.onboardingService.disconnect();
  }

  get isIssuerStep(): boolean {
    return this.qr?.step === 'ISSUER_QR';
  }

  get credentialSubjectEntries(): Array<{ key: string; value: unknown }> {
    const subject = this.qr?.credentialPreview?.subject;
    if (!subject) {
      return [];
    }
    return Object.entries(subject).map(([key, value]) => ({ key, value }));
  }

  retry(): void {
    this.loadQr();
  }

  private loadQr(): void {
    this.loading = true;
    this.error = undefined;
    this.onboardingService.fetchCurrent().subscribe({
      next: qr => {
        this.qr = qr;
        this.loading = false;
      },
      error: () => {
        this.error = 'Unable to load the verifiable presentation request. Please try again.';
        this.loading = false;
      }
    });
  }
}
