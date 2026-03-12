import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Observable, from } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import type { SsiAuthService } from './service';
import type { ProvideSsiAuthOptions } from './types';

const DEFAULT_HEADER = 'Authorization';

export class SsiAuthInterceptor implements HttpInterceptor {
  constructor(private readonly auth: SsiAuthService, private readonly options: ProvideSsiAuthOptions) {}

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    return from(this.auth.getAccessToken()).pipe(
      switchMap((token) => {
        if (!token) {
          return next.handle(req);
        }
        const headerName = this.options.interceptorHeader ?? DEFAULT_HEADER;
        const authReq = req.clone({
          setHeaders: {
            [headerName]: headerName.toLowerCase() === 'authorization' ? `Bearer ${token}` : token
          }
        });
        return next.handle(authReq);
      })
    );
  }
}
