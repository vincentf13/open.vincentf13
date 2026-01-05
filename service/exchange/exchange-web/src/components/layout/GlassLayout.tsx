import React from 'react';

interface GlassLayoutProps {
  children: React.ReactNode;
}

export default function GlassLayout({ children }: GlassLayoutProps) {
  return (
    <div className="min-h-screen w-full bg-[#D6E4F0] relative overflow-x-hidden">
      {/* Background Gradients */}
      <div className="fixed inset-0 pointer-events-none">
        <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] bg-blue-300/20 rounded-full blur-[120px]" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] bg-purple-300/20 rounded-full blur-[120px]" />
        <div className="absolute top-[20%] right-[10%] w-[30%] h-[30%] bg-cyan-300/20 rounded-full blur-[100px]" />
      </div>

      {/* Main Content */}
      <div className="relative z-10 mx-auto max-w-[1600px] p-4 lg:p-6 min-h-screen flex flex-col">
        <div className="liquid-shell flex-1 flex flex-col rounded-3xl overflow-visible">
            {children}
        </div>
      </div>
    </div>
  );
}
