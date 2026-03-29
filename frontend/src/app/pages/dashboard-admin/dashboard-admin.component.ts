import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  inject,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { Chart, type ChartConfiguration } from 'chart.js/auto';

import { parseSpringErrorMessage } from '../../core/utils/http-error.util';
import { AdminService } from '../../core/services/admin.service';
import { AppuntamentoService } from '../../core/services/appuntamento.service';
import { AuthService } from '../../core/services/auth.service';
import { AnnuncioService } from '../../core/services/annuncio.service';
import type { Appuntamento } from '../../models/appuntamento.model';
import type { Asta } from '../../models/asta.model';
import type { Annuncio } from '../../models/annuncio.model';
import { nomeCompletoAutoreRecensione, type Recensione } from '../../models/recensione.model';
import { IconTrashComponent } from '../../shared/components/icon-trash/icon-trash.component';
import type { Utente } from '../../models/utente.model';

@Component({
  selector: 'app-dashboard-admin',
  standalone: true,
  imports: [CommonModule, RouterLink, IconTrashComponent],
  templateUrl: './dashboard-admin.component.html',
  styleUrl: './dashboard-admin.component.scss',
})
export class DashboardAdminComponent implements OnInit, OnDestroy {
  private readonly adminService = inject(AdminService);
  private readonly annuncioService = inject(AnnuncioService);
  private readonly appuntamentoService = inject(AppuntamentoService);
  private readonly authService = inject(AuthService);

  @ViewChild('canvasCategoria') canvasCategoria!: ElementRef<HTMLCanvasElement>;
  @ViewChild('canvasTipo') canvasTipo!: ElementRef<HTMLCanvasElement>;

  utenti: Utente[] = [];
  annunci: Annuncio[] = [];
  /** Annunci “miei” se questo admin ha anche annunci da venditore. */
  mieiAnnunci: Annuncio[] = [];
  aste: Asta[] = [];
  recensioni: Recensione[] = [];
  statsCategoria: Record<string, number> = {};
  statsTipo: Record<string, number> = {};

  /** Ultime visite (admin vede tutte). */
  appuntamentiRecenti: Appuntamento[] = [];
  /** Conteggio appuntamenti prima dello slice a 10. */
  appuntamentiTotalCount = 0;
  completaAppuntamentoId: number | null = null;
  rifiutaAppuntamentoId: number | null = null;
  erroreAppuntamento: string | null = null;

  readonly maxAppuntamentiRecenti = 10;

  loading = true;
  errore: string | null = null;
  messaggio: string | null = null;

  /** Benvenuto in testa. */
  nomeBenvenuto = '';

  readonly nomeCompletoAutoreRecensione = nomeCompletoAutoreRecensione;

  /** Accordion sezioni: partono chiuse. */
  sezioneAppuntamentiAperta = false;
  sezioneUtentiAperta = false;
  sezioneAnnunciAperta = false;
  sezioneAsteAperta = false;
  sezioneRecensioniAperta = false;

  /** Fino a che data consideri “visti” gli annunci (localStorage); primo avvio = max createdAt. */
  private readonly storageKeyAnnunciLetti = 'admin-dashboard-annunci-letti-fino';
  nuoviAnnunciCount = 0;

  private chartCat: Chart | null = null;
  private chartTipo: Chart | null = null;

