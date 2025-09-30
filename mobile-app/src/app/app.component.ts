import { Component, inject } from '@angular/core';
import { IonApp, IonRouterOutlet } from '@ionic/angular/standalone';

import { KeyService } from './services/key.service';
import { DidService } from './services/did.service';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  imports: [IonApp, IonRouterOutlet],
})
export class AppComponent {
  private readonly keyService = inject(KeyService);
  private readonly didService = inject(DidService);

  constructor() {
    void this.initialize();
  }

  private async initialize(): Promise<void> {
    try {
      await this.keyService.ensureKeyPair();
      await this.didService.ensureDid();
    } catch (error) {
      console.error('Failed to initialize wallet identity.', error);
    }
  }
}
