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
  return (
    <div
      className="
        w-full bg-white flex flex-col overflow-y-auto
        h-[24vh] min-h-[210px]
        sm:h-[26vh] sm:min-h-[220px]
        md:h-[30vh] md:min-h-[250px]
      "
      style={{
        borderTopLeftRadius: '20px',
        borderTopRightRadius: '20px',
        padding: '8px 12px 10px',
      }}
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-2 px-1">
        <span style={{ fontSize: '15px', fontWeight: 600 }}>
          현재 추천 Top3
        </span>
        <span style={{ fontSize: '11px', fontWeight: 500, color: '#6B7280' }}>
          실시간 갱신 60~180초
        </span>
      </div>

      {/* Cards */}
      <div className="flex flex-col gap-2.5">
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
  );
}
