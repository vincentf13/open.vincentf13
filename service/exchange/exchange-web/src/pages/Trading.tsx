import { useEffect, useMemo, useRef, useState, type MouseEvent } from 'react';

import { message, Tooltip } from 'antd';

import {
  AUTH_REQUIRED_EVENT,
  REFRESH_AFTER_LOGIN_KEY,
  hasToken,
} from '../api/client';
import {
  getBalanceSheet,
  getPlatformAccounts,
  getJournalsByReference,
  getAccountJournals,
  getPlatformAccountJournals,
  type AccountBalanceItem,
  type AccountBalanceResponse,
  type PlatformAccountItem,
  type PlatformAccountResponse,
  type AccountReferenceJournalResponse,
  type AccountJournalItem,
  type PlatformJournalItem,
  type AccountJournalResponse,
  type PlatformAccountJournalResponse,
} from '../api/account';
import {
  fetchInstrumentSummaries,
  getCachedInstrumentSummaries,
  setCachedInstrumentId,
  getCachedInstrumentId,
  type InstrumentSummary,
} from '../api/instrument';
import { resetSystemData } from '../api/admin';
import { getCurrentUser } from '../api/user';
import { getTradesByInstrument, type TradeResponse } from '../api/trade';

import AuthModal from '../components/auth/AuthModal';
import AccountPanel from '../components/trading/AccountPanel';
import MarketStats from '../components/trading/MarketStats';
import OrderBook from '../components/trading/OrderBook';
import TradeForm from '../components/trading/TradeForm';
import Positions from '../components/trading/Positions';
import Chart from '../components/trading/Chart';

