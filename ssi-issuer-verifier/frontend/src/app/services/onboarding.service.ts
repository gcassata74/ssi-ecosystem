import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Observable, Subject, catchError, map, throwError } from 'rxjs';

export interface CredentialPreview {
  issuerName?: string;
  issuerId?: string;
  type?: string[];
  subject?: Record<string, unknown>;
  rawJson?: string;
}

export interface OnboardingQr {
  step: string;
  title: string;
  description: string;
  helperText?: string;
  qrCodePayload?: string;
  qrCodeImageDataUrl?: string;
  actionLabel?: string;
  actionUrl?: string;
  credentialPreview?: CredentialPreview;
  errorMessage?: string;
}

@Injectable({ providedIn: 'root' })
export class OnboardingService implements OnDestroy {
  private readonly updatesSubject = new Subject<OnboardingQr>();
  private readonly client: Client;
  private subscription?: StompSubscription;
  private readonly launchParams = typeof window !== 'undefined' ? new URLSearchParams(window.location.search) : new URLSearchParams();
  private redirectInProgress = false;

  constructor(private readonly http: HttpClient, private readonly zone: NgZone) {
    this.client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      heartbeatOutgoing: 20000,
      heartbeatIncoming: 0,
      debug: () => undefined
    });

    this.client.onConnect = () => {
      this.zone.run(() => this.subscribeToTopic());
    };

    this.client.onDisconnect = () => {
      this.zone.run(() => this.clearSubscription());
    };

    this.client.onStompError = frame => {
      console.error('STOMP error', frame.headers['message'], frame.body);
    };

    this.client.onWebSocketError = event => {
      console.error('WebSocket error', event);
    };

    this.client.onWebSocketClose = () => {
      this.zone.run(() => this.clearSubscription());
    };
  }

  connect(): void {
    if (!this.client.active) {
      this.client.activate();
    }
  }

  disconnect(): void {
    this.clearSubscription();
    if (this.client.active) {
      this.client.deactivate();
    }
  }

  fetchCurrent(): Observable<OnboardingQr> {
    const params = this.buildQueryParams();
    return this.http.get<unknown>('/api/onboarding/qr', { params }).pipe(
      map(payload => this.normalizePayload(payload)),
      catchError(error => {
        if (error?.status === 404) {
          return this.http.get<unknown>('/api/poc/vp-request', { params }).pipe(
            map(payload => this.normalizePayload(payload))
          );
        }
        return throwError(() => error);
      })
    );
  }

  fetchIssuer(): Observable<OnboardingQr> {
    return this.http.get<unknown>('/api/onboarding/issuer', { params: this.buildQueryParams() }).pipe(
      map(payload => this.normalizeQr(payload as Record<string, unknown>))
    );
  }

  updates(): Observable<OnboardingQr> {
    return this.updatesSubject.asObservable();
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.updatesSubject.complete();
  }

  private subscribeToTopic(): void {
    if (!this.client.connected) {
      return;
    }
    this.clearSubscription();
    this.subscription = this.client.subscribe('/topic/onboarding', message => {
      this.zone.run(() => this.handleMessage(message));
    });
  }

  private clearSubscription(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
      this.subscription = undefined;
    }
  }

  private handleMessage(message: IMessage): void {
    const rawBody = message.body;
    if (!rawBody) {
      return;
    }

    try {
      const parsed = JSON.parse(rawBody);
      const payload = this.normalizePayload(parsed);
      this.updatesSubject.next(payload);
    } catch (error) {
      console.error('Unable to parse onboarding update', error);
    }
  }

  private normalizePayload(payload: unknown): OnboardingQr {
    if (payload == null || typeof payload !== 'object') {
      throw new Error('Invalid onboarding payload received');
    }

    const candidate = payload as Record<string, unknown>;

    if (candidate['verifier'] || candidate['issuer'] || candidate['currentStep']) {
      return this.normalizeStatusPayload(candidate);
    }

    return this.normalizeQr(candidate);
  }

  private normalizeStatusPayload(status: Record<string, unknown>): OnboardingQr {
    const currentStep = typeof status['currentStep'] === 'string'
      ? (status['currentStep'] as string)
      : undefined;

    const verifier = status['verifier'];
    const issuer = status['issuer'];
    const verifierError = this.extractString(status, ['verifierError']);

    this.handleAuthorizationRedirect(status);

    if (issuer && typeof currentStep === 'string' && currentStep.startsWith('ISSUER')) {
      const qr = this.normalizeQr(issuer as Record<string, unknown>);
      qr.step = currentStep;
      return qr;
    }

    if (verifier) {
      const qr = this.normalizeQr(verifier as Record<string, unknown>);
      if (currentStep) {
        qr.step = currentStep;
      }
      if (verifierError) {
        qr.errorMessage = verifierError;
      }
      return qr;
    }

    throw new Error('Unsupported onboarding status payload');
  }

  private buildQueryParams(): HttpParams {
    let params = new HttpParams();
    const redirectUri = this.getLaunchParam('redirect_uri');
    if (redirectUri) {
      params = params.set('redirect_uri', redirectUri);
    }
    const clientId = this.getLaunchParam('client_id');
    if (clientId) {
      params = params.set('client_id', clientId);
    }
    const state = this.getLaunchParam('state');
    if (state) {
      params = params.set('state', state);
    }
    return params;
  }

  private getLaunchParam(name: string): string | undefined {
    const value = this.launchParams.get(name);
    return value ? value : undefined;
  }

  private handleAuthorizationRedirect(status: Record<string, unknown>): void {
    if (this.redirectInProgress || typeof window === 'undefined') {
      return;
    }

    const code = this.extractString(status, ['authorizationCode']);
    const redirectUri = this.extractString(status, ['authorizationRedirectUri', 'redirectUri']);
    if (!code || !redirectUri) {
      return;
    }

    const authState = this.extractString(status, ['authorizationState', 'state']);

    try {
      const target = new URL(redirectUri, window.location.origin);
      target.searchParams.set('code', code);
      if (authState) {
        target.searchParams.set('state', authState);
      }
      this.redirectInProgress = true;
      window.location.assign(target.toString());
    } catch (error) {
      console.error('Failed to redirect after verifier authorization', error);
    }
  }

  private normalizeQr(source: Record<string, unknown>): OnboardingQr {
    const step = this.extractString(source, ['step']) ?? 'VP_REQUEST';
    const qrCodeImageDataUrl = this.extractString(source, ['qrCodeImageDataUrl', 'qrImageDataUrl']);
    const qrCodePayload = this.extractString(source, ['qrCodePayload', 'payload']);

    const qrDataRequired = step !== 'ISSUER_SPID_PROMPT';
    if (qrDataRequired && (!qrCodeImageDataUrl || !qrCodePayload)) {
      throw new Error('Missing QR code data in onboarding payload');
    }

    const title = this.extractString(source, ['title', 'label']) ?? 'Verifiable Presentation Request';
    const description = this.extractString(source, ['description', 'instructions'])
      ?? 'Scan this code with your SSI wallet to continue the verification flow.';
    const helperText = this.extractString(source, ['helperText']);
    const actionLabel = this.extractString(source, ['actionLabel']);
    const actionUrl = this.extractString(source, ['actionUrl']);

    const qr: OnboardingQr = {
      step,
      title,
      description,
      helperText: helperText ?? undefined,
      actionLabel: actionLabel ?? undefined,
      actionUrl: actionUrl ?? undefined
    };

    const serverPreview = this.normalizeServerCredentialPreview(source['credentialPreview']);
    if (serverPreview) {
      qr.credentialPreview = serverPreview;
    }

    if (qrCodePayload) {
      qr.qrCodePayload = qrCodePayload;
      if (!qr.credentialPreview) {
        const credentialPreview = this.extractCredentialPreview(qrCodePayload);
        if (credentialPreview) {
          qr.credentialPreview = credentialPreview;
        }
      }
    }

    if (qrCodeImageDataUrl) {
      qr.qrCodeImageDataUrl = qrCodeImageDataUrl;
    }

    return qr;
  }

  private normalizeServerCredentialPreview(input: unknown): CredentialPreview | undefined {
    if (!input || typeof input !== 'object') {
      return undefined;
    }
    const record = input as Record<string, unknown>;
    const issuerName = typeof record['issuerName'] === 'string' ? record['issuerName'] : undefined;
    const issuerId = typeof record['issuerId'] === 'string' ? record['issuerId'] : undefined;
    const rawJson = typeof record['rawJson'] === 'string' ? record['rawJson'] : undefined;

    let type: string[] | undefined;
    if (Array.isArray(record['type'])) {
      type = record['type'].filter((item): item is string => typeof item === 'string');
    }

    const subject = this.extractSubject(record['subject']);

    if (!issuerName && !issuerId && !type && !subject && !rawJson) {
      return undefined;
    }

    return {
      issuerName,
      issuerId,
      type,
      subject,
      rawJson
    };
  }

  private extractString(source: Record<string, unknown>, keys: string[]): string | undefined {
    for (const key of keys) {
      const value = source[key];
      if (typeof value === 'string' && value.trim().length > 0) {
        return value;
      }
    }
    return undefined;
  }

  private extractCredentialPreview(payload: string): CredentialPreview | undefined {
    const prefix = 'SSICredential:';
    if (!payload || !payload.startsWith(prefix)) {
      return undefined;
    }

    const encoded = payload.slice(prefix.length);
    if (!encoded) {
      return undefined;
    }

    const normalized = this.normalizeBase64(encoded);
    if (!normalized) {
      return undefined;
    }

    try {
      const jsonString = atob(normalized);
      const parsed = JSON.parse(jsonString) as Record<string, unknown>;
      const issuer = this.extractIssuer(parsed['issuer']);
      const type = Array.isArray(parsed['type'])
        ? parsed['type'].filter((item): item is string => typeof item === 'string')
        : undefined;
      const subject = this.extractSubject(parsed['credentialSubject']);

      return {
        issuerName: issuer?.name,
        issuerId: issuer?.id,
        type,
        subject,
        rawJson: jsonString
      };
    } catch (error) {
      console.error('Unable to decode credential preview', error);
      return undefined;
    }
  }

  private normalizeBase64(value: string): string | undefined {
    const sanitized = value.replace(/-/g, '+').replace(/_/g, '/');
    let padded = sanitized;
    const remainder = sanitized.length % 4;
    if (remainder === 1) {
      return undefined;
    }
    if (remainder > 0) {
      padded = sanitized + '='.repeat(4 - remainder);
    }
    return padded;
  }

  private extractIssuer(input: unknown): { id?: string; name?: string } | undefined {
    if (!input || typeof input !== 'object') {
      return undefined;
    }
    const record = input as Record<string, unknown>;
    const id = typeof record['id'] === 'string' ? record['id'] : undefined;
    const name = typeof record['name'] === 'string' ? record['name'] : undefined;
    if (!id && !name) {
      return undefined;
    }
    return { id, name };
  }

  private extractSubject(input: unknown): Record<string, unknown> | undefined {
    if (!input || typeof input !== 'object') {
      return undefined;
    }
    return input as Record<string, unknown>;
  }
}
