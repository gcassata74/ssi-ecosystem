import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Capacitor, CapacitorHttp } from '@capacitor/core';
import { firstValueFrom } from 'rxjs';

import { CredentialService } from './credential.service';
import { DidService, StoredDid } from './did.service';
import { KeyService, StoredKeyPair } from './key.service';

type PresentationDefinition = {
  id?: string;
  input_descriptors?: InputDescriptor[];
};

type InputDescriptor = {
  id?: string;
};

type PresentationSubmission = {
  id: string;
  definition_id: string;
  descriptor_map: DescriptorMapEntry[];
};

type DescriptorMapEntry = {
  id: string;
  format: 'jwt_vp';
  path: string;
};

type ParsedOidc4vpRequest = {
  clientId?: string;
  clientIdScheme?: string;
  requestUri?: string;
  responseUri: string;
  responseMode?: string;
  state?: string;
  nonce?: string;
  presentationDefinition?: PresentationDefinition;
};

type Proof = {
  type: string;
  created: string;
  proofPurpose: string;
  verificationMethod: string;
  challenge?: string;
  domain?: string;
  jws: string;
};

type VerifiablePresentation = {
  '@context': string[];
  type: string[];
  holder?: string;
  verifiableCredential?: unknown[];
  proof: Proof;
};

