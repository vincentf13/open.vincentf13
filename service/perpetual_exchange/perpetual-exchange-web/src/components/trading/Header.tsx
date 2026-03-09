import {Dropdown, message} from 'antd';

import {logout} from '../../api/auth';

type HeaderProps = {
    onLogout: () => void;
};

export default function Header({onLogout}: HeaderProps) {
    const handleLogout = async () => {
        try {
            await logout();
        } catch (error: any) {
            message.error(error?.response?.data?.message || '登出失敗');
        } finally {
            localStorage.removeItem('accessToken');
            onLogout();
        }
    };

    return (
        <header
            id="liquid-flow-header"
            className="flex flex-wrap items-center justify-between gap-4 px-6 py-4 border-b border-white/20 bg-white/10 backdrop-blur-md"
        >
            <div className="flex items-center gap-4">
                <div
                    className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/50 bg-gradient-to-br from-white/60 to-white/20 shadow-sm backdrop-blur-md">
                    <div
                        className="h-5 w-5 rounded-full bg-gradient-to-tr from-blue-500 to-cyan-400 shadow-inner ring-2 ring-white/50"/>
                </div>
                <div>
                    <h1 className="text-xl font-bold tracking-tight text-slate-800 flex items-center gap-2">
                        Liquid Flow
                        <span
                            className="rounded-full bg-blue-100/50 px-2 py-0.5 text-[9px] font-bold text-blue-600 uppercase tracking-wider border border-blue-200/50">Beta</span>
                    </h1>
                </div>
            </div>

            <div className="flex items-center gap-4">
                <div className="flex items-center gap-3">
                    <div className="text-right hidden sm:block">
                        <div className="text-xs font-medium text-slate-500">Portfolio Value</div>
                        <div className="text-sm font-bold text-slate-800">
                            -- <span className="text-[11px] font-medium text-slate-500">USDT</span>
                        </div>
                    </div>
                    <Dropdown
                        trigger={['click']}
                        menu={{
                            items: [{key: 'logout', label: 'Log out'}],
                            onClick: ({key}) => {
                                if (key === 'logout') {
                                    void handleLogout();
                                }
                            },
                        }}
                    >
                        <div
                            className="h-10 w-10 rounded-xl bg-gradient-to-br from-slate-700 to-slate-900 shadow-lg border border-white/20 flex items-center justify-center text-white font-bold text-xs cursor-pointer hover:scale-105 transition-transform"
                            role="button"
                            tabIndex={0}
                        >
                            VF
                        </div>
                    </Dropdown>
                </div>
            </div>
        </header>
    );
}
