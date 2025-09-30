import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PocQr {
  label: string;
  instructions: string;
  payload: string;
  qrImageDataUrl: string;
}

@Injectable({ providedIn: 'root' })
export class QrService {
  constructor(private readonly http: HttpClient) {}

  loadPocQr(): Observable<PocQr> {
    return this.http.get<PocQr>('/api/poc/vp-request');
  }
}
