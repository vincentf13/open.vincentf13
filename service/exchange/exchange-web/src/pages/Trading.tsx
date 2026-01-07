import { useEffect, useRef, useState } from 'react';

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
  const [isPaused, setIsPaused] = useState(false);
  const [isTabVisible, setIsTabVisible] = useState(true);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [instruments, setInstruments] = useState<InstrumentSummary[]>(() => getCachedInstrumentSummaries());
  const [selectedInstrumentId, setSelectedInstrumentId] = useState<string | null>(() => getCachedInstrumentId());

  const [balanceSheetOpen, setBalanceSheetOpen] = useState(false);
  const [balanceSheetLoading, setBalanceSheetLoading] = useState(false);
  const [balanceSheetData, setBalanceSheetData] = useState<AccountBalanceResponse | null>(null);
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

  useEffect(() => {
    if (balanceSheetOpen) {
      getBalanceSheet().then(res => { if (String(res?.code)==='0') setBalanceSheetData(res.data); });
      getPlatformAccounts().then(res => { if (String(res?.code)==='0') setPlatformAccountsData(res.data); });
    }
  }, [balanceSheetOpen, refreshTrigger]);

  useEffect(() => {
    if (accountJournalOpen && accountJournalData?.accountId) {
      getAccountJournals(accountJournalData.accountId).then(res => { if(String(res?.code)==='0') setAccountJournalData(res.data); });
    }
  }, [refreshTrigger]);

  useEffect(() => {
    if (platformAccountJournalOpen && platformAccountJournalData?.accountId) {
      getPlatformAccountJournals(platformAccountJournalData.accountId).then(res => { if(String(res?.code)==='0') setPlatformAccountJournalResponse(res.data); });
    }
  }, [refreshTrigger]);

  useEffect(() => {
    if (referenceJournalOpen && referenceJournalData?.referenceType && referenceJournalData?.referenceId) {
      getJournalsByReference(referenceJournalData.referenceType, referenceJournalData.referenceId).then(res => { if(String(res?.code)==='0') setReferenceJournalData(res.data); });
    }
  }, [refreshTrigger]);

  const handleRefresh = () => setRefreshTrigger(v => v + 1);
  const selectedInstrument = instruments.find(i => i.instrumentId === selectedInstrumentId) || instruments[0] || null;

  const handleOpenBalanceSheet = () => {
    setBalanceSheetOpen(true);
    setBalanceSheetLoading(true);
    getBalanceSheet().then(res => { if (String(res?.code)==='0') setBalanceSheetData(res.data); }).finally(() => setBalanceSheetLoading(false));
    getPlatformAccounts().then(res => { if (String(res?.code)==='0') setPlatformAccountsData(res.data); });
  };

  const handleCloseBalanceSheet = () => {
    setBalanceSheetOpen(false);
    setAccountJournalOpen(false);
    setPlatformAccountJournalOpen(false);
    setReferenceJournalOpen(false);
  };
  const handleCloseAccountJournal = () => setAccountJournalOpen(false);
  const handleClosePlatformAccountJournal = () => setPlatformAccountJournalOpen(false);
  const handleCloseReferenceJournals = () => setReferenceJournalOpen(false);

  const handleOpenAccountJournal = (accountId: number) => {
    setPlatformAccountJournalOpen(false);
    setReferenceJournalOpen(false);
    setAccountJournalOpen(true);
    setAccountJournalLoading(true);
    getAccountJournals(accountId).then(res => { if(String(res?.code)==='0') setAccountJournalData(res.data); }).finally(() => setAccountJournalLoading(false));
  };

  const handleOpenPlatformAccountJournal = (accountId: number) => {
    setAccountJournalOpen(false);
    setReferenceJournalOpen(false);
    setPlatformAccountJournalOpen(true);
    setPlatformAccountJournalLoading(true);
    getPlatformAccountJournals(accountId).then(res => { if(String(res?.code)==='0') setPlatformAccountJournalResponse(res.data); }).finally(() => setPlatformAccountJournalLoading(false));
  };

  const handleOpenReferenceJournals = (type: string, id: string) => {
    setReferenceJournalOpen(true);
    setReferenceJournalLoading(true);
    getJournalsByReference(type, id).then(res => { if(String(res?.code)==='0') setReferenceJournalData(res.data); }).finally(() => setReferenceJournalLoading(false));
  };

  const renderJournalRows = (items?: AccountJournalItem[], options?: { disableReferenceLink?: boolean }) => {
    const rows = items && items.length > 0 ? items : [null];
    const disableReferenceLink = options?.disableReferenceLink ?? false;
    return (
      <div className="overflow-x-auto">
        <table className="w-full text-[10px] text-slate-600 border-collapse table-fixed">
          <thead>
            <tr className="text-[9px] uppercase tracking-wider text-slate-400 border-b border-white/60 text-right whitespace-nowrap">
              <th className="py-1 pr-1 font-semibold text-left w-[100px]">journalId</th>
              <th className="py-1 pr-1 font-semibold text-left w-[60px]">userId</th>
              <th className="py-1 pr-1 font-semibold text-left w-[70px]">accountId</th>
              <th className="py-1 pr-1 font-semibold text-left w-[80px]">accCode</th>
              <th className="py-1 pr-1 font-semibold text-left w-[80px]">accName</th>
              <th className="py-1 pr-1 font-semibold text-left w-[60px]">category</th>
              <th className="py-1 pr-1 font-semibold text-left w-[50px]">asset</th>
              <th className="py-1 pr-1 font-semibold w-[80px]">amount</th>
              <th className="py-1 pr-1 font-semibold w-[60px]">direction</th>
              <th className="py-1 pr-1 font-semibold w-[85px]">balanceAfter</th>
              <th className="py-1 pr-1 font-semibold text-left w-[110px]">referenceType</th>
              <th className="py-1 pr-1 font-semibold text-left w-[120px]">
                <div className="flex items-center gap-1">
                  referenceId
                  <Tooltip 
                    title={(
                      <div className="text-[10px] leading-relaxed min-w-[240px]">
                        <p className="font-bold text-sky-400 border-b border-white/10 pb-1 mb-1">關聯查詢 (Reference Query)</p>
                        <p>點擊 ID 可查詢該筆業務操作（如撮合、手續費）產生的所有關聯帳戶分錄。</p>
                        <p className="text-slate-300 mt-1">Click to view all journal entries (e.g., matching, fees) related to this transaction.</p>
                      </div>
                    )}
                    overlayStyle={{ maxWidth: 'none' }}
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
              const isFrozen = refType === 'ORDER_FEE_FROZEN' || refType === 'ORDER_MARGIN_FROZEN';
              const highlightCredit = (category === 'ASSET' || category === 'EXPENSE') && direction === 'CREDIT';
              const highlightDebit = (category === 'LIABILITY' || category === 'EQUITY' || category === 'REVENUE') && direction === 'DEBIT';
              const highlightClass = (!isFrozen && (highlightCredit || highlightDebit)) ? 'text-rose-600 font-medium' : 'text-slate-700';
              return (
                <tr key={index} className="text-right align-top whitespace-nowrap">
                  <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.journalId ?? '-')}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.userId ?? '-')}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.accountId ?? '-')}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.accountCode ?? '-')}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.accountName ?? '-')}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.category ?? '-')}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.asset ?? '-')}</td>
                  <td className={`py-1 pr-1 font-mono ${highlightClass} overflow-hidden`}>{String(item?.amount ?? '-')}</td>
                  <td className={`py-1 pr-1 ${highlightClass} overflow-hidden`}>{String(item?.direction ?? '-')}</td>
                  <td className={`py-1 pr-1 font-mono ${highlightClass} overflow-hidden`}>{String(item?.balanceAfter ?? '-')}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.referenceType ?? '-')}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden">
                    {item?.referenceId ? (
                      disableReferenceLink ? <span>{String(item.referenceId)}</span> : (
                        <button onClick={() => handleOpenReferenceJournals(item.referenceType, item.referenceId)} className="text-sky-500 hover:text-sky-600 underline">
                          {String(item.referenceId)}
                        </button>
                      )
                    ) : '-'}
                  </td>
                  <td className="py-1 pr-1 font-mono overflow-hidden">{String(item?.seq ?? '-')}</td>
                  <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.description ?? '-')}</td>
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

  const renderPlatformJournalRows = (items?: PlatformJournalItem[], options?: { disableReferenceLink?: boolean }) => {
    const rows = items && items.length > 0 ? items : [null];
    const disableReferenceLink = options?.disableReferenceLink ?? false;
    return (
      <div className="overflow-x-auto">
        <table className="w-full text-[10px] text-slate-600 border-collapse table-fixed">
          <thead>
            <tr className="text-[9px] uppercase tracking-wider text-slate-400 border-b border-white/60 text-right whitespace-nowrap">
              <th className="py-1 pr-1 font-semibold text-left w-[100px]">journalId</th>
              <th className="py-1 pr-1 font-semibold text-left w-[100px]">accountId</th>
              <th className="py-1 pr-1 font-semibold text-left w-[55px]">category</th>
              <th className="py-1 pr-1 font-semibold text-left w-[50px]">asset</th>
              <th className="py-1 pr-1 font-semibold w-[100px]">amount</th>
              <th className="py-1 pr-1 font-semibold w-[60px]">direction</th>
              <th className="py-1 pr-1 font-semibold w-[85px]">balanceAfter</th>
              <th className="py-1 pr-1 font-semibold text-left w-[110px]">referenceType</th>
              <th className="py-1 pr-1 font-semibold text-left w-[120px]">
                <div className="flex items-center gap-1">
                  referenceId
                  <Tooltip 
                    title={(
                      <div className="text-[10px] leading-relaxed min-w-[240px]">
                        <p className="font-bold text-indigo-400 border-b border-white/10 pb-1 mb-1">關聯查詢 (Reference Query)</p>
                        <p>點擊 ID 可查詢該筆業務操作產生的所有關聯帳戶分錄。</p>
                        <p className="text-slate-300 mt-1">Click to view all journal entries related to this transaction.</p>
                      </div>
                    )}
                    overlayStyle={{ maxWidth: 'none' }}
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
            {rows.map((item, index) => (
              <tr key={index} className="text-right align-top whitespace-nowrap">
                <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.journalId ?? '-')}</td>
                <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.accountId ?? '-')}</td>
                <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.category ?? '-')}</td>
                <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.asset ?? '-')}</td>
                <td className="py-1 pr-1 font-mono text-slate-700 overflow-hidden">{String(item?.amount ?? '-')}</td>
                <td className="py-1 pr-1 text-slate-700 overflow-hidden">{String(item?.direction ?? '-')}</td>
                <td className="py-1 pr-1 font-mono text-slate-700 overflow-hidden">{String(item?.balanceAfter ?? '-')}</td>
                <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.referenceType ?? '-')}</td>
                <td className="py-1 pr-1 text-left overflow-hidden">
                  {item?.referenceId ? (
                    disableReferenceLink ? <span>{String(item.referenceId)}</span> : (
                      <button onClick={() => handleOpenReferenceJournals(item.referenceType, item.referenceId)} className="text-sky-500 hover:text-sky-600 underline">
                        {String(item.referenceId)}
                      </button>
                    )
                  ) : '-'}
                </td>
                <td className="py-1 pr-1 font-mono overflow-hidden">{String(item?.seq ?? '-')}</td>
                <td className="py-1 pr-1 text-left overflow-hidden">{String(item?.description ?? '-')}</td>
                <td className="py-1 pr-1 font-mono text-[9px] overflow-hidden">{String(item?.eventTime ?? '-')}</td>
                <td className="py-1 pr-1 font-mono text-[9px] overflow-hidden">{String(item?.createdAt ?? '-')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  const renderAccountItems = (items?: AccountBalanceItem[]) => {
    const rows = items && items.length > 0 ? items : [null];
    return (
      <div className="overflow-x-auto">
        <table className="w-full text-[10px] text-slate-600 border-collapse table-fixed">
          <thead>
            <tr className="text-[9px] uppercase tracking-wider text-slate-400 border-b border-white/60 text-right whitespace-nowrap">
              <th className="py-1 pr-1 font-semibold text-left w-[90px]">accountId</th>
              <th className="py-1 pr-1 font-semibold text-left w-[60px]">accCode</th>
              <th className="py-1 pr-1 font-semibold text-left w-[75px]">accName</th>
              <th className="py-1 pr-1 font-semibold text-left w-[55px]">category</th>
              <th className="py-1 pr-1 font-semibold text-left w-[55px]">instId</th>
              <th className="py-1 pr-1 font-semibold text-left w-[50px]">asset</th>
              <th className="py-1 pr-1 font-semibold w-[80px]">balance</th>
              <th className="py-1 pr-1 font-semibold w-[80px]">available</th>
              <th className="py-1 pr-1 font-semibold w-[80px]">reserved</th>
              <th className="py-1 pr-1 font-semibold w-[30px]">ver</th>
              <th className="py-1 pr-1 font-semibold w-[95px]">createdAt</th>
              <th className="py-1 pr-1 font-semibold w-[95px]">updatedAt</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/40">
            {rows.map((item, index) => (
              <tr key={index} className="text-right whitespace-nowrap">
                <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">
                  {item?.accountId != null ? (
                    <button onClick={() => handleOpenAccountJournal(Number(item.accountId))} className="text-sky-500 underline">{item.accountId}</button>
                  ) : '-'}
                </td>
                <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{String(item?.accountCode ?? '-')}</td>
                <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{String(item?.accountName ?? '-')}</td>
                <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{String(item?.category ?? '-')}</td>
                <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{String(item?.instrumentId ?? '-')}</td>
                <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{String(item?.asset ?? '-')}</td>
                <td className="py-1 pr-1 font-mono text-slate-700 overflow-hidden text-ellipsis">{String(item?.balance ?? '-')}</td>
                <td className="py-1 pr-1 font-mono text-slate-700 overflow-hidden text-ellipsis">{String(item?.available ?? '-')}</td>
                <td className="py-1 pr-1 font-mono text-slate-700 overflow-hidden text-ellipsis">{String(item?.reserved ?? '-')}</td>
                <td className="py-1 pr-1 font-mono overflow-hidden text-ellipsis">{String(item?.version ?? '-')}</td>
                <td className="py-1 pr-1 font-mono text-[9px] overflow-hidden text-ellipsis">{String(item?.createdAt ?? '-')}</td>
                <td className="py-1 pr-1 font-mono text-[9px] overflow-hidden text-ellipsis">{String(item?.updatedAt ?? '-')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  const renderPlatformAccountRows = (items?: PlatformAccountItem[]) => {
    const rows = items && items.length > 0 ? items : [null];
    return (
      <div className="overflow-x-auto">
        <table className="w-full text-[10px] text-slate-600 border-collapse table-fixed">
          <thead>
            <tr className="text-[9px] uppercase tracking-wider text-slate-400 border-b border-white/60 text-right whitespace-nowrap">
              <th className="py-1 pr-1 font-semibold text-left w-[100px]">accountId</th>
              <th className="py-1 pr-1 font-semibold text-left w-[60px]">accCode</th>
              <th className="py-1 pr-1 font-semibold text-left w-[75px]">accName</th>
              <th className="py-1 pr-1 font-semibold text-left w-[55px]">category</th>
              <th className="py-1 pr-1 font-semibold text-left w-[50px]">asset</th>
              <th className="py-1 pr-1 font-semibold w-[85px]">balance</th>
              <th className="py-1 pr-1 font-semibold w-[30px]">ver</th>
              <th className="py-1 pr-1 font-semibold w-[100px]">createdAt</th>
              <th className="py-1 pr-1 font-semibold w-[100px]">updatedAt</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/40">
            {rows.map((item, index) => (
              <tr key={index} className="text-right whitespace-nowrap">
                <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">
                  {item?.accountId != null ? (
                    <button onClick={() => handleOpenPlatformAccountJournal(Number(item.accountId))} className="text-sky-500 underline">{item.accountId}</button>
                  ) : '-'}
                </td>
                <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{item?.accountCode}</td>
                <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{item?.accountName}</td>
                <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{item?.category}</td>
                <td className="py-1 pr-1 text-left overflow-hidden text-ellipsis">{item?.asset}</td>
                <td className="py-1 pr-1 font-mono text-slate-700 overflow-hidden text-ellipsis">{item?.balance}</td>
                <td className="py-1 pr-1 font-mono overflow-hidden text-ellipsis">{item?.version}</td>
                <td className="py-1 pr-1 font-mono text-[9px] overflow-hidden text-ellipsis">{item?.createdAt}</td>
                <td className="py-1 pr-1 font-mono text-[9px] overflow-hidden text-ellipsis">{item?.updatedAt}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  };

  const handleLogout = () => { localStorage.removeItem('accessToken'); setAuthOpen(true); };
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

  return (
    <div className="flex h-screen flex-col bg-slate-50 overflow-hidden text-slate-900">
      <header className="flex h-14 items-center justify-between border-b border-white bg-white/80 px-6 backdrop-blur-md shadow-sm z-50">
        <div className="flex items-center gap-4">
          <div className="flex h-8 w-8 items-center justify-center rounded-xl bg-gradient-to-br from-sky-500 to-indigo-600 text-white font-black italic shadow-lg">LF</div>
          <h1 className="text-lg font-bold">Liquid Flow <span className="font-light text-slate-400">| Exchange</span></h1>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => setIsPaused(!isPaused)} className={`px-3 py-1.5 rounded-lg border text-[10px] font-bold uppercase transition-all ${isPaused ? 'bg-amber-500/20 border-amber-500/40 text-amber-600' : 'bg-slate-500/10 border-slate-500/20 text-slate-500'}`}>{isPaused ? 'Paused' : 'Syncing'}</button>
          <button onClick={handleResetData} disabled={resetting} className="px-3 py-1.5 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-600 text-[10px] font-bold uppercase hover:bg-rose-500 hover:text-white disabled:opacity-50">{resetting ? 'Resetting...' : 'Reset Data'}</button>
          <button onClick={handleRefresh} className="px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 text-[10px] font-bold uppercase hover:bg-emerald-500 hover:text-white">Refresh</button>
          <button onClick={handleLogout} className="ml-4 rounded-lg border border-slate-200 bg-white px-4 py-1.5 text-[10px] font-bold uppercase text-slate-600 hover:bg-slate-50">Switch Account</button>
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
                <OrderBook selectedInstrumentId={selectedInstrumentId} refreshTrigger={refreshTrigger} isPaused={isPaused} />
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
                      {referenceJournalOpen ? `Related Journals` : (accountJournalOpen ? `Account Journal` : `Platform Journal`)}
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
                        <div className="space-y-6">
                          <div>
                            <div className="text-[10px] font-bold text-slate-400 mb-2 uppercase flex items-center gap-2">
                              <div className="w-1 h-1 rounded-full bg-slate-400"></div>
                              User Journals
                            </div>
                            {renderJournalRows(referenceJournalData?.accountJournals, { disableReferenceLink: true })}
                          </div>
                          <div>
                            <div className="text-[10px] font-bold text-slate-400 mb-2 uppercase flex items-center gap-2">
                              <div className="w-1 h-1 rounded-full bg-slate-400"></div>
                              Platform Journals
                            </div>
                            {renderPlatformJournalRows(referenceJournalData?.platformJournals, { disableReferenceLink: true })}
                          </div>
                        </div>
                      )
                    ) : (
                      <>
                        {accountJournalOpen && (accountJournalLoading && !accountJournalData ? <div className="p-8 text-center text-xs text-slate-400">Loading...</div> : renderJournalRows(accountJournalData?.journals))}
                        {platformAccountJournalOpen && (platformAccountJournalLoading && !platformAccountJournalData ? <div className="p-8 text-center text-xs text-slate-400">Loading...</div> : renderPlatformJournalRows(platformAccountJournalData?.journals))}
                      </>
                    )}
                  </div>
                </div>
              </div>
            )}
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-sm font-bold uppercase tracking-widest text-slate-800">Balance Sheet</h2>
              <button onClick={handleCloseBalanceSheet} className="h-8 w-8 flex items-center justify-center rounded-full border border-white/60 bg-white/70 text-slate-500 hover:text-slate-700">X</button>
            </div>
            <div className="flex-1 overflow-y-auto">
              <div className="space-y-12">
                {/* User Ledger */}
                <section>
                  <div className="flex items-center gap-2 mb-4 border-b-2 border-sky-100 pb-1">
                    <div className="text-xs font-black uppercase tracking-widest text-sky-600">User Ledger</div>
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
                                <li><span className="font-semibold text-rose-600">Liabilities (負債)</span>: 用戶對平台的欠款。</li>
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
                                <li><span className="font-semibold text-rose-600">Liabilities</span>: Debts or loans owed to platform.</li>
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
                      overlayStyle={{ maxWidth: 'none' }}
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
                    <div className="text-xs font-black uppercase tracking-widest text-indigo-600">Exchange Ledger</div>
                    <Tooltip 
                      title={(
                        <div className="text-[10px] leading-relaxed min-w-[520px]">
                          <div className="grid grid-cols-2 gap-6">
                            {/* Chinese Column */}
                            <div className="space-y-2 text-slate-700">
                              <p className="font-bold text-indigo-600 border-b border-slate-200 pb-1">交易所內部記帳</p>
                              <p>交易所作為託管方，其內部的 <span className="text-indigo-600 font-semibold">資產</span> 必須恆等於對所有用戶的 <span className="text-indigo-600 font-semibold">總負債</span>。</p>
                              <p>這裡展示的是平台帳戶（庫存、手續費收入等）與整體系統平衡狀態。</p>
                              <div className="pt-1 border-t border-slate-200">
                                <p className="font-bold text-slate-600">分錄 (Journal Entries)</p>
                                <p>系統自動化生成分錄，記錄每一分錢的流向。分錄是審計的基礎，確保平台資產與用戶權益隨時對齊。</p>
                              </div>
                            </div>
                            {/* English Column */}
                            <div className="space-y-2 text-slate-700">
                              <p className="font-bold text-indigo-600 border-b border-slate-200 pb-1">Exchange Accounting</p>
                              <p>As a custodian, the exchange's <span className="text-indigo-500 font-semibold">Assets</span> must always equal its <span className="text-indigo-500 font-semibold">Total Liabilities</span> to users.</p>
                              <p>This section displays platform accounts (inventory, fees) and overall system equilibrium.</p>
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
                      overlayStyle={{ maxWidth: 'none' }}
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
          </div>
        </div>
      )}

      <AuthModal open={authOpen} onCancel={() => setAuthOpen(false)} onLoginSuccess={handleRefresh} />
    </div>
  );
}
