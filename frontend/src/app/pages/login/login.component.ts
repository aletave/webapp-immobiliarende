import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';

/** Login: form → AuthService → redirect per ruolo. */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** Errore API login o null. */
  errorMessage: string | null = null;

  /** Dopo register con ?registered= */
  registrationSuccess = false;

  /** Mostra il testo della password in chiaro (toggle occhio). */
  passwordVisible = false;

  /** Form con validazioni simili al backend. */
  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  /** Query registered per messaggio post-registrazione. */
  ngOnInit(): void {
    const registered = this.route.snapshot.queryParamMap.get('registered');
    this.registrationSuccess = registered === 'true' || registered === '1';
  }

  /** Submit → login → dashboard in base al ruolo nel token. */
  onSubmit(): void {
    this.errorMessage = null;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { email, password } = this.form.getRawValue();

    this.authService.login(email, password).subscribe({
      next: () => {
        const ruolo =
          this.authService.getRuoloFromToken() ?? this.authService.getUser()?.ruolo ?? '';

        if (ruolo === 'ADMIN') {
          void this.router.navigateByUrl('/dashboard/admin');
          return;
        }
        if (ruolo === 'VENDITORE') {
          void this.router.navigateByUrl('/dashboard/venditore');
          return;
        }
        void this.router.navigateByUrl('/home');
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse) {
          if (err.status === 401) {
            this.errorMessage = 'Credenziali non valide';
            return;
          }
          if (err.status === 403) {
            this.errorMessage = 'Sei stato bannato dall’amministratore.';
            return;
          }
        }
        this.errorMessage = 'Si è verificato un errore. Riprova.';
      },
    });
  }

  /** Mostra errore sotto il campo quando conviene. */
  showFieldError(field: 'email' | 'password'): boolean {
    const c = this.form.controls[field];
    return c.invalid && (c.dirty || c.touched);
  }
}
