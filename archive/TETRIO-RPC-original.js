// ==UserScript==
// @name     TETRIO-RPC
// @version  1.0
// @author   PATXS
// @description Discord RPC for TETR.IO, in the browser
//
// @grant GM_notification
//
// @updateURL https://raw.githubusercontent.com/PATXS/TETRIO-browser-rpc/main/TETRIO-RPC.js
// @downloadURL https://raw.githubusercontent.com/PATXS/TETRIO-browser-rpc/main/TETRIO-RPC.js
// @include https://tetr.io/*
// @exclude https://tetr.io/res/*
// ==/UserScript==

var startTs = 0;
var prev_menu = "";
var closed = false;
var idling_started = false;
var idleStart = 0;
var Socket;
(function() {
    "use strict";

    var detail = "";
    var currMenu = "";
    var gamestate = "";
    var checkMenu = setInterval(() => {
        if(!document.body.classList.contains("ingame")){
            prev_menu = currMenu;
            updateTimestamp();
        }
        currMenu = document.querySelector("#menus").getAttribute("data-menu-type");
        if(currMenu != "none"){
            detail = "In Menus";
            if(currMenu == "tetra" || currMenu == "tetra_records" || currMenu == "tetra_players" || currMenu == "tetra_me"){
                detail = "Tetra Channel";
            }
            if(document.querySelector("#header_text").innerText == "RESULTS"){
                gamestate = "Results Screen";
            }
            if(currMenu == "lobby"){
                gamestate = "In Lobby";
                if(document.querySelector("#roomview").className.includes("sysroom")){
                    detail = "QUICK PLAY";
                }
                else{
                    detail = "CUSTOM ROOM";
                }
            }
            if(currMenu == "victory"){
                gamestate = "Game Ending";
            }
        }
        if((currMenu == "none" || currMenu == "lobby") && document.body.classList.contains("ingame")){
            if(detail == "TETRA LEAGUE"){
                if(document.querySelector("#mm_status_header").innerText == "MATCH FOUND"){
                    gamestate = "In Game";
                }
            }
            else if(prev_menu == "40l"){
                detail = "40 LINES";
                gamestate = null;
            }
            else if(prev_menu == "blitz"){
                detail = "BLITZ";
                gamestate = null;
            }
            else if(prev_menu == "zen"){
                detail = "ZEN";
                gamestate = null;
            }
            else if(prev_menu == "custom"){
                detail = "CUSTOM GAME";
                gamestate = null;
            }
            else if(prev_menu == "lobby"){
                gamestate = "In Game";
            }
        }
        if(!document.querySelector("#replay").className.includes("hidden")){
            detail = document.querySelector("#data_replay > span:nth-child(2)").innerText;
            gamestate = "Watching Replay";
        }
        if(!document.querySelector("#spectate").className.includes("hidden")){
            gamestate = "Spectating";
        }
        if((detail == "QUICK PLAY" || detail == "CUSTOM ROOM") && document.querySelector("#mm_status_header").innerText == "ZEN WHILE WAITING" && !(document.querySelector("#mm_status.room") == null)){
            gamestate = "In Lobby (ZEN)"
        }
        if(document.querySelector("#mm_status_header").innerText == "FINDING MATCH" && document.querySelector("#mm_status").className.includes("shown")){
            detail = "TETRA LEAGUE";
            gamestate = "In Queue";
        }
    }, 1000);

    Socket = new WebSocket("ws://127.0.0.1:6680");

    var Execution = setInterval(() => {
        if(!closed){
        Socket.send(JSON.stringify(establishRPC(
            Date.now(), detail, gamestate, (document.querySelector("body").className.includes("anon")), document.querySelector("#me_username").innerText, document.querySelector("#me_level").innerText, ((document.querySelector("#me_leaguerank").src).replace("https://tetr.io/res/league-ranks/", "")).replace(".png", "").toUpperCase()
        )));}
        else{
            if(!document.body.classList.contains("nofocus")){
                idling_started = false;
                idleStart = 0;
                closed = false;
                Socket = new WebSocket("ws://127.0.0.1:6680");
            }
        }
    }, 3000);

    Socket.onerror = function (Error)
    {
        console.error ("# [TETRIO-RPC] Error: " + Error);
    };

    Socket.onclose = function (Error)
    {
        console.error ("# [TETRIO-RPC] Error: " + Error);

        if (Error.code != "1000")
        {
            console.log("It seems the DPS is not running on port 6680.");
        }
    };

    Socket.onopen = function ()
    {
        console.log("TETRIO-RPC connected");
    };
    var unnecessary_credit = setTimeout(() => {
        document.getElementById("version_line").innerText += "\nTETRIO-RPC v1.0 by PATXS"
    }, 10000);
})();

function establishRPC(timestamp, detail, gamestate, anon, username, lvl, rank)
{
    var time;
    var sik;
    var lit;
    if(anon){
        lit = "Playing anonymously"
    }
    else{
        lit = username + " - Lv. " +lvl +" - " +rank
    }
    if(detail == "40 LINES"){
        sik = "mode_40l";
    }
    if(detail == "BLITZ"){
        sik = "mode_blitz";
    }
    if(detail == "ZEN"){
        sik = "mode_zen";
    }
    if(detail == "CUSTOM GAME"){
        sik = "mode_custom";
    }
    if(detail == "CUSTOM ROOM"){
        sik = "mode_custom";
    }
    if(detail == "QUICK PLAY"){
        sik = "mode_quickplay";
    }
    if(detail == "TETRA LEAGUE"){
        sik = "mode_league";
    }
    if(detail == "In Menus" || detail == "Tetra Channel"){
        gamestate = null;
        time = null;
    }
    else{
        time = startTs;
    }
    if(gamestate == "In Lobby" || gamestate == "In Queue" || gamestate == "Results Screen" || gamestate == "Game Ending"){
        time = null;
    }
    if(!document.querySelector("#preload").className.includes("hidden")){
        lit = "Logging in...";
        detail = "Logging in...";
        time = null;
    }
    if(document.body.classList.contains("nofocus")){
        idling();
        detail = "Idle";
        gamestate = null;
        time = null;
    }
    else{
        idling_started = false;
    }
    return {
        cid: "688741895307788290",

        rpc:
        {
            state: gamestate,
            details: detail,

            //partySize: 1,
            //partyMax: 1,

            largeImageKey: "logo",
            largeImageText: lit,

            smallImageKey: sik,
            smallImageText: detail,
            startTimestamp: time
        }
    };
};

function updateTimestamp(){
    startTs = Date.now();
}

function idling(){
    //after a 5 minute idle time, kill the RPC until the user comes back
    if(!idling_started){
        idling_started = true;
        idleStart = Date.now();
    }
    else{
        if(idleStart+300000 <= Date.now()){
            Socket.close();
            closed = true;
        }
    }
}
