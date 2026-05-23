import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { RouterModule, Routes } from '@angular/router';

import { AppComponent } from './app.component';
import { VerifierPageComponent } from './verifier-page/verifier-page.component';
import { PortalDemoComponent } from './portal-demo/portal-demo.component';

const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'verifier' },
  { path: 'verifier', component: VerifierPageComponent },
  { path: 'portal', component: PortalDemoComponent },
  { path: '**', redirectTo: 'verifier' }
];

@NgModule({
  declarations: [AppComponent, VerifierPageComponent, PortalDemoComponent],
  imports: [BrowserModule, HttpClientModule, RouterModule.forRoot(routes)],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule {}
