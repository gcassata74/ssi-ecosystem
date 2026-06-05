/*
 * SSI Client Application
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
import { SsiAuthService } from '@ssi/issuer-auth-client/angular';
import { issuerBaseUrl, keycloakRealm } from './app.config';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit, OnDestroy {
  authStatus: string = 'unauthenticated';
  accessToken?: string;
  holderDid?: string;
  credentialEntries: Array<{ key: string; value: unknown }> = [];
  tokenPayloadJson?: string;

  readonly issuerUrl = `${issuerBaseUrl}/issuer?realm=${keycloakRealm}`;

  private readonly dashboardPath = '/dashboard';
  private tokensSub?: Subscription;

  constructor(private readonly auth: SsiAuthService) {}

  ngOnInit(): void {
    this.authStatus = this.auth.clientInstance.authStatus;

    const snapshot = this.auth.clientInstance.getTokenSnapshot();
    if (snapshot?.accessToken) {
      this.applyAuthenticatedState(snapshot.accessToken);
    }

    this.tokensSub = this.auth.tokens$.subscribe((tokens) => {
      if (!tokens?.accessToken) {
        this.resetView();
        return;
      }
      this.applyAuthenticatedState(tokens.accessToken);
    });
  }

  ngOnDestroy(): void {
    this.tokensSub?.unsubscribe();
  }

  public onGetCredentialClick(): void {
    window.location.href = this.issuerUrl;
  }

  public onLoginClick(): void {
    this.auth.beginVerifierFlow().catch((error) => console.error('Unable to start verifier flow', error));
  }

  private applyAuthenticatedState(accessToken: string): void {
    this.authStatus = 'authenticated';
    this.accessToken = accessToken;
    this.decodeAccessToken(accessToken);
    this.navigateToDashboard();
  }

  private resetView(): void {
    this.authStatus = 'unauthenticated';
    this.accessToken = undefined;
    this.holderDid = undefined;
    this.credentialEntries = [];
    this.tokenPayloadJson = undefined;

    if (this.isDashboardPath() && !this.hasOauthParams()) {
      this.replacePath('/');
    }
  }

  private navigateToDashboard(): void {
    if (!this.isBrowser()) {
      return;
    }
    if (window.location.pathname !== this.dashboardPath) {
      this.replacePath(this.dashboardPath);
    }
  }

  private isDashboardPath(): boolean {
    return this.isBrowser() && window.location.pathname === this.dashboardPath;
  }

  private hasOauthParams(): boolean {
    if (!this.isBrowser()) {
      return false;
    }
    const params = new URLSearchParams(window.location.search);
    return params.has('code') || params.has('state') || params.has('error');
  }

  private replacePath(path: string): void {
    if (!this.isBrowser()) {
      return;
    }
    window.history.replaceState({}, document.title, path);
  }

  private isBrowser(): boolean {
    return typeof window !== 'undefined';
  }

  private decodeAccessToken(token: string): void {
    try {
      const payload = this.decodeJwtPayload(token);
      this.tokenPayloadJson = JSON.stringify(payload, null, 2);
      this.holderDid = typeof payload.sub === 'string' ? payload.sub : undefined;

      const preview = payload.credential_preview as { subject?: Record<string, unknown> } | undefined;
      if (preview?.subject && typeof preview.subject === 'object') {
        this.credentialEntries = Object.entries(preview.subject).map(([key, value]) => ({ key, value }));
      } else {
        this.credentialEntries = [];
      }
    } catch (error) {
      console.error('Failed to decode access token', error);
      this.credentialEntries = [];
      this.tokenPayloadJson = undefined;
    }
  }

  private decodeJwtPayload(token: string): any {
    const segments = token.split('.');
    if (segments.length < 2) {
      throw new Error('Token is not a valid JWT');
    }
    const payloadSegment = segments[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = payloadSegment.padEnd(payloadSegment.length + (4 - (payloadSegment.length % 4)) % 4, '=');
    const decoded = this.decodeBase64(padded);
    return JSON.parse(decoded);
  }

  private decodeBase64(value: string): string {
    if (typeof atob === 'function') {
      return atob(value);
    }
    if (typeof globalThis !== 'undefined' && (globalThis as any).Buffer) {
      return (globalThis as any).Buffer.from(value, 'base64').toString('utf-8');
    }
    throw new Error('Base64 decoding is not supported in this environment');
  }
}