  ngOnInit(): void {
    const u = this.authService.getUser();
    const n = u?.nome?.trim() ?? '';
    const c = u?.cognome?.trim() ?? '';
    this.nomeBenvenuto = `${n} ${c}`.trim();
    forkJoin({
      utenti: this.adminService.getUtenti(),
      annunci: this.annuncioService.getAnnunci(),
      miei: this.annuncioService.getAnnunciMiei().pipe(catchError(() => of<Annuncio[]>([]))),
      aste: this.adminService.getAste().pipe(catchError(() => of<Asta[]>([]))),
      recensioni: this.adminService.getRecensioni(),
      cat: this.adminService.getStatsAnnunciPerCategoria(),
      tipo: this.adminService.getStatsAnnunciPerTipo(),
      appuntamenti: this.appuntamentoService
        .getPerVenditore()
        .pipe(catchError(() => of<Appuntamento[]>([]))),
    }).subscribe({
      next: ({ utenti, annunci, miei, aste, recensioni, cat, tipo, appuntamenti }) => {
        this.utenti = utenti;
        this.mieiAnnunci = miei ?? [];
        this.annunci = [...annunci].sort((a, b) => a.id - b.id);
        this.initBaselineAnnunciNuovi(this.annunci);
        this.syncNuoviAnnunci();
        this.aste = [...aste].sort((a, b) => a.id - b.id);
        this.recensioni = recensioni;
        this.statsCategoria = cat;
        this.statsTipo = tipo;
        this.appuntamentiTotalCount = appuntamenti.length;
        this.appuntamentiRecenti = appuntamenti.slice(0, this.maxAppuntamentiRecenti);
        this.loading = false;
        setTimeout(() => this.buildCharts(), 0);
      },
      error: () => {
        this.errore = 'Impossibile caricare i dati amministratore.';
        this.loading = false;
      },
    });
  }

  ngOnDestroy(): void {
    this.chartCat?.destroy();
    this.chartTipo?.destroy();
  }

  private buildCharts(): void {
    if (!this.canvasCategoria?.nativeElement || !this.canvasTipo?.nativeElement) {
      return;
    }
    const labelsCat = Object.keys(this.statsCategoria);
    const dataCat = Object.values(this.statsCategoria);
    const labelsTipo = Object.keys(this.statsTipo);
    const dataTipo = Object.values(this.statsTipo);

    this.chartCat?.destroy();
    this.chartTipo?.destroy();

    const cfgBar: ChartConfiguration = {
      type: 'bar',
      data: {
        labels: labelsCat.length ? labelsCat : ['—'],
        datasets: [
          {
            label: 'Annunci per categoria',
            data: labelsCat.length ? dataCat : [0],
            backgroundColor: 'rgba(13, 110, 253, 0.55)',
            borderColor: 'rgba(13, 110, 253, 1)',
            borderWidth: 1,
          },
        ],
      },
      options: {
        responsive: true,
        plugins: {
          legend: { display: false },
          title: { display: true, text: 'Annunci per categoria' },
        },
        scales: {
          y: { beginAtZero: true, ticks: { stepSize: 1 } },
        },
      },
    };

    const cfgDoughnut: ChartConfiguration = {
      type: 'doughnut',
      data: {
        labels: labelsTipo.length ? labelsTipo : ['—'],
        datasets: [
          {
            data: labelsTipo.length ? dataTipo : [0],
            backgroundColor: ['#0d6efd', '#6610f2', '#6c757d'],
          },
        ],
      },
      options: {
        responsive: true,
        plugins: {
          title: { display: true, text: 'Distribuzione VENDITA / AFFITTO' },
        },
      },
    };

    this.chartCat = new Chart(this.canvasCategoria.nativeElement, cfgBar);
    this.chartTipo = new Chart(this.canvasTipo.nativeElement, cfgDoughnut);
  }

  toggleBan(u: Utente): void {
    this.messaggio = null;
    this.errore = null;
    const req = u.bannato
      ? this.adminService.sbannaUtente(u.id)
      : this.adminService.bannaUtente(u.id);
    req.subscribe({
      next: (agg) => {
        const i = this.utenti.findIndex((x) => x.id === agg.id);
        if (i >= 0) {
          this.utenti[i] = agg;
        }
        this.messaggio = agg.bannato
          ? 'Utente bannato.'
          : 'Utente sbannato: può di nuovo accedere.';
      },
      error: () => {
        this.errore = 'Operazione non riuscita.';
      },
    });
  }

  promuovi(u: Utente): void {
    if (!confirm(`Promuovere ${u.email} ad ADMIN?`)) {
      return;
    }
    this.adminService.promuoviAdmin(u.id).subscribe({
      next: (agg) => {
        const i = this.utenti.findIndex((x) => x.id === agg.id);
        if (i >= 0) {
          this.utenti[i] = agg;
        }
        this.messaggio = 'Utente promosso ad amministratore.';
      },
      error: (err: unknown) => {
        this.errore =
          err instanceof HttpErrorResponse ? `Errore ${err.status}` : 'Errore.';
      },
    });
  }

