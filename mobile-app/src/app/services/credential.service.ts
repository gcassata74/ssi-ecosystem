import { Injectable } from '@angular/core';

/**
 * Placeholder credential store until real issuance is wired in.
 */
@Injectable({ providedIn: 'root' })
export class CredentialService {
  async listVerifiableCredentials(): Promise<unknown[]> {
    return [];
  }
}
