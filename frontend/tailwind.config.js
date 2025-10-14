/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',              // Vite의 메인 HTML 파일
    './src/**/*.{js,ts,jsx,tsx}' // src 폴더의 모든 React/TS 파일
  ],
  theme: {
    extend: {
      colors: {
        primary: '#2563eb',   // Tailwind blue-600
        secondary: '#1e3a8a', // Tailwind blue-900
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
