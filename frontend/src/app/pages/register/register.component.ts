import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';

/** Registrazione: form e AuthService.register. */
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  /** Messaggio errore server (es. email duplicata). */
  errorMessage: string | null = null;

  /** Ruoli selezionabili in fase di iscrizione (la traccia prevede anche ADMIN solo da promozione). */
  readonly ruoli = ['ACQUIRENTE', 'VENDITORE'] as const;

  /** Mostra il testo della password in chiaro (toggle occhio). */
  passwordVisible = false;

  readonly form = this.fb.nonNullable.group({
    nome: ['', Validators.required],
    cognome: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
    ruolo: ['ACQUIRENTE' as 'ACQUIRENTE' | 'VENDITORE', Validators.required],
  });

  /** Register ok → login con query registered. */
  onSubmit(): void {
    this.errorMessage = null;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();

    this.authService
      .register(v.nome, v.cognome, v.email, v.password, v.ruolo)
      .subscribe({
        next: () => {
          void this.router.navigate(['/login'], {
            queryParams: { registered: 'true' },
          });
        },
        error: (err: unknown) => {
          if (err instanceof HttpErrorResponse && err.status === 409) {
            this.errorMessage = 'Email già registrata';
            return;
          }
          this.errorMessage = 'Si è verificato un errore. Riprova.';
        },
      });
  }

  /** Errori campo per il template. */
  showFieldError(
    field: 'nome' | 'cognome' | 'email' | 'password' | 'ruolo'
  ): boolean {
    const c = this.form.controls[field];
    return c.invalid && (c.dirty || c.touched);
  }
}
