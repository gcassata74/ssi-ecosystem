import { Component, OnDestroy, OnInit } from '@angular/core';
import { OnboardingService } from './services/onboarding.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  constructor(private readonly onboardingService: OnboardingService) {}

  ngOnInit(): void {
    this.onboardingService.connect();
  }

  ngOnDestroy(): void {
    this.onboardingService.disconnect();
  }
}
