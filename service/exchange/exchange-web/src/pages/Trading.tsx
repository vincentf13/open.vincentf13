
import GlassLayout from '../components/layout/GlassLayout';
import Header from '../components/trading/Header';
import Chart from '../components/trading/Chart';
import OrderBook from '../components/trading/OrderBook';
import TradeForm from '../components/trading/TradeForm';
import Positions from '../components/trading/Positions';
import MarketStats from '../components/trading/MarketStats';
import AccountPanel from '../components/trading/AccountPanel';

export default function Trading() {
  return (
    <GlassLayout>
      <div className="relative flex-1 min-h-0">
        <div className="absolute inset-x-0 top-0 z-30">
          <Header />
        </div>

        <div className="flex-1 min-h-0 pt-24 lg:pr-[260px] flex flex-col gap-4">
          <div className="grid grid-cols-1 lg:grid-cols-[2.4fr,0.6fr] min-h-0">
            {/* Left Column: Market Info + Chart */}
            <div className="flex flex-col min-w-0 border-r border-white/20">
              <div className="border-b border-white/20 bg-white/5">
                <MarketStats />
              </div>
              <div className="flex-1 min-h-0 relative z-10">
                <Chart />
              </div>
            </div>

            {/* Middle Column: Order Book */}
            <div className="flex flex-col min-w-0 bg-white/5">
              <OrderBook />
            </div>
          </div>

          <div className="px-2 lg:px-4">
            <Positions />
          </div>
        </div>

        {/* Floating Right Panel: Trade + Account */}
        <div className="hidden lg:flex flex-col absolute top-0 right-0 h-full w-[260px] z-10">
          <div className="flex flex-col h-full border-l border-white/20 bg-white/5 pt-24">
            <div className="border-b border-white/20">
              <TradeForm />
            </div>
            <div className="flex-1 min-h-0 p-4">
              <AccountPanel />
            </div>
          </div>
        </div>

        {/* Stacked Right Panel for Mobile */}
        <div className="lg:hidden border-t border-white/20 bg-white/5">
          <div className="border-b border-white/20">
            <TradeForm />
          </div>
          <div className="p-4">
            <AccountPanel />
          </div>
        </div>
      </div>
    </GlassLayout>
  );
}
