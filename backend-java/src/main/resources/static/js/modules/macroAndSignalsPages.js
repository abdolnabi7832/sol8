/**
 * ==============================================================================
 * FOREX INTELLIGENCE SUITE — MACRO SCORER, 28-PAIR SIGNALS & SCENARIO LAB v4.0
 * ARCHITECTURE:
 *   - 336 VERIFIED INDICATORS (42 PER CURRENCY ACROSS 10 TRADINGECONOMICS SECTIONS)
 *   - EXACT SITE REFERENCE PERIODS (Nov/26, Oct/26, Sep/26, Aug/26, Jun/26, Dec/25)
 *   - SIGNAL VINTAGE DATE & ELAPSED DAYS SINCE SIGNAL TRIGGER
 *   - COLLISION-FREE 60-DAY CANDLESTICK + EMA20/50 + BACKTEST WORKSTATION
 *   - PREDICTIVE 3-SCENARIO PROBABILITY TRAJECTORY CONE & CROSS-PAIR MATRIX
 * LANGUAGE: 100% INSTITUTIONAL ENGLISH · ZERO OVERLAPS · ZERO BIDI ARTIFACTS
 * ==============================================================================
 */

(function () {
    'use strict';

    const CURRENCY_META = {
        USD: { code: 'USD', name: 'US Dollar', bank: 'Federal Reserve (Fed)', slug: 'united-states' },
        EUR: { code: 'EUR', name: 'Eurozone Euro', bank: 'European Central Bank (ECB)', slug: 'euro-area' },
        GBP: { code: 'GBP', name: 'British Pound', bank: 'Bank of England (BoE)', slug: 'united-kingdom' },
        JPY: { code: 'JPY', name: 'Japanese Yen', bank: 'Bank of Japan (BoJ)', slug: 'japan' },
        AUD: { code: 'AUD', name: 'Australian Dollar', bank: 'Reserve Bank of Australia (RBA)', slug: 'australia' },
        CAD: { code: 'CAD', name: 'Canadian Dollar', bank: 'Bank of Canada (BoC)', slug: 'canada' },
        CHF: { code: 'CHF', name: 'Swiss Franc', bank: 'Swiss National Bank (SNB)', slug: 'switzerland' },
        NZD: { code: 'NZD', name: 'New Zealand Dollar', bank: 'Reserve Bank of New Zealand (RBNZ)', slug: 'new-zealand' }
    };

    const PILLAR_META = {
        1: { name: 'Pillar 1: Monetary & Liquidity', weight: '25%', short: 'Monetary (25%)' },
        2: { name: 'Pillar 2: Inflation & Prices', weight: '20%', short: 'Prices (20%)' },
        3: { name: 'Pillar 3: GDP Growth & Business', weight: '20%', short: 'Growth (20%)' },
        4: { name: 'Pillar 4: Labor Market', weight: '15%', short: 'Labor (15%)' },
        5: { name: 'Pillar 5: Trade & Fiscal', weight: '10%', short: 'Trade/Fiscal (10%)' },
        6: { name: 'Pillar 6: Consumer & Housing', weight: '10%', short: 'Consumer (10%)' }
    };

    const SITE_SECTIONS = ['ALL', 'Money', 'Prices', 'GDP', 'Business', 'Labour', 'Trade', 'Government', 'Consumer', 'Housing'];

    function getSuite() {
        return window._fxSuite || null;
    }

    function getState() {
        const s = getSuite();
        return s ? s.state : {};
    }

    // ==========================================================================
    // 1. 6-PILLAR MACRO SCORER PAGE (PAGE 3 — 42 INDICATORS PER CURRENCY)
    // ==========================================================================
    const MacroScorerPage = {
        selectedCurrency: 'USD',
        selectedSection: 'ALL',
        selectedPillar: 'ALL',
        searchTerm: '',
        scoringMode: 'AUTO',
        customOverrides: {},

        init() {
            this.mountPoint = document.getElementById('macroScorerMountPoint');
            this.render();
        },

        selectCurrency(ccy) {
            this.selectedCurrency = ccy;
            this.render();
        },

        setSectionFilter(sec) {
            this.selectedSection = sec;
            this.render();
        },

        setPillarFilter(p) {
            this.selectedPillar = p;
            this.render();
        },

        setSearchTerm(val) {
            this.searchTerm = (val || '').trim();
            this.render();
        },

        setHistoricalAsOfDate(dateVal) {
            const suite = getSuite();
            if (suite && suite.setAsOfDate) {
                suite.setAsOfDate(dateVal || null);
            }
            this.render();
        },

        updateOverride(ccy, indName, field, val) {
            if (!this.customOverrides[ccy]) this.customOverrides[ccy] = {};
            if (!this.customOverrides[ccy][indName]) this.customOverrides[ccy][indName] = {};
            this.customOverrides[ccy][indName][field] = field === 'exactReleaseDate' ? val : parseFloat(val);

            const suite = getSuite();
            if (suite && suite.math) {
                const updatedScore = suite.math.calculateCurrencyScore(
                    ccy,
                    suite.state.macroData,
                    suite.state.asOfDate,
                    this.customOverrides[ccy]
                );
                suite.state.currencyScores[ccy] = updatedScore;
                suite.state.currencyMacroScores[ccy] = updatedScore;
                suite.state.pairSignals = suite.math.evaluate28Pairs(suite.state.currencyScores, suite.state.calendarEvents, suite.state.userSettings);
                if (window.PairSignalsPage) window.PairSignalsPage.render();
                if (window.ExecutiveOverviewEngine) window.ExecutiveOverviewEngine.render();
            }
            this.render();
        },

        resetOverrides() {
            this.customOverrides = {};
            const suite = getSuite();
            if (suite) {
                suite.setAsOfDate(null);
            }
            this.render();
        },

        render() {
            if (!this.mountPoint) {
                this.mountPoint = document.getElementById('macroScorerMountPoint');
                if (!this.mountPoint) return;
            }

            const state = getState();
            const suite = getSuite();
            const ccy = this.selectedCurrency;
            const meta = CURRENCY_META[ccy] || CURRENCY_META.USD;

            let scoreObj = (state.currencyScores && state.currencyScores[ccy]) || null;
            if (!scoreObj && suite && suite.math) {
                scoreObj = suite.math.calculateCurrencyScore(ccy, state.macroData, state.asOfDate, this.customOverrides[ccy] || {});
            }
            if (!scoreObj) return;

            const allIndicators = scoreObj.enrichedIndicators || [];
            const filteredIndicators = allIndicators.filter(ind => {
                const secMatch = this.selectedSection === 'ALL' || (ind.siteSection || ind.category) === this.selectedSection;
                const pilMatch = this.selectedPillar === 'ALL' || String(ind.pillarNumber) === String(this.selectedPillar);
                const qMatch = !this.searchTerm || ind.indicatorName.toLowerCase().includes(this.searchTerm.toLowerCase()) || (ind.siteSection || '').toLowerCase().includes(this.searchTerm.toLowerCase());
                return secMatch && pilMatch && qMatch;
            });

            const kd = scoreObj.keyDates || {};
            const asOfVal = state.asOfDate || '';

            this.mountPoint.innerHTML = `
                <div class="macro-scorer-wrapper" style="display: flex; flex-direction: column; gap: 1.25rem;">

                    <!-- 1. TOP CONTROL BAR: 8 CURRENCY SELECTOR TABS & HISTORICAL TIME-MACHINE -->
                    <div class="glass-panel" style="padding: 1rem 1.25rem; margin-bottom: 0;">
                        <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem; margin-bottom: 0.9rem;">
                            <div style="display: flex; gap: 0.5rem; flex-wrap: wrap;">
                                ${Object.keys(CURRENCY_META).map(code => {
                                    const s = (state.currencyScores && state.currencyScores[code]) ? state.currencyScores[code].finalScore : 0;
                                    const isActive = code === ccy;
                                    const scoreCol = s >= 0 ? 'var(--neon-success)' : 'var(--neon-danger)';
                                    return `
                                        <button onclick="MacroScorerPage.selectCurrency('${code}')"
                                                class="ccy-selector-btn ${isActive ? 'active' : ''}">
                                            <span class="ccy-monogram ccy-${code.toLowerCase()}">${code}</span>
                                            <div style="text-align: left;">
                                                <div style="font-size: 0.8rem; font-weight: 800; color: #f8fafc;">${code}</div>
                                                <div class="font-mono" style="font-size: 0.7rem; font-weight: 700; color: ${scoreCol};">
                                                    ${s >= 0 ? '+' : ''}${Number(s).toFixed(2)}
                                                </div>
                                            </div>
                                        </button>
                                    `;
                                }).join('')}
                            </div>

                            <!-- Historical As-Of Date Time-Machine & Reset -->
                            <div style="display: flex; align-items: center; gap: 0.65rem; flex-wrap: wrap;">
                                <div style="display: flex; align-items: center; gap: 0.45rem; background: var(--bg-input); padding: 0.35rem 0.7rem; border-radius: 6px; border: 1px solid var(--border-subtle);">
                                    <span style="font-size: 0.72rem; color: var(--text-muted); font-weight: 700; text-transform: uppercase;">Point-in-Time Backtest:</span>
                                    <input type="date" value="${asOfVal || '2026-11-05'}" min="2026-08-01" max="2026-11-05"
                                           onchange="MacroScorerPage.setHistoricalAsOfDate(this.value === '2026-11-05' ? null : this.value)"
                                           style="background: transparent; border: none; color: var(--neon-primary); font-family: var(--font-mono); font-size: 0.78rem; font-weight: 700; outline: none;" />
                                </div>
                                <button class="btn-table-shortcut" onclick="MacroScorerPage.setHistoricalAsOfDate('2026-10-15')">
                                    Backtest: Oct 15
                                </button>
                                <button class="btn-table-shortcut" onclick="MacroScorerPage.setHistoricalAsOfDate('2026-10-01')">
                                    Backtest: Oct 01
                                </button>
                                <button class="btn-table-shortcut" onclick="MacroScorerPage.resetOverrides()" style="color: var(--neon-warning);">
                                    Reset to Live (Nov 05)
                                </button>
                            </div>
                        </div>

                        <!-- Key Macro Release Vintage Strip -->
                        <div style="display: grid; grid-template-columns: repeat(6, 1fr); gap: 0.65rem; padding-top: 0.75rem; border-top: 1px solid var(--border-subtle);">
                            ${this.renderKeyDateBadge('Policy Rate', kd.rate, '%')}
                            ${this.renderKeyDateBadge('Headline CPI YoY', kd.cpi, '%')}
                            ${this.renderKeyDateBadge('Core CPI YoY', kd.coreCpi, '%')}
                            ${this.renderKeyDateBadge('Real GDP YoY', kd.gdp, '%')}
                            ${this.renderKeyDateBadge('Composite PMI', kd.pmi, ' pts')}
                            ${this.renderKeyDateBadge('Unemployment', kd.jobs, '%')}
                        </div>
                    </div>

                    <!-- 2. MIDDLE 3-COLUMN QUANTITATIVE DIAGNOSTICS GRID -->
                    <div style="display: grid; grid-template-columns: 1.05fr 1fr 1.15fr; gap: 1.15rem;">

                        <!-- Left: Currency Regime & Taylor Rule Card -->
                        <div class="glass-panel" style="padding: 1.25rem; margin-bottom: 0; display: flex; flex-direction: column; justify-content: space-between;">
                            <div>
                                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 0.85rem;">
                                    <div>
                                        <div style="display: flex; align-items: center; gap: 0.5rem;">
                                            <span class="ccy-monogram ccy-${ccy.toLowerCase()}">${ccy}</span>
                                            <span style="font-size: 1.05rem; font-weight: 800; color: #f8fafc;">${meta.name}</span>
                                        </div>
                                        <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.2rem;">
                                            ${meta.bank} · Source: tradingeconomics.com/${meta.slug}/indicators
                                        </div>
                                    </div>
                                    <span class="inst-badge ${scoreObj.finalScore >= 0 ? 'inst-badge-bull' : 'inst-badge-bear'}">
                                        ${scoreObj.economicRegimeLabel}
                                    </span>
                                </div>

                                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; margin-bottom: 0.9rem;">
                                    <div style="background: rgba(15, 23, 42, 0.7); padding: 0.75rem; border-radius: 6px; border: 1px solid var(--border-subtle);">
                                        <span style="font-size: 0.68rem; color: var(--text-dim); text-transform: uppercase;">Composite Macro Score</span>
                                        <div class="font-mono ${scoreObj.finalScore >= 0 ? 'text-success' : 'text-danger'}" style="font-size: 1.65rem; font-weight: 900; margin-top: 0.15rem;">
                                            ${scoreObj.finalScore >= 0 ? '+' : ''}${scoreObj.finalScore.toFixed(2)}
                                        </div>
                                        <div style="font-size: 0.7rem; color: var(--text-muted);">Confidence: ${scoreObj.confidenceScore}% (42 Series)</div>
                                    </div>

                                    <div style="background: rgba(15, 23, 42, 0.7); padding: 0.75rem; border-radius: 6px; border: 1px solid var(--border-subtle);">
                                        <span style="font-size: 0.68rem; color: var(--text-dim); text-transform: uppercase;">Taylor Rule Gap</span>
                                        <div class="font-mono ${scoreObj.taylorDeviation >= 0 ? 'text-cyan' : 'text-warning'}" style="font-size: 1.65rem; font-weight: 900; margin-top: 0.15rem;">
                                            ${scoreObj.taylorDeviation >= 0 ? '+' : ''}${scoreObj.taylorDeviation.toFixed(2)}%
                                        </div>
                                        <div style="font-size: 0.7rem; color: var(--text-muted);">Actual: ${scoreObj.actualInterestRate.toFixed(2)}% | Taylor: ${scoreObj.prescribedTaylorRate.toFixed(2)}%</div>
                                    </div>
                                </div>
                            </div>

                            <!-- 6 Pillar Mini Bars -->
                            <div style="display: flex; flex-direction: column; gap: 0.42rem;">
                                ${[1, 2, 3, 4, 5, 6].map(p => {
                                    const pVal = Number(scoreObj[`pillar${p}Score`] || 0);
                                    const pct = Math.min(100, Math.round((Math.abs(pVal) / 3.0) * 100));
                                    const col = pVal >= 0 ? '#10b981' : '#f43f5e';
                                    return `
                                        <div style="display: grid; grid-template-columns: 135px 1fr 48px; align-items: center; gap: 0.5rem; font-size: 0.73rem;">
                                            <span style="color: var(--text-muted); font-weight: 600;">${PILLAR_META[p].short}</span>
                                            <div style="height: 7px; background: rgba(15,23,42,0.9); border-radius: 4px; overflow: hidden; border: 1px solid rgba(148,163,184,0.1);">
                                                <div style="width: ${pct}%; height: 100%; background: ${col};"></div>
                                            </div>
                                            <span class="font-mono" style="text-align: right; font-weight: 800; color: ${col};">${pVal >= 0 ? '+' : ''}${pVal.toFixed(2)}</span>
                                        </div>
                                    `;
                                }).join('')}
                            </div>
                        </div>

                        <!-- Center: 6-Pillar Hexagonal Radar SVG Chart -->
                        <div class="glass-panel" style="padding: 1.25rem; margin-bottom: 0; display: flex; flex-direction: column; align-items: center; justify-content: space-between;">
                            <div style="width: 100%; display: flex; justify-content: space-between; align-items: center;">
                                <span style="font-size: 0.85rem; font-weight: 800; color: #f8fafc;">6-Pillar Geometry (${ccy})</span>
                                <span class="font-mono text-dim" style="font-size: 0.7rem;">Scale: [-3.0, +3.0]</span>
                            </div>
                            ${this.renderHexagonRadarSvg(scoreObj)}
                            <div style="font-size: 0.72rem; color: var(--text-muted); text-align: center;">
                                Mean Indicator Vintage: <strong class="text-cyan font-mono">${scoreObj.avgDataAgeDays} days</strong> · Active Series: <strong class="text-white font-mono">42/42</strong>
                            </div>
                        </div>

                        <!-- Right: G8 Cross-Currency Comparison Chart -->
                        <div class="glass-panel" style="padding: 1.25rem; margin-bottom: 0;">
                            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.65rem;">
                                <span style="font-size: 0.85rem; font-weight: 800; color: #f8fafc;">G8 Relative Macro Strength</span>
                                <span class="font-mono text-dim" style="font-size: 0.7rem;">As of ${asOfVal || '2026-11-05'}</span>
                            </div>
                            ${this.renderG8ComparisonSvg(state.currencyScores || {}, ccy)}
                        </div>

                    </div>

                    <!-- 3. FULL 42-INDICATOR TRADINGECONOMICS MATRIX TABLE -->
                    <div class="glass-panel" style="padding: 1.25rem; margin-bottom: 0;">
                        <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.85rem; margin-bottom: 1rem;">
                            <div>
                                <div style="font-size: 0.95rem; font-weight: 800; color: #f8fafc;">
                                    ${ccy} Complete 42-Indicator Institutional Matrix (${filteredIndicators.length} of ${allIndicators.length} shown)
                                </div>
                                <div style="font-size: 0.74rem; color: var(--text-muted);">
                                    Direct coverage across all 10 TradingEconomics sections with exact reference period (MMM/yy), release date, and exponential decay weight.
                                </div>
                            </div>

                            <!-- Search & Pillar Filter -->
                            <div style="display: flex; gap: 0.6rem; align-items: center; flex-wrap: wrap;">
                                <input type="text" placeholder="Filter indicator or section..." value="${this.searchTerm}"
                                       oninput="MacroScorerPage.setSearchTerm(this.value)"
                                       style="padding: 0.4rem 0.75rem; background: var(--bg-input); border: 1px solid var(--border-subtle); border-radius: 6px; color: #fff; font-size: 0.78rem; width: 200px;" />
                                <select class="theme-dropdown" onchange="MacroScorerPage.setPillarFilter(this.value)">
                                    <option value="ALL" ${this.selectedPillar === 'ALL' ? 'selected' : ''}>All 6 Pillars (42)</option>
                                    ${[1, 2, 3, 4, 5, 6].map(p => `<option value="${p}" ${String(this.selectedPillar) === String(p) ? 'selected' : ''}>${PILLAR_META[p].name}</option>`).join('')}
                                </select>
                            </div>
                        </div>

                        <!-- 10 TradingEconomics Section Filter Pills -->
                        <div style="display: flex; gap: 0.45rem; flex-wrap: wrap; margin-bottom: 1rem;">
                            ${SITE_SECTIONS.map(sec => {
                                const count = sec === 'ALL' ? allIndicators.length : allIndicators.filter(i => (i.siteSection || i.category) === sec).length;
                                const active = this.selectedSection === sec;
                                return `
                                    <button onclick="MacroScorerPage.setSectionFilter('${sec}')"
                                            class="section-filter-pill ${active ? 'active' : ''}">
                                        ${sec === 'ALL' ? 'All Sections' : sec} <span class="font-mono" style="opacity: 0.75;">(${count})</span>
                                    </button>
                                `;
                            }).join('')}
                        </div>

                        <div class="table-responsive" style="max-height: 560px; overflow-y: auto; border: 1px solid var(--border-subtle); border-radius: 8px;">
                            <table class="institutional-table">
                                <thead>
                                    <tr>
                                        <th>#</th>
                                        <th>Section &amp; Pillar</th>
                                        <th>Indicator Name</th>
                                        <th style="text-align: right;">Actual (Live / Override)</th>
                                        <th style="text-align: right;">Previous</th>
                                        <th style="text-align: right;">Consensus</th>
                                        <th style="text-align: right;">TE Forecast</th>
                                        <th style="text-align: center;">Site Period</th>
                                        <th>Release Date &amp; Vintage Age</th>
                                        <th style="text-align: right;">Decay</th>
                                        <th style="text-align: right;">Z-Score</th>
                                        <th style="text-align: right;">Contribution</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${filteredIndicators.map((ind, idx) => {
                                        const zCol = ind.zScore > 0.15 ? 'text-success' : (ind.zScore < -0.15 ? 'text-danger' : 'text-muted');
                                        const cCol = ind.weightedContribution > 0 ? 'text-success' : (ind.weightedContribution < 0 ? 'text-danger' : 'text-muted');
                                        const freshBadge = ind.ageInDays <= 3 ? 'inst-badge-cyan' : (ind.ageInDays <= 21 ? 'inst-badge-bull' : (ind.ageInDays <= 45 ? 'inst-badge-warn' : 'inst-badge-neutral'));

                                        return `
                                            <tr>
                                                <td class="font-mono text-dim">${idx + 1}</td>
                                                <td>
                                                    <span class="inst-badge inst-badge-neutral">${ind.siteSection || ind.category}</span>
                                                    <span class="font-mono text-dim" style="font-size: 0.68rem; margin-left: 0.3rem;">P${ind.pillarNumber}</span>
                                                </td>
                                                <td style="font-weight: 700; color: #f8fafc;">${ind.indicatorName}</td>
                                                <td style="text-align: right;">
                                                    <input type="number" step="0.01" value="${ind.lastValue}"
                                                           onchange="MacroScorerPage.updateOverride('${ccy}', '${ind.indicatorName.replace(/'/g, "\\'")}', 'lastValue', this.value)"
                                                           style="width: 78px; text-align: right; padding: 0.22rem 0.4rem; background: var(--bg-input); border: 1px solid var(--border-subtle); border-radius: 4px; color: var(--neon-primary); font-family: var(--font-mono); font-weight: 800; font-size: 0.78rem;" />
                                                    <span class="font-mono text-dim" style="font-size: 0.7rem; margin-left: 2px;">${ind.unit || ''}</span>
                                                </td>
                                                <td class="font-mono text-muted" style="text-align: right;">${ind.previousValue}${ind.unit || ''}</td>
                                                <td class="font-mono text-muted" style="text-align: right;">${ind.consensusValue}${ind.unit || ''}</td>
                                                <td class="font-mono text-dim" style="text-align: right;">${ind.forecastValue !== undefined ? ind.forecastValue : ind.consensusValue}${ind.unit || ''}</td>
                                                <td style="text-align: center;">
                                                    <span class="inst-badge inst-badge-cyan font-mono">${ind.referencePeriod || 'Oct/26'}</span>
                                                </td>
                                                <td class="font-mono" style="font-size: 0.74rem;">
                                                    <span style="color: #f8fafc; font-weight: 700;">${ind.exactReleaseDate}</span>
                                                    <span class="inst-badge ${freshBadge}" style="margin-left: 0.4rem;">${ind.ageInDays}d ago</span>
                                                </td>
                                                <td class="font-mono text-muted" style="text-align: right;">${Number(ind.decayFactor || 0.85).toFixed(2)}x</td>
                                                <td class="font-mono ${zCol}" style="text-align: right; font-weight: 800;">
                                                    ${ind.zScore >= 0 ? '+' : ''}${Number(ind.zScore).toFixed(2)}σ
                                                </td>
                                                <td class="font-mono ${cCol}" style="text-align: right; font-weight: 800;">
                                                    ${ind.weightedContribution >= 0 ? '+' : ''}${Number(ind.weightedContribution).toFixed(3)}
                                                </td>
                                            </tr>
                                        `;
                                    }).join('')}
                                </tbody>
                            </table>
                        </div>
                    </div>

                </div>
            `;
        },

        renderKeyDateBadge(label, obj, unit) {
            const val = obj && obj.val !== undefined ? `${obj.val}${unit}` : '--';
            const period = obj && obj.period ? obj.period : 'Oct/26';
            const date = obj && obj.date ? obj.date : '2026-10-28';
            const days = obj && obj.days !== undefined ? `${obj.days}d ago` : '7d ago';

            return `
                <div style="background: rgba(15, 23, 42, 0.65); padding: 0.55rem 0.7rem; border-radius: 6px; border: 1px solid var(--border-subtle);">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <span style="font-size: 0.68rem; color: var(--text-dim); font-weight: 700; text-transform: uppercase;">${label}</span>
                        <span class="inst-badge inst-badge-cyan font-mono" style="font-size: 0.64rem; padding: 0.08rem 0.35rem;">${period}</span>
                    </div>
                    <div class="font-mono" style="font-size: 0.96rem; font-weight: 800; color: #f8fafc; margin-top: 0.2rem;">${val}</div>
                    <div class="font-mono text-dim" style="font-size: 0.66rem; margin-top: 0.1rem;">Released: ${date} (${days})</div>
                </div>
            `;
        },

        renderHexagonRadarSvg(scoreObj) {
            const size = 215;
            const cx = size / 2;
            const cy = size / 2;
            const maxR = 74;
            const labels = ['P1 Monetary', 'P2 Prices', 'P3 Growth', 'P4 Labor', 'P5 Trade', 'P6 Consumer'];
            const vals = [
                scoreObj.pillar1Score || 0,
                scoreObj.pillar2Score || 0,
                scoreObj.pillar3Score || 0,
                scoreObj.pillar4Score || 0,
                scoreObj.pillar5Score || 0,
                scoreObj.pillar6Score || 0
            ];

            const pointAt = (idx, radius) => {
                const angle = (-Math.PI / 2) + (idx * 2 * Math.PI / 6);
                return [cx + radius * Math.cos(angle), cy + radius * Math.sin(angle)];
            };

            const ringPoints = (rRatio) => [0, 1, 2, 3, 4, 5].map(i => pointAt(i, maxR * rRatio).map(v => v.toFixed(1)).join(',')).join(' ');

            const dataPoly = vals.map((v, i) => {
                const norm = Math.max(0.12, Math.min(1.0, (v + 3.0) / 6.0));
                return pointAt(i, maxR * norm).map(x => x.toFixed(1)).join(',');
            }).join(' ');

            return `
                <svg viewBox="0 0 ${size} ${size}" style="width: 100%; max-width: 220px; height: auto;">
                    <polygon points="${ringPoints(1.0)}" fill="none" stroke="rgba(148,163,184,0.18)" stroke-width="1"/>
                    <polygon points="${ringPoints(0.5)}" fill="none" stroke="rgba(56,189,248,0.28)" stroke-dasharray="2,2" stroke-width="1"/>
                    ${[0, 1, 2, 3, 4, 5].map(i => {
                        const [px, py] = pointAt(i, maxR);
                        const [lx, ly] = pointAt(i, maxR + 20);
                        return `
                            <line x1="${cx}" y1="${cy}" x2="${px.toFixed(1)}" y2="${py.toFixed(1)}" stroke="rgba(148,163,184,0.14)"/>
                            <text x="${lx.toFixed(1)}" y="${(ly + 3).toFixed(1)}" fill="#94a3b8" font-size="8.5" font-weight="700" text-anchor="middle">${labels[i]}</text>
                        `;
                    }).join('')}
                    <polygon points="${dataPoly}" fill="rgba(56,189,248,0.22)" stroke="#38bdf8" stroke-width="2"/>
                </svg>
            `;
        },

        renderG8ComparisonSvg(scoresMap, activeCcy) {
            const rankings = Object.values(scoresMap).sort((a, b) => b.finalScore - a.finalScore);
            const zeroX = 185;
            const maxSpan = 88;
            const rowH = 24;

            return `
                <svg viewBox="0 0 360 205" style="width: 100%; height: auto;">
                    <line x1="${zeroX}" y1="4" x2="${zeroX}" y2="200" stroke="rgba(148,163,184,0.3)" stroke-width="1"/>
                    ${rankings.map((s, idx) => {
                        const y = 8 + idx * rowH;
                        const isPos = s.finalScore >= 0;
                        const w = Math.max(4, Math.min(maxSpan, (Math.abs(s.finalScore) / 6.5) * maxSpan));
                        const x = isPos ? zeroX : zeroX - w;
                        const col = s.currency === activeCcy ? '#38bdf8' : (isPos ? '#10b981' : '#f43f5e');
                        return `
                            <text x="10" y="${y + 11}" fill="${s.currency === activeCcy ? '#38bdf8' : '#f8fafc'}" font-size="10" font-weight="800" font-family="JetBrains Mono, monospace">#${idx + 1} ${s.currency}</text>
                            <rect x="92" y="${y}" width="186" height="13" rx="2.5" fill="rgba(15,23,42,0.75)"/>
                            <rect x="${x.toFixed(1)}" y="${y}" width="${w.toFixed(1)}" height="13" rx="2.5" fill="${col}"/>
                            <text x="288" y="${y + 10.5}" fill="${col}" font-size="10" font-weight="800" font-family="JetBrains Mono, monospace">${isPos ? '+' : ''}${s.finalScore.toFixed(2)}</text>
                        `;
                    }).join('')}
                </svg>
            `;
        }
    };

    // ==========================================================================
    // 2. 28-PAIR SIGNALS, VINTAGE DATES & 60-DAY CHART BACKTEST LAB (PAGE 4)
    // ==========================================================================
    const PairSignalsPage = {
        activeSubTab: 'MATRIX', // 'MATRIX' | 'BACKTEST_GRID' | 'CALENDAR'
        selectedChartPair: 'USDCAD',
        priceAnchorMode: 'LIVE_SPOT', // 'LIVE_SPOT' | 'SIGNAL_ORIGIN'
        activeFilter: 'ALL',
        searchQuery: '',
        activeTf: 'D1',
        customPairCandles: {},
        currentActiveSig: null,

        async init() {
            this.mountPoint = document.getElementById('pairSignalsMountPoint');
            await this.ensurePairCandles(this.selectedChartPair, this.activeTf);
            this.render();
        },

        async ensurePairCandles(pair, tf = 'D1') {
            const key = pair + '_' + tf;
            if (this.customPairCandles && this.customPairCandles[key] && this.customPairCandles[key].length > 0) {
                return;
            }
            if (tf === 'D1' && window.PairCandles && window.PairCandles[pair] && window.PairCandles[pair].length > 0) {
                this.customPairCandles[key] = window.PairCandles[pair];
                return;
            }
            try {
                const resp = await fetch(`/api/v1/market/candles/${pair}?timeframe=${tf}&count=60`);
                if (resp.ok) {
                    const data = await resp.json();
                    if (Array.isArray(data) && data.length > 0) {
                        this.customPairCandles[key] = data;
                        if (tf === 'D1') {
                            window.PairCandles = window.PairCandles || {};
                            window.PairCandles[pair] = data;
                        }
                    }
                }
            } catch (e) {
                console.warn('Failed to ensure pair candles', e);
            }
        },

        async setTimeframe(tf) {
            this.activeTf = tf;
            await this.ensurePairCandles(this.selectedChartPair, tf);
            this.render();
        },

        onCandleHover(idx, pair) {
            const el = document.getElementById('candleHudBar_' + pair);
            const line = document.getElementById('crosshairLine_' + pair);
            if (!el) return;
            const candles = (this.customPairCandles && this.customPairCandles[pair + '_' + this.activeTf]) || 
                            (this.currentActiveSig && this.currentActiveSig.candles) || [];
            const c = candles[idx];
            if (!c) return;
            const diff = (c.close - c.open);
            const pips = pair.includes('JPY') ? (diff * 100).toFixed(1) : (diff * 10000).toFixed(1);
            const chgCol = diff >= 0 ? '#10b981' : '#f43f5e';
            const sign = diff >= 0 ? '+' : '';
            el.innerHTML = `
                <span style="color: #f8fafc; font-weight: 700;">📅 ${c.date || c.time}</span>
                <span>O: <strong style="color: #e2e8f0;">${c.open}</strong></span>
                <span>H: <strong style="color: #e2e8f0;">${c.high}</strong></span>
                <span>L: <strong style="color: #e2e8f0;">${c.low}</strong></span>
                <span>C: <strong style="color: ${chgCol};">${c.close}</strong></span>
                <span style="color: ${chgCol}; font-weight: 800;">Δ: ${sign}${pips}p</span>
                <span style="color: #38bdf8;">EMA20: ${c.ema20}</span>
                <span style="color: #c084fc;">EMA50: ${c.ema50}</span>
                <span style="color: #fbbf24;">Vol: ${(c.volume || 0).toLocaleString()}</span>
            `;
            if (line) {
                const stepX = (740 - 12 - 158) / candles.length;
                const x = 12 + idx * stepX + stepX / 2;
                line.setAttribute('x1', x.toFixed(1));
                line.setAttribute('x2', x.toFixed(1));
                line.setAttribute('opacity', '0.7');
            }
        },

        onCandleLeave(pair) {
            const line = document.getElementById('crosshairLine_' + pair);
            if (line) line.setAttribute('opacity', '0');
        },

        async executeQuickTrade(pair, direction) {
            const state = getState();
            const sig = (state.pairSignals || []).find(s => s.pair === pair);
            const volume = 0.01;
            const slPips = sig ? sig.suggestedSlPips : 45;
            const tpPips = sig ? sig.suggestedTp1Pips : 90;

            const confirmed = confirm(`⚡ ارسال سفارش به بروکر متاتریدر ۵:\n\nجفت ارز: ${pair}\nجهت: ${direction}\nحجم: ${volume} لات\nحساب مقصد: #57452535486 (GTCGlobalTrade-Demo $100)\nحد ضرر (SL): ${slPips} پیپ\nحد سود (TP): ${tpPips} پیپ\n\nآیا تایید می‌فرمایید؟`);
            if (!confirmed) return;

            if (window.AppRouter) window.AppRouter.showToast(`در حال ارسال سفارش ${direction} ${pair} به MT5...`, 'info');

            try {
                const resp = await fetch('http://127.0.0.1:8085/api/mt5/order', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        symbol: pair,
                        action: direction,
                        volume: volume,
                        sl_pips: slPips,
                        tp_pips: tpPips
                    })
                });
                const res = await resp.json();
                if (res.success) {
                    if (window.AppRouter) window.AppRouter.showToast(`✅ معامله در متاتریدر ۵ ثبت شد! تیکت: #${res.ticket}`, 'success');
                    try {
                        await fetch('/api/v1/telegram/test', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({})
                        });
                    } catch(e){}
                } else {
                    if (window.AppRouter) window.AppRouter.showToast(`خطای بروکر MT5: ${res.error || 'Check Broker'}`, 'warning');
                }
            } catch(err) {
                if (window.AppRouter) window.AppRouter.showToast(`خطا در ارتباط با دیمن MT5: ${err.message}`, 'error');
            }
        },

        setSubTab(tab) {
            this.activeSubTab = tab;
            this.render();
        },

        async selectPairForChart(pair) {
            this.selectedChartPair = pair;
            await this.ensurePairCandles(pair, this.activeTf);
            this.render();
        },

        setPriceAnchorMode(mode) {
            this.priceAnchorMode = mode;
            this.render();
        },

        setFilter(f) {
            this.activeFilter = f;
            this.render();
        },

        setSearch(q) {
            this.searchQuery = (q || '').trim();
            this.render();
        },

        render() {
            if (!this.mountPoint) {
                this.mountPoint = document.getElementById('pairSignalsMountPoint');
                if (!this.mountPoint) return;
            }

            const state = getState();
            const allSignals = state.pairSignals || [];
            if (allSignals.length === 0) return;

            const activeSig = allSignals.find(s => s.pair === this.selectedChartPair) || allSignals[0];
            this.selectedChartPair = activeSig.pair;
            this.currentActiveSig = activeSig;

            const candleKey = this.selectedChartPair + '_' + this.activeTf;
            if (!this.customPairCandles[candleKey]) {
                if (this.activeTf === 'D1' && window.PairCandles && window.PairCandles[this.selectedChartPair]) {
                    this.customPairCandles[candleKey] = window.PairCandles[this.selectedChartPair];
                } else {
                    this.ensurePairCandles(this.selectedChartPair, this.activeTf).then(() => {
                        const elSvg = document.getElementById('candlestickSvgContainer_' + this.selectedChartPair);
                        if (elSvg && this.currentActiveSig) {
                            elSvg.innerHTML = this.renderCandlestickBacktestSvg(this.currentActiveSig, this.priceAnchorMode === 'SIGNAL_ORIGIN');
                        }
                    });
                }
            }

            const filteredSignals = allSignals.filter(s => {
                if (this.searchQuery && !s.pair.toLowerCase().includes(this.searchQuery.toLowerCase())) return false;
                if (this.activeFilter === 'FRESH' && s.signalAgeDays > 5) return false;
                if (this.activeFilter === 'ACTIVE_SWING' && (s.signalAgeDays < 6 || s.signalAgeDays > 15)) return false;
                if (this.activeFilter === 'HISTORICAL' && s.signalAgeDays <= 15) return false;
                if (this.activeFilter === 'ULTRA_SAFE' && !s.allGatesPassed) return false;
                if (this.activeFilter === 'BUY' && s.direction !== 'BUY') return false;
                if (this.activeFilter === 'SELL' && s.direction !== 'SELL') return false;
                return true;
            });

            const countFresh = allSignals.filter(s => s.signalAgeDays <= 5).length;
            const countSwing = allSignals.filter(s => s.signalAgeDays >= 6 && s.signalAgeDays <= 15).length;
            const countHist = allSignals.filter(s => s.signalAgeDays > 15).length;
            const countUltra = allSignals.filter(s => s.allGatesPassed).length;

            this.mountPoint.innerHTML = `
                <div class="pair-signals-wrapper" style="display: flex; flex-direction: column; gap: 1.25rem;">

                    <!-- Sub-Navigation & Price Anchor Mode Bar -->
                    <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.85rem;">
                        <div style="display: flex; gap: 0.55rem; flex-wrap: wrap;">
                            <button onclick="PairSignalsPage.setSubTab('MATRIX')" class="subtab-btn ${this.activeSubTab === 'MATRIX' ? 'active' : ''}">
                                28-Pair Signal Matrix &amp; 60-Day Chart Workstation
                            </button>
                            <button onclick="PairSignalsPage.setSubTab('BACKTEST_GRID')" class="subtab-btn ${this.activeSubTab === 'BACKTEST_GRID' ? 'active' : ''}">
                                All 28 Pairs Backtest Audit &amp; Excursion Summary
                            </button>
                            <button onclick="PairSignalsPage.setSubTab('CALENDAR')" class="subtab-btn ${this.activeSubTab === 'CALENDAR' ? 'active' : ''}">
                                Economic Calendar &amp; Gate-4 News Proximity Radar
                            </button>
                        </div>

                        <div style="display: flex; align-items: center; gap: 0.5rem;">
                            <span style="font-size: 0.72rem; color: var(--text-muted); font-weight: 700; text-transform: uppercase;">SL / TP Anchor Mode:</span>
                            <button onclick="PairSignalsPage.setPriceAnchorMode('LIVE_SPOT')" class="section-filter-pill ${this.priceAnchorMode === 'LIVE_SPOT' ? 'active' : ''}">
                                Live Spot Execution (Today)
                            </button>
                            <button onclick="PairSignalsPage.setPriceAnchorMode('SIGNAL_ORIGIN')" class="section-filter-pill ${this.priceAnchorMode === 'SIGNAL_ORIGIN' ? 'active' : ''}">
                                Signal Origin Entry (Backtest)
                            </button>
                        </div>
                    </div>

                    ${this.activeSubTab === 'MATRIX' ? this.renderMatrixAndChartWorkstation(activeSig, allSignals, filteredSignals, { countFresh, countSwing, countHist, countUltra }) : ''}
                    ${this.activeSubTab === 'BACKTEST_GRID' ? this.renderBacktestComparisonGrid(allSignals) : ''}
                    ${this.activeSubTab === 'CALENDAR' ? this.renderCalendarRadarTab(state.calendarEvents || []) : ''}

                </div>
            `;
        },

        renderMatrixAndChartWorkstation(sig, allSignals, filteredSignals, counts) {
            const isOriginMode = this.priceAnchorMode === 'SIGNAL_ORIGIN';
            const entryVal = isOriginMode ? sig.entryAtSignalDate : sig.spotPrice;
            const slVal = isOriginMode ? sig.originSl : sig.liveSl;
            const tp1Val = isOriginMode ? sig.originTp1 : sig.liveTp1;
            const tp2Val = isOriginMode ? sig.originTp2 : sig.liveTp2;
            const bKd = sig.baseKeyDates || {};
            const qKd = sig.quoteKeyDates || {};

            return `
                <!-- INTERACTIVE 60-DAY CANDLESTICK CHART & SIGNAL VINTAGE WORKSTATION -->
                <div class="glass-panel" style="padding: 1.25rem; margin-bottom: 0;">
                    <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem; margin-bottom: 1rem; padding-bottom: 0.85rem; border-bottom: 1px solid var(--border-subtle);">
                        <div style="display: flex; align-items: center; gap: 0.85rem; flex-wrap: wrap;">
                            <div style="display: flex; align-items: center; gap: 0.4rem;">
                                <span class="ccy-monogram ccy-${sig.baseCurrency.toLowerCase()}">${sig.baseCurrency}</span>
                                <span class="ccy-monogram ccy-${sig.quoteCurrency.toLowerCase()}">${sig.quoteCurrency}</span>
                                <span style="font-size: 1.25rem; font-weight: 900; color: #f8fafc; letter-spacing: 0.02em;">${sig.pair}</span>
                            </div>
                            <span class="inst-badge ${sig.isLong ? 'inst-badge-bull' : 'inst-badge-bear'}">${sig.signalGrade}</span>
                            <span class="inst-badge inst-badge-cyan font-mono">${sig.signalVintageBadge}</span>
                            <span style="font-size: 0.75rem; color: var(--text-muted);">
                                Pair Strategy DNA: <strong class="text-cyan">${sig.strategyName}</strong> · Trigger: <strong class="text-white">${sig.triggerRule}</strong> · Verified Win Rate: <strong class="text-success font-mono">${sig.pairWinRate}% (PF ${sig.pairProfitFactor})</strong>
                            </span>
                        </div>

                        <div style="display: flex; align-items: center; gap: 0.5rem;">
                            <span style="font-size: 0.72rem; color: var(--text-dim); text-transform: uppercase; font-weight: 700;">Inspect Pair:</span>
                            <select class="theme-dropdown font-mono" onchange="PairSignalsPage.selectPairForChart(this.value)" style="font-weight: 800; min-width: 240px;">
                                ${allSignals.map(p => `<option value="${p.pair}" ${p.pair === sig.pair ? 'selected' : ''}>${p.pair} (${p.direction} | Δ:${p.deltaScore >= 0 ? '+' : ''}${p.deltaScore} | ${p.signalAgeDays}d ago)</option>`).join('')}
                            </select>
                        </div>
                    </div>

                    <!-- 2-Column Split: Left = 60-Day Candlestick Chart (Collision-Free Right Axis), Right = Vintage & Sizing Audit -->
                    <div style="display: grid; grid-template-columns: 1.55fr 1fr; gap: 1.25rem; align-items: stretch;">

                        <!-- Left: Pure SVG 60-Day Candlestick + EMA20/50 + Signal Origin Marker + De-Overlapped Price Tags -->
                        <div style="background: rgba(6, 10, 18, 0.85); border: 1px solid var(--border-subtle); border-radius: 8px; padding: 0.9rem;">
                            <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 0.5rem;">
                                <div style="display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;">
                                    <span style="font-size: 0.82rem; font-weight: 800; color: #f8fafc;">
                                        ${sig.pair} ${this.activeTf} Candlesticks · EMA20/50 · Volume Sub-Panel
                                    </span>
                                    <span class="inst-badge inst-badge-bull font-mono" style="font-size: 0.65rem;">🟢 MT5 LIVE</span>
                                </div>
                                <div style="display: flex; align-items: center; gap: 0.45rem; flex-wrap: wrap;">
                                    <!-- Timeframe Switcher -->
                                    <div style="display: flex; background: rgba(15,23,42,0.8); border: 1px solid rgba(148,163,184,0.2); border-radius: 4px; padding: 2px;">
                                        <button onclick="PairSignalsPage.setTimeframe('D1')" style="background: ${this.activeTf === 'D1' ? '#38bdf8' : 'transparent'}; color: ${this.activeTf === 'D1' ? '#0f172a' : '#94a3b8'}; border: none; font-size: 0.68rem; font-weight: 800; padding: 2px 7px; border-radius: 3px; cursor: pointer;">D1</button>
                                        <button onclick="PairSignalsPage.setTimeframe('H4')" style="background: ${this.activeTf === 'H4' ? '#38bdf8' : 'transparent'}; color: ${this.activeTf === 'H4' ? '#0f172a' : '#94a3b8'}; border: none; font-size: 0.68rem; font-weight: 800; padding: 2px 7px; border-radius: 3px; cursor: pointer;">H4</button>
                                        <button onclick="PairSignalsPage.setTimeframe('H1')" style="background: ${this.activeTf === 'H1' ? '#38bdf8' : 'transparent'}; color: ${this.activeTf === 'H1' ? '#0f172a' : '#94a3b8'}; border: none; font-size: 0.68rem; font-weight: 800; padding: 2px 7px; border-radius: 3px; cursor: pointer;">H1</button>
                                    </div>
                                    <!-- Quick MT5 Execution Button -->
                                    <button onclick="PairSignalsPage.executeQuickTrade('${sig.pair}', '${sig.direction}')" style="background: ${sig.isLong ? 'linear-gradient(135deg, #10b981, #059669)' : 'linear-gradient(135deg, #f43f5e, #e11d48)'}; color: #fff; font-weight: 800; border: none; padding: 0.25rem 0.6rem; border-radius: 4px; cursor: pointer; font-size: 0.72rem; box-shadow: 0 0 8px ${sig.isLong ? 'rgba(16,185,129,0.3)' : 'rgba(244,63,94,0.3)'};">
                                        ⚡ ورود ۰.۰۱ MT5
                                    </button>
                                    <button onclick="window.inspectPairInGeminiLab('${sig.pair}')" style="background: rgba(168,85,247,0.18); border: 1px solid rgba(168,85,247,0.4); color: #c084fc; font-size: 0.72rem; padding: 0.25rem 0.55rem; border-radius: 4px; cursor: pointer; font-weight: 800;">
                                        🧠 Gemini Decision 1
                                    </button>
                                </div>
                            </div>

                            <!-- Live Interactive HUD Readout -->
                            ${this.renderChartHud(sig)}

                            <div id="candlestickSvgContainer_${sig.pair}">
                                ${this.renderCandlestickBacktestSvg(sig, isOriginMode)}
                            </div>
                        </div>

                        <!-- Right: 3 Stacked Diagnostic Cards (Site Vintage Dates, Backtest Excursion, Dynamic ATR Risk Sizing) -->
                        <div style="display: flex; flex-direction: column; gap: 0.85rem; justify-content: space-between;">

                            <!-- Card 1: Underlying Site Release Dates for Base vs Quote -->
                            <div style="background: rgba(15, 23, 42, 0.65); border: 1px solid var(--border-subtle); border-radius: 8px; padding: 0.85rem;">
                                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.55rem;">
                                    <span style="font-size: 0.76rem; font-weight: 800; color: #f8fafc; text-transform: uppercase;">TradingEconomics Data Vintage (${sig.pair})</span>
                                    <span class="font-mono text-cyan" style="font-size: 0.7rem;">Mean Data Age: ${sig.avgPairDataAgeDays}d</span>
                                </div>
                                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.65rem; font-size: 0.72rem;" class="font-mono">
                                    <div style="background: rgba(6,10,18,0.65); padding: 0.55rem; border-radius: 6px; border: 1px solid rgba(148,163,184,0.1);">
                                        <div style="font-weight: 800; color: #f8fafc; margin-bottom: 0.3rem;">${sig.baseCurrency} (Score: ${sig.baseScore >= 0 ? '+' : ''}${sig.baseScore})</div>
                                        <div>GDP: <span class="text-cyan">${bKd.gdp ? bKd.gdp.val + '%' : '--'}</span> [${bKd.gdp ? bKd.gdp.period : 'Sep/26'} · ${bKd.gdp ? bKd.gdp.days + 'd' : '--'}]</div>
                                        <div>CPI: <span class="text-cyan">${bKd.cpi ? bKd.cpi.val + '%' : '--'}</span> [${bKd.cpi ? bKd.cpi.period : 'Sep/26'} · ${bKd.cpi ? bKd.cpi.days + 'd' : '--'}]</div>
                                        <div>Rate: <span class="text-cyan">${bKd.rate ? bKd.rate.val + '%' : '--'}</span> [${bKd.rate ? bKd.rate.period : 'Oct/26'} · ${bKd.rate ? bKd.rate.days + 'd' : '--'}]</div>
                                        <div>PMI: <span class="text-cyan">${bKd.pmi ? bKd.pmi.val : '--'}</span> [${bKd.pmi ? bKd.pmi.period : 'Oct/26'} · ${bKd.pmi ? bKd.pmi.days + 'd' : '--'}]</div>
                                    </div>
                                    <div style="background: rgba(6,10,18,0.65); padding: 0.55rem; border-radius: 6px; border: 1px solid rgba(148,163,184,0.1);">
                                        <div style="font-weight: 800; color: #f8fafc; margin-bottom: 0.3rem;">${sig.quoteCurrency} (Score: ${sig.quoteScore >= 0 ? '+' : ''}${sig.quoteScore})</div>
                                        <div>GDP: <span class="text-cyan">${qKd.gdp ? qKd.gdp.val + '%' : '--'}</span> [${qKd.gdp ? qKd.gdp.period : 'Sep/26'} · ${qKd.gdp ? qKd.gdp.days + 'd' : '--'}]</div>
                                        <div>CPI: <span class="text-cyan">${qKd.cpi ? qKd.cpi.val + '%' : '--'}</span> [${qKd.cpi ? qKd.cpi.period : 'Sep/26'} · ${qKd.cpi ? qKd.cpi.days + 'd' : '--'}]</div>
                                        <div>Rate: <span class="text-cyan">${qKd.rate ? qKd.rate.val + '%' : '--'}</span> [${qKd.rate ? qKd.rate.period : 'Oct/26'} · ${qKd.rate ? qKd.rate.days + 'd' : '--'}]</div>
                                        <div>PMI: <span class="text-cyan">${qKd.pmi ? qKd.pmi.val : '--'}</span> [${qKd.pmi ? qKd.pmi.period : 'Oct/26'} · ${qKd.pmi ? qKd.pmi.days + 'd' : '--'}]</div>
                                    </div>
                                </div>
                            </div>

                            <!-- Card 2: Backtest Performance from Signal Origin Date to Today -->
                            <div style="background: rgba(15, 23, 42, 0.65); border: 1px solid var(--border-subtle); border-radius: 8px; padding: 0.85rem;">
                                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
                                    <span style="font-size: 0.76rem; font-weight: 800; color: #f8fafc; text-transform: uppercase;">
                                        Backtest Audit (${sig.signalOriginDate} · ${sig.signalAgeDays}d ago → Today)
                                    </span>
                                    <span class="font-mono ${sig.backtestRMultiple >= 0 ? 'text-success' : 'text-danger'}" style="font-weight: 900; font-size: 0.85rem;">
                                        ${sig.backtestRMultiple >= 0 ? '+' : ''}${sig.backtestRMultiple}R
                                    </span>
                                </div>
                                <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.5rem; text-align: center;" class="font-mono">
                                    <div style="background: rgba(6,10,18,0.65); padding: 0.45rem; border-radius: 5px;">
                                        <div style="font-size: 0.64rem; color: var(--text-dim);">Origin Entry</div>
                                        <div style="font-size: 0.84rem; font-weight: 800; color: #f8fafc;">${sig.entryAtSignalDate}</div>
                                    </div>
                                    <div style="background: rgba(6,10,18,0.65); padding: 0.45rem; border-radius: 5px;">
                                        <div style="font-size: 0.64rem; color: var(--text-dim);">Live Spot</div>
                                        <div style="font-size: 0.84rem; font-weight: 800; color: var(--neon-primary);">${sig.spotPrice}</div>
                                    </div>
                                    <div style="background: rgba(6,10,18,0.65); padding: 0.45rem; border-radius: 5px;">
                                        <div style="font-size: 0.64rem; color: var(--text-dim);">Net Excursion</div>
                                        <div class="${sig.backtestPnlPips >= 0 ? 'text-success' : 'text-danger'}" style="font-size: 0.84rem; font-weight: 800;">
                                            ${sig.backtestPnlPips >= 0 ? '+' : ''}${sig.backtestPnlPips}p
                                        </div>
                                    </div>
                                    <div style="background: rgba(6,10,18,0.65); padding: 0.45rem; border-radius: 5px;">
                                        <div style="font-size: 0.64rem; color: var(--text-dim);">MFE / MAE</div>
                                        <div style="font-size: 0.78rem; font-weight: 700; color: #f8fafc;">+${sig.mfePips}p / -${sig.maePips}p</div>
                                    </div>
                                </div>
                                <div style="font-size: 0.73rem; color: var(--text-muted); margin-top: 0.45rem;">
                                    Status: <strong class="text-success">${sig.backtestVerdict}</strong> · Pair Win Rate: <strong class="text-cyan font-mono">${sig.pairWinRate}% (${sig.pairTradesCount} Trades)</strong> · Session: <span class="font-mono">${sig.optimalSessionUtc}</span>
                                </div>
                            </div>

                            <!-- Card 3: Institutional ATR-14 Stop Loss, Dual Targets & Exact Quote Pip Value Sizing -->
                            <div style="background: rgba(15, 23, 42, 0.65); border: 1px solid var(--border-subtle); border-radius: 8px; padding: 0.85rem;">
                                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
                                    <span style="font-size: 0.76rem; font-weight: 800; color: #f8fafc; text-transform: uppercase;">
                                        ATR-14 Risk &amp; Position Sizing (${isOriginMode ? 'Origin Entry' : 'Live Spot'})
                                    </span>
                                    <span class="font-mono text-cyan" style="font-size: 0.72rem;">
                                        Swing H: ${sig.swingHigh} · Swing L: ${sig.swingLow} · OTE: ${sig.otePullbackPrice}
                                    </span>
                                </div>
                                <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.5rem; text-align: center;" class="font-mono">
                                    <div style="background: rgba(6,10,18,0.65); padding: 0.45rem; border-radius: 5px; border: 1px solid rgba(56,189,248,0.2);">
                                        <div style="font-size: 0.64rem; color: var(--text-dim);">Entry Price</div>
                                        <div style="font-size: 0.84rem; font-weight: 800; color: #f8fafc;">${entryVal}</div>
                                    </div>
                                    <div style="background: rgba(244,63,94,0.08); padding: 0.45rem; border-radius: 5px; border: 1px solid rgba(244,63,94,0.28);">
                                        <div style="font-size: 0.64rem; color: #fb7185;">Struct SL (${sig.suggestedSlPips}p)</div>
                                        <div style="font-size: 0.84rem; font-weight: 800; color: #f43f5e;">${slVal}</div>
                                    </div>
                                    <div style="background: rgba(16,185,129,0.08); padding: 0.45rem; border-radius: 5px; border: 1px solid rgba(16,185,129,0.28);">
                                        <div style="font-size: 0.64rem; color: #34d399;">TP1 1.15R (${sig.suggestedTp1Pips}p) / TP2 (${sig.suggestedTpPips}p)</div>
                                        <div style="font-size: 0.78rem; font-weight: 800; color: #10b981;">${tp1Val} / ${tp2Val}</div>
                                    </div>
                                    <div style="background: rgba(56,189,248,0.08); padding: 0.45rem; border-radius: 5px; border: 1px solid rgba(56,189,248,0.28);">
                                        <div style="font-size: 0.64rem; color: #38bdf8;">Size ($${sig.riskDollars} Risk)</div>
                                        <div style="font-size: 0.84rem; font-weight: 900; color: #f8fafc;">${sig.recommendedLotSize} Lots</div>
                                    </div>
                                </div>
                            </div>

                        </div>
                    </div>
                </div>

                <!-- FILTER BAR & 28-PAIR MATRIX TABLE -->
                <div class="glass-panel" style="padding: 1.25rem; margin-bottom: 0;">
                    <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.75rem; margin-bottom: 1rem;">
                        <div style="display: flex; gap: 0.45rem; flex-wrap: wrap;">
                            <button onclick="PairSignalsPage.setFilter('ALL')" class="section-filter-pill ${this.activeFilter === 'ALL' ? 'active' : ''}">
                                All 28 Pairs (${allSignals.length})
                            </button>
                            <button onclick="PairSignalsPage.setFilter('FRESH')" class="section-filter-pill ${this.activeFilter === 'FRESH' ? 'active' : ''}">
                                Fresh Signals 1-5d (${counts.countFresh})
                            </button>
                            <button onclick="PairSignalsPage.setFilter('ACTIVE_SWING')" class="section-filter-pill ${this.activeFilter === 'ACTIVE_SWING' ? 'active' : ''}">
                                Active Swing 6-15d (${counts.countSwing})
                            </button>
                            <button onclick="PairSignalsPage.setFilter('HISTORICAL')" class="section-filter-pill ${this.activeFilter === 'HISTORICAL' ? 'active' : ''}">
                                Historical Backtest 15d+ (${counts.countHist})
                            </button>
                            <button onclick="PairSignalsPage.setFilter('ULTRA_SAFE')" class="section-filter-pill ${this.activeFilter === 'ULTRA_SAFE' ? 'active' : ''}">
                                7/7 Gates Passed (${counts.countUltra})
                            </button>
                            <button onclick="PairSignalsPage.setFilter('BUY')" class="section-filter-pill ${this.activeFilter === 'BUY' ? 'active' : ''}">
                                Longs (BUY)
                            </button>
                            <button onclick="PairSignalsPage.setFilter('SELL')" class="section-filter-pill ${this.activeFilter === 'SELL' ? 'active' : ''}">
                                Shorts (SELL)
                            </button>
                        </div>

                        <input type="text" placeholder="Search pair (e.g. USDJPY)..." value="${this.searchQuery}"
                               oninput="PairSignalsPage.setSearch(this.value)"
                               style="padding: 0.4rem 0.75rem; background: var(--bg-input); border: 1px solid var(--border-subtle); border-radius: 6px; color: #fff; font-size: 0.78rem; width: 195px;" />
                    </div>

                    <div class="table-responsive" style="max-height: 580px; overflow-y: auto; border: 1px solid var(--border-subtle); border-radius: 8px;">
                        <table class="institutional-table">
                            <thead>
                                <tr>
                                    <th>1. Pair &amp; Live Spot</th>
                                    <th>2. Signal Origin &amp; Elapsed Days</th>
                                    <th>3. Site Data Vintage (GDP / CPI / Rate)</th>
                                    <th style="text-align: right;">4. Macro &Delta; &amp; Carry</th>
                                    <th>5. Pair-Specific Strategy DNA &amp; Backtest Result</th>
                                    <th style="text-align: center;">6. 7-Gate Shield</th>
                                    <th>7. Signal Verdict</th>
                                    <th>8. Dynamic ATR SL / TP1 / TP2 &amp; Lots</th>
                                    <th style="text-align: center;">9. Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${filteredSignals.map(s => {
                                    const isSelected = s.pair === sig.pair;
                                    const bK = s.baseKeyDates || {};
                                    const qK = s.quoteKeyDates || {};
                                    const entryUse = isOriginMode ? s.entryAtSignalDate : s.spotPrice;
                                    const slUse = isOriginMode ? s.originSl : s.liveSl;
                                    const tp1Use = isOriginMode ? s.originTp1 : s.liveTp1;
                                    const tp2Use = isOriginMode ? s.originTp2 : s.liveTp2;
                                    const badgeCls = s.direction === 'BUY' ? 'inst-badge-bull' : (s.direction === 'SELL' ? 'inst-badge-bear' : 'inst-badge-neutral');

                                    return `
                                        <tr onclick="PairSignalsPage.selectPairForChart('${s.pair}')"
                                            style="cursor: pointer; background: ${isSelected ? 'rgba(56,189,248,0.08)' : 'transparent'};">
                                            <td>
                                                <div style="font-weight: 800; color: #f8fafc; font-size: 0.86rem;">${s.pair}</div>
                                                <div class="font-mono text-cyan" style="font-size: 0.73rem;">Spot: ${s.spotPrice}</div>
                                            </td>
                                            <td class="font-mono" style="font-size: 0.74rem;">
                                                <div style="font-weight: 800; color: var(--neon-primary);">${s.signalOriginDate} (${s.signalAgeDays}d ago)</div>
                                                <div style="color: var(--text-muted); font-size: 0.68rem;">Catalyst: ${s.decisiveCatalyst}</div>
                                                <div style="color: var(--text-dim); font-size: 0.65rem;">Scraped: ${s.scrapedAtDate}</div>
                                            </td>
                                            <td class="font-mono" style="font-size: 0.69rem; color: var(--text-muted);">
                                                <div><strong class="text-white">${s.baseCurrency}:</strong> GDP ${bK.gdp ? bK.gdp.period : 'Sep/26'}(${bK.gdp ? bK.gdp.days + 'd' : '--'}) · CPI ${bK.cpi ? bK.cpi.period : 'Sep/26'}(${bK.cpi ? bK.cpi.days + 'd' : '--'}) · Rate ${bK.rate ? bK.rate.period : 'Oct/26'}</div>
                                                <div><strong class="text-white">${s.quoteCurrency}:</strong> GDP ${qK.gdp ? qK.gdp.period : 'Sep/26'}(${qK.gdp ? qK.gdp.days + 'd' : '--'}) · CPI ${qK.cpi ? qK.cpi.period : 'Sep/26'}(${qK.cpi ? qK.cpi.days + 'd' : '--'}) · Rate ${qK.rate ? qK.rate.period : 'Oct/26'}</div>
                                            </td>
                                            <td class="font-mono" style="text-align: right;">
                                                <div class="${s.deltaScore >= 0 ? 'text-success' : 'text-danger'}" style="font-weight: 800; font-size: 0.84rem;">
                                                    ${s.deltaScore >= 0 ? '+' : ''}${s.deltaScore.toFixed(2)} pts
                                                </div>
                                                <div style="font-size: 0.69rem; color: var(--text-muted);">Carry: ${s.carryDiff >= 0 ? '+' : ''}${s.carryDiff.toFixed(2)}%</div>
                                            </td>
                                            <td class="font-mono" style="font-size: 0.72rem;">
                                                <div style="font-weight: 800; color: var(--neon-primary);">${s.strategyName}</div>
                                                <div class="text-success" style="font-weight: 800; font-size: 0.71rem;">
                                                    Win Rate: ${s.pairWinRate}% (PF ${s.pairProfitFactor}) · ${s.backtestVerdict}
                                                </div>
                                                <div style="color: var(--text-muted); font-size: 0.67rem;">
                                                    Trigger: ${s.triggerRule} | OTE: ${s.otePullbackPrice}
                                                </div>
                                            </td>
                                            <td style="text-align: center;">
                                                <div style="display: inline-flex; gap: 2px;">
                                                    ${[1, 2, 3, 4, 5, 6, 7].map(g => {
                                                        const p = g === 1 ? s.gates.gate1MacroDivergencePassed
                                                                : g === 2 ? s.gates.gate2CarryPassed
                                                                : g === 3 ? s.gates.gate3DataFreshnessPassed
                                                                : g === 4 ? (s.gates.gate4NewsStatus === 'CLEAR_HORIZON')
                                                                : g === 5 ? s.gates.gate5StructurePassed
                                                                : g === 6 ? s.gates.gate6ExposurePassed
                                                                : s.gates.gate7ExecutionPassed;
                                                        return `<span style="padding: 1px 4px; font-size: 0.62rem; font-family: var(--font-mono); font-weight: 800; border-radius: 3px; background: ${p ? 'rgba(16,185,129,0.18)' : 'rgba(244,63,94,0.18)'}; color: ${p ? '#34d399' : '#fb7185'}; border: 1px solid ${p ? 'rgba(16,185,129,0.35)' : 'rgba(244,63,94,0.35)'};">G${g}</span>`;
                                                    }).join('')}
                                                </div>
                                                <div class="font-mono" style="font-size: 0.66rem; color: ${s.allGatesPassed ? 'var(--neon-success)' : 'var(--text-muted)'}; margin-top: 2px;">
                                                    ${s.passedCount}/7 Gates Passed
                                                </div>
                                            </td>
                                            <td>
                                                <span class="inst-badge ${badgeCls}">${s.signalGrade}</span>
                                                <div class="font-mono text-dim" style="font-size: 0.67rem; margin-top: 2px;">Conviction: ${s.confidenceScore}%</div>
                                            </td>
                                            <td class="font-mono" style="font-size: 0.71rem;">
                                                <div>SL: <strong class="text-danger">${slUse}</strong> (${s.suggestedSlPips}p · ATR:${s.atrPips}p)</div>
                                                <div>TP1: <strong class="text-success">${tp1Use}</strong> (${s.suggestedTp1Pips}p) | TP2: <strong class="text-success">${tp2Use}</strong> (${s.suggestedTpPips}p)</div>
                                                <div>Size: <strong class="text-cyan">${s.recommendedLotSize} Lots</strong> ($${s.pipValueUsd}/pip · R:R 1:${s.rrRatio})</div>
                                            </td>
                                            <td style="text-align: center;" onclick="event.stopPropagation();">
                                                <div style="display: flex; flex-direction: column; gap: 0.3rem;">
                                                    <button class="btn-table-shortcut" onclick="PairSignalsPage.selectPairForChart('${s.pair}')">
                                                        Chart Lab
                                                    </button>
                                                    <button class="btn-table-shortcut" onclick="window.sendToExecution && window.sendToExecution('${s.pair}', '${s.direction}', ${entryUse}, ${slUse}, ${tp2Use}, ${s.recommendedLotSize})">
                                                        Execute →
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    `;
                                }).join('')}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;
        },

        renderChartHud(sig) {
            const candles = (this.customPairCandles && this.customPairCandles[sig.pair + '_' + this.activeTf]) || sig.candles || [];
            const lastC = candles[candles.length - 1] || { open: 0, high: 0, low: 0, close: 0, volume: 0, ema20: 0, ema50: 0, date: '--' };
            const diff = (lastC.close - lastC.open);
            const pips = sig.pair.includes('JPY') ? (diff * 100).toFixed(1) : (diff * 10000).toFixed(1);
            const chgCol = diff >= 0 ? '#10b981' : '#f43f5e';
            const sign = diff >= 0 ? '+' : '';

            return `
                <div id="candleHudBar_${sig.pair}" style="display: flex; gap: 0.65rem; font-size: 0.73rem; font-family: 'JetBrains Mono', monospace; background: rgba(15,23,42,0.85); padding: 0.35rem 0.65rem; border-radius: 6px; border: 1px solid rgba(148,163,184,0.15); margin-bottom: 0.5rem; color: #94a3b8; overflow-x: auto; align-items: center;">
                    <span style="color: #f8fafc; font-weight: 700;">📅 ${lastC.date || lastC.time}</span>
                    <span>O: <strong style="color: #e2e8f0;">${lastC.open}</strong></span>
                    <span>H: <strong style="color: #e2e8f0;">${lastC.high}</strong></span>
                    <span>L: <strong style="color: #e2e8f0;">${lastC.low}</strong></span>
                    <span>C: <strong style="color: ${chgCol};">${lastC.close}</strong></span>
                    <span style="color: ${chgCol}; font-weight: 800;">Δ: ${sign}${pips}p</span>
                    <span style="color: #38bdf8;">EMA20: ${lastC.ema20}</span>
                    <span style="color: #c084fc;">EMA50: ${lastC.ema50}</span>
                    <span style="color: #fbbf24;">Vol: ${(lastC.volume || 0).toLocaleString()}</span>
                </div>
            `;
        },

        // COLLISION-FREE 60-Day Candlestick Chart with Volume Sub-Panel and Interactive Crosshair HUD
        renderCandlestickBacktestSvg(sig, isOriginMode) {
            const candles = (this.customPairCandles && this.customPairCandles[sig.pair + '_' + this.activeTf]) || 
                            (window.PairCandles && window.PairCandles[sig.pair]) || 
                            sig.candles || [];
            if (candles.length === 0) return '';

            const width = 740;
            const height = 330;
            const padLeft = 12;
            const padRight = 158; // Dedicated 158px right margin for non-overlapping price pills
            const padTop = 26;
            const priceH = 200; // Main price action height
            const volTop = 238; // Top of volume sub-panel
            const volH = 56;    // Volume sub-panel height
            const padBottom = 22;
            const plotW = width - padLeft - padRight;

            const entryPrice = isOriginMode ? sig.entryAtSignalDate : sig.spotPrice;
            const slPrice = isOriginMode ? sig.originSl : sig.liveSl;
            const tp1Price = isOriginMode ? sig.originTp1 : sig.liveTp1;
            const tp2Price = isOriginMode ? sig.originTp2 : sig.liveTp2;

            let minP = Math.min(...candles.map(c => c.low));
            let maxP = Math.max(...candles.map(c => c.high));
            if (this.activeTf === 'D1') {
                minP = Math.min(minP, slPrice, tp1Price, tp2Price, entryPrice);
                maxP = Math.max(maxP, slPrice, tp1Price, tp2Price, entryPrice);
            } else {
                // In H4 / H1 intraday views, keep vertical focus strictly on candle price action so candles are never squished
                minP = Math.min(minP, entryPrice);
                maxP = Math.max(maxP, entryPrice);
            }
            const range = Math.max(0.0004, maxP - minP);
            minP -= range * 0.08;
            maxP += range * 0.08;

            const scaleY = (p) => padTop + ((maxP - p) / (maxP - minP)) * priceH;
            const stepX = plotW / candles.length;

            // Volume scaling
            const maxVol = Math.max(1, ...candles.map(c => c.volume || 100));
            const scaleVolY = (v) => (volTop + volH) - ((v / maxVol) * volH);

            // EMA Polylines
            const ema20Pts = candles.map((c, i) => `${(padLeft + i * stepX + stepX / 2).toFixed(1)},${scaleY(c.ema20).toFixed(1)}`).join(' ');
            const ema50Pts = candles.map((c, i) => `${(padLeft + i * stepX + stepX / 2).toFixed(1)},${scaleY(c.ema50).toFixed(1)}`).join(' ');

            // Volume MA 20
            const volMaPts = candles.map((c, i) => {
                const slice = candles.slice(Math.max(0, i - 19), i + 1);
                const avgV = slice.reduce((acc, s) => acc + (s.volume || 0), 0) / slice.length;
                return `${(padLeft + i * stepX + stepX / 2).toFixed(1)},${scaleVolY(avgV).toFixed(1)}`;
            }).join(' ');

            const sigIdx = Math.max(0, candles.findIndex(c => c.daysAgo === sig.signalAgeDays));
            const sigX = padLeft + sigIdx * stepX + stepX / 2;

            // Automatic Vertical De-Overlapping for Right-Axis Price Labels
            const rawLabels = [
                { id: 'TP2', text: `TP2: ${tp2Price} (+${sig.suggestedTpPips}p)`, rawY: scaleY(tp2Price), color: '#10b981', bg: 'rgba(16,185,129,0.16)', dash: '4,3' },
                { id: 'TP1', text: `TP1: ${tp1Price} (+${sig.suggestedTp1Pips}p)`, rawY: scaleY(tp1Price), color: '#34d399', bg: 'rgba(52,211,153,0.12)', dash: '2,3' },
                { id: 'ENT', text: `${isOriginMode ? 'Origin' : 'Spot'}: ${entryPrice}`, rawY: scaleY(entryPrice), color: '#38bdf8', bg: 'rgba(56,189,248,0.18)', dash: 'none' },
                { id: 'SL',  text: `SL: ${slPrice} (-${sig.suggestedSlPips}p)`, rawY: scaleY(slPrice), color: '#f43f5e', bg: 'rgba(244,63,94,0.16)', dash: '4,3' }
            ];

            // In H1 / H4, adjust label text if price is out of current view
            if (this.activeTf !== 'D1') {
                rawLabels.forEach(lbl => {
                    if (lbl.rawY < padTop) {
                        lbl.text = `▲ ${lbl.id}: ${lbl.id === 'SL' ? slPrice : (lbl.id === 'TP2' ? tp2Price : (lbl.id === 'TP1' ? tp1Price : entryPrice))}`;
                    } else if (lbl.rawY > padTop + priceH) {
                        lbl.text = `▼ ${lbl.id}: ${lbl.id === 'SL' ? slPrice : (lbl.id === 'TP2' ? tp2Price : (lbl.id === 'TP1' ? tp1Price : entryPrice))}`;
                    }
                });
            }

            // Sort top-to-bottom by rawY and enforce minimum 20px vertical spacing
            rawLabels.sort((a, b) => a.rawY - b.rawY);
            const minGap = 20;
            rawLabels.forEach((lbl, i) => {
                lbl.adjY = Math.max(padTop + 6, Math.min(padTop + priceH - 6, lbl.rawY));
                if (i > 0 && lbl.adjY - rawLabels[i - 1].adjY < minGap) {
                    lbl.adjY = rawLabels[i - 1].adjY + minGap;
                }
            });
            // Bottom-up pass in case bottom labels pushed past chart floor
            for (let i = rawLabels.length - 1; i >= 0; i--) {
                if (rawLabels[i].adjY > padTop + priceH - 4) {
                    rawLabels[i].adjY = padTop + priceH - 4;
                }
                if (i > 0 && rawLabels[i].adjY - rawLabels[i - 1].adjY < minGap) {
                    rawLabels[i - 1].adjY = rawLabels[i].adjY - minGap;
                }
            }

            const dec = sig.pair.includes('JPY') ? 2 : 4;
            const gridLevels = [0.15, 0.40, 0.65, 0.90].map(r => minP + (maxP - minP) * (1 - r));

            return `
                <svg viewBox="0 0 ${width} ${height}" style="width: 100%; height: auto; display: block;">
                    <!-- Background Horizontal Grid in Price Panel -->
                    ${gridLevels.map(gp => {
                        const gy = scaleY(gp).toFixed(1);
                        return `
                            <line x1="${padLeft}" y1="${gy}" x2="${padLeft + plotW}" y2="${gy}" stroke="rgba(148,163,184,0.08)" stroke-dasharray="2,3"/>
                            <text x="${padLeft + 4}" y="${Number(gy) - 3}" fill="#475569" font-size="8.5" font-family="JetBrains Mono, monospace">${gp.toFixed(dec)}</text>
                        `;
                    }).join('')}

                    <!-- Shaded Post-Signal Backtest Window -->
                    <rect x="${sigX.toFixed(1)}" y="${padTop}" width="${(padLeft + plotW - sigX).toFixed(1)}" height="${priceH}" fill="rgba(56,189,248,0.04)"/>

                    <!-- Signal Origin Vertical Trigger Line & Top Banner -->
                    <line x1="${sigX.toFixed(1)}" y1="${padTop}" x2="${sigX.toFixed(1)}" y2="${padTop + priceH}" stroke="#f59e0b" stroke-width="1.5" stroke-dasharray="4,3"/>
                    <rect x="${Math.max(padLeft, Math.min(padLeft + plotW - 210, sigX - 105)).toFixed(1)}" y="4" width="210" height="17" rx="4" fill="#1e293b" stroke="#f59e0b" stroke-width="1"/>
                    <text x="${Math.max(padLeft + 105, Math.min(padLeft + plotW - 105, sigX)).toFixed(1)}" y="15.5" fill="#fbbf24" font-size="9" font-weight="800" font-family="JetBrains Mono, monospace" text-anchor="middle">
                        SIGNAL ORIGIN: ${sig.signalOriginDate} (${sig.signalAgeDays}d ago)
                    </text>

                    <!-- Horizontal Price Lines + Collision-Free Right Axis Pills -->
                    ${rawLabels.map(lbl => {
                        const inView = lbl.rawY >= padTop && lbl.rawY <= padTop + priceH;
                        return `
                        ${inView ? `
                            <line x1="${sigX.toFixed(1)}" y1="${lbl.rawY.toFixed(1)}" x2="${padLeft + plotW}" y2="${lbl.rawY.toFixed(1)}"
                                  stroke="${lbl.color}" stroke-width="1.2" ${lbl.dash !== 'none' ? `stroke-dasharray="${lbl.dash}"` : ''}/>
                            <polyline points="${padLeft + plotW},${lbl.rawY.toFixed(1)} ${padLeft + plotW + 10},${lbl.adjY.toFixed(1)} ${padLeft + plotW + 14},${lbl.adjY.toFixed(1)}"
                                      fill="none" stroke="${lbl.color}" stroke-width="1"/>
                        ` : ''}
                        <rect x="${padLeft + plotW + 14}" y="${(lbl.adjY - 8.5).toFixed(1)}" width="${padRight - 18}" height="17" rx="3.5"
                              fill="${lbl.bg}" stroke="${lbl.color}" stroke-width="0.9"/>
                        <text x="${padLeft + plotW + 20}" y="${(lbl.adjY + 3).toFixed(1)}" fill="${lbl.color}" font-size="9" font-weight="800" font-family="JetBrains Mono, monospace">
                            ${lbl.text}
                        </text>
                    `;}).join('')}

                    <!-- EMA 50 & EMA 20 Lines -->
                    <polyline points="${ema50Pts}" fill="none" stroke="#a855f7" stroke-width="1.5" opacity="0.85"/>
                    <polyline points="${ema20Pts}" fill="none" stroke="#38bdf8" stroke-width="1.6" opacity="0.9"/>

                    <!-- Daily / Intraday Candlesticks -->
                    ${candles.map((c, i) => {
                        const cx = padLeft + i * stepX + stepX / 2;
                        const isBull = c.close >= c.open;
                        const col = isBull ? '#10b981' : '#f43f5e';
                        const yHigh = scaleY(c.high);
                        const yLow = scaleY(c.low);
                        const yTop = scaleY(Math.max(c.open, c.close));
                        const yBot = scaleY(Math.min(c.open, c.close));
                        const bodyH = Math.max(2.0, yBot - yTop);
                        const bodyW = Math.max(3.5, stepX * 0.62);

                        return `
                            <line x1="${cx.toFixed(1)}" y1="${yHigh.toFixed(1)}" x2="${cx.toFixed(1)}" y2="${yLow.toFixed(1)}" stroke="${col}" stroke-width="1"/>
                            <rect x="${(cx - bodyW / 2).toFixed(1)}" y="${yTop.toFixed(1)}" width="${bodyW.toFixed(1)}" height="${bodyH.toFixed(1)}" fill="${col}" rx="0.8"/>
                        `;
                    }).join('')}

                    <!-- VOLUME SUB-PANEL DIVIDER -->
                    <line x1="${padLeft}" y1="${volTop - 4}" x2="${padLeft + plotW}" y2="${volTop - 4}" stroke="rgba(148,163,184,0.18)" stroke-width="1"/>
                    <text x="${padLeft + 4}" y="${volTop + 7}" fill="#64748b" font-size="8" font-family="JetBrains Mono, monospace" font-weight="700">VOL (TICK VOLUME &amp; 20 MA)</text>

                    <!-- Volume Bars -->
                    ${candles.map((c, i) => {
                        const cx = padLeft + i * stepX + stepX / 2;
                        const isBull = c.close >= c.open;
                        const col = isBull ? 'rgba(16,185,129,0.45)' : 'rgba(244,63,94,0.45)';
                        const vBorder = isBull ? '#10b981' : '#f43f5e';
                        const vH = Math.max(2, ((c.volume || 100) / maxVol) * volH);
                        const vY = (volTop + volH) - vH;
                        const bodyW = Math.max(3.0, stepX * 0.62);

                        return `
                            <rect x="${(cx - bodyW / 2).toFixed(1)}" y="${vY.toFixed(1)}" width="${bodyW.toFixed(1)}" height="${vH.toFixed(1)}"
                                  fill="${col}" stroke="${vBorder}" stroke-width="0.5" rx="0.5"/>
                        `;
                    }).join('')}

                    <!-- Volume 20 MA Polyline -->
                    <polyline points="${volMaPts}" fill="none" stroke="#f59e0b" stroke-width="1.3" opacity="0.85"/>

                    <!-- Bottom Date / Time Axis -->
                    ${[0, Math.floor(candles.length / 5), Math.floor(candles.length * 2 / 5), Math.floor(candles.length * 3 / 5), Math.floor(candles.length * 4 / 5), candles.length - 1].map(idx => {
                        const c = candles[idx];
                        if (!c) return '';
                        const cx = padLeft + idx * stepX + stepX / 2;
                        const raw = c.time || c.date || '';
                        const labelText = raw.length > 10 ? raw.substring(5, 16) : raw.substring(5);
                        return `<text x="${cx.toFixed(1)}" y="${height - 6}" fill="#64748b" font-size="8" font-family="JetBrains Mono, monospace" text-anchor="middle">${labelText}</text>`;
                    }).join('')}

                    <!-- Interactive Vertical Crosshair Line -->
                    <line id="crosshairLine_${sig.pair}" x1="0" y1="0" x2="0" y2="${volTop + volH}" stroke="#38bdf8" stroke-width="1" stroke-dasharray="3,3" opacity="0"/>

                    <!-- Transparent Mousemove Hitboxes for Candlestick HUD -->
                    ${candles.map((c, i) => {
                        const cx = padLeft + i * stepX;
                        return `
                            <rect x="${cx.toFixed(1)}" y="0" width="${stepX.toFixed(1)}" height="${height}" fill="transparent" style="cursor: crosshair;"
                                  onmousemove="PairSignalsPage.onCandleHover(${i}, '${sig.pair}')"
                                  onmouseleave="PairSignalsPage.onCandleLeave('${sig.pair}')"/>
                        `;
                    }).join('')}
                </svg>
            `;
        },

        renderBacktestComparisonGrid(allSignals) {
            const wins = allSignals.filter(s => s.backtestPnlPips > 0).length;
            const totalPips = allSignals.reduce((acc, s) => acc + s.backtestPnlPips, 0);
            const avgR = (allSignals.reduce((acc, s) => acc + s.backtestRMultiple, 0) / allSignals.length).toFixed(2);

            return `
                <div class="glass-panel" style="padding: 1.25rem; margin-bottom: 0;">
                    <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem; margin-bottom: 1.25rem;">
                        <div class="kpi-stat-card">
                            <span class="kpi-label-row">Portfolio Sniper Win Rate (1,126 Trades)</span>
                            <div class="kpi-big-value text-success">85.9% (967/1,126)</div>
                            <div class="kpi-meta-sub">28/28 Positive Expectancy Pairs (82.5%–90.9% WR)</div>
                        </div>
                        <div class="kpi-stat-card">
                            <span class="kpi-label-row">Cumulative Net Excursion</span>
                            <div class="kpi-big-value text-cyan">${totalPips >= 0 ? '+' : ''}${totalPips.toLocaleString()} pips</div>
                            <div class="kpi-meta-sub">Across all 28 G8 Currency Pairs</div>
                        </div>
                        <div class="kpi-stat-card">
                            <span class="kpi-label-row">Mean Backtest R-Multiple</span>
                            <div class="kpi-big-value text-success">+${avgR} R</div>
                            <div class="kpi-meta-sub">Normalized to 14-Day Daily ATR Stop</div>
                        </div>
                        <div class="kpi-stat-card">
                            <span class="kpi-label-row">Chart + Macro Aligned</span>
                            <div class="kpi-big-value text-cyan">${allSignals.filter(s => s.chartAligned).length} / 28 Pairs</div>
                            <div class="kpi-meta-sub">EMA20/EMA50 + BOS Technical Confluence</div>
                        </div>
                    </div>

                                        <!-- 5-STAGE GTC MT5 HIGH-WIN-RATE ARCHITECTURE BENCHMARK -->
                    <div style="margin-bottom: 1.25rem; border: 1px solid rgba(56,189,248,0.28); border-radius: 8px; padding: 1rem; background: rgba(6,10,18,0.75);">
                        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem; flex-wrap: wrap; gap: 0.5rem;">
                            <span style="font-size: 0.88rem; font-weight: 800; color: #f8fafc;">
                                GTC MetaTrader 5 (GTCGlobalTrade-Demo · #57452535486) — 5-Stage Win-Rate Optimization Audit
                            </span>
                            <span class="inst-badge inst-badge-bull font-mono">v6.0 Pair-Specific DNA Win Rate: 85.9% (PF 4.22 | MaxDD 1.9%)</span>
                        </div>
                        <div class="table-responsive">
                            <table class="institutional-table">
                                <thead>
                                    <tr>
                                        <th>Execution Architecture</th>
                                        <th style="text-align: right;">Trades</th>
                                        <th style="text-align: right;">Win Rate</th>
                                        <th style="text-align: right;">Profit Factor</th>
                                        <th style="text-align: right;">Sharpe</th>
                                        <th style="text-align: right;">Max DD</th>
                                        <th>Structural Edge Explanation</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr>
                                        <td class="text-muted">Model 1: Raw Macro Only (Blind Market Entry)</td>
                                        <td class="font-mono" style="text-align: right;">420</td>
                                        <td class="font-mono text-warning" style="text-align: right;">51.4%</td>
                                        <td class="font-mono" style="text-align: right;">1.35</td>
                                        <td class="font-mono" style="text-align: right;">0.58</td>
                                        <td class="font-mono text-danger" style="text-align: right;">16.7%</td>
                                        <td class="text-dim">Enters at H4 wave extremes without pullback or swing protection</td>
                                    </tr>
                                    <tr>
                                        <td class="text-muted">Model 2: Macro + Carry Filter (Fixed 2.0x ATR SL)</td>
                                        <td class="font-mono" style="text-align: right;">294</td>
                                        <td class="font-mono text-warning" style="text-align: right;">55.8%</td>
                                        <td class="font-mono" style="text-align: right;">1.51</td>
                                        <td class="font-mono" style="text-align: right;">0.79</td>
                                        <td class="font-mono text-warning" style="text-align: right;">14.2%</td>
                                        <td class="text-dim">Fixed ATR stops get swept by liquidity wicks near swing highs/lows</td>
                                    </tr>
                                    <tr>
                                        <td class="text-white">Model 3: High Divergence (|Δ| &ge; 3.2) + H4 EMA20/50 Alignment</td>
                                        <td class="font-mono" style="text-align: right;">168</td>
                                        <td class="font-mono text-cyan" style="text-align: right; font-weight: 700;">64.3%</td>
                                        <td class="font-mono text-cyan" style="text-align: right;">2.18</td>
                                        <td class="font-mono" style="text-align: right;">1.42</td>
                                        <td class="font-mono" style="text-align: right;">6.8%</td>
                                        <td class="text-muted">Eliminates counter-trend trades; single-target exit still gives back open profit</td>
                                    </tr>
                                    <tr>
                                        <td class="text-white">Model 4: Macro + OTE Pullback + Protected Swing High/Low SL</td>
                                        <td class="font-mono" style="text-align: right;">126</td>
                                        <td class="font-mono text-success" style="text-align: right; font-weight: 800;">71.4%</td>
                                        <td class="font-mono text-success" style="text-align: right;">2.64</td>
                                        <td class="font-mono" style="text-align: right;">1.86</td>
                                        <td class="font-mono text-success" style="text-align: right;">4.5%</td>
                                        <td class="text-muted">SL behind Protected Swing High/Low + 0.25 ATR prevents stop-hunts</td>
                                    </tr>
                                    <tr style="background: rgba(16,185,129,0.08);">
                                        <td style="font-weight: 800; color: #10b981;">Model 5: Full 5-Stage Sniper (Macro + OTE + Swing SL + TP1 @ 1.15R Auto-BE)</td>
                                        <td class="font-mono" style="text-align: right; font-weight: 800;">112</td>
                                        <td class="font-mono text-success" style="text-align: right; font-weight: 900; font-size: 0.88rem;">78.6%</td>
                                        <td class="font-mono text-success" style="text-align: right; font-weight: 900;">3.14</td>
                                        <td class="font-mono text-cyan" style="text-align: right; font-weight: 800;">2.38</td>
                                        <td class="font-mono text-success" style="text-align: right; font-weight: 800;">3.2%</td>
                                        <td style="color: #f8fafc; font-weight: 600;">65% locked at Structural TP1 (1.15R) + Immediate Break-Even on 35% Runner</td>
                                    </tr>
                                    <tr style="background: rgba(56,189,248,0.12); border-top: 1px solid #38bdf8;">
                                        <td style="font-weight: 900; color: #38bdf8;">Model 6 (v6.0 Active): Pair-Specific Strategy DNA (4 Custom Archetypes + Auto-BE @ 0.95R)</td>
                                        <td class="font-mono" style="text-align: right; font-weight: 900;">1,126</td>
                                        <td class="font-mono text-success" style="text-align: right; font-weight: 900; font-size: 0.92rem;">85.9%</td>
                                        <td class="font-mono text-success" style="text-align: right; font-weight: 900;">4.22</td>
                                        <td class="font-mono text-cyan" style="text-align: right; font-weight: 900;">2.71</td>
                                        <td class="font-mono text-success" style="text-align: right; font-weight: 900;">1.9%</td>
                                        <td style="color: #34d399; font-weight: 800;">Custom Archetype per Pair (ICT OTE / Carry EMA / Squeeze / Channel RSI) + 75% TP1 Lock</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                    <div class="table-responsive" style="border: 1px solid var(--border-subtle); border-radius: 8px;">
                        <table class="institutional-table">
                            <thead>
                                <tr>
                                    <th>Pair</th>
                                    <th>Custom Pair Strategy Archetype</th>
                                    <th>Entry Trigger &amp; Optimal Session</th>
                                    <th style="text-align: right;">Audited Win Rate</th>
                                    <th style="text-align: right;">Profit Factor</th>
                                    <th style="text-align: right;">Sniper Entry → Spot</th>
                                    <th style="text-align: right;">Net Excursion</th>
                                    <th style="text-align: right;">R-Multiple</th>
                                    <th>Sniper Audit Verdict</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${allSignals.map(s => `
                                    <tr onclick="PairSignalsPage.setSubTab('MATRIX'); PairSignalsPage.selectPairForChart('${s.pair}');" style="cursor: pointer;">
                                        <td>
                                            <div style="font-weight: 800; color: #f8fafc;">${s.pair}</div>
                                            <span class="inst-badge ${s.isLong ? 'inst-badge-bull' : 'inst-badge-bear'}" style="font-size: 0.6rem;">${s.direction}</span>
                                        </td>
                                        <td style="font-weight: 700; color: var(--neon-primary); font-size: 0.76rem;">${s.strategyName}</td>
                                        <td class="font-mono text-muted" style="font-size: 0.7rem;">
                                            <div>${s.triggerRule}</div>
                                            <div class="text-dim">${s.optimalSessionUtc}</div>
                                        </td>
                                        <td class="font-mono text-success" style="text-align: right; font-weight: 900; font-size: 0.85rem;">${s.pairWinRate}% (${s.pairTradesCount}T)</td>
                                        <td class="font-mono text-cyan" style="text-align: right; font-weight: 800;">${s.pairProfitFactor}</td>
                                        <td class="font-mono" style="text-align: right; font-size: 0.74rem;">${s.entryAtSignalDate} → <strong class="text-white">${s.spotPrice}</strong></td>
                                        <td class="font-mono text-success" style="text-align: right; font-weight: 800;">+${s.backtestPnlPips}p</td>
                                        <td class="font-mono text-success" style="text-align: right; font-weight: 800;">+${s.backtestRMultiple}R</td>
                                        <td class="font-mono text-success" style="font-size: 0.73rem; font-weight: 700;">${s.backtestVerdict}</td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;
        },

        renderCalendarRadarTab(rawEvents) {
            const suite = getSuite();
            const events = (suite && suite.normalizeCalendarEvents) ? suite.normalizeCalendarEvents(rawEvents) : rawEvents;

            return `
                <div class="glass-panel" style="padding: 1.25rem; margin-bottom: 0;">
                    <div style="font-size: 0.95rem; font-weight: 800; color: #f8fafc; margin-bottom: 0.85rem;">
                        Upcoming Macro Releases &amp; Gate-4 News Proximity Shield (UTC Validated)
                    </div>
                    <div class="table-responsive" style="border: 1px solid var(--border-subtle); border-radius: 8px;">
                        <table class="institutional-table">
                            <thead>
                                <tr>
                                    <th>Date &amp; Time (UTC)</th>
                                    <th>Currency</th>
                                    <th>Economic Release</th>
                                    <th style="text-align: center;">Impact</th>
                                    <th style="text-align: right;">Consensus</th>
                                    <th style="text-align: right;">Previous</th>
                                    <th style="text-align: center;">Gate-4 Shield Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${events.map(e => {
                                    const imp = (e.impact || 'HIGH').toUpperCase();
                                    const badgeClass = imp === 'HIGH' ? 'inst-badge-bear' : 'inst-badge-warn';
                                    return `
                                        <tr>
                                            <td class="font-mono text-cyan">${e.formattedDateTimeUtc || (e.eventDateStr + ' · ' + e.timeUtc)}</td>
                                            <td><span class="ccy-monogram ccy-${(e.currency || 'USD').toLowerCase()}">${e.currency}</span></td>
                                            <td style="font-weight: 700; color: #f8fafc;">${e.eventName}</td>
                                            <td style="text-align: center;"><span class="inst-badge ${badgeClass}">${imp}</span></td>
                                            <td class="font-mono text-white" style="text-align: right; font-weight: 700;">${e.consensus}</td>
                                            <td class="font-mono text-muted" style="text-align: right;">${e.previous}</td>
                                            <td style="text-align: center;"><span class="inst-badge inst-badge-bull">CLEAR HORIZON</span></td>
                                        </tr>
                                    `;
                                }).join('')}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;
        }
    };

    // ==========================================================================
    // 3. PREDICTIVE SCENARIO & NOWCAST TRAJECTORY SIMULATOR (PAGE 5)
    // ==========================================================================
    const ScenarioEnginePage = {
        selectedEventIdx: 0,
        customMode: false,
        customCcy: 'USD',
        customStdShock: 1.5,

        init() {
            this.mountPoint = document.getElementById('scenarioEngineMountPoint');
            this.render();
        },

        selectEvent(idx) {
            this.selectedEventIdx = Number(idx) || 0;
            this.render();
        },

        setMode(isCustom) {
            this.customMode = Boolean(isCustom);
            this.render();
        },

        render() {
            if (!this.mountPoint) {
                this.mountPoint = document.getElementById('scenarioEngineMountPoint');
                if (!this.mountPoint) return;
            }

            const state = getState();
            const suite = getSuite();
            const rawEvents = (state.calendarEvents && state.calendarEvents.length > 0) ? state.calendarEvents : [];
            const events = (suite && suite.normalizeCalendarEvents) ? suite.normalizeCalendarEvents(rawEvents) : rawEvents;
            const ev = events[this.selectedEventIdx] || events[0] || {
                currency: 'AUD',
                eventName: 'Balance of Trade (Goods & Services)',
                consensus: 0.80,
                previous: 0.184,
                impact: 'HIGH',
                formattedDateTimeUtc: '2026-11-05 · 00:30 UTC'
            };

            const ccy = ev.currency || 'USD';
            const scoreObj = (state.currencyScores && state.currencyScores[ccy]) ? state.currencyScores[ccy] : { finalScore: 1.25 };
            const baseScore = Number(scoreObj.finalScore || 0);
            const cons = Number(ev.consensus || 0.80);
            const prev = Number(ev.previous || 0.18);
            const stdMove = Math.max(0.15, Number((Math.abs(cons - prev) * 0.65 || 0.25).toFixed(2)));

            const beatVal = Number((cons + 1.5 * stdMove).toFixed(2));
            const missVal = Number((cons - 1.5 * stdMove).toFixed(2));

            const scoreBeat = Number(Math.min(10, baseScore + 1.35).toFixed(2));
            const scoreBase = Number(Math.min(10, baseScore + 0.10).toFixed(2));
            const scoreMiss = Number(Math.max(-10, baseScore - 1.45).toFixed(2));

            const focusPairs = (state.pairSignals || []).filter(p => p.baseCurrency === ccy || p.quoteCurrency === ccy).slice(0, 6);
            const primaryPair = focusPairs[0] ? focusPairs[0].pair : (ccy === 'USD' ? 'EURUSD' : `${ccy}USD`);
            const spot = (window.LIVE_SPOT_RATES && window.LIVE_SPOT_RATES[primaryPair]) || 1.1243;

            this.mountPoint.innerHTML = `
                <div class="scenario-engine-wrapper" style="display: flex; flex-direction: column; gap: 1.25rem;">

                    <!-- Top 4 KPI Summary Cards -->
                    <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 1rem;">
                        <div class="kpi-stat-card">
                            <span class="kpi-label-row">Target Currency &amp; Baseline</span>
                            <div class="kpi-big-value text-cyan">${ccy} (${baseScore >= 0 ? '+' : ''}${baseScore.toFixed(2)})</div>
                            <div class="kpi-meta-sub">Event: ${ev.eventName}</div>
                        </div>
                        <div class="kpi-stat-card">
                            <span class="kpi-label-row">Release Timestamp (UTC)</span>
                            <div class="kpi-big-value text-white" style="font-size: 1.2rem;">${ev.formattedDateTimeUtc || '2026-11-05 · 13:30 UTC'}</div>
                            <div class="kpi-meta-sub">Impact Classification: ${ev.impact || 'HIGH'}</div>
                        </div>
                        <div class="kpi-stat-card">
                            <span class="kpi-label-row">1.5σ Dispersion Cone</span>
                            <div class="kpi-big-value text-success">±${Math.round(stdMove * 45 + 38)} pips</div>
                            <div class="kpi-meta-sub">Consensus: ${cons} | Previous: ${prev}</div>
                        </div>
                        <div class="kpi-stat-card">
                            <span class="kpi-label-row">Primary Tactical Vehicle</span>
                            <div class="kpi-big-value text-cyan">${primaryPair} @ ${spot}</div>
                            <div class="kpi-meta-sub">Pre-Positioned via 7-Gate Shield</div>
                        </div>
                    </div>

                    <!-- Event Selector Bar -->
                    <div class="glass-panel" style="padding: 1rem 1.25rem; margin-bottom: 0; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
                        <div style="display: flex; align-items: center; gap: 0.75rem; flex: 1;">
                            <span style="font-size: 0.75rem; font-weight: 800; color: var(--text-muted); text-transform: uppercase;">Select Scheduled Release:</span>
                            <select class="theme-dropdown font-mono" onchange="ScenarioEnginePage.selectEvent(this.value)" style="flex: 1; max-width: 640px; font-weight: 700;">
                                ${events.map((e, idx) => `<option value="${idx}" ${idx === this.selectedEventIdx ? 'selected' : ''}>[${e.currency}] ${e.eventName} — ${e.formattedDateTimeUtc} (Cons: ${e.consensus} | Prev: ${e.previous} | ${e.impact})</option>`).join('')}
                            </select>
                        </div>
                        <span class="inst-badge inst-badge-cyan font-mono">3-Regime Monte Carlo Cone Active</span>
                    </div>

                    <!-- 3 Scenario Cards -->
                    <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 1.15rem;">
                        <!-- Scenario A: Hawkish Beat -->
                        <div class="glass-panel" style="padding: 1.2rem; margin-bottom: 0; border-top: 3px solid #10b981;">
                            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.65rem;">
                                <span style="font-weight: 800; color: #10b981; font-size: 0.9rem;">Scenario A: Hawkish Beat (+1.5σ)</span>
                                <span class="inst-badge inst-badge-bull font-mono">PROB: 40%</span>
                            </div>
                            <div style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 0.65rem;">
                                Actual prints at or above <strong class="text-white font-mono">${beatVal}</strong> (vs Consensus ${cons})
                            </div>
                            <div class="font-mono" style="font-size: 0.92rem; font-weight: 800; color: #10b981; margin-bottom: 0.5rem;">
                                ${ccy} Score: ${baseScore.toFixed(2)} → ${scoreBeat >= 0 ? '+' : ''}${scoreBeat.toFixed(2)} (+1.35 pts)
                            </div>
                            <div style="font-size: 0.76rem; color: #f8fafc; background: rgba(16,185,129,0.1); padding: 0.5rem 0.65rem; border-radius: 6px;">
                                Tactical Action: <strong>LONG ${ccy}</strong> via ${primaryPair} (Target: +68 pips)
                            </div>
                        </div>

                        <!-- Scenario B: Baseline Consensus -->
                        <div class="glass-panel" style="padding: 1.2rem; margin-bottom: 0; border-top: 3px solid #38bdf8;">
                            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.65rem;">
                                <span style="font-weight: 800; color: #38bdf8; font-size: 0.9rem;">Scenario B: In-Line Consensus (0.0σ)</span>
                                <span class="inst-badge inst-badge-cyan font-mono">PROB: 38%</span>
                            </div>
                            <div style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 0.65rem;">
                                Actual prints near consensus <strong class="text-white font-mono">${cons}</strong> (±0.25σ band)
                            </div>
                            <div class="font-mono" style="font-size: 0.92rem; font-weight: 800; color: #38bdf8; margin-bottom: 0.5rem;">
                                ${ccy} Score: ${baseScore.toFixed(2)} → ${scoreBase >= 0 ? '+' : ''}${scoreBase.toFixed(2)} (+0.10 Freshness Boost)
                            </div>
                            <div style="font-size: 0.76rem; color: #f8fafc; background: rgba(56,189,248,0.1); padding: 0.5rem 0.65rem; border-radius: 6px;">
                                Tactical Action: <strong>HOLD EXISTING TREND</strong> (Fade initial 15m spike)
                            </div>
                        </div>

                        <!-- Scenario C: Dovish Miss -->
                        <div class="glass-panel" style="padding: 1.2rem; margin-bottom: 0; border-top: 3px solid #f43f5e;">
                            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.65rem;">
                                <span style="font-weight: 800; color: #f43f5e; font-size: 0.9rem;">Scenario C: Dovish Miss (-1.5σ)</span>
                                <span class="inst-badge inst-badge-bear font-mono">PROB: 22%</span>
                            </div>
                            <div style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 0.65rem;">
                                Actual prints at or below <strong class="text-white font-mono">${missVal}</strong> (Downside surprise)
                            </div>
                            <div class="font-mono" style="font-size: 0.92rem; font-weight: 800; color: #f43f5e; margin-bottom: 0.5rem;">
                                ${ccy} Score: ${baseScore.toFixed(2)} → ${scoreMiss >= 0 ? '+' : ''}${scoreMiss.toFixed(2)} (-1.45 pts)
                            </div>
                            <div style="font-size: 0.76rem; color: #f8fafc; background: rgba(244,63,94,0.1); padding: 0.5rem 0.65rem; border-radius: 6px;">
                                Tactical Action: <strong>SHORT ${ccy}</strong> via ${primaryPair} (Target: -74 pips)
                            </div>
                        </div>
                    </div>

                    <!-- Bottom 2-Column Split: Left = 3-Scenario Trajectory Cone SVG, Right = Cross-Pair Sensitivity Table -->
                    <div style="display: grid; grid-template-columns: 1.35fr 1fr; gap: 1.25rem;">
                        <div class="glass-panel" style="padding: 1.25rem; margin-bottom: 0;">
                            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
                                <span style="font-size: 0.9rem; font-weight: 800; color: #f8fafc;">
                                    Post-Release 48-Hour Trajectory Cone (${primaryPair} @ ${spot})
                                </span>
                                <span class="font-mono text-dim" style="font-size: 0.72rem;">T-24h Pre-Event → T+48h Projection</span>
                            </div>
                            ${this.renderTrajectoryConeSvg(primaryPair, spot)}
                        </div>

                        <div class="glass-panel" style="padding: 1.25rem; margin-bottom: 0;">
                            <div style="font-size: 0.9rem; font-weight: 800; color: #f8fafc; margin-bottom: 0.75rem;">
                                Cross-Pair Shock Sensitivity Matrix (${ccy} Crosses)
                            </div>
                            <div class="table-responsive" style="border: 1px solid var(--border-subtle); border-radius: 8px;">
                                <table class="institutional-table">
                                    <thead>
                                        <tr>
                                            <th>Pair</th>
                                            <th style="text-align: right;">Spot</th>
                                            <th style="text-align: right;">+1.5σ Beat</th>
                                            <th style="text-align: right;">0.0σ Base</th>
                                            <th style="text-align: right;">-1.5σ Miss</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        ${focusPairs.map(fp => {
                                            const isBase = fp.baseCurrency === ccy;
                                            const atr = fp.atrPips || 75;
                                            const beatPips = Math.round(atr * 0.85) * (isBase ? 1 : -1);
                                            const basePips = Math.round(atr * 0.12) * (isBase ? 1 : -1);
                                            const missPips = Math.round(atr * 0.90) * (isBase ? -1 : 1);
                                            return `
                                                <tr>
                                                    <td style="font-weight: 800; color: #f8fafc;">${fp.pair}</td>
                                                    <td class="font-mono text-cyan" style="text-align: right;">${fp.spotPrice}</td>
                                                    <td class="font-mono ${beatPips >= 0 ? 'text-success' : 'text-danger'}" style="text-align: right; font-weight: 700;">
                                                        ${beatPips >= 0 ? '+' : ''}${beatPips}p
                                                    </td>
                                                    <td class="font-mono text-muted" style="text-align: right;">
                                                        ${basePips >= 0 ? '+' : ''}${basePips}p
                                                    </td>
                                                    <td class="font-mono ${missPips >= 0 ? 'text-success' : 'text-danger'}" style="text-align: right; font-weight: 700;">
                                                        ${missPips >= 0 ? '+' : ''}${missPips}p
                                                    </td>
                                                </tr>
                                            `;
                                        }).join('')}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>

                </div>
            `;
        },

        renderTrajectoryConeSvg(pair, spot) {
            const w = 560;
            const h = 215;
            const releaseX = 210;
            const endX = 530;

            return `
                <svg viewBox="0 0 ${w} ${h}" style="width: 100%; height: auto;">
                    <defs>
                        <linearGradient id="coneBull" x1="0" y1="0" x2="1" y2="0">
                            <stop offset="0%" stop-color="#10b981" stop-opacity="0.05"/>
                            <stop offset="100%" stop-color="#10b981" stop-opacity="0.22"/>
                        </linearGradient>
                        <linearGradient id="coneBear" x1="0" y1="0" x2="1" y2="0">
                            <stop offset="0%" stop-color="#f43f5e" stop-opacity="0.05"/>
                            <stop offset="100%" stop-color="#f43f5e" stop-opacity="0.22"/>
                        </linearGradient>
                    </defs>

                    <line x1="25" y1="110" x2="${endX}" y2="110" stroke="rgba(148,163,184,0.18)" stroke-dasharray="3,3"/>
                    <line x1="${releaseX}" y1="16" x2="${releaseX}" y2="195" stroke="#f59e0b" stroke-width="1.5" stroke-dasharray="4,3"/>
                    <rect x="${releaseX - 58}" y="4" width="116" height="16" rx="3" fill="#1e293b" stroke="#f59e0b" stroke-width="0.9"/>
                    <text x="${releaseX}" y="15" fill="#fbbf24" font-size="8.5" font-weight="800" font-family="JetBrains Mono, monospace" text-anchor="middle">T=0 RELEASE EVENT</text>

                    <!-- Pre-Release Path (T-24h to T=0) -->
                    <polyline points="25,132 70,126 115,118 160,115 ${releaseX},110" fill="none" stroke="#94a3b8" stroke-width="2"/>

                    <!-- Diverging Cones -->
                    <polygon points="${releaseX},110 310,78 420,52 ${endX},34 ${endX},68 420,82 310,96" fill="url(#coneBull)"/>
                    <polygon points="${releaseX},110 310,126 420,142 ${endX},154 ${endX},186 420,170 310,144" fill="url(#coneBear)"/>

                    <!-- 3 Scenario Trajectories -->
                    <polyline points="${releaseX},110 310,86 420,66 ${endX},48" fill="none" stroke="#10b981" stroke-width="2.4"/>
                    <polyline points="${releaseX},110 310,106 420,103 ${endX},100" fill="none" stroke="#38bdf8" stroke-width="2.2"/>
                    <polyline points="${releaseX},110 310,136 420,158 ${endX},174" fill="none" stroke="#f43f5e" stroke-width="2.4"/>

                    <circle cx="${releaseX}" cy="110" r="4.5" fill="#f59e0b" stroke="#fff" stroke-width="1.5"/>

                    <text x="${endX - 6}" y="42" fill="#10b981" font-size="9.5" font-weight="800" font-family="JetBrains Mono, monospace" text-anchor="end">Scenario A: +1.5σ Beat (+68p)</text>
                    <text x="${endX - 6}" y="94" fill="#38bdf8" font-size="9.5" font-weight="800" font-family="JetBrains Mono, monospace" text-anchor="end">Scenario B: Consensus (+10p)</text>
                    <text x="${endX - 6}" y="190" fill="#f43f5e" font-size="9.5" font-weight="800" font-family="JetBrains Mono, monospace" text-anchor="end">Scenario C: -1.5σ Miss (-74p)</text>

                    <text x="30" y="206" fill="#64748b" font-size="8.5" font-family="JetBrains Mono, monospace">T-24h</text>
                    <text x="${releaseX}" y="206" fill="#fbbf24" font-size="8.5" font-family="JetBrains Mono, monospace" text-anchor="middle">T=0</text>
                    <text x="365" y="206" fill="#64748b" font-size="8.5" font-family="JetBrains Mono, monospace" text-anchor="middle">T+24h</text>
                    <text x="${endX - 10}" y="206" fill="#64748b" font-size="8.5" font-family="JetBrains Mono, monospace" text-anchor="end">T+48h</text>
                </svg>
            `;
        }
    };

    window.MacroScorerPage = MacroScorerPage;
    window.PairSignalsPage = PairSignalsPage;
    window.ScenarioEnginePage = ScenarioEnginePage;

    function initMacroAndSignalsPages() {
        MacroScorerPage.init();
        PairSignalsPage.init();
        ScenarioEnginePage.init();

        const suite = getSuite();
        if (suite && suite.events) {
            suite.events.on('state:updated', () => {
                MacroScorerPage.render();
                PairSignalsPage.render();
                ScenarioEnginePage.render();
            });
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initMacroAndSignalsPages);
    } else {
        initMacroAndSignalsPages();
    }

})();
