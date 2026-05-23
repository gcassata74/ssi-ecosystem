import { Injectable } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';

@Injectable({ providedIn: 'root' })
export class KeycloakAuthService {

  constructor(private readonly keycloak: KeycloakService) {}

  isLoggedIn(): boolean {
    return this.keycloak.isLoggedIn();
  }

  async getToken(): Promise<string> {
    return this.keycloak.getToken();
  }

  getTokenParsed(): Record<string, unknown> | undefined {
    return this.keycloak.getKeycloakInstance()?.tokenParsed as Record<string, unknown> | undefined;
  }
}
