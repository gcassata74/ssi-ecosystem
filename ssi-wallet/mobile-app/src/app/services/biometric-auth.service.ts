import { Injectable, inject } from '@angular/core';
import { AlertController } from '@ionic/angular/standalone';
import { App } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import {
  BiometricAuthError,
  BiometryType,
  NativeBiometric,
} from '@capgo/capacitor-native-biometric';

@Injectable({ providedIn: 'root' })
export class BiometricAuthService {
  private readonly alertController = inject(AlertController);

  private isUnlocked = false;
  private ongoingVerification?: Promise<boolean>;

  async ensureUnlocked(): Promise<boolean> {
    if (!this.requiresBiometrics()) {
      this.isUnlocked = true;
      return true;
    }

    if (this.isUnlocked) {
      return true;
    }

    if (!this.ongoingVerification) {
      this.ongoingVerification = this.verifyUntilComplete().finally(() => {
        this.ongoingVerification = undefined;
      });
    }

    return this.ongoingVerification;
  }

  private requiresBiometrics(): boolean {
    if (Capacitor.getPlatform() !== 'android') {
      return false;
    }

    const isNative =
      typeof Capacitor.isNativePlatform === 'function'
        ? Capacitor.isNativePlatform()
        : true;

    return isNative;
  }

  private async verifyUntilComplete(): Promise<boolean> {
    const hasFingerprintSupport = await this.hasFingerprintCapability();
    if (!hasFingerprintSupport) {
      await this.presentAvailabilityAlert();
      await this.exitApp();
      return false;
    }

    while (true) {
      try {
        await NativeBiometric.verifyIdentity({
          reason: 'Autenticazione richiesta per aprire il wallet',
          title: 'Sblocca il wallet',
          subtitle: 'Impronta digitale',
          description: 'Posiziona il dito sul sensore per continuare.',
          negativeButtonText: 'Annulla',
          allowedBiometryTypes: [BiometryType.FINGERPRINT],
          maxAttempts: 5,
        });

        this.isUnlocked = true;
        return true;
      } catch (error) {
        const shouldRetry = await this.presentFailureAlert(error);
        if (!shouldRetry) {
          return false;
        }
      }
    }
  }

  private async hasFingerprintCapability(): Promise<boolean> {
    try {
      const result = await NativeBiometric.isAvailable({ useFallback: false });
      if (!result.isAvailable) {
        return false;
      }

      switch (result.biometryType) {
        case BiometryType.FINGERPRINT:
        case BiometryType.MULTIPLE:
          return true;
        default:
          return false;
      }
    } catch (error) {
      console.warn('Biometric availability check failed.', error);
      return false;
    }
  }

  private async presentAvailabilityAlert(): Promise<void> {
    const alert = await this.alertController.create({
      header: 'Impronta digitale necessaria',
      message:
        'Per accedere al wallet e\' necessario configurare almeno un\'impronta digitale nelle impostazioni di sistema.',
      buttons: [
        {
          text: 'Chiudi',
          role: 'cancel',
        },
      ],
      backdropDismiss: false,
    });

    await alert.present();
    await alert.onDidDismiss();
  }

  private async presentFailureAlert(error: unknown): Promise<boolean> {
    const code = this.extractErrorCode(error);
    const message = this.describeError(code, error);

    const offerRetry = this.shouldOfferRetry(code);
    const buttons = offerRetry
      ? [
          {
            text: 'Esci',
            role: 'cancel',
          },
          {
            text: 'Riprova',
            role: 'confirm',
          },
        ]
      : [
          {
            text: 'Chiudi',
            role: 'cancel',
          },
        ];

    const alert = await this.alertController.create({
      header: 'Autenticazione biometrica',
      message,
      backdropDismiss: false,
      buttons,
    });

    await alert.present();
    const { role } = await alert.onDidDismiss();

    if (role === 'confirm') {
      return true;
    }

    await this.exitApp();
    return false;
  }

  private extractErrorCode(error: unknown): number | undefined {
    if (!error || typeof error !== 'object') {
      return undefined;
    }

    const code = (error as { code?: unknown }).code;
    if (typeof code === 'number') {
      return code;
    }

    if (typeof code === 'string') {
      const parsed = Number(code);
      return Number.isFinite(parsed) ? parsed : undefined;
    }

    return undefined;
  }

  private describeError(code: number | undefined, error: unknown): string {
    switch (code) {
      case BiometricAuthError.BIOMETRICS_NOT_ENROLLED:
        return "Non ci sono impronte registrate sul dispositivo.";
      case BiometricAuthError.BIOMETRICS_UNAVAILABLE:
        return 'L\'autenticazione biometrica non e\' disponibile in questo momento.';
      case BiometricAuthError.USER_LOCKOUT:
      case BiometricAuthError.USER_TEMPORARY_LOCKOUT:
        return 'Troppi tentativi falliti. Riprova piu tardi.';
      case BiometricAuthError.USER_CANCEL:
      case BiometricAuthError.APP_CANCEL:
      case BiometricAuthError.SYSTEM_CANCEL:
        return 'Autenticazione annullata. L\'impronta digitale e\' necessaria per continuare.';
      case BiometricAuthError.AUTHENTICATION_FAILED:
        return 'Impronta non riconosciuta. Ripeti la scansione.';
      default:
        break;
    }

    const fallback = this.extractMessage(error);
    return fallback ?? 'Autenticazione biometrica non riuscita.';
  }

  private extractMessage(error: unknown): string | undefined {
    if (!error) {
      return undefined;
    }

    if (typeof error === 'string') {
      return error;
    }

    if (error instanceof Error) {
      return error.message;
    }

    if (typeof error === 'object') {
      const message = (error as { message?: unknown }).message;
      if (typeof message === 'string') {
        return message;
      }
    }

    return undefined;
  }

  private shouldOfferRetry(code: number | undefined): boolean {
    if (code === undefined) {
      return true;
    }

    switch (code) {
      case BiometricAuthError.BIOMETRICS_NOT_ENROLLED:
      case BiometricAuthError.BIOMETRICS_UNAVAILABLE:
      case BiometricAuthError.USER_LOCKOUT:
      case BiometricAuthError.USER_TEMPORARY_LOCKOUT:
        return false;
      default:
        return true;
    }
  }

  private async exitApp(): Promise<void> {
    try {
      if (this.requiresBiometrics()) {
        await App.exitApp();
      }
    } catch (error) {
      console.warn('Failed to exit app after biometric failure.', error);
    }
  }
}