export default function Trading() {
  const [authOpen, setAuthOpen] = useState(() => !hasToken());
  const [resetting, setResetting] = useState(false);
  const [isPaused, setIsPaused] = useState(true);
  const [isTabVisible, setIsTabVisible] = useState(true);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [instruments, setInstruments] = useState<InstrumentSummary[]>(() => getCachedInstrumentSummaries());
  const [selectedInstrumentId, setSelectedInstrumentId] = useState<string | null>(() => getCachedInstrumentId());

  const [balanceSheetOpen, setBalanceSheetOpen] = useState(false);
  const [balanceSheetLoading, setBalanceSheetLoading] = useState(false);
  const [balanceSheetData, setBalanceSheetData] = useState<AccountBalanceResponse | null>(null);
  const [balanceSheetRange, setBalanceSheetRange] = useState<{ earliest: number; latest: number } | null>(null);
  const [balanceSheetTimestamp, setBalanceSheetTimestamp] = useState<number | null>(null);
  const [balanceSheetSelectionOpen, setBalanceSheetSelectionOpen] = useState(false);
  const [balanceSheetPendingTimestamp, setBalanceSheetPendingTimestamp] = useState<number | null>(null);
  const [platformAccountsData, setPlatformAccountsData] = useState<PlatformAccountResponse | null>(null);
  const balanceSheetAnchorRef = useRef<HTMLDivElement | null>(null);

  const [accountJournalOpen, setAccountJournalOpen] = useState(false);
  const [accountJournalLoading, setAccountJournalLoading] = useState(false);
  const [accountJournalData, setAccountJournalData] = useState<AccountJournalResponse | null>(null);
  const accountJournalRef = useRef<HTMLDivElement | null>(null);

  const [platformAccountJournalOpen, setPlatformAccountJournalOpen] = useState(false);
  const [platformAccountJournalLoading, setPlatformAccountJournalLoading] = useState(false);
  const [platformAccountJournalError, setPlatformAccountJournalError] = useState<string | null>(null);
  const [platformAccountJournalData, setPlatformAccountJournalResponse] = useState<PlatformAccountJournalResponse | null>(null);
  const platformAccountJournalRef = useRef<HTMLDivElement | null>(null);

  const [referenceJournalOpen, setReferenceJournalOpen] = useState(false);
  const [referenceJournalLoading, setReferenceJournalLoading] = useState(false);
  const [referenceJournalError, setReferenceJournalError] = useState<string | null>(null);
  const [referenceJournalData, setReferenceJournalData] = useState<AccountReferenceJournalResponse | null>(null);
  const referenceJournalRef = useRef<HTMLDivElement | null>(null);
  const [journalTradeDetail, setJournalTradeDetail] = useState<TradeResponse | null>(null);
  const [journalTradeDetailLoading, setJournalTradeDetailLoading] = useState(false);
  const [journalTradeAnchor, setJournalTradeAnchor] = useState<{ top: number; left: number } | null>(null);

  useEffect(() => {
    const h = () => setIsTabVisible(document.visibilityState === 'visible');
    document.addEventListener('visibilitychange', h);
    const interval = setInterval(() => { if (!isPaused && document.visibilityState === 'visible') setRefreshTrigger(v => v + 1); }, 3000);
    return () => { document.removeEventListener('visibilitychange', h); clearInterval(interval); };
  }, [isPaused]);

  useEffect(() => {
    let triggerAtClick: HTMLElement | null = null;
    const hmd = (e: MouseEvent) => { document.body.classList.add('hide-tooltips'); triggerAtClick = (e.target as HTMLElement).closest('.liquid-tooltip-trigger'); };
    const hmm = (e: MouseEvent) => {
      if (!document.body.classList.contains('hide-tooltips')) return;
      if ((e.target as HTMLElement).closest('.liquid-tooltip-trigger') !== triggerAtClick) document.body.classList.remove('hide-tooltips');
    };
    window.addEventListener('mousedown', hmd, true);
    window.addEventListener('mousemove', hmm, true);
    return () => { window.removeEventListener('mousedown', hmd, true); window.removeEventListener('mousemove', hmm, true); };
  }, []);

  useEffect(() => {
    fetchInstrumentSummaries().then(list => {
      setInstruments(list);
      setSelectedInstrumentId(curr => list.find(i => i.instrumentId === curr)?.instrumentId || list[0]?.instrumentId || null);
    });
  }, [refreshTrigger]);

  const loadBalanceSheet = (snapshotMs: number | null) => {
    if (!balanceSheetOpen) {
      return;
    }
    const snapshotAt = snapshotMs != null ? new Date(snapshotMs).toISOString() : undefined;
    setBalanceSheetLoading(true);
    getBalanceSheet(snapshotAt)
      .then(res => {
        if (String(res?.code) === '0') {
          const data = res.data;
          setBalanceSheetData(data);
          if (data?.earliestSnapshotAt && data?.latestSnapshotAt) {
            setBalanceSheetRange({
              earliest: new Date(data.earliestSnapshotAt).getTime(),
              latest: new Date(data.latestSnapshotAt).getTime(),
            });
          }
        }
      })
      .finally(() => setBalanceSheetLoading(false));
  };

  const loadPlatformAccounts = (snapshotMs: number | null) => {
    const snapshotAt = snapshotMs != null ? new Date(snapshotMs).toISOString() : undefined;
    getPlatformAccounts(snapshotAt).then(res => { if (String(res?.code)==='0') setPlatformAccountsData(res.data); });
  };

  useEffect(() => {
    if (!balanceSheetOpen) {
      return;
    }
    loadBalanceSheet(balanceSheetTimestamp ?? null);
    loadPlatformAccounts(balanceSheetTimestamp ?? null);
  }, [balanceSheetOpen, refreshTrigger, balanceSheetTimestamp]);

  useEffect(() => {
    if (accountJournalOpen && accountJournalData?.accountId) {
      const snapshotAt = balanceSheetTimestamp != null ? new Date(balanceSheetTimestamp).toISOString() : undefined;
      getAccountJournals(accountJournalData.accountId, snapshotAt).then(res => { if(String(res?.code)==='0') setAccountJournalData(res.data); });
    }
  }, [refreshTrigger, balanceSheetTimestamp]);

  useEffect(() => {
    if (platformAccountJournalOpen && platformAccountJournalData?.accountId) {
      const snapshotAt = balanceSheetTimestamp != null ? new Date(balanceSheetTimestamp).toISOString() : undefined;
      getPlatformAccountJournals(platformAccountJournalData.accountId, snapshotAt).then(res => { if(String(res?.code)==='0') setPlatformAccountJournalResponse(res.data); });
    }
  }, [refreshTrigger, balanceSheetTimestamp]);

  useEffect(() => {
    if (referenceJournalOpen && referenceJournalData?.referenceType && referenceJournalData?.referenceId) {
      getJournalsByReference(referenceJournalData.referenceType, referenceJournalData.referenceId).then(res => { if(String(res?.code)==='0') setReferenceJournalData(res.data); });
    }
  }, [refreshTrigger]);

  const handleRefresh = () => setRefreshTrigger(v => v + 1);
  const selectedInstrument = instruments.find(i => i.instrumentId === selectedInstrumentId) || instruments[0] || null;

  const handleOpenBalanceSheet = () => {
    setBalanceSheetOpen(true);
    setBalanceSheetTimestamp(null);
    setBalanceSheetRange(null);
    setBalanceSheetSelectionOpen(false);
    setBalanceSheetPendingTimestamp(null);
  };

  const handleCloseBalanceSheet = () => {
    setBalanceSheetOpen(false);
    setAccountJournalOpen(false);
    setPlatformAccountJournalOpen(false);
    setReferenceJournalOpen(false);
    setBalanceSheetRange(null);
    setBalanceSheetTimestamp(null);
    setBalanceSheetSelectionOpen(false);
    setBalanceSheetPendingTimestamp(null);
  };
  const handleCloseAccountJournal = () => setAccountJournalOpen(false);
  const handleClosePlatformAccountJournal = () => setPlatformAccountJournalOpen(false);
  const handleCloseReferenceJournals = () => setReferenceJournalOpen(false);

  const handleOpenAccountJournal = (accountId: number) => {
    setPlatformAccountJournalOpen(false);
    setReferenceJournalOpen(false);
    setAccountJournalOpen(true);
    setAccountJournalLoading(true);
    const snapshotAt = balanceSheetTimestamp != null ? new Date(balanceSheetTimestamp).toISOString() : undefined;
    getAccountJournals(accountId, snapshotAt).then(res => { if(String(res?.code)==='0') setAccountJournalData(res.data); }).finally(() => setAccountJournalLoading(false));
  };

  const handleOpenPlatformAccountJournal = (accountId: number) => {
    setAccountJournalOpen(false);
    setReferenceJournalOpen(false);
    setPlatformAccountJournalOpen(true);
    setPlatformAccountJournalLoading(true);
    const snapshotAt = balanceSheetTimestamp != null ? new Date(balanceSheetTimestamp).toISOString() : undefined;
    getPlatformAccountJournals(accountId, snapshotAt).then(res => { if(String(res?.code)==='0') setPlatformAccountJournalResponse(res.data); }).finally(() => setPlatformAccountJournalLoading(false));
  };

  const handleOpenReferenceJournals = (type: string, id: string) => {
    setReferenceJournalOpen(true);
    setReferenceJournalLoading(true);
    getJournalsByReference(type, id).then(res => { if(String(res?.code)==='0') setReferenceJournalData(res.data); }).finally(() => setReferenceJournalLoading(false));
  };

  const clampSnapshotValue = (value: number, range: { earliest: number; latest: number }) => {
    if (value < range.earliest) return range.earliest;
    if (value > range.latest) return range.latest;
    return value;
  };

  const resolveSnapshotTimestamp = (value: number, range: { earliest: number; latest: number }) => {
    return value >= range.latest ? null : value;
  };

  const handleBalanceSheetSliderChange = (value: number) => {
    if (!balanceSheetRange) {
      return;
    }
    setBalanceSheetPendingTimestamp(clampSnapshotValue(value, balanceSheetRange));
  };

  const formatLocalDatetime = (value: number) => {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  };

  const handleApplyBalanceSheetSnapshot = () => {
    if (!balanceSheetRange || balanceSheetPendingTimestamp == null) {
      return;
    }
    const normalized = resolveSnapshotTimestamp(balanceSheetPendingTimestamp, balanceSheetRange);
    setBalanceSheetTimestamp(normalized);
    setBalanceSheetSelectionOpen(false);
  };

  const handleSelectLatestBalanceSheet = () => {
    setBalanceSheetSelectionOpen(false);
    setBalanceSheetPendingTimestamp(null);
    setBalanceSheetTimestamp(null);
  };

  const handleOpenHistoricalBalanceSheet = () => {
    if (!balanceSheetRange) {
      return;
    }
    setBalanceSheetSelectionOpen(true);
    const defaultValue = balanceSheetTimestamp ?? balanceSheetRange.latest;
    setBalanceSheetPendingTimestamp(clampSnapshotValue(defaultValue, balanceSheetRange));
  };

  const splitExpenseRevenue = <T extends { category?: string | null }>(items?: T[]) => {
    const rows = items ?? [];
    const expenseRevenue = rows.filter(item => {
      const category = String(item?.category ?? '').toUpperCase();
      return category === 'EXPENSE' || category === 'REVENUE';
    });
    const core = rows.filter(item => {
      const category = String(item?.category ?? '').toUpperCase();
      return category !== 'EXPENSE' && category !== 'REVENUE';
    });
    return { core, expenseRevenue };
  };

  const tradeRefTypes = new Set(['TRADE_MARGIN_SETTLED', 'TRADE_FEE', 'TRADE_FEE_REFUND', 'POSITION_MARGIN_RELEASED', 'POSITION_REALIZED_PNL']);

  const openJournalTradeDetail = async (rawReferenceId: string, event: MouseEvent<HTMLButtonElement>) => {
    const refId = String(rawReferenceId || '').split(':')[0];
    if (!refId) {
      message.warning('Missing trade id');
      return;
    }
    if (!selectedInstrumentId) {
      message.warning('Please select an instrument');
      return;
    }
    const rect = event.currentTarget.getBoundingClientRect();
    setJournalTradeAnchor({ top: rect.top, left: rect.right + 8 });
    setJournalTradeDetailLoading(true);
    try {
      const res = await getTradesByInstrument(selectedInstrumentId);
      if (String(res?.code) === '0') {
        const trades = res.data || [];
        const match = trades.find((t: TradeResponse) => String(t.tradeId) === refId) || null;
        if (!match) {
          message.warning('Trade not found');
        }
        setJournalTradeDetail(match);
      }
    } catch {
      message.error('Failed to load trade');
    } finally {
      setJournalTradeDetailLoading(false);
    }
  };

  const renderCategoryChip = (value?: string | null) => {
    const text = String(value ?? '-');
    return (
      <span className="inline-flex items-center px-2 py-0.5 rounded-md border border-slate-200 bg-slate-50 text-[10px] font-semibold uppercase tracking-widest text-slate-500">
        {text}
      </span>
    );
  };

  const renderJournalRows = (items?: AccountJournalItem[], options?: { disableReferenceLink?: boolean; highlightMode?: 'background' | 'text'; highlightExpenses?: boolean }) => {
    const rows = items && items.length > 0 ? items : [null];
    const disableReferenceLink = options?.disableReferenceLink ?? false;
    const highlightMode = options?.highlightMode ?? 'background';
    const textOnlyHighlight = highlightMode === 'text';
    const highlightExpenses = options?.highlightExpenses ?? false;
    return (
      <div className="overflow-x-auto">
        <table className="w-full text-xs text-slate-600 border-separate border-spacing-x-0 table-fixed">
          <thead>
            <tr className="text-[10px] uppercase text-slate-400 border-b border-white/60 text-right whitespace-nowrap">
              <th className="py-1 px-1 font-semibold text-left w-[100px]">journalId</th>
              <th className="py-1 px-1 font-semibold text-left w-[70px]">accountId</th>
              <th className="py-1 px-1 font-semibold text-left w-[80px]">accCode</th>
              <th className="py-1 px-1 font-semibold text-left w-[80px]">accName</th>
              <th className="py-1 px-1 font-semibold text-left w-[60px]">category</th>
              <th className="py-1 px-1 font-semibold text-left w-[40px]">asset</th>
              <th className="py-1 px-1 font-semibold w-[80px]">amount</th>
              <th className="py-1 px-1 font-semibold w-[60px]">
                <div className="flex items-center gap-1 justify-end">
                  direction
                  <Tooltip 
                    title={(
                      <div className="text-[10px] leading-relaxed min-w-[480px]">
                        <div className="grid grid-cols-2 gap-6">
                          {/* Chinese Column */}
                          <div className="space-y-2 text-slate-700">
                            <p className="font-bold text-sky-600 border-b border-slate-200 pb-1">借貸方向 (Debit / Credit)</p>
                            <p className="font-mono text-sky-600">借 (DEBIT) = 貸 (CREDIT)</p>
                            <ul className="list-disc pl-4 space-y-1">
                              <li><span className="font-semibold text-emerald-600">借 (DEBIT)</span>: 代表資產/費用增加，或負債/權益/收入減少。</li>
                              <li><span className="font-semibold text-rose-600">貸 (CREDIT)</span>: 代表負債/權益/收入增加，或資產/費用減少。</li>
                            </ul>
                          </div>
                          {/* English Column */}
                          <div className="space-y-2 text-slate-700">
                            <p className="font-bold text-sky-600 border-b border-slate-200 pb-1">Direction Accounting Rules</p>
                            <p className="font-mono text-sky-600">Total Debits = Total Credits</p>
                            <ul className="list-disc pl-4 space-y-1">
                              <li><span className="font-semibold text-emerald-600">DEBIT</span>: Increases in Assets/Expenses; decreases in Liabilities/Equity/Revenue.</li>
                              <li><span className="font-semibold text-rose-600">CREDIT</span>: Increases in Liabilities/Equity/Revenue; decreases in Assets/Expenses.</li>
                            </ul>
                          </div>
                        </div>
                      </div>
                    )}
                    classNames={{ root: 'liquid-tooltip' }}
                    styles={{ root: { maxWidth: 'none' } }}
                  >
                    <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-slate-100 flex items-center justify-center text-[9px] text-slate-400 cursor-help border border-slate-200">?</div>
                  </Tooltip>
                </div>
              </th>
              <th className="py-1 px-1 font-semibold w-[85px]">balanceAfter</th>
              <th className="py-1 px-1 font-semibold text-left w-[110px]">referenceType</th>
              <th className="py-1 px-1 font-semibold text-left w-[120px]">
                <div className="flex items-center gap-1">
                  referenceId
                  <Tooltip 
                    title={(
                      <div className="text-[10px] leading-relaxed min-w-[320px]">
                        <div className="grid grid-cols-2 gap-6">
                          <div className="space-y-2 text-slate-700">
                            <p className="font-bold text-sky-500 border-b border-slate-200 pb-1">關聯查詢</p>
                            <p>點擊 ID 可查詢該筆業務操作（如撮合、手續費）產生的所有關聯帳戶分錄。</p>
                          </div>
                          <div className="space-y-2 text-slate-700">
                            <p className="font-bold text-sky-500 border-b border-slate-200 pb-1">Reference Query</p>
                            <p>Click the ID to view all journal entries (e.g., matching, fees) related to this transaction.</p>
                          </div>
                        </div>
                      </div>
                    )}
                    classNames={{ root: 'liquid-tooltip' }}
                    styles={{ root: { maxWidth: 'none' } }}
                  >
                    <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-slate-100 flex items-center justify-center text-[9px] text-slate-400 cursor-help border border-slate-200">?</div>
                  </Tooltip>
                </div>
              </th>
              <th className="py-1 px-1 font-semibold w-[30px]">seq</th>
              <th className="py-1 px-1 font-semibold text-left w-[150px]">description</th>
              <th className="py-1 px-1 font-semibold w-[100px]">eventTime</th>
              <th className="py-1 px-1 font-semibold w-[100px]">createdAt</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/40">
            {rows.map((item, index) => {
              const category = String(item?.category ?? '').toUpperCase();
              const direction = String(item?.direction ?? '').toUpperCase();
              const refType = String(item?.referenceType ?? '').toUpperCase();
              const isFrozen = refType === 'ORDER_FEE_FROZEN' || refType === 'ORDER_MARGIN_FROZEN' || refType === 'TRADE_FEE_REFUND';
              const highlightCredit = (category === 'ASSET' || category === 'EXPENSE') && direction === 'CREDIT';
              const highlightDebit = (category === 'LIABILITY' || category === 'EQUITY' || category === 'REVENUE') && direction === 'DEBIT';
              const highlightBackground = !isFrozen && highlightDebit && !textOnlyHighlight;
              const highlightText = !isFrozen && (highlightCredit || (textOnlyHighlight && highlightDebit));
      const baseHighlight = highlightBackground ? 'bg-red-100' : '';
      const rowClass = isFrozen ? 'text-slate-300 font-normal' : 'text-slate-600';
      const expenseHighlight = highlightExpenses && category === 'EXPENSE';
              
              return (
                <tr key={index} className={`text-right align-top whitespace-nowrap ${rowClass}`}>
                  <td className="py-1 px-1 text-left overflow-hidden">{String(item?.journalId ?? '-')}</td>
                  <td className="py-1 px-1 text-left overflow-hidden">{String(item?.accountId ?? '-')}</td>
                  <td className="py-1 px-1 text-left overflow-hidden">{String(item?.accountCode ?? '-')}</td>
                  <td className="py-1 px-1 text-left overflow-hidden">{String(item?.accountName ?? '-')}</td>
                  <td className={`py-1 px-1 text-left overflow-hidden ${baseHighlight}`}>{renderCategoryChip(item?.category)}</td>
                  <td className="py-1 px-1 text-left overflow-hidden">{String(item?.asset ?? '-')}</td>
        <td className={`py-1 px-1 overflow-hidden ${baseHighlight} ${highlightBackground ? 'rounded-l' : ''}`}>{renderNumberCell(item?.amount, highlightBackground, expenseHighlight ? 'text-rose-600' : (highlightText ? 'text-rose-600' : undefined), isFrozen)}</td>
                  <td className={`py-1 px-1 overflow-hidden ${baseHighlight}`}>
                    <span className={`px-1.5 py-0.5 rounded-md border text-[9px] font-black uppercase tracking-tighter ${isFrozen ? 'border-slate-200 text-slate-400' : 'bg-slate-100 border-slate-200 text-slate-600'}`}>
                      {direction}
                    </span>
                  </td>
        <td className={`py-1 px-1 overflow-hidden ${baseHighlight} ${highlightBackground ? 'rounded-r' : ''}`}>{renderNumberCell(item?.balanceAfter, highlightBackground, expenseHighlight ? 'text-rose-600' : (highlightText ? 'text-rose-600' : undefined), isFrozen)}</td>
                  <td className="py-1 px-1 text-left overflow-hidden">
                    <span className={`px-1.5 py-0.5 rounded-md border text-[9px] font-black uppercase tracking-tighter ${isFrozen ? 'border-slate-200 text-slate-300' : 'bg-slate-100 border-slate-200 text-slate-600'}`}>
                      {String(item?.referenceType ?? '-')}
                    </span>
                  </td>
                  <td className="py-1 pr-1 text-left overflow-hidden">
                    <div className="flex items-center gap-1">
                      {isFrozen && item?.referenceId && (
                        <Tooltip
                          title={(
                            <div className="text-[10px] leading-relaxed min-w-[320px]">
                              <div className="grid grid-cols-2 gap-6">
                                <div className="space-y-2 text-slate-700">
                                  <p className="font-bold text-sky-500 border-b border-slate-200 pb-1">凍結與解凍說明</p>
                                  <p>資金凍結與解凍操作，不影響帳戶餘額。</p>
                                </div>
                                <div className="space-y-2 text-slate-700">
                                  <p className="font-bold text-sky-500 border-b border-slate-200 pb-1">Freeze/Unfreeze Note</p>
                                  <p>Freeze/unfreeze operations do not change account balances.</p>
                                </div>
                              </div>
                            </div>
                          )}
                          classNames={{ root: 'liquid-tooltip' }}
                          styles={{ root: { maxWidth: 'none' } }}
                        >
                          <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-slate-100 flex items-center justify-center text-[9px] text-slate-400 cursor-help border border-slate-200">?</div>
                        </Tooltip>
                      )}
                      {item?.referenceId && tradeRefTypes.has(refType) && (
                        <button
                          className="text-slate-400 hover:text-slate-600"
                          onClick={(event) => openJournalTradeDetail(String(item.referenceId), event)}
                          aria-label="Open trade detail"
                        >
                          <svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4">
                            <circle cx="7" cy="7" r="4.5" />
                            <path d="M10.5 10.5L14 14" strokeLinecap="round" />
                          </svg>
                        </button>
                      )}
                      {item?.referenceId ? (
                        disableReferenceLink ? <span>{String(item.referenceId)}</span> : (
                          <button onClick={() => handleOpenReferenceJournals(item.referenceType, item.referenceId)} className="text-sky-500 hover:text-sky-600 underline">
                            {String(item.referenceId)}
                          </button>
                        )
                      ) : '-'}
                    </div>
                  </td>
                  <td className="py-1 pr-1 font-mono overflow-hidden">{String(item?.seq ?? '-')}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden bg-yellow-200/50">{String(item?.description ?? '-')}</td>
                  <td className="py-1 pr-1 font-mono text-[9px] overflow-hidden">{String(item?.eventTime ?? '-')}</td>
                  <td className="py-1 pr-1 font-mono text-[9px] overflow-hidden">{String(item?.createdAt ?? '-')}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    );
  };

  const renderPlatformJournalRows = (items?: PlatformJournalItem[], options?: { disableReferenceLink?: boolean; highlightMode?: 'background' | 'text'; highlightExpenses?: boolean }) => {
    const rows = items && items.length > 0 ? items : [null];
    const disableReferenceLink = options?.disableReferenceLink ?? false;
    const highlightMode = options?.highlightMode ?? 'background';
    const textOnlyHighlight = highlightMode === 'text';
    const highlightExpenses = options?.highlightExpenses ?? false;
    return (
      <div className="overflow-x-auto">
        <table className="w-full text-xs text-slate-600 border-collapse table-fixed">
          <thead>
            <tr className="text-[10px] uppercase text-slate-400 border-b border-white/60 text-right whitespace-nowrap">
              <th className="py-1 pr-1 font-semibold text-left w-[100px]">journalId</th>
              <th className="py-1 pr-1 font-semibold text-left w-[70px]">accountId</th>
              <th className="py-1 pr-1 font-semibold text-left w-[80px]">accCode</th>
              <th className="py-1 pr-1 font-semibold text-left w-[80px]">accName</th>
              <th className="py-1 pr-1 font-semibold text-left w-[60px]">category</th>
              <th className="py-1 pr-1 font-semibold text-left w-[40px]">asset</th>
              <th className="py-1 pr-1 font-semibold w-[100px]">amount</th>
              <th className="py-1 px-1 font-semibold w-[60px]">
                <div className="flex items-center gap-1 justify-end">
                  direction
                  <Tooltip 
                    title={(
                      <div className="text-[10px] leading-relaxed min-w-[480px]">
                        <div className="grid grid-cols-2 gap-6">
                          {/* Chinese Column */}
                          <div className="space-y-2 text-slate-700">
                            <p className="font-bold text-indigo-600 border-b border-slate-200 pb-1">借貸方向 (Debit / Credit)</p>
                            <p className="font-mono text-indigo-600">借 (DEBIT) = 貸 (CREDIT)</p>
                            <ul className="list-disc pl-4 space-y-1">
                              <li><span className="font-semibold text-emerald-600">借 (DEBIT)</span>: 代表資產/費用增加，或負債/權益/收入減少。</li>
                              <li><span className="font-semibold text-rose-600">貸 (CREDIT)</span>: 代表負債/權益/收入增加，或資產/費用減少。</li>
                            </ul>
                          </div>
                          {/* English Column */}
                          <div className="space-y-2 text-slate-700">
                            <p className="font-bold text-indigo-600 border-b border-slate-200 pb-1">Direction Accounting Rules</p>
                            <p className="font-mono text-indigo-600">Total Debits = Total Credits</p>
                            <ul className="list-disc pl-4 space-y-1">
                              <li><span className="font-semibold text-emerald-600">DEBIT</span>: Increases in Assets/Expenses; decreases in Liabilities/Equity/Revenue.</li>
                              <li><span className="font-semibold text-rose-600">CREDIT</span>: Increases in Liabilities/Equity/Revenue; decreases in Assets/Expenses.</li>
                            </ul>
                          </div>
                        </div>
                      </div>
                    )}
                    classNames={{ root: 'liquid-tooltip' }}
                    styles={{ root: { maxWidth: 'none' } }}
                  >
                    <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-slate-100 flex items-center justify-center text-[9px] text-slate-400 cursor-help border border-slate-200">?</div>
                  </Tooltip>
                </div>
              </th>
              <th className="py-1 pr-1 font-semibold w-[85px]">balanceAfter</th>
              <th className="py-1 pr-1 font-semibold text-left w-[110px]">referenceType</th>
              <th className="py-1 pr-1 font-semibold text-left w-[120px]">
                <div className="flex items-center gap-1">
                  referenceId
                  <Tooltip 
                    title={(
                      <div className="text-[10px] leading-relaxed min-w-[320px]">
                        <div className="grid grid-cols-2 gap-6">
                          <div className="space-y-2 text-slate-700">
                            <p className="font-bold text-indigo-500 border-b border-slate-200 pb-1">關聯查詢</p>
                            <p>點擊 ID 可查詢該筆業務操作產生的所有關聯帳戶分錄。</p>
                          </div>
                          <div className="space-y-2 text-slate-700">
                            <p className="font-bold text-indigo-500 border-b border-slate-200 pb-1">Reference Query</p>
                            <p>Click the ID to view all journal entries related to this transaction.</p>
                          </div>
                        </div>
                      </div>
                    )}
                    classNames={{ root: 'liquid-tooltip' }}
                    styles={{ root: { maxWidth: 'none' } }}
                  >
                    <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-slate-100 flex items-center justify-center text-[9px] text-slate-400 cursor-help border border-slate-200">?</div>
                  </Tooltip>
                </div>
              </th>
              <th className="py-1 pr-1 font-semibold w-[30px]">seq</th>
              <th className="py-1 pr-1 font-semibold text-left w-[150px]">description</th>
              <th className="py-1 pr-1 font-semibold w-[100px]">eventTime</th>
              <th className="py-1 pr-1 font-semibold w-[100px]">createdAt</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/40">
            {rows.map((item, index) => {
              const category = String(item?.category ?? '').toUpperCase();
              const direction = String(item?.direction ?? '').toUpperCase();
              const refType = String(item?.referenceType ?? '').toUpperCase();
              const isFrozen = refType === 'ORDER_FEE_FROZEN' || refType === 'ORDER_MARGIN_FROZEN' || refType === 'TRADE_FEE_REFUND';
              const highlightCredit = (category === 'ASSET' || category === 'EXPENSE') && direction === 'CREDIT';
              const highlightDebit = (category === 'LIABILITY' || category === 'EQUITY' || category === 'REVENUE') && direction === 'DEBIT';
              const isHighlighted = !isFrozen && (highlightCredit || highlightDebit) && !textOnlyHighlight;
              const highlightText = !isFrozen && (highlightCredit || highlightDebit) && textOnlyHighlight;
      const baseHighlight = isHighlighted ? 'bg-red-100' : '';
      const rowClass = isFrozen ? 'text-slate-300 font-normal' : 'text-slate-600';
      const expenseHighlight = highlightExpenses && category === 'EXPENSE';

              return (
                <tr key={index} className={`text-right align-top whitespace-nowrap ${rowClass}`}>
                  <td className="py-1 px-1 text-left overflow-hidden">{String(item?.journalId ?? '-')}</td>
                  <td className="py-1 px-1 text-left overflow-hidden">{String(item?.accountId ?? '-')}</td>
                  <td className="py-1 px-1 text-left overflow-hidden">{String(item?.accountCode ?? '-')}</td>
                  <td className="py-1 px-1 text-left overflow-hidden">{String(item?.accountName ?? '-')}</td>
                  <td className={`py-1 px-1 text-left overflow-hidden ${baseHighlight}`}>{renderCategoryChip(item?.category)}</td>
                  <td className="py-1 px-1 text-left overflow-hidden">{String(item?.asset ?? '-')}</td>
        <td className={`py-1 px-1 overflow-hidden ${baseHighlight} ${isHighlighted ? 'rounded-l' : ''}`}>{renderNumberCell(item?.amount, isHighlighted, expenseHighlight ? 'text-rose-600' : (highlightText ? 'text-rose-600' : undefined), isFrozen)}</td>
                  <td className={`py-1 px-1 overflow-hidden ${baseHighlight}`}>
                    <span className={`px-1.5 py-0.5 rounded-md border text-[9px] font-black uppercase tracking-tighter ${isFrozen ? 'border-slate-200 text-slate-300' : 'bg-slate-100 border-slate-200 text-slate-600'}`}>
                      {direction}
                    </span>
                  </td>
        <td className={`py-1 px-1 overflow-hidden ${baseHighlight} ${isHighlighted ? 'rounded-r' : ''}`}>{renderNumberCell(item?.balanceAfter, isHighlighted, expenseHighlight ? 'text-rose-600' : (highlightText ? 'text-rose-600' : undefined), isFrozen)}</td>
                  <td className="py-1 px-1 text-left overflow-hidden">
                    <span className={`px-1.5 py-0.5 rounded-md border text-[9px] font-black uppercase tracking-tighter ${isFrozen ? 'border-slate-200 text-slate-300' : 'bg-slate-100 border-slate-200 text-slate-600'}`}>
                      {item?.referenceType ?? '-'}
                    </span>
                  </td>
                <td className="py-1 pr-1 text-left overflow-hidden">
                  <div className="flex items-center gap-1">
                    {isFrozen && item?.referenceId && (
                      <Tooltip
                        title={(
                          <div className="text-[10px] leading-relaxed min-w-[320px]">
                            <div className="grid grid-cols-2 gap-6">
                              <div className="space-y-2 text-slate-700">
                                <p className="font-bold text-indigo-500 border-b border-slate-200 pb-1">凍結與解凍說明</p>
                                <p>資金凍結與解凍操作，不影響帳戶餘額。</p>
                              </div>
                              <div className="space-y-2 text-slate-700">
                                <p className="font-bold text-indigo-500 border-b border-slate-200 pb-1">Freeze/Unfreeze Note</p>
                                <p>Freeze/unfreeze operations do not change account balances.</p>
                              </div>
                            </div>
                          </div>
                        )}
                        classNames={{ root: 'liquid-tooltip' }}
                        styles={{ root: { maxWidth: 'none' } }}
                      >
                        <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-slate-100 flex items-center justify-center text-[9px] text-slate-400 cursor-help border border-slate-200">?</div>
                      </Tooltip>
                    )}
                    {item?.referenceId && tradeRefTypes.has(refType) && (
                      <button
                        className="text-slate-400 hover:text-slate-600"
                        onClick={(event) => openJournalTradeDetail(String(item.referenceId), event)}
                        aria-label="Open trade detail"
                      >
                        <svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.4">
                          <circle cx="7" cy="7" r="4.5" />
                          <path d="M10.5 10.5L14 14" strokeLinecap="round" />
                        </svg>
                      </button>
                    )}
                    {item?.referenceId ? (
                      disableReferenceLink ? <span>{String(item.referenceId)}</span> : (
                        <button onClick={() => handleOpenReferenceJournals(item.referenceType, item.referenceId)} className="text-sky-500 hover:text-sky-600 underline">
                          {String(item.referenceId)}
                        </button>
                      )
                    ) : '-'}
                  </div>
                </td>
                <td className="py-1 pr-1 font-mono overflow-hidden">{String(item?.seq ?? '-')}</td>
                <td className="py-1 pr-1 text-left overflow-hidden bg-yellow-200/50">{String(item?.description ?? '-')}</td>
                <td className="py-1 pr-1 font-mono text-[9px] overflow-hidden">{String(item?.eventTime ?? '-')}</td>
                                <td className="py-1 pr-1 font-mono text-[9px] overflow-hidden">{String(item?.createdAt ?? '-')}</td>
              </tr>
            );
          })}
        </tbody>
        </table>
      </div>
    );
  };

  const instrumentMap = useMemo(() => new Map(instruments.map(i => [String(i.instrumentId), i.name || i.symbol || String(i.instrumentId)])), [instruments]);
  const referenceUserSplit = splitExpenseRevenue(referenceJournalData?.accountJournals);
  const referencePlatformSplit = splitExpenseRevenue(referenceJournalData?.platformJournals);
  const referenceUserRows = referenceUserSplit.core;

  const renderAccountItems = (items?: AccountBalanceItem[]) => {
    const rows = items && items.length > 0 ? items : [null];
    return (
      <div className="overflow-x-auto">
        <table className="w-full text-xs text-slate-600 border-collapse table-fixed">
          <thead>
            <tr className="text-[10px] uppercase text-slate-400 border-b border-white/60 text-right whitespace-nowrap">
              <th className="py-1 pr-1 font-semibold text-left w-[90px]">accountId</th>
              <th className="py-1 pr-1 font-semibold text-left w-[60px]">accCode</th>
              <th className="py-1 pr-1 font-semibold text-left w-[75px]">accName</th>
              <th className="py-1 pr-1 font-semibold text-left w-[55px]">category</th>
              <th className="py-1 pr-1 font-semibold text-left w-[70px]">Inst</th>
              <th className="py-1 pr-1 font-semibold text-left w-[40px]">asset</th>
              <th className="py-1 pr-1 font-semibold w-[80px]">balance</th>
              <th className="py-1 pr-1 font-semibold w-[80px]">available</th>
              <th className="py-1 pr-1 font-semibold w-[80px]">reserved</th>
              <th className="py-1 pr-1 font-semibold w-[30px]">ver</th>
              <th className="py-1 pr-1 font-semibold w-[95px]">updatedAt</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/40">
            {rows.map((item, index) => {
              const isExpense = String(item?.category ?? '').toUpperCase() === 'EXPENSE';
              const forceRed = isExpense ? 'text-rose-600' : undefined;
              return (
                <tr key={index} className="text-right whitespace-nowrap">
                  <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">
                    {item?.accountId != null ? (
                      <button onClick={() => handleOpenAccountJournal(Number(item.accountId))} className="text-sky-500 underline">{item.accountId}</button>
                    ) : '-'}
                  </td>
                  <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{String(item?.accountCode ?? '-')}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{String(item?.accountName ?? '-')}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{renderCategoryChip(item?.category)}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">
                    {item?.instrumentId ? (instrumentMap.get(String(item.instrumentId)) || String(item.instrumentId)) : ''}
                  </td>
                  <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{String(item?.asset ?? '-')}</td>
                  <td className="py-1 pr-1 overflow-hidden text-ellipsis">{renderNumberCell(item?.balance, false, forceRed)}</td>
                  <td className="py-1 pr-1 overflow-hidden text-ellipsis">{renderNumberCell(item?.available, false, forceRed)}</td>
                  <td className="py-1 pr-1 overflow-hidden text-ellipsis">{renderNumberCell(item?.reserved, false)}</td>
                  <td className="py-1 pr-1 font-mono overflow-hidden text-ellipsis">{String(item?.version ?? '-')}</td>
                  <td className="py-1 pr-1 font-mono text-[9px] overflow-hidden text-ellipsis">{String(item?.updatedAt ?? '-')}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    );
  };

  const renderPlatformAccountRows = (items?: PlatformAccountItem[]) => {
    const rows = items && items.length > 0 ? items : [null];
    return (
      <div className="overflow-x-auto">
        <table className="w-full text-xs text-slate-600 border-collapse table-fixed">
          <thead>
            <tr className="text-[10px] uppercase text-slate-400 border-b border-white/60 text-right whitespace-nowrap">
              <th className="py-1 pr-1 font-semibold text-left w-[100px]">accountId</th>
              <th className="py-1 pr-1 font-semibold text-left w-[60px]">accCode</th>
              <th className="py-1 pr-1 font-semibold text-left w-[75px]">accName</th>
              <th className="py-1 pr-1 font-semibold text-left w-[55px]">category</th>
              <th className="py-1 pr-1 font-semibold text-left w-[40px]">asset</th>
              <th className="py-1 pr-1 font-semibold w-[85px]">balance</th>
              <th className="py-1 pr-1 font-semibold w-[30px]">ver</th>
              <th className="py-1 pr-1 font-semibold w-[100px]">updatedAt</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/40">
            {rows.map((item, index) => {
              const isExpense = String(item?.category ?? '').toUpperCase() === 'EXPENSE';
              const forceRed = isExpense ? 'text-rose-600' : undefined;
              return (
                <tr key={index} className="text-right whitespace-nowrap">
                  <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">
                    {item?.accountId != null ? (
                      <button onClick={() => handleOpenPlatformAccountJournal(Number(item.accountId))} className="text-sky-500 underline">{item.accountId}</button>
                    ) : '-'}
                  </td>
                  <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{item?.accountCode}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{item?.accountName}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{renderCategoryChip(item?.category)}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{item?.asset}</td>
                  <td className="py-1 pr-1 overflow-hidden text-ellipsis">{renderNumberCell(item?.balance, false, forceRed)}</td>
                  <td className="py-1 pr-1 font-mono overflow-hidden text-ellipsis">{item?.version}</td>
                  <td className="py-1 pr-1 font-mono text-[9px] overflow-hidden text-ellipsis">{item?.updatedAt}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    );
  };

  const [currentUser, setCurrentUser] = useState<{ id: number; email?: string } | null>(null);

  useEffect(() => {
    getCurrentUser().then(res => {
      if (String(res?.code) === '0' && res.data) {
        setCurrentUser(res.data);
        setAuthOpen(false);
      } else {
        setAuthOpen(true);
      }
    }).catch(() => setAuthOpen(true));
  }, [refreshTrigger]);

  const handleLoginSuccess = () => {
    setIsPaused(false);
    handleRefresh();
  };

  const handleLogout = () => { 
    localStorage.removeItem('accessToken'); 
    setCurrentUser(null);
    setAuthOpen(true); 
    setIsPaused(true);
  };
  const handleResetData = async () => {
    if (!window.confirm('Are you sure?')) return;
    setResetting(true);
    try {
      const res = await resetSystemData();
      if (String(res?.code) === '0') { message.success('Reset successfully'); handleRefresh(); }
      else message.error(res?.message || 'Failed');
    } catch { message.error('Error'); }
    finally { setResetting(false); }
  };

  const formatSnapshotTimestamp = (value?: number | null) => {
    if (value == null) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    return date.toISOString();
  };

  const formatValue = (v: any) => {
    const n = Number(v);
    if (isNaN(n)) return String(v ?? '-');
    return n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 8 });
  };

  const renderNumberCell = (v: any, isHighlighted: boolean = false, colorOverride?: string, isFrozen?: boolean) => {
    const n = Number(v);
    if (isNaN(n)) return <span className={isFrozen ? "text-slate-300" : "font-bold"}>{String(v ?? '-')}</span>;
    let colorClass = colorOverride || (isHighlighted ? 'text-slate-900' : (n > 0 ? 'text-emerald-600' : (n < 0 ? 'text-rose-600' : 'text-slate-600')));
    if (isFrozen) colorClass = 'text-slate-300';
    return <span className={`font-mono ${isFrozen ? "" : "font-bold"} ${colorClass}`}>{formatValue(v)}</span>;
  };

  return (
    <div className="flex h-screen flex-col bg-slate-50 overflow-hidden text-slate-900">
      <header className="flex h-14 items-center justify-between border-b border-white bg-white/80 px-6 backdrop-blur-md shadow-sm z-50">
        <div className="flex items-center gap-4">
          <div className="flex h-8 w-8 items-center justify-center rounded-xl bg-gradient-to-br from-sky-500 to-indigo-600 text-white font-black italic shadow-lg">LF</div>
          <h1 className="text-lg font-bold">Liquid Flow <span className="font-light text-slate-400">| Exchange</span></h1>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => setIsPaused(!isPaused)} className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border text-[10px] font-bold uppercase transition-all ${isPaused ? 'bg-amber-500/20 border-amber-500/40 text-amber-600' : 'bg-slate-500/10 border-slate-500/20 text-slate-500'}`}>
            {!isPaused && <span className="w-1.5 h-1.5 rounded-full bg-rose-500 animate-pulse shadow-[0_0_8px_rgba(244,63,94,0.8)]" />}
            {isPaused ? 'Paused' : 'Syncing'}
            <Tooltip title={(
              <div className="text-[10px] leading-relaxed">
                <p>「同步中」每 3 秒自動更新數據；「已暫停」則停止自動輪詢。</p>
                <p className="text-slate-400 mt-1">"Syncing" auto-updates every 3s; "Paused" stops auto-polling.</p>
              </div>
            )} classNames={{ root: 'liquid-tooltip' }} styles={{ root: { maxWidth: 'none' } }}>
              <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-white/50 flex items-center justify-center text-[9px] cursor-help border border-white/30">?</div>
            </Tooltip>
          </button>

          <button onClick={handleResetData} disabled={resetting} className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-600 text-[10px] font-bold uppercase hover:bg-rose-500 hover:text-white disabled:opacity-50 transition-all group">
            {resetting ? 'Resetting...' : 'Reset Data'}
            <Tooltip title={(
              <div className="text-[10px] leading-relaxed">
                <p>重置所有交易數據、餘額與分錄（僅測試環境）。</p>
                <p className="text-slate-400 mt-1">Resets all trades, balances, and journals (Test only).</p>
              </div>
            )} classNames={{ root: 'liquid-tooltip' }} styles={{ root: { maxWidth: 'none' } }}>
              <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-rose-200/50 group-hover:bg-white/20 flex items-center justify-center text-[9px] cursor-help border border-rose-300/50 group-hover:border-white/30">?</div>
            </Tooltip>
          </button>

          <button onClick={handleRefresh} className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 text-[10px] font-bold uppercase hover:bg-emerald-500 hover:text-white transition-all group">
            Refresh
            <Tooltip title={(
              <div className="text-[10px] leading-relaxed">
                <p>手動強制刷新當前頁面所有可見數據。</p>
                <p className="text-slate-400 mt-1">Manually force refresh all visible data on the page.</p>
              </div>
            )} classNames={{ root: 'liquid-tooltip' }} styles={{ root: { maxWidth: 'none' } }}>
              <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-emerald-200/50 group-hover:bg-white/20 flex items-center justify-center text-[9px] cursor-help border border-emerald-300/50 group-hover:border-white/30">?</div>
            </Tooltip>
          </button>

          <div className="ml-4 flex items-center gap-2 border-l border-slate-200 pl-4">
            {currentUser ? (
              <>
                <div className="flex flex-col items-end mr-2">
                  <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">User ID</span>
                  <span className="text-xs font-mono font-semibold text-indigo-600">{currentUser.id}</span>
                </div>
                <button onClick={handleLogout} className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-[10px] font-bold uppercase text-slate-600 hover:bg-slate-50 hover:text-rose-600 transition-colors">
                  Logout
                </button>
              </>
            ) : (
              <span className="text-xs text-slate-400 italic">Not logged in</span>
            )}
          </div>
        </div>
      </header>

      <main className="flex-1 flex flex-col overflow-hidden">
        <div className="flex-1 flex gap-4 p-4 min-h-0 overflow-hidden">
          <div className="flex-1 flex flex-col gap-4 min-w-0 overflow-hidden">
            <div className="flex-[3] flex gap-4 min-h-0">
              <div className="flex-1 flex flex-col gap-2 min-w-0">
                <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden p-1">
                  <MarketStats instruments={instruments} selectedInstrument={selectedInstrument} onSelectInstrument={i => setSelectedInstrumentId(i.instrumentId)} refreshTrigger={refreshTrigger} isPaused={isPaused} />
                </div>
                <div className="flex-1 bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
                  <Chart instrumentId={selectedInstrumentId} refreshTrigger={refreshTrigger} />
                </div>
              </div>
              <div className="w-[300px] bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
                <OrderBook selectedInstrumentId={selectedInstrumentId} refreshTrigger={refreshTrigger} isPaused={isPaused} contractSize={Number(selectedInstrument?.contractSize || 1)} />
              </div>
            </div>
            <div className="flex-[2] bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden flex flex-col">
              <Positions instruments={instruments} selectedInstrumentId={selectedInstrumentId} refreshTrigger={refreshTrigger} isPaused={isPaused} />
            </div>
          </div>
          
          <div className="w-[300px] flex flex-col gap-4 flex-none overflow-hidden">
            <div className="flex-none bg-white rounded-2xl border border-slate-200 shadow-sm p-4">
              <TradeForm instrument={selectedInstrument} refreshTrigger={refreshTrigger} onOrderCreated={handleRefresh} isPaused={isPaused} />
            </div>
                        <div className="flex-1 bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden flex flex-col relative min-h-0">
                          <AccountPanel refreshTrigger={refreshTrigger} onOpenBalanceSheet={handleOpenBalanceSheet} />
                        </div>
          </div>
        </div>
      </main>

      {balanceSheetOpen && (
        <div 
          className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-md"
          onClick={(e) => { if (e.target === e.currentTarget) handleCloseBalanceSheet(); }}
        >
          <div className="relative w-[96vw] max-w-[1860px] bg-white/95 backdrop-blur-sm rounded-3xl border border-white/70 shadow-2xl p-6 overflow-hidden flex flex-col max-h-[95vh]">
            {(accountJournalOpen || referenceJournalOpen || platformAccountJournalOpen) && (
              <div 
                className="absolute inset-0 z-[110] flex items-center justify-center p-4"
                onClick={(e) => {
                  if (e.target !== e.currentTarget) return;
                  if (referenceJournalOpen) {
                    handleCloseReferenceJournals();
                  } else {
                    handleCloseAccountJournal();
                    handleClosePlatformAccountJournal();
                    handleCloseReferenceJournals();
                  }
                }}
              >
                <div className="absolute inset-0 bg-slate-900/20 backdrop-blur-sm pointer-events-none" />
                <div ref={referenceJournalOpen ? referenceJournalRef : (accountJournalOpen ? accountJournalRef : platformAccountJournalRef)} className="relative w-full max-w-[90%] rounded-2xl border border-white bg-white/95 p-6 shadow-2xl flex flex-col pointer-events-auto">
                  <div className="flex items-center justify-between mb-4">
                    <div className="text-xs font-bold uppercase text-slate-500 tracking-widest">
                      {referenceJournalOpen ? `Related Journals` : (accountJournalOpen ? `Account Journal` : `Exchange Journal`)}
                    </div>
                    <button 
                      onClick={() => {
                        if (referenceJournalOpen) {
                          handleCloseReferenceJournals();
                        } else {
                          handleCloseAccountJournal();
                          handleClosePlatformAccountJournal();
                        }
                      }} 
                      className="px-4 py-1.5 rounded-full border border-slate-200 bg-white text-[10px] font-bold uppercase text-slate-500 hover:text-slate-700 hover:bg-slate-50 transition-all shadow-sm"
                    >
                      Back
                    </button>
                  </div>
                  <div className="overflow-auto max-h-[70vh]">
                    {referenceJournalOpen ? (
                      referenceJournalLoading && !referenceJournalData ? (
                        <div className="p-8 text-center text-xs text-slate-400">Loading...</div>
                      ) : (
                        <div className="space-y-8">
                          <div>
                            <div className="text-[10px] font-bold text-slate-400 mb-2 uppercase flex items-center gap-2">
                              <div className="w-1 h-1 rounded-full bg-slate-400"></div>
                              User
                            </div>
                            {renderJournalRows(referenceUserRows, { disableReferenceLink: true, highlightMode: 'text' })}
                          </div>
                          <div className="border-t border-slate-200/80" />
                          <div>
                            <div className="text-[10px] font-bold text-slate-400 mb-2 uppercase flex items-center gap-2">
                              <div className="w-1 h-1 rounded-full bg-slate-400"></div>
                              Exchange
                            </div>
                            {(referencePlatformSplit.core.length > 0 || referencePlatformSplit.expenseRevenue.length === 0) && renderPlatformJournalRows(referencePlatformSplit.core, { disableReferenceLink: true, highlightMode: 'text' })}
                          </div>
                          <div className="border-t border-slate-200/80" />
                          <div>
                            <div className="text-[10px] font-bold text-slate-400 mb-2 uppercase flex items-center gap-2">
                              <div className="w-1 h-1 rounded-full bg-slate-400"></div>
                              Expense & Revenue
                            </div>
                            <div className="flex flex-col gap-6">
                              <div className="space-y-2">
                                <div className="text-[10px] font-semibold uppercase text-slate-500">User</div>
                                {renderJournalRows(referenceUserSplit.expenseRevenue, { disableReferenceLink: true, highlightMode: 'text', highlightExpenses: true })}
                              </div>
                              <div className="space-y-2">
                                <div className="text-[10px] font-semibold uppercase text-slate-500">Exchange</div>
                                {renderPlatformJournalRows(referencePlatformSplit.expenseRevenue, { disableReferenceLink: true, highlightMode: 'text', highlightExpenses: true })}
                              </div>
                            </div>
                          </div>
                        </div>
                      )
                    ) : (
                      <>
                        {accountJournalOpen && (accountJournalLoading && !accountJournalData ? <div className="p-8 text-center text-xs text-slate-400">Loading...</div> : renderJournalRows(accountJournalData?.journals, { highlightMode: 'text' }))}
                        {platformAccountJournalOpen && (platformAccountJournalLoading && !platformAccountJournalData ? <div className="p-8 text-center text-xs text-slate-400">Loading...</div> : renderPlatformJournalRows(platformAccountJournalData?.journals, { highlightMode: 'text' }))}
                      </>
                    )}
                  </div>
                </div>
              </div>
            )}
            <div className="flex items-center justify-between mb-6">
                <div className="flex items-center gap-3">
                  <h2 className="text-sm font-bold uppercase tracking-widest text-slate-800">Balance Sheet</h2>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={handleSelectLatestBalanceSheet}
                      className={`px-3 py-1 rounded-full text-[10px] font-bold uppercase border transition-all ${balanceSheetTimestamp == null ? 'bg-emerald-500/15 border-emerald-500/30 text-emerald-600' : 'bg-white/70 border-white/60 text-slate-500 hover:text-slate-700'}`}
                    >
                      Latest
                    </button>
                    <button
                      onClick={handleOpenHistoricalBalanceSheet}
                      className={`px-3 py-1 rounded-full text-[10px] font-bold uppercase border transition-all ${balanceSheetSelectionOpen ? 'bg-sky-500/15 border-sky-500/30 text-sky-600' : 'bg-white/70 border-white/60 text-slate-500 hover:text-slate-700'}`}
                    >
                      Historical
                    </button>
                    <Tooltip 
                      title={(
                        <div className="text-[10px] leading-relaxed min-w-[280px]">
                          <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-2 text-slate-700">
                              <p className="font-bold border-b border-slate-200 pb-1">快照說明</p>
                              <ul className="space-y-1">
                                <li><span className="font-bold text-emerald-600">Latest</span>: 顯示當前最新狀態。</li>
                                <li><span className="font-bold text-sky-600">Historical</span>: 查詢歷史時間點的帳戶快照。</li>
                                <li><span className="font-bold text-slate-600">Snapshot</span>: 數據對應的實際時間點。</li>
                              </ul>
                            </div>
                            <div className="space-y-2 text-slate-700">
                              <p className="font-bold border-b border-slate-200 pb-1">Snapshot Guide</p>
                              <ul className="space-y-1">
                                <li><span className="font-bold text-emerald-600">Latest</span>: Shows real-time state.</li>
                                <li><span className="font-bold text-sky-600">Historical</span>: Query account state at past time.</li>
                                <li><span className="font-bold text-slate-600">Snapshot</span>: Exact time of data.</li>
                              </ul>
                            </div>
                          </div>
                        </div>
                      )}
                      placement="bottom"
                      classNames={{ root: 'liquid-tooltip' }}
                      styles={{ root: { maxWidth: 'none' } }}
                    >
                      <div className="liquid-tooltip-trigger w-3 h-3 rounded-full bg-slate-100 flex items-center justify-center text-[9px] text-slate-400 cursor-help border border-slate-200">?</div>
                    </Tooltip>
                    <span className="text-[10px] text-slate-400">
                      Snapshot: <span className="font-mono text-[11px] text-slate-500">{balanceSheetData?.snapshotAt ?? '-'}</span>
                    </span>
                  </div>
                </div>
              <button onClick={handleCloseBalanceSheet} className="h-8 w-8 flex items-center justify-center rounded-full border border-white/60 bg-white/70 text-slate-500 hover:text-slate-700">X</button>
            </div>
            {balanceSheetRange && balanceSheetSelectionOpen && (
              <div className="mb-4 px-2">
                <div className="flex flex-col gap-2 text-[10px] text-slate-500 mb-3">
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] font-semibold text-slate-600">Select Time</span>
                    <input
                      type="datetime-local"
                      min={formatLocalDatetime(balanceSheetRange.earliest)}
                      max={formatLocalDatetime(balanceSheetRange.latest)}
                      value={balanceSheetPendingTimestamp != null ? formatLocalDatetime(balanceSheetPendingTimestamp) : ''}
                      onChange={(e) => {
                        if (!balanceSheetRange) {
                          return;
                        }
                        const nextValue = new Date(e.target.value).getTime();
                        if (Number.isNaN(nextValue)) {
                          return;
                        }
                        setBalanceSheetPendingTimestamp(clampSnapshotValue(nextValue, balanceSheetRange));
                      }}
                      className="w-[220px] rounded-md border border-slate-200 bg-white px-2 py-1 text-[10px] font-mono text-slate-600"
                    />
                    <button
                      onClick={handleApplyBalanceSheetSnapshot}
                      className="px-3 py-1 rounded-full text-[10px] font-bold uppercase border border-sky-500/30 text-sky-600 bg-sky-500/10 hover:bg-sky-500 hover:text-white transition-all"
                    >
                      Query
                    </button>
                  </div>
                  <div className="flex items-center justify-between">
                  <span className="flex items-center gap-1">Earliest <span className="font-mono text-[11px] text-slate-400">{formatSnapshotTimestamp(balanceSheetRange.earliest)}</span></span>
                  <span className="flex items-center gap-1">Selected <span className="font-mono text-[11px] text-slate-400">{formatSnapshotTimestamp(balanceSheetPendingTimestamp ?? balanceSheetRange.latest)}</span></span>
                  <span className="flex items-center gap-1">Latest <span className="font-mono text-[11px] text-slate-400">{formatSnapshotTimestamp(balanceSheetRange.latest)}</span></span>
                  </div>
                </div>
                <input
                  type="range"
                  min={balanceSheetRange.earliest}
                  max={balanceSheetRange.latest}
                  step={60000}
                  value={balanceSheetPendingTimestamp ?? balanceSheetRange.latest}
                  onChange={(e) => handleBalanceSheetSliderChange(Number(e.target.value))}
                  className="w-full accent-sky-500"
                />
              </div>
            )}
            <div className="flex-1 overflow-y-auto">
              <div className="space-y-12">
                {/* User Ledger */}
                <section>
                  <div className="flex items-center gap-2 mb-4 border-b-2 border-sky-100 pb-1">
                    <div className="text-xs font-black uppercase tracking-widest text-sky-600">User</div>
                    <Tooltip 
                      title={(
                        <div className="text-[10px] leading-relaxed min-w-[520px]">
                          <div className="grid grid-cols-2 gap-6">
                            {/* Chinese Column */}
                            <div className="space-y-2 text-slate-700">
                              <p className="font-bold text-sky-600 border-b border-slate-200 pb-1">用戶複式記帳法</p>
                              <p>遵循 <span className="text-sky-600 font-mono">資產 = 負債 + 權益 + (收入 - 費用)</span></p>
                              <ul className="list-disc pl-4 space-y-1">
                                <li><span className="font-semibold text-emerald-600">Assets (資產)</span>: 用戶實際持有的代幣。</li>
                                <li><span className="font-semibold text-rose-600">Liabilities (負債)</span>: 用戶對交易所的欠款。</li>
                                <li><span className="font-semibold text-sky-600">Equity (權益)</span>: 用戶的淨資產。</li>
                                <li><span className="font-semibold text-amber-600">Revenue (收入)</span>: 交易獲利或入金。</li>
                                <li><span className="font-semibold text-slate-500">Expenses (費用)</span>: 交易虧損或手續費。</li>
                              </ul>
                              <div className="pt-1 border-t border-slate-200">
                                <p className="font-bold text-slate-600">分錄 (Journal Entries)</p>
                                <p>所有餘額變動均由「分錄」驅動。每一筆交易都會產生對應的借貸分錄，確保帳本永遠平衡。</p>
                              </div>
                            </div>
                            {/* English Column */}
                            <div className="space-y-2 text-slate-700">
                              <p className="font-bold text-sky-600 border-b border-slate-200 pb-1">User Double-Entry Bookkeeping</p>
                              <p className="font-mono text-sky-600 text-[9px]">Assets = Liabilities + Equity + (Revenue - Expenses)</p>
                              <ul className="list-disc pl-4 space-y-1">
                                <li><span className="font-semibold text-emerald-600">Assets</span>: Funds/Tokens owned by user.</li>
                                <li><span className="font-semibold text-rose-600">Liabilities</span>: Debts or loans owed to the exchange.</li>
                                <li><span className="font-semibold text-sky-600">Equity</span>: Net worth of the account.</li>
                                <li><span className="font-semibold text-amber-600">Revenue</span>: Trading gains or deposits.</li>
                                <li><span className="font-semibold text-slate-500">Expenses</span>: Trading losses or fees paid.</li>
                              </ul>
                              <div className="pt-1 border-t border-slate-200">
                                <p className="font-bold text-slate-600">Journal Entries</p>
                                <p>All balance changes are driven by "Journals". Every trade generates debit/credit entries, ensuring <span className="italic">Total Debits = Total Credits</span>.</p>
                              </div>
                            </div>
                          </div>
                        </div>
                      )}
                      placement="right"
                      classNames={{ root: 'liquid-tooltip' }}
                      classNames={{ root: 'liquid-tooltip' }}
                      styles={{ root: { maxWidth: 'none' } }}
                    >
                      <div className="liquid-tooltip-trigger w-4 h-4 rounded-full bg-sky-50 flex items-center justify-center text-[10px] text-sky-400 cursor-help border border-sky-200">?</div>
                    </Tooltip>
                  </div>
                  <div className="flex flex-col border border-slate-200/80 divide-y divide-slate-200/80">
                    <div className="flex flex-col divide-y divide-slate-200/80 min-[1600px]:grid min-[1600px]:grid-cols-2 min-[1600px]:divide-x min-[1600px]:divide-y-0">
                      <div className="p-4 flex flex-col">
                        <div className="text-[10px] font-bold text-slate-400 uppercase mb-3 tracking-wider">Assets</div>
                        <div className="flex-1">{renderAccountItems(balanceSheetData?.assets)}</div>
                      </div>
                      <div className="flex flex-col divide-y divide-slate-200/80">
                        <div className="p-4 flex flex-col flex-1">
                          <div className="text-[10px] font-bold text-slate-400 uppercase mb-3 tracking-wider">Liabilities</div>
                          <div className="flex-1">{renderAccountItems(balanceSheetData?.liabilities)}</div>
                        </div>
                        <div className="p-4 flex flex-col flex-1">
                          <div className="text-[10px] font-bold text-slate-400 uppercase mb-3 tracking-wider">Equity</div>
                          <div className="flex-1">{renderAccountItems(balanceSheetData?.equity)}</div>
                        </div>
                      </div>
                    </div>
                    <div className="flex flex-col divide-y divide-slate-200/80 min-[1600px]:grid min-[1600px]:grid-cols-2 min-[1600px]:divide-x min-[1600px]:divide-y-0">
                      <div className="p-4 flex flex-col">
                        <div className="text-[10px] font-bold text-slate-400 uppercase mb-3 tracking-wider">Expenses</div>
                        <div className="flex-1">{renderAccountItems(balanceSheetData?.expenses)}</div>
                      </div>
                      <div className="p-4 flex flex-col">
                        <div className="text-[10px] font-bold text-slate-400 uppercase mb-3 tracking-wider">Revenue</div>
                        <div className="flex-1">{renderAccountItems(balanceSheetData?.revenue)}</div>
                      </div>
                    </div>
                  </div>
                </section>

                {/* Exchange Ledger */}
                <section>
                  <div className="flex items-center gap-2 mb-4 border-b-2 border-indigo-100 pb-1">
                    <div className="text-xs font-black uppercase tracking-widest text-indigo-600">Exchange</div>
                    <Tooltip 
                      title={(
                        <div className="text-[10px] leading-relaxed min-w-[520px]">
                          <div className="grid grid-cols-2 gap-6">
                            {/* Chinese Column */}
                            <div className="space-y-2 text-slate-700">
                              <p className="font-bold text-indigo-600 border-b border-slate-200 pb-1">交易所內部記帳</p>
                              <p>交易所作為託管方，其內部的 <span className="text-indigo-600 font-semibold">資產</span> 必須恆等於對所有用戶的 <span className="text-indigo-600 font-semibold">總負債</span>。</p>
                              <p>這裡展示的是交易所帳戶（庫存、手續費收入等）與整體系統平衡狀態。</p>
                              <div className="pt-1 border-t border-slate-200">
                                <p className="font-bold text-slate-600">分錄 (Journal Entries)</p>
                                <p>系統自動化生成分錄，記錄每一分錢的流向。分錄是審計的基礎，確保交易所資產與用戶權益隨時對齊。</p>
                              </div>
                            </div>
                            {/* English Column */}
                            <div className="space-y-2 text-slate-700">
                              <p className="font-bold text-indigo-600 border-b border-slate-200 pb-1">Exchange Accounting</p>
                              <p>As a custodian, the exchange's <span className="text-indigo-500 font-semibold">Assets</span> must always equal its <span className="text-indigo-500 font-semibold">Total Liabilities</span> to users.</p>
                              <p>This section displays exchange accounts (inventory, fees) and overall system equilibrium.</p>
                              <div className="pt-1 border-t border-slate-200">
                                <p className="font-bold text-slate-600">Journal Entries</p>
                                <p>Automated journals record every movement of funds. They are the foundation for auditing and asset-equity alignment.</p>
                              </div>
                            </div>
                          </div>
                        </div>
                      )}
                      placement="right"
                      classNames={{ root: 'liquid-tooltip' }}
                      classNames={{ root: 'liquid-tooltip' }}
                      styles={{ root: { maxWidth: 'none' } }}
                    >
                      <div className="liquid-tooltip-trigger w-4 h-4 rounded-full bg-indigo-50 flex items-center justify-center text-[10px] text-indigo-400 cursor-help border border-indigo-200">?</div>
                    </Tooltip>
                  </div>
                  <div className="flex flex-col border border-slate-200/80 divide-y divide-slate-200/80">
                    <div className="flex flex-col divide-y divide-slate-200/80 min-[1600px]:grid min-[1600px]:grid-cols-2 min-[1600px]:divide-x min-[1600px]:divide-y-0">
                      <div className="p-4 flex flex-col">
                        <div className="text-[10px] font-bold text-slate-400 uppercase mb-3 tracking-wider">Assets</div>
                        <div className="flex-1">{renderPlatformAccountRows(platformAccountsData?.assets)}</div>
                      </div>
                      <div className="flex flex-col divide-y divide-slate-200/80">
                        <div className="p-4 flex flex-col flex-1">
                          <div className="text-[10px] font-bold text-slate-400 uppercase mb-3 tracking-wider">Liabilities</div>
                          <div className="flex-1">{renderPlatformAccountRows(platformAccountsData?.liabilities)}</div>
                        </div>
                        <div className="p-4 flex flex-col flex-1">
                          <div className="text-[10px] font-bold text-slate-400 uppercase mb-3 tracking-wider">Equity</div>
                          <div className="flex-1">{renderPlatformAccountRows(platformAccountsData?.equity)}</div>
                        </div>
                      </div>
                    </div>
                    <div className="flex flex-col divide-y divide-slate-200/80 min-[1600px]:grid min-[1600px]:grid-cols-2 min-[1600px]:divide-x min-[1600px]:divide-y-0">
                      <div className="p-4 flex flex-col">
                        <div className="text-[10px] font-bold text-slate-400 uppercase mb-3 tracking-wider">Expenses</div>
                        <div className="flex-1">{renderPlatformAccountRows(platformAccountsData?.expenses)}</div>
                      </div>
                      <div className="p-4 flex flex-col">
                        <div className="text-[10px] font-bold text-slate-400 uppercase mb-3 tracking-wider">Revenue</div>
                        <div className="flex-1">{renderPlatformAccountRows(platformAccountsData?.revenue)}</div>
                      </div>
                    </div>
                  </div>
                </section>
              </div>
            </div>
            <div className="pb-6" />
          </div>
        </div>
      )}

      <AuthModal open={authOpen} onSuccess={handleLoginSuccess} />
      {journalTradeDetail && (
        <div className="fixed inset-0 z-[9999] bg-slate-900/30" onClick={() => setJournalTradeDetail(null)}>
          <div
            className="absolute w-[360px] max-w-[90vw] -translate-y-full rounded-xl border border-slate-200 bg-white p-3 text-[11px] text-slate-700 shadow-xl"
            style={{ top: journalTradeAnchor?.top ?? 16, left: journalTradeAnchor?.left ?? 16 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-2 flex items-center justify-between">
              <div className="text-[10px] uppercase font-bold text-slate-500">Trade Detail</div>
              <button className="text-slate-400 hover:text-slate-600" onClick={() => setJournalTradeDetail(null)}>x</button>
            </div>
            {journalTradeDetailLoading ? (
              <div className="py-6 text-center text-slate-400">Loading...</div>
            ) : (
              <div className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                <span className="text-slate-400">Trade Id</span><span className="font-mono">{journalTradeDetail.tradeId}</span>
                <span className="text-slate-400">Instrument</span><span>{instrumentMap.get(String(journalTradeDetail.instrumentId)) || journalTradeDetail.instrumentId}</span>
                <span className="text-slate-400">Order Id</span><span className="font-mono">{journalTradeDetail.orderId}</span>
                <span className="text-slate-400">Counterparty</span><span className="font-mono">{journalTradeDetail.counterpartyOrderId}</span>
                <span className="text-slate-400">Price</span><span className="font-mono text-sky-600 font-bold">{formatValue(journalTradeDetail.price)}</span>
                <span className="text-slate-400">Quantity</span><span className="font-mono text-sky-600 font-bold">{formatValue(journalTradeDetail.quantity)}</span>
                <span className="text-slate-400">Maker Fee</span><span className="font-mono">{formatValue(journalTradeDetail.makerFee)}</span>
                <span className="text-slate-400">Taker Fee</span><span className="font-mono">{formatValue(journalTradeDetail.takerFee)}</span>
                <span className="text-slate-400">Executed At</span><span className="font-mono">{String(journalTradeDetail.executedAt ?? '-')}</span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
