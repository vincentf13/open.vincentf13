import { useEffect, useRef, useState } from 'react';

import { getAccountBalances } from '../../api/account';

type BalanceState = {
  balance: string;
  available: string;
  reserved: string;
  asset: string;
};

const defaultBalances: BalanceState = {
  balance: '--',
  available: '--',
  reserved: '--',
  asset: '--',
};

const formatAmount = (value: unknown) => {
  if (value === null || value === undefined) {
    return '--';
  }
  return String(value);
};

const formatAmountWithAsset = (value: string, asset: string) => {
  if (value === '--') {
    return '--';
  }
  if (!asset || asset === '--') {
    return value;
  }
  return `${value} ${asset}`;
};

export default function AccountPanel() {
  const [balances, setBalances] = useState<BalanceState>(defaultBalances);
  const requestRef = useRef<Promise<any> | null>(null);

  useEffect(() => {
    let isActive = true;
    const loadBalances = async () => {
      try {
        if (!requestRef.current) {
          requestRef.current = getAccountBalances('USDT');
        }
        const result = await requestRef.current;
        if (!isActive) {
          return;
        }
        if (String(result?.code) === '0' && result?.data?.balances) {
          const payload = result.data.balances;
          setBalances({
            balance: formatAmount(payload.balance),
            available: formatAmount(payload.available),
            reserved: formatAmount(payload.reserved),
            asset: formatAmount(payload.asset),
          });
        }
      } catch {
        if (isActive) {
          setBalances(defaultBalances);
        }
      }
    };
    loadBalances();
    return () => {
      isActive = false;
    };
  }, []);

  return (
    <div className="flex flex-col gap-4 p-4">
      <div className="flex items-center justify-between">
        <div className="text-sm font-semibold text-slate-700">Account</div>
        <button className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-white/30 bg-white/20 hover:bg-white/40 text-slate-600 hover:text-slate-800 transition-all active:scale-95">
          Transfer
        </button>
      </div>

      <div className="space-y-3 text-xs">
        <div className="flex items-center justify-between gap-2 whitespace-nowrap">
          <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider truncate">Balance</span>
          <span className="text-sm font-mono font-medium text-slate-700 truncate">
            {formatAmountWithAsset(balances.balance, balances.asset)}
          </span>
        </div>
        <div className="flex items-center justify-between gap-2 whitespace-nowrap">
          <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider truncate">Available</span>
          <span className="text-sm font-mono font-medium text-slate-700 truncate">
            {formatAmountWithAsset(balances.available, balances.asset)}
          </span>
        </div>
        <div className="flex items-center justify-between gap-2 whitespace-nowrap">
          <span className="text-[10px] uppercase text-slate-400 font-bold tracking-wider truncate">Reserved</span>
          <span className="text-sm font-mono font-medium text-slate-700 truncate">
            {formatAmountWithAsset(balances.reserved, balances.asset)}
          </span>
        </div>
      </div>
    </div>
  );
}
