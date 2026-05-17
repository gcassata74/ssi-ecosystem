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
import { Subscription } from 'rxjs';
import { VerifierQr, VerificationService } from '../services/verification.service';

@Component({
  selector: 'app-verifier-page',
  templateUrl: './verifier-page.component.html',
  styleUrls: ['./verifier-page.component.css']
})
export class VerifierPageComponent implements OnInit, OnDestroy {
  loading = true;
  error?: string;
  qr?: VerifierQr;
  private updatesSub?: Subscription;

  constructor(private readonly verificationService: VerificationService) {}

  ngOnInit(): void {
    this.updatesSub = this.verificationService.updates().subscribe(update => {
      this.qr = update;
      this.loading = false;
      this.error = update.errorMessage ?? undefined;
    });

    this.load();
  }

  ngOnDestroy(): void {
    this.updatesSub?.unsubscribe();
  }

  get credentialSubjectEntries(): Array<{ key: string; value: unknown }> {
    const subject = this.qr?.credentialPreview?.subject;
    if (!subject) return [];
    return Object.entries(subject).map(([key, value]) => ({ key, value }));
  }

  retry(): void { this.load(); }

  private load(): void {
    this.loading = true;
    this.error = undefined;
    this.verificationService.fetchCurrent().subscribe({
      next: qr => { this.qr = qr; this.loading = false; this.error = qr.errorMessage ?? undefined; },
      error: () => { this.error = 'Unable to load the verification QR. Please try again.'; this.loading = false; }
    });
  }
}
