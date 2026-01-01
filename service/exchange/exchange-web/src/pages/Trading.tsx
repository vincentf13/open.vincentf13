
import { useEffect, useState } from 'react';

import GlassLayout from '../components/layout/GlassLayout';
import AuthModal from '../components/auth/AuthModal';
import { AUTH_REQUIRED_EVENT } from '../api/client';
import {
  fetchInstrumentSummaries,
  getCachedInstrumentId,
  getCachedInstrumentSummaries,
  setCachedInstrumentId,
  type InstrumentSummary,
} from '../api/instrument';
import { resetSystemData } from '../api/admin';
import Header from '../components/trading/Header';
// ... (omitted)

export default function Trading() {
  const [resetting, setResetting] = useState(false);
  // ... (rest of states)

  const handleResetData = async () => {
    if (!window.confirm('WARNING: This will delete ALL trading data, orders, positions and Kafka topics. Are you sure?')) {
      return;
    }
    setResetting(true);
    try {
      await resetSystemData();
      alert('System data reset successfully. Please refresh the page.');
      window.location.reload();
    } catch (error: any) {
      alert('Failed to reset data: ' + (error.response?.data?.message || error.message));
    } finally {
      setResetting(false);
    }
  };

  // ... (rest of logic)

  return (
    <GlassLayout>
      <div className="relative flex-1 min-h-0">
        {/* ... (Header) */}

        <div className="flex-1 min-h-0 pt-24 lg:pr-[260px] flex flex-col gap-4">
          {/* ... (Main Content) */}
        </div>

        {/* Reset Data Button - Bottom Left */}
        <div className="fixed bottom-4 left-4 z-50">
          <button
            onClick={handleResetData}
            disabled={resetting}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-600 text-[10px] font-bold uppercase tracking-wider hover:bg-rose-500 hover:text-white transition-all active:scale-95 disabled:opacity-50"
          >
            <span className="w-2 h-2 rounded-full bg-rose-500 animate-pulse" />
            {resetting ? 'Resetting...' : 'Reset System Data'}
          </button>
        </div>

        {/* ... (Right Panel) */}
      </div>
      <AuthModal open={authOpen} onSuccess={() => setAuthOpen(false)} />
    </GlassLayout>
  );
}
