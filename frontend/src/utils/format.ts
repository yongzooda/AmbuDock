// src/utils/format.ts

export const minutesFromSec = (sec: number) => Math.max(0, Math.round(sec / 60));
export const levelOrder: Record<string, number> = { GREEN: 0, YELLOW: 1, RED: 2, GRAY: 3 };
