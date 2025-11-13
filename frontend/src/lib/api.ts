// src/lib/api.ts
import type {
  NearbyResponse, DisasterEvaluateResponse, TmapRouteResponse,
  TmapCandidate, HospitalDetail
} from '../types';

const API_BASE = import.meta.env.VITE_API_BASE || '/api';

async function jfetch<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) }
  });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return res.json() as Promise<T>;
}

export const Api = {
  listNearby: (center: string, radiusKm: number) =>
    jfetch<NearbyResponse>(`${API_BASE}/hospitals?center=${encodeURIComponent(center)}&radius_km=${radiusKm}`),

  evalDisasters: (hpids: string[]) =>
    jfetch<DisasterEvaluateResponse>(`${API_BASE}/disasters/evaluate`, {
      method: 'POST', body: JSON.stringify({ hpids })
    }),

  tmapRoute: (origin: string, candidates: TmapCandidate[]) =>
    jfetch<TmapRouteResponse>(`${API_BASE}/tmap/route`, {
      method: 'POST', body: JSON.stringify({ origin, candidates, options:{ car:true, walk:true } })
    }),

  hospitalDetail: (hpid: string) =>
    jfetch<HospitalDetail>(`${API_BASE}/hospitals/${encodeURIComponent(hpid)}`),
};
