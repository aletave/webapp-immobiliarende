import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import * as L from 'leaflet';

import { AnnuncioService } from '../../core/services/annuncio.service';
import type { Annuncio } from '../../models/annuncio.model';

/** Mappa Leaflet: annunci con coordinate, popup verso dettaglio. */
@Component({
  selector: 'app-mappa-annunci',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './mappa-annunci.component.html',
  styleUrl: './mappa-annunci.component.scss',
})
export class MappaAnnunciComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly annuncioService = inject(AnnuncioService);
  private readonly route = inject(ActivatedRoute);

  /** Annunci da API. */
  annunci: Annuncio[] = [];

  /** Filtra pin per tipo; vuoto = tutti. */
  filterTipo = '';

  private map: L.Map | null = null;
  private markerLayer: L.LayerGroup | null = null;

  ngOnInit(): void {
    const tipoQ = this.route.snapshot.queryParamMap.get('tipo');
    if (tipoQ === 'VENDITA' || tipoQ === 'AFFITTO') {
      this.filterTipo = tipoQ;
    }

    // Evita marker “rotti” se si usa l’icona di default di Leaflet in altri contesti.
    const iconDefault = L.icon({
      iconUrl: 'assets/marker-icon.png',
      shadowUrl: 'assets/marker-shadow.png',
      iconSize: [25, 41],
      iconAnchor: [12, 41],
    });
    L.Marker.prototype.options.icon = iconDefault;

    this.annuncioService.getAnnunci().subscribe({
      next: (data) => {
        this.annunci = data;
        this.aggiungiMarkerSePronto();
      },
      error: () => {
        this.annunci = [];
        this.aggiungiMarkerSePronto();
      },
    });
  }

  ngAfterViewInit(): void {
    this.inizializzaMappa();
    this.aggiungiMarkerSePronto();
  }

  ngOnDestroy(): void {
    this.map?.remove();
    this.map = null;
    this.markerLayer = null;
  }

  /** Inizializza Leaflet sul div quando c’è il DOM. */
  private inizializzaMappa(): void {
    if (this.map) {
      return;
    }
    const el = document.getElementById('mappa-leaflet');
    if (!el) {
      return;
    }
    this.map = L.map('mappa-leaflet').setView([39.35, 16.18], 13);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors',
    }).addTo(this.map);
    this.markerLayer = L.layerGroup().addTo(this.map);
    setTimeout(() => this.map?.invalidateSize(), 0);
  }

  /** Marker quando mappa e annunci sono pronti (init asincrono). */
  private aggiungiMarkerSePronto(): void {
    if (!this.map || !this.markerLayer) {
      return;
    }
    this.markerLayer.clearLayers();
    const validi: L.LatLng[] = [];
    const iconVendita = this.creaIconaPin('#B45309');
    const iconAffitto = this.creaIconaPin('#0D9488');

    for (const a of this.annunci) {
      if (this.filterTipo && a.tipo !== this.filterTipo) {
        continue;
      }
      if (a.lat == null || a.lng == null || Number.isNaN(a.lat) || Number.isNaN(a.lng)) {
        continue;
      }
      const latlng = L.latLng(a.lat, a.lng);
      validi.push(latlng);
      const icon = a.tipo === 'AFFITTO' ? iconAffitto : iconVendita;
      const marker = L.marker(latlng, { icon });
      marker.bindPopup(this.costruisciPopupHtml(a));
      marker.addTo(this.markerLayer);
    }

    if (validi.length > 0) {
      const bounds = L.latLngBounds(validi);
      this.map.fitBounds(bounds, { padding: [48, 48], maxZoom: 15 });
    } else {
      this.map.setView([39.35, 16.18], 13);
    }
  }

  /** Pin a goccia con colore per vendita (ambra) o affitto (teal). */
  private creaIconaPin(colore: string): L.DivIcon {
    const html = `<div style="background:${colore}; width:32px; height:32px; border-radius:50% 50% 50% 0; transform:rotate(-45deg); border: 3px solid white; box-shadow: 0 2px 8px rgba(0,0,0,0.3)"></div>`;
    return L.divIcon({
      className: 'mappa-annunci__div-icon',
      html,
      iconSize: [32, 32],
      iconAnchor: [16, 32],
      popupAnchor: [0, -28],
    });
  }

  /** Ricalcola i marker dopo cambio filtro dalla UI. */
  onFiltroTipoChange(): void {
    this.aggiungiMarkerSePronto();
  }

  private costruisciPopupHtml(a: Annuncio): string {
    const titolo = this.escapeHtml(a.titolo);
    const prezzo = new Intl.NumberFormat('it-IT', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(a.prezzo);
    const cat = this.escapeHtml(a.categoria);
    const mq = a.mq ?? '';
    const tipoLabel = a.tipo === 'AFFITTO' ? 'Affitto' : 'Vendita';
    const tipoColor = a.tipo === 'AFFITTO' ? '#0D9488' : '#B45309';
    return `
      <div style="font-family: Outfit, sans-serif; min-width: 200px">
        <strong style="font-family: Cormorant Garamond, serif; font-size: 1rem">${titolo}</strong>
        <p style="color: ${tipoColor}; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em; margin: 6px 0 2px">
          ${tipoLabel}
        </p>
        <p style="color: ${tipoColor}; font-size: 1.1rem; font-weight: 600; margin: 4px 0">
          ${prezzo} €
        </p>
        <p style="color: #78716C; font-size: 0.8rem; margin: 0">
          ${cat} · ${mq} m²
        </p>
        <a href="/annunci/${a.id}"
           style="display: block; margin-top: 8px; background: #1C1917; color: white; text-align: center; padding: 6px 12px; border-radius: 6px; text-decoration: none; font-size: 0.8rem">
          Vedi annuncio →
        </a>
      </div>`;
  }

  private escapeHtml(s: string): string {
    return s
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
}