  eliminaAnnuncio(a: Annuncio): void {
    if (!confirm(`Eliminare definitivamente l’annuncio #${a.id}?`)) {
      return;
    }
    this.adminService.eliminaAnnuncio(a.id).subscribe({
      next: () => {
        this.annunci = this.annunci.filter((x) => x.id !== a.id);
        this.mieiAnnunci = this.mieiAnnunci.filter((x) => x.id !== a.id);
        this.syncNuoviAnnunci();
        this.messaggio = 'Annuncio eliminato.';
        this.refreshStats();
      },
      error: () => {
        this.errore = 'Eliminazione non riuscita.';
      },
    });
  }

  titoloAnnuncioAsta(ev: Asta): string {
    return ev.annuncioTitolo?.trim()
      ? ev.annuncioTitolo.trim()
      : `Annuncio #${ev.annuncioId}`;
  }

  nomeAcquirenteAppuntamento(a: Appuntamento): string {
    const n = a.nomeAcquirente?.trim() ?? '';
    const c = a.cognomeAcquirente?.trim() ?? '';
    const t = `${n} ${c}`.trim();
    return t || a.emailAcquirente || 'Acquirente';
  }

  /** Completa una visita PENDING (come venditore, ma per admin su tutte). */
  segnaVisitaCompletata(a: Appuntamento): void {
    if (a.stato !== 'PENDING') {
      return;
    }
    this.erroreAppuntamento = null;
    this.completaAppuntamentoId = a.id;
    this.appuntamentoService.completa(a.id).subscribe({
      next: (agg) => {
        const idx = this.appuntamentiRecenti.findIndex((x) => x.id === a.id);
        if (idx >= 0) {
          this.appuntamentiRecenti[idx] = agg;
        }
        this.completaAppuntamentoId = null;
        this.messaggio = 'Visita segnata come completata.';
      },
      error: (err: unknown) => {
        this.completaAppuntamentoId = null;
        const msg = parseSpringErrorMessage(err);
        this.erroreAppuntamento =
          msg
          ?? (err instanceof HttpErrorResponse && err.status === 403
            ? 'Operazione non consentita su questa richiesta.'
            : err instanceof HttpErrorResponse && err.status === 404
              ? 'Richiesta non trovata.'
              : 'Impossibile completare la visita. Riprova.');
      },
    });
  }

