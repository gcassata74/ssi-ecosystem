import { Routes } from '@angular/router';

import { biometricGuard } from './services/biometric.guard';

export const routes: Routes = [
  {
    path: '',
    canMatch: [biometricGuard],
    loadChildren: () => import('./tabs/tabs.routes').then((m) => m.routes),
  },
];
