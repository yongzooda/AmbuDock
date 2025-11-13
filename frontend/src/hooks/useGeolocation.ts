// src/hooks/useGeolocation.ts
import * as React from 'react';

export function useGeolocation() {
  const [coords, setCoords] = React.useState<{lat:number; lon:number} | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!navigator.geolocation) { setError('GEO_UNSUPPORTED'); return; }
    const watchId = navigator.geolocation.watchPosition(
      (p) => setCoords({ lat: p.coords.latitude, lon: p.coords.longitude }),
      (e) => setError(e.code === e.PERMISSION_DENIED ? 'DENIED' : 'ERROR'),
      { enableHighAccuracy: true, maximumAge: 10000, timeout: 10000 }
    );
    return () => navigator.geolocation.clearWatch(watchId);
  }, []);

  return { coords, error };
}
