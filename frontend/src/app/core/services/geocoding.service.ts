import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Stesso host degli altri servizi. */
const API_BASE_URL = environment.apiUrl;

/** Risposta anteprima geocoding (Nominatim tramite backend). */
export interface GeocodingSearchResult {
  lat: number;
  lng: number;
  displayName: string;
}

/** Geocoding che passa dal nostro Spring (Nominatim lì). */
@Injectable({ providedIn: 'root' })
export class GeocodingService {
  private readonly http = inject(HttpClient);

  cerca(indirizzo: string): Observable<GeocodingSearchResult> {
    const params = new HttpParams().set('q', indirizzo);
    return this.http.get<GeocodingSearchResult>(`${API_BASE_URL}/api/geocoding/search`, {
      params,
    });
  }

  reverseGeocode(lat: number, lng: number): Observable<GeocodingSearchResult> {
    const params = new HttpParams().set('lat', lat.toString()).set('lng', lng.toString());
    return this.http.get<GeocodingSearchResult>(`${API_BASE_URL}/api/geocoding/reverse`, {
      params,
    });
  }
}
