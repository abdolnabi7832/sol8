/**
 * ==============================================================================
 * FOREX INTELLIGENCE SUITE — AUTHENTICATION & INSTITUTIONAL REPORTING MODULE
 * ==============================================================================
 * 1. AppAuth: Handles login state, bearer token storage, and UI status widget.
 * 2. AppReports: Renders Period Picker (24h, 7d, 30d, 90d, All), metrics cards,
 *    and direct stream download for institutional TXT and JSON reports.
 * ==============================================================================
 */

window.AppAuth = {
    token: localStorage.getItem('fx_auth_token') || null,
    currentUser: localStorage.getItem('fx_auth_user') || 'Guest Analyst',

    init() {
        this.updateHeaderUI();
        if (this.token) {
            this.verifyToken();
        } else {
            this.openAuthModal(true);
        }
    },

    updateHeaderUI() {
        const widget = document.getElementById('authHeaderWidget');
        if (!widget) return;

        if (this.token) {
            widget.innerHTML = `
                <div style="display: flex; align-items: center; gap: 0.5rem; background: rgba(16,185,129,0.12); border: 1px solid rgba(16,185,129,0.3); padding: 0.35rem 0.75rem; border-radius: 6px;">
                    <span style="display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #10b981; box-shadow: 0 0 6px #10b981;"></span>
                    <span style="font-size: 0.8rem; font-weight: 700; color: #34d399; font-family: monospace;">${this.currentUser.toUpperCase()}</span>
                    <button onclick="AppAuth.openChangeCredentialsModal()" style="background: rgba(56,189,248,0.15); border: 1px solid rgba(56,189,248,0.3); color: #38bdf8; font-size: 0.72rem; padding: 0.2rem 0.45rem; border-radius: 4px; cursor: pointer; margin-left: 0.35rem;" title="تغییر نام کاربری و کلمه عبور">⚙️ رمز ورود</button>
                    <button onclick="AppAuth.handleLogout()" style="background: transparent; border: none; color: #94a3b8; font-size: 0.8rem; cursor: pointer; margin-left: 0.25rem;" title="خروج">✕</button>
                </div>
            `;
        } else {
            widget.innerHTML = `
                <button id="btnHeaderAuth" class="btn-force-update" style="background: rgba(59,130,246,0.18); border-color: rgba(59,130,246,0.4); color: #93c5fd; font-weight: 700;" onclick="AppAuth.openAuthModal(true)">
                    🔒 SIGN IN (ورود)
                </button>
            `;
        }
    },

    openAuthModal(isMandatory = false) {
        const modal = document.getElementById('authModal');
        const err = document.getElementById('authErrorMessage');
        const btnClose = document.getElementById('authModalCloseBtn');
        if (err) err.style.display = 'none';
        if (btnClose) btnClose.style.display = isMandatory ? 'none' : 'block';
        if (modal) modal.style.display = 'flex';
    },

    closeAuthModal() {
        if (!this.token) {
            // Cannot bypass mandatory authentication
            return;
        }
        const modal = document.getElementById('authModal');
        if (modal) modal.style.display = 'none';
    },

    async handleLogin(e) {
        e.preventDefault();
        const username = document.getElementById('authUsernameInput').value;
        const password = document.getElementById('authPasswordInput').value;
        const err = document.getElementById('authErrorMessage');
        const btn = document.getElementById('btnSubmitLogin');

        btn.disabled = true;
        btn.textContent = 'AUTHENTICATING...';

        try {
            const resp = await fetch('/api/v1/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const data = await resp.json();

            if (resp.ok && data.token) {
                this.token = data.token;
                this.currentUser = data.username || username;
                localStorage.setItem('fx_auth_token', this.token);
                localStorage.setItem('fx_auth_user', this.currentUser);
                this.updateHeaderUI();
                const modal = document.getElementById('authModal');
                if (modal) modal.style.display = 'none';
                if (window.showToast) window.showToast('ورود با موفقیت انجام شد خوش آمدید! ✅', 'success');
            } else {
                err.style.display = 'block';
                err.textContent = data.message || 'نام کاربری یا کلمه عبور نادرست است.';
            }
        } catch (error) {
            err.style.display = 'block';
            err.textContent = 'ارتباط با سرور برقرار نشد. لطفاً از روشن بودن برنامه مطمئن شوید.';
        } finally {
            btn.disabled = false;
            btn.textContent = 'AUTHENTICATE & ENTER (ورود)';
        }
    },

    openChangeCredentialsModal() {
        const modal = document.getElementById('changeCredsModal');
        const err = document.getElementById('changeCredsError');
        const userInp = document.getElementById('changeCredsNewUser');
        if (userInp) userInp.value = this.currentUser;
        if (err) err.style.display = 'none';
        if (modal) modal.style.display = 'flex';
    },

    closeChangeCredentialsModal() {
        const modal = document.getElementById('changeCredsModal');
        if (modal) modal.style.display = 'none';
    },

    async handleChangeCredentials(e) {
        e.preventDefault();
        const curPass = document.getElementById('changeCredsCurrentPass').value;
        const newUser = document.getElementById('changeCredsNewUser').value;
        const newPass = document.getElementById('changeCredsNewPass').value;
        const confirmPass = document.getElementById('changeCredsConfirmPass').value;
        const err = document.getElementById('changeCredsError');
        const btn = document.getElementById('btnSubmitChangeCreds');

        if (newPass && newPass !== confirmPass) {
            err.style.display = 'block';
            err.textContent = 'تکرار رمز عبور جدید با آن مطابقت ندارد.';
            return;
        }

        btn.disabled = true;
        btn.textContent = 'در حال ذخیره‌سازی...';

        try {
            const resp = await fetch('/api/v1/auth/update-credentials', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.token}`
                },
                body: JSON.stringify({
                    currentPassword: curPass,
                    newUsername: newUser,
                    newPassword: newPass
                })
            });
            const data = await resp.json();

            if (resp.ok) {
                if (data.username) {
                    this.currentUser = data.username;
                    localStorage.setItem('fx_auth_user', this.currentUser);
                }
                this.updateHeaderUI();
                this.closeChangeCredentialsModal();
                if (window.showToast) window.showToast('اطلاعات ورود (نام کاربری / رمز عبور) با موفقیت تغییر یافت. ✅', 'success');
            } else {
                err.style.display = 'block';
                err.textContent = data.error || 'خطا در تغییر اطلاعات کاربری.';
            }
        } catch (e) {
            err.style.display = 'block';
            err.textContent = 'خطا در ارتباط با سرور.';
        } finally {
            btn.disabled = false;
            btn.textContent = '💾 ذخیره تغییرات';
        }
    },

    async verifyToken() {
        try {
            const resp = await fetch('/api/v1/auth/verify', {
                headers: { 'Authorization': `Bearer ${this.token}` }
            });
            if (!resp.ok) {
                this.handleLogout(false);
            }
        } catch (e) {
            // offline or net glitch
        }
    },

    handleLogout(showNotification = true) {
        if (this.token) {
            fetch('/api/v1/auth/logout', { method: 'POST' }).catch(() => {});
        }
        this.token = null;
        this.currentUser = 'Guest Analyst';
        localStorage.removeItem('fx_auth_token');
        localStorage.removeItem('fx_auth_user');
        this.updateHeaderUI();
        if (showNotification && window.showToast) window.showToast('از حساب کاربری خارج شدید.', 'info');
        this.openAuthModal(true);
    }
};

window.AppReports = {
    currentPeriod: '30d',

    init() {
        const mount = document.getElementById('reportsMountPoint');
        if (!mount) return;
        this.renderReportsView(mount);
    },

    renderReportsView(mount) {
        mount.innerHTML = `
            <div class="glass-panel" style="margin-bottom: 1.5rem;">
                <div class="panel-header" style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
                    <div>
                        <div class="panel-title" style="display: flex; align-items: center; gap: 0.5rem;">
                            <span>📊</span> Institutional Audit &amp; Performance Telemetry Report
                        </div>
                        <span class="panel-subtext font-mono">Anton Kreil 6-Pillar &middot; 7-Gate Risk Shield &middot; Closed &amp; Active Trade Audit</span>
                    </div>

                    <!-- PERIOD SELECTOR -->
                    <div style="display: flex; align-items: center; gap: 0.5rem; background: rgba(15,23,42,0.6); padding: 0.25rem 0.5rem; border-radius: 8px; border: 1px solid rgba(255,255,255,0.1);">
                        <span style="font-size: 0.75rem; color: #94a3b8; font-weight: 700;">PERIOD:</span>
                        <div class="btn-group-toggle" style="display: flex; gap: 0.25rem;">
                            <button class="btn-period ${this.currentPeriod === '24h' ? 'active' : ''}" onclick="AppReports.setPeriod('24h')">24H</button>
                            <button class="btn-period ${this.currentPeriod === '7d' ? 'active' : ''}" onclick="AppReports.setPeriod('7d')">7D</button>
                            <button class="btn-period ${this.currentPeriod === '30d' ? 'active' : ''}" onclick="AppReports.setPeriod('30d')">30D</button>
                            <button class="btn-period ${this.currentPeriod === '90d' ? 'active' : ''}" onclick="AppReports.setPeriod('90d')">90D</button>
                            <button class="btn-period ${this.currentPeriod === 'all' ? 'active' : ''}" onclick="AppReports.setPeriod('all')">ALL</button>
                        </div>
                    </div>
                </div>

                <div style="padding: 1.5rem;">
                    <!-- DOWNLOAD ACTION CARDS -->
                    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 1.25rem; margin-bottom: 1.75rem;">
                        <!-- TXT EXPORT -->
                        <div style="background: rgba(15,23,42,0.8); border: 1px solid rgba(6,182,212,0.3); border-radius: 10px; padding: 1.25rem; display: flex; flex-direction: column; justify-content: space-between;">
                            <div>
                                <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.5rem;">
                                    <span style="font-weight: 800; color: #38bdf8; font-size: 1.05rem;">📄 Text Audit Report (.txt)</span>
                                    <span style="background: rgba(56,189,248,0.15); color: #38bdf8; font-size: 0.7rem; font-weight: 800; padding: 0.2rem 0.5rem; border-radius: 4px;">HUMAN READABLE</span>
                                </div>
                                <p style="font-size: 0.85rem; color: #94a3b8; line-height: 1.4; margin-bottom: 1rem;">
                                    Formatted ASCII tables with summary KPIs, portfolio equity, active positions, 8 currency macro regimes, and closed journal trade logs.
                                </p>
                            </div>
                            <button onclick="AppReports.downloadReport('txt')" style="display: flex; align-items: center; justify-content: center; gap: 0.5rem; width: 100%; padding: 0.75rem; background: #0284c7; color: #ffffff; font-weight: 800; border: none; border-radius: 6px; cursor: pointer; transition: background 0.2s;">
                                ⬇ DOWNLOAD TXT REPORT
                            </button>
                        </div>

                        <!-- JSON EXPORT -->
                        <div style="background: rgba(15,23,42,0.8); border: 1px solid rgba(16,185,129,0.3); border-radius: 10px; padding: 1.25rem; display: flex; flex-direction: column; justify-content: space-between;">
                            <div>
                                <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.5rem;">
                                    <span style="font-weight: 800; color: #34d399; font-size: 1.05rem;">💾 Structured Dataset (.json)</span>
                                    <span style="background: rgba(16,185,129,0.15); color: #34d399; font-size: 0.7rem; font-weight: 800; padding: 0.2rem 0.5rem; border-radius: 4px;">MACHINE PARSABLE</span>
                                </div>
                                <p style="font-size: 0.85rem; color: #94a3b8; line-height: 1.4; margin-bottom: 1rem;">
                                    Complete raw telemetry JSON containing portfolio object, 336-series macro scores, 28-pair 7-gate ratings, and psychology audits.
                                </p>
                            </div>
                            <button onclick="AppReports.downloadReport('json')" style="display: flex; align-items: center; justify-content: center; gap: 0.5rem; width: 100%; padding: 0.75rem; background: #059669; color: #ffffff; font-weight: 800; border: none; border-radius: 6px; cursor: pointer; transition: background 0.2s;">
                                ⬇ DOWNLOAD JSON DATASET
                            </button>
                        </div>
                    </div>

                    <!-- PREVIEW METRICS CONTAINER -->
                    <div id="reportMetricsPreview" style="background: rgba(2,6,23,0.6); border: 1px solid rgba(255,255,255,0.08); border-radius: 8px; padding: 1.25rem;">
                        <div style="font-weight: 700; font-size: 0.9rem; color: #e2e8f0; margin-bottom: 0.75rem;">
                            Live Data Snapshot Preview (Selected Filter: <span style="color: #06b6d4;">${this.currentPeriod.toUpperCase()}</span>)
                        </div>
                        <div id="reportPreviewContent" style="color: #94a3b8; font-size: 0.85rem; font-family: monospace;">
                            Loading live metrics...
                        </div>
                    </div>
                </div>
            </div>
        `;

        this.injectStyles();
        this.loadPreviewMetrics();
    },

    injectStyles() {
        if (document.getElementById('reportsStyles')) return;
        const style = document.createElement('style');
        style.id = 'reportsStyles';
        style.textContent = `
            .btn-period {
                background: transparent;
                border: none;
                color: #94a3b8;
                font-size: 0.75rem;
                font-weight: 700;
                padding: 0.25rem 0.6rem;
                border-radius: 4px;
                cursor: pointer;
                transition: all 0.2s;
            }
            .btn-period:hover {
                color: #fff;
                background: rgba(255,255,255,0.1);
            }
            .btn-period.active {
                background: #06b6d4;
                color: #020617;
            }
        `;
        document.head.appendChild(style);
    },

    setPeriod(period) {
        this.currentPeriod = period;
        const mount = document.getElementById('reportsMountPoint');
        if (mount) this.renderReportsView(mount);
    },

    async loadPreviewMetrics() {
        const preview = document.getElementById('reportPreviewContent');
        if (!preview) return;

        try {
            const resp = await fetch(`/api/v1/bootstrap`);
            if (!resp.ok) throw new Error('Bootstrap telemetry unavailable');
            const data = await resp.json();

            const p = data.portfolio || {};
            const trades = data.activeTrades || [];
            const journal = data.tradeJournal || [];

            preview.innerHTML = `
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem; margin-bottom: 0.75rem;">
                    <div><span style="color: #64748b;">Account Balance:</span> <b style="color: #38bdf8;">$${Number(p.accountBalance || 0).toLocaleString()} USD</b></div>
                    <div><span style="color: #64748b;">Open Positions:</span> <b style="color: #f1f5f9;">${trades.length} active</b></div>
                    <div><span style="color: #64748b;">Historical Trades:</span> <b style="color: #f1f5f9;">${journal.length} records</b></div>
                    <div><span style="color: #64748b;">Cloud Host Target:</span> <b style="color: #a855f7;">Railway (Java 21 LTS)</b></div>
                </div>
                <div style="color: #64748b; font-size: 0.75rem;">
                    Ready for instant one-click download in selected format above.
                </div>
            `;
        } catch (e) {
            preview.innerHTML = `<span style="color: #fca5a5;">Telemetry preview unavailable (${e.message}). Export will still query database directly.</span>`;
        }
    },

    downloadReport(format) {
        const url = `/api/v1/report/export?period=${encodeURIComponent(this.currentPeriod)}&format=${encodeURIComponent(format)}`;
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `forex_audit_report_${this.currentPeriod}.${format}`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        if (window.showToast) window.showToast(`Downloading ${format.toUpperCase()} Report (${this.currentPeriod.toUpperCase()})...`, 'info');
    }
};

// Initialize modules upon DOM ready
document.addEventListener('DOMContentLoaded', () => {
    AppAuth.init();
    AppReports.init();
});
