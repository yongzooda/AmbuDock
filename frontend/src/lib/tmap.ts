// src/lib/tmap.ts

export type TmapOpenParams = {
  /** 목적지 위도 */
  lat?: number;
  /** 목적지 경도 */
  lon?: number;
  /** 목적지 이름 */
  name: string;
};

export const TMAP_IOS_STORE_LINK =
  'https://apps.apple.com/kr/app/tmap-%ED%8A%B8%EB%9E%9C%EC%A7%80%ED%8F%AC%EC%9D%B8%ED%8A%B8/id431589174';

export const TMAP_ANDROID_STORE_LINK =
  'https://play.google.com/store/apps/details?id=com.skt.tmap.ku';

/** UA 헬퍼 */
function getUserAgent(): string {
  if (typeof window === 'undefined') return '';
  return window.navigator.userAgent || '';
}

export function isAndroid(): boolean {
  return /Android/i.test(getUserAgent());
}

export function isIOS(): boolean {
  return /iPhone|iPad|iPod/i.test(getUserAgent());
}

/**
 * TMAP 길안내 딥링크만 시도하는 함수
 *
 * - TMAP 설치 O:
 *   - iOS: "TMAP에서 열겠습니까?" 시스템 팝업 → '열기' 선택 시 앱 전환
 *   - Android: 조용히 TMAP 앱으로 전환
 *
 * - TMAP 설치 X:
 *   - iOS: Safari에서 "주소가 유효하지 않습니다" 알림 가능
 *   - Android: 아무 반응이 없을 수 있음
 *
 * ⚠️ App Store / Play Store로 자동 이동은 하지 않는다.
 *     설치 유도는 화면(UI)에서 TMAP_IOS_STORE_LINK / TMAP_ANDROID_STORE_LINK를
 *     사용하는 별도 버튼으로 처리한다.
 */
export function launchTmapNavigation({ lat, lon, name }: TmapOpenParams): void {
  if (typeof window === 'undefined') return;

  const safeName = name && name.length > 0 ? name : '목적지';
  const encodedName = encodeURIComponent(safeName);

  // 기본 딥링크: 목적지 이름
  let scheme = `tmap://route?goalname=${encodedName}`;

  // 좌표가 있으면 goalx / goaly까지 포함 (WGS84GEO)
  if (typeof lat === 'number' && typeof lon === 'number') {
    scheme += `&goalx=${lon}&goaly=${lat}&coordType=WGS84GEO`;
  }

  const ua = getUserAgent();
  const isMobile = /Android|iPhone|iPad|iPod/i.test(ua);

  // 데스크톱 환경이면 실제 이동은 하지 않고 로그만 남김
  if (!isMobile) {
    console.info('[TMAP] deep link (non-mobile):', scheme);
    return;
  }

  try {
    window.location.href = scheme;
  } catch (e) {
    console.error('[TMAP] deep link error', e);
  }
}

/**
 * 과거 코드와의 호환용: 기존에 쓰던 이름을 그대로 유지
 * - 내부적으로는 launchTmapNavigation을 그대로 호출
 */
export function openTmapNavigation(params: TmapOpenParams): void {
  launchTmapNavigation(params);
}
