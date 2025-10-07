import { CommonModule } from '@angular/common';
import { Component, ElementRef, NgZone, OnDestroy, ViewChild, inject } from '@angular/core';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
  IonContent,
  IonHeader,
  IonIcon,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';
import { ExploreContainerComponent } from '../explore-container/explore-container.component';
import { Capacitor } from '@capacitor/core';
import { Camera, CameraPermissionState } from '@capacitor/camera';
import { Oidc4vcService } from '../services/oidc4vc.service';
import { Oidc4vpService } from '../services/oidc4vp.service';
import { CredentialService } from '../services/credential.service';

@Component({
  selector: 'app-tab1',
  templateUrl: 'tab1.page.html',
  styleUrls: ['tab1.page.scss'],
  imports: [
    CommonModule,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    IonButton,
    IonIcon,
    IonSpinner,
    IonCard,
    IonCardHeader,
    IonCardTitle,
    IonCardContent,
    IonText,
  ],
})
export class Tab1Page implements OnDestroy {
  @ViewChild('videoElement') private videoElement?: ElementRef<HTMLVideoElement>;

  isScanning = false;
  scanResult?: string;
  vpSubmissionMessage?: string;
  vcIssuanceMessage?: string;
  scanError?: string;
  isProcessingPresentation = false;
  isProcessingIssuance = false;

  private activeStream?: MediaStream;
  private detector?: BarcodeDetectorLike;
  private scanAnimationId?: number;
  private readonly ngZone = inject(NgZone);
  private readonly oidc4vpService = inject(Oidc4vpService);
  private readonly oidc4vcService = inject(Oidc4vcService);
  private readonly credentialService = inject(CredentialService);

