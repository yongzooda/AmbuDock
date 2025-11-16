// src/components/MarkerHighlight.tsx

export function MarkerHighlight({ style }: { style?: React.CSSProperties }) {
  return (
    <div className="absolute" style={style}>
      {/* Outer pulse ring */}
      <div 
        className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-12 h-12 rounded-full animate-ping"
        style={{ 
          background: 'rgba(59, 130, 246, 0.3)',
          animationDuration: '2s'
        }}
      />
      {/* Middle glow ring */}
      <div 
        className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-9 h-9 rounded-full"
        style={{ 
          background: 'rgba(59, 130, 246, 0.2)',
          boxShadow: '0 0 12px rgba(59, 130, 246, 0.4)'
        }}
      />
      {/* Inner glow ring */}
      <div 
        className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-7 h-7 rounded-full"
        style={{ 
          background: 'rgba(59, 130, 246, 0.25)',
          boxShadow: '0 0 8px rgba(59, 130, 246, 0.5)'
        }}
      />
    </div>
  );
}
