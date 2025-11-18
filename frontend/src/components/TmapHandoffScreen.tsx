// src/components/TmapHandoffScreen.tsx

import { Navigation, Loader2 } from 'lucide-react';
import { useState } from 'react';

type TmapHandoffScreenProps = {
  isOpen: boolean;
  hospitalName: string;
  lat?: number; // 목적지 위도
  lon?: number; // 목적지 경도
  onConfirm: () => void;
  onBack: () => void;
};

export function TmapHandoffScreen({
  isOpen,
  hospitalName,
  lat,
  lon,
  onConfirm,
  onBack,
}: TmapHandoffScreenProps) {
  const [isLoading, setIsLoading] = useState(false);

  if (!isOpen) return null;

  const openTmapDeepLink = () => {
    if (typeof window === 'undefined') return;

    try {
      const encodedName = encodeURIComponent(
        hospitalName && hospitalName.length > 0 ? hospitalName : '목적지'
      );

      // 기본 딥링크: 목적지 이름
      let deepLink = `tmap://route?goalname=${encodedName}`;

      // 위경도가 있으면 좌표까지 포함해서 길안내
      if (typeof lat === 'number' && typeof lon === 'number') {
        // TMAP 스펙: goalx = 경도, goaly = 위도
        deepLink += `&goalx=${lon}&goaly=${lat}`;
      }

      const ua = window.navigator.userAgent || '';
      const isAndroid = /Android/i.test(ua);
      const isIOS = /iPhone|iPad|iPod/i.test(ua);

      // 모바일이 아니면(데스크톱 개발 환경) 콘솔만 찍고 종료
      if (!isAndroid && !isIOS) {
        console.info('[TMAP] deep link (desktop):', deepLink);
        return;
      }

      // 스토어 URL (앱 미설치 시 fallback)
      const androidStoreUrl =
        'https://play.google.com/store/apps/details?id=com.skt.tmap.ku';
      const iosStoreUrl = 'https://apps.apple.com/app/id431589174';
      const storeUrl = isAndroid ? androidStoreUrl : iosStoreUrl;

      const start = Date.now();

      // 1) TMAP 딥링크 시도
      window.location.href = deepLink;

      // 2) 짧은 시간 안에 브라우저가 그대로면 → 앱 미설치로 보고 스토어로 fallback
      setTimeout(() => {
        const elapsed = Date.now() - start;
        if (elapsed < 1500) {
          window.location.href = storeUrl;
        }
      }, 1200);
    } catch (e) {
      console.error('[TMAP] deep link 실패', e);
    }
  };

  const handleConfirm = () => {
    if (isLoading) return;

    setIsLoading(true);

    // 1) TMAP 딥링크 + 스토어 fallback 시도
    openTmapDeepLink();

    // 2) UI 상태 전환 (로딩 → 상위에서 오버레이 표시)
    setTimeout(() => {
      setIsLoading(false);
      onConfirm(); // App.tsx에서 handoff 닫고 TmapOpenedOverlay 열어줌
    }, 800);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* 가운데 놓인 '폰 화면' 영역 */}
      <div
        className="flex h-full w-full max-w-[390px] flex-col items-center justify-between"
        style={{
          background:
            'linear-gradient(180deg, #3B82F6 0%, #1D4ED8 50%, #1E40AF 100%)',
        }}
      >
        {/* Main content - centered */}
        <div className="flex-1 flex flex-col items-center justify-center px-6">
          {/* Large navigation icon */}
          <div className="mb-8">
            <Navigation
              className="w-24 h-24"
              style={{
                color: 'rgba(255, 255, 255, 0.9)',
                strokeWidth: 1.5,
                filter: 'drop-shadow(0 4px 12px rgba(0, 0, 0, 0.2))',
              }}
            />
          </div>

          {/* Title */}
          <h1
            className="mb-12 text-center"
            style={{
              fontSize: '32px',
              fontWeight: 700,
              color: '#FFFFFF',
              letterSpacing: '-0.5px',
            }}
          >
            TMAP 길안내
          </h1>

          {/* Destination section */}
          <div className="w-full max-w-xs text-center">
            <div
              className="mb-3"
              style={{
                fontSize: '14px',
                fontWeight: 500,
                color: 'rgba(255, 255, 255, 0.7)',
                letterSpacing: '0.5px',
              }}
            >
              목적지
            </div>

            <div
              className="mb-6 rounded-2xl px-4 py-3"
              style={{
                background: 'rgba(255, 255, 255, 0.15)',
                backdropFilter: 'blur(10px)',
                border: '1px solid rgba(255, 255, 255, 0.2)',
              }}
            >
              <div
                style={{
                  fontSize: '24px',
                  fontWeight: 700,
                  color: '#FFFFFF',
                  lineHeight: 1.3,
                  wordBreak: 'keep-all',
                }}
              >
                {hospitalName}
              </div>
            </div>

            <div
              style={{
                fontSize: '16px',
                fontWeight: 500,
                color: 'rgba(255, 255, 255, 0.8)',
              }}
            >
              TMAP으로 이동합니다.
            </div>
          </div>
        </div>

        {/* Bottom buttons - 항상 하단 고정 */}
        <div className="w-full space-y-3 px-6 pb-8">
          {/* Primary button - 확인 */}
          <button
            className="w-full rounded-xl px-6 py-4 transition-all"
            style={{
              background: '#FFFFFF',
              color: '#1E40AF',
              fontSize: '18px',
              fontWeight: 700,
              boxShadow: '0 4px 16px rgba(0, 0, 0, 0.2)',
            }}
            onClick={handleConfirm}
            disabled={isLoading}
            aria-label="확인"
          >
            {isLoading ? (
              <div className="flex items-center justify-center gap-2">
                <Loader2 className="h-5 w-5 animate-spin" />
                <span>이동 중…</span>
              </div>
            ) : (
              '확인'
            )}
          </button>

          {/* Secondary button - 돌아가기 */}
          <button
            className="w-full rounded-xl px-6 py-4 transition-all"
            style={{
              background: 'rgba(255, 255, 255, 0.2)',
              color: '#FFFFFF',
              fontSize: '16px',
              fontWeight: 600,
              border: '1px solid rgba(255, 255, 255, 0.3)',
              backdropFilter: 'blur(10px)',
            }}
            onClick={onBack}
            disabled={isLoading}
            aria-label="돌아가기"
          >
            돌아가기
          </button>
        </div>
      </div>
    </div>
  );
}
