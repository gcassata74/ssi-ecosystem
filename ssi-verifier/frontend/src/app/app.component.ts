import { Component } from '@angular/core';
import { VerificationService } from './services/verification.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  constructor(private readonly verificationService: VerificationService) {
    this.verificationService.connect();
  }
}
