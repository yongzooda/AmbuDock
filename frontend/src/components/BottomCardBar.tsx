// src/components/BottomCardBar.tsx

import { HospitalCard } from './HospitalCard';

type Hospital = {
  id: string;
  rank: number;
  name: string;
  level: 'GREEN' | 'YELLOW' | 'RED' | 'GRAY';
  hli: number;
  minutesAgo: number;
  walkMin: number;
  carMin: number;
  distanceKm: number;
  state?: 'default' | 'loading' | 'eta-pending' | 'warn' | 'gray';
  warnMessage?: string;
  freshness?: 'FRESH' | 'SOFT' | null;
  phone: string;
  address: string;
  erAvailable: number;
  icuAvailTotal: number;
};

type BottomCardBarProps = {
  hospitals: Hospital[];
  selectedHospitalId: string | null;
  onCardSelect: (id: string) => void;
  onViewDetails: (hospital: Hospital) => void;
  onStartNavigation: (hospital: Hospital) => void;
};

export function BottomCardBar({
  hospitals,
  selectedHospitalId,
  onCardSelect,
  onViewDetails,
  onStartNavigation,
}: BottomCardBarProps) {
  // 추천 병원이 없으면 바 자체를 숨김
  if (!hospitals.length) return null;

  return (
    <section
      className="
        w-full flex-none
        bg-white/95 backdrop-blur-sm
        border-t border-slate-200
        rounded-t-[20px]
      "
    >
      {/* 전체 바는 최대 38vh까지만 차지 → 지도 영역이 더 넓어짐 */}
      <div
        className="
          mx-auto max-w-screen-sm
          px-3 pt-2
          pb-[env(safe-area-inset-bottom,12px)]
          flex flex-col gap-2
          max-h-[38vh]
        "
      >
        {/* Header (항상 보이게 flex-none) */}
        <div className="flex items-center justify-between px-1 flex-none">
          <span className="text-[15px] font-semibold">현재 추천 Top3</span>
          <span className="text-[11px] font-medium text-slate-500">
            실시간 갱신 60~180초
          </span>
        </div>

        {/* Cards 영역만 스크롤 */}
        <div className="flex-1 flex flex-col gap-2.5 overflow-y-auto">
          {hospitals.map((hospital) => (
            <HospitalCard
              key={hospital.id}
              rank={hospital.rank}
              name={hospital.name}
              level={hospital.level}
              hli={hospital.hli}
              minutesAgo={hospital.minutesAgo}
              walkMin={hospital.walkMin}
              carMin={hospital.carMin}
              distanceKm={hospital.distanceKm}
              state={hospital.state}
              warnMessage={hospital.warnMessage}
              freshness={hospital.freshness}
              isSelected={hospital.id === selectedHospitalId}
              onCardClick={() => onCardSelect(hospital.id)}
              onViewDetails={() => onViewDetails(hospital)}
              onStartNavigation={() => onStartNavigation(hospital)}
            />
          ))}
        </div>
      </div>
    </section>
  );
}
