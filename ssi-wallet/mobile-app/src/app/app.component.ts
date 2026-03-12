import { Component, inject } from '@angular/core';
import { IonApp, IonRouterOutlet } from '@ionic/angular/standalone';

import { KeyService } from './services/key.service';
import { DidService } from './services/did.service';
import { BiometricAuthService } from './services/biometric-auth.service';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  imports: [IonApp, IonRouterOutlet],
})
export class AppComponent {
  private readonly keyService = inject(KeyService);
  private readonly didService = inject(DidService);
  private readonly biometricAuthService = inject(BiometricAuthService);

  constructor() {
    void this.initialize();
  }

  private async initialize(): Promise<void> {
    const unlocked = await this.biometricAuthService.ensureUnlocked();
    if (!unlocked) {
      return;
    }

    try {
      await this.keyService.ensureKeyPair();
      await this.didService.ensureDid();
    } catch (error) {
      console.error('Failed to initialize wallet identity.', error);
    }
  }
}
