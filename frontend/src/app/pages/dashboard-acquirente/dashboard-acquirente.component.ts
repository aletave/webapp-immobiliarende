import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { AppuntamentoService } from '../../core/services/appuntamento.service';
import { AuthService } from '../../core/services/auth.service';
import { AstaService } from '../../core/services/asta.service';
import { MessaggioService, type MessaggioMioDto } from '../../core/services/messaggio.service';
import { RecensioneService } from '../../core/services/recensione.service';
import type { Appuntamento } from '../../models/appuntamento.model';
import type { Asta } from '../../models/asta.model';
import { nomeCompletoAutoreRecensione, type Recensione } from '../../models/recensione.model';
import { IconTrashComponent } from '../../shared/components/icon-trash/icon-trash.component';

/** Bootstrap JS da angular.json (collapse). */
interface WindowWithBootstrap extends Window {
  bootstrap?: {
    Collapse: {
      getOrCreateInstance(element: Element): { show(): void; hide(): void };
    };
  };
}

@Component({
  selector: 'app-dashboard-acquirente',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, IconTrashComponent],
  templateUrl: './dashboard-acquirente.component.html',
  styleUrl: './dashboard-acquirente.component.scss',
})
export class DashboardAcquirenteComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly appuntamentoService = inject(AppuntamentoService);
  private readonly astaService = inject(AstaService);
  private readonly messaggioService = inject(MessaggioService);
  private readonly recensioneService = inject(RecensioneService);
  private readonly fb = inject(FormBuilder);

  loading = true;
  errore: string | null = null;
  /** Errore solo sul blocco recensioni. */
  erroreRecensioneAzione: string | null = null;
  /** Errore solo sui messaggi. */
  erroreMessaggioAzione: string | null = null;
  /** Errore solo sul blocco aste. */
  erroreAstaAzione: string | null = null;
  /** Cancellazione account in corso. */
  eliminazioneAccountInCorso = false;

  aste: Asta[] = [];
  messaggi: MessaggioMioDto[] = [];
  recensioni: Recensione[] = [];
  /** Le tue richieste visita. */
  appuntamenti: Appuntamento[] = [];

  readonly stelleIndici = [1, 2, 3, 4, 5];
  readonly votiDisponibili = [1, 2, 3, 4, 5];

  readonly recensioneEditForm = this.fb.nonNullable.group({
    testo: ['', Validators.required],
    voto: [5, [Validators.required, Validators.min(1), Validators.max(5)]],
  });

  readonly messaggioEditForm = this.fb.nonNullable.group({
    testo: ['', Validators.required],
  });

  readonly astaEditForm = this.fb.nonNullable.group({
    importo: [0, [Validators.required, Validators.min(0.01)]],
  });

  recensioneModificaId: number | null = null;
  messaggioModificaId: number | null = null;
  astaModificaId: number | null = null;

  /** Intestazione benvenuto. */
  nomeBenvenuto = '';

  readonly nomeCompletoAutoreRecensione = nomeCompletoAutoreRecensione;

  ngOnInit(): void {
    const u = this.authService.getUser();
    const n = u?.nome?.trim() ?? '';
    const c = u?.cognome?.trim() ?? '';
    this.nomeBenvenuto = `${n} ${c}`.trim();
    this.carica();
  }

  /** Apri sezione messaggi e scroll all’ancora (mobile). */
  vaiAncoraMessaggi(): void {
    const panel = document.getElementById('da-collapse-messaggi');
    if (!panel) {
      return;
    }
    const Collapse = (window as unknown as WindowWithBootstrap).bootstrap?.Collapse;
    const inst = Collapse?.getOrCreateInstance(panel);
    const wasOpen = panel.classList.contains('show');
    inst?.show();

    const scrollToAnchor = (): void => {
      document.getElementById('da-ancora-messaggi')?.scrollIntoView({
        behavior: 'smooth',
        block: 'start',
      });
    };

    if (wasOpen) {
      requestAnimationFrame(scrollToAnchor);
    } else {
      setTimeout(scrollToAnchor, 380);
    }
  }

  carica(): void {
    this.loading = true;
    this.errore = null;
    forkJoin({
      aste: this.astaService.getMieAste(),
      messaggi: this.messaggioService.getMieiMessaggi(),
      recensioni: this.recensioneService.getMieRecensioni(),
      appuntamenti: this.appuntamentoService
        .getMiei()
        .pipe(catchError(() => of<Appuntamento[]>([]))),
    }).subscribe({
      next: (data) => {
        this.aste = data.aste;
        this.messaggi = data.messaggi;
        this.recensioni = data.recensioni;
        this.appuntamenti = data.appuntamenti;
        this.loading = false;
      },
      error: (err: unknown) => {
        this.errore = this.msgErroreHttp(err);
        this.loading = false;
      },
    });
  }

  private msgErroreHttp(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      const e = err.error;
      if (e && typeof e === 'object' && 'message' in e) {
        const m = (e as { message?: string }).message;
        if (typeof m === 'string' && m.trim().length > 0) {
          return m;
        }
      }
      return `Errore server (${err.status}).`;
    }
    return 'Impossibile caricare la dashboard.';
  }

  /** Asta già scaduta (ora locale). */
  astaScaduta(scadenzaIso: string): boolean {
    const t = Date.parse(scadenzaIso);
    if (Number.isNaN(t)) {
      return false;
    }
    return t < Date.now();
  }

  /** Floor leggermente sopra base per la modifica offerta. */
  minImportoModificaAsta(a: Asta): number {
    return a.prezzoBase + 0.01;
  }

  titoloAnnuncioAsta(a: Asta): string {
    return (a.annuncioTitolo && a.annuncioTitolo.trim()) ? a.annuncioTitolo.trim() : `Annuncio #${a.annuncioId}`;
  }

  titoloAnnuncioAppuntamento(ap: Appuntamento): string {
    return ap.titoloAnnuncio?.trim()
      ? ap.titoloAnnuncio.trim()
      : ap.annuncioId != null
        ? `Annuncio #${ap.annuncioId}`
        : 'Annuncio';
  }

  /** Hai almeno una visita rifiutata in lista. */
  haRichiesteVisitaRifiutate(): boolean {
    return this.appuntamenti.some((a) => a.stato === 'RIFIUTATO');
  }

  iniziaModificaAsta(a: Asta): void {
    this.erroreAstaAzione = null;
    this.astaModificaId = a.id;
    this.astaEditForm.reset({ importo: a.offertaAttuale });
  }

  annullaModificaAsta(): void {
    this.astaModificaId = null;
    this.erroreAstaAzione = null;
  }

  salvaModificaAsta(): void {
    this.erroreAstaAzione = null;
    if (this.astaModificaId == null || this.astaEditForm.invalid) {
      this.astaEditForm.markAllAsTouched();
      return;
    }
    const row = this.aste.find((x) => x.id === this.astaModificaId);
    if (!row) {
      return;
    }
    const importo = Number(this.astaEditForm.getRawValue().importo);
    if (!Number.isFinite(importo) || importo <= row.prezzoBase) {
      this.erroreAstaAzione = `L’importo deve essere superiore al prezzo base (${row.prezzoBase.toFixed(2)} €).`;
      return;
    }
    if (Math.abs(importo - row.offertaAttuale) < 1e-6) {
      this.erroreAstaAzione = 'Inserisci un importo diverso da quello attuale.';
      return;
    }
    this.astaService.modificaMiaOfferta(row.id, importo).subscribe({
      next: (updated) => {
        this.aste = this.aste.map((x) =>
          x.id === updated.id
            ? {
                ...updated,
                annuncioTitolo: updated.annuncioTitolo ?? x.annuncioTitolo,
              }
            : x
        );
        this.astaModificaId = null;
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 400) {
          this.erroreAstaAzione =
            'Impossibile aggiornare: asta chiusa o importo non valido.';
        } else if (err instanceof HttpErrorResponse && err.status === 403) {
          this.erroreAstaAzione = 'Non sei l’offerente di questa asta.';
        } else {
          this.erroreAstaAzione = 'Impossibile aggiornare l’offerta.';
        }
      },
    });
  }

  ritiraOffertaAsta(a: Asta): void {
    this.erroreAstaAzione = null;
    if (
      !confirm(
        'Ritirare la tua offerta? L’offerta visibile sull’annuncio tornerà al prezzo base finché qualcun altro non offre di più.'
      )
    ) {
      return;
    }
    this.astaService.ritiraOfferta(a.id).subscribe({
      next: () => {
        if (this.astaModificaId === a.id) {
          this.astaModificaId = null;
        }
        this.aste = this.aste.filter((x) => x.id !== a.id);
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 400) {
          this.erroreAstaAzione = 'Impossibile ritirare: asta già terminata.';
        } else if (err instanceof HttpErrorResponse && err.status === 403) {
          this.erroreAstaAzione = 'Non sei l’offerente di questa asta.';
        } else {
          this.erroreAstaAzione = 'Impossibile ritirare l’offerta.';
        }
      },
    });
  }

  titoloAnnuncioMessaggio(m: MessaggioMioDto): string {
    return (m.annuncioTitolo && m.annuncioTitolo.trim())
      ? m.annuncioTitolo.trim()
      : `Annuncio #${m.annuncioId}`;
  }

  titoloAnnuncioRecensione(r: Recensione): string {
    return (r.annuncioTitolo && r.annuncioTitolo.trim())
      ? r.annuncioTitolo.trim()
      : `Annuncio #${r.annuncioId ?? ''}`;
  }

  iniziaModificaMessaggio(m: MessaggioMioDto): void {
    this.erroreMessaggioAzione = null;
    this.messaggioModificaId = m.id;
    this.messaggioEditForm.reset({
      testo: m.testo,
    });
  }

  annullaModificaMessaggio(): void {
    this.messaggioModificaId = null;
    this.erroreMessaggioAzione = null;
  }

  salvaModificaMessaggio(): void {
    this.erroreMessaggioAzione = null;
    if (this.messaggioModificaId == null || this.messaggioEditForm.invalid) {
      this.messaggioEditForm.markAllAsTouched();
      return;
    }
    const { testo } = this.messaggioEditForm.getRawValue();
    this.messaggioService
      .aggiornaMessaggio(this.messaggioModificaId, testo)
      .subscribe({
        next: () => {
          this.messaggioModificaId = null;
          this.ricaricaMessaggi();
        },
        error: () => {
          this.erroreMessaggioAzione = 'Impossibile aggiornare il messaggio.';
        },
      });
  }

  eliminaMessaggio(m: MessaggioMioDto): void {
    this.erroreMessaggioAzione = null;
    if (!confirm('Eliminare questo messaggio? Il venditore non lo vedrà più nell’elenco dell’annuncio.')) {
      return;
    }
    this.messaggioService.eliminaMessaggio(m.id).subscribe({
      next: () => {
        if (this.messaggioModificaId === m.id) {
          this.messaggioModificaId = null;
        }
        this.ricaricaMessaggi();
      },
      error: () => {
        this.erroreMessaggioAzione = 'Impossibile eliminare il messaggio.';
      },
    });
  }

  private ricaricaMessaggi(): void {
    this.messaggioService.getMieiMessaggi().subscribe({
      next: (list) => {
        this.messaggi = list;
      },
    });
  }

  iniziaModificaRecensione(r: Recensione): void {
    this.erroreRecensioneAzione = null;
    this.recensioneModificaId = r.id;
    this.recensioneEditForm.reset({ testo: r.testo, voto: r.voto });
  }

  annullaModificaRecensione(): void {
    this.recensioneModificaId = null;
    this.erroreRecensioneAzione = null;
  }

  salvaModificaRecensione(): void {
    this.erroreRecensioneAzione = null;
    if (this.recensioneModificaId == null || this.recensioneEditForm.invalid) {
      this.recensioneEditForm.markAllAsTouched();
      return;
    }
    const { testo, voto } = this.recensioneEditForm.getRawValue();
    this.recensioneService.aggiornaRecensione(this.recensioneModificaId, testo, voto).subscribe({
      next: () => {
        this.recensioneModificaId = null;
        this.ricaricaRecensioni();
      },
      error: () => {
        this.erroreRecensioneAzione = 'Impossibile aggiornare la recensione.';
      },
    });
  }

  eliminaRecensione(r: Recensione): void {
    this.erroreRecensioneAzione = null;
    if (!confirm('Eliminare questa recensione? L’operazione non può essere annullata.')) {
      return;
    }
    this.recensioneService.eliminaRecensione(r.id).subscribe({
      next: () => {
        if (this.recensioneModificaId === r.id) {
          this.recensioneModificaId = null;
        }
        this.ricaricaRecensioni();
      },
      error: () => {
        this.erroreRecensioneAzione = 'Impossibile eliminare la recensione.';
      },
    });
  }

  private ricaricaRecensioni(): void {
    this.recensioneService.getMieRecensioni().subscribe({
      next: (list) => {
        this.recensioni = list;
      },
    });
  }

  eliminaAccount(): void {
    this.errore = null;
    if (
      !confirm(
        'Eliminare definitivamente il tuo account? I tuoi dati e le tue recensioni verranno rimossi. Questa azione non può essere annullata.'
      )
    ) {
      return;
    }
    this.eliminazioneAccountInCorso = true;
    this.authService.deleteAccount().subscribe({
      next: () => {
        this.authService.logout();
        void this.router.navigateByUrl('/home');
      },
      error: (err: unknown) => {
        this.eliminazioneAccountInCorso = false;
        this.errore =
          err instanceof HttpErrorResponse && err.status === 403
            ? 'Operazione non consentita.'
            : 'Impossibile eliminare l’account. Riprova più tardi.';
      },
    });
  }
}
