// src/components/DetailHalfSheet.tsx

import { X, Phone } from 'lucide-react';

type Level = 'GREEN' | 'YELLOW' | 'RED' | 'GRAY';
type DataFreshness = 'FRESH' | 'SOFT' | 'GRAY';

type DetailHalfSheetProps = {
  isOpen: boolean;
  onClose: () => void;
  hospitalName: string;
  hli: number;
  level: Level;
  minutesAgo: number;
  freshness?: DataFreshness;
  phone: string;
  address: string;
  erAvailable: number;
  icuAvailTotal: number;
  hvDetails?: {
    hv2?: number;
    hv3?: number;
    hv4?: number;
    hv5?: number;
    hv6?: number;
    hvoc?: number;
  };
  warnMessage?: string;
  onStartNavigation: () => void;
  onCall: () => void;
};

const levelStyles = {
  GREEN: { bg: '#ECFDF5', text: '#047857' },
  YELLOW: { bg: '#FFFBEB', text: '#B45309' },
  RED: { bg: '#FEF2F2', text: '#B91C1C' },
  GRAY: { bg: '#F3F4F6', text: '#6B7280' },
};

const freshnessStyles = {
  FRESH: { bg: '#ECFDF5', text: '#047857' },
  SOFT: { bg: '#FFF7ED', text: '#B45309' },
  GRAY: { bg: '#F3F4F6', text: '#6B7280' },
};

