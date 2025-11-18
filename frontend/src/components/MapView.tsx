// src/components/MapView.tsx

import { useEffect, useRef, useState } from 'react';
import { Crosshair } from 'lucide-react';
import { MapLegend } from './MapLegend';
import { MarkerTooltip } from './MarkerTooltip';
import { MarkerHighlight } from './MarkerHighlight';
import type { HospitalListItem, HliLevel } from '../types';
import { Api } from '../lib/api';

type MapViewProps = {
  coords: { lat: number; lon: number } | null;
  hospitals: HospitalListItem[];
  selectedHospitalId?: string | null;
  onMarkerSelect?: (hpid: string) => void;
};

/**
 * 병원 리스트 아이템에 연락처/주소 필드가 옵션으로 붙어올 수 있어서
 * 툴팁에서만 사용할 보조 타입
 */
type HospitalWithContact = HospitalListItem & {
  telEmergency?: string;
  tel_emergency?: string;
  addrRoad?: string;
  address?: string;
};

/**
 * Kakao Maps 최소 타입 정의
 */
declare global {
  interface KakaoLatLng {
    readonly __brand?: 'KakaoLatLng';
  }

  interface KakaoLatLngBounds {
    extend(latlng: KakaoLatLng): void;
  }

  interface KakaoPoint {
    x: number;
    y: number;
  }

  interface KakaoMapProjection {
    containerPointFromCoords(latlng: KakaoLatLng): KakaoPoint;
  }

  interface KakaoMap {
    setBounds(bounds: KakaoLatLngBounds): void;
    panTo(latlng: KakaoLatLng): void;
    getLevel(): number;
    setLevel(level: number): void;
    getProjection(): KakaoMapProjection;
  }

  interface KakaoCustomOverlay {
    setMap(map: KakaoMap | null): void;
    getPosition?(): KakaoLatLng;
    setPosition?(latlng: KakaoLatLng): void;
  }

  interface KakaoMapsEvent {
    addListener(
      target: KakaoMap | KakaoCustomOverlay | HTMLElement,
      type: string,
      handler: (...args: unknown[]) => void
    ): void;
  }

  interface KakaoMapsNamespace {
    LatLng: new (lat: number, lng: number) => KakaoLatLng;
    LatLngBounds: new () => KakaoLatLngBounds;
    Map: new (
      container: HTMLElement,
      options: { center: KakaoLatLng; level: number }
    ) => KakaoMap;
    CustomOverlay: new (options: {
      position: KakaoLatLng;
      yAnchor?: number;
      content: HTMLElement;
    }) => KakaoCustomOverlay;
    event: KakaoMapsEvent;
    load(callback: () => void): void;
  }

  interface KakaoNamespace {
    maps: KakaoMapsNamespace;
  }

  interface Window {
    kakao?: KakaoNamespace;
  }
}

const levelColors: Record<HliLevel, string> = {
  GREEN: '#10B981',
  YELLOW: '#F59E0B',
  RED: '#EF4444',
  GRAY: '#9CA3AF',
};

// 지도에서 기본 반경(병원 검색 반경과 맞춤)
const DEFAULT_RADIUS_KM = 15;
// 카드 선택 시 포커스할 반경
const FOCUS_RADIUS_KM = 1;

