// src/components/HospitalCard.tsx

type CardState = 'default' | 'loading' | 'eta-pending' | 'warn' | 'gray';
type Level = 'GREEN' | 'YELLOW' | 'RED' | 'GRAY';
type DataFreshness = 'FRESH' | 'SOFT' | null;

type HospitalCardProps = {
  rank: number;
  name: string;
  level: Level;
  hli: number;
  minutesAgo: number;
  walkMin: number;
  carMin: number;
  distanceKm: number;
  state?: CardState;
  warnMessage?: string;
  freshness?: DataFreshness;
  isSelected?: boolean;
  onCardClick?: () => void;
  onViewDetails?: () => void;
  onStartNavigation?: () => void;
};

const levelStyles = {
  GREEN: { bg: '#ECFDF5', text: '#047857' },
  YELLOW: { bg: '#FFFBEB', text: '#B45309' },
  RED: { bg: '#FEF2F2', text: '#B91C1C' },
  GRAY: { bg: '#F3F4F6', text: '#6B7280' },
};

export function HospitalCard({
  rank,
  name,
  level,
  hli,
  minutesAgo,
  walkMin,
  carMin,
  distanceKm,
  state = 'default',
  warnMessage,
  freshness = null,
  isSelected = false,
  onCardClick,
  onViewDetails,
  onStartNavigation,
}: HospitalCardProps) {
  const levelStyle = levelStyles[level];
  const isGray = state === 'gray' || level === 'GRAY';

  // ✅ 표시용 숫자 포맷팅
  const roundedWalkMin = Number.isFinite(walkMin) ? Math.round(walkMin) : 0;
  const roundedCarMin = Number.isFinite(carMin) ? Math.round(carMin) : 0;
  const displayDistanceKm = Number.isFinite(distanceKm)
    ? Number(distanceKm.toFixed(1))
    : 0;

  if (state === 'loading') {
    return (
      <div
        className="w-full min-h-22 rounded-2xl p-3 flex flex-col gap-2"
        style={{ border: '1px solid #E5E7EB' }}
      >
        <div
          className="w-30 h-3 rounded-md animate-pulse"
          style={{ background: '#E5E7EB' }}
        />
        <div
          className="w-40 h-2.5 rounded-md animate-pulse"
          style={{ background: '#E5E7EB' }}
        />
        <div
          className="w-36 h-2.5 rounded-md animate-pulse"
          style={{ background: '#E5E7EB' }}
        />
      </div>
    );
  }

  return (
    <div
      className="w-full min-h-22 rounded-2xl p-3 flex flex-col gap-2 transition-all duration-250 cursor-pointer"
      style={{
        border: isSelected ? '2px solid #3B82F6' : '1px solid #E5E7EB',
        boxShadow: isSelected ? '0 4px 12px rgba(59, 130, 246, 0.15)' : 'none',
      }}
      role="article"
      aria-label={
        isSelected
          ? `선택됨, ${name}, HLI ${hli}점 ${level}, 차량 ${roundedCarMin}분, 도보 ${roundedWalkMin}분`
          : `${rank}위, ${name}, 차량 ${roundedCarMin}분, 도보 ${roundedWalkMin}분, HLI ${hli}점, ${minutesAgo}분 전`
      }
      onClick={onCardClick}
    >
      {/* Header line */}
      <div className="flex items-center justify-between gap-2">
        <span
          className="truncate"
          style={{ fontSize: '16px', fontWeight: 700 }}
        >
          {name}
        </span>
        <div
          className="flex-shrink-0 px-2 h-5 rounded-full flex items-center justify-center text-xs"
          style={{
            background: levelStyle.bg,
            color: levelStyle.text,
            fontWeight: 600,
          }}
        >
          {level}
        </div>
      </div>

      {/* Core line */}
      <div className="flex items-center gap-2 flex-wrap">
        <span style={{ fontSize: '14px', fontWeight: 600 }}>
          HLI {hli} · {level} · {minutesAgo}분 전
        </span>
        {isGray && minutesAgo >= 60 && (
          <span
            className="px-2 py-0.5 rounded text-xs"
            style={{ background: '#F3F4F6', color: '#6B7280' }}
          >
            데이터 지연(60m+)
          </span>
        )}
        {freshness === 'FRESH' && (
          <span
            className="px-2 py-0.5 rounded text-xs"
            style={{ background: '#F3F4F6', color: '#6B7280' }}
          >
            FRESH
          </span>
        )}
        {freshness === 'SOFT' && (
          <span
            className="px-2 py-0.5 rounded text-xs"
            style={{ background: '#F3F4F6', color: '#6B7280' }}
          >
            SOFT
          </span>
        )}
      </div>

      {/* Route summary */}
      {state === 'eta-pending' ? (
        <span
          style={{ fontSize: '14px', fontWeight: 500, color: '#6B7280' }}
        >
          경로 계산 중… · 직선 {displayDistanceKm}km
        </span>
      ) : (
        <span
          style={{ fontSize: '14px', fontWeight: 500, color: '#6B7280' }}
        >
          도보 {roundedWalkMin}분 · 차량 {roundedCarMin}분 · 직선{' '}
          {displayDistanceKm}km
        </span>
      )}

      {/* WARN badge */}
      {state === 'warn' && warnMessage && (
        <div
          className="px-2 py-1 rounded text-xs"
          style={{
            background: '#FFF7ED',
            color: '#C2410C',
            fontWeight: 500,
          }}
        >
          WARN: {warnMessage}
        </div>
      )}

      {/* Actions row */}
      <div className="flex items-center justify-end gap-2 mt-1">
        <button
          className="px-3 h-7 rounded transition-colors hover:bg-gray-200"
          style={{
            background: '#F3F4F6',
            color: '#111827',
            fontSize: '14px',
            fontWeight: 500,
          }}
          onClick={(e) => {
            e.stopPropagation();
            onViewDetails?.();
          }}
          aria-label="상세보기"
        >
          상세보기
        </button>
        <button
          className="px-3 h-7 rounded transition-colors hover:bg-blue-600"
          style={{
            background: '#3B82F6',
            color: '#FFFFFF',
            fontSize: '14px',
            fontWeight: 600,
          }}
          onClick={(e) => {
            e.stopPropagation();
            onStartNavigation?.();
          }}
          aria-label="길 안내 시작"
        >
          길 안내 시작
        </button>
      </div>
    </div>
  );
}
