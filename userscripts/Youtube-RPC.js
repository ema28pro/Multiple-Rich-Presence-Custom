// ==UserScript==
// @name         YouTube-RPC
// @version      2.3
// @author       Custom
// @description  Discord RPC for YouTube — works with the Node.js bridge
//
// @match        https://www.youtube.com/*
// @match        https://youtube.com/*
// @run-at       document-idle
// @grant        unsafeWindow
// @grant        GM_registerMenuCommand
// @grant        GM_getValue
// @grant        GM_setValue
// ==/UserScript==

// Leer preferencia guardada (default: true)
var SHOW_THUMBNAIL = GM_getValue("show_thumbnail", true);

function updateMenuLabel() {
    GM_registerMenuCommand(
        "Miniatura: " + (SHOW_THUMBNAIL ? "ON" : "OFF"),
        function () {
            SHOW_THUMBNAIL = !SHOW_THUMBNAIL;
            GM_setValue("show_thumbnail", SHOW_THUMBNAIL);
            console.log("[YouTube-RPC] Miniatura:", SHOW_THUMBNAIL ? "activada" : "desactivada");
            // Recargar para que el menú refleje el nuevo estado
            location.reload();
        }
    );
}

var Socket = null;
var lastUrl = window.location.href;
var shortsStartTs = null;

function truncate(str, max) {
    if (!str) return "";
    str = str.trim();
    return str.length > max ? str.substring(0, max - 1) + "…" : str;
}

function calcStartTs(currentTimeSec) {
    return Date.now() - Math.floor(currentTimeSec * 1000);
}

function connect() {
    if (Socket && (Socket.readyState === 0 || Socket.readyState === 1)) return;

    Socket = new unsafeWindow.WebSocket("ws://127.0.0.1:6680");

    Socket.onopen = () => console.log("[YouTube-RPC] Connected");
    Socket.onerror = (e) => console.error("[YouTube-RPC] Error:", e);
    Socket.onclose = (e) => {
        console.log("[YouTube-RPC] Disconnected, code:", e.code);
        Socket = null;
    };
}

function send(payload) {
    if (!Socket || Socket.readyState !== 1) {
        connect();
        return;
    }
    try {
        Socket.send(JSON.stringify(payload));
    } catch (e) {
        console.error('[YouTube-RPC] Error sending payload:', e);
    }
}

function buildRPC() {
    var url = window.location.href;

    // ── SHORTS ────────────────────────────────────────────────────────────
    if (url.includes("/shorts/")) {
        // Iniciar timestamp la primera vez que entramos a Shorts, no resetear al cambiar de Short
        if (!shortsStartTs) shortsStartTs = Date.now();
        return {
            source: "youtube",
            priority: 2,
            rpc: {
                details: "Shorts",
                state: null,
                largeImageKey: "youtube",
                largeImageText: "YouTube Shorts",
                smallImageKey: "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/Youtube_shorts_icon.svg/1280px-Youtube_shorts_icon.svg.png",
                smallImageText: "Shorts",
                startTimestamp: shortsStartTs
            }
        };
    }
    // Al salir de Shorts, resetear para la próxima vez
    shortsStartTs = null;

    // ── EN UN VIDEO ───────────────────────────────────────────────────────
    if (url.includes("/watch?")) {
        var video = document.querySelector("video");
        var isPaused = !video || video.paused;
        var curTime = video ? video.currentTime : 0;

        // Título — si no está listo aún, no enviar nada
        var titleEl = document.querySelector("h1.ytd-watch-metadata yt-formatted-string") ||
            document.querySelector("h1.ytd-watch-metadata") ||
            document.querySelector("#title h1");
        var title = titleEl ? titleEl.innerText.trim() : null;
        if (!title) return null;

        // Canal — si no está listo aún, no enviar nada
        var channelEl = document.querySelector("#channel-name a") ||
            document.querySelector("ytd-channel-name a") ||
            document.querySelector("#owner #channel-name a");
        var channel = channelEl ? channelEl.innerText.trim() : null;
        if (!channel) return null;

        title = truncate(title, 64);
        channel = truncate(channel, 64);

        // Avatar del canal (imagen pequeña)
        var avatarEl = document.querySelector("#avatar img") ||
            document.querySelector("ytd-video-owner-renderer img");
        var avatarUrl = avatarEl ? avatarEl.src : null;

        // Miniatura del video (imagen grande)
        var videoId = null;
        try { videoId = new URL(url).searchParams.get("v"); } catch (e) { }
        var thumbnailUrl = videoId
            ? "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg"
            : null;

        var largeImage = (SHOW_THUMBNAIL && thumbnailUrl) ? thumbnailUrl : "youtube";
        var largeText = (SHOW_THUMBNAIL && thumbnailUrl) ? title : "YouTube";

        // Timestamp
        var time = (!isPaused && curTime > 0) ? calcStartTs(curTime) : null;

        return {
            source: "youtube",
            priority: 2,
            rpc: {
                details: title,
                state: isPaused ? "⏸ Paused" : channel,
                largeImageKey: largeImage,
                largeImageText: largeText,
                smallImageKey: avatarUrl,
                smallImageText: channel,
                startTimestamp: time
            }
        };
    }

    // ── FUERA DE UN VIDEO ─────────────────────────────────────────────────
    var pageDetail = "Browsing YouTube";
    if (url.includes("/results?")) {
        try {
            var q = new URL(url).searchParams.get("search_query");
            pageDetail = truncate("Searching: " + (q || "…"), 64);
        } catch (e) { }
    } else if (url.includes("/@") || url.includes("/channel/") || url.includes("/c/")) {
        var chanEl = document.querySelector("ytd-channel-name h1") ||
            document.querySelector("#channel-header-container h1");
        pageDetail = truncate("Viewing: " + (chanEl ? chanEl.innerText : "a channel"), 64);
    } else if (url.includes("/playlist?")) {
        var listEl = document.querySelector("h1.ytd-playlist-header-renderer");
        pageDetail = truncate("Playlist: " + (listEl ? listEl.innerText : "…"), 64);
    }

    return {
        source: "youtube",
        priority: 2,
        rpc: {
            details: pageDetail,
            state: null,
            largeImageKey: "youtube",
            largeImageText: "YouTube",
            smallImageKey: null,
            smallImageText: null,
            startTimestamp: null
        }
    };
}

// ── INICIAR ───────────────────────────────────────────────────────────────
updateMenuLabel();
connect();

// Enviar RPC cada 3 segundos — solo si la pestaña está activa
setInterval(() => {
    if (document.hidden) return; // pestaña en segundo plano → no enviar
    if (!Socket || Socket.readyState === 3) connect();
    if (!Socket || Socket.readyState !== 1) return;
    var rpc = buildRPC();
    if (rpc) send(rpc);
}, 3000);

// Detectar cambios de URL (YouTube SPA)
setInterval(() => {
    if (window.location.href !== lastUrl) {
        lastUrl = window.location.href;
        console.log("[YouTube-RPC] URL changed:", lastUrl);
    }
}, 1000);

console.log("[YouTube-RPC] Script loaded");