export function MapView({
  coords,
  hospitals,
  selectedHospitalId,
  onMarkerSelect,
}: MapViewProps) {
  const mapRef = useRef<HTMLDivElement | null>(null);
  const mapInstanceRef = useRef<KakaoMap | null>(null);
  const overlayMapRef = useRef<Map<string, KakaoCustomOverlay>>(
    new Map<string, KakaoCustomOverlay>()
  );
  const myLocationOverlayRef = useRef<KakaoCustomOverlay | null>(null);

  // 지도 준비 여부 플래그
  const [mapReady, setMapReady] = useState(false);

  // 카드 선택 시 지도 중앙에 잠깐 보여줄 하이라이트 링
  const [cardHighlightCenter, setCardHighlightCenter] = useState(false);

  // onMarkerSelect를 ref로 보관해서 effect 의존성에서 제거
  const onMarkerSelectRef = useRef<MapViewProps['onMarkerSelect'] | undefined>(
    onMarkerSelect
  );
  useEffect(() => {
    onMarkerSelectRef.current = onMarkerSelect;
  }, [onMarkerSelect]);

  // hpid -> ER 가용 병상수(hvec) 캐시
  const [erCache, setErCache] = useState<Map<string, number>>(
    () => new Map<string, number>()
  );

  // 현재 열려 있는 툴팁 상태
  const [tooltipState, setTooltipState] = useState<{
    hospital: HospitalWithContact;
    position: { x: number; y: number };
  } | null>(null);

  /**
   * 내 위치 마커(파란 링 + 점)를 생성/이동시키는 헬퍼
   */
  const updateMyLocationOverlay = (
    kakaoMaps: KakaoMapsNamespace,
    map: KakaoMap,
    lat: number,
    lon: number
  ) => {
    const pos = new kakaoMaps.LatLng(lat, lon);

    // 이미 존재하면 위치만 업데이트
    if (myLocationOverlayRef.current) {
      if (myLocationOverlayRef.current.setPosition) {
        myLocationOverlayRef.current.setPosition(pos);
      }
      myLocationOverlayRef.current.setMap(map);
      return;
    }

    // 새로 생성
    const container = document.createElement('div');
    container.style.position = 'relative';
    container.style.width = '24px';
    container.style.height = '24px';

    const ring = document.createElement('div');
    ring.style.position = 'absolute';
    ring.style.left = '0';
    ring.style.top = '0';
    ring.style.width = '24px';
    ring.style.height = '24px';
    ring.style.borderRadius = '999px';
    ring.style.background = 'rgba(37, 99, 235, 0.15)';
    ring.style.border = '2px solid rgba(37, 99, 235, 0.7)';

    const dot = document.createElement('div');
    dot.style.position = 'absolute';
    dot.style.left = '50%';
    dot.style.top = '50%';
    dot.style.width = '8px';
    dot.style.height = '8px';
    dot.style.marginLeft = '-4px';
    dot.style.marginTop = '-4px';
    dot.style.borderRadius = '999px';
    dot.style.background = '#2563EB';

    container.appendChild(ring);
    container.appendChild(dot);

    const overlay = new kakaoMaps.CustomOverlay({
      position: pos,
      yAnchor: 0.5,
      content: container,
    });

    overlay.setMap(map);
    myLocationOverlayRef.current = overlay;
  };

  /**
   * 특정 중심(lat, lon)을 기준으로 radiusKm 반경이 보이도록 지도 bounds 맞추기
   */
  const fitMapToRadius = (
    kakaoMaps: KakaoMapsNamespace,
    map: KakaoMap,
    lat: number,
    lon: number,
    radiusKm: number = DEFAULT_RADIUS_KM
  ) => {
    const bounds = new kakaoMaps.LatLngBounds();

    // 위도 1도 ≈ 111km
    const deltaLat = radiusKm / 111;

    // 경도는 위도에 따라 달라짐
    const rad = (lat * Math.PI) / 180;
    const kmPerLonDegree = 111 * Math.cos(rad || 0.00001);
    const deltaLon = radiusKm / kmPerLonDegree;

    const south = lat - deltaLat;
    const north = lat + deltaLat;
    const west = lon - deltaLon;
    const east = lon + deltaLon;

    bounds.extend(new kakaoMaps.LatLng(south, west));
    bounds.extend(new kakaoMaps.LatLng(north, east));

    map.setBounds(bounds);

    // 너무 확대/축소 방지용 레벨 클램프
    const level = map.getLevel();
    const minLevel = 3;
    const maxLevel = 8;

    if (level < minLevel) {
      map.setLevel(minLevel);
    } else if (level > maxLevel) {
      map.setLevel(maxLevel);
    }
  };

  // Kakao SDK 로드 + 지도 초기화 (한 번만)
  useEffect(() => {
    const scriptId = 'kakao-map-sdk';

    const initMap = () => {
      if (!mapRef.current || !window.kakao?.maps) return;

      // 초기 중심은 서울 시청 고정
      const center = new window.kakao.maps.LatLng(37.5665, 126.978);

      const map = new window.kakao.maps.Map(mapRef.current, {
        center,
        level: 5,
      });

      mapInstanceRef.current = map;

      // 지도의 아무 곳이나 클릭하면 툴팁 닫기
      if (window.kakao.maps.event) {
        window.kakao.maps.event.addListener(map, 'click', () => {
          setTooltipState(null);
        });
      }

      setMapReady(true);
    };

    // 이미 kakao.maps 가 있으면 바로 load
    if (window.kakao && window.kakao.maps) {
      window.kakao.maps.load(initMap);
      return;
    }

    // 스크립트만 있는 경우
    if (document.getElementById(scriptId)) {
      window.kakao?.maps.load(initMap);
      return;
    }

    // 처음 로드
    const script = document.createElement('script');
    script.id = scriptId;
    script.async = true;
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${
      import.meta.env.VITE_KAKAO_MAP_KEY
    }&autoload=false`;
    script.onload = () => {
      window.kakao?.maps.load(initMap);
    };
    script.onerror = (e) => {
      console.error('[KAKAO] sdk 로드 실패', e);
    };
    document.head.appendChild(script);
  }, []);

  /**
   * coords 가 준비되면:
   *  - 내 위치 마커 자동 표시
   *  - 내 위치 기준 반경 15km가 보이도록 지도 bounds 맞춤
   */
  useEffect(() => {
    const kakao = window.kakao;
    const map = mapInstanceRef.current;
    if (!kakao?.maps || !map || !coords) return;

    const { lat, lon } = coords;

    updateMyLocationOverlay(kakao.maps, map, lat, lon);
    fitMapToRadius(kakao.maps, map, lat, lon, DEFAULT_RADIUS_KM);
  }, [coords]);

  // 병원 마커 클릭 시 처리: 카드 선택 + 툴팁 위치 + ER 가용수 로딩
  const handleMarkerClick = async (h: HospitalWithContact) => {
    const kakao = window.kakao;
    const map = mapInstanceRef.current;
    if (!kakao?.maps || !map) return;

    const pos = new kakao.maps.LatLng(h.lat, h.lon);
    const projection = map.getProjection();

    const point = projection.containerPointFromCoords(pos);

    // 카드/Top3와 동기화
    onMarkerSelectRef.current?.(h.hpid);

    // 툴팁은 바로 띄움
    setTooltipState({
      hospital: h,
      position: { x: point.x, y: point.y },
    });

    // ER 가용수가 캐시에 없으면 상세 API 한 번만 호출
    if (!erCache.has(h.hpid)) {
      try {
        const d = await Api.hospitalDetail(h.hpid);
        const hvec = d.hvec ?? 0;

        setErCache((prev) => {
          const next = new Map(prev);
          next.set(h.hpid, hvec);
          return next;
        });
      } catch (e) {
        console.error('[MapView] hospitalDetail error', e);
      }
    }
  };

  // 병원 목록이 바뀌거나 지도 준비되면 병원 마커 다시 그림
  useEffect(() => {
    const kakao = window.kakao;
    const map = mapInstanceRef.current;
    if (!mapReady || !kakao?.maps || !map) return;

    // 기존 병원 오버레이 제거
    overlayMapRef.current.forEach((ov) => ov.setMap(null));
    overlayMapRef.current.clear();

    if (hospitals.length === 0) return;

    hospitals.forEach((h) => {
      const pos = new kakao.maps.LatLng(h.lat, h.lon);
      const color = levelColors[h.hli.level];

      const container = document.createElement('div');
      container.style.width = '18px';
      container.style.height = '18px';
      container.style.borderRadius = '999px';
      container.style.background = color;
      container.style.boxShadow = '0 2px 8px rgba(0,0,0,0.12)';
      container.style.display = 'flex';
      container.style.alignItems = 'center';
      container.style.justifyContent = 'center';
      container.style.cursor = 'pointer';

      const inner = document.createElement('div');
      inner.style.width = '10px';
      inner.style.height = '10px';
      inner.style.borderRadius = '999px';
      inner.style.background = '#ffffff';

      container.appendChild(inner);

      container.onclick = () => {
        void handleMarkerClick(h);
      };

      const overlay = new kakao.maps.CustomOverlay({
        position: pos,
        yAnchor: 1,
        content: container,
      });

      overlay.setMap(map);
      overlayMapRef.current.set(h.hpid, overlay);
    });
  }, [hospitals, mapReady]);

  /**
   * 카드/Top3에서 병원이 선택되면:
   *  - 해당 병원 중심으로 반경 3km가 보이도록 줌
   *  - 지도 중앙에 하이라이트 링을 잠깐 표시
   */
  useEffect(() => {
    const kakao = window.kakao;
    const map = mapInstanceRef.current;
    if (!kakao?.maps || !map || !selectedHospitalId) return;

    const target = hospitals.find((h) => h.hpid === selectedHospitalId);
    if (!target) return;

    fitMapToRadius(kakao.maps, map, target.lat, target.lon, FOCUS_RADIUS_KM);

    // 중앙 하이라이트를 잠깐 켰다가 끄기 (깜빡임 효과)
    setCardHighlightCenter(true);
    const timeoutId = window.setTimeout(() => {
      setCardHighlightCenter(false);
    }, 1200);

    return () => window.clearTimeout(timeoutId);
  }, [selectedHospitalId, hospitals]);

  // 현위치 버튼: 내 위치 + 반경 15km로 다시 맞추기
  const handleRecenter = () => {
    const kakao = window.kakao;
    const map = mapInstanceRef.current;
    if (!kakao?.maps || !map || !coords) return;

    const { lat, lon } = coords;

    updateMyLocationOverlay(kakao.maps, map, lat, lon);
    fitMapToRadius(kakao.maps, map, lat, lon, DEFAULT_RADIUS_KM);
  };

  // 툴팁이 사용할 컨테이너 경계 (맵 영역 크기 기준)
  const tooltipBounds = mapRef.current
    ? {
        top: 0,
        left: 0,
        right: mapRef.current.clientWidth,
        bottom: mapRef.current.clientHeight,
      }
    : undefined;

  return (
    <div className="relative h-full overflow-hidden">
      {/* 실제 Kakao Map 이 그려지는 영역 */}
      <div ref={mapRef} className="absolute inset-0" />

      {/* 범례 */}
      <MapLegend />

      {/* 현위치 FAB */}
      <button
        className="absolute bottom-6 right-4 w-14 h-14 rounded-full bg-white flex items-center justify-center transition-transform hover:scale-95 active:scale-90"
        style={{
          boxShadow:
            '0 4px 12px rgba(0, 0, 0, 0.15), 0 2px 4px rgba(0, 0, 0, 0.1)',
          zIndex: 10,
        }}
        aria-label="현위치로 재중심"
        onClick={handleRecenter}
      >
        <Crosshair className="w-6 h-6" style={{ color: '#111827' }} />
      </button>

      {/* 카드 선택 시 지도 중앙 하이라이트 (줌과 함께) */}
      {cardHighlightCenter && (
        <MarkerHighlight
          style={{
            left: '50%',
            top: '50%',
            transform: 'translate(-50%, -50%)',
            pointerEvents: 'none',
            zIndex: 20,
          }}
        />
      )}

      {/* 선택된 마커 하이라이트 + 툴팁 */}
      {tooltipState && (
        <>
          <MarkerHighlight
            style={{
              left: tooltipState.position.x,
              top: tooltipState.position.y,
              transform: 'translate(-50%, -50%)',
              pointerEvents: 'none',
              zIndex: 25,
            }}
          />

          {(() => {
            const hospital = tooltipState.hospital;

            const phone =
              hospital.telEmergency ?? hospital.tel_emergency ?? undefined;
            const address = hospital.addrRoad ?? hospital.address ?? undefined;
            const hvecCount = erCache.get(hospital.hpid);

            return (
              <MarkerTooltip
                hospitalName={hospital.name}
                phone={phone}
                address={address}
                hli={hospital.hli.score}
                level={hospital.hli.level}
                minutesAgo={
                  hospital.hli.lagSec
                    ? Math.max(0, Math.round(hospital.hli.lagSec / 60))
                    : 0
                }
                hvecCount={hvecCount}
                warnMessage={undefined}
                variant={
                  hospital.hli.staleLevel === 'HARD' ? 'gray' : 'default'
                }
                position={tooltipState.position}
                containerBounds={tooltipBounds}
                onClose={() => setTooltipState(null)}
              />
            );
          })()}
        </>
      )}
    </div>
  );
}
