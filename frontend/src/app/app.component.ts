import { AfterViewInit, Component, OnInit } from '@angular/core';
import { QrService, PocQr } from './services/qr.service';

declare global {
  interface Window {
    SPID?: {
      init?: (config: Record<string, unknown>) => unknown;
      assetsBaseUrl?: string;
      stylesheetUrl?: string;
    };
  }
}

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, AfterViewInit {
  loading = true;
  error?: string;
  qr?: PocQr;

  constructor(private readonly qrService: QrService) {}

  ngOnInit(): void {
    this.loadQr();
  }

  ngAfterViewInit(): void {
    this.mountSpidButton();
  }

  retry(): void {
    this.loadQr();
  }

  private loadQr(): void {
    this.loading = true;
    this.error = undefined;
    this.qrService.loadPocQr().subscribe({
      next: qr => {
        this.qr = qr;
        this.loading = false;
      },
      error: () => {
        this.error = 'Unable to load the verifiable presentation request. Please try again.';
        this.loading = false;
      }
    });
  }

  private mountSpidButton(attempt = 0): void {
    const spid = window.SPID;
    if (spid && typeof spid.init === 'function') {
      try {
        spid.init({
          selector: '#spid-button',
          lang: 'it',
          url: '/login/spid?idp={{idp}}',
          method: 'GET',
          size: 'medium',
          colorScheme: 'positive',
          fluid: true,
          cornerStyle: 'rounded'
        });
      } catch (error) {
        console.error('Unable to initialise the SPID smart button', error);
      }
      return;
    }

    if (attempt > 40) {
      console.warn('Unable to locate the SPID smart button library');
      return;
    }

    setTimeout(() => this.mountSpidButton(attempt + 1), 150);
  }
}
