// ==============================================================================
// FOREX INTELLIGENCE SUITE — REAL-TIME SERVER & CLOUD TELEMETRY ENGINE
// Live CPU, RAM, JVM Heap, Disk, Uptime, Sparklines & MT5 Connection Monitor
// ==============================================================================

const ServerTelemetryEngine = {
    pollingInterval: null,
    mountPointId: 'serverTelemetryMountPoint',
    lastMetrics: null,

    init() {
        this.fetchMetrics();
        if (!this.pollingInterval) {
            this.pollingInterval = setInterval(() => this.fetchMetrics(), 3000);
        }
    },

    async fetchMetrics() {
        try {
            const resp = await fetch('/api/v1/telemetry/server-metrics');
            if (resp.ok) {
                const data = await resp.json();
                this.lastMetrics = data;
                this.render();
                this.updateHeaderPill(data);
            }
        } catch (e) {
            // silent retry
        }
    },

    updateHeaderPill(data) {
        const pill = document.getElementById('mt5HeaderPill');
        if (pill && data.mt5) {
            pill.innerHTML = `
                <span class="sync-dot" style="background: #10b981; box-shadow: 0 0 8px #10b981;"></span>
                <span>MT5: #${data.mt5.account} (ONLINE)</span>
            `;
        }
    },

    renderSparklineSvg(history, color = '#38bdf8', maxVal = 100) {
        if (!history || history.length === 0) return '';
        const w = 180;
        const h = 42;
        const pad = 4;
        const pts = history.map((val, idx) => {
            const x = pad + (idx / Math.max(1, history.length - 1)) * (w - 2 * pad);
            const clamped = Math.max(0, Math.min(maxVal, val));
            const y = (h - pad) - (clamped / maxVal) * (h - 2 * pad);
            return `${x.toFixed(1)},${y.toFixed(1)}`;
        }).join(' ');

        return `
            <svg viewBox="0 0 ${w} ${h}" style="width: 100%; height: 42px; display: block; overflow: visible;">
                <polyline points="${pts}" fill="none" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <circle cx="${w - pad}" cy="${(h - pad) - (Math.min(maxVal, history[history.length - 1] || 0) / maxVal) * (h - 2 * pad)}" r="3" fill="${color}"/>
            </svg>
        `;
    },

    render() {
        const el = document.getElementById(this.mountPointId);
        if (!el || !this.lastMetrics) return;

        const m = this.lastMetrics;
        const cpuCol = m.cpuUsagePercent > 80 ? '#f43f5e' : (m.cpuUsagePercent > 50 ? '#f59e0b' : '#38bdf8');
        const ramCol = m.ramUsagePercent > 85 ? '#f43f5e' : (m.ramUsagePercent > 70 ? '#f59e0b' : '#10b981');
        const heapCol = m.heapUsagePercent > 85 ? '#f43f5e' : '#a855f7';

        el.innerHTML = `
            <div style="background: rgba(15,23,42,0.85); border: 1px solid rgba(148,163,184,0.18); border-radius: 10px; padding: 1.1rem; margin-bottom: 1.25rem; box-shadow: 0 4px 20px rgba(0,0,0,0.35);">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; border-bottom: 1px solid rgba(148,163,184,0.12); padding-bottom: 0.75rem;">
                    <div style="display: flex; align-items: center; gap: 0.6rem;">
                        <span style="font-size: 1.1rem;">⚡</span>
                        <div>
                            <span style="font-size: 0.95rem; font-weight: 800; color: #f8fafc;">Live Server Hardware & Cloud Telemetry</span>
                            <span style="font-size: 0.72rem; color: #94a3b8; margin-left: 0.5rem; font-family: monospace;">(${m.cloudRegion} · ${m.osName} · Java ${m.javaVersion})</span>
                        </div>
                    </div>
                    <div style="display: flex; align-items: center; gap: 0.5rem;">
                        <span style="display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #10b981; box-shadow: 0 0 8px #10b981;"></span>
                        <span style="font-size: 0.72rem; font-weight: 800; color: #34d399; font-family: monospace;">REALTIME 3s REFRESH</span>
                    </div>
                </div>

                <!-- 4 Live Metric Cards -->
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 0.85rem; margin-bottom: 1rem;">
                    
                    <!-- 1. CPU Usage -->
                    <div style="background: rgba(30,41,59,0.7); border: 1px solid rgba(56,189,248,0.25); border-radius: 8px; padding: 0.85rem;">
                        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.35rem;">
                            <span style="font-size: 0.75rem; font-weight: 700; color: #94a3b8;">SYSTEM CPU LOAD</span>
                            <span style="font-size: 0.68rem; color: #38bdf8; font-family: monospace;">${m.processorsCount} Cores</span>
                        </div>
                        <div style="display: flex; align-items: baseline; gap: 0.4rem; margin-bottom: 0.4rem;">
                            <span style="font-size: 1.6rem; font-weight: 900; color: ${cpuCol}; font-family: monospace;">${m.cpuUsagePercent}%</span>
                            <span style="font-size: 0.72rem; color: #64748b;">(App: ${m.processCpuPercent}%)</span>
                        </div>
                        ${this.renderSparklineSvg(m.cpuHistory, '#38bdf8', 100)}
                    </div>

                    <!-- 2. RAM Usage -->
                    <div style="background: rgba(30,41,59,0.7); border: 1px solid rgba(16,185,129,0.25); border-radius: 8px; padding: 0.85rem;">
                        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.35rem;">
                            <span style="font-size: 0.75rem; font-weight: 700; color: #94a3b8;">SERVER RAM USAGE</span>
                            <span style="font-size: 0.68rem; color: #10b981; font-family: monospace;">${m.ramUsedMb} / ${m.ramTotalMb} MB</span>
                        </div>
                        <div style="display: flex; align-items: baseline; gap: 0.4rem; margin-bottom: 0.4rem;">
                            <span style="font-size: 1.6rem; font-weight: 900; color: ${ramCol}; font-family: monospace;">${m.ramUsagePercent}%</span>
                            <span style="font-size: 0.72rem; color: #64748b;">Available</span>
                        </div>
                        ${this.renderSparklineSvg(m.ramHistory, '#10b981', 100)}
                    </div>

                    <!-- 3. JVM Heap Allocation -->
                    <div style="background: rgba(30,41,59,0.7); border: 1px solid rgba(168,85,247,0.25); border-radius: 8px; padding: 0.85rem;">
                        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.35rem;">
                            <span style="font-size: 0.75rem; font-weight: 700; color: #94a3b8;">JVM HEAP & DISK</span>
                            <span style="font-size: 0.68rem; color: #c084fc; font-family: monospace;">Disk: ${m.diskUsedGb} / ${m.diskTotalGb} GB</span>
                        </div>
                        <div style="display: flex; align-items: baseline; gap: 0.4rem; margin-bottom: 0.4rem;">
                            <span style="font-size: 1.6rem; font-weight: 900; color: ${heapCol}; font-family: monospace;">${m.heapUsedMb} MB</span>
                            <span style="font-size: 0.72rem; color: #64748b;">/ ${m.heapMaxMb} MB Max</span>
                        </div>
                        <div style="width: 100%; height: 6px; background: rgba(148,163,184,0.15); border-radius: 3px; overflow: hidden; margin-top: 0.8rem;">
                            <div style="width: ${m.heapUsagePercent}%; height: 100%; background: linear-gradient(90deg, #a855f7, #c084fc); border-radius: 3px;"></div>
                        </div>
                        <div style="display: flex; justify-content: space-between; margin-top: 0.4rem; font-size: 0.68rem; color: #94a3b8; font-family: monospace;">
                            <span>Heap: ${m.heapUsagePercent}%</span>
                            <span>Threads: ${m.activeThreads}</span>
                        </div>
                    </div>

                    <!-- 4. Uptime & MT5 Status -->
                    <div style="background: rgba(30,41,59,0.7); border: 1px solid rgba(245,158,11,0.25); border-radius: 8px; padding: 0.85rem;">
                        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.35rem;">
                            <span style="font-size: 0.75rem; font-weight: 700; color: #94a3b8;">SERVER UPTIME</span>
                            <span class="inst-badge inst-badge-bull font-mono" style="font-size: 0.62rem;">ONLINE</span>
                        </div>
                        <div style="font-size: 1.25rem; font-weight: 900; color: #fbbf24; font-family: monospace; margin-bottom: 0.45rem;">
                            ⏱️ ${m.uptimeFormatted}
                        </div>
                        <div style="border-top: 1px solid rgba(148,163,184,0.12); padding-top: 0.45rem; font-size: 0.72rem;">
                            <div style="display: flex; justify-content: space-between; color: #e2e8f0; font-family: monospace;">
                                <span>MT5 Account:</span>
                                <strong style="color: #38bdf8;">#${m.mt5.account}</strong>
                            </div>
                            <div style="display: flex; justify-content: space-between; color: #e2e8f0; font-family: monospace; margin-top: 2px;">
                                <span>Broker Ping:</span>
                                <strong style="color: #34d399;">${m.mt5.pingMs} ms</strong>
                            </div>
                        </div>
                    </div>

                </div>

                <!-- MT5 Gateway Status Banner -->
                <div style="background: rgba(16,185,129,0.08); border: 1px solid rgba(16,185,129,0.28); border-radius: 6px; padding: 0.65rem 0.9rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem;">
                    <div style="display: flex; align-items: center; gap: 0.6rem;">
                        <span style="display: inline-block; width: 9px; height: 9px; border-radius: 50%; background: #10b981; box-shadow: 0 0 10px #10b981;"></span>
                        <span style="font-size: 0.78rem; font-weight: 800; color: #34d399; font-family: monospace;">MT5 LIVE GATEWAY & BROKER SYNC ACTIVE</span>
                        <span style="font-size: 0.74rem; color: #cbd5e1;">· Account: <strong style="color: #f8fafc;">#${m.mt5.account}</strong> (${m.mt5.server}) · Balance: <strong style="color: #38bdf8;">${m.mt5.balance}</strong> · Equity: <strong style="color: #34d399;">${m.mt5.equity}</strong></span>
                    </div>
                    <div style="display: flex; gap: 0.4rem;">
                        <button onclick="AppRouter.navigateTo('page-telegram-cockpit')" style="background: rgba(56,189,248,0.18); border: 1px solid rgba(56,189,248,0.4); color: #38bdf8; font-size: 0.7rem; font-weight: 800; padding: 3px 8px; border-radius: 4px; cursor: pointer;">
                            📱 Open Telegram & MT5 Cockpit →
                        </button>
                    </div>
                </div>

            </div>
        `;
    }
};

document.addEventListener('DOMContentLoaded', () => {
    ServerTelemetryEngine.init();
});
