import { CanMatchFn } from '@angular/router';
import { inject } from '@angular/core';

import { BiometricAuthService } from './biometric-auth.service';

export const biometricGuard: CanMatchFn = () =>
  inject(BiometricAuthService).ensureUnlocked();
