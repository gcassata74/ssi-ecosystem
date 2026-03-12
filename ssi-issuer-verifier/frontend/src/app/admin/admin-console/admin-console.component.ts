import { Component, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { PresentationDefinitionBuilderComponent } from '../../presentation-definition-builder/presentation-definition-builder.component';
import { AdminAuthService, AdminUser } from '../../services/admin-auth.service';
import {
  AdminApiService,
  ClientResponse,
  ClientSecretResponse,
  CreateClientPayload,
  CreateTenantPayload,
  PresentationDefinitionPayload,
  PresentationDefinitionResponse,
  TenantResponse
} from '../../services/admin-api.service';

@Component({
  selector: 'app-admin-console',
  templateUrl: './admin-console.component.html',
  styleUrls: ['./admin-console.component.scss']
})
export class AdminConsoleComponent implements OnInit {
  @ViewChild(PresentationDefinitionBuilderComponent)
  definitionBuilder?: PresentationDefinitionBuilderComponent;

  currentUser?: AdminUser | null;

  tenants: TenantResponse[] = [];
  selectedTenant?: TenantResponse;

  clients: ClientResponse[] = [];
  selectedClient?: ClientResponse;

  definitions: PresentationDefinitionResponse[] = [];
  selectedDefinition?: PresentationDefinitionResponse;

  tenantForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    contactEmail: ['', [Validators.required, Validators.email]],
    description: ['']
  });

  clientForm = this.fb.nonNullable.group({
    clientId: ['', Validators.required],
    name: ['', Validators.required],
    description: [''],
    redirectUris: ['']
  });

  definitionEditorOpen = false;
  definitionMode: 'create' | 'edit' = 'create';
  editingDefinition?: PresentationDefinitionResponse;
  definitionSaving = false;

  loadingTenants = false;
  loadingClients = false;
  loadingDefinitions = false;
  clientSecret?: ClientSecretResponse;
  notification?: string;
  error?: string;

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AdminAuthService,
    private readonly api: AdminApiService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.authService.ensureSession().subscribe(user => {
      this.currentUser = user;
      if (!user) {
        this.router.navigate(['/admin/login']);
        return;
      }
      this.loadTenants();
    });
  }

  logout(): void {
    this.authService.logout().subscribe(() => {
      this.router.navigate(['/admin/login']);
    });
  }

  loadTenants(): void {
    this.loadingTenants = true;
    this.error = undefined;
    this.notification = undefined;
    this.api.getTenants().subscribe({
      next: tenants => {
        this.loadingTenants = false;
        this.tenants = tenants;
        if (!this.selectedTenant && tenants.length) {
          this.selectTenant(tenants[0]);
        }
      },
      error: () => {
        this.loadingTenants = false;
        this.error = 'Unable to load tenants.';
      }
    });
  }

  selectTenant(tenant: TenantResponse): void {
    if (this.selectedTenant?.id === tenant.id) {
      return;
    }
    this.selectedTenant = tenant;
    this.selectedClient = undefined;
    this.clients = [];
    this.definitions = [];
    this.loadClients();
  }

  createTenant(): void {
    if (this.tenantForm.invalid) {
      this.tenantForm.markAllAsTouched();
      return;
    }
    this.error = undefined;
    this.notification = undefined;
    const payload = this.tenantForm.getRawValue() as CreateTenantPayload;
    this.api.createTenant(payload).subscribe({
      next: tenant => {
        this.tenants = [...this.tenants, tenant];
        this.notification = `Tenant "${tenant.name}" created.`;
        this.tenantForm.reset();
        if (!this.selectedTenant) {
          this.selectTenant(tenant);
        }
      },
      error: () => {
        this.error = 'Unable to create tenant (duplicate name?).';
      }
    });
  }

  loadClients(): void {
    if (!this.selectedTenant) {
      return;
    }
    this.loadingClients = true;
    this.error = undefined;
    this.notification = undefined;
    this.api.getClients(this.selectedTenant.id).subscribe({
      next: clients => {
        this.loadingClients = false;
        this.clients = clients;
        if (!this.selectedClient && clients.length) {
          this.selectClient(clients[0]);
        } else if (this.selectedClient) {
          const refreshed = clients.find(client => client.id === this.selectedClient?.id);
          if (refreshed) {
            this.selectedClient = refreshed;
          }
        }
      },
      error: () => {
        this.loadingClients = false;
        this.error = 'Unable to load clients for the selected tenant.';
      }
    });
  }

  selectClient(client: ClientResponse): void {
    if (this.selectedClient?.id === client.id) {
      return;
    }
    this.selectedClient = client;
    this.clientSecret = undefined;
    this.loadDefinitions();
  }

  createClient(): void {
    if (!this.selectedTenant) {
      return;
    }
    if (this.clientForm.invalid) {
      this.clientForm.markAllAsTouched();
      return;
    }
    this.error = undefined;
    this.notification = undefined;
    const value = this.clientForm.getRawValue();
    const payload: CreateClientPayload = {
      clientId: value.clientId.trim(),
      name: value.name.trim(),
      description: value.description?.trim() || undefined,
      redirectUris: this.parseRedirectUris(value.redirectUris)
    };

    this.api.createClient(this.selectedTenant.id, payload).subscribe({
      next: secret => {
        this.clientForm.reset();
        this.clientSecret = secret;
        this.notification = `Client "${secret.clientId}" created.`;
        this.loadClients();
      },
      error: () => {
        this.error = 'Unable to create client (duplicate ID?).';
      }
    });
  }

  rotateClientSecret(): void {
    if (!this.selectedTenant || !this.selectedClient) {
      return;
    }
    this.error = undefined;
    this.notification = undefined;
    this.api.rotateSecret(this.selectedTenant.id, this.selectedClient.clientId).subscribe({
      next: secret => {
        this.clientSecret = secret;
        this.notification = `Client secret rotated for "${secret.clientId}".`;
      },
      error: () => {
        this.error = 'Unable to rotate client secret.';
      }
    });
  }

  deleteClient(client: ClientResponse): void {
    if (!this.selectedTenant) {
      return;
    }
    const confirmed = confirm(`Delete client "${client.clientId}"?`);
    if (!confirmed) {
      return;
    }
    this.error = undefined;
    this.notification = undefined;
    this.api.deleteClient(this.selectedTenant.id, client.clientId).subscribe({
      next: () => {
        this.notification = `Client "${client.clientId}" deleted.`;
        this.clients = this.clients.filter(item => item.id !== client.id);
        if (this.selectedClient?.id === client.id) {
          this.selectedClient = undefined;
          this.definitions = [];
        }
      },
      error: () => {
        this.error = 'Unable to delete client.';
      }
    });
  }

  loadDefinitions(): void {
    if (!this.selectedTenant || !this.selectedClient) {
      return;
    }
    this.loadingDefinitions = true;
    this.error = undefined;
    this.notification = undefined;
    this.api
      .getDefinitions(this.selectedTenant.id, this.selectedClient.clientId)
      .subscribe({
        next: definitions => {
          this.loadingDefinitions = false;
          this.definitions = definitions;
        },
        error: () => {
          this.loadingDefinitions = false;
          this.error = 'Unable to load presentation definitions.';
        }
      });
  }

  openCreateDefinition(): void {
    this.definitionMode = 'create';
    this.editingDefinition = undefined;
    this.definitionEditorOpen = true;
    this.definitionSaving = false;
    setTimeout(() => {
      this.definitionBuilder?.resetToSample();
    });
  }

  openEditDefinition(definition: PresentationDefinitionResponse): void {
    this.definitionMode = 'edit';
    this.editingDefinition = definition;
    this.definitionEditorOpen = true;
    this.definitionSaving = false;
    setTimeout(() => {
      this.definitionBuilder?.loadDefinitionFromJson(definition.definitionJson);
    });
  }

  closeDefinitionEditor(): void {
    this.definitionEditorOpen = false;
    this.definitionSaving = false;
    this.editingDefinition = undefined;
  }

  saveDefinition(): void {
    if (!this.selectedTenant || !this.selectedClient || !this.definitionBuilder) {
      return;
    }

    const definition = this.definitionBuilder.exportDefinition();
    if (!definition) {
      return;
    }

    const payload: PresentationDefinitionPayload = {
      definitionId: definition.id,
      definitionJson: JSON.stringify(definition, null, 2)
    };

    this.error = undefined;
    this.definitionSaving = true;

    const request = this.definitionMode === 'create'
      ? this.api.createDefinition(this.selectedTenant.id, this.selectedClient.clientId, payload)
      : this.api.updateDefinition(
          this.selectedTenant.id,
          this.selectedClient.clientId,
          this.editingDefinition!.definitionId,
          payload
        );

    request.subscribe({
      next: () => {
        this.definitionSaving = false;
        this.notification = `Presentation definition ${this.definitionMode === 'create' ? 'created' : 'updated'}.`;
        this.closeDefinitionEditor();
        this.loadDefinitions();
      },
      error: () => {
        this.definitionSaving = false;
        this.error = 'Unable to save presentation definition.';
      }
    });
  }

  deleteDefinition(definition: PresentationDefinitionResponse): void {
    if (!this.selectedTenant || !this.selectedClient) {
      return;
    }
    const confirmed = confirm(`Delete presentation definition "${definition.definitionId}"?`);
    if (!confirmed) {
      return;
    }
    this.error = undefined;
    this.api
      .deleteDefinition(this.selectedTenant.id, this.selectedClient.clientId, definition.definitionId)
      .subscribe({
        next: () => {
          this.notification = `Presentation definition "${definition.definitionId}" deleted.`;
          this.loadDefinitions();
        },
        error: () => {
          this.error = 'Unable to delete presentation definition.';
        }
      });
  }

  private parseRedirectUris(value: string | null | undefined): string[] {
    if (!value) {
      return [];
    }
    return value
      .split(/[\n,]/)
      .map(item => item.trim())
      .filter(item => item.length > 0);
  }
}
