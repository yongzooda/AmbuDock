// src/components/HospitalMarker.tsx

import { Clock } from 'lucide-react';

type MarkerLevel = 'GREEN' | 'YELLOW' | 'RED' | 'GRAY';

type HospitalMarkerProps = {
  level: MarkerLevel;
  isStale?: boolean;
  style?: React.CSSProperties;
};

const levelColors = {
  GREEN: '#10B981',
  YELLOW: '#F59E0B',
  RED: '#EF4444',
  GRAY: '#9CA3AF',
};

export function HospitalMarker({ level, isStale = false }: HospitalMarkerProps) {
  const ariaLabel = `${level} 병원 마커`;
  
  return (
    <div className="relative" role="img" aria-label={ariaLabel}>
      <div 
        className="w-5.5 h-5.5 rounded-full flex items-center justify-center relative"
        style={{ 
          background: levelColors[level],
          boxShadow: '0 2px 8px rgba(0, 0, 0, 0.12)'
        }}
      >
        <div className="w-3 h-3 rounded-full bg-white" />
      </div>
      
      {/* Stale indicator - clock icon for GRAY */}
      {isStale && level === 'GRAY' && (
        <div className="absolute -bottom-1 left-1/2 -translate-x-1/2">
          <Clock className="w-2 h-2" style={{ color: '#6B7280' }} />
        </div>
      )}
    </div>
  );
}
