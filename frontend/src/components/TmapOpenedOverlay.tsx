// src/components/TmapOpenedOverlay.tsx

import { Check } from 'lucide-react';

type TmapOpenedOverlayProps = {
  isOpen: boolean;
  onClose: () => void;
};

export function TmapOpenedOverlay({ isOpen, onClose }: TmapOpenedOverlayProps) {
  if (!isOpen) return null;

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black z-50 transition-opacity"
        style={{ opacity: 0.6 }}
        onClick={onClose}
      />

      {/* Success card */}
      <div
        className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 bg-white rounded-2xl p-8 z-50 shadow-2xl text-center"
        style={{
          maxWidth: '320px',
          width: '85%',
        }}
      >
        <div className="mb-4 flex justify-center">
          <div
            className="w-16 h-16 rounded-full flex items-center justify-center"
            style={{
              background: 'linear-gradient(135deg, #3B82F6 0%, #1E40AF 100%)',
            }}
          >
            <Check className="w-8 h-8" style={{ color: '#FFFFFF', strokeWidth: 3 }} />
          </div>
        </div>

        <h3 className="mb-2" style={{ fontSize: '20px', fontWeight: 700 }}>
          TMAP으로 전환됨
        </h3>

        <p className="mb-6" style={{ fontSize: '16px', fontWeight: 500, color: '#6B7280' }}>
          TMAP 앱에서 길 안내를 시작합니다.
        </p>

        <button
          className="w-full px-4 py-3 rounded-lg transition-colors hover:bg-blue-600"
          style={{
            background: '#3B82F6',
            color: '#FFFFFF',
            fontSize: '16px',
            fontWeight: 600,
          }}
          onClick={onClose}
        >
          확인
        </button>
      </div>
    </>
  );
}
