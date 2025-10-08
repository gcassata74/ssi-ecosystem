import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
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
  private lastStep?: string;
  private readonly issuerSteps = new Set(['ISSUER_QR', 'ISSUER_SPID_PROMPT']);

  constructor(
    private readonly onboardingService: OnboardingService,
    private readonly router: Router,
    private readonly route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.updatePageTitle();

    this.updatesSub = this.onboardingService.updates().subscribe(update => {
      this.qr = update;
      this.loading = false;
      this.error = undefined;
      this.syncRouteWithStep(update.step);
    });

    this.loadQr();
  }

  ngOnDestroy(): void {
    this.updatesSub?.unsubscribe();
  }

  get isIssuerStep(): boolean {
    const step = this.qr?.step;
    return !!step && this.issuerSteps.has(step);
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
    this.loadQr();
  }

  startSpidLogin(): void {
    if (!this.qr?.actionUrl) {
      return;
    }
    window.location.href = this.qr.actionUrl;
  }

  private loadQr(): void {
    this.loading = true;
    this.error = undefined;
    this.onboardingService.fetchCurrent().subscribe({
      next: qr => {
        this.qr = qr;
        this.loading = false;
        this.syncRouteWithStep(qr.step);
      },
      error: () => {
        this.error = 'Unable to load the verifiable presentation request. Please try again.';
        this.loading = false;
      }
    });
  }

  private syncRouteWithStep(step?: string): void {
    if (!step) {
      return;
    }

    const issuerActive = this.issuerSteps.has(step);
    const targetPath = issuerActive ? '/issuer' : '/verifier';
    const needsNavigation = !this.router.url.startsWith(targetPath);

    if (needsNavigation) {
      this.router.navigateByUrl(targetPath, { replaceUrl: true }).catch(() => undefined);
    }

    if (step !== this.lastStep) {
      this.lastStep = step;
    }
  }

  private updatePageTitle(): void {
    const view = this.route.snapshot.data['view'];
    this.pageTitle = view === 'issuer' ? 'Izylife Issuer Portal' : 'Izylife Verifier Portal';
  }
}
