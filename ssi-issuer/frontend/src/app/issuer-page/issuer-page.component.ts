/*
 * SSI Issuer
 * Copyright (c) 2026-present Izylife Solutions s.r.l.
 * Author: Giuseppe Cassata
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { KeycloakAuthService } from '../services/keycloak-auth.service';
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
  tokenClaims?: Record<string, unknown>;
  private updatesSub?: Subscription;

  constructor(
    private readonly keycloakAuth: KeycloakAuthService,
    private readonly onboardingService: OnboardingService
  ) {}

  async ngOnInit(): Promise<void> {
    this.tokenClaims = this.keycloakAuth.getTokenParsed();

    this.updatesSub = this.onboardingService.updates().subscribe(update => {
      if (update.step === 'ISSUER_QR') {
        this.qr = update;
        this.loading = false;
        this.error = undefined;
      }
    });

    this.onboardingService.connect();
    await this.enroll();
  }

  ngOnDestroy(): void {
    this.updatesSub?.unsubscribe();
  }

  get tokenClaimsJson(): string {
    return this.tokenClaims ? JSON.stringify(this.tokenClaims, null, 2) : '';
  }

  get credentialSubjectEntries(): Array<{ key: string; value: unknown }> {
    const subject = this.qr?.credentialPreview?.subject;
    if (!subject) return [];
    return Object.entries(subject).map(([key, value]) => ({ key, value }));
  }

  async retry(): Promise<void> {
    this.error = undefined;
    await this.enroll();
  }

  private async enroll(): Promise<void> {
    this.loading = true;
    try {
      const token = await this.keycloakAuth.getToken();
      this.onboardingService.enroll(token).subscribe({
        next: qr => {
          this.qr = qr;
          this.loading = false;
        },
        error: () => {
          this.error = 'Enrollment failed. Please try again.';
          this.loading = false;
        }
      });
    } catch {
      this.error = 'Unable to obtain access token. Please refresh the page.';
      this.loading = false;
    }
  }
}
