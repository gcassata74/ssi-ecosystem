/*
 * SSI Verifier
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
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { CredentialPreview, VerificationService } from '../services/verification.service';

interface QrResponse {
  step?: string;
  title?: string;
  description?: string;
  helperText?: string;
  qrCodePayload?: string;
  qrCodeImageDataUrl?: string;
  credentialPreview?: CredentialPreview;
  errorMessage?: string;
}

@Component({
  selector: 'app-portal-demo',
  templateUrl: './portal-demo.component.html',
  styleUrls: ['./portal-demo.component.css']
})
export class PortalDemoComponent implements OnInit, OnDestroy {

  state: 'landing' | 'loading' | 'qr' | 'success' | 'error' = 'landing';
  qr?: QrResponse;
  errorMessage?: string;
  verifiedCredential?: CredentialPreview;

  private wsSub?: Subscription;

  constructor(
    private readonly http: HttpClient,
    private readonly verificationService: VerificationService
  ) {}

  ngOnInit(): void {
    this.verificationService.connect();
    this.wsSub = this.verificationService.updates().subscribe(update => {
      if (update.credentialPreview && this.state === 'qr') {
        this.verifiedCredential = update.credentialPreview;
        this.state = 'success';
      }
    });
  }

  ngOnDestroy(): void {
    this.wsSub?.unsubscribe();
    this.verificationService.disconnect();
  }

  startVerification(): void {
    this.state = 'loading';
    this.errorMessage = undefined;

    this.http.get<QrResponse>('/api/verification/qr').subscribe({
      next: qr => {
        this.qr = qr;
        this.state = 'qr';
      },
      error: () => {
        this.errorMessage = 'Impossibile contattare il servizio di verifica. Riprovare.';
        this.state = 'error';
      }
    });
  }

  reset(): void {
    this.state = 'landing';
    this.qr = undefined;
    this.verifiedCredential = undefined;
    this.errorMessage = undefined;
  }

  get credentialSubjectEntries(): Array<{ key: string; value: unknown }> {
    const subject = this.verifiedCredential?.subject;
    if (!subject) return [];
    return Object.entries(subject)
      .filter(([key]) => key !== 'id')
      .map(([key, value]) => ({ key, value }));
  }
}
