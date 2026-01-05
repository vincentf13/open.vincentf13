import { useEffect, useState } from 'react';
import { Tooltip } from 'antd';

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
import {
  getAccountJournals,
  getJournalsByReference,
  getBalanceSheet,
  type AccountBalanceSheetResponse,
  type AccountBalanceItem,
  type AccountJournalResponse,
  type AccountJournalItem,
  type AccountReferenceJournalResponse,
  type PlatformJournalItem,
} from '../api/account';
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
  const [balanceSheetOpen, setBalanceSheetOpen] = useState(false);
  const [balanceSheetLoading, setBalanceSheetLoading] = useState(false);
  const [balanceSheetError, setBalanceSheetError] = useState<string | null>(null);
  const [balanceSheetData, setBalanceSheetData] = useState<AccountBalanceSheetResponse | null>(null);
  const [accountJournalOpen, setAccountJournalOpen] = useState(false);
  const [accountJournalLoading, setAccountJournalLoading] = useState(false);
  const [accountJournalError, setAccountJournalError] = useState<string | null>(null);
  const [accountJournalData, setAccountJournalData] = useState<AccountJournalResponse | null>(null);
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null);
  const [referenceJournalOpen, setReferenceJournalOpen] = useState(false);
  const [referenceJournalLoading, setReferenceJournalLoading] = useState(false);
  const [referenceJournalError, setReferenceJournalError] = useState<string | null>(null);
  const [referenceJournalData, setReferenceJournalData] = useState<AccountReferenceJournalResponse | null>(null);

  const handleRefresh = () => {
    setRefreshTrigger((prev) => prev + 1);
  };
  const handleOpenBalanceSheet = () => {
    setBalanceSheetOpen(true);
    setBalanceSheetError(null);
    setBalanceSheetLoading(true);
    getBalanceSheet()
      .then((result) => {
        if (String(result?.code) !== '0') {
          setBalanceSheetError(result?.message || 'Failed to load balance sheet.');
          setBalanceSheetData(null);
          return;
        }
        setBalanceSheetData(result?.data ?? null);
      })
      .catch(() => {
        setBalanceSheetError('Failed to load balance sheet.');
        setBalanceSheetData(null);
      })
      .finally(() => {
        setBalanceSheetLoading(false);
      });
  };
  const handleCloseBalanceSheet = () => {
    setBalanceSheetOpen(false);
    setAccountJournalOpen(false);
    setReferenceJournalOpen(false);
  };
  const handleOpenAccountJournal = (accountId: number) => {
    if (!Number.isFinite(accountId)) {
      return;
    }
    setSelectedAccountId(accountId);
    setAccountJournalOpen(true);
    setAccountJournalError(null);
    setAccountJournalData(null);
    setReferenceJournalOpen(false);
    setAccountJournalLoading(true);
    getAccountJournals(accountId)
      .then((result) => {
        if (String(result?.code) !== '0') {
          setAccountJournalError(result?.message || 'Failed to load account journals.');
          setAccountJournalData(null);
          return;
        }
        setAccountJournalData(result?.data ?? null);
      })
      .catch(() => {
        setAccountJournalError('Failed to load account journals.');
        setAccountJournalData(null);
      })
      .finally(() => {
        setAccountJournalLoading(false);
      });
  };
  const handleCloseAccountJournal = () => {
    setAccountJournalOpen(false);
    setSelectedAccountId(null);
    setReferenceJournalOpen(false);
  };

  const normalizeReferenceId = (value?: string | null) => {
    if (!value) {
      return null;
    }
    const prefix = value.split(':')[0].trim();
    const digits = prefix.replace(/\D/g, '');
    return digits || null;
  };

  const handleOpenReferenceJournals = (referenceType?: string | null, referenceId?: string | null) => {
    if (!referenceType || !referenceId) {
      return;
    }
    const prefix = normalizeReferenceId(referenceId);
    if (!prefix) {
      return;
    }
    setReferenceJournalOpen(true);
    setReferenceJournalError(null);
    setReferenceJournalData(null);
    setReferenceJournalLoading(true);
    getJournalsByReference(referenceType, prefix)
      .then((result) => {
        if (String(result?.code) !== '0') {
          setReferenceJournalError(result?.message || 'Failed to load reference journals.');
          setReferenceJournalData(null);
          return;
        }
        setReferenceJournalData(result?.data ?? null);
      })
      .catch(() => {
        setReferenceJournalError('Failed to load reference journals.');
        setReferenceJournalData(null);
      })
      .finally(() => {
        setReferenceJournalLoading(false);
      });
  };
  const handleCloseReferenceJournals = () => {
    setReferenceJournalOpen(false);
  };

  const renderJournalRows = (items?: AccountJournalItem[]) => {
    const rows = items && items.length > 0 ? items : [null];
    return (
      <table className="w-max text-[10px] text-left text-slate-600">
        <thead>
          <tr className="text-[9px] uppercase tracking-wider text-slate-400 border-b border-white/60">
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Journal ID</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">User ID</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Account ID</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Category</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Asset</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Amount</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Direction</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Balance After</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Reference Type</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Reference ID</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Description</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Event Time</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Created At</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-white/40">
          {rows.map((item, index) => (
            <tr key={`${item?.journalId ?? index}-${item?.referenceId ?? 'ref'}`}>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.journalId ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.userId ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.accountId ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.category ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.asset ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.amount ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.direction ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.balanceAfter ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.referenceType ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">
                {item?.referenceId ? (
                  <button
                    type="button"
                    onClick={() => handleOpenReferenceJournals(item.referenceType, item.referenceId)}
                    className="text-slate-700 hover:text-blue-600 underline decoration-dotted underline-offset-2"
                  >
                    {String(item.referenceId)}
                  </button>
                ) : (
                  '-'
                )}
              </td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.description ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.eventTime ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.createdAt ?? '-')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  };

  const renderPlatformJournalRows = (items?: PlatformJournalItem[]) => {
    const rows = items && items.length > 0 ? items : [null];
    return (
      <table className="w-max text-[10px] text-left text-slate-600">
        <thead>
          <tr className="text-[9px] uppercase tracking-wider text-slate-400 border-b border-white/60">
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Journal ID</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Account ID</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Category</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Asset</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Amount</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Direction</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Balance After</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Reference Type</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Reference ID</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Description</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Event Time</th>
            <th className="py-1 pr-2 font-semibold whitespace-nowrap">Created At</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-white/40">
          {rows.map((item, index) => (
            <tr key={`${item?.journalId ?? index}-${item?.referenceId ?? 'ref'}`}>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.journalId ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.accountId ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.category ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.asset ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.amount ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.direction ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.balanceAfter ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.referenceType ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.referenceId ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.description ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.eventTime ?? '-')}</td>
              <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.createdAt ?? '-')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  };

  const renderAccountItems = (items?: AccountBalanceItem[]) => {
    const rows = items && items.length > 0 ? items : [null];
    return (
      <div className="rounded-lg border border-white/60 bg-white/70 p-2">
        <div className="w-max">
          <table className="w-max text-[10px] text-left text-slate-600">
            <thead>
              <tr className="text-[9px] uppercase tracking-wider text-slate-400 border-b border-white/60">
                <th className="py-1 pr-2 font-semibold whitespace-nowrap">Account ID</th>
                <th className="py-1 pr-2 font-semibold whitespace-nowrap">Account Code</th>
                <th className="py-1 pr-2 font-semibold whitespace-nowrap">Account Name</th>
                <th className="py-1 pr-2 font-semibold whitespace-nowrap">Category</th>
                <th className="py-1 pr-2 font-semibold whitespace-nowrap">Instrument ID</th>
                <th className="py-1 pr-2 font-semibold whitespace-nowrap">Asset</th>
                <th className="py-1 pr-2 font-semibold whitespace-nowrap">Balance</th>
                <th className="py-1 pr-2 font-semibold whitespace-nowrap">Available</th>
                <th className="py-1 pr-2 font-semibold whitespace-nowrap">Reserved</th>
                <th className="py-1 pr-2 font-semibold whitespace-nowrap">Version</th>
                <th className="py-1 pr-2 font-semibold whitespace-nowrap">Updated At</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/40">
              {rows.map((item, index) => (
                <tr key={`${item?.accountId ?? index}-${item?.asset ?? 'asset'}-${item?.instrumentId ?? '0'}`}>
                  <td className="py-1 pr-2 whitespace-nowrap text-slate-700">
                    {item?.accountId != null ? (
                      <button
                        type="button"
                        onClick={() => handleOpenAccountJournal(Number(item.accountId))}
                        className="text-slate-700 hover:text-blue-600 underline decoration-dotted underline-offset-2"
                      >
                        {String(item.accountId)}
                      </button>
                    ) : (
                      '-'
                    )}
                  </td>
                  <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.accountCode ?? '-')}</td>
                  <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.accountName ?? '-')}</td>
                  <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.category ?? '-')}</td>
                  <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.instrumentId ?? '-')}</td>
                  <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.asset ?? '-')}</td>
                  <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.balance ?? '-')}</td>
                  <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.available ?? '-')}</td>
                  <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.reserved ?? '-')}</td>
                  <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.version ?? '-')}</td>
                  <td className="py-1 pr-2 whitespace-nowrap text-slate-700">{String(item?.updatedAt ?? '-')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  };

  const scheduleRefresh = () => {
    [300, 700, 1500, 3000, 5000].forEach((delay) => {
      setTimeout(() => {
        handleRefresh();
      }, delay);
    });
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
      scheduleRefresh();
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

        <div className="flex-1 min-h-0 pt-24 pb-24 lg:pr-[260px] flex flex-col gap-4">
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
        <div className="relative flex flex-col w-full border-t border-white/20 bg-white/5 lg:absolute lg:top-0 lg:right-0 lg:h-full lg:w-[260px] lg:z-10 lg:border-t-0 lg:border-l lg:border-white/20">
          <div className="flex flex-col h-full lg:pt-24">
            <div className="border-b border-white/20">
              <TradeForm instrument={selectedInstrument} onOrderPlaced={scheduleRefresh} />
            </div>
            <div className="flex-1 min-h-0 p-4">
              <div className="flex flex-col gap-3">
                <div className="relative">
                  {balanceSheetOpen && (
                    <div className="absolute bottom-full right-0 z-40 w-max max-w-none mb-3">
                      <div className="relative min-h-[320px] rounded-2xl border border-white/70 bg-white/95 shadow-xl backdrop-blur-sm p-4">
                        <div className="absolute -bottom-2 right-8 h-4 w-4 rotate-45 border border-white/70 bg-white/95" />
                        {accountJournalOpen && (
                          <div className="absolute left-4 top-4 z-50 w-max max-w-none">
                            <div className="relative rounded-2xl border border-white/70 bg-white/95 shadow-xl backdrop-blur-sm p-4">
                              <div className="flex items-center justify-between mb-3">
                                <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
                                  Account Journals
                                </div>
                                <button
                                  type="button"
                                  className="h-6 w-6 rounded-full border border-white/60 bg-white/70 text-[10px] text-slate-500 hover:text-slate-700 hover:bg-white transition-all"
                                  onClick={handleCloseAccountJournal}
                                >
                                  X
                                </button>
                              </div>
                              {accountJournalLoading && (
                                <div className="text-xs text-slate-400">Loading...</div>
                              )}
                              {accountJournalError && (
                                <div className="text-xs text-rose-500">{accountJournalError}</div>
                              )}
                              {!accountJournalLoading && !accountJournalError && (
                                <div className="max-h-[50vh] overflow-auto pr-1">
                                  {renderJournalRows(accountJournalData?.journals)}
                                </div>
                              )}
                              {referenceJournalOpen && (
                                <div className="absolute left-full top-0 ml-3 z-50 w-max max-w-none">
                                  <div className="relative rounded-2xl border border-white/70 bg-white/95 shadow-xl backdrop-blur-sm p-4">
                                    <div className="flex items-center justify-between mb-3">
                                      <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">
                                        Reference Journals
                                      </div>
                                      <button
                                        type="button"
                                        className="h-6 w-6 rounded-full border border-white/60 bg-white/70 text-[10px] text-slate-500 hover:text-slate-700 hover:bg-white transition-all"
                                        onClick={handleCloseReferenceJournals}
                                      >
                                        X
                                      </button>
                                    </div>
                                    {referenceJournalLoading && (
                                      <div className="text-xs text-slate-400">Loading...</div>
                                    )}
                                    {referenceJournalError && (
                                      <div className="text-xs text-rose-500">{referenceJournalError}</div>
                                    )}
                                    {!referenceJournalLoading && !referenceJournalError && (
                                      <div className="max-h-[50vh] overflow-auto pr-1">
                                        <div className="text-[10px] font-semibold text-slate-600 mb-2">Account Journals</div>
                                        {renderJournalRows(referenceJournalData?.accountJournals)}
                                        <div className="text-[10px] font-semibold text-slate-600 mt-4 mb-2">Platform Journals</div>
                                        {renderPlatformJournalRows(referenceJournalData?.platformJournals)}
                                      </div>
                                    )}
                                  </div>
                                </div>
                              )}
                            </div>
                          </div>
                        )}
                        <div className="flex items-center justify-between mb-3">
                          <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">Balance Sheet</div>
                          <button
                            type="button"
                            className="h-6 w-6 rounded-full border border-white/60 bg-white/70 text-[10px] text-slate-500 hover:text-slate-700 hover:bg-white transition-all"
                            onClick={handleCloseBalanceSheet}
                          >
                            X
                          </button>
                        </div>
                        {balanceSheetLoading && (
                          <div className="text-xs text-slate-400">Loading...</div>
                        )}
                        {balanceSheetError && (
                          <div className="text-xs text-rose-500">{balanceSheetError}</div>
                        )}
                        {!balanceSheetLoading && !balanceSheetError && (
                          <div className="max-h-[60vh] overflow-y-auto pr-1">
                            <div className="grid grid-cols-2 text-xs text-slate-600 border border-slate-200/80 divide-x divide-y divide-slate-200/80">
                              <div className="flex flex-col gap-2 p-3">
                                <div className="font-semibold text-slate-700">Assets</div>
                                {renderAccountItems(balanceSheetData?.assets)}
                              </div>
                              <div className="flex flex-col p-3">
                                <div className="flex flex-col gap-2 pb-3 border-b border-slate-200/80">
                                  <div className="font-semibold text-slate-700">Liabilities</div>
                                  {renderAccountItems(balanceSheetData?.liabilities)}
                                </div>
                                <div className="flex flex-col gap-2 pt-3">
                                  <div className="font-semibold text-slate-700">Equity</div>
                                  {renderAccountItems(balanceSheetData?.equity)}
                                </div>
                              </div>
                              <div className="flex flex-col gap-2 p-3">
                                <div className="font-semibold text-slate-700">Expenses</div>
                                {renderAccountItems(balanceSheetData?.expenses)}
                              </div>
                              <div className="flex flex-col gap-2 p-3">
                                <div className="font-semibold text-slate-700">Revenue</div>
                                {renderAccountItems(balanceSheetData?.revenue)}
                              </div>
                            </div>
                            {balanceSheetData?.snapshotAt && (
                              <div className="text-[10px] text-slate-400 mt-3 text-right">
                                Snapshot: {balanceSheetData.snapshotAt}
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                  <AccountPanel refreshTrigger={refreshTrigger} onOpenBalanceSheet={handleOpenBalanceSheet} />
                </div>
                <div className="flex flex-col gap-2">
                  <Tooltip
                    title={(
                      <div className="text-xs">
                        <div className="whitespace-nowrap">重置所有交易、帳務數據與Kafka，加速測試效率。</div>
                        <div className="whitespace-nowrap">Resets all trading/account data and Kafka to speed up testing.</div>
                      </div>
                    )}
                    overlayStyle={{ maxWidth: 'none' }}
                    overlayInnerStyle={{ maxWidth: 'none' }}
                  >
                    <button
                      onClick={handleResetData}
                      disabled={resetting}
                      className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-600 text-[10px] font-bold uppercase tracking-wider hover:bg-rose-500 hover:text-white transition-all active:scale-95 disabled:opacity-50"
                    >
                      <span className="w-2 h-2 rounded-full bg-rose-500 animate-pulse" />
                      {resetting ? 'Resetting...' : 'Reset System Data'}
                    </button>
                  </Tooltip>

                  <Tooltip
                    title={(
                      <div className="text-xs">
                        <div className="whitespace-nowrap">重載所有頁面數據，加速測試。</div>
                        <div className="whitespace-nowrap">Reloads all page data to speed up testing.</div>
                      </div>
                    )}
                    overlayStyle={{ maxWidth: 'none' }}
                    overlayInnerStyle={{ maxWidth: 'none' }}
                  >
                    <button
                      onClick={handleRefresh}
                      className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 text-[10px] font-bold uppercase tracking-wider hover:bg-emerald-500 hover:text-white transition-all active:scale-95"
                    >
                      <span className="text-xs">⟳</span>
                      Refresh Data
                    </button>
                  </Tooltip>
                </div>
              </div>
            </div>
          </div>
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
