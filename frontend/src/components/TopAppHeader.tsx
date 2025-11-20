// src/components/TopAppHeader.tsx

import ambudockLogo from '../assets/ambudock-logo.png';

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
        {/* Brand logo image */}
        <img
          src={ambudockLogo}
          alt="AmbuDock"
          className="block"
          style={{
            height: '28px', // 필요하면 24~32px로 조절
            width: 'auto',
          }}
        />

        <span
          style={{
            fontSize: '18px',
            fontWeight: 700,
            letterSpacing: '0.01em',
            color: '#111827',
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
          fontWeight: 600,
        }}
      >
        LIVE
      </div>
    </header>
  );
}
