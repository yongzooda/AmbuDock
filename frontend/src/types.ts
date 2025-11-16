// src/types.ts

export type HliLevel = 'GREEN' | 'YELLOW' | 'RED' | 'GRAY';
export type StaleLevel = 'FRESH' | 'SOFT' | 'HARD';
export type Severity = 'IGNORE' | 'WARN' | 'BLOCK';

export type DataFreshness = Extract<StaleLevel, 'FRESH' | 'SOFT'>;

export interface HliSummary {
  score: number;
  level: HliLevel;
  color?: string;
  lagSec: number;
  staleLevel: StaleLevel;
  updatedAt: string; // ISO UTC
}

export interface HospitalListItem {
  hpid: string;
  name: string;
  lat: number;
  lon: number;
  addrRoad: string;
  telEmergency: string;
  distanceKm: number;
  hli: HliSummary;
}

export interface NearbyResponse { items: HospitalListItem[]; }

export interface DisasterEvaluateItem {
  hpid: string;
  blocked: boolean;
  badges: string[];
  severity?: Severity | null;
}
export interface DisasterEvaluateResponse { results: DisasterEvaluateItem[]; }

export interface TmapCandidate { hpid: string; lat: number; lon: number; }
export interface TmapRouteResult {
  hpid: string;
  eta_car_min: number | null;
  eta_walk_min: number | null;
  distance_line_km: number;
  route_car_km: number | null;
  route_walk_km: number | null;
  source: 'TMAP' | 'ESTIMATE';
}
export interface TmapRouteResponse {
  items: TmapRouteResult[];
  durationMs: number;
  source: 'TMAP' | 'ESTIMATE';
  cached: number;
}

export interface HospitalDetail {
  hpid: string;
  name: string;
  addrRoad: string;
  telEmergency: string;
  hli: HliSummary;
  hvec: number;
  hv2?: number; hv3?: number; hv4?: number; hv5?: number; hv6?: number; hvoc?: number;
}
