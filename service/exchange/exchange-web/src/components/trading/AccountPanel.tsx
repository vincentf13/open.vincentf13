import { useCallback, useEffect, useRef, useState } from 'react';
import { InputNumber, Modal, Radio, message, Tooltip } from 'antd';

import { depositAccount, getAccountBalances, withdrawAccount } from '../../api/account';
import { getCurrentUser } from '../../api/user';

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

type AccountPanelProps = {
  refreshTrigger?: number;
  onOpenBalanceSheet?: () => void;
  onSyncComplete?: (name: string) => void;
};

export default function AccountPanel({ refreshTrigger, onOpenBalanceSheet, onSyncComplete }: AccountPanelProps) {
  const [balances, setBalances] = useState<BalanceState>(defaultBalances);
  const requestRef = useRef<Promise<any> | null>(null);
  const mountedRef = useRef(true);
  const [transferOpen, setTransferOpen] = useState(false);
  const [transferType, setTransferType] = useState<'deposit' | 'withdraw'>('deposit');
  const [transferAmount, setTransferAmount] = useState<number | string | null>(null);
  const [transferLoading, setTransferLoading] = useState(false);
  const userRequestRef = useRef<Promise<any> | null>(null);

  const loadBalances = useCallback(async (force?: boolean) => {
    try {
      if (force) {
        requestRef.current = null;
      }
      if (!requestRef.current) {
        requestRef.current = getAccountBalances('USDT');
      }
      const result = await requestRef.current;
      if (!mountedRef.current) {
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
      if (mountedRef.current) {
        setBalances(defaultBalances);
      }
    } finally {
        if (mountedRef.current) {
            onSyncComplete?.('AccountPanel');
        }
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    loadBalances(true);
    return () => {
      mountedRef.current = false;
    };
  }, [loadBalances, refreshTrigger]);

  const resolveUserId = useCallback(async () => {
    try {
      if (!userRequestRef.current) {
        userRequestRef.current = getCurrentUser();
      }
      const result = await userRequestRef.current;
      if (String(result?.code) !== '0') {
        return null;
      }
      return result?.data?.id ?? null;
    } catch {
      userRequestRef.current = null;
      return null;
    }
  }, []);

  const createTxId = () => {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return `tx-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  };

  const handleTransferConfirm = async () => {
    if (!transferAmount || Number(transferAmount) <= 0) {
      message.warning('請輸入金額');
      return;
    }
    setTransferLoading(true);
    try {
      const userId = await resolveUserId();
      if (!userId) {
        message.error('無法取得使用者資訊');
        return;
      }
      const payload = {
        userId,
        asset: 'USDT',
        amount: String(transferAmount),
        txId: createTxId(),
        creditedAt: new Date().toISOString(),
      };
      const result = transferType === 'deposit'
        ? await depositAccount(payload)
        : await withdrawAccount(payload);
      if (String(result?.code) === '0') {
        message.success(transferType === 'deposit' ? '入金成功' : '出金成功');
        setTransferOpen(false);
        setTransferAmount(null);
        await loadBalances(true);
      } else {
        message.error(result?.message || '操作失敗');
      }
    } finally {
      setTransferLoading(false);
    }
  };

  return (
    <div className="relative flex flex-col gap-4 p-4 rounded-2xl border border-white/25 bg-white/15 shadow-md overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-br from-white/20 via-transparent to-transparent pointer-events-none" />
      <div className="flex items-center justify-between">
        <div className="text-sm font-semibold text-slate-700">Account</div>
        <button
          className="text-xs font-bold px-3 py-1.5 rounded-lg bg-white border border-slate-300 text-slate-700 shadow-sm hover:bg-slate-50 hover:border-slate-400 transition-all active:scale-95 uppercase tracking-wider"
          onClick={() => setTransferOpen(true)}
        >
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
        <div className="flex justify-start">
          <button
            type="button"
            className="w-full text-xs font-black px-3 py-2.5 rounded-xl bg-gradient-to-b from-slate-700 to-slate-800 text-white border-t border-slate-600 shadow-[0_4px_12px_rgba(0,0,0,0.15)] hover:from-slate-600 hover:to-slate-700 hover:shadow-[0_6px_16px_rgba(0,0,0,0.2)] transition-all active:translate-y-0.5 active:shadow-inner uppercase tracking-widest"
            onClick={() => onOpenBalanceSheet?.()}
          >
            Balance Sheet
          </button>
        </div>
      </div>

      <Modal
        title="Transfer"
        open={transferOpen}
        onOk={handleTransferConfirm}
        onCancel={() => setTransferOpen(false)}
        okText="Confirm"
        cancelText="Cancel"
        confirmLoading={transferLoading}
        centered
      >
        <div className="space-y-4">
          <Radio.Group
            value={transferType}
            onChange={(event) => setTransferType(event.target.value)}
            optionType="button"
            buttonStyle="solid"
          >
            <Radio.Button value="deposit">Deposit</Radio.Button>
            <Radio.Button value="withdraw">Withdraw</Radio.Button>
          </Radio.Group>
          <div className="flex items-center gap-3">
            <InputNumber
              min={0}
              value={transferAmount}
              onChange={setTransferAmount}
              className="w-full"
              placeholder="輸入金額"
            />
            <span className="text-xs font-semibold text-slate-500">USDT</span>
          </div>
        </div>
      </Modal>
    </div>
  );
}
