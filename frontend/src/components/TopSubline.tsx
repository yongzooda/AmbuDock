import { Target, Info } from 'lucide-react';

type TopSublineProps = {
  state?: 'default' | 'loading' | 'empty';
  radiusKm?: string;
  count?: string;
};

export function TopSubline({ state = 'default', radiusKm = '15', count = '32' }: TopSublineProps) {
  return (
    <div 
      className="absolute left-1/2 -translate-x-1/2 z-20"
      style={{ width: 'fit-content', top: '64px' }}
      role="status"
      aria-label={state === 'default' ? `반경 ${radiusKm}킬로미터, ${count}개 병원` : undefined}
    >
      <div 
        className="flex items-center gap-2 px-3.5 h-11 rounded-[18px]"
        style={{ 
          background: 'rgba(255, 255, 255, 0.76)',
          border: '1px solid rgba(255, 255, 255, 0.6)',
          boxShadow: '0 2px 12px rgba(0, 0, 0, 0.12)',
          minWidth: '240px',
          whiteSpace: 'nowrap'
        }}
      >
        <Target className="w-4 h-4 flex-shrink-0" style={{ color: '#3B82F6' }} />
        
        {state === 'loading' && (
          <div 
            className="w-30 h-2.5 rounded-md animate-pulse" 
            style={{ background: '#E5E7EB' }} 
            aria-label="로딩 중"
          />
        )}
        
        {state === 'default' && (
          <span className="text-base" style={{ fontWeight: 600, letterSpacing: '-0.01em' }}>
            반경 {radiusKm}km · {count}개 병원
          </span>
        )}
        
        {state === 'empty' && (
          <>
            <span className="text-base" style={{ fontWeight: 600, letterSpacing: '-0.01em' }}>
              반경 {radiusKm}km · 0개 병원
            </span>
            <div className="relative group flex-shrink-0">
              <Info className="w-3.5 h-3.5" style={{ color: '#6B7280' }} />
              <div 
                className="absolute top-full mt-2 left-1/2 -translate-x-1/2 px-2 py-2 text-white text-xs rounded-lg opacity-0 group-hover:opacity-100 pointer-events-none whitespace-nowrap"
                style={{ 
                  background: 'rgba(17, 24, 39, 0.9)',
                  fontSize: '12px'
                }}
              >
                통제 지역일 수 있어요
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
