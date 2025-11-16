// src/components/MapView.tsx

import { useState, type MouseEvent } from 'react';
import { Crosshair } from 'lucide-react';
import { HospitalMarker } from './HospitalMarker';
import { MapLegend } from './MapLegend';
import { MarkerTooltip } from './MarkerTooltip';
import { MarkerHighlight } from './MarkerHighlight';

type MapViewProps = {
  highlightedMarkerId?: string | null;
};

export function MapView({ highlightedMarkerId }: MapViewProps) {
  const [selectedMarker, setSelectedMarker] = useState<string | null>('green-1');

  // Calculate pixel position from percentage
  const getPixelPosition = (
    leftPercent: string,
    topPercent: string,
    containerWidth: number = 390,
    containerHeight: number = 500,
  ) => {
    const left = (parseFloat(leftPercent) / 100) * containerWidth;
    const top = (parseFloat(topPercent) / 100) * containerHeight;
    return { x: left, y: top };
  };

  const handleMapClick = () => {
    setSelectedMarker(null);
  };

  const handleMarkerClick = (markerId: string, e: MouseEvent<HTMLDivElement>) => {
    e.stopPropagation();
    setSelectedMarker(markerId);
  };

  // GREEN marker is centered in the safe viewing area (corresponds to Card #1)
  const greenMarkerPosition = { left: '50%', top: '45%' };
  const tooltipPosition = getPixelPosition(
    greenMarkerPosition.left,
    greenMarkerPosition.top,
  );

  // Show highlight for card selection (hospital-1 = Card #1 = green marker)
  const showHighlight = highlightedMarkerId === 'hospital-1';

  return (
    <div className="relative flex-1 overflow-hidden">
      {/* Seoul Jung-gu realistic map base */}
      <div
        className="w-full h-full relative"
        style={{
          background: '#F5F5F5',
          opacity: 1,
          filter: 'none',
        }}
        onClick={handleMapClick}
      >
        {/* Map base with roads and landmarks - panned to keep centered marker looking natural */}
        <svg
          className="w-full h-full"
          viewBox="0 0 390 500"
          preserveAspectRatio="xMidYMid slice"
          style={{ opacity: 1 }}
        >
          {/* Background */}
          <rect width="390" height="500" fill="#F5F5F5" />

          {/* Building blocks - soft gray - adjusted positions for natural look */}
          <rect
            x="40"
            y="100"
            width="60"
            height="80"
            fill="#E8E8E8"
            stroke="#D0D0D0"
            strokeWidth="0.5"
          />
          <rect
            x="120"
            y="80"
            width="70"
            height="90"
            fill="#E8E8E8"
            stroke="#D0D0D0"
            strokeWidth="0.5"
          />
          <rect
            x="220"
            y="90"
            width="55"
            height="75"
            fill="#E8E8E8"
            stroke="#D0D0D0"
            strokeWidth="0.5"
          />
          <rect
            x="300"
            y="70"
            width="65"
            height="95"
            fill="#E8E8E8"
            stroke="#D0D0D0"
            strokeWidth="0.5"
          />
          <rect
            x="50"
            y="220"
            width="75"
            height="85"
            fill="#E8E8E8"
            stroke="#D0D0D0"
            strokeWidth="0.5"
          />
          <rect
            x="160"
            y="210"
            width="60"
            height="70"
            fill="#E8E8E8"
            stroke="#D0D0D0"
            strokeWidth="0.5"
          />
          <rect
            x="250"
            y="220"
            width="80"
            height="90"
            fill="#E8E8E8"
            stroke="#D0D0D0"
            strokeWidth="0.5"
          />
          <rect
            x="70"
            y="340"
            width="65"
            height="75"
            fill="#E8E8E8"
            stroke="#D0D0D0"
            strokeWidth="0.5"
          />
          <rect
            x="180"
            y="350"
            width="70"
            height="80"
            fill="#E8E8E8"
            stroke="#D0D0D0"
            strokeWidth="0.5"
          />
          <rect
            x="290"
            y="335"
            width="60"
            height="85"
            fill="#E8E8E8"
            stroke="#D0D0D0"
            strokeWidth="0.5"
          />

          {/* Park area - pale green (덕수궁) */}
          <rect
            x="100"
            y="160"
            width="50"
            height="50"
            fill="#E8F5E9"
            stroke="#C8E6C9"
            strokeWidth="0.5"
          />
          <text
            x="125"
            y="190"
            fill="#558B2F"
            fontSize="7"
            textAnchor="middle"
            fontWeight="500"
          >
            덕수궁
          </text>

          {/* Primary roads - darker gray with white center line */}
          {/* 세종대로 (vertical main road) */}
          <rect x="185" y="0" width="18" height="500" fill="#B8B8B8" />
          <line
            x1="194"
            y1="0"
            x2="194"
            y2="500"
            stroke="white"
            strokeWidth="1"
            strokeDasharray="8,6"
          />
          <text
            x="194"
            y="40"
            fill="#666666"
            fontSize="8"
            textAnchor="middle"
            fontWeight="500"
          >
            세종대로
          </text>

          {/* 남대문로 (diagonal) */}
          <path d="M 60 200 L 340 240" stroke="#B8B8B8" strokeWidth="14" />
          <line
            x1="70"
            y1="203"
            x2="330"
            y2="238"
            stroke="white"
            strokeWidth="1"
            strokeDasharray="6,4"
          />
          <text
            x="200"
            y="225"
            fill="#666666"
            fontSize="8"
            textAnchor="middle"
            fontWeight="500"
          >
            남대문로
          </text>

          {/* 을지로 (horizontal) */}
          <rect x="0" y="310" width="390" height="14" fill="#B8B8B8" />
          <line
            x1="0"
            y1="317"
            x2="390"
            y2="317"
            stroke="white"
            strokeWidth="1"
            strokeDasharray="8,6"
          />
          <text
            x="320"
            y="320"
            fill="#666666"
            fontSize="8"
            textAnchor="middle"
            fontWeight="500"
          >
            을지로
          </text>

          {/* Secondary roads - lighter gray */}
          <rect x="0" y="180" width="390" height="8" fill="#D0D0D0" />
          <rect x="265" y="0" width="8" height="500" fill="#D0D0D0" />
          <rect x="125" y="0" width="8" height="500" fill="#D0D0D0" />

          {/* Landmark labels */}
          <text
            x="195"
            y="130"
            fill="#333333"
            fontSize="9"
            textAnchor="middle"
            fontWeight="600"
          >
            시청
          </text>
        </svg>

        {/* User location - centered near upper-middle */}
        <div
          className="absolute left-1/2 top-1/3 -translate-x-1/2 -translate-y-1/2"
          style={{ zIndex: 5 }}
        >
          {/* Accuracy ring */}
          <div
            className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-15 h-15 rounded-full"
            style={{
              background: 'rgba(59, 130, 246, 0.16)',
              border: '1px solid rgba(59, 130, 246, 0.4)',
            }}
          />
          {/* Blue dot */}
          <div
            className="relative w-2.5 h-2.5 rounded-full"
            style={{ background: '#2563EB', zIndex: 6 }}
            role="img"
            aria-label="현재 위치"
          />
        </div>

        {/* Marker highlights - show when card is selected */}
        {showHighlight && (
          <MarkerHighlight
            style={{
              left: greenMarkerPosition.left,
              top: greenMarkerPosition.top,
              transform: 'translate(-50%, -50%)',
              zIndex: 4,
            }}
          />
        )}

        {/* Exactly 4 hospital markers */}
        {/* GREEN hospital marker - centered in safe viewing area (Card #1) */}
        <div
          className="absolute cursor-pointer transition-transform hover:scale-110"
          style={{
            left: greenMarkerPosition.left,
            top: greenMarkerPosition.top,
            zIndex: 5,
            transform: 'translate(-50%, -50%)',
          }}
          onClick={(e) => handleMarkerClick('green-1', e)}
        >
          <HospitalMarker level="GREEN" />
        </div>

        {/* YELLOW hospital marker (Card #2) */}
        <div
          className="absolute cursor-pointer transition-transform hover:scale-110"
          style={{
            left: '65%',
            top: '35%',
            zIndex: 5,
            transform: 'translate(-50%, -50%)',
          }}
          onClick={(e) => handleMarkerClick('yellow-1', e)}
        >
          <HospitalMarker level="YELLOW" />
        </div>

        {/* RED hospital marker (Card #3) */}
        <div
          className="absolute cursor-pointer transition-transform hover:scale-110"
          style={{
            left: '75%',
            top: '55%',
            zIndex: 5,
            transform: 'translate(-50%, -50%)',
          }}
          onClick={(e) => handleMarkerClick('red-1', e)}
        >
          <HospitalMarker level="RED" />
        </div>

        {/* GRAY hospital marker (stale ≥60m) with clock icon */}
        <div
          className="absolute cursor-pointer transition-transform hover:scale-110"
          style={{
            left: '25%',
            top: '60%',
            zIndex: 5,
            transform: 'translate(-50%, -50%)',
          }}
          onClick={(e) => handleMarkerClick('gray-1', e)}
        >
          <HospitalMarker level="GRAY" isStale />
        </div>

        {/* Marker Tooltip - shows for selected marker (when clicked) */}
        {selectedMarker === 'green-1' && (
          <MarkerTooltip
            hospitalName="한빛병원"
            phone="010-1234-5678"
            address="서울 중구 남대문로 00"
            hli={87}
            level="GREEN"
            minutesAgo={2}
            hvecCount={3}
            variant="warn"
            warnMessage="호우경보(중구)"
            position={tooltipPosition}
            containerBounds={{ top: 80, left: 16, right: 374, bottom: 490 }}
          />
        )}
      </div>

      {/* Map Legend - bottom-left */}
      <MapLegend />

      {/* FAB - 현위치 */}
      <button
        className="absolute bottom-6 right-4 w-14 h-14 rounded-full bg-white flex items-center justify-center transition-transform hover:scale-95 active:scale-90"
        style={{
          boxShadow:
            '0 4px 12px rgba(0, 0, 0, 0.15), 0 2px 4px rgba(0, 0, 0, 0.1)',
          zIndex: 10,
        }}
        aria-label="현위치로 재중심"
      >
        <Crosshair className="w-6 h-6" style={{ color: '#111827' }} />
      </button>
    </div>
  );
}
