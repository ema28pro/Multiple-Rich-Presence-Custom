// ==UserScript==
// @name         Anime-RPC
// @version      3.0
// @author       Ema (Claud Modified)
// @description  Discord RPC for anime streaming sites
//
// @grant        none
//
// @match        https://*.veranimes.net/*
// @match        https://veranimes.net/*
// @match        https://*.crunchyroll.com/*
// @match        https://crunchyroll.com/*
// @match        https://*.animeflv.net/*
// @match        https://animeflv.net/*
// @match        https://*.jkanime.net/*
// @match        https://jkanime.net/*
// ==/UserScript==

(function () {
    "use strict";

    const WS_URL = "ws://127.0.0.1:6680";
    const INTERVAL_MS = 3000;
    const RECONNECT_DELAY_MS = 5000;

    // ─── Site parsers ────────────────────────────────────────────────────────────

    const SITES = {
        jkanime: {
            match: /jkanime\.net/,
            name: "JKAnime",
            parse() {
                // https://jkanime.net/{slug}/{episode}/
                const m = location.pathname.match(/^\/([^/]+)\/(\d+)\/?$/);
                if (!m) return null;
                const [, slug, episode] = m;

                // Name: prefer the alt text of the cover image (already has proper casing)
                const cover = document.querySelector(`img[src*="${slug}"]`);
                const animeName = cover?.alt?.trim()
                    || slugToTitle(slug);

                const image = `https://cdn.jkdesa.com/assets/images/animes/image/${slug}.jpg`;
                return { animeName, episode, image };
            }
        },

        veranimes: {
            match: /veranimes\.net/,
            name: "VerAnimes",
            parse() {
                // https://wwv.veranimes.net/ver/{slug}-{episode}
                const m = location.pathname.match(/^\/ver\/(.+)-(\d+)$/);
                if (!m) return null;
                const [, slug, episode] = m;

                const animeName = slugToTitle(slug);
                const image = `https://wwv.veranimes.net/cdn/img/anime/${slug}.webp`;
                return { animeName, episode, image };
            }
        },

        animeflv: {
            match: /animeflv\.net/,
            name: "AnimeFLV",
            parse() {
                // https://www3.animeflv.net/ver/{slug}-{episode}
                const m = location.pathname.match(/^\/ver\/(.+)-(\d+)$/);
                if (!m) return null;
                const [, slug, episode] = m;

                const animeName = slugToTitle(slug);
                // AnimeFLV doesn't have a reliable public CDN cover URL — use og:image fallback
                const image = document.querySelector('meta[property="og:image"]')?.content || null;
                return { animeName, episode, image };
            }
        },

        crunchyroll: {
            match: /crunchyroll\.com/,
            name: "Crunchyroll",
            parse() {
                if (!location.pathname.includes("/watch/")) return null;

                // Crunchyroll has no info in the URL — fall back to meta tags
                const ogTitle = document.querySelector('meta[property="og:title"]')?.content || document.title;
                const parts = ogTitle.split(/\s*[-–—]\s*/);
                const animeName = parts[0]?.trim() || null;
                if (!animeName) return null;

                const epMatch = ogTitle.match(/[Ee]p(?:isode)?\s*(\d+)/);
                const episode = epMatch?.[1] || null;
                const image = document.querySelector('meta[property="og:image"]')?.content
                    || document.querySelector("video[poster]")?.poster
                    || null;

                return { animeName, episode, image };
            }
        }
    };

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    function slugToTitle(slug) {
        return slug
            .replace(/-/g, " ")
            .replace(/\b\w/g, c => c.toUpperCase());
    }

    function getCurrentSite() {
        for (const [key, site] of Object.entries(SITES)) {
            if (site.match.test(location.hostname)) return { key, ...site };
        }
        return null;
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
            this.ws.onopen = () => console.log("[Anime-RPC] Bridge connected");
            this.ws.onclose = () => {
                console.log(`[Anime-RPC] Bridge disconnected — retrying in ${RECONNECT_DELAY_MS / 1000}s`);
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

    // ─── Main ────────────────────────────────────────────────────────────────────

    const site = getCurrentSite();
    if (!site) return;

    console.log(`[Anime-RPC] Site detected: ${site.name}`);

    const bridge = new Bridge(WS_URL);

    // SPA navigation support (Crunchyroll & others that use History API)
    let lastUrl = location.href;
    const navObserver = new MutationObserver(() => {
        if (location.href !== lastUrl) lastUrl = location.href;
    });
    navObserver.observe(document.body, { childList: true, subtree: true });

    setInterval(() => {
        const info = site.parse();

        const payload = {
            source: "anime",
            priority: 2,
            rpc: info ? {
                details: info.animeName,
                state: info.episode ? `Episode ${info.episode}` : "Watching",
                largeImageKey: info.image,
                largeImageText: info.animeName,
                smallImageText: site.name
            } : {
                details: `Browsing ${site.name}`,
                state: null,
                largeImageKey: null,
                largeImageText: site.name,
                smallImageText: site.name
            }
        };

        bridge.send(payload);
    }, INTERVAL_MS);

})();