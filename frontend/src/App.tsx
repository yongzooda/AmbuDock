// src/App.tsx

import { useEffect, useMemo, useState } from 'react';
import { TopAppHeader } from './components/TopAppHeader';
import { TopSubline } from './components/TopSubline';
import { MapView } from './components/MapView';
import { BottomCardBar } from './components/BottomCardBar';
import { DetailHalfSheet } from './components/DetailHalfSheet';
import { TmapHandoffScreen } from './components/TmapHandoffScreen';
import { TmapOpenedOverlay } from './components/TmapOpenedOverlay';
import { SimpleOverlay } from './components/SimpleOverlay';

import { Api } from './lib/api';
import type {
  HospitalListItem,
  TmapCandidate,
  DataFreshness,
  HliLevel,
} from './types';
import { levelOrder, minutesFromSec } from './utils/format';
import { useGeolocation } from './hooks/useGeolocation';

type CardState = 'default' | 'loading' | 'eta-pending' | 'warn' | 'gray';

type CardModel = {
  id: string;
  rank: number;
  name: string;
  level: HliLevel;
  hli: number;
  minutesAgo: number;
  walkMin: number;
  carMin: number;
  distanceKm: number;
  state?: CardState;
  warnMessage?: string;
  freshness?: DataFreshness | null; // null 허용(외부에서 넘어오는 경우 방지)
  phone: string;
  address: string;
  erAvailable: number;
  icuAvailTotal: number;
  hv?: Partial<{
    hv2: number;
    hv3: number;
    hv4: number;
    hv5: number;
    hv6: number;
    hvoc: number;
  }>;
};

const DEFAULT_RADIUS_KM = 15;

/** CardModel 형태인지 검사하는 타입가드 */
function isCardModel(x: unknown): x is CardModel {
  if (!x || typeof x !== 'object') return false;
  const o = x as Record<string, unknown>;
  return (
    typeof o.id === 'string' &&
    typeof o.name === 'string' &&
    typeof o.hli === 'number' &&
    typeof o.level === 'string'
  );
}

/** 외부에서 받은 값을 CardModel로 정규화 (any 금지, unknown 사용) */
function normalizeCard(c: unknown): CardModel {
  if (isCardModel(c)) {
    return {
      ...c,
      // null → undefined로 정규화(표시 생략)
      freshness: c.freshness === null ? undefined : c.freshness,
      walkMin: c.walkMin ?? 0,
      carMin: c.carMin ?? 0,
      erAvailable: c.erAvailable ?? 0,
      icuAvailTotal: c.icuAvailTotal ?? 0,
    };
  }
  // HospitalListItem처럼 보이면 기본 매핑
  const h = c as Partial<HospitalListItem>;
  return {
    id: String(h?.hpid ?? ''),
    rank: 0,
    name: String(h?.name ?? ''),
    level: (h?.hli?.level ?? 'GRAY') as HliLevel,
    hli: Number(h?.hli?.score ?? 0),
    minutesAgo: minutesFromSec(Number(h?.hli?.lagSec ?? 0)),
    walkMin: 0,
    carMin: 0,
    distanceKm: Number(
      typeof h?.distanceKm === 'number' ? Number(h.distanceKm.toFixed(1)) : 0,
    ),
    state: (h?.hli?.level === 'GRAY' ? 'gray' : 'eta-pending') as CardState,
    warnMessage: undefined,
    freshness:
      h?.hli?.staleLevel === 'FRESH'
        ? 'FRESH'
        : h?.hli?.staleLevel === 'SOFT'
        ? 'SOFT'
        : undefined,
    phone: String(h?.telEmergency ?? ''),
    address: String(h?.addrRoad ?? ''),
    erAvailable: 0,
    icuAvailTotal: 0,
  };
}

