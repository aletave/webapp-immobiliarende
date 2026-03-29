import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

import type { Annuncio } from '../../../models/annuncio.model';

/** Card annuncio in griglia con routerLink al dettaglio. */
@Component({
  selector: 'app-annuncio-card',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './annuncio-card.component.html',
  styleUrl: './annuncio-card.component.scss',
})
export class AnnuncioCardComponent {
  /** Annuncio da mostrare. */
  @Input({ required: true }) annuncio!: Annuncio;

  /** C’è prezzo precedente da barrare. */
  haPrezzoPrecedente(): boolean {
    return this.annuncio.prezzoPrecedente != null && this.annuncio.prezzoPrecedente !== undefined;
  }

  /** Ribasso con fine ancora nel futuro. */
  ribassoTemporaneoAttivo(): boolean {
    const f = this.annuncio.ribassoFine;
    if (f == null || f === '') {
      return false;
    }
    const t = Date.parse(f);
    return !Number.isNaN(t) && t > Date.now();
  }

  /** Nome venditore in una riga. */
  nomeVenditoreCompleto(): string {
    const n = this.annuncio.nomeVenditore?.trim() ?? '';
    const c = this.annuncio.cognomeVenditore?.trim() ?? '';
    const full = `${n} ${c}`.trim();
    return full || 'Venditore';
  }
}
