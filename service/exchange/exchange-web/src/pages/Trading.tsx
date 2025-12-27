
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

export default function Trading() {
  const [authOpen, setAuthOpen] = useState(() => !hasToken());
  const [instruments, setInstruments] = useState<InstrumentSummary[]>(() => getCachedInstrumentSummaries());
  const [selectedInstrumentId, setSelectedInstrumentId] = useState<string | null>(() => {
    const cachedId = getCachedInstrumentId();
    const cachedList = getCachedInstrumentSummaries();
    return cachedId || cachedList[0]?.instrumentId || null;
  });
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
      }
      catch (error) {
        return;
      }
    };

    loadInstruments();

    return () => {
      cancelled = true;
    };
  }, []);

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
                  onSelectInstrument={(instrument) => {
                    setSelectedInstrumentId(instrument.instrumentId);
                    setCachedInstrumentId(instrument.instrumentId);
                  }}
                />
              </div>
              <div className="flex-1 min-h-0 relative z-10">
                <Chart instrumentId={selectedInstrumentId} />
              </div>
            </div>

            {/* Middle Column: Order Book */}
            <div className="flex flex-col min-w-0 bg-white/5">
              <OrderBook />
            </div>
          </div>

          <div className="px-2 lg:px-4">
            <Positions instruments={instruments} selectedInstrumentId={selectedInstrumentId} />
          </div>
        </div>

        {/* Right Panel: Trade + Account */}
        <div className="flex flex-col w-full border-t border-white/20 bg-white/5 lg:absolute lg:top-0 lg:right-0 lg:h-full lg:w-[260px] lg:z-10 lg:border-t-0 lg:border-l lg:border-white/20">
          <div className="flex flex-col h-full lg:pt-24">
            <div className="border-b border-white/20">
              <TradeForm />
            </div>
            <div className="flex-1 min-h-0 p-4">
              <AccountPanel />
            </div>
          </div>
        </div>
      </div>
      <AuthModal open={authOpen} onSuccess={() => setAuthOpen(false)} />
    </GlassLayout>
  );
}