  /** Rifiuta una visita PENDING (stesso endpoint del venditore). */
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
        const idx = this.appuntamentiRecenti.findIndex((x) => x.id === a.id);
        if (idx >= 0) {
          this.appuntamentiRecenti[idx] = agg;
        }
        this.rifiutaAppuntamentoId = null;
        this.messaggio = 'Richiesta di visita rifiutata.';
      },
      error: (err: unknown) => {
        this.rifiutaAppuntamentoId = null;
        const msg = parseSpringErrorMessage(err);
        this.erroreAppuntamento =
          msg
          ?? (err instanceof HttpErrorResponse && err.status === 403
            ? 'Operazione non consentita su questa richiesta.'
            : err instanceof HttpErrorResponse && err.status === 404
              ? 'Richiesta non trovata.'
              : 'Impossibile rifiutare la visita. Riprova.');
      },
    });
  }

  astaScaduta(scadenzaIso: string): boolean {
    const t = Date.parse(scadenzaIso);
    if (Number.isNaN(t)) {
      return false;
    }
    return t < Date.now();
  }

  eliminaAsta(ev: Asta): void {
    if (
      !confirm(
        `Rimuovere l’asta #${ev.id} sull’annuncio «${this.titoloAnnuncioAsta(ev)}»? L’annuncio resterà online; il venditore potrà creare una nuova asta.`
      )
    ) {
      return;
    }
    this.adminService.eliminaAsta(ev.id).subscribe({
      next: () => {
        this.aste = this.aste.filter((x) => x.id !== ev.id);
        this.messaggio = 'Asta rimossa.';
      },
      error: () => {
        this.errore = 'Eliminazione asta non riuscita.';
      },
    });
  }

  eliminaRecensione(r: Recensione): void {
    if (!confirm('Eliminare questa recensione?')) {
      return;
    }
    this.adminService.eliminaRecensione(r.id).subscribe({
      next: () => {
        this.recensioni = this.recensioni.filter((x) => x.id !== r.id);
        this.messaggio = 'Recensione eliminata.';
      },
      error: () => {
        this.errore = 'Eliminazione non riuscita.';
      },
    });
  }

  /** “Ho visto tutti”: baseline = max createdAt degli annunci in lista. */
  segnaAnnunciVisti(): void {
    if (typeof localStorage === 'undefined') {
      return;
    }
    const ts = this.annunci.length === 0 ? Date.now() : this.maxCreatedAtTs(this.annunci);
    localStorage.setItem(this.storageKeyAnnunciLetti, new Date(ts).toISOString());
    this.nuoviAnnunciCount = this.contaAnnunciNuovi(this.annunci);
  }

  /** Riga tabella evidenziata se l’annuncio è più recente della baseline salvata. */
  annuncioSegnalatoNuovo(a: Annuncio): boolean {
    if (typeof localStorage === 'undefined') {
      return false;
    }
    const raw = localStorage.getItem(this.storageKeyAnnunciLetti);
    if (raw == null) {
      return false;
    }
    const t0 = Date.parse(raw);
    if (Number.isNaN(t0)) {
      return false;
    }
    return this.tsCreatedAt(a.createdAt) > t0;
  }

  private initBaselineAnnunciNuovi(annunci: Annuncio[]): void {
    if (typeof localStorage === 'undefined') {
      return;
    }
    if (localStorage.getItem(this.storageKeyAnnunciLetti) != null) {
      return;
    }
    const ts = annunci.length === 0 ? Date.now() : this.maxCreatedAtTs(annunci);
    localStorage.setItem(this.storageKeyAnnunciLetti, new Date(ts).toISOString());
  }

  private syncNuoviAnnunci(): void {
    this.nuoviAnnunciCount = this.contaAnnunciNuovi(this.annunci);
    if (this.nuoviAnnunciCount > 0) {
      this.sezioneAnnunciAperta = true;
    }
  }

  private contaAnnunciNuovi(annunci: Annuncio[]): number {
    if (typeof localStorage === 'undefined') {
      return 0;
    }
    const raw = localStorage.getItem(this.storageKeyAnnunciLetti);
    if (raw == null) {
      return 0;
    }
    const t0 = Date.parse(raw);
    if (Number.isNaN(t0)) {
      return 0;
    }
    return annunci.filter((x) => this.tsCreatedAt(x.createdAt) > t0).length;
  }

  private maxCreatedAtTs(annunci: Annuncio[]): number {
    if (annunci.length === 0) {
      return Date.now();
    }
    return Math.max(...annunci.map((a) => this.tsCreatedAt(a.createdAt)));
  }

  private tsCreatedAt(createdAt: string): number {
    const t = Date.parse(this.normalizeCreatedAt(createdAt));
    return Number.isNaN(t) ? 0 : t;
  }

  /** Normalizza createdAt: stringa ISO o array numerico da Jackson. */
  private normalizeCreatedAt(createdAt: string): string {
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

  private refreshStats(): void {
    forkJoin({
      cat: this.adminService.getStatsAnnunciPerCategoria(),
      tipo: this.adminService.getStatsAnnunciPerTipo(),
    }).subscribe({
      next: ({ cat, tipo }) => {
        this.statsCategoria = cat;
        this.statsTipo = tipo;
        this.chartCat?.destroy();
        this.chartTipo?.destroy();
        this.chartCat = null;
        this.chartTipo = null;
        setTimeout(() => this.buildCharts(), 0);
      },
    });
  }
}
