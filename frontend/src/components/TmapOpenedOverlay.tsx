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
        className="fixed inset-0 z-40 bg-black/60"
        onClick={onClose}
      />

      {/* Success card */}
      <div className="fixed left-1/2 top-1/2 z-50 w-[85%] max-w-[320px] -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-white p-6 text-center shadow-2xl">
        <div className="mb-4 flex justify-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full"
               style={{
                 background:
                   'linear-gradient(135deg, #3B82F6 0%, #1E40AF 100%)',
               }}
          >
            <Check className="h-8 w-8" style={{ color: '#FFFFFF', strokeWidth: 3 }} />
          </div>
        </div>

        <h3 className="mb-2 text-[20px] font-bold text-slate-900">
          TMAP으로 이동 중입니다
        </h3>

        <p className="mb-6 text-[15px] font-medium text-slate-600 leading-relaxed">
          TMAP 앱(또는 앱스토어)으로 전환되었어요.
          <br />
          앱에서 길 안내를 시작한 뒤,
          <br />
          이 화면은 닫으셔도 괜찮습니다.
        </p>

        <button
          className="w-full rounded-lg px-4 py-3 text-[16px] font-semibold text-white transition-colors hover:bg-blue-600"
          style={{
            background: '#3B82F6',
          }}
          onClick={onClose}
        >
          확인
        </button>
      </div>
    </>
  );
}
