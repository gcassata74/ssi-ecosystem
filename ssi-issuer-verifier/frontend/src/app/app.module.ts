import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { RouterModule, Routes } from '@angular/router';

import { AppComponent } from './app.component';
import { IssuerPageComponent } from './issuer-page/issuer-page.component';
import { OnboardingPageComponent } from './onboarding-page/onboarding-page.component';

const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'verifier' },
  { path: 'verifier', component: OnboardingPageComponent, data: { view: 'verifier' } },
  { path: 'issuer', component: IssuerPageComponent },
  { path: '**', redirectTo: 'verifier' }
];

@NgModule({
  declarations: [AppComponent, OnboardingPageComponent, IssuerPageComponent],
  imports: [BrowserModule, HttpClientModule, RouterModule.forRoot(routes)],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule {}
