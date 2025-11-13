import { Crosshair } from 'lucide-react';

export function TopAppHeader() {
  return (
    <header 
      className="fixed top-0 left-0 right-0 z-30 flex items-center justify-between px-4"
      style={{ 
        height: '56px',
        background: 'rgba(255, 255, 255, 0.95)',
        borderBottom: '1px solid #E5E7EB',
        boxShadow: '0 1px 4px rgba(0, 0, 0, 0.06)',
      }}
      aria-label="AmbuDock"
    >
      {/* Left: Logo */}
      <div className="flex items-center gap-2">
        {/* Emblem */}
        <div 
          className="w-5 h-5 rounded-full flex items-center justify-center bg-white"
          style={{ border: '1px solid #3B82F6' }}
        >
          <Crosshair className="w-3 h-3" style={{ color: '#3B82F6' }} />
        </div>
        
        {/* Wordmark */}
        <span 
          style={{ 
            fontSize: '18px',
            fontWeight: 700,
            letterSpacing: '0.01em',
            color: '#111827'
          }}
        >
          AmbuDock
        </span>
      </div>

      {/* Right: Status chip */}
      <div 
        className="px-2 py-1 rounded-full"
        style={{ 
          background: '#ECFDF5',
          color: '#047857',
          fontSize: '12px',
          fontWeight: 600
        }}
      >
        LIVE
      </div>
    </header>
  );
}