export default function App() {
  const { coords, error: geoError } = useGeolocation();
  const [radiusKm] = useState(DEFAULT_RADIUS_KM);

  const [allHospitals, setAllHospitals] = useState<HospitalListItem[]>([]);
  const [hasLoadedHospitals, setHasLoadedHospitals] = useState(false);
  const [cards, setCards] = useState<CardModel[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [detail, setDetail] = useState<{ open: boolean; card?: CardModel }>({
    open: false,
  });
  const [handoff, setHandoff] = useState<{ open: boolean; name?: string }>({
    open: false,
  });
  const [opened, setOpened] = useState(false);
  const [geoOverlayClosed, setGeoOverlayClosed] = useState(false);

  useEffect(() => {
    (async () => {
      if (!coords) return;

      const center = `${coords.lat},${coords.lon}`;
      const nearby = await Api.listNearby(center, radiusKm);
      setAllHospitals(nearby.items);
      setHasLoadedHospitals(true);

      const hpids = nearby.items.map((h) => h.hpid);
      if (hpids.length === 0) {
        setCards([]);
        setSelectedId(null);
        return;
      }

      const dis = await Api.evalDisasters(hpids);
      const warnMap = new Map(dis.results.map((r) => [r.hpid, r]));

      // BLOCK 제외
      const filtered = nearby.items.filter(
        (h) => !warnMap.get(h.hpid)?.blocked,
      );
      const k = filtered.slice(
        0,
        Math.min(10, Math.max(5, filtered.length)),
      );

      // 후보가 없으면 종료
      if (k.length === 0) {
        setCards([]);
        setSelectedId(null);
        return;
      }

      // ETA 대기 스켈레톤
      const skeleton: CardModel[] = k.map((h, i) => ({
        id: h.hpid,
        rank: i + 1,
        name: h.name,
        level: h.hli.level,
        hli: h.hli.score,
        minutesAgo: minutesFromSec(h.hli.lagSec),
        walkMin: 0,
        carMin: 0,
        distanceKm: Number(h.distanceKm.toFixed(1)),
        state: h.hli.level === 'GRAY' ? 'gray' : 'eta-pending',
        warnMessage: warnMap.get(h.hpid)?.badges?.join(' · ') || undefined,
        // HARD는 표시 생략(undefined)
        freshness:
          h.hli.staleLevel === 'FRESH'
            ? 'FRESH'
            : h.hli.staleLevel === 'SOFT'
            ? 'SOFT'
            : undefined,
        phone: h.telEmergency || '',
        address: h.addrRoad || '',
        erAvailable: 0,
        icuAvailTotal: 0,
      }));
      setCards(skeleton);

      // TMAP ETA 병합
      const candidates: TmapCandidate[] = k.map((h) => ({
        hpid: h.hpid,
        lat: h.lat,
        lon: h.lon,
      }));
      const route = await Api.tmapRoute(center, candidates);

      const etaMap = new Map(route.items.map((r) => [r.hpid, r]));
      const merged: CardModel[] = skeleton.map((c) => {
        const r = etaMap.get(c.id);
        const nextState: CardState = c.state === 'gray' ? 'gray' : 'default';
        return {
          ...c,
          carMin: r?.eta_car_min ?? 0,
          walkMin: r?.eta_walk_min ?? 0,
          state: nextState,
        };
      });

      // 정렬: Level → HLI desc → ETA_car asc
      merged.sort((a, b) => {
        const lv = levelOrder[a.level] - levelOrder[b.level];
        if (lv !== 0) return lv;
        if (b.hli !== a.hli) return b.hli - a.hli;
        return (a.carMin || 1e9) - (b.carMin || 1e9);
      });

      const top3 = merged.slice(0, 3).map((c, i) => ({ ...c, rank: i + 1 }));
      setCards(top3);
      setSelectedId(top3[0]?.id ?? null);
    })().catch(console.error);
  }, [coords, radiusKm]);

  // TopSubline prop 타입과 일치
  const subline = useMemo<{
    state: 'loading' | 'default' | 'empty';
    radius: string;
    count: string;
  }>(() => {
    let state: 'loading' | 'default' | 'empty';

    if (!coords || !hasLoadedHospitals) {
      state = 'loading';
    } else if (allHospitals.length === 0) {
      state = 'empty';
    } else {
      state = 'default';
    }

    return {
      state,
      radius: String(radiusKm),
      count: String(allHospitals.length || 0),
    };
  }, [coords, hasLoadedHospitals, allHospitals.length, radiusKm]);

  // 현재는 Top1 후보를 항상 'hospital-1' 마커와 연결
  const highlightedMarkerId = useMemo(
    () => (cards[0] ? 'hospital-1' : null),
    [cards],
  );

  const isGeoOverlayOpen = !!geoError && !geoOverlayClosed;

  return (
    <>
      <TopAppHeader />
      <TopSubline
        state={subline.state}
        radiusKm={subline.radius}
        count={subline.count}
      />

      <div className="pt-14 h-screen flex flex-col">
        <div className="relative flex-1">
          <MapView highlightedMarkerId={highlightedMarkerId} />
        </div>

        <BottomCardBar
          hospitals={cards}
          selectedHospitalId={selectedId}
          onCardSelect={(id) => setSelectedId(id)}
          onViewDetails={async (raw) => {
            const card = normalizeCard(raw);
            try {
              const d = await Api.hospitalDetail(card.id);
              const icu =
                (d.hv2 || 0) +
                (d.hv3 || 0) +
                (d.hv4 || 0) +
                (d.hv5 || 0) +
                (d.hv6 || 0) +
                (d.hvoc || 0);
              setDetail({
                open: true,
                card: {
                  ...card,
                  erAvailable: d.hvec ?? 0,
                  icuAvailTotal: icu,
                  hv: {
                    hv2: d.hv2,
                    hv3: d.hv3,
                    hv4: d.hv4,
                    hv5: d.hv5,
                    hv6: d.hv6,
                    hvoc: d.hvoc,
                  },
                },
              });
            } catch {
              setDetail({ open: true, card });
            }
          }}
          onStartNavigation={(raw) => {
            const card = normalizeCard(raw);
            setHandoff({ open: true, name: card.name });
          }}
        />
      </div>

      <DetailHalfSheet
        isOpen={detail.open}
        onClose={() => setDetail({ open: false })}
        hospitalName={detail.card?.name || ''}
        hli={detail.card?.hli || 0}
        level={detail.card?.level || 'GRAY'}
        minutesAgo={detail.card?.minutesAgo || 0}
        freshness={detail.card?.freshness ?? undefined}
        phone={detail.card?.phone || ''}
        address={detail.card?.address || ''}
        erAvailable={detail.card?.erAvailable || 0}
        icuAvailTotal={detail.card?.icuAvailTotal || 0}
        hvDetails={detail.card?.hv}
        warnMessage={detail.card?.warnMessage}
        onStartNavigation={() => {
          setDetail({ open: false });
          setHandoff({ open: true, name: detail.card?.name });
        }}
        onCall={() => {
          if (detail.card?.phone) {
            window.location.href = `tel:${detail.card.phone}`;
          }
        }}
      />

      <TmapHandoffScreen
        isOpen={handoff.open}
        hospitalName={handoff.name || ''}
        onBack={() => setHandoff({ open: false })}
        onConfirm={() => {
          setHandoff({ open: false });
          setOpened(true);
        }}
      />
      <TmapOpenedOverlay isOpen={opened} onClose={() => setOpened(false)} />

      <SimpleOverlay
        isOpen={isGeoOverlayOpen}
        onClose={() => setGeoOverlayClosed(true)}
        title={
          geoError === 'DENIED'
            ? '위치 권한이 필요합니다'
            : '현재 위치를 가져올 수 없어요'
        }
        message={
          geoError === 'DENIED'
            ? '주변 응급실을 추천하려면 브라우저 위치 권한을 허용해 주세요.'
            : geoError === 'GEO_UNSUPPORTED'
            ? '이 브라우저에서는 위치 정보를 지원하지 않아, 예시 화면으로만 확인할 수 있어요.'
            : '위치 정보를 불러오는 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요.'
        }
      />
    </>
  );
}
