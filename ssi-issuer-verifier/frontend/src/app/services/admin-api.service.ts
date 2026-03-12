import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface TenantResponse {
  id: string;
  name: string;
  contactEmail: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateTenantPayload {
  name: string;
  contactEmail: string;
  description?: string;
}

export interface ClientResponse {
  id: string;
  tenantId: string;
  clientId: string;
  name: string;
  description?: string;
  redirectUris: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface ClientSecretResponse {
  clientId: string;
  clientSecret: string;
}

export interface CreateClientPayload {
  clientId: string;
  name: string;
  description?: string;
  redirectUris: string[];
}

export interface UpdateClientPayload {
  name: string;
  description?: string;
  redirectUris: string[];
}

export interface PresentationDefinitionResponse {
  id: string;
  tenantId: string;
  clientId: string;
  definitionId: string;
  name?: string;
  description?: string;
  definitionJson: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PresentationDefinitionPayload {
  definitionId: string;
  definitionJson: string;
}

@Injectable({ providedIn: 'root' })
export class AdminApiService {
  constructor(private readonly http: HttpClient) {}

  getTenants(): Observable<TenantResponse[]> {
    return this.http.get<TenantResponse[]>('/api/admin/tenants', { withCredentials: true });
  }

  createTenant(payload: CreateTenantPayload): Observable<TenantResponse> {
    return this.http.post<TenantResponse>('/api/admin/tenants', payload, { withCredentials: true });
  }

  getClients(tenantId: string): Observable<ClientResponse[]> {
    return this.http.get<ClientResponse[]>(`/api/admin/tenants/${tenantId}/clients`, {
      withCredentials: true
    });
  }

  createClient(tenantId: string, payload: CreateClientPayload): Observable<ClientSecretResponse> {
    return this.http.post<ClientSecretResponse>(`/api/admin/tenants/${tenantId}/clients`, payload, {
      withCredentials: true
    });
  }

  updateClient(tenantId: string, clientId: string, payload: UpdateClientPayload): Observable<ClientResponse> {
    return this.http.put<ClientResponse>(`/api/admin/tenants/${tenantId}/clients/${clientId}`, payload, {
      withCredentials: true
    });
  }

  rotateSecret(tenantId: string, clientId: string): Observable<ClientSecretResponse> {
    return this.http.post<ClientSecretResponse>(
      `/api/admin/tenants/${tenantId}/clients/${clientId}/rotate-secret`,
      {},
      { withCredentials: true }
    );
  }

  deleteClient(tenantId: string, clientId: string): Observable<void> {
    return this.http.delete<void>(`/api/admin/tenants/${tenantId}/clients/${clientId}`, {
      withCredentials: true
    });
  }

  getDefinitions(tenantId: string, clientId: string): Observable<PresentationDefinitionResponse[]> {
    return this.http.get<PresentationDefinitionResponse[]>(
      `/api/admin/tenants/${tenantId}/clients/${clientId}/definitions`,
      { withCredentials: true }
    );
  }

  createDefinition(
    tenantId: string,
    clientId: string,
    payload: PresentationDefinitionPayload
  ): Observable<PresentationDefinitionResponse> {
    return this.http.post<PresentationDefinitionResponse>(
      `/api/admin/tenants/${tenantId}/clients/${clientId}/definitions`,
      payload,
      { withCredentials: true }
    );
  }

  updateDefinition(
    tenantId: string,
    clientId: string,
    definitionId: string,
    payload: PresentationDefinitionPayload
  ): Observable<PresentationDefinitionResponse> {
    return this.http.put<PresentationDefinitionResponse>(
      `/api/admin/tenants/${tenantId}/clients/${clientId}/definitions/${definitionId}`,
      payload,
      { withCredentials: true }
    );
  }

  deleteDefinition(tenantId: string, clientId: string, definitionId: string): Observable<void> {
    return this.http.delete<void>(
      `/api/admin/tenants/${tenantId}/clients/${clientId}/definitions/${definitionId}`,
      { withCredentials: true }
    );
  }
}
