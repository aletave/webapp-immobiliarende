import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import * as L from 'leaflet';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  catchError,
  concatMap,
  EMPTY,
  forkJoin,
  from,
  last,
  map,
  Observable,
  of,
  tap,
} from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { AnnuncioService } from '../../core/services/annuncio.service';
import { GeocodingService } from '../../core/services/geocoding.service';
import { AstaService } from '../../core/services/asta.service';
import { FotoService } from '../../core/services/foto.service';
import {
  MessaggioService,
  type MessaggioRiepilogoNonLettiDto,
} from '../../core/services/messaggio.service';
import { AppuntamentoService } from '../../core/services/appuntamento.service';
import { RecensioneService } from '../../core/services/recensione.service';
import type { Appuntamento } from '../../models/appuntamento.model';
import type { Asta } from '../../models/asta.model';
import type { Annuncio, AnnuncioRequest } from '../../models/annuncio.model';
import type { Foto } from '../../models/foto.model';
import {
  nomeCompletoAutoreRecensione,
  type Recensione,
} from '../../models/recensione.model';
import { parseSpringErrorMessage } from '../../core/utils/http-error.util';
import { IconTrashComponent } from '../../shared/components/icon-trash/icon-trash.component';

const MAX_FOTO_PER_ANNUNCIO = 10;
/** Paginazione tabella annunci (5 righe). */
const PAGE_SIZE_ANNUNCI = 5;
/** Max MB upload foto (allineato al backend). */
const MAX_MEGABYTES_FOTO = 20;
const MAX_BYTES_FOTO = MAX_MEGABYTES_FOTO * 1024 * 1024;
const ALLOWED_FOTO_TYPES = new Set([
  'image/jpeg',
  'image/png',
  'image/gif',
  'image/webp',
]);

