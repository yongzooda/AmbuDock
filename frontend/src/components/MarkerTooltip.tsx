import { useEffect, useRef, useState } from 'react';
import { AlertTriangle } from 'lucide-react';

type TooltipVariant = 'default' | 'warn' | 'missing-data' | 'gray';
type Placement = 'top' | 'bottom';

type MarkerTooltipProps = {
  hospitalName: string;
  phone?: string;
  address?: string;
  hli: number;
  level: 'GREEN' | 'YELLOW' | 'RED' | 'GRAY';
  minutesAgo: number;
  hvecCount?: number;
  warnMessage?: string;
  variant?: TooltipVariant;
  position: { x: number; y: number };
  containerBounds?: { top: number; left: number; right: number; bottom: number };
};

const levelStyles = {
  GREEN: { color: '#047857' },
  YELLOW: { color: '#B45309' },
  RED: { color: '#B91C1C' },
  GRAY: { color: '#6B7280' },
};

export function MarkerTooltip({
  hospitalName,
  phone,
  address,
  hli,
  level,
  minutesAgo,
  hvecCount,
  warnMessage,
  variant = 'default',
  position,
  containerBounds = { top: 80, left: 16, right: 374, bottom: 490 },
}: MarkerTooltipProps) {
  const tooltipRef = useRef<HTMLDivElement>(null);
  const [placement, setPlacement] = useState<Placement>('top');
  const [adjustedPosition, setAdjustedPosition] = useState(position);
  
  const levelStyle = levelStyles[level];
  const isGray = variant === 'gray';
  const showWarn = variant === 'warn' && warnMessage;

  useEffect(() => {
    if (!tooltipRef.current) return;

    const tooltipRect = tooltipRef.current.getBoundingClientRect();
    const tooltipWidth = tooltipRect.width;
    const tooltipHeight = tooltipRect.height;
    const offset = 14;

    let newPlacement: Placement = 'top';
    let finalX = position.x;
    let finalY = position.y;

    // Check if tooltip fits above the marker
    if (position.y - tooltipHeight - offset < containerBounds.top) {
      // Not enough space above, flip to bottom
      newPlacement = 'bottom';
    }

    // Horizontal bounds checking
    const halfWidth = tooltipWidth / 2;
    if (finalX - halfWidth < containerBounds.left) {
      finalX = containerBounds.left + halfWidth;
    } else if (finalX + halfWidth > containerBounds.right) {
      finalX = containerBounds.right - halfWidth;
    }

    setPlacement(newPlacement);
    setAdjustedPosition({ x: finalX, y: finalY });
  }, [position, containerBounds]);

  const offset = 14;
  const isBottom = placement === 'bottom';

  return (
    <div
      ref={tooltipRef}
      className="absolute z-30 pointer-events-none"
      style={{
        left: `${adjustedPosition.x}px`,
        top: `${adjustedPosition.y}px`,
        transform: isBottom ? 'translate(-50%, 0%)' : 'translate(-50%, -100%)',
        marginTop: isBottom ? `${offset}px` : `-${offset}px`,
      }}
    >
      {/* Pointer arrow - top of tooltip when placement is bottom */}
      {isBottom && (
        <>
          <div
            className="absolute left-1/2 -translate-x-1/2"
            style={{
              top: '-6px',
              width: 0,
              height: 0,
              borderLeft: '6px solid transparent',
              borderRight: '6px solid transparent',
              borderBottom: '6px solid #FFFFFF',
            }}
          />
          <div
            className="absolute left-1/2 -translate-x-1/2"
            style={{
              top: '-7px',
              width: 0,
              height: 0,
              borderLeft: '7px solid transparent',
              borderRight: '7px solid transparent',
              borderBottom: '7px solid #E5E7EB',
              zIndex: -1,
            }}
          />
        </>
      )}

      {/* Tooltip card */}
      <div
        className="relative rounded-lg p-3 shadow-lg"
        style={{
          background: '#FFFFFF',
          border: '1px solid #E5E7EB',
          minWidth: '200px',
          maxWidth: '260px',
          marginTop: isBottom ? '0' : '0',
          marginBottom: isBottom ? '0' : '0',
        }}
        role="tooltip"
        aria-label={
          `${hospitalName}${showWarn ? `, 경보: WARN ${warnMessage}` : ''}, HLI ${hli}점 ${level}, ${minutesAgo}분 전${hvecCount !== undefined ? `, 가용 응급실 병상수 ${hvecCount}개` : ''}`
        }
      >
        {/* Hospital name - bold */}
        <div className="mb-2" style={{ fontSize: '16px', fontWeight: 700 }}>
          {hospitalName}
        </div>

        {/* WARN badge - only in warn variant, directly under hospital name */}
        {showWarn && (
          <div
            className="mb-2 px-2 py-1 rounded-md inline-flex items-center gap-1.5"
            style={{
              background: '#FFF7ED',
              color: '#C2410C',
              fontWeight: 600,
              fontSize: '13px',
              border: '1px solid #FED7AA',
            }}
          >
            <AlertTriangle className="w-3.5 h-3.5" style={{ strokeWidth: 2.5 }} />
            <span>WARN: {warnMessage}</span>
          </div>
        )}

        {/* Phone - only if provided */}
        {phone && (
          <div className="mb-1" style={{ fontSize: '14px', fontWeight: 500, color: '#6B7280' }}>
            전화 {phone}
          </div>
        )}

        {/* Address - only if provided */}
        {address && (
          <div className="mb-2" style={{ fontSize: '14px', fontWeight: 500, color: '#6B7280', wordWrap: 'break-word' }}>
            주소 {address}
          </div>
        )}

        {/* HLI status line */}
        <div className="mb-1" style={{ fontSize: '14px', fontWeight: 600 }}>
          <span>HLI {hli}</span>
          <span> · </span>
          <span style={{ color: levelStyle.color }}>{level}</span>
          <span> · </span>
          <span style={{ color: isGray ? '#9CA3AF' : 'inherit' }}>{minutesAgo}분 전</span>
        </div>

        {/* ER capacity - only if provided */}
        {hvecCount !== undefined && (
          <div 
            className="mb-1" 
            style={{ fontSize: '14px', fontWeight: 500, color: '#6B7280' }}
            aria-label={`가용 응급실 병상수 ${hvecCount}개`}
          >
            가용 응급실 병상수(ER 가용수) {hvecCount}개
          </div>
        )}
      </div>

      {/* Pointer arrow - bottom of tooltip when placement is top */}
      {!isBottom && (
        <>
          <div
            className="absolute left-1/2 -translate-x-1/2"
            style={{
              bottom: '-6px',
              width: 0,
              height: 0,
              borderLeft: '6px solid transparent',
              borderRight: '6px solid transparent',
              borderTop: '6px solid #FFFFFF',
            }}
          />
          <div
            className="absolute left-1/2 -translate-x-1/2"
            style={{
              bottom: '-7px',
              width: 0,
              height: 0,
              borderLeft: '7px solid transparent',
              borderRight: '7px solid transparent',
              borderTop: '7px solid #E5E7EB',
              zIndex: -1,
            }}
          />
        </>
      )}
    </div>
  );
}
