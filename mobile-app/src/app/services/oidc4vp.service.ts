import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { CredentialService } from './credential.service';
import { DidService, StoredDid } from './did.service';
import { KeyService, StoredKeyPair } from './key.service';

type ParsedOidc4vpRequest = {
  clientId?: string;
  clientIdScheme?: string;
  requestUri?: string;
  responseUri: string;
  responseMode?: string;
  state?: string;
  nonce?: string;
  presentationDefinition?: unknown;
};

type Proof = {
  type: string;
  created: string;
  proofPurpose: string;
  verificationMethod: string;
  jws: string;
};

type VerifiablePresentation = {
  '@context': string[];
  type: string[];
  holder?: string;
  verifiableCredential?: unknown[];
  holderDidDocumentBase58: string;
  proof: Proof;
};

export type Oidc4vpSubmissionResult = {
  responseUri: string;
  credentialCount: number;
  request: ParsedOidc4vpRequest;
};

@Injectable({ providedIn: 'root' })
export class Oidc4vpService {
  private readonly http = inject(HttpClient);
  private readonly didService = inject(DidService);
  private readonly credentialService = inject(CredentialService);
  private readonly keyService = inject(KeyService);

  isOidc4vpUri(raw: string | null | undefined): boolean {
    if (!raw) {
      return false;
    }

    try {
      const url = new URL(raw);
      const protocol = url.protocol.toLowerCase();
      if (protocol === 'openid4vp:' || protocol === 'openid-vc:' || protocol === 'openid:') {
        return true;
      }

      const responseType = url.searchParams.get('response_type');
      if (responseType?.split(/[\s+]/).includes('vp_token')) {
        return true;
      }

      if (url.searchParams.has('request_uri')) {
        return true;
      }

      return false;
    } catch {
      return false;
    }
  }

  async submitPresentationFromUri(raw: string): Promise<Oidc4vpSubmissionResult> {
    const parsed = await this.parseRequest(raw);
    const did = await this.didService.ensureDid();
    const keyPair = await this.keyService.ensureKeyPair();
    const credentials = await this.credentialService.listVerifiableCredentials();

    const presentation = await this.buildPresentation(did, keyPair, credentials);
    const payload = this.buildResponsePayload(parsed, presentation, credentials.length);

    await firstValueFrom(
      this.http.post(parsed.responseUri, payload, {
        headers: new HttpHeaders({ 'Content-Type': 'application/json' }),
      }),
    );

    return {
      responseUri: parsed.responseUri,
      credentialCount: credentials.length,
      request: parsed,
    } satisfies Oidc4vpSubmissionResult;
  }

  private async parseRequest(raw: string): Promise<ParsedOidc4vpRequest> {
    const url = this.parseUrl(raw);

    const requestUri = url.searchParams.get('request_uri') ?? undefined;
    let responseUri = url.searchParams.get('response_uri') ?? undefined;
    let clientId = url.searchParams.get('client_id') ?? undefined;
    let clientIdScheme = url.searchParams.get('client_id_scheme') ?? undefined;
    let responseMode = url.searchParams.get('response_mode') ?? undefined;
    let state = url.searchParams.get('state') ?? undefined;
    let nonce = url.searchParams.get('nonce') ?? undefined;
    let presentationDefinition: unknown;
    let presentationDefinitionUri = url.searchParams.get('presentation_definition_uri') ?? undefined;

    if (requestUri) {
      const requestObject = await this.fetchRequestObject(requestUri);
      if (requestObject) {
        responseUri ??= this.extractString(requestObject, 'response_uri');
        clientId ??= this.extractString(requestObject, 'client_id');
        clientIdScheme ??= this.extractString(requestObject, 'client_id_scheme');
        responseMode ??= this.extractString(requestObject, 'response_mode');
        state ??= this.extractString(requestObject, 'state');
        nonce ??= this.extractString(requestObject, 'nonce');
        presentationDefinition = this.extractPresentationDefinition(requestObject);
        presentationDefinitionUri ??=
          this.extractString(requestObject, 'presentation_definition_uri') ?? presentationDefinitionUri;
      }
    }

    if (!presentationDefinition && presentationDefinitionUri) {
      presentationDefinition = await this.fetchPresentationDefinition(presentationDefinitionUri);
    }

    if (!responseUri && responseMode === 'direct_post' && clientId && this.isHttpUrl(clientId)) {
      responseUri = clientId;
    }

    if (!responseUri) {
      throw new Error('OIDC4VP request does not include a response_uri.');
    }

    return {
      clientId,
      clientIdScheme,
      requestUri,
      responseUri,
      responseMode,
      state,
      nonce,
      presentationDefinition,
    } satisfies ParsedOidc4vpRequest;
  }

