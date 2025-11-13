import { Navigation, Loader2 } from 'lucide-react';
import { useState } from 'react';

type TmapHandoffScreenProps = {
  isOpen: boolean;
  hospitalName: string;
  onConfirm: () => void;
  onBack: () => void;
};

export function TmapHandoffScreen({
  isOpen,
  hospitalName,
  onConfirm,
  onBack,
}: TmapHandoffScreenProps) {
  const [isLoading, setIsLoading] = useState(false);

  if (!isOpen) return null;

  const handleConfirm = () => {
    setIsLoading(true);
    // Simulate loading for 1 second
    setTimeout(() => {
      setIsLoading(false);
      onConfirm();
    }, 1000);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex flex-col items-center justify-between"
      style={{
        background: 'linear-gradient(180deg, #3B82F6 0%, #1D4ED8 50%, #1E40AF 100%)',
        width: '390px',
        height: '844px',
        margin: '0 auto',
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
            className="mb-6 px-4 py-3 rounded-2xl"
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

      {/* Bottom buttons - fixed */}
      <div className="w-full px-6 pb-8 space-y-3">
        {/* Primary button - 확인 */}
        <button
          className="w-full px-6 py-4 rounded-xl transition-all"
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
              <Loader2 className="w-5 h-5 animate-spin" />
              <span>이동 중…</span>
            </div>
          ) : (
            '확인'
          )}
        </button>

        {/* Secondary button - 돌아가기 */}
        <button
          className="w-full px-6 py-4 rounded-xl transition-all"
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
  );
}
