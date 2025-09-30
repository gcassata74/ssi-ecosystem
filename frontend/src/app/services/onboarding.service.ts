import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface SpidProvider {
  identifier: string;
  entityId: string;
  name: string;
  imageUrl: string;
}

export interface OnboardingState {
  step: string;
  title: string;
  description: string;
  helperText: string;
  qrCodePayload?: string;
  qrCodeImageDataUrl?: string;
  actionLabel?: string;
  actionUrl?: string;
  spidProviders?: SpidProvider[];
}

@Injectable({ providedIn: 'root' })
export class OnboardingService {
  constructor(private readonly http: HttpClient) {}

  getCurrentState(): Observable<OnboardingState> {
    return this.http.get<OnboardingState>('/api/onboarding/qr');
  }
}