  private async fetchRequestObject(requestUri: string): Promise<Record<string, unknown> | undefined> {
    const inline = this.extractInlineRequestObject(requestUri);
    if (inline) {
      return inline;
    }

    if (!this.isHttpUrl(requestUri)) {
      return undefined;
    }

    try {
      const raw = await firstValueFrom(
        this.http.get(requestUri, { responseType: 'text' as unknown as 'json' }),
      );

      return this.parseRequestObjectContent(raw);
    } catch (error) {
      console.warn('Failed to fetch OIDC4VP request object.', error);
      return undefined;
    }
  }

  private extractInlineRequestObject(requestUri: string): Record<string, unknown> | undefined {
    const trimmed = requestUri?.trim();
    if (!trimmed) {
      return undefined;
    }

    const direct = this.parseRequestObjectContent(trimmed);
    if (direct) {
      return direct;
    }

    const decoded = this.safeDecodeComponent(trimmed);
    if (decoded) {
      const decodedContent = this.parseRequestObjectContent(decoded.trim());
      if (decodedContent) {
        return decodedContent;
      }
    }

    if (trimmed.startsWith('data:')) {
      const separatorIndex = trimmed.indexOf(',');
      if (separatorIndex > -1) {
        const metadata = trimmed.substring(5, separatorIndex);
        const data = trimmed.substring(separatorIndex + 1);
        const isBase64 = /;base64$/u.test(metadata) || metadata.includes(';base64;');
        const payload = isBase64 ? this.decodeBase64(data) : this.safeDecodeComponent(data) ?? data;
        if (payload) {
          const parsed = this.parseRequestObjectContent(payload.trim());
          if (parsed) {
            return parsed;
          }
        }
      }
    }

    return undefined;
  }

  private parseRequestObjectContent(raw: unknown): Record<string, unknown> | undefined {
    if (!raw) {
      return undefined;
    }

    const text = String(raw).trim();
    if (!text) {
      return undefined;
    }

    if (text.startsWith('{')) {
      return this.tryParseJson(text);
    }

    if (this.isJwt(text)) {
      const [, payload] = text.split('.');
      const decoded = this.base64UrlDecode(payload);
      return this.tryParseJson(decoded);
    }

    return undefined;
  }

  private safeDecodeComponent(value: string): string | undefined {
    try {
      return decodeURIComponent(value);
    } catch {
      return undefined;
    }
  }

  private decodeBase64(value: string): string | undefined {
    if (typeof globalThis.atob !== 'function') {
      console.warn('Base64 decoding is not supported in this runtime.');
      return undefined;
    }

    try {
      return globalThis.atob(value);
    } catch (error) {
      console.warn('Failed to decode base64 content from request_uri.', error);
      return undefined;
    }
  }

  private async fetchPresentationDefinition(uri: string): Promise<unknown> {
    try {
      const definition = await firstValueFrom(this.http.get<unknown>(uri));
      if (definition && typeof definition === 'object') {
        return definition;
      }

      return undefined;
    } catch (error) {
      console.warn('Failed to fetch presentation definition.', error);
      return undefined;
    }
  }

  private async buildPresentation(
    did: StoredDid,
    keyPair: StoredKeyPair,
    credentials: unknown[],
  ): Promise<VerifiablePresentation> {
    const verificationMethod = this.extractVerificationMethodId(did);

    const basePresentation: Omit<VerifiablePresentation, 'proof'> = {
      '@context': [
        'https://www.w3.org/2018/credentials/v1',
        'https://www.w3.org/ns/did/v1',
      ],
      type: ['VerifiablePresentation'],
      holder: did.id,
      verifiableCredential: credentials,
      holderDidDocumentBase58: did.encodedDocument,
    };

    const proof = await this.createProof(basePresentation, verificationMethod, keyPair.privateKey);

    return { ...basePresentation, proof } satisfies VerifiablePresentation;
  }

  private buildResponsePayload(
    parsed: ParsedOidc4vpRequest,
    presentation: VerifiablePresentation,
    credentialCount: number,
  ): Record<string, unknown> {
    const payload: Record<string, unknown> = {
      vp_token: presentation,
      credential_count: credentialCount,
    };

    if (parsed.state) {
      payload['state'] = parsed.state;
    }

    if (parsed.nonce) {
      payload['nonce'] = parsed.nonce;
    }

    if (parsed.clientId) {
      payload['client_id'] = parsed.clientId;
    }

    if (parsed.presentationDefinition) {
      const definitionId = this.extractString(parsed.presentationDefinition, 'id') ?? 'presentation-definition';
      payload['presentation_submission'] = {
        id: `presentation-submission-${Date.now()}`,
        definition_id: definitionId,
        descriptor_map: [],
      };
    }

    return payload;
  }

