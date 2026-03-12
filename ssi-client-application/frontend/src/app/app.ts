import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { SsiAuthService } from '@ssi/issuer-auth-client/angular';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit, OnDestroy {
  authStatus: string = 'unauthenticated';
  accessToken?: string;
  holderDid?: string;
  credentialEntries: Array<{ key: string; value: unknown }> = [];
  tokenPayloadJson?: string;

  private tokensSub?: Subscription;

  constructor(private readonly auth: SsiAuthService) {}

  ngOnInit(): void {
    this.authStatus = this.auth.clientInstance.authStatus;
    this.tokensSub = this.auth.tokens$.subscribe((tokens) => {
      if (!tokens) {
        this.resetView();
        return;
      }
      this.authStatus = 'authenticated';
      this.accessToken = tokens.accessToken;
      this.decodeAccessToken(tokens.accessToken);
    });
  }

  ngOnDestroy(): void {
    this.tokensSub?.unsubscribe();
  }

  public onLoginClick(): void {
    this.auth.beginVerifierFlow().catch((error) => console.error('Unable to start verifier flow', error));
  }

  private resetView(): void {
    this.authStatus = 'unauthenticated';
    this.accessToken = undefined;
    this.holderDid = undefined;
    this.credentialEntries = [];
    this.tokenPayloadJson = undefined;
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
