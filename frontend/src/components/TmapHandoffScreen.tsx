// src/components/TmapHandoffScreen.tsx

import { Navigation, Loader2 } from 'lucide-react';
import { useState } from 'react';
import {
  openTmapNavigation,
  isAndroid,
  isIOS,
  TMAP_ANDROID_STORE_LINK,
  TMAP_IOS_STORE_LINK,
} from '../lib/tmap';

type TmapHandoffScreenProps = {
  isOpen: boolean;
  hospitalName: string;
  lat?: number; // 목적지 위도
  lon?: number; // 목적지 경도
  onConfirm: () => void; // 상위(App.tsx)에서 오버레이 열기 등 처리
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

  const uaIsAndroid = isAndroid();
  const uaIsIOS = isIOS();
  const storeUrl = uaIsAndroid
    ? TMAP_ANDROID_STORE_LINK
    : uaIsIOS
    ? TMAP_IOS_STORE_LINK
    : undefined;

  const handleConfirm = () => {
    if (isLoading) return;

    // 좌표가 없으면 딥링크 없이 상위로만 알려주고 종료
    if (typeof lat !== 'number' || typeof lon !== 'number') {
      console.warn('[TMAP] missing lat/lon for deep link');
      onConfirm();
      return;
    }

    setIsLoading(true);

    // 살짝 로딩을 보여준 뒤 딥링크 시도
    setTimeout(() => {
      openTmapNavigation({
        lat,
        lon,
        name: hospitalName && hospitalName.length > 0 ? hospitalName : '목적지',
      });

      setIsLoading(false);
      onConfirm(); // App.tsx: handoff 닫고 TmapOpenedOverlay 열기 등
    }, 300);
  };

  const handleOpenStore = () => {
    if (!storeUrl) return;
    window.location.href = storeUrl;
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
        <div className="flex flex-1 flex-col items-center justify-center px-6">
          {/* Large navigation icon */}
          <div className="mb-8">
            <Navigation
              className="h-24 w-24"
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
              className="mb-4 rounded-2xl px-4 py-3"
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
              className="mb-2"
              style={{
                fontSize: '16px',
                fontWeight: 500,
                color: 'rgba(255, 255, 255, 0.9)',
              }}
            >
              TMAP으로 이동합니다.
            </div>

            {/* OS별 친절한 안내 문구 */}
            <div
              style={{
                fontSize: '13px',
                fontWeight: 400,
                color: 'rgba(255, 255, 255, 0.8)',
                lineHeight: 1.5,
                whiteSpace: 'pre-line',
              }}
            >
              {uaIsIOS
                ? 'iPhone에 TMAP이 설치되어 있지 않다면\n"주소가 유효하지 않습니다" 안내가 표시될 수 있어요.\n그 경우 아래 "TMAP 설치하기" 버튼을 눌러\nApp Store에서 설치해 주세요.'
                : uaIsAndroid
                ? 'TMAP이 설치되어 있지 않다면\n길안내가 바로 열리지 않을 수 있어요.\n그 경우 아래 "TMAP 설치하기" 버튼을 눌러\nPlay 스토어에서 설치해 주세요.'
                : '모바일 환경에서 TMAP 앱이 설치되어 있어야\n길안내가 정상적으로 실행됩니다.'}
            </div>
          </div>
        </div>

        {/* Bottom buttons - 항상 하단 고정 */}
        <div className="w-full space-y-3 px-6 pb-8">
          {/* Primary button - 확인 (딥링크) */}
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

          {/* Optional: TMAP 설치하기 버튼 (OS 인식 시만 노출) */}
          {storeUrl && (
            <button
              className="w-full rounded-xl px-6 py-3 transition-all"
              style={{
                background: 'rgba(255, 255, 255, 0.18)',
                color: '#FFFFFF',
                fontSize: '15px',
                fontWeight: 600,
                border: '1px solid rgba(255, 255, 255, 0.35)',
                backdropFilter: 'blur(10px)',
              }}
              onClick={handleOpenStore}
              disabled={isLoading}
              aria-label="TMAP 설치하기"
            >
              {uaIsIOS
                ? 'TMAP 설치하기 (App Store)'
                : 'TMAP 설치하기 (Play 스토어)'}
            </button>
          )}

          {/* Secondary button - 돌아가기 */}
          <button
            className="w-full rounded-xl px-6 py-4 transition-all"
            style={{
              background: 'rgba(255, 255, 255, 0.12)',
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