@Component({
  selector: 'app-dashboard-venditore',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, IconTrashComponent],
  templateUrl: './dashboard-venditore.component.html',
  styleUrl: './dashboard-venditore.component.scss',
})
export class DashboardVenditoreComponent implements OnInit, OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly annuncioService = inject(AnnuncioService);
  private readonly astaService = inject(AstaService);
  private readonly fotoService = inject(FotoService);
  private readonly messaggioService = inject(MessaggioService);
  private readonly recensioneService = inject(RecensioneService);
  private readonly appuntamentoService = inject(AppuntamentoService);
  private readonly geocodingService = inject(GeocodingService);
  private readonly fb = inject(FormBuilder);

  annunci: Annuncio[] = [];
  /** Pagina corrente tabella annunci (1-based). */
  paginaCorrenteAnnunci = 1;
  readonly pageSizeAnnunci = PAGE_SIZE_ANNUNCI;
  loading = true;
  errore: string | null = null;
  messaggio: string | null = null;
  /** Cancellazione account in corso. */
  eliminazioneAccountInCorso = false;

  /** Thread messaggi per l’annuncio aperto nel pannello. */
  messaggi: any[] = [];
  /** Annuncio del pannello messaggi; null se chiuso. */
  messaggiAnnuncioId: number | null = null;

  /** Conteggi non letti per annuncio. */
  riepilogoMessaggiNonLetti: MessaggioRiepilogoNonLettiDto[] = [];
  /** Le tue aste da venditore. */
  asteVenditore: Asta[] = [];
  /** Richieste visita sui tuoi annunci. */
  appuntamenti: Appuntamento[] = [];
  /** Completa visita: richiesta HTTP per questo id. */
  completaAppuntamentoId: number | null = null;
  rifiutaAppuntamentoId: number | null = null;
  erroreAppuntamento: string | null = null;
  /** Delete messaggio: id in corso. */
  eliminazioneMessaggioId: number | null = null;

  /** null = creazione; altrimenti id in modifica. */
  editingId: number | null = null;
  mostraForm = false;

  /** In creazione: solo form, senza resto dashboard. */
  get soloFormNuovoAnnuncio(): boolean {
    return this.mostraForm && this.editingId == null;
  }

  /** File in coda upload dopo il salvataggio. */
  fotoFileDaCaricare: File[] = [];
  /** Foto già in DB in modifica (+ coda, max 10). */
  fotoEsistenti: Foto[] = [];
  /** Caricamento lista foto in modifica. */
  fotoEsistentiLoading = false;
  /** Foto in eliminazione (UI). */
  eliminazioneFotoId: number | null = null;

  /** Tooltip se «Ripristina listino» è disabilitato. */
  readonly tooltipRipristinoListinoDisabilitato =
    'Disponibile solo se c’è un ribasso attivo (prezzo barrato)';

  readonly maxFotoPerAnnuncio = MAX_FOTO_PER_ANNUNCIO;
  readonly maxMegabytesFoto = MAX_MEGABYTES_FOTO;

  /** Intestazione benvenuto. */
  nomeBenvenuto = '';

  /** Utente con ruolo ADMIN (anche qui in vista venditore). */
  get isAdmin(): boolean {
    return this.authService.getUser()?.ruolo === 'ADMIN';
  }

  /** Più notifiche attive: layout a colonne. */
  get notificheMultiColonna(): boolean {
    let n = 0;
    if (this.totaleMessaggiNonLetti > 0) {
      n++;
    }
    if (!this.loading && this.totaleNuoveOfferteAste > 0) {
      n++;
    }
    if (!this.loading && this.nuoveRecensioniCount > 0) {
      n++;
    }
    if (!this.loading && this.nuoveRichiesteVisitaElenco.length > 0) {
      n++;
    }
    return n > 1;
  }

  /** Id corrente (chiavi localStorage notifiche). */
  private venditoreId = 0;

  /** Recensioni sui tuoi annunci. */
  recensioniRicevute: Recensione[] = [];
  /** Recensioni “nuove” dopo baseline. */
  nuoveRecensioniElenco: Recensione[] = [];
  nuoveRecensioniCount = 0;

  /** Visite PENDING dopo baseline (notifica). */
  nuoveRichiesteVisitaElenco: Appuntamento[] = [];

  readonly nomeCompletoAutoreRecensione = nomeCompletoAutoreRecensione;

  readonly categorie = ['APPARTAMENTO', 'VILLA', 'BOX', 'TERRENO'] as const;
  readonly tipi = ['VENDITA', 'AFFITTO'] as const;

  /** Mappa nel form (click = pin). */
  formMapInstance: L.Map | null = null;
  formMarker: L.Marker | null = null;
  /** Reverse geocode in corso. */
  reverseGeocoding = false;
  /** Geocode indirizzo testuale in corso. */
  geocodingIndirizzoManuale = false;
  /** Errore su “Verifica” indirizzo. */
  erroreIndirizzoManuale: string | null = null;

  readonly annuncioForm = this.fb.nonNullable.group({
    titolo: ['', [Validators.required, Validators.maxLength(200)]],
    descrizione: ['', [Validators.required, Validators.maxLength(20000)]],
    prezzo: [
      0,
      [
        Validators.required,
        Validators.min(0.01),
        Validators.max(999_999_999.99),
      ],
    ],
    mq: [50, [Validators.required, Validators.min(1), Validators.max(10_000_000)]],
    categoria: ['APPARTAMENTO', Validators.required],
    tipo: ['VENDITA', Validators.required],
    indirizzo: ['', [Validators.required, Validators.maxLength(500)]],
  });

  readonly ribassaForm = this.fb.nonNullable.group({
    annuncioId: [0, Validators.required],
    nuovoPrezzo: [
      0,
      [Validators.required, Validators.min(0.01), Validators.max(999_999_999.99)],
    ],
    /** Permanente o temporaneo (con fine). */
    modalitaRibasso: ['permanente' as 'permanente' | 'temporaneo'],
    /** Fine ribasso (datetime-local) se temporaneo. */
    ribassoFine: [''],
  });

  readonly astaForm = this.fb.nonNullable.group({
    annuncioId: [0, Validators.required],
    prezzoBase: [0, [Validators.required, Validators.min(0.01)]],
    scadenza: ['', Validators.required],
  });

  /** Annunci della pagina corrente. */
  get annunciPagina(): Annuncio[] {
    const start = (this.paginaCorrenteAnnunci - 1) * this.pageSizeAnnunci;
    return this.annunci.slice(start, start + this.pageSizeAnnunci);
  }

  /** Pagine tabella (min 1 se c’è almeno un annuncio). */
  get totalePagineAnnunci(): number {
    return Math.max(1, Math.ceil(this.annunci.length / this.pageSizeAnnunci));
  }

  /**
   * Salva: in creazione serve form valido; in modifica se dirty o foto in coda;
   * eccezione solo upload senza dirty (PUT non inviato, utile dati legacy invalidi).
   */
  get salvaAnnuncioAbilitato(): boolean {
    if (this.editingId == null) {
      return this.annuncioForm.valid;
    }
    const soloUploadFoto =
      this.fotoFileDaCaricare.length > 0 && !this.annuncioForm.dirty;
    if (soloUploadFoto) {
      return true;
    }
    if (this.annuncioForm.invalid) {
      return false;
    }
    return this.annuncioForm.dirty || this.fotoFileDaCaricare.length > 0;
  }

  /** Testo “da–a di totale” in tabella. */
  get annunciRangeLabel(): string {
    const total = this.annunci.length;
    if (total === 0) {
      return '';
    }
    const start = (this.paginaCorrenteAnnunci - 1) * this.pageSizeAnnunci + 1;
    const end = Math.min(
      this.paginaCorrenteAnnunci * this.pageSizeAnnunci,
      total,
    );
    return `${start}–${end} di ${total}`;
  }

  /** Badge somma messaggi non letti. */
  get totaleMessaggiNonLetti(): number {
    return this.riepilogoMessaggiNonLetti.reduce((s, x) => s + x.numNonLetti, 0);
  }

  /** Quante aste hanno flag nuovaOfferta dal backend. */
  get totaleNuoveOfferteAste(): number {
    return this.asteVenditore.filter((a) => a.nuovaOfferta === true).length;
  }

  /** Riga evidenziata se ci sono non letti per questo annuncio. */
  annuncioHaMessaggiNuovi(annuncioId: number): boolean {
    return this.numNonLettiAnnuncio(annuncioId) > 0;
  }

  /** C’è prezzo precedente → ribasso attivo, ripristinabile. */
  annuncioHaRibassoDaAnnullare(a: Annuncio): boolean {
    return a.prezzoPrecedente != null && a.prezzoPrecedente !== undefined;
  }

  /** Promo a tempo ancora valida (come in home/card). */
  ribassoTemporaneoAttivo(a: Annuncio): boolean {
    const f = a.ribassoFine;
    if (f == null || f === '') {
      return false;
    }
    const t = Date.parse(f);
    return !Number.isNaN(t) && t > Date.now();
  }

  ripristinaPrezzoListino(a: Annuncio): void {
    if (!this.annuncioHaRibassoDaAnnullare(a)) {
      return;
    }
    const listino = a.prezzoPrecedente as number;
    if (
      !confirm(
        `Ripristinare il prezzo listino (${listino.toFixed(2).replace('.', ',')} €)? ` +
          `L’annuncio tornerà a mostrare un solo prezzo, senza ribasso.`,
      )
    ) {
      return;
    }
    this.errore = null;
    this.annuncioService.annullaRibasso(a.id).subscribe({
      next: () => {
        this.messaggio = 'Prezzo listino ripristinato.';
        this.carica();
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 400) {
          this.errore = 'Nessun ribasso da annullare per questo annuncio.';
          return;
        }
        this.errore = 'Ripristino non riuscito.';
      },
    });
  }

  numNonLettiAnnuncio(annuncioId: number): number {
    const r = this.riepilogoMessaggiNonLetti.find((x) => x.annuncioId === annuncioId);
    return r?.numNonLetti ?? 0;
  }

  /** Asta già scaduta. */
  astaScaduta(scadenzaIso: string): boolean {
    const t = Date.parse(scadenzaIso);
    if (Number.isNaN(t)) {
      return false;
    }
    return t < Date.now();
  }

  titoloAnnuncioAsta(a: Asta): string {
    return a.annuncioTitolo?.trim()
      ? a.annuncioTitolo.trim()
      : `Annuncio #${a.annuncioId}`;
  }

  paginaPrecedenteAnnunci(): void {
    if (this.paginaCorrenteAnnunci > 1) {
      this.paginaCorrenteAnnunci--;
    }
  }

  paginaSuccessivaAnnunci(): void {
    if (this.paginaCorrenteAnnunci < this.totalePagineAnnunci) {
      this.paginaCorrenteAnnunci++;
    }
  }

  ngOnInit(): void {
    const u = this.authService.getUser();
    this.venditoreId = u?.id ?? 0;
    const n = u?.nome?.trim() ?? '';
    const c = u?.cognome?.trim() ?? '';
    this.nomeBenvenuto = `${n} ${c}`.trim();
    this.carica();
  }

  ngOnDestroy(): void {
    this.destroyFormMap();
  }

  carica(): void {
    this.loading = true;
    this.errore = null;
    this.erroreAppuntamento = null;
    forkJoin({
      annunci: this.annuncioService.getAnnunciMiei(),
      nonLetti: this.messaggioService.getRiepilogoNonLettiVenditore(),
      aste: this.astaService.getAsteVenditore().pipe(catchError(() => of<Asta[]>([]))),
      recensioni: this.recensioneService
        .getRecensioniVenditore()
        .pipe(catchError(() => of<Recensione[]>([]))),
      appuntamenti: this.appuntamentoService
        .getPerVenditore()
        .pipe(catchError(() => of<Appuntamento[]>([]))),
    }).subscribe({
      next: ({ annunci, nonLetti, aste, recensioni, appuntamenti }) => {
        this.annunci = annunci;
        this.riepilogoMessaggiNonLetti = nonLetti;
        this.asteVenditore = aste;
        this.recensioniRicevute = recensioni;
        this.appuntamenti = appuntamenti;
        this.initBaselineRecensioni(this.recensioniRicevute);
        this.syncNuoveRecensioni();
        this.initBaselineAppuntamenti(this.appuntamenti);
        this.syncNuoveRichiesteVisita();
        this.loading = false;
        const maxPage = Math.max(
          1,
          Math.ceil(this.annunci.length / this.pageSizeAnnunci),
        );
        if (this.paginaCorrenteAnnunci > maxPage) {
          this.paginaCorrenteAnnunci = maxPage;
        }
        this.applicaHandoffDaAdminDashboard();
      },
      error: () => {
        this.errore = 'Impossibile caricare i tuoi annunci.';
        this.loading = false;
      },
    });
  }

  nomeAcquirenteAppuntamento(a: Appuntamento): string {
    const n = a.nomeAcquirente?.trim() ?? '';
    const c = a.cognomeAcquirente?.trim() ?? '';
    const t = `${n} ${c}`.trim();
    return t || a.emailAcquirente || 'Acquirente';
  }

  /** Visita completata → l’acquirente può recensire. */
  segnaVisitaCompletata(a: Appuntamento): void {
    if (a.stato !== 'PENDING') {
      return;
    }
    this.erroreAppuntamento = null;
    this.completaAppuntamentoId = a.id;
    this.appuntamentoService.completa(a.id).subscribe({
      next: (agg) => {
        const idx = this.appuntamenti.findIndex((x) => x.id === a.id);
        if (idx >= 0) {
          this.appuntamenti[idx] = agg;
        }
        this.syncNuoveRichiesteVisita();
        this.completaAppuntamentoId = null;
      },
      error: (err: unknown) => {
        this.completaAppuntamentoId = null;
        const msg = parseSpringErrorMessage(err);
        this.erroreAppuntamento =
          msg
          ?? (err instanceof HttpErrorResponse && err.status === 403
            ? 'Non sei il venditore di questo annuncio.'
            : err instanceof HttpErrorResponse && err.status === 404
              ? 'Richiesta non trovata.'
              : 'Impossibile segnare la visita come completata. Riprova.');
      },
    });
  }

  segnaVisitaRifiutata(a: Appuntamento): void {
    if (a.stato !== 'PENDING') {
      return;
    }
    if (
      !confirm(
        'Rifiutare questa richiesta di visita? L’acquirente potrà inviarne una nuova in seguito.',
      )
    ) {
      return;
    }
    this.erroreAppuntamento = null;
    this.rifiutaAppuntamentoId = a.id;
    this.appuntamentoService.rifiuta(a.id).subscribe({
      next: (agg) => {
        const idx = this.appuntamenti.findIndex((x) => x.id === a.id);
        if (idx >= 0) {
          this.appuntamenti[idx] = agg;
        }
        this.syncNuoveRichiesteVisita();
        this.rifiutaAppuntamentoId = null;
      },
      error: (err: unknown) => {
        this.rifiutaAppuntamentoId = null;
        const msg = parseSpringErrorMessage(err);
        this.erroreAppuntamento =
          msg
          ?? (err instanceof HttpErrorResponse && err.status === 403
            ? 'Non sei il venditore di questo annuncio.'
            : err instanceof HttpErrorResponse && err.status === 404
              ? 'Richiesta non trovata.'
              : 'Impossibile rifiutare la visita. Riprova.');
      },
    });
  }

  /** Handoff da admin: sessionStorage apre modifica/messaggi/… qui. */
  private applicaHandoffDaAdminDashboard(): void {
    if (typeof sessionStorage === 'undefined') {
      return;
    }
    const raw = sessionStorage.getItem('dv-admin-handoff');
    if (!raw) {
      return;
    }
    sessionStorage.removeItem('dv-admin-handoff');
    let parsed: { annuncioId: number; action: string };
    try {
      parsed = JSON.parse(raw) as { annuncioId: number; action: string };
    } catch {
      return;
    }
    const id = Number(parsed.annuncioId);
    if (Number.isNaN(id)) {
      return;
    }
    const idx = this.annunci.findIndex((x) => x.id === id);
    if (idx < 0) {
      return;
    }
    this.paginaCorrenteAnnunci = Math.floor(idx / this.pageSizeAnnunci) + 1;
    const a = this.annunci[idx]!;
    const scrollTo = (elId: string) => {
      setTimeout(() => {
        document.getElementById(elId)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }, 200);
    };
    switch (parsed.action) {
      case 'modifica':
        this.modifica(a);
        scrollTo('dv-form-annuncio');
        break;
      case 'messaggi':
        this.caricaMessaggi(a.id);
        setTimeout(() => {
          document.getElementById('dv-panel-messaggi')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }, 500);
        break;
      case 'ribassa':
        this.apriRibassa(a);
        scrollTo('dv-sezione-ribassa');
        break;
      case 'ripristina':
        if (this.annuncioHaRibassoDaAnnullare(a)) {
          this.ripristinaPrezzoListino(a);
        }
        break;
      case 'asta':
        this.apriAsta(a);
        scrollTo('dv-sezione-asta');
        break;
      default:
        break;
    }
  }

  /** Aggiorna baseline recensioni → banner nuove sparisce. */
  segnaRecensioniViste(): void {
    if (typeof localStorage === 'undefined' || this.venditoreId === 0) {
      return;
    }
    const ts =
      this.recensioniRicevute.length === 0
        ? Date.now()
        : this.maxCreatedAtTsRecensioni(this.recensioniRicevute);
    localStorage.setItem(
      this.storageKeyRecensioniLetti(),
      new Date(ts).toISOString(),
    );
    this.syncNuoveRecensioni();
  }

  /** Come segnaRecensioniViste ma per le visite. */
  segnaVisiteViste(): void {
    if (typeof localStorage === 'undefined' || this.venditoreId === 0) {
      return;
    }
    const ts =
      this.appuntamenti.length === 0
        ? Date.now()
        : this.maxCreatedAtTsAppuntamenti(this.appuntamenti);
    localStorage.setItem(
      this.storageKeyAppuntamentiVisti(),
      new Date(ts).toISOString(),
    );
    this.syncNuoveRichiesteVisita();
  }

  /** Apri collapse visite e scroll. */
  apriSezioneRichiesteVisita(): void {
    const panel = document.getElementById('dv-collapse-visite');
    if (panel) {
      const win = window as unknown as {
        bootstrap?: {
          Collapse: {
            getOrCreateInstance(el: Element): { show(): void };
          };
        };
      };
      win.bootstrap?.Collapse?.getOrCreateInstance(panel).show();
    }
    setTimeout(() => {
      document
        .getElementById('dv-sezione-visite')
        ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 120);
  }

  private storageKeyAppuntamentiVisti(): string {
    return `venditore-dashboard-appuntamenti-visti-fino-${this.venditoreId}`;
  }

  /** Primo avvio: baseline = max createdAt (niente allarme su tutto lo storico). */
  private initBaselineAppuntamenti(appuntamenti: Appuntamento[]): void {
    if (typeof localStorage === 'undefined' || this.venditoreId === 0) {
      return;
    }
    if (localStorage.getItem(this.storageKeyAppuntamentiVisti()) != null) {
      return;
    }
    const ts =
      appuntamenti.length === 0
        ? Date.now()
        : this.maxCreatedAtTsAppuntamenti(appuntamenti);
    localStorage.setItem(
      this.storageKeyAppuntamentiVisti(),
      new Date(ts).toISOString(),
    );
  }

  private syncNuoveRichiesteVisita(): void {
    if (this.venditoreId === 0) {
      this.nuoveRichiesteVisitaElenco = [];
      return;
    }
    if (typeof localStorage === 'undefined') {
      this.nuoveRichiesteVisitaElenco = [];
      return;
    }
    const raw = localStorage.getItem(this.storageKeyAppuntamentiVisti());
    if (raw == null) {
      this.nuoveRichiesteVisitaElenco = [];
      return;
    }
    const t0 = Date.parse(raw);
    if (Number.isNaN(t0)) {
      this.nuoveRichiesteVisitaElenco = [];
      return;
    }
    this.nuoveRichiesteVisitaElenco = this.appuntamenti
      .filter(
        (ap) =>
          ap.stato === 'PENDING' &&
          this.tsCreatedAtAppuntamento(ap.createdAt) > t0,
      )
      .sort(
        (a, b) =>
          this.tsCreatedAtAppuntamento(b.createdAt) -
          this.tsCreatedAtAppuntamento(a.createdAt),
      );
  }

  private maxCreatedAtTsAppuntamenti(appuntamenti: Appuntamento[]): number {
    if (appuntamenti.length === 0) {
      return Date.now();
    }
    return Math.max(
      ...appuntamenti.map((a) => this.tsCreatedAtAppuntamento(a.createdAt)),
    );
  }

  private tsCreatedAtAppuntamento(createdAt: string): number {
    const t = Date.parse(this.normalizeCreatedAtAppuntamento(createdAt));
    return Number.isNaN(t) ? 0 : t;
  }

  private normalizeCreatedAtAppuntamento(createdAt: string): string {
    const v = createdAt as unknown;
    if (typeof v === 'string') {
      return v;
    }
    if (Array.isArray(v) && v.length >= 3) {
      const [y, mo, d, h = 0, min = 0, s = 0] = v as number[];
      return new Date(y, mo - 1, d, h, min, s).toISOString();
    }
    return '';
  }

  private storageKeyRecensioniLetti(): string {
    return `venditore-dashboard-recensioni-letti-fino-${this.venditoreId}`;
  }

  /** Primo avvio: baseline = max createdAt. */
  private initBaselineRecensioni(recensioni: Recensione[]): void {
    if (typeof localStorage === 'undefined' || this.venditoreId === 0) {
      return;
    }
    if (localStorage.getItem(this.storageKeyRecensioniLetti()) != null) {
      return;
    }
    const ts =
      recensioni.length === 0 ? Date.now() : this.maxCreatedAtTsRecensioni(recensioni);
    localStorage.setItem(this.storageKeyRecensioniLetti(), new Date(ts).toISOString());
  }

  private syncNuoveRecensioni(): void {
    if (this.venditoreId === 0) {
      this.nuoveRecensioniCount = 0;
      this.nuoveRecensioniElenco = [];
      return;
    }
    if (typeof localStorage === 'undefined') {
      this.nuoveRecensioniCount = 0;
      this.nuoveRecensioniElenco = [];
      return;
    }
    const raw = localStorage.getItem(this.storageKeyRecensioniLetti());
    if (raw == null) {
      this.nuoveRecensioniCount = 0;
      this.nuoveRecensioniElenco = [];
      return;
    }
    const t0 = Date.parse(raw);
    if (Number.isNaN(t0)) {
      this.nuoveRecensioniCount = 0;
      this.nuoveRecensioniElenco = [];
      return;
    }
    this.nuoveRecensioniElenco = this.recensioniRicevute.filter(
      (r) => this.tsCreatedAtRecensione(r.createdAt) > t0,
    );
    this.nuoveRecensioniCount = this.nuoveRecensioniElenco.length;
  }

  private maxCreatedAtTsRecensioni(recensioni: Recensione[]): number {
    if (recensioni.length === 0) {
      return Date.now();
    }
    return Math.max(...recensioni.map((r) => this.tsCreatedAtRecensione(r.createdAt)));
  }

  private tsCreatedAtRecensione(createdAt: string): number {
    const t = Date.parse(this.normalizeCreatedAtRecensione(createdAt));
    return Number.isNaN(t) ? 0 : t;
  }

  private normalizeCreatedAtRecensione(createdAt: string): string {
    const v = createdAt as unknown;
    if (typeof v === 'string') {
      return v;
    }
    if (Array.isArray(v) && v.length >= 3) {
      const [y, mo, d, h = 0, min = 0, s = 0] = v as number[];
      return new Date(y, mo - 1, d, h, min, s).toISOString();
    }
    return '';
  }

  private ricaricaRiepilogoNonLetti(): void {
    this.messaggioService.getRiepilogoNonLettiVenditore().subscribe({
      next: (data) => {
        this.riepilogoMessaggiNonLetti = data;
      },
    });
  }

  apriNuovo(): void {
    this.editingId = null;
    this.fotoEsistenti = [];
    this.fotoEsistentiLoading = false;
    this.eliminazioneFotoId = null;
    this.fotoFileDaCaricare = [];
    this.annuncioForm.reset({
      titolo: '',
      descrizione: '',
      prezzo: 0,
      mq: 50,
      categoria: 'APPARTAMENTO',
      tipo: 'VENDITA',
      indirizzo: '',
    });
    this.erroreIndirizzoManuale = null;
    this.mostraForm = true;
    setTimeout(() => this.initFormMap(), 100);
  }

  modifica(a: Annuncio): void {
    this.editingId = a.id;
    this.fotoFileDaCaricare = [];
    this.eliminazioneFotoId = null;
    this.fotoEsistenti = [];
    this.fotoEsistentiLoading = true;
    this.fotoService.getFotoAnnuncio(a.id).subscribe({
      next: (foto) => {
        this.fotoEsistenti = foto;
        this.fotoEsistentiLoading = false;
      },
      error: () => {
        this.fotoEsistenti = [];
        this.fotoEsistentiLoading = false;
      },
    });
    this.annuncioForm.patchValue({
      titolo: a.titolo,
      descrizione: a.descrizione,
      prezzo: a.prezzo,
      mq: a.mq,
      categoria: a.categoria,
      tipo: a.tipo,
      indirizzo: a.indirizzo?.trim() ?? '',
    });
    this.mostraForm = true;
    const lat =
      a.lat != null && !Number.isNaN(a.lat) ? a.lat : undefined;
    const lng =
      a.lng != null && !Number.isNaN(a.lng) ? a.lng : undefined;
    this.erroreIndirizzoManuale = null;
    setTimeout(() => this.initFormMap(lat, lng), 100);
  }

  annullaForm(): void {
    this.erroreIndirizzoManuale = null;
    this.destroyFormMap();
    this.mostraForm = false;
    this.editingId = null;
    this.fotoFileDaCaricare = [];
    this.fotoEsistenti = [];
    this.fotoEsistentiLoading = false;
    this.eliminazioneFotoId = null;
  }

  /** Leaflet nel form: click aggiorna pin e indirizzo; lat/lng opzionali in modifica. */
  initFormMap(lat?: number, lng?: number): void {
    this.destroyFormMap();
    const el = document.getElementById('form-map');
    if (!el) {
      return;
    }
    const iconMarker = this.createFormMapIcon();
    L.Marker.prototype.options.icon = iconMarker;

    const hasCoords =
      lat != null &&
      lng != null &&
      !Number.isNaN(lat) &&
      !Number.isNaN(lng);
    const centerLat = hasCoords ? lat! : 39.35;
    const centerLng = hasCoords ? lng! : 16.18;
    const zoom = hasCoords ? 15 : 13;

    this.formMapInstance = L.map('form-map').setView([centerLat, centerLng], zoom);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors',
    }).addTo(this.formMapInstance);

    if (hasCoords) {
      this.formMarker = L.marker([lat!, lng!], { icon: iconMarker }).addTo(
        this.formMapInstance,
      );
    }

    this.formMapInstance.on('click', (e: L.LeafletMouseEvent) => {
      const clickLat = e.latlng.lat;
      const clickLng = e.latlng.lng;
      if (this.formMarker) {
        this.formMapInstance!.removeLayer(this.formMarker);
      }
      this.formMarker = L.marker([clickLat, clickLng], {
        icon: iconMarker,
      }).addTo(this.formMapInstance!);
      this.erroreIndirizzoManuale = null;
      this.reverseGeocoding = true;
      this.geocodingService.reverseGeocode(clickLat, clickLng).subscribe({
        next: (res) => {
          this.annuncioForm.patchValue({ indirizzo: res.displayName });
          this.annuncioForm.markAsDirty();
          this.reverseGeocoding = false;
        },
        error: () => {
          this.annuncioForm.patchValue({
            indirizzo: `${clickLat}, ${clickLng}`,
          });
          this.annuncioForm.markAsDirty();
          this.reverseGeocoding = false;
        },
      });
    });

    setTimeout(() => this.formMapInstance?.invalidateSize(), 0);
  }

  /** Marker default del form. */
  private createFormMapIcon(): L.Icon {
    return L.icon({
      iconUrl: 'assets/marker-icon.png',
      shadowUrl: 'assets/marker-shadow.png',
      iconSize: [25, 41],
      iconAnchor: [12, 41],
    });
  }

  /** Geocode testo indirizzo e centra mappa. */
  verificaIndirizzoManuale(): void {
    const testo = this.annuncioForm.get('indirizzo')?.value?.trim();
    if (!testo) {
      this.erroreIndirizzoManuale = 'Inserisci un indirizzo.';
      return;
    }
    this.erroreIndirizzoManuale = null;
    this.geocodingIndirizzoManuale = true;
    this.geocodingService.cerca(testo).subscribe({
      next: (res) => {
        this.annuncioForm.patchValue({ indirizzo: res.displayName });
        this.annuncioForm.markAsDirty();
        this.posizionaMarkerSuMappa(res.lat, res.lng);
        this.geocodingIndirizzoManuale = false;
      },
      error: () => {
        this.erroreIndirizzoManuale =
          'Indirizzo non trovato. Controlla il testo o seleziona un punto sulla mappa.';
        this.geocodingIndirizzoManuale = false;
      },
    });
  }

  /** Pin + flyTo dopo geocode o riallineamento. */
  private posizionaMarkerSuMappa(lat: number, lng: number): void {
    const leafletMap = this.formMapInstance;
    if (!leafletMap) {
      return;
    }
    const icon = this.createFormMapIcon();
    if (this.formMarker) {
      leafletMap.removeLayer(this.formMarker);
    }
    this.formMarker = L.marker([lat, lng], { icon }).addTo(leafletMap);
    leafletMap.setView([lat, lng], 15);
    setTimeout(() => leafletMap.invalidateSize(), 0);
  }

  /** teardown Leaflet (riapertura form senza doppi listener). */
  destroyFormMap(): void {
    if (this.formMapInstance) {
      this.formMapInstance.remove();
      this.formMapInstance = null;
    }
    this.formMarker = null;
  }

  /** Rimuovi foto già salvata (modifica). */
  rimuoviFotoEsistente(f: Foto): void {
    if (!confirm('Rimuovere questa foto dall’annuncio?')) {
      return;
    }
    this.errore = null;
    this.eliminazioneFotoId = f.id;
    this.fotoService.eliminaFoto(f.id).subscribe({
      next: () => {
        this.fotoEsistenti = this.fotoEsistenti.filter((x) => x.id !== f.id);
        this.eliminazioneFotoId = null;
      },
      error: () => {
        this.eliminazioneFotoId = null;
        this.errore = 'Impossibile eliminare la foto.';
      },
    });
  }

  onFotoSelezionate(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const files = input.files;
    if (!files?.length) {
      return;
    }
    const aggiunte: File[] = [];
    for (let i = 0; i < files.length; i++) {
      const f = files[i];
      if (f.size > MAX_BYTES_FOTO) {
        this.errore = `Il file «${f.name}» supera ${MAX_MEGABYTES_FOTO} MB.`;
        input.value = '';
        return;
      }
      if (!ALLOWED_FOTO_TYPES.has(f.type)) {
        this.errore = `Formato non consentito per «${f.name}». Usa JPEG, PNG, GIF o WebP.`;
        input.value = '';
        return;
      }
      aggiunte.push(f);
    }
    const totale =
      this.fotoEsistenti.length + this.fotoFileDaCaricare.length + aggiunte.length;
    if (totale > MAX_FOTO_PER_ANNUNCIO) {
      this.errore = `Puoi avere al massimo ${MAX_FOTO_PER_ANNUNCIO} foto per annuncio (già presenti o in coda: ${this.fotoEsistenti.length + this.fotoFileDaCaricare.length}).`;
      input.value = '';
      return;
    }
    this.fotoFileDaCaricare = [...this.fotoFileDaCaricare, ...aggiunte];
    this.errore = null;
    input.value = '';
  }

  rimuoviFotoInCoda(index: number): void {
    this.fotoFileDaCaricare = this.fotoFileDaCaricare.filter((_, i) => i !== index);
  }

  /** Coda upload sequenziale; void per coerenza col pipe. */
  private uploadFotoInCodaPerAnnuncio(annuncioId: number): Observable<void> {
    const files = this.fotoFileDaCaricare;
    if (files.length === 0) {
      return of(undefined);
    }
    return from(files).pipe(
      concatMap((file) => this.fotoService.uploadFoto(annuncioId, file)),
      last(),
      map((): void => undefined),
    );
  }

  salvaAnnuncio(): void {
    const wasCreate = this.editingId == null;
    const editingIdCorrente = this.editingId;
    /** Solo nuove foto, form pristine → niente PUT. */
    const soloUploadFotoInModifica =
      !wasCreate &&
      !this.annuncioForm.dirty &&
      this.fotoFileDaCaricare.length > 0;

    if (!soloUploadFotoInModifica && this.annuncioForm.invalid) {
      this.annuncioForm.markAllAsTouched();
      return;
    }
    if (!this.salvaAnnuncioAbilitato) {
      return;
    }
    this.messaggio = null;
    const raw = this.annuncioForm.getRawValue();
    const body: AnnuncioRequest = {
      titolo: raw.titolo.trim(),
      descrizione: raw.descrizione.trim(),
      prezzo: Number(raw.prezzo),
      mq: Number(raw.mq),
      categoria: raw.categoria,
      tipo: raw.tipo,
      indirizzo: raw.indirizzo.trim(),
    };

    /** Conteggio coda prima che salvaAnnuncio la svuoti. */
    const nFotoCaricate = this.fotoFileDaCaricare.length;

    /** POST/PUT (se serve) poi concatMap upload in coda. */
    const flusso$ = soloUploadFotoInModifica
      ? of(undefined).pipe(
          tap(() => {
            this.errore = null;
          }),
          concatMap(() => this.uploadFotoInCodaPerAnnuncio(editingIdCorrente!)),
        )
      : wasCreate
        ? this.annuncioService.creaAnnuncio(body).pipe(
            tap(() => {
              this.errore = null;
            }),
            concatMap((annuncio) => this.uploadFotoInCodaPerAnnuncio(annuncio.id)),
          )
        : this.annuncioService.modificaAnnuncio(editingIdCorrente!, body).pipe(
            tap(() => {
              this.errore = null;
            }),
            concatMap(() => this.uploadFotoInCodaPerAnnuncio(editingIdCorrente!)),
          );

    flusso$
      .pipe(
        catchError((err: unknown) => {
          this.messaggio = null;
          this.errore = this.msgErroreHttp(err);
          return EMPTY;
        }),
      )
      .subscribe({
        next: () => {
          let msg: string;
          if (soloUploadFotoInModifica) {
            msg = 'Foto caricate.';
          } else {
            msg = wasCreate ? 'Annuncio creato.' : 'Annuncio aggiornato.';
            if (nFotoCaricate > 0) {
              msg += ' Foto caricate.';
            }
          }
          this.messaggio = msg;
          this.fotoFileDaCaricare = [];
          this.fotoEsistenti = [];
          this.fotoEsistentiLoading = false;
          this.eliminazioneFotoId = null;
          this.destroyFormMap();
          this.erroreIndirizzoManuale = null;
          this.mostraForm = false;
          this.editingId = null;
          this.carica();
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
    return 'Operazione non riuscita.';
  }

  elimina(a: Annuncio): void {
    if (!confirm(`Eliminare l’annuncio «${a.titolo}»?`)) {
      return;
    }
    this.annuncioService.eliminaAnnuncio(a.id).subscribe({
      next: () => {
        this.messaggio = 'Annuncio eliminato.';
        this.carica();
      },
      error: () => {
        this.errore = 'Eliminazione non consentita o errore di rete.';
      },
    });
  }

  apriRibassa(a: Annuncio): void {
    const tra7g = new Date();
    tra7g.setDate(tra7g.getDate() + 7);
    const defaultFine = tra7g.toISOString().slice(0, 16);
    this.ribassaForm.patchValue({
      annuncioId: a.id,
      nuovoPrezzo: Math.max(0.01, a.prezzo * 0.95),
      modalitaRibasso: 'permanente',
      ribassoFine: defaultFine,
    });
  }

  inviaRibassa(): void {
    if (this.ribassaForm.invalid) {
      this.ribassaForm.markAllAsTouched();
      return;
    }
    const { annuncioId, nuovoPrezzo, modalitaRibasso, ribassoFine } = this.ribassaForm.getRawValue();
    let fineIso: string | null = null;
    if (modalitaRibasso === 'temporaneo') {
      const raw = ribassoFine?.trim() ?? '';
      if (!raw) {
        this.errore = 'Indica data e ora di fine del ribasso temporaneo.';
        this.ribassaForm.get('ribassoFine')?.markAsTouched();
        return;
      }
      let iso = raw;
      if (iso.length === 16) {
        iso = `${iso}:00`;
      }
      const scadenza = Date.parse(iso);
      if (Number.isNaN(scadenza) || scadenza <= Date.now()) {
        this.errore = 'La fine del ribasso deve essere nel futuro.';
        return;
      }
      fineIso = iso;
    }
    this.annuncioService.ribassaPrezzo(annuncioId, nuovoPrezzo, fineIso).subscribe({
      next: () => {
        this.messaggio =
          modalitaRibasso === 'temporaneo'
            ? 'Prezzo ribassato (promo temporanea impostata).'
            : 'Prezzo ribassato.';
        this.ribassaForm.patchValue({
          annuncioId: 0,
          nuovoPrezzo: 0,
          modalitaRibasso: 'permanente',
          ribassoFine: '',
        });
        this.carica();
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 400) {
          this.errore =
            'Controlla il nuovo prezzo (deve essere inferiore all’attuale) e, se promo temporanea, la data di fine nel futuro.';
          return;
        }
        this.errore = 'Ribasso non riuscito.';
      },
    });
  }

  apriAsta(a: Annuncio): void {
    const d = new Date();
    d.setDate(d.getDate() + 7);
    const local = d.toISOString().slice(0, 16);
    this.astaForm.patchValue({
      annuncioId: a.id,
      prezzoBase: a.prezzo,
      scadenza: local,
    });
  }

  /** Sincronizza “offerte viste” sul server e aggiorna UI. */
  segnaOfferteAsteViste(): void {
    this.errore = null;
    this.astaService.segnaOfferteVisteVenditore().subscribe({
      next: () => this.carica(),
      error: () => {
        this.errore = 'Impossibile aggiornare lo stato delle offerte.';
      },
    });
  }

  creaAsta(): void {
    if (this.astaForm.invalid) {
      this.astaForm.markAllAsTouched();
      return;
    }
    const { annuncioId, prezzoBase, scadenza } = this.astaForm.getRawValue();
    let iso = scadenza;
    if (iso.length === 16) {
      iso = `${iso}:00`;
    }
    this.astaService.creaAsta(prezzoBase, iso, annuncioId).subscribe({
      next: () => {
        this.messaggio = 'Asta creata.';
        this.astaForm.patchValue({ annuncioId: 0, prezzoBase: 0, scadenza: '' });
        this.carica();
      },
      error: (err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 400) {
          this.errore = 'Esiste già un’asta per questo annuncio.';
          return;
        }
        this.errore = 'Creazione asta non riuscita.';
      },
    });
  }

  /** Messaggi per annuncio (venditore proprietario). */
  caricaMessaggi(annuncioId: number): void {
    this.messaggioService.getMessaggiAnnuncio(annuncioId).subscribe({
      next: (data) => {
        this.messaggi = Array.isArray(data) ? data : [];
        this.messaggiAnnuncioId = annuncioId;
        this.ricaricaRiepilogoNonLetti();
      },
      error: () => {
        this.errore = 'Impossibile caricare i messaggi.';
      },
    });
  }

  /** Delete messaggio (venditore o mittente, stesso endpoint). */
  eliminaMessaggioRicevuto(m: { id: number }): void {
    if (!confirm('Eliminare questo messaggio dalla tua posta?')) {
      return;
    }
    this.errore = null;
    this.eliminazioneMessaggioId = m.id;
    this.messaggioService.eliminaMessaggio(m.id).subscribe({
      next: () => {
        this.eliminazioneMessaggioId = null;
        if (this.messaggiAnnuncioId != null) {
          this.caricaMessaggi(this.messaggiAnnuncioId);
        } else {
          this.ricaricaRiepilogoNonLetti();
        }
      },
      error: () => {
        this.eliminazioneMessaggioId = null;
        this.errore = 'Impossibile eliminare il messaggio.';
      },
    });
  }

  /** Chiudi pannello messaggi. */
  chiudiMessaggi(): void {
    this.messaggi = [];
    this.messaggiAnnuncioId = null;
  }

  /** Titolo annuncio aperto nei messaggi (da lista). */
  titoloMessaggiAnnuncio(): string {
    if (this.messaggiAnnuncioId == null) {
      return '';
    }
    const a = this.annunci.find((x) => x.id === this.messaggiAnnuncioId);
    return a?.titolo ?? '';
  }

  eliminaAccount(): void {
    this.errore = null;
    this.messaggio = null;
    if (
      !confirm(
        'Eliminare definitivamente il tuo account venditore? Tutti i tuoi annunci, le foto, le aste e i messaggi collegati verranno rimossi. Questa azione non può essere annullata.'
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
        if (err instanceof HttpErrorResponse && err.status === 403) {
          this.errore = 'Operazione non consentita.';
          return;
        }
        this.errore = 'Impossibile eliminare l’account. Riprova più tardi.';
      },
    });
  }
}
