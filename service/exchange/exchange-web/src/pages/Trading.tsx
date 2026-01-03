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
import Chart from '../components/trading/Chart';
import OrderBook from '../components/trading/OrderBook';
import TradeForm from '../components/trading/TradeForm';
import Positions from '../components/trading/Positions';
import MarketStats from '../components/trading/MarketStats';
import AccountPanel from '../components/trading/AccountPanel';

const hasToken = () => {
  return Boolean(localStorage.getItem('accessToken'));
};

const REFRESH_AFTER_LOGIN_KEY = 'refreshAfterLogin';

export default function Trading() {
  const [authOpen, setAuthOpen] = useState(() => !hasToken());
  const [resetting, setResetting] = useState(false);
  const [instruments, setInstruments] = useState<InstrumentSummary[]>(() => getCachedInstrumentSummaries());
  const [selectedInstrumentId, setSelectedInstrumentId] = useState<string | null>(() => {
    const cachedId = getCachedInstrumentId();
    const cachedList = getCachedInstrumentSummaries();
    return cachedId || cachedList[0]?.instrumentId || null;
  });
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  const handleRefresh = () => {
    setRefreshTrigger((prev) => prev + 1);
  };

  const handleLogout = () => {
    setAuthOpen(true);
  };

  const selectedInstrument =
    instruments.find(item => item.instrumentId === selectedInstrumentId) || instruments[0] || null;

  useEffect(() => {
    const handleAuthRequired = () => {
      setAuthOpen(true);
    };
    window.addEventListener(AUTH_REQUIRED_EVENT, handleAuthRequired);
    return () => {
      window.removeEventListener(AUTH_REQUIRED_EVENT, handleAuthRequired);
    };
  }, []);

  useEffect(() => {
    if (localStorage.getItem(REFRESH_AFTER_LOGIN_KEY) === '1') {
      localStorage.removeItem(REFRESH_AFTER_LOGIN_KEY);
      setRefreshTrigger((prev) => prev + 1);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    const loadInstruments = async () => {
      try {
        const list = await fetchInstrumentSummaries();
        if (cancelled) {
          return;
        }
        setInstruments(list);
        setSelectedInstrumentId((current) => {
          const next =
            list.find(item => item.instrumentId === current)?.instrumentId ||
            list[0]?.instrumentId ||
            null;
          setCachedInstrumentId(next);
          return next;
        });
      } catch (error) {
        return;
      }
    };

    loadInstruments();

    return () => {
      cancelled = true;
    };
  }, []);

  const handleResetData = async () => {
    if (!window.confirm('WARNING: This will delete ALL trading data, orders, positions and Kafka topics. Are you sure?')) {
      return;
    }
    setResetting(true);
    try {
      const result = await resetSystemData();
      if (String(result?.code) !== '0') {
        alert(result?.message || 'Failed to reset data.');
        return;
      }
      // 僅清空業務相關數據，保留 accessToken 以維持登入狀態
      localStorage.removeItem('instrumentSummaries');
      localStorage.removeItem('selectedInstrumentId');
      try {
        const list = await fetchInstrumentSummaries();
        setInstruments(list);
        setSelectedInstrumentId(() => {
          const next = list[0]?.instrumentId || null;
          setCachedInstrumentId(next);
          return next;
        });
      } catch (error) {
        // Ignore refresh failure; keep current view.
      }
      setTimeout(() => {
        handleRefresh();
      }, 300);
      setTimeout(() => {
        handleRefresh();
      }, 2000);
      setTimeout(() => {
        handleRefresh();
      }, 4000);
      alert('System data reset successfully.');
    } catch (error: any) {
      alert('Failed to reset data: ' + (error.response?.data?.message || error.message));
    } finally {
      setResetting(false);
    }
  };

  return (
    <GlassLayout>
      <div className="relative flex-1 min-h-0">
        <div className="absolute inset-x-0 top-0 z-30">
          <Header onLogout={handleLogout} />
        </div>

        <div className="flex-1 min-h-0 pt-24 lg:pr-[260px] flex flex-col gap-4">
          <div className="grid grid-cols-1 lg:grid-cols-[2.4fr,0.6fr] min-h-0">
            {/* Left Column: Market Info + Chart */}
            <div className="flex flex-col min-w-0 border-r border-white/20">
              <div className="border-b border-white/20 bg-white/5">
                <MarketStats
                  instruments={instruments}
                  selectedInstrument={selectedInstrument}
                  refreshTrigger={refreshTrigger}
                  onSelectInstrument={(instrument) => {
                    setSelectedInstrumentId(instrument.instrumentId);
                    setCachedInstrumentId(instrument.instrumentId);
                  }}
                />
              </div>
              <div className="flex-1 min-h-0 relative z-10">
                <Chart instrumentId={selectedInstrumentId} refreshTrigger={refreshTrigger} />
              </div>
            </div>

            {/* Middle Column: Order Book */}
            <div className="flex flex-col min-w-0 bg-white/5">
              <OrderBook instrumentId={selectedInstrumentId} contractSize={selectedInstrument?.contractSize} refreshTrigger={refreshTrigger} />
            </div>
          </div>

          <div className="px-2 lg:px-4">
            <Positions instruments={instruments} selectedInstrumentId={selectedInstrumentId} refreshTrigger={refreshTrigger} />
          </div>
        </div>

        {/* Right Panel: Trade + Account */}
        <div className="flex flex-col w-full border-t border-white/20 bg-white/5 lg:absolute lg:top-0 lg:right-0 lg:h-full lg:w-[260px] lg:z-10 lg:border-t-0 lg:border-l lg:border-white/20">
          <div className="flex flex-col h-full lg:pt-24">
            <div className="border-b border-white/20">
              <TradeForm instrument={selectedInstrument} onOrderPlaced={handleRefresh} />
            </div>
            <div className="flex-1 min-h-0 p-4">
              <AccountPanel refreshTrigger={refreshTrigger} />
            </div>
          </div>
        </div>

        {/* Reset Data Button - Bottom Left */}
        <div className="fixed bottom-4 left-4 z-50 flex items-center gap-2">
          <button
            onClick={handleResetData}
            disabled={resetting}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-600 text-[10px] font-bold uppercase tracking-wider hover:bg-rose-500 hover:text-white transition-all active:scale-95 disabled:opacity-50"
          >
            <span className="w-2 h-2 rounded-full bg-rose-500 animate-pulse" />
            {resetting ? 'Resetting...' : 'Reset System Data'}
          </button>

          <button
            onClick={handleRefresh}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 text-[10px] font-bold uppercase tracking-wider hover:bg-emerald-500 hover:text-white transition-all active:scale-95"
          >
            <span className="text-xs">⟳</span>
            Refresh Data
          </button>
        </div>
      </div>
      <AuthModal
        open={authOpen}
        onSuccess={() => {
          setAuthOpen(false);
          handleRefresh();
        }}
      />
    </GlassLayout>
  );
}
