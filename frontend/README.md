# AmbuDock 화면

React 19, TypeScript, Vite, Tailwind CSS로 만든 휴대폰용 지도 화면. 서비스 설명과 전체 실행 방법은 [저장소 README](../README.md)에 있음

```bash
cp .env.example .env.local    # VITE_KAKAO_MAP_KEY 채우기
npm install
npm run dev                   # http://localhost:5173, /api 요청은 localhost:8080 서버로 넘김
```

- `src/App.tsx`: 위치가 100m 넘게 바뀌면 병원 목록, 재난문자 판정, TMAP 이동 시간을 차례로 받아 추천 Top3 카드를 만듦
- `src/components/MapView.tsx`: 카카오 지도, 병원 마커, 내 위치 표시
- `src/components/DetailHalfSheet.tsx`: 병원 상세 (응급실·중환자실·수술실 가용 병상, 전화, 길 안내)
- `src/lib/tmap.ts`: TMAP 앱 딥링크, 앱이 없을 때 스토어 이동