export function DetailHalfSheet({
  isOpen,
  onClose,
  hospitalName,
  hli,
  level,
  minutesAgo,
  freshness,
  phone,
  address,
  erAvailable,
  icuAvailTotal,
  hvDetails = {},
  warnMessage,
  onStartNavigation,
  onCall,
}: DetailHalfSheetProps) {
  if (!isOpen) return null;

  const levelStyle = levelStyles[level];
  const freshnessStyle = freshness ? freshnessStyles[freshness] : null;

  return (
    <>
      {/* Backdrop */}
      <div 
        className="fixed inset-0 bg-black z-40 transition-opacity"
        style={{ opacity: 0.4 }}
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Half-sheet */}
      <div 
        className="fixed left-0 right-0 bottom-0 bg-white z-50 overflow-y-auto"
        style={{
          maxHeight: '70vh',
          borderTopLeftRadius: '20px',
          borderTopRightRadius: '20px',
          boxShadow: '0 -4px 20px rgba(0, 0, 0, 0.15)',
        }}
        role="dialog"
        aria-label={`${hospitalName} 상세 정보, HLI ${hli}점 ${level}, ${minutesAgo}분 전 업데이트됨`}
      >
        {/* Close button */}
        <button
          className="absolute top-4 right-4 w-8 h-8 rounded-full flex items-center justify-center transition-colors hover:bg-gray-100"
          onClick={onClose}
          aria-label="닫기"
        >
          <X className="w-5 h-5" style={{ color: '#6B7280' }} />
        </button>

        <div className="p-6 pb-8">
          {/* Header Summary */}
          <div className="mb-4">
            <div className="flex items-center gap-2 mb-2">
              <span style={{ fontSize: '14px', fontWeight: 600 }}>
                HLI {hli}
              </span>
              <span style={{ fontSize: '14px', fontWeight: 600 }}>·</span>
              <span 
                className="px-2 py-0.5 rounded text-xs"
                style={{ 
                  background: levelStyle.bg,
                  color: levelStyle.text,
                  fontWeight: 600
                }}
              >
                {level}
              </span>
              <span style={{ fontSize: '14px', fontWeight: 600 }}>·</span>
              <span style={{ fontSize: '14px', fontWeight: 500, color: '#6B7280' }}>
                {minutesAgo}분 전
              </span>
              {freshnessStyle && (
                <>
                  <span 
                    className="px-2 py-0.5 rounded text-xs"
                    style={{ 
                      background: freshnessStyle.bg,
                      color: freshnessStyle.text,
                      fontWeight: 600
                    }}
                  >
                    {freshness}
                  </span>
                </>
              )}
            </div>
          </div>

          {/* Hospital Name */}
          <h2 className="mb-4" style={{ fontSize: '24px', fontWeight: 700 }}>
            {hospitalName}
          </h2>

          {/* Contact Info */}
          <div className="mb-6 space-y-2">
            <div style={{ fontSize: '16px', fontWeight: 500, color: '#6B7280' }}>
              전화 {phone}
            </div>
            <div style={{ fontSize: '16px', fontWeight: 500, color: '#6B7280' }}>
              주소 {address}
            </div>
          </div>

          {/* ER/ICU Section */}
          <div className="mb-6">
            <h3 className="mb-3" style={{ fontSize: '18px', fontWeight: 600 }}>
              ER/ ICU 정보
            </h3>
            
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span style={{ fontSize: '16px', fontWeight: 500 }}>
                  가용 응급실 병상수(ER 가용수)
                </span>
                <span style={{ fontSize: '16px', fontWeight: 700 }}>
                  {erAvailable}개
                </span>
              </div>

              <div className="flex items-center justify-between">
                <span style={{ fontSize: '16px', fontWeight: 500 }}>
                  입원실/ 중환자실/ 수술실 가용
                </span>
                <span style={{ fontSize: '16px', fontWeight: 700 }}>
                  {icuAvailTotal}개
                </span>
              </div>

              {/* Detailed ICU breakdown */}
              {Object.keys(hvDetails).length > 0 && (
                <div className="mt-3 pl-4 space-y-1">
                  {hvDetails.hv2 !== undefined && (
                    <div className="flex items-center justify-between">
                      <span style={{ fontSize: '14px', fontWeight: 500, color: '#6B7280' }}>
                        내과중환자실
                      </span>
                      <span style={{ fontSize: '14px', fontWeight: 600, color: '#6B7280' }}>
                        {hvDetails.hv2}개
                      </span>
                    </div>
                  )}
                  {hvDetails.hv3 !== undefined && (
                    <div className="flex items-center justify-between">
                      <span style={{ fontSize: '14px', fontWeight: 500, color: '#6B7280' }}>
                        외과중환자실
                      </span>
                      <span style={{ fontSize: '14px', fontWeight: 600, color: '#6B7280' }}>
                        {hvDetails.hv3}개
                      </span>
                    </div>
                  )}
                  {hvDetails.hv4 !== undefined && (
                    <div className="flex items-center justify-between">
                      <span style={{ fontSize: '14px', fontWeight: 500, color: '#6B7280' }}>
                        외과입원실(정형외과)
                      </span>
                      <span style={{ fontSize: '14px', fontWeight: 600, color: '#6B7280' }}>
                        {hvDetails.hv4}개
                      </span>
                    </div>
                  )}
                  {hvDetails.hv5 !== undefined && (
                    <div className="flex items-center justify-between">
                      <span style={{ fontSize: '14px', fontWeight: 500, color: '#6B7280' }}>
                        신경과입원실
                      </span>
                      <span style={{ fontSize: '14px', fontWeight: 600, color: '#6B7280' }}>
                        {hvDetails.hv5}개
                      </span>
                    </div>
                  )}
                  {hvDetails.hv6 !== undefined && (
                    <div className="flex items-center justify-between">
                      <span style={{ fontSize: '14px', fontWeight: 500, color: '#6B7280' }}>
                        신경외과중환자실
                      </span>
                      <span style={{ fontSize: '14px', fontWeight: 600, color: '#6B7280' }}>
                        {hvDetails.hv6}개
                      </span>
                    </div>
                  )}
                  {hvDetails.hvoc !== undefined && (
                    <div className="flex items-center justify-between">
                      <span style={{ fontSize: '14px', fontWeight: 500, color: '#6B7280' }}>
                        수술실
                      </span>
                      <span style={{ fontSize: '14px', fontWeight: 600, color: '#6B7280' }}>
                        {hvDetails.hvoc}개
                      </span>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* WARN badge */}
          {warnMessage && (
            <div 
              className="mb-6 px-3 py-2 rounded"
              style={{ 
                background: '#FFF7ED',
                border: '1px solid #FED7AA'
              }}
            >
              <div style={{ fontSize: '14px', fontWeight: 600, color: '#C2410C', marginBottom: '4px' }}>
                경보/통제 정보
              </div>
              <div style={{ fontSize: '14px', fontWeight: 500, color: '#C2410C' }}>
                {warnMessage}
              </div>
            </div>
          )}

          {/* Actions */}
          <div className="flex gap-3">
            <button
              className="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-lg transition-colors hover:bg-gray-100"
              style={{ 
                background: '#F3F4F6',
                color: '#111827',
                fontSize: '16px',
                fontWeight: 600
              }}
              onClick={onCall}
              aria-label="전화하기"
            >
              <Phone className="w-5 h-5" />
              전화하기
            </button>
            <button
              className="flex-1 px-4 py-3 rounded-lg transition-colors hover:bg-blue-600"
              style={{ 
                background: '#3B82F6',
                color: '#FFFFFF',
                fontSize: '16px',
                fontWeight: 600
              }}
              onClick={onStartNavigation}
              aria-label="길 안내 시작"
            >
              길 안내 시작
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