  private parseUrl(raw: string): URL {
    const trimmed = raw?.trim();
    if (!trimmed) {
      throw new Error('QR code did not contain data.');
    }

    try {
      return new URL(trimmed);
    } catch (error) {
      throw new Error('QR code is not a valid URI.');
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

  private extractString(source: unknown, key: string): string | undefined {
    if (!source || typeof source !== 'object' || !key) {
      return undefined;
    }

    const value = (source as Record<string, unknown>)[key];
    if (typeof value === 'string') {
      return value;
    }

    return undefined;
  }

  private extractPresentationDefinition(requestObject: Record<string, unknown>): unknown {
    if (!requestObject) {
      return undefined;
    }

    const direct = (requestObject as { presentation_definition?: unknown }).presentation_definition;
    if (direct && typeof direct === 'object') {
      return direct;
    }

    const claims = (requestObject as { claims?: unknown }).claims;
    if (claims && typeof claims === 'object') {
      const vpToken = (claims as { vp_token?: unknown }).vp_token;
      if (vpToken && typeof vpToken === 'object') {
        const nested = (vpToken as { presentation_definition?: unknown }).presentation_definition;
        if (nested && typeof nested === 'object') {
          return nested;
        }
      }
    }

    return undefined;
  }

  private async createProof(
    presentation: Omit<VerifiablePresentation, 'proof'>,
    verificationMethod: string,
    privateKey: JsonWebKey,
  ): Promise<Proof> {
    const created = new Date().toISOString();
    const jwsPayload = JSON.stringify(presentation);
    const jws = await this.createJws(jwsPayload, privateKey);

    return {
      type: 'JsonWebSignature2020',
      created,
      proofPurpose: 'authentication',
      verificationMethod,
      jws,
    } satisfies Proof;
  }

  private async createJws(payload: string, privateKey: JsonWebKey): Promise<string> {
    const header = { alg: 'ES256', typ: 'JOSE' };
    const encodedHeader = this.base64UrlEncodeText(JSON.stringify(header));
    const encodedPayload = this.base64UrlEncodeText(payload);
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

    if (!privateKey || typeof privateKey !== 'object' || !(privateKey as { d?: unknown }).d) {
      throw new Error('Private key is missing signing material.');
    }

    const cryptoKey = await subtle.importKey(
      'jwk',
      privateKey,
      { name: 'ECDSA', namedCurve: 'P-256' },
      false,
      ['sign'],
    );

    const signature = await subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, cryptoKey, new TextEncoder().encode(data));
    return new Uint8Array(signature);
  }

  private base64UrlEncode(data: Uint8Array): string {
    let binary = '';
    for (let i = 0; i < data.length; i += 1) {
      binary += String.fromCharCode(data[i]);
    }

    if (typeof globalThis.btoa !== 'function') {
      throw new Error('Base64 encoding is not supported in this runtime.');
    }

    const base64 = globalThis.btoa(binary);

    return base64
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/u, '');
  }

  private base64UrlEncodeText(value: string): string {
    return this.base64UrlEncode(new TextEncoder().encode(value));
  }

  private extractVerificationMethodId(did: StoredDid): string {
    const [method] = did.document.verificationMethod;
    if (!method?.id) {
      throw new Error('DID document does not include a verification method identifier.');
    }

    return method.id;
  }

  private base64UrlDecode(value: string): string {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    const padding = normalized.length % 4 === 0 ? '' : '='.repeat(4 - (normalized.length % 4));
    if (typeof globalThis.atob !== 'function') {
      throw new Error('Base64 decoding is not supported in this runtime.');
    }

    return globalThis.atob(normalized + padding);
  }

  private isJwt(candidate: string): boolean {
    const parts = candidate.split('.');
    return parts.length === 3 && parts.every((part) => part.length > 0);
  }

  private tryParseJson(text: string): Record<string, unknown> | undefined {
    try {
      const parsed = JSON.parse(text);
      return typeof parsed === 'object' && parsed ? (parsed as Record<string, unknown>) : undefined;
    } catch (error) {
      console.warn('Failed to parse OIDC4VP request object as JSON.', error);
      return undefined;
    }
  }
}
