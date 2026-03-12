import { InjectionToken } from '@angular/core';
import type { SsiAuthClient } from '../SsiAuthClient';
import type { ProvideSsiAuthOptions } from './types';

export const SSI_AUTH_OPTIONS = new InjectionToken<ProvideSsiAuthOptions>('SSI_AUTH_OPTIONS');
export const SSI_AUTH_CLIENT = new InjectionToken<SsiAuthClient>('SSI_AUTH_CLIENT');
