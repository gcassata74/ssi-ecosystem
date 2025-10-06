import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Capacitor, CapacitorHttp } from '@capacitor/core';
import { firstValueFrom } from 'rxjs';

import { CredentialService } from './credential.service';
import { DidService } from './did.service';
import { KeyService } from './key.service';

type CredentialOffer = {
  credential_issuer?: string;
  credentials?: CredentialOfferEntry[];
  credential_configuration_ids?: string[];
  grants?: Record<string, unknown>;
};

type CredentialOfferEntry = {
  format?: string;
  credential_definition?: Record<string, unknown>;
  credential_configuration_id?: string;
};

type CredentialConfigurationMetadata = {
  format?: string;
  credential_definition?: Record<string, unknown>;
  [key: string]: unknown;
};

type CredentialIssuerMetadata = {
  credential_endpoint?: string;
  token_endpoint?: string;
  credential_configurations_supported?: Record<string, CredentialConfigurationMetadata>;
  credentials_supported?: Record<string, CredentialConfigurationMetadata>;
  [key: string]: unknown;
};

type PreAuthorizedGrant = {
  preAuthorizedCode: string;
  userPinRequired?: boolean;
};

type TokenResponse = {
  accessToken: string;
  tokenType: string;
  cNonce?: string;
};

type CredentialRequest = {
  format?: string;
  credentialDefinition?: Record<string, unknown>;
  credentialConfigurationId?: string;
};

type CredentialResponse = {
  credential?: unknown;
  format?: string;
  cNonce?: string;
};

export type Oidc4vcIssuanceResult = {
  issuer: string;
  credentialCount: number;
  offer: CredentialOffer;
  credentials: unknown[];
  cNonce?: string;
};

@Injectable({ providedIn: 'root' })
export class Oidc4vcService {
  private readonly http = inject(HttpClient);
  private readonly credentialService = inject(CredentialService);
  private readonly didService = inject(DidService);
  private readonly keyService = inject(KeyService);

  isOidc4vcUri(raw: string | null | undefined): boolean {
    if (!raw) {
      return false;
    }

    try {
      const url = this.parseUrl(raw);
      if (url.protocol.toLowerCase() === 'openid-credential-offer:') {
        return true;
      }

      const params = this.extractSearchParams(raw, url);
      return params.has('credential_offer') || params.has('credential_offer_uri');
    } catch {
      return raw.startsWith('openid-credential-offer:');
    }
  }

  async acceptCredentialOfferFromUri(raw: string, options?: { pin?: string }): Promise<Oidc4vcIssuanceResult> {
    const url = this.parseUrl(raw);
    const params = this.extractSearchParams(raw, url);
    const offer = await this.resolveCredentialOffer(params);
    const issuer = this.ensureIssuer(offer);
    const metadata = await this.fetchIssuerMetadata(issuer);
    const grant = this.extractPreAuthorizedGrant(offer);

    const token = await this.redeemPreAuthorizedCode(
      this.ensureTokenEndpoint(metadata),
      grant.preAuthorizedCode,
      options?.pin,
    );

    const credentialEndpoint = this.ensureCredentialEndpoint(metadata);
    const requests = this.buildCredentialRequests(offer, metadata);
    const issued: unknown[] = [];

    let proofNonce = token.cNonce;

    if (!proofNonce) {
      throw new Error('Credential issuer token response did not include a c_nonce required for proof.');
    }

    for (const request of requests) {
      const proofJwt = await this.buildProofJwt(issuer, proofNonce);
      const response = await this.requestCredential(
        credentialEndpoint,
        token.accessToken,
        request,
        proofJwt,
      );
      const credential = this.extractCredential(response);
      if (credential !== undefined) {
        issued.push(credential);
      }

      if (response.cNonce) {
        proofNonce = response.cNonce;
      }
    }

    if (issued.length > 0) {
      await this.credentialService.addVerifiableCredentials(issued);
    }

    return {
      issuer,
      credentialCount: issued.length,
      offer,
      credentials: issued,
      cNonce: token.cNonce,
    } satisfies Oidc4vcIssuanceResult;
  }

