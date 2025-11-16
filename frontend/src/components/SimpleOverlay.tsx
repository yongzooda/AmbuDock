// src/components/SimpleOverlay.tsx

type SimpleOverlayProps = {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  message: string;
};

export function SimpleOverlay({ isOpen, onClose, title, message }: SimpleOverlayProps) {
  if (!isOpen) return null;

  return (
    <>
      {/* Backdrop */}
      <div 
        className="fixed inset-0 bg-black z-50 flex items-center justify-center transition-opacity"
        style={{ opacity: 0.5 }}
        onClick={onClose}
      />

      {/* Overlay card */}
      <div 
        className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 bg-white rounded-2xl p-6 z-50 shadow-2xl"
        style={{ 
          maxWidth: '320px',
          width: '85%'
        }}
      >
        <h3 className="mb-3" style={{ fontSize: '20px', fontWeight: 700 }}>
          {title}
        </h3>
        <p className="mb-4" style={{ fontSize: '16px', fontWeight: 500, color: '#6B7280' }}>
          {message}
        </p>
        <button
          className="w-full px-4 py-3 rounded-lg transition-colors hover:bg-blue-600"
          style={{ 
            background: '#3B82F6',
            color: '#FFFFFF',
            fontSize: '16px',
            fontWeight: 600
          }}
          onClick={onClose}
        >
          확인
        </button>
      </div>
    </>
  );
}
