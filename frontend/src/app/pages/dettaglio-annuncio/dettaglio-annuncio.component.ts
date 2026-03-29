import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, EMPTY, forkJoin, of, Subscription, TimeoutError } from 'rxjs';
import { map, switchMap, tap, timeout } from 'rxjs/operators';

import { AppuntamentoService } from '../../core/services/appuntamento.service';
import { AnnuncioService } from '../../core/services/annuncio.service';
import { AstaService } from '../../core/services/asta.service';
import { AuthService } from '../../core/services/auth.service';
import { FotoService } from '../../core/services/foto.service';
import { MessaggioService } from '../../core/services/messaggio.service';
import { RecensioneService } from '../../core/services/recensione.service';
import type { Appuntamento } from '../../models/appuntamento.model';
import type { Annuncio } from '../../models/annuncio.model';
import type { Asta } from '../../models/asta.model';
import type { Foto } from '../../models/foto.model';
import { nomeCompletoAutoreRecensione, type Recensione } from '../../models/recensione.model';
import { parseSpringErrorMessage } from '../../core/utils/http-error.util';
import { IconTrashComponent } from '../../shared/components/icon-trash/icon-trash.component';

/** Pagina annuncio: foto, dati, mappa, messaggi, asta, recensioni, social. */
@Component({
  selector: 'app-dettaglio-annuncio',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, IconTrashComponent],
  templateUrl: './dettaglio-annuncio.component.html',
  styleUrl: './dettaglio-annuncio.component.scss',
})
export class DettaglioAnnuncioComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly appuntamentoService = inject(AppuntamentoService);
  private readonly annuncioService = inject(AnnuncioService);
  private readonly fotoService = inject(FotoService);
  private readonly astaService = inject(AstaService);
  private readonly recensioneService = inject(RecensioneService);
  private readonly messaggioService = inject(MessaggioService);
  private readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly sanitizer = inject(DomSanitizer);

  private routeSub?: Subscription;

  annuncio: Annuncio | null = null;
  foto: Foto[] = [];
  recensioni: Recensione[] = [];
  /** Acquirente che ha già recensito → niente form nuova recensione. */
  haGiaRecensito = false;
  /** La tua richiesta visita su questo annuncio, se c’è. */
  mioAppuntamento: Appuntamento | null = null;
  richiestaVisitaInCorso = false;
  erroreRichiestaVisita: string | null = null;
  asta: Asta | null = null;

  /** Slide corrente nel carousel (solo Angular, niente Bootstrap carousel). */
  indiceFotoAttiva = 0;

  /** iframe maps già sanitizzato, così non lo ricalcoliamo ogni change detection. */
  mappaSafeUrl: SafeResourceUrl | null = null;

  loading = true;
  erroreCaricamento: string | null = null;
  parametroNonValido = false;

  erroreOfferta: string | null = null;
  erroreRecensione: string | null = null;

  /** Errore dopo POST messaggio (serve essere acquirente loggato). */
  erroreMessaggio: string | null = null;

  /** Messaggio inviato con successo. */
  messaggioInviato = false;

  /** Offerta in asta (solo acquirente se c’è asta). */
  readonly offertaForm = this.fb.nonNullable.group({
    importo: [0, [Validators.required, Validators.min(0.01)]],
  });

  /** Nuova recensione (acquirente). */
  readonly recensioneForm = this.fb.nonNullable.group({
    testo: ['', Validators.required],
    voto: [5, [Validators.required, Validators.min(1), Validators.max(5)]],
  });

  /** Modifica della tua recensione. */
  readonly recensioneEditForm = this.fb.nonNullable.group({
    testo: ['', Validators.required],
    voto: [5, [Validators.required, Validators.min(1), Validators.max(5)]],
  });

  /** Quale recensione stai modificando in linea. */
  recensioneModificaId: number | null = null;

  /** Testo messaggio al venditore (mittente lo mette il backend dal profilo). */
  readonly messaggioForm = this.fb.nonNullable.group({
    testo: ['', Validators.required],
  });

  /** Per disegnare le stelle. */
  readonly stelleIndici = [1, 2, 3, 4, 5];

  /** Voto nel select. */
  readonly votiDisponibili = [1, 2, 3, 4, 5];

  /** Helper nome autore recensione nel template. */
  readonly nomeCompletoAutoreRecensione = nomeCompletoAutoreRecensione;

  ngOnInit(): void {
    this.routeSub = this.route.paramMap
      .pipe(
        map((params) => {
          const raw = params.get('id');
          const id = raw ? Number(raw) : NaN;
          return id;
        }),
        switchMap((id) => {
          if (Number.isNaN(id)) {
            this.parametroNonValido = true;
            this.loading = false;
            return EMPTY;
          }
          this.parametroNonValido = false;
          return this.caricaDatiStream(id);
        })
      )
      .subscribe();
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  /** Carica annuncio e collaterali; catchError sui secondari; timeout; switchMap annulla richieste vecchie su cambio id. */
  private caricaDatiStream(annuncioId: number) {
    this.loading = true;
    this.erroreCaricamento = null;
    this.annuncio = null;
    this.mappaSafeUrl = null;
    this.indiceFotoAttiva = 0;
    this.mioAppuntamento = null;
    this.erroreRichiestaVisita = null;
    const user = this.authService.getUser();
    const appuntamento$ =
      user?.ruolo === 'ACQUIRENTE'
        ? this.appuntamentoService.getMioPerAnnuncio(annuncioId)
        : of(null);
    return forkJoin({
      annuncio: this.annuncioService.getAnnuncio(annuncioId),
      foto: this.fotoService.getFotoAnnuncio(annuncioId).pipe(
        catchError(() => of<Foto[]>([]))
      ),
      recensioni: this.recensioneService.getRecensioniAnnuncio(annuncioId).pipe(
        catchError(() => of<Recensione[]>([]))
      ),
      aste: this.astaService.getAsteAnnuncio(annuncioId).pipe(
        catchError(() => of<Asta[]>([]))
      ),
      appuntamento: appuntamento$,
    }).pipe(
      timeout(20000),
      catchError((err: unknown) => {
        this.loading = false;
        if (err instanceof TimeoutError) {
          this.erroreCaricamento =
            'Timeout: il server non risponde. Avvia il backend Spring Boot su http://localhost:8080 e riprova.';
        } else {
          this.erroreCaricamento = 'Annuncio non trovato.';
        }
        return EMPTY;
      }),
      tap(({ annuncio, foto, recensioni, aste, appuntamento }) => {
        if (annuncio == null) {
          this.erroreCaricamento = 'Annuncio non trovato.';
          this.loading = false;
          return;
        }
        this.annuncio = annuncio;
        this.foto = foto;
        this.recensioni = recensioni;
        this.mioAppuntamento = appuntamento;
        this.aggiornaHaGiaRecensito();
        this.asta = aste.length > 0 ? aste[0] : null;
        this.indiceFotoAttiva = 0;
        this.mappaSafeUrl = this.buildMappaSafeUrl(annuncio);
        this.loading = false;
        this.erroreMessaggio = null;
        this.messaggioInviato = false;
        if (this.asta) {
          const suggerito = Math.max(this.asta.offertaAttuale + 1, 0.01);
          this.offertaForm.patchValue({ importo: suggerito });
        }
      })
    );
  }

  /** iframe Google Maps sanitizzato quando carichi l’annuncio. */
  private buildMappaSafeUrl(a: Annuncio): SafeResourceUrl | null {
    if (a.lat == null || a.lng == null || Number.isNaN(a.lat) || Number.isNaN(a.lng)) {
      return null;
    }
    const q = `${a.lat},${a.lng}`;
    const url = `https://maps.google.com/maps?q=${q}&z=15&output=embed`;
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  }

  /** Foto precedente. */
  fotoPrecedente(): void {
    if (this.foto.length <= 1) {
      return;
    }
    this.indiceFotoAttiva =
      (this.indiceFotoAttiva - 1 + this.foto.length) % this.foto.length;
  }

  /** Foto successiva. */
  fotoSuccessiva(): void {
    if (this.foto.length <= 1) {
      return;
    }
    this.indiceFotoAttiva = (this.indiceFotoAttiva + 1) % this.foto.length;
  }

  /** POST messaggio (serve JWT acquirente). */
  inviaMessaggio(): void {
    this.erroreMessaggio = null;
    if (!this.annuncio || this.messaggioForm.invalid) {
      this.messaggioForm.markAllAsTouched();
      return;
    }
    const { testo } = this.messaggioForm.getRawValue();
    this.messaggioService
      .inviaMessaggio({
        testo,
        annuncioId: this.annuncio.id,
      })
      .subscribe({
        next: () => {
          this.messaggioInviato = true;
          this.messaggioForm.reset({ testo: '' });
        },
        error: (err: unknown) => {
          if (err instanceof HttpErrorResponse) {
            if (err.status === 401) {
              this.erroreMessaggio = 'Sessione scaduta o non valida. Accedi di nuovo.';
              return;
            }
            if (err.status === 403) {
              this.erroreMessaggio = 'Solo gli account acquirente possono inviare messaggi da qui.';
              return;
            }
          }
          this.erroreMessaggio = 'Impossibile inviare il messaggio. Riprova.';
        },
      });
  }

  /** mailto: al venditore con oggetto che richiama id e titolo. */
  linkMailtoVenditore(): string {
    if (!this.annuncio?.emailVenditore) {
      return '#';
    }
    const subject = encodeURIComponent(
      `Annuncio ${this.annuncio.id} - ${this.annuncio.titolo}`
    );
    return `mailto:${this.annuncio.emailVenditore}?subject=${subject}`;
  }

  /** Sei acquirente loggato (offerte e recensioni). */
  isAcquirente(): boolean {
    return this.authService.getUser()?.ruolo === 'ACQUIRENTE';
  }

  /** C’è un token (per mostrare il form contatto servono anche i permessi giusti). */
  isLoggato(): boolean {
    return this.authService.isLoggedIn();
  }

  /** Aggiorna haGiaRecensito in base alla lista caricata. */
  private aggiornaHaGiaRecensito(): void {
    const u = this.authService.getUser();
    this.haGiaRecensito =
      u != null && this.recensioni.some((r) => r.acquirenteId === u.id);
  }

  /** È la tua recensione. */
  isMiaRecensione(rec: Recensione): boolean {
    const u = this.authService.getUser();
    return this.isAcquirente() && u != null && rec.acquirenteId === u.id;
  }

  /** Link completo di questa scheda (share). */
  private urlCondivisioneAnnuncio(): string {
    if (!this.annuncio) {
      return '';
    }
    if (typeof window !== 'undefined' && window.location?.href) {
      return window.location.href.split('#')[0];
    }
    return `http://localhost:4200/annunci/${this.annuncio.id}`;
  }

  /** Condividi su Facebook. */
  condividiSuFacebook(): void {
    if (!this.annuncio) {
      return;
    }
    const u = encodeURIComponent(this.urlCondivisioneAnnuncio());
    window.open(
      `https://www.facebook.com/sharer/sharer.php?u=${u}`,
      '_blank',
      'noopener,noreferrer'
    );
  }

  /** Condividi su WhatsApp con titolo + link. */
  condividiSuWhatsApp(): void {
    if (!this.annuncio) {
      return;
    }
    const url = this.urlCondivisioneAnnuncio();
    const text = encodeURIComponent(`${this.annuncio.titolo}\n${url}`);
    window.open(`https://wa.me/?text=${text}`, '_blank', 'noopener,noreferrer');
  }

  /** POST offerta e aggiorna l’asta locale. */
  inviaOfferta(): void {
    this.erroreOfferta = null;
    if (!this.asta || this.offertaForm.invalid) {
      this.offertaForm.markAllAsTouched();
      return;
    }
    const importo = Number(this.offertaForm.getRawValue().importo);
    this.astaService.faiOfferta(this.asta.id, importo).subscribe({
      next: (a) => {
        this.asta = a;
        this.offertaForm.patchValue({
          importo: Math.max(a.offertaAttuale + 1, 0.01),
        });
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 400) {
          this.erroreOfferta =
            'Importo non valido: deve essere superiore all’offerta attuale.';
          return;
        }
        if (err instanceof HttpErrorResponse && err.status === 409) {
          const msg = parseSpringErrorMessage(err);
          this.erroreOfferta =
            msg ??
            "Un altro utente ha appena fatto un'offerta, ricarica la pagina e riprova";
          return;
        }
        this.erroreOfferta = 'Impossibile inviare l’offerta.';
      },
    });
  }

  /** Nuova recensione poi ricarica la lista. */
  inviaRecensione(): void {
    this.erroreRecensione = null;
    if (!this.annuncio || this.recensioneForm.invalid) {
      this.recensioneForm.markAllAsTouched();
      return;
    }
    const { testo, voto } = this.recensioneForm.getRawValue();
    this.recensioneService
      .creaRecensione(testo, voto, this.annuncio.id)
      .subscribe({
        next: () => {
          this.haGiaRecensito = true;
          this.ricaricaRecensioni();
          this.recensioneForm.reset({ testo: '', voto: 5 });
        },
        error: (err: unknown) => {
          if (err instanceof HttpErrorResponse && err.status === 403) {
            const body = err.error as { message?: string } | null;
            this.erroreRecensione =
              typeof body?.message === 'string'
                ? body.message
                : 'Non puoi recensire senza una visita completata dal venditore.';
            return;
          }
          if (err instanceof HttpErrorResponse && err.status === 409) {
            const body = err.error as { message?: string } | null;
            this.erroreRecensione =
              typeof body?.message === 'string'
                ? body.message
                : 'Hai già lasciato una recensione per questo annuncio.';
            return;
          }
          this.erroreRecensione = 'Impossibile inviare la recensione.';
        },
      });
  }

  /** Sei il venditore di questo annuncio (niente visita sul tuo stesso annuncio). */
  isMioAnnuncio(): boolean {
    const u = this.authService.getUser();
    if (!this.annuncio || !u) {
      return false;
    }
    return this.annuncio.venditoreId === u.id;
  }

  /** Venditore ha segnato visita completata → puoi recensire. */
  visitaCompletata(): boolean {
    return this.mioAppuntamento?.stato === 'COMPLETATO';
  }

  /** Form nuova recensione: visita ok e non hai già recensito. */
  possoMostrareFormRecensione(): boolean {
    return (
      this.isAcquirente() &&
      !this.haGiaRecensito &&
      this.visitaCompletata()
    );
  }

  /** Messaggio se non puoi ancora recensire. */
  messaggioBloccoRecensione(): string {
    if (this.isMioAnnuncio()) {
      return 'Non puoi recensire un annuncio di cui sei il venditore.';
    }
    if (this.mioAppuntamento?.stato === 'PENDING') {
      return 'Quando il venditore segnerà la visita come completata, potrai lasciare una recensione qui.';
    }
    if (this.mioAppuntamento?.stato === 'RIFIUTATO') {
      return 'La richiesta di visita è stata rifiutata. Puoi richiederne una nuova sopra; la recensione resta disponibile solo dopo una visita completata.';
    }
    return 'Per recensire serve prima una visita completata: usa «Richiedi visita» sopra e attendi la conferma del venditore.';
  }

  /** Richiedi visita (resta in attesa finché il venditore non chiude dalla dashboard). */
  richiediVisita(): void {
    this.erroreRichiestaVisita = null;
    if (!this.annuncio || this.isMioAnnuncio()) {
      return;
    }
    this.richiestaVisitaInCorso = true;
    this.appuntamentoService.richiediVisita(this.annuncio.id).subscribe({
      next: (a) => {
        this.mioAppuntamento = a;
        this.richiestaVisitaInCorso = false;
      },
      error: (err: unknown) => {
        this.richiestaVisitaInCorso = false;
        const msg = parseSpringErrorMessage(err);
        if (msg) {
          this.erroreRichiestaVisita = msg;
          return;
        }
        if (err instanceof HttpErrorResponse) {
          if (err.status === 409) {
            this.erroreRichiestaVisita =
              'Hai già una richiesta in attesa per questo annuncio.';
            return;
          }
          if (err.status === 400) {
            this.erroreRichiestaVisita = 'Richiesta non valida.';
            return;
          }
          if (err.status === 404) {
            this.erroreRichiestaVisita = 'Annuncio non trovato.';
            return;
          }
        }
        this.erroreRichiestaVisita = 'Impossibile inviare la richiesta di visita.';
      },
    });
  }

  iniziaModificaRecensione(rec: Recensione): void {
    this.erroreRecensione = null;
    this.recensioneModificaId = rec.id;
    this.recensioneEditForm.reset({
      testo: rec.testo,
      voto: rec.voto,
    });
  }

  annullaModificaRecensione(): void {
    this.recensioneModificaId = null;
    this.erroreRecensione = null;
  }

  salvaModificaRecensione(): void {
    this.erroreRecensione = null;
    if (this.recensioneModificaId == null || this.recensioneEditForm.invalid) {
      this.recensioneEditForm.markAllAsTouched();
      return;
    }
    const { testo, voto } = this.recensioneEditForm.getRawValue();
    this.recensioneService
      .aggiornaRecensione(this.recensioneModificaId, testo, voto)
      .subscribe({
        next: () => {
          this.recensioneModificaId = null;
          this.ricaricaRecensioni();
        },
        error: () => {
          this.erroreRecensione = 'Impossibile aggiornare la recensione.';
        },
      });
  }

  eliminaRecensione(rec: Recensione): void {
    this.erroreRecensione = null;
    if (!confirm('Eliminare questa recensione? L’operazione non può essere annullata.')) {
      return;
    }
    this.recensioneService.eliminaRecensione(rec.id).subscribe({
      next: () => {
        if (this.recensioneModificaId === rec.id) {
          this.recensioneModificaId = null;
        }
        this.ricaricaRecensioni();
      },
      error: () => {
        this.erroreRecensione = 'Impossibile eliminare la recensione.';
      },
    });
  }

  private ricaricaRecensioni(): void {
    if (!this.annuncio) {
      return;
    }
    this.recensioneService.getRecensioniAnnuncio(this.annuncio.id).subscribe({
      next: (list) => {
        this.recensioni = list;
        this.aggiornaHaGiaRecensito();
      },
    });
  }

  /** C’è un prezzo “prima del ribasso” da barrare. */
  haPrezzoPrecedente(): boolean {
    return (
      this.annuncio != null &&
      this.annuncio.prezzoPrecedente != null &&
      this.annuncio.prezzoPrecedente !== undefined
    );
  }

  /** Ribasso con data di fine ancora nel futuro. */
  ribassoTemporaneoAttivo(): boolean {
    if (!this.annuncio) {
      return false;
    }
    const f = this.annuncio.ribassoFine;
    if (f == null || f === '') {
      return false;
    }
    const t = Date.parse(f);
    return !Number.isNaN(t) && t > Date.now();
  }

  /** Nome completo venditore per la UI. */
  nomeVenditoreCompleto(): string {
    if (!this.annuncio) {
      return '';
    }
    const n = this.annuncio.nomeVenditore?.trim() ?? '';
    const c = this.annuncio.cognomeVenditore?.trim() ?? '';
    const t = `${n} ${c}`.trim();
    return t || '—';
  }

  /** Adatta stringa ISO o array “Jackson” per il DatePipe. */
  dataPerPipe(value: string | unknown): string | number | Date | null {
    if (value == null) {
      return null;
    }
    if (typeof value === 'string') {
      return value;
    }
    if (Array.isArray(value) && value.length >= 3) {
      const [y, m, d, h = 0, min = 0] = value as number[];
      return new Date(y, m - 1, d, h, min);
    }
    return null;
  }
}
