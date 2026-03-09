// ==UserScript==
// @name         Wplace-RPC
// @version      1.5
// @author       Ema
// @description  Discord RPC for wplace.live
//
// @grant        none
//
// @match        https://*.wplace.live/*
// @match        https://wplace.live/*
// ==/UserScript==

(function () {
    "use strict";

    const WS_URL = "ws://127.0.0.1:6680";
    const INTERVAL_MS = 3000;
    const RECONNECT_DELAY_MS = 5000;

    let cachedLevel = null;
    let cachedPixels = null;

    function getUserInfo() {
        const levelEl = document.querySelector('.text-secondary .font-semibold');
        if (levelEl) {
            const match = levelEl.textContent.match(/Level\s*(\d+)/i);
            if (match) cachedLevel = match[1];
        }

        // Píxeles pintados: span con text-primary y font-semibold
        const pixelsEl = document.querySelector('.text-primary.font-semibold');
        if (pixelsEl) {
            const match = pixelsEl.textContent.match(/^[\d,]+/);
            if (match) cachedPixels = match[0];
        }

        const colorGrid = document.querySelector('div[class*="grid-cols-8"]');
        const isPainting = !!(colorGrid && colorGrid.offsetParent !== null);

        return { level: cachedLevel, pixels: cachedPixels, isPainting };
    }

    // ─── WebSocket manager ───────────────────────────────────────────────────────

    class Bridge {
        constructor(url) {
            this.url = url;
            this.ws = null;
            this._connect();
        }

        _connect() {
            this.ws = new WebSocket(this.url);
            this.ws.onopen = () => console.log("[Wplace-RPC] Bridge connected");
            this.ws.onclose = () => {
                console.log(`[Wplace-RPC] Bridge disconnected — retrying in ${RECONNECT_DELAY_MS / 1000}s`);
                setTimeout(() => this._connect(), RECONNECT_DELAY_MS);
            };
            this.ws.onerror = () => { /* onclose fires after onerror, handles retry */ };
        }

        send(payload) {
            if (this.ws?.readyState === WebSocket.OPEN) {
                this.ws.send(JSON.stringify(payload));
            }
        }
    }

    // ─── Main ─────────────────────────────────────────────────────────────────────

    console.log("[Wplace-RPC] Loaded on wplace.live");

    const bridge = new Bridge(WS_URL);

    let lastState = null;
    let stateStart = null;

    setInterval(() => {
        if (document.hidden) return; // Quitar si no quieres que se desactive con inactividad
        // Texto que se muestra
        const { level, pixels, isPainting } = getUserInfo();
        const currentState = isPainting ? "painting" : "browsing";

        // Reiniciar timer si cambió el estado
        if (currentState !== lastState) {
            lastState = currentState;
            stateStart = Date.now();
        }

        // "Level 214 • 115,127 pixels"
        const parts = [];
        if (level) parts.push(`Level ${level}`);
        if (pixels) parts.push(`${pixels} Pixels`);
        const stateText = parts.length > 0 ? parts.join(" • ") : "wplace.live";

        const payload = {
            source: "wplace",
            priority: 2,
            rpc: {
                details: isPainting ? "Painting pixels" : "Browsing the canvas",
                state: stateText,
                startTimestamp: stateStart,
                largeImageKey: "icon",
                largeImageText: "Wplace.live",
            }
        };

        bridge.send(payload);
    }, INTERVAL_MS);

})();