export type Oidc4vpSubmissionResult = {
  responseUri: string;
  credentialCount: number;
  request: ParsedOidc4vpRequest;
  presentationSubmission: PresentationSubmission;
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
    const allCredentials = await this.credentialService.listVerifiableCredentials();
    if (!parsed.presentationDefinition) {
      throw new Error('OIDC4VP request did not include a presentation definition.');
    }

    const credentials = this.selectCredentialsForDefinition(parsed.presentationDefinition, allCredentials);
    const presentation = await this.buildPresentation(parsed, did, keyPair, credentials);
    const audience = parsed.clientId ?? parsed.responseUri;
    const vpToken = await this.buildVpTokenJwt(presentation, did, keyPair.privateKey, audience, parsed.nonce);
    const presentationSubmission = this.buildPresentationSubmission(parsed.presentationDefinition, credentials);

    if (parsed.responseMode === 'direct_post') {
      const form: Record<string, string> = {
        vp_token: vpToken,
        presentation_submission: JSON.stringify(presentationSubmission),
      };

      if (parsed.state) {
        form['state'] = parsed.state;
      }
      await this.postFormUrlEncoded(parsed.responseUri, form);
    } else {
      const payload: Record<string, unknown> = {
        vp_token: vpToken,
        presentation_submission: JSON.stringify(presentationSubmission),
      };

      if (parsed.state) {
        payload['state'] = parsed.state;
      }

      await this.postJson(parsed.responseUri, payload);
    }

    return {
      responseUri: parsed.responseUri,
      credentialCount: credentials.length,
      request: parsed,
      presentationSubmission,
    } satisfies Oidc4vpSubmissionResult;
  }

  private async parseRequest(raw: string): Promise<ParsedOidc4vpRequest> {
    const url = this.parseUrl(raw);
    const params = this.extractSearchParams(raw, url);

    const requestUri = params.get('request_uri') ?? undefined;
    let responseUri = params.get('response_uri') ?? undefined;
    let clientId = params.get('client_id') ?? undefined;
    let clientIdScheme = params.get('client_id_scheme') ?? undefined;
    let responseMode = params.get('response_mode') ?? undefined;
    let state = params.get('state') ?? undefined;
    let nonce = params.get('nonce') ?? undefined;
    let presentationDefinition: PresentationDefinition | undefined;
    const presentationDefinitionParam = params.get('presentation_definition') ?? undefined;
    let presentationDefinitionUri = params.get('presentation_definition_uri') ?? undefined;

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

    if (!presentationDefinition && presentationDefinitionParam) {
      presentationDefinition = this.extractPresentationDefinitionFromUri(presentationDefinitionParam);
    }

    if (!presentationDefinition && presentationDefinitionUri) {
      presentationDefinition = this.extractPresentationDefinitionFromUri(presentationDefinitionUri);
      if (!presentationDefinition && this.isHttpUrl(presentationDefinitionUri)) {
        presentationDefinition = await this.fetchPresentationDefinition(presentationDefinitionUri);
      }
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
      const raw = await this.getText(requestUri);
      return raw ? this.parseRequestObjectContent(raw) : undefined;
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
      try {
        const [, payload] = text.split('.');
        const decoded = this.base64UrlDecode(payload);
        return decoded ? this.tryParseJson(decoded) : undefined;
      } catch (error) {
        console.warn('Failed to decode JWT-style request object payload.', error);
        return undefined;
      }
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

  private async fetchPresentationDefinition(uri: string): Promise<PresentationDefinition | undefined> {
    try {
      const definition = await this.getJson<PresentationDefinition>(uri);
      if (definition && typeof definition === 'object') {
        return definition;
      }

      return undefined;
    } catch (error) {
      console.warn('Failed to fetch presentation definition.', error);
      return undefined;
    }
  }

  private extractPresentationDefinitionFromUri(uri: string): PresentationDefinition | undefined {
    const trimmed = uri?.trim();
    if (!trimmed) {
      return undefined;
    }

    const direct = this.parsePresentationDefinitionContent(trimmed);
    if (direct) {
      return direct;
    }

    const decoded = this.safeDecodeComponent(trimmed);
    if (decoded) {
      const decodedContent = this.parsePresentationDefinitionContent(decoded.trim());
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
          const parsed = this.parsePresentationDefinitionContent(payload.trim());
          if (parsed) {
            return parsed;
          }
        }
      }
    }

    return undefined;
  }

  private parsePresentationDefinitionContent(raw: string): PresentationDefinition | undefined {
    if (!raw) {
      return undefined;
    }

    const text = String(raw).trim();
    if (!text) {
      return undefined;
    }

    if (text.startsWith('{')) {
      const parsed = this.tryParseJson(text);
      if (parsed) {
        return this.ensurePresentationDefinition(parsed);
      }
    }

    return undefined;
  }

  private canUseNativeHttp(): boolean {
    try {
      return Capacitor.isNativePlatform();
    } catch {
      return false;
    }
  }

  private async getText(url: string): Promise<string | undefined> {
    if (this.canUseNativeHttp()) {
      const response = await CapacitorHttp.get({
        url,
        responseType: 'text',
        headers: {
          Accept: 'application/json, text/plain, */*',
        },
      });

      if (typeof response.data === 'string') {
        return response.data;
      }

      return typeof response.data === 'object' ? JSON.stringify(response.data) : String(response.data ?? '');
    }

    const raw = await firstValueFrom(
      this.http.get(url, { responseType: 'text' as unknown as 'json' }),
    );

    return raw as unknown as string;
  }

  private async getJson<T>(url: string): Promise<T | undefined> {
    if (this.canUseNativeHttp()) {
      const response = await CapacitorHttp.get({
        url,
        responseType: 'json',
        headers: {
          Accept: 'application/json',
        },
      });

      if (typeof response.data === 'string') {
        try {
          return JSON.parse(response.data) as T;
        } catch {
          return undefined;
        }
      }

      return response.data as T;
    }

    return firstValueFrom(this.http.get<T>(url));
  }

  private async postJson(url: string, payload: Record<string, unknown>): Promise<void> {
    if (this.canUseNativeHttp()) {
      await CapacitorHttp.post({
        url,
        data: payload,
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
      });
      return;
    }

    await firstValueFrom(
      this.http.post(url, payload, {
        headers: new HttpHeaders({ 'Content-Type': 'application/json' }),
      }),
    );
  }

  private async postFormUrlEncoded(url: string, params: Record<string, string>): Promise<void> {
    const body = new URLSearchParams(params).toString();

    if (this.canUseNativeHttp()) {
      await CapacitorHttp.post({
        url,
        data: body,
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
      });
      return;
    }

    await firstValueFrom(
      this.http.post(url, body, {
        headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }),
        responseType: 'text' as const,
      }),
    );
  }

  private async buildPresentation(
    parsed: ParsedOidc4vpRequest,
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
    };

    const proof = await this.createProof(
      basePresentation,
      verificationMethod,
      keyPair.privateKey,
      parsed.nonce,
      parsed.clientId ?? parsed.responseUri,
    );

    return { ...basePresentation, proof } satisfies VerifiablePresentation;
  }

  private selectCredentialsForDefinition(
    definition: PresentationDefinition,
    credentials: unknown[],
  ): any[] {
    if (!credentials || credentials.length === 0) {
      console.log('Wallet does not store any verifiable credentials for presentation.');
      return [];
    }

    const descriptors = Array.isArray(definition.input_descriptors) ? definition.input_descriptors : [];
    if (descriptors.length === 0) {
      throw new Error('Presentation definition does not specify any input descriptors.');
    }

    if (credentials.length < descriptors.length) {
      throw new Error('Wallet does not have enough credentials to satisfy the requested descriptors.');
    }

    return credentials.slice(0, descriptors.length);
  }

  private buildPresentationSubmission(
    definition: PresentationDefinition,
    credentials: unknown[],
  ): PresentationSubmission {
    if (!definition.id) {
      throw new Error('Presentation definition is missing an identifier.');
    }

    const descriptors = Array.isArray(definition.input_descriptors) ? definition.input_descriptors : [];
    if (descriptors.length === 0) {
      throw new Error('Presentation definition does not specify any input descriptors.');
    }


    const descriptorMap = descriptors.map((descriptor, index) => {
      const descriptorId = descriptor?.id;
      if (!descriptorId) {
        throw new Error(`Presentation definition descriptor at index ${index} is missing an id.`);
      }

      return {
        id: descriptorId,
        format: 'jwt_vp' as const,
        path: '$',
      } satisfies DescriptorMapEntry;
    });

    return {
      id: this.generatePresentationSubmissionId(),
      definition_id: definition.id,
      descriptor_map: descriptorMap,
    } satisfies PresentationSubmission;
  }

  private generatePresentationSubmissionId(): string {
    const generator = globalThis.crypto?.randomUUID;
    if (typeof generator === 'function') {
      return generator.call(globalThis.crypto);
    }
    return `submission-${Date.now()}-${Math.random().toString(16).slice(2)}`;
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

  private extractSearchParams(raw: string, url: URL): URLSearchParams {
    if (this.hasSearchParams(url.searchParams)) {
      return url.searchParams;
    }

    const normalizedPath = url.pathname.startsWith('/') ? url.pathname.substring(1) : url.pathname;
    const fallbackSources = [url.host, normalizedPath];
    for (const source of fallbackSources) {
      if (!source || !source.includes('=')) {
        continue;
      }
      const params = new URLSearchParams(source);
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

  private extractPresentationDefinition(requestObject: Record<string, unknown>): PresentationDefinition | undefined {
    if (!requestObject) {
      return undefined;
    }

    const direct = (requestObject as { presentation_definition?: unknown }).presentation_definition;
    if (direct && typeof direct === 'object') {
      return this.ensurePresentationDefinition(direct);
    }

    const claims = (requestObject as { claims?: unknown }).claims;
    if (claims && typeof claims === 'object') {
      const vpToken = (claims as { vp_token?: unknown }).vp_token;
      if (vpToken && typeof vpToken === 'object') {
        const nested = (vpToken as { presentation_definition?: unknown }).presentation_definition;
        if (nested && typeof nested === 'object') {
          return this.ensurePresentationDefinition(nested);
        }
      }
    }

    return undefined;
  }

  private ensurePresentationDefinition(candidate: unknown): PresentationDefinition | undefined {
    if (!candidate || typeof candidate !== 'object') {
      return undefined;
    }
    const definition = candidate as PresentationDefinition;
    if (!definition.input_descriptors || Array.isArray(definition.input_descriptors)) {
      return definition;
    }
    return {
      ...definition,
      input_descriptors: Array.isArray(definition.input_descriptors) ? definition.input_descriptors : undefined,
    } satisfies PresentationDefinition;
  }

  private async buildVpTokenJwt(
    presentation: VerifiablePresentation,
    did: StoredDid,
    privateKey: JsonWebKey,
    audience: string,
    nonce?: string,
  ): Promise<string> {
    const issuedAt = Math.floor(Date.now() / 1000);
    const lifetimeSeconds = 5 * 60;
    const payload: Record<string, unknown> = {
      iss: did.id,
      aud: audience,
      iat: issuedAt,
      exp: issuedAt + lifetimeSeconds,
      vp: presentation,
    };

    if (nonce) {
      payload['nonce'] = nonce;
    }

    return this.createJws(JSON.stringify(payload), privateKey, { typ: 'JWT' });
  }

  private async createProof(
    presentation: Omit<VerifiablePresentation, 'proof'>,
    verificationMethod: string,
    privateKey: JsonWebKey,
    challenge?: string,
    domain?: string,
  ): Promise<Proof> {
    const created = new Date().toISOString();
    const jwsPayload = JSON.stringify(presentation);
    const jws = await this.createJws(jwsPayload, privateKey);

    return {
      type: 'JsonWebSignature2020',
      created,
      proofPurpose: 'authentication',
      verificationMethod,
      challenge,
      domain,
      jws,
    } satisfies Proof;
  }

  private async createJws(
    payload: string,
    privateKey: JsonWebKey,
    headerOverrides?: Record<string, unknown>,
  ): Promise<string> {
    const header = { alg: 'ES256', typ: 'JOSE', ...headerOverrides };
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
    if (parts.length !== 3) {
      return false;
    }

    const base64UrlPattern = /^[A-Za-z0-9_-]+$/u;
    return parts.every((part) => part.length > 0 && base64UrlPattern.test(part));
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
