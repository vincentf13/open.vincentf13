import { useCallback, useEffect, useRef, useState } from 'react';
import { InputNumber, Modal, Radio, message } from 'antd';

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
};

export default function AccountPanel({ refreshTrigger }: AccountPanelProps) {
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
    <div className="flex flex-col gap-4 p-4">
      <div className="flex items-center justify-between">
        <div className="text-sm font-semibold text-slate-700">Account</div>
        <button
          className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-white/30 bg-white/20 hover:bg-white/40 text-slate-600 hover:text-slate-800 transition-all active:scale-95"
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