  private async resolveCredentialOffer(params: URLSearchParams): Promise<CredentialOffer> {
    const inlineOffer = params.get('credential_offer');
    if (inlineOffer) {
      const parsed = this.parseCredentialOfferContent(inlineOffer);
      if (parsed) {
        return parsed;
      }
    }

    const offerUri = params.get('credential_offer_uri');
    if (!offerUri) {
      throw new Error('Credential offer URI does not include an offer payload.');
    }

    const resolved = await this.fetchCredentialOffer(offerUri);
    if (!resolved) {
      throw new Error('Failed to fetch credential offer from credential_offer_uri.');
    }

    return resolved;
  }

  private async fetchCredentialOffer(uri: string): Promise<CredentialOffer | undefined> {
    if (!uri) {
      return undefined;
    }

    if (uri.startsWith('data:')) {
      const payload = this.extractDataUriPayload(uri);
      if (payload) {
        return this.parseCredentialOfferContent(payload);
      }

      return undefined;
    }

    if (!this.isHttpUrl(uri)) {
      return this.parseCredentialOfferContent(uri);
    }

    try {
      const response = await this.getJson<CredentialOffer>(uri);
      if (response && typeof response === 'object') {
        return response;
      }

      return undefined;
    } catch (error) {
      console.warn('Failed to fetch credential offer from URI.', error);
      return undefined;
    }
  }

  private parseCredentialOfferContent(raw: string): CredentialOffer | undefined {
    const trimmed = raw?.trim();
    if (!trimmed) {
      return undefined;
    }

    const decoded = this.safeDecode(trimmed) ?? trimmed;
    if (decoded.startsWith('{')) {
      return this.tryParseJson(decoded) as CredentialOffer | undefined;
    }

    return undefined;
  }

  private ensureIssuer(offer: CredentialOffer): string {
    if (!offer || typeof offer !== 'object' || typeof offer.credential_issuer !== 'string') {
      throw new Error('Credential offer does not specify a credential_issuer.');
    }

    return offer.credential_issuer;
  }

  private async fetchIssuerMetadata(issuer: string): Promise<CredentialIssuerMetadata> {
    const metadataUrls = [
      issuer.endsWith('/')
        ? `${issuer}.well-known/openid-credential-issuer`
        : `${issuer}/.well-known/openid-credential-issuer`,
      issuer,
    ];

    for (const url of metadataUrls) {
      if (!this.isHttpUrl(url)) {
        continue;
      }

      try {
        const metadata = await this.getJson<CredentialIssuerMetadata>(url);
        if (metadata && typeof metadata === 'object') {
          return metadata;
        }
      } catch (error) {
        console.warn('Failed to fetch credential issuer metadata.', error);
      }
    }

    throw new Error('Credential issuer metadata could not be retrieved.');
  }

  private ensureTokenEndpoint(metadata: CredentialIssuerMetadata): string {
    const endpoint = metadata?.token_endpoint;
    if (!endpoint || typeof endpoint !== 'string') {
      throw new Error('Credential issuer metadata does not expose a token_endpoint.');
    }

    return endpoint;
  }

  private ensureCredentialEndpoint(metadata: CredentialIssuerMetadata): string {
    const endpoint = metadata?.credential_endpoint;
    if (!endpoint || typeof endpoint !== 'string') {
      throw new Error('Credential issuer metadata does not expose a credential_endpoint.');
    }

    return endpoint;
  }

