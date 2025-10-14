import React from 'react'

function App() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gray-50 text-gray-800">
      {/* Header */}
      <header className="w-full bg-blue-600 text-white py-4 shadow-md">
        <h1 className="text-2xl font-bold text-center">AmbuDock 🚑</h1>
      </header>

      {/* Main Section */}
      <main className="flex flex-col items-center mt-10 space-y-6">
        <h2 className="text-3xl font-semibold text-blue-600">
          국가 위기 대응 응급실 길안내 서비스
        </h2>

        <p className="text-gray-600 max-w-md text-center">
          위급 상황에서 지금 가장 빨리 치료받을 수 있는 병원을 한눈에 확인하세요.
        </p>

        <button className="bg-blue-500 text-white px-6 py-2 rounded-lg shadow hover:bg-blue-600 transition">
          주변 병원 보기
        </button>
      </main>

      {/* Footer */}
      <footer className="mt-auto py-4 text-gray-400 text-sm">
        © 2025 AmbuDock. All rights reserved.
      </footer>
    </div>
  )
}

export default App
