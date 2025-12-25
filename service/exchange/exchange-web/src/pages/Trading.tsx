
import GlassLayout from '../components/layout/GlassLayout';
import Header from '../components/trading/Header';
import Chart from '../components/trading/Chart';
import OrderBook from '../components/trading/OrderBook';
import TradeForm from '../components/trading/TradeForm';
import Positions from '../components/trading/Positions';
import MarketStats from '../components/trading/MarketStats';

export default function Trading() {
  return (
    <GlassLayout>
      <Header />

      <div className="flex-1 flex flex-col lg:flex-row min-h-0 relative z-0">
        {/* Left Column: Chart & Positions */}
        <div className="flex-1 flex flex-col min-w-0 border-r border-white/20">
            {/* Top: Chart */}
            <div className="h-[450px] lg:h-[60%] border-b border-white/20 relative z-10">
                <Chart />
            </div>

            {/* Bottom: Positions */}
            <div className="flex-1 overflow-y-auto p-0 bg-gradient-to-b from-white/5 to-transparent">
               <div className="p-4 h-full">
                  <Positions />
               </div>
            </div>
        </div>

        {/* Right Column: Order Book & Trade Form */}
        <div className="w-full lg:w-[320px] xl:w-[360px] flex flex-col bg-white/5 backdrop-blur-sm z-20 h-full overflow-y-auto lg:overflow-hidden">
            <div className="flex-1 min-h-[400px] flex flex-col">
                <OrderBook />
            </div>
            <div className="flex-shrink-0">
                <MarketStats />
                <TradeForm />
            </div>
        </div>
      </div>
    </GlassLayout>
  );
}
