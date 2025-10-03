import { Component, OnInit } from '@angular/core';
import { QrService, PocQr } from './services/qr.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  loading = true;
  error?: string;
  qr?: PocQr;

  constructor(private readonly qrService: QrService) {}

  ngOnInit(): void {
    this.loadQr();
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
}