  async startQrScan(): Promise<void> {
    if (this.isScanning) {
      return;
    }

    this.scanError = undefined;
    this.vpSubmissionMessage = undefined;
    this.vcIssuanceMessage = undefined;
    this.isProcessingPresentation = false;
    this.isProcessingIssuance = false;
    this.isScanning = true;

    try {
      await this.ensureCameraPermission();

      const detectorCtor = (window as typeof window & { BarcodeDetector?: BarcodeDetectorCtor }).BarcodeDetector;
      if (!detectorCtor) {
        throw new Error('BarcodeDetector API is not available on this device.');
      }

      this.detector = new detectorCtor({ formats: ['qr_code'] });

      if (!navigator.mediaDevices?.getUserMedia) {
        throw new Error('Camera access is not supported in this browser.');
      }

      await this.waitForVideoElement();
      const video = this.videoElement?.nativeElement;

      if (!video) {
        throw new Error('Camera preview is not ready.');
      }

      this.activeStream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: 'environment' },
        },
      });

      video.srcObject = this.activeStream;
      await video.play();

      this.scheduleDetection(video);
    } catch (error) {
      this.ngZone.run(() => {
        this.scanError = this.stringifyError(error);
      });
      this.stopQrScan();
    }
  }

  /*public testScan(fake?: string): void {
    const demo = fake ?? 'openid://?client_id=http%3A%2F%2Fverifier.izylife.com%3A9090%2Foidc4vp%2Fresponses&client_id_scheme=redirect_uri&request_uri=https%3A%2F%2Fmica-semicivilized-heavily.ngrok-free.dev%2Foidc4vp%2Frequests%2Fdb8f329a-8c5d-4483-978c-128091e7a222&response_type=vp_token&response_mode=direct_post&scope=openid&nonce=nonce-db8f329a&state=db8f329a-8c5d-4483-978c-128091e7a222&presentation_definition_uri=https%3A%2F%2Fmica-semicivilized-heavily.ngrok-free.dev%2Fdefinitions%2Fstaff-credential.json';
    this.ngZone.run(() => {
      this.stopQrScan();                 // ensure no camera loop is running
      void this.processScanResult(demo); // reuse the real handler
    });
  }*/


  stopQrScan(): void {
    if (this.scanAnimationId !== undefined) {
      cancelAnimationFrame(this.scanAnimationId);
      this.scanAnimationId = undefined;
    }

    if (this.activeStream) {
      this.activeStream.getTracks().forEach((track) => track.stop());
      this.activeStream = undefined;
    }

    const video = this.videoElement?.nativeElement;
    if (video) {
      video.pause();
      video.srcObject = null;
    }

    this.isScanning = false;
    this.detector = undefined;
  }

  clearScan(): void {
    this.scanResult = undefined;
    this.vpSubmissionMessage = undefined;
    this.vcIssuanceMessage = undefined;
    this.scanError = undefined;
    this.isProcessingPresentation = false;
    this.isProcessingIssuance = false;
  }

  dismissError(): void {
    this.scanError = undefined;
  }

  async clearStoredCredentials(): Promise<void> {
    try {
      await this.credentialService.replaceVerifiableCredentials([]);
      this.scanError = undefined;
      this.vpSubmissionMessage = undefined;
      this.vcIssuanceMessage = 'Cleared stored credentials for testing.';
    } catch (error) {
      this.scanError = this.stringifyError(error);
    }
  }

  ngOnDestroy(): void {
    this.stopQrScan();
  }

  private scheduleDetection(video: HTMLVideoElement): void {
    const tick = async () => {
      if (!this.isScanning || !this.detector) {
        return;
      }

      try {
        const barcodes = await this.detector.detect(video);
        if (barcodes.length > 0) {
          const value = barcodes[0]?.rawValue ?? '';
          this.ngZone.run(() => {
            this.stopQrScan();
            void this.processScanResult(value);
          });
          return;
        }
      } catch (error) {
        this.ngZone.run(() => {
          this.scanError = this.stringifyError(error);
          this.stopQrScan();
        });
        return;
      }

      this.scanAnimationId = requestAnimationFrame(tick);
    };

    this.scanAnimationId = requestAnimationFrame(tick);
  }

  private async waitForVideoElement(): Promise<void> {
    const maxAttempts = 10;
    let attempts = 0;

    while (!this.videoElement?.nativeElement && attempts < maxAttempts) {
      await new Promise((resolve) => setTimeout(resolve, 16));
      attempts += 1;
    }
  }

  private async processScanResult(data: string): Promise<void> {
    this.scanResult = data;
    this.scanError = undefined;
    this.vpSubmissionMessage = undefined;
    this.vcIssuanceMessage = undefined;

    if (!data) {
      this.scanError = 'QR code did not contain data.';
      return;
    }

    if (this.oidc4vpService.isOidc4vpUri(data)) {
      await this.handlePresentationSubmission(data);
      return;
    }

    if (this.oidc4vcService.isOidc4vcUri(data)) {
      await this.handleCredentialOffer(data);
      return;
    }
  }

  private async handlePresentationSubmission(uri: string): Promise<void> {
    this.isProcessingPresentation = true;
    this.scanError = undefined;
    try {
      const result = await this.oidc4vpService.submitPresentationFromUri(uri);
      this.vpSubmissionMessage = `Submitted VP to ${result.responseUri} with ${result.credentialCount} credential(s).`;
    } catch (error) {
      this.scanError = this.stringifyError(error);
    } finally {
      this.isProcessingPresentation = false;
    }
  }

  private async handleCredentialOffer(uri: string): Promise<void> {
    this.isProcessingIssuance = true;
    this.scanError = undefined;
    try {
      const result = await this.oidc4vcService.acceptCredentialOfferFromUri(uri);
      this.vcIssuanceMessage = `Stored ${result.credentialCount} credential(s) from ${result.issuer}.`;
    } catch (error) {
      this.scanError = this.stringifyError(error);
    } finally {
      this.isProcessingIssuance = false;
    }
  }

  private stringifyError(error: unknown): string {
    if (!error) {
      return 'Unknown scanner error.';
    }

    if (error instanceof Error) {
      return error.message;
    }

    return String(error);
  }

  private async ensureCameraPermission(): Promise<void> {
    if (Capacitor.getPlatform() === 'web') {
      return;
    }

    const status = await Camera.checkPermissions();
    if (this.isPermissionGranted(status)) {
      return;
    }

    const request = await Camera.requestPermissions({ permissions: ['camera'] });
    if (!this.isPermissionGranted(request)) {
      throw new Error('Camera permission denied on device. Enable it in Settings to scan codes.');
    }
  }

  private isPermissionGranted(permissions: { camera?: CameraPermissionState }): boolean {
    const state = permissions.camera;
    return state === 'granted' || state === 'limited';
  }
}

interface BarcodeDetectorOptions {
  formats?: string[];
}
type BarcodeDetectorCtor = new (options?: BarcodeDetectorOptions) => BarcodeDetectorLike;

interface BarcodeDetectorLike {
  detect(source: CanvasImageSource): Promise<BarcodeDetectorResult[]>;
}

interface BarcodeDetectorResult {
  rawValue?: string;
  format?: string;
}
