// ==UserScript==
// @name         YouTube-RPC
// @version      1.0
// @author       Custom
// @description  Discord RPC for YouTube — works with the Node.js bridge
//
// @include      https://www.youtube.com/*
// ==/UserScript==

var Socket;


function safeText(selector) {
    var el = document.querySelector(selector);
    return el ? (el.innerText || "").trim() : "";
}

function truncate(str, max) {
    if (!str) return "";
    return str.length > max ? str.substring(0, max - 1) + "…" : str;
}

// Calcula el timestamp de inicio ajustado al tiempo actual del video
// Para que Discord muestre "1:23 elapsed" correctamente
function calcStartTs(currentTimeSec) {
    return Date.now() - (currentTimeSec * 1000);
}

(function () {
    "use strict";

    Socket = new WebSocket("ws://127.0.0.1:6680");

    Socket.onerror = (e) => console.error("# [YouTube-RPC] Error:", e);
    Socket.onclose = (e) => {
        console.error("# [YouTube-RPC] Disconnected:", e);
        if (e.code != "1000") console.log("[YouTube-RPC] Bridge not running on port 6680.");
    };
    Socket.onopen = () => console.log("[YouTube-RPC] Connected to bridge");

    var Execution = setInterval(() => {
        if (closed) {
            if (!document.hidden) {
                closed = false;
                idling_started = false;
                idleStart = 0;
                Socket = new WebSocket("ws://127.0.0.1:6680");
            }
            return;
        }

        try {
            var rpc = buildRPC();
            if (rpc) Socket.send(JSON.stringify(rpc));
        } catch (e) {
            console.error("[YouTube-RPC] Error sending RPC:", e);
        }
    }, 3000);

})();

function buildRPC() {
    var url = window.location.href;

    // ── EN UN VIDEO ───────────────────────────────────────────────────────
    if (url.includes("watch?v=") || url.includes("shorts/")) {
        var video = document.querySelector("video");

        // Título del video
        var titleEl = document.querySelector("h1.ytd-watch-metadata yt-formatted-string") ||
            document.querySelector("h1.title") ||
            document.querySelector("#title h1");
        var title = truncate(titleEl ? titleEl.innerText.trim() : "Unknown video", 64);

        // Nombre del canal
        var channelEl = document.querySelector("#channel-name a") ||
            document.querySelector("ytd-channel-name a") ||
            document.querySelector("#owner #channel-name a");
        var channel = truncate(channelEl ? channelEl.innerText.trim() : "Unknown channel", 64);

        // Foto del canal para smallImage
        var avatarEl = document.querySelector("#avatar img") ||
            document.querySelector("ytd-video-owner-renderer img");
        var avatarUrl = avatarEl ? avatarEl.src : null;

        var isPaused = !video || video.paused;
        var currentTime = video ? video.currentTime : 0;

        // Recalcular startTs solo si el video está corriendo
        // (si está pausado, dejamos el último valor)
        var time = null;
        if (!isPaused && currentTime > 0) {
            time = calcStartTs(currentTime);
        }

        return {
            source: "youtube",
            priority: 2,
            rpc: {
                state: isPaused ? "⏸ Paused" : channel,
                details: title,
                largeImageKey: "youtube",
                largeImageText: "YouTube",
                smallImageKey: avatarUrl || null,
                smallImageText: channel,
                startTimestamp: isPaused ? null : time
            }
        };
    }

    // ── EN HOME / SEARCH / OTRO ───────────────────────────────────────────
    var pageDetail = "Browsing YouTube";
    if (url.includes("/results?")) {
        var query = new URL(url).searchParams.get("search_query");
        pageDetail = truncate("Searching: " + (query || "…"), 64);
    } else if (url.includes("/@") || url.includes("/channel/") || url.includes("/c/")) {
        var chanName = safeText("ytd-channel-name h1") ||
            safeText("#channel-header-container h1");
        pageDetail = truncate("Viewing: " + (chanName || "a channel"), 64);
    } else if (url.includes("/playlist?")) {
        var listName = safeText("h1.ytd-playlist-header-renderer") ||
            safeText("#title .ytd-playlist-header-renderer");
        pageDetail = truncate("Playlist: " + (listName || "…"), 64);
    }

    return {
        source: "youtube",
        priority: 3,
        rpc: {
            state: null,
            details: pageDetail,
            largeImageKey: "youtube",
            largeImageText: "YouTube",
            smallImageKey: null,
            smallImageText: null,
            startTimestamp: null
        }
    };
}