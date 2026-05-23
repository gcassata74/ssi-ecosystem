import { APP_INITIALIZER, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { RouterModule, Routes } from '@angular/router';
import { KeycloakAngularModule, KeycloakService } from 'keycloak-angular';

import { AppComponent } from './app.component';
import { IssuerPageComponent } from './issuer-page/issuer-page.component';
import { OnboardingPageComponent } from './onboarding-page/onboarding-page.component';
import { AdminConsoleComponent } from './admin/admin-console/admin-console.component';
import { AdminLoginComponent } from './admin/admin-login/admin-login.component';
import { PresentationDefinitionBuilderComponent } from './presentation-definition-builder/presentation-definition-builder.component';
import { environment } from '../environments/environment';

const routes: Routes = [
  { path: '', pathMatch: 'full', component: OnboardingPageComponent, data: { view: 'issuer' } },
  { path: 'issuer', component: IssuerPageComponent },
  { path: 'admin/login', component: AdminLoginComponent },
  { path: 'admin', component: AdminConsoleComponent },
  { path: '**', redirectTo: '' }
];

function initializeKeycloak(keycloak: KeycloakService) {
  return (): Promise<boolean | void> => {
    const params = new URLSearchParams(
      typeof window !== 'undefined' ? window.location.search : ''
    );
    const realm = params.get('realm');
    if (!realm) {
      return Promise.resolve();
    }
    return keycloak.init({
      config: {
        url: environment.keycloakUrl,
        realm,
        clientId: environment.keycloakClientId
      },
      initOptions: {
        onLoad: 'login-required',
        checkLoginIframe: false,
        pkceMethod: 'S256'
      },
      shouldAddToken: () => false
    });
  };
}

@NgModule({
  declarations: [
    AppComponent,
    OnboardingPageComponent,
    IssuerPageComponent,
    AdminConsoleComponent,
    AdminLoginComponent,
    PresentationDefinitionBuilderComponent
  ],
  imports: [
    BrowserModule,
    KeycloakAngularModule,
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule.forRoot(routes)
  ],
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: initializeKeycloak,
      deps: [KeycloakService],
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}