  private extractPreAuthorizedGrant(offer: CredentialOffer): PreAuthorizedGrant {
    const grants = offer?.grants;
    if (!grants || typeof grants !== 'object') {
      throw new Error('Credential offer does not include grant information.');
    }

    const preAuthKey = 'urn:ietf:params:oauth:grant-type:pre-authorized_code';
    const grant = grants[preAuthKey];
    if (!grant || typeof grant !== 'object') {
      throw new Error('Credential offer does not include a pre-authorized grant.');
    }

    const preAuthorizedCode = (grant as { 'pre-authorized_code'?: unknown })['pre-authorized_code'];
    if (typeof preAuthorizedCode !== 'string' || !preAuthorizedCode) {
      throw new Error('Credential offer pre-authorized grant is missing the pre-authorized_code value.');
    }

    const userPinRequired = Boolean((grant as { user_pin_required?: unknown })['user_pin_required']);

    return {
      preAuthorizedCode,
      userPinRequired,
    } satisfies PreAuthorizedGrant;
  }

  private async redeemPreAuthorizedCode(
    tokenEndpoint: string,
    code: string,
    pin?: string,
  ): Promise<TokenResponse> {
    const params = new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:pre-authorized_code',
      'pre-authorized_code': code,
    });

    if (pin) {
      params.set('user_pin', pin);
    }

    const body = params.toString();
    let raw: Record<string, unknown>;

    if (this.canUseNativeHttp()) {
      const response = await CapacitorHttp.post({
        url: tokenEndpoint,
        data: body,
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          Accept: 'application/json',
        },
      });
      raw = (typeof response.data === 'string' ? this.tryParseJson(response.data) : response.data) ?? {};
    } else {
      raw = await firstValueFrom(
        this.http.post<Record<string, unknown>>(tokenEndpoint, body, {
          headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }),
        }),
      );
    }

    const accessToken = raw?.['access_token'];
    const tokenType = raw?.['token_type'];
    const cNonce = raw?.['c_nonce'];

    if (typeof accessToken !== 'string' || !accessToken) {
      throw new Error('Credential issuer did not return an access_token.');
    }

    if (typeof tokenType !== 'string' || tokenType.toLowerCase() !== 'bearer') {
      throw new Error('Credential issuer token response is missing a Bearer token_type.');
    }

    return {
      accessToken,
      tokenType,
      cNonce: typeof cNonce === 'string' ? cNonce : undefined,
    } satisfies TokenResponse;
  }

  private buildCredentialRequests(
    offer: CredentialOffer,
    metadata: CredentialIssuerMetadata,
  ): CredentialRequest[] {
    const direct = Array.isArray(offer.credentials) ? offer.credentials : [];
    const configurationIds = Array.isArray(offer.credential_configuration_ids)
      ? offer.credential_configuration_ids
      : [];

    const requests: CredentialRequest[] = [];

    for (const entry of direct) {
      const format = typeof entry?.format === 'string' ? entry.format : undefined;
      const definition =
        entry?.credential_definition && typeof entry.credential_definition === 'object'
          ? (entry.credential_definition as Record<string, unknown>)
          : undefined;
      const configurationId =
        typeof entry?.credential_configuration_id === 'string'
          ? entry.credential_configuration_id
          : undefined;

      const enriched = this.ensureRequestFormat(
        {
          format,
          credentialDefinition: definition,
          credentialConfigurationId: configurationId,
        },
        metadata,
      );

      requests.push(enriched);
    }

    for (const id of configurationIds) {
      if (typeof id === 'string' && id) {
        const enriched = this.ensureRequestFormat({ credentialConfigurationId: id }, metadata);
        requests.push(enriched);
      }
    }

    if (requests.length === 0) {
      throw new Error('Credential offer did not specify any credentials to request.');
    }

    return requests;
  }

  private ensureRequestFormat(
    request: CredentialRequest,
    metadata: CredentialIssuerMetadata,
  ): CredentialRequest {
    if (request.format) {
      return request;
    }

    const configId = request.credentialConfigurationId;
    if (!configId) {
      return request;
    }

    const configurations = metadata?.credential_configurations_supported ?? metadata?.credentials_supported;
    const configuration = configurations?.[configId];
    const format = configuration && typeof configuration?.format === 'string' ? configuration.format : undefined;

    if (!format) {
      return request;
    }

    return {
      ...request,
      format,
    } satisfies CredentialRequest;
  }

  private async requestCredential(
    endpoint: string,
    accessToken: string,
    request: CredentialRequest,
    proofJwt: string,
  ): Promise<CredentialResponse> {
    const payload: Record<string, unknown> = {};
    if (request.format) {
      payload['format'] = request.format;
    }
    if (request.credentialDefinition) {
      payload['credential_definition'] = request.credentialDefinition;
    }
    if (request.credentialConfigurationId) {
      payload['credential_configuration_id'] = request.credentialConfigurationId;
    }
    payload['proof'] = {
      proof_type: 'jwt',
      jwt: proofJwt,
    };

    let response: Record<string, unknown> | undefined;

    if (this.canUseNativeHttp()) {
      const result = await CapacitorHttp.post({
        url: endpoint,
        data: payload,
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
      });

      response = typeof result.data === 'string' ? this.tryParseJson(result.data) : (result.data as Record<string, unknown>);
    } else {
      response = await firstValueFrom(
        this.http.post<Record<string, unknown>>(endpoint, payload, {
          headers: new HttpHeaders({
            'Content-Type': 'application/json',
            Authorization: `Bearer ${accessToken}`,
          }),
        }),
      );
    }

    return {
      credential: response?.['credential'],
      format: typeof response?.['format'] === 'string' ? (response?.['format'] as string) : undefined,
      cNonce: typeof response?.['c_nonce'] === 'string' ? (response?.['c_nonce'] as string) : undefined,
    } satisfies CredentialResponse;
  }

  private async buildProofJwt(audience: string, nonce: string): Promise<string> {
    const [did, keyPair] = await Promise.all([
      this.didService.ensureDid(),
      this.keyService.ensureKeyPair(),
    ]);

    const issuedAt = Math.floor(Date.now() / 1000);
    const payload: Record<string, unknown> = {
      iss: did.id,
      aud: audience,
      iat: issuedAt,
      nonce,
    };

    return this.createJws(payload, keyPair.privateKey);
  }

  private async createJws(payload: Record<string, unknown>, privateKey: JsonWebKey): Promise<string> {
    const header = { alg: 'ES256', typ: 'JWT' };
    const encodedHeader = this.base64UrlEncodeText(JSON.stringify(header));
    const encodedPayload = this.base64UrlEncodeText(JSON.stringify(payload));
    const signingInput = `${encodedHeader}.${encodedPayload}`;
    const signatureBytes = await this.signWithPrivateKey(signingInput, privateKey);
    const signature = this.base64UrlEncode(signatureBytes);

    return `${signingInput}.${signature}`;
  }

  private async signWithPrivateKey(data: string, privateKey: JsonWebKey): Promise<Uint8Array> {
    const subtle = globalThis.crypto?.subtle;
    if (!subtle) {
      throw new Error('WebCrypto subtle API is not available for signing.');
    }

    const cryptoKey = await subtle.importKey(
      'jwk',
      privateKey,
      { name: 'ECDSA', namedCurve: 'P-256' },
      false,
      ['sign'],
    );

    const signature = await subtle.sign(
      { name: 'ECDSA', hash: 'SHA-256' },
      cryptoKey,
      new TextEncoder().encode(data),
    );

    return new Uint8Array(signature);
  }

  private base64UrlEncode(bytes: Uint8Array): string {
    let binary = '';
    for (const byte of bytes) {
      binary += String.fromCharCode(byte);
    }
    const base64 = globalThis.btoa(binary);
    return base64.replace(/=/gu, '').replace(/\+/gu, '-').replace(/\//gu, '_');
  }

  private base64UrlEncodeText(value: string): string {
    return this.base64UrlEncode(new TextEncoder().encode(value));
  }

  private extractCredential(response: CredentialResponse): unknown {
    if (!response || typeof response !== 'object') {
      return undefined;
    }

    if (!('credential' in response)) {
      throw new Error('Credential issuer response did not include a credential.');
    }

    return response.credential;
  }

  private parseUrl(raw: string): URL {
    const trimmed = raw?.trim();
    if (!trimmed) {
      throw new Error('Credential offer QR code did not contain data.');
    }

    try {
      return new URL(trimmed);
    } catch (error) {
      if (trimmed.startsWith('openid-credential-offer:')) {
        const normalized = trimmed.startsWith('openid-credential-offer://')
          ? trimmed
          : trimmed.replace('openid-credential-offer:', 'openid-credential-offer://');

        return new URL(normalized);
      }

      throw new Error('Credential offer QR code is not a valid URI.');
    }
  }

  private extractSearchParams(raw: string, url: URL): URLSearchParams {
    if (this.hasSearchParams(url.searchParams)) {
      return url.searchParams;
    }

    const normalizedPath = url.pathname.startsWith('/') ? url.pathname.substring(1) : url.pathname;
    const candidates = [url.host, normalizedPath];
    for (const candidate of candidates) {
      if (!candidate || !candidate.includes('=')) {
        continue;
      }
      const params = new URLSearchParams(candidate);
      if (this.hasSearchParams(params)) {
        return params;
      }
    }

    const withoutFragment = raw.split('#', 1)[0] ?? raw;
    const queryIndex = withoutFragment.indexOf('?');
    if (queryIndex > -1) {
      const query = withoutFragment.substring(queryIndex + 1);
      const params = new URLSearchParams(query);
      if (this.hasSearchParams(params)) {
        return params;
      }
    }

    const schemeIndex = withoutFragment.indexOf(':');
    if (schemeIndex > -1) {
      let remainder = withoutFragment.substring(schemeIndex + 1);
      remainder = remainder.startsWith('//') ? remainder.substring(2) : remainder;
      if (remainder.includes('=')) {
        const params = new URLSearchParams(remainder);
        if (this.hasSearchParams(params)) {
          return params;
        }
      }
    }

    return new URLSearchParams();
  }

  private hasSearchParams(params: URLSearchParams): boolean {
    return params.toString().length > 0;
  }

  private canUseNativeHttp(): boolean {
    try {
      return Capacitor.isNativePlatform();
    } catch {
      return false;
    }
  }

  private async getJson<T>(url: string): Promise<T> {
    if (this.canUseNativeHttp()) {
      const response = await CapacitorHttp.get({
        url,
        responseType: 'json',
        headers: {
          Accept: 'application/json',
        },
      });

      if (typeof response.data === 'string') {
        return JSON.parse(response.data) as T;
      }

      return response.data as T;
    }

    return firstValueFrom(this.http.get<T>(url));
  }

  private safeDecode(value: string): string | undefined {
    try {
      return decodeURIComponent(value);
    } catch {
      return undefined;
    }
  }

  private extractDataUriPayload(uri: string): string | undefined {
    const separator = uri.indexOf(',');
    if (separator === -1) {
      return undefined;
    }

    const metadata = uri.substring(5, separator);
    const data = uri.substring(separator + 1);
    const isBase64 = /;base64$/u.test(metadata) || metadata.includes(';base64;');

    if (isBase64) {
      try {
        return globalThis.atob?.(data);
      } catch (error) {
        console.warn('Failed to decode base64 credential offer.', error);
        return undefined;
      }
    }

    return this.safeDecode(data) ?? data;
  }

  private tryParseJson(text: string): Record<string, unknown> | undefined {
    try {
      const parsed = JSON.parse(text);
      return typeof parsed === 'object' && parsed ? (parsed as Record<string, unknown>) : undefined;
    } catch (error) {
      console.warn('Failed to parse JSON content.', error);
      return undefined;
    }
  }

  private isHttpUrl(candidate: string): boolean {
    try {
      const parsed = new URL(candidate);
      return parsed.protocol === 'https:' || parsed.protocol === 'http:';
    } catch {
      return false;
    }
  }
}
