import { CommonModule } from '@angular/common';
import { Component, HostListener, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AnnuncioService, type FiltriAnnunci } from '../../core/services/annuncio.service';
import type { Annuncio } from '../../models/annuncio.model';
import { AnnuncioCardComponent } from '../../shared/components/annuncio-card/annuncio-card.component';

/** Home: griglia annunci con filtri e ordinamento. */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AnnuncioCardComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  private readonly annuncioService = inject(AnnuncioService);

  /** Annunci in griglia. */
  annunci: Annuncio[] = [];

  /** Vendita/affitto; vuoto = entrambi. */
  filterTipo = '';

  /** Categoria; vuoto = tutte. */
  filterCategoria = '';

  /** Select ordinamento → orderBy/direction in GET. */
  filterOrdine = 'prezzo-asc';

  /** Pulsante torna su dopo scroll. */
  mostraTornaSu = false;

  private readonly sogliaScrollTornaSu = 360;

  ngOnInit(): void {
    this.caricaAnnunci();
    this.aggiornaVisibilitaTornaSu();
  }

  @HostListener('window:scroll')
  onWindowScroll(): void {
    this.aggiornaVisibilitaTornaSu();
  }

  private aggiornaVisibilitaTornaSu(): void {
    const y =
      window.scrollY ??
      document.documentElement.scrollTop ??
      document.body.scrollTop ??
      0;
    this.mostraTornaSu = y > this.sogliaScrollTornaSu;
  }

  /** Scroll in cima (smooth se non chiedi motion ridotta). */
  tornaSu(): void {
    const ridotto =
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (ridotto) {
      window.scrollTo(0, 0);
    } else {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  /** Ricarica lista dal server. */
  caricaAnnunci(): void {
    const filtri = this.costruisciFiltri();
    this.annuncioService.getAnnunci(filtri).subscribe({
      next: (data) => {
        this.annunci = data;
      },
      error: () => {
        this.annunci = [];
      },
    });
  }

  /** Cerca: stesso di caricaAnnunci (bottone form). */
  cerca(): void {
    this.caricaAnnunci();
  }

  /** Costruisce query GET per l’API annunci. */
  private costruisciFiltri(): FiltriAnnunci {
    const out: FiltriAnnunci = {};
    if (this.filterTipo.trim()) {
      out.tipo = this.filterTipo.trim();
    }
    if (this.filterCategoria.trim()) {
      out.categoria = this.filterCategoria.trim();
    }
    const { orderBy, direction } = this.mappaOrdinamento(this.filterOrdine);
    out.orderBy = orderBy;
    out.direction = direction;
    return out;
  }

  /** Select → orderBy + direction. */
  private mappaOrdinamento(chiave: string): { orderBy: string; direction: string } {
    switch (chiave) {
      case 'prezzo-desc':
        return { orderBy: 'prezzo', direction: 'desc' };
      case 'mq-asc':
        return { orderBy: 'mq', direction: 'asc' };
      case 'mq-desc':
        return { orderBy: 'mq', direction: 'desc' };
      case 'prezzo-asc':
      default:
        return { orderBy: 'prezzo', direction: 'asc' };
    }
  }
}
