// src/components/MapLegend.tsx

export function MapLegend() {
  const legendItems = [
    { color: '#10B981', label: 'GREEN: 여유', shape: 'circle' as const },
    { color: '#F59E0B', label: 'YELLOW: 보통', shape: 'circle' as const },
    { color: '#EF4444', label: 'RED: 혼잡', shape: 'circle' as const },
    { color: '#9CA3AF', label: 'GRAY: 데이터 지연(60m+)', shape: 'ring' as const },
  ];

  return (
    <div 
      className="absolute bottom-3 left-3 z-10"
      style={{ maxWidth: '240px' }}
    >
      <div 
        className="rounded-xl p-2"
        style={{ 
          background: 'rgba(255, 255, 255, 0.9)',
          border: '1px solid #E5E7EB',
        }}
      >
        {/* Title */}
        <div 
          className="mb-1.5"
          style={{ 
            fontSize: '11px',
            fontWeight: 600,
            color: '#6B7280'
          }}
        >
          표시 안내
        </div>

        {/* Legend items */}
        <div className="flex flex-col gap-1.5">
          {legendItems.map((item, index) => (
            <div 
              key={index}
              className="flex items-center gap-2"
              role="listitem"
              aria-label={item.label}
            >
              {/* Shape indicator */}
              {item.shape === 'circle' && (
                <div 
                  className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                  style={{ background: item.color }}
                />
              )}
              {item.shape === 'ring' && (
                <div 
                  className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                  style={{ 
                    border: `2px solid ${item.color}`,
                    background: 'transparent'
                  }}
                />
              )}
              
              {/* Label */}
              <span 
                style={{ 
                  fontSize: '12px',
                  fontWeight: 600,
                  color: '#111827'
                }}
              >
                {item.label}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
