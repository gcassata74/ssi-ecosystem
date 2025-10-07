import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { RouterModule, Routes } from '@angular/router';

import { AppComponent } from './app.component';
import { OnboardingPageComponent } from './onboarding-page/onboarding-page.component';

const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'verifier' },
  { path: 'verifier', component: OnboardingPageComponent, data: { view: 'verifier' } },
  { path: 'issuer', component: OnboardingPageComponent, data: { view: 'issuer' } },
  { path: '**', redirectTo: 'verifier' }
];

@NgModule({
  declarations: [AppComponent, OnboardingPageComponent],
  imports: [BrowserModule, HttpClientModule, RouterModule.forRoot(routes)],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule {}
