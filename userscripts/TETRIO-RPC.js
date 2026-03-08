// ==UserScript==
// @name         TETRIO-RPC
// @version      2.7
// @author       PATXS (Rewritten for TETR.IO 1.7.7+)
// @description  Discord RPC for TETR.IO — works with the Node.js bridge
//
// @grant        GM_notification
//
// @include      https://tetr.io/*
// @exclude      https://tetr.io/res/*
// ==/UserScript==

var startTs = Date.now();
var prev_menu = "";
var closed = false;
var idling_started = false;
var idleStart = 0;
var Socket;

var committed_detail = "";
var committed_gamestate = "";
var committed_startTs = Date.now();

function safeText(selector) {
    var el = document.querySelector(selector);
    return el ? (el.innerText || "").trim() : "";
}
function safeClass(selector) {
    var el = document.querySelector(selector);
    return el ? el.className : "";
}
function hasClass(selector, cls) {
    return safeClass(selector).includes(cls);
}

(function () {
    "use strict";

    var detail = "In Menus";
    var gamestate = null;
    var currMenu = "";

    var checkMenu = setInterval(() => {
        var menusEl = document.querySelector("#menus");
        if (!menusEl) return;

        var bodyClass = document.body.className;
        var isIngame = bodyClass.includes("ingame");

        currMenu = menusEl.getAttribute("data-menu-type") || "none";

        // Guardar prev_menu cuando no estamos en partida y el menu es real
        if (!isIngame && currMenu !== "none") {
            prev_menu = currMenu;
        }

        // ── QUICK PLAY (ZENITH) ──────────────────────────────────────────
        if (currMenu === "zenith" || bodyClass.includes("inzenith")) {
            detail = "QUICK PLAY";
            if (!isIngame) {
                gamestate = "In Lobby";
                updateTimestamp();
            } else if (bodyClass.includes("ingame_phys")) {
                gamestate = "In Game";
            } else {
                gamestate = "In Lobby (ZEN)";
            }
        }

        // ── TETRA LEAGUE (cola) ───────────────────────────────────────────
        else if (currMenu === "league" || bodyClass.includes("matchmaking")) {
            detail = "TETRA LEAGUE";
            if (bodyClass.includes("matchmaking")) {
                gamestate = "In Queue";
            } else if (isIngame && bodyClass.includes("ingame_phys")) {
                gamestate = "In Game";
            } else if (isIngame) {
                gamestate = "In Lobby (ZEN)";
            } else {
                gamestate = "In Lobby";
                updateTimestamp();
            }
        }

        // ── TETRA LEAGUE (en partida) ─────────────────────────────────────
        // body: inmulti + multiplexed + ingame, SIN innormalmulti ni inzenith
        else if (bodyClass.includes("inmulti") && bodyClass.includes("multiplexed") &&
            !bodyClass.includes("innormalmulti") && !bodyClass.includes("inzenith")) {
            detail = "TETRA LEAGUE";
            gamestate = "In Game";
        }

        // ── PUBLIC / CUSTOM ROOM ──────────────────────────────────────────
        // body tiene innormalmulti (cualquier sala que no sea TL ni QP)
        else if (currMenu === "lobby" || bodyClass.includes("innormalmulti")) {
            var roomviewClass = safeClass("#roomview");
            detail = roomviewClass.includes("sysroom") ? "QUICK PLAY ROOM" : "CUSTOM ROOM";

            if (!isIngame) {
                gamestate = "In Lobby";
                updateTimestamp();
            } else if (bodyClass.includes("spectating")) {
                gamestate = "Spectating";
            } else if (bodyClass.includes("inmultizen")) {
                // ZEN mientras espera en la sala — mostrar ZEN, no la sala
                detail = "ZEN";
                gamestate = null; // sin estado = jugando activamente
            } else if (bodyClass.includes("ingame_phys")) {
                gamestate = "In Game";
            } else {
                gamestate = "In Lobby";
            }
        }

        // ── MODOS SOLO ────────────────────────────────────────────────────
        // En menú: currMenu = "40l" / "blitz" / "zen" / "custom"
        // En partida: currMenu = "none", body solo tiene ingame + ingame_phys
        //             → usamos prev_menu para saber qué modo era
        else if (currMenu === "40l" || (prev_menu === "40l" && isIngame && currMenu === "none")) {
            detail = "40 LINES";
            gamestate = null;
            if (!isIngame) updateTimestamp();
        }
        else if (currMenu === "blitz" || (prev_menu === "blitz" && isIngame && currMenu === "none")) {
            detail = "BLITZ";
            gamestate = null;
            if (!isIngame) updateTimestamp();
        }
        else if (currMenu === "zen" || (prev_menu === "zen" && isIngame && currMenu === "none")) {
            detail = "ZEN";
            gamestate = null;
            if (!isIngame) updateTimestamp();
        }
        else if (currMenu === "custom" || (prev_menu === "custom" && isIngame && currMenu === "none")) {
            detail = "CUSTOM GAME";
            gamestate = null;
            if (!isIngame) updateTimestamp();
        }

        // ── TETRA CHANNEL ─────────────────────────────────────────────────
        else if (["tetra", "tetra_records", "tetra_players", "tetra_me"].includes(currMenu)) {
            detail = "Tetra Channel";
            gamestate = null;
            updateTimestamp();
        }

        // ── MENÚS GENERALES ───────────────────────────────────────────────
        else {
            detail = "In Menus";
            gamestate = null;
            updateTimestamp();
        }

        // ── PANTALLAS DE RESULTADO (sobreescriben gamestate) ──────────────
        if (safeText("#header_text") === "RESULTS") {
            gamestate = "Results Screen";
        }
        if (!hasClass("#victoryview", "hidden")) {
            gamestate = "Game Ending";
        }
        if (!hasClass("#zenithmenu", "hidden")) {
            gamestate = "Results Screen";
        }

        // ── REPLAY / SPECTATE ─────────────────────────────────────────────
        if (!hasClass("#replay", "hidden")) {
            var replaySpan = document.querySelector("#data_replay > span:nth-child(2)");
            detail = replaySpan ? replaySpan.innerText : "Replay";
            gamestate = "Watching Replay";
        }
        if (!hasClass("#spectate", "hidden")) {
            gamestate = "Spectating";
        }

        // ── GUARDAR ESTADO COMPROMETIDO ───────────────────────────────────
        committed_detail = detail;
        committed_gamestate = gamestate;
        committed_startTs = startTs;

    }, 1000);

    Socket = new WebSocket("ws://127.0.0.1:6680");

    var Execution = setInterval(() => {
        if (!closed) {
            try {
                var isAnon = document.body.className.includes("anon");
                var username = safeText("#me_username");
                var lvl = safeText("#me_level");
                var rankEl = document.querySelector("#me_leaguerank");
                var rank = "?";
                if (rankEl && rankEl.src) {
                    rank = rankEl.src
                        .replace(/.*league-ranks\//, "")
                        .replace(".png", "")
                        .toUpperCase() || "?";
                }
                Socket.send(JSON.stringify(
                    establishRPC(Date.now(), detail, gamestate, isAnon, username, lvl, rank)
                ));
            } catch (e) {
                console.error("[TETRIO-RPC] Error sending RPC:", e);
            }
        } else {
            if (!document.body.classList.contains("nofocus")) {
                idling_started = false;
                idleStart = 0;
                closed = false;
                Socket = new WebSocket("ws://127.0.0.1:6680");
            }
        }
    }, 3000);

    Socket.onerror = (e) => console.error("# [TETRIO-RPC] Error:", e);
    Socket.onclose = (e) => {
        console.error("# [TETRIO-RPC] Disconnected:", e);
        if (e.code != "1000") console.log("[TETRIO-RPC] Bridge not running on port 6680.");
    };
    Socket.onopen = () => console.log("[TETRIO-RPC] Connected to bridge");

    setTimeout(() => {
        var vl = document.getElementById("version_line");
        if (vl) vl.innerText += "\nTETRIO-RPC v2.7";
    }, 10000);
})();

function establishRPC(timestamp, detail, gamestate, anon, username, lvl, rank) {
    var sik = null;
    var lit;

    if (anon) {
        lit = "Playing anonymously";
    } else {
        lit = (username || "Unknown") + " - Lv. " + (lvl || "?") + " - " + (rank || "?");
    }

    var iconMap = {
        "40 LINES": "mode_40l",
        "BLITZ": "mode_blitz",
        "ZEN": "mode_zen",
        "CUSTOM GAME": "mode_custom",
        "CUSTOM ROOM": "mode_custom",
        "QUICK PLAY ROOM": "mode_custom",
        "QUICK PLAY": "mode_quickplay",
        "TETRA LEAGUE": "mode_league"
    };
    sik = iconMap[detail] || null;

    // Timestamp siempre corre, se reinicia al cambiar de estado
    // Solo se oculta en estados de resultado / espectador
    var noTimeStates = ["Results Screen", "Game Ending", "Watching Replay", "Spectating"];
    var time = noTimeStates.includes(gamestate) ? null : startTs;

    // Pantalla de carga
    var preload = document.querySelector("#preload");
    if (preload && !preload.className.includes("hidden")) {
        lit = "Logging in...";
        detail = "Logging in...";
        time = null;
    }

    // ── PERDER FOCO ───────────────────────────────────────────────────────
    if (document.body.classList.contains("nofocus")) {
        var inActiveMode = committed_detail &&
            committed_detail !== "In Menus" &&
            committed_detail !== "Idle";

        if (inActiveMode) {
            detail = committed_detail;
            gamestate = committed_gamestate;
            time = noTimeStates.includes(gamestate) ? null : committed_startTs;
            idling_started = false;
        } else {
            idling();
            detail = "Idle";
            gamestate = null;
            time = idleStart || startTs;
        }
    } else {
        idling_started = false;
    }

    return {
        source: "tetrio",
        priority: 2,
        rpc: {
            state: gamestate,
            details: detail,
            largeImageKey: "logo",
            largeImageText: lit,
            smallImageKey: sik,
            smallImageText: detail,
            startTimestamp: time
        }
    };
}

function updateTimestamp() {
    startTs = Date.now();
}

function idling() {
    if (!idling_started) {
        idling_started = true;
        idleStart = Date.now();
    } else if (idleStart + 300000 <= Date.now()) {
        Socket.close();
        closed = true;
    }
}