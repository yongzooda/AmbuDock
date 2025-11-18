// src/components/MarkerTooltip.tsx

import { useEffect, useRef, useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { HospitalMarker } from './HospitalMarker';

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
  containerBounds?: {
    top: number;
    left: number;
    right: number;
    bottom: number;
  };
  /** 닫기 버튼 / 지도 클릭으로 툴팁을 닫을 때 사용할 콜백 */
  onClose?: () => void;
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
  containerBounds,
  onClose,
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
    const tooltipWidth = tooltipRect.width || 0;
    const tooltipHeight = tooltipRect.height || 0;
    const offset = 10;

    // containerBounds 가 아직 없거나 값이 이상하면 위치 보정하지 않음
    if (
      !containerBounds ||
      containerBounds.right <= containerBounds.left ||
      containerBounds.bottom <= containerBounds.top
    ) {
      setPlacement('top');
      setAdjustedPosition(position);
      return;
    }

    let newPlacement: Placement = 'top';
    let finalX = position.x;
    const finalY = position.y;

    // 위쪽 공간 부족하면 아래에 배치
    if (position.y - tooltipHeight - offset < containerBounds.top) {
      newPlacement = 'bottom';
    }

    // 좌우 경계 보정
    const halfWidth = tooltipWidth / 2;
    if (finalX - halfWidth < containerBounds.left) {
      finalX = containerBounds.left + halfWidth;
    } else if (finalX + halfWidth > containerBounds.right) {
      finalX = containerBounds.right - halfWidth;
    }

    setPlacement(newPlacement);
    setAdjustedPosition({ x: finalX, y: finalY });
  }, [position, containerBounds]);

  const offset = 10;
  const isBottom = placement === 'bottom';

  return (
    <div
      className="absolute z-30 pointer-events-none"
      style={{
        left: `${adjustedPosition.x}px`,
        top: `${adjustedPosition.y}px`,
        transform: isBottom ? 'translate(-50%, 0%)' : 'translate(-50%, -100%)',
        marginTop: isBottom ? `${offset}px` : `-${offset}px`,
      }}
    >
      {/* 아래 배치일 때 화살표 (툴팁 위쪽) */}
      {isBottom && (
        <>
          <div
            className="absolute left-1/2 -translate-x-1/2"
            style={{
              top: '-5px',
              width: 0,
              height: 0,
              borderLeft: '5px solid transparent',
              borderRight: '5px solid transparent',
              borderBottom: '5px solid #FFFFFF',
            }}
          />
          <div
            className="absolute left-1/2 -translate-x-1/2"
            style={{
              top: '-6px',
              width: 0,
              height: 0,
              borderLeft: '6px solid transparent',
              borderRight: '6px solid transparent',
              borderBottom: '6px solid #E5E7EB',
              zIndex: -1,
            }}
          />
        </>
      )}

      {/* Tooltip card (여기만 클릭 가능) */}
      <div
        ref={tooltipRef}
        className="relative rounded-md p-2.5 shadow-lg pointer-events-auto"
        style={{
          background: '#FFFFFF',
          border: '1px solid #E5E7EB',
          minWidth: '160px',
          maxWidth: '200px',
        }}
        role="tooltip"
        aria-label={`${hospitalName}${
          showWarn ? `, 경보: WARN ${warnMessage}` : ''
        }, HLI ${hli}점 ${level}, ${minutesAgo}분 전${
          hvecCount !== undefined ? `, 가용 응급실 병상수 ${hvecCount}개` : ''
        }`}
      >
        {/* 닫기 버튼 */}
        {onClose && (
          <button
            type="button"
            className="absolute top-1.5 right-1.5 w-5 h-5 rounded-full flex items-center justify-center hover:bg-gray-100"
            onClick={(e) => {
              e.stopPropagation();
              onClose();
            }}
            aria-label="툴팁 닫기"
          >
            <span
              style={{
                fontSize: '12px',
                fontWeight: 700,
                color: '#6B7280',
                lineHeight: 1,
              }}
            >
              ×
            </span>
          </button>
        )}

        {/* 병원 이름 + 레벨 아이콘 */}
        <div className="mb-1.5 flex items-center gap-1.5 pr-4">
          <HospitalMarker
            level={level}
            isStale={isGray}
            style={{ transform: 'scale(0.8)' }}
          />
          <div style={{ fontSize: '14px', fontWeight: 600 }}>
            {hospitalName}
          </div>
        </div>

        {/* WARN 배지 */}
        {showWarn && (
          <div
            className="mb-1.5 px-1.5 py-0.5 rounded-md inline-flex items-center gap-1"
            style={{
              background: '#FFF7ED',
              color: '#C2410C',
              fontWeight: 600,
              fontSize: '11px',
              border: '1px solid #FED7AA',
            }}
          >
            <AlertTriangle className="w-3 h-3" style={{ strokeWidth: 2.3 }} />
            <span>WARN: {warnMessage}</span>
          </div>
        )}

        {/* 전화번호 */}
        {phone && (
          <div
            className="mb-0.5"
            style={{ fontSize: '12px', fontWeight: 500, color: '#6B7280' }}
          >
            전화 {phone}
          </div>
        )}

        {/* 주소 */}
        {address && (
          <div
            className="mb-1.5"
            style={{
              fontSize: '12px',
              fontWeight: 500,
              color: '#6B7280',
              wordWrap: 'break-word',
            }}
          >
            주소 {address}
          </div>
        )}

        {/* HLI 상태 */}
        <div className="mb-0.5" style={{ fontSize: '13px', fontWeight: 600 }}>
          <span>HLI {hli}</span>
          <span> · </span>
          <span style={{ color: levelStyle.color }}>{level}</span>
          <span> · </span>
          <span style={{ color: isGray ? '#9CA3AF' : 'inherit' }}>
            {minutesAgo}분 전
          </span>
        </div>

        {/* ER 가용 수 */}
        {hvecCount !== undefined && (
          <div
            className="mb-0.5"
            style={{ fontSize: '12px', fontWeight: 500, color: '#6B7280' }}
            aria-label={`가용 응급실 병상수 ${hvecCount}개`}
          >
            가용 응급실 병상수(ER 가용수) {hvecCount}개
          </div>
        )}
      </div>

      {/* 위 배치일 때 화살표 (툴팁 아래쪽) */}
      {!isBottom && (
        <>
          <div
            className="absolute left-1/2 -translate-x-1/2"
            style={{
              bottom: '-5px',
              width: 0,
              height: 0,
              borderLeft: '5px solid transparent',
              borderRight: '5px solid transparent',
              borderTop: '5px solid #FFFFFF',
            }}
          />
          <div
            className="absolute left-1/2 -translate-x-1/2"
            style={{
              bottom: '-6px',
              width: 0,
              height: 0,
              borderLeft: '6px solid transparent',
              borderRight: '6px solid transparent',
              borderTop: '6px solid #E5E7EB',
              zIndex: -1,
            }}
          />
        </>
      )}
    </div>
  );
}
