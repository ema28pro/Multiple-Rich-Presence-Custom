// Default Client IDs from config.json (will be updated dynamically via WebSocket)
        let configClientIds = {
            "Default / General": "1480066932358119605",
            "TETR.IO": "688741895307788290",
            "Roblox": "1480095698899701880",
            "YouTube": "1480095585011892265",
            "WPlace": "1480394033112023171",
            "Anime": "1480393899473371177"
        };

        // DOM Elements Map
        const els = {
            // Application ID
            appIdSelect: document.getElementById("rpcAppIdSelect"),
            optgroupConfig: document.getElementById("optgroupConfig"),
            optgroupHistory: document.getElementById("optgroupHistory"),
            clientId: document.getElementById("rpcClientId"),
            appIdBadge: document.getElementById("appIdBadge"),
            appIdStatusHint: document.getElementById("appIdStatusHint"),
            btnClearHistory: document.getElementById("btnClearHistory"),

            // General
            activityTypeRadios: document.querySelectorAll('input[name="activityType"]'),
            streamUrlGroup: document.getElementById("streamUrlGroup"),
            streamUrl: document.getElementById("rpcStreamUrl"),
            name: document.getElementById("rpcName"),
            statusDisplayType: document.getElementById("rpcStatusDisplayType"),
            pid: document.getElementById("rpcPid"),
            instance: document.getElementById("rpcInstance"),

            // Details & State
            details: document.getElementById("rpcDetails"),
            detailsUrl: document.getElementById("rpcDetailsUrl"),
            state: document.getElementById("rpcState"),
            stateUrl: document.getElementById("rpcStateUrl"),

            // Timestamps
            startEnable: document.getElementById("rpcStartEnable"),
            startGroup: document.getElementById("startGroup"),
            startOffset: document.getElementById("rpcStartOffset"),
            startUnix: document.getElementById("rpcStartUnix"),

            endEnable: document.getElementById("rpcEndEnable"),
            endGroup: document.getElementById("endGroup"),
            endOffset: document.getElementById("rpcEndOffset"),
            endUnix: document.getElementById("rpcEndUnix"),

            musicBarEnable: document.getElementById("rpcMusicBarEnable"),
            musicBarGroup: document.getElementById("musicBarGroup"),
            musicCurrent: document.getElementById("rpcMusicCurrent"),
            musicTotal: document.getElementById("rpcMusicTotal"),

            // Assets
            largeImg: document.getElementById("rpcLargeImg"),
            largeText: document.getElementById("rpcLargeText"),
            largeUrl: document.getElementById("rpcLargeUrl"),
            smallImg: document.getElementById("rpcSmallImg"),
            smallText: document.getElementById("rpcSmallText"),
            smallUrl: document.getElementById("rpcSmallUrl"),

            // Multiplayer & Lobby (Party & Secrets)
            partyEnable: document.getElementById("rpcPartyEnable"),
            partyGroup: document.getElementById("partyGroup"),
            partyCur: document.getElementById("rpcPartyCur"),
            partyMax: document.getElementById("rpcPartyMax"),
            partyStatusBanner: document.getElementById("partyStatusBanner"),
            allowJoin: document.getElementById("rpcAllowJoin"),
            allowSpectate: document.getElementById("rpcAllowSpectate"),
            advancedTokensEnable: document.getElementById("rpcAdvancedTokensEnable"),
            advancedTokensGroup: document.getElementById("advancedTokensGroup"),
            partyId: document.getElementById("rpcPartyId"),
            joinSecret: document.getElementById("rpcJoinSecret"),
            spectateSecret: document.getElementById("rpcSpectateSecret"),
            matchSecret: document.getElementById("rpcMatchSecret"),
            multiplayerNotice: document.getElementById("multiplayerButtonsNotice"),

            // Buttons
            buttonsNotice: document.getElementById("buttonsIncompatibleNotice"),
            btn1Enable: document.getElementById("rpcBtn1Enable"),
            btn1Group: document.getElementById("btn1Group"),
            btn1Text: document.getElementById("rpcBtn1Text"),
            btn1Url: document.getElementById("rpcBtn1Url"),

            btn2Enable: document.getElementById("rpcBtn2Enable"),
            btn2Group: document.getElementById("btn2Group"),
            btn2Text: document.getElementById("rpcBtn2Text"),
            btn2Url: document.getElementById("rpcBtn2Url"),

            // Payload Override
            overrideEnable: document.getElementById("rpcOverrideEnable"),
            overrideGroup: document.getElementById("overrideGroup"),
            payloadOverride: document.getElementById("rpcPayloadOverride"),
            jsonStatus: document.getElementById("jsonStatus"),

            // Controls
            activate: document.getElementById("btnActivate"),
            deactivate: document.getElementById("btnDeactivate"),
            activateBottom: document.getElementById("btnActivateBottom"),
            deactivateBottom: document.getElementById("btnDeactivateBottom"),
            save: document.getElementById("btnSave"),
            saveBottom: document.getElementById("btnSaveBottom"),
            preset: document.getElementById("btnPreset"),
            copyJson: document.getElementById("btnCopyJson"),

            // Connection & Preview
            connStatus: document.getElementById("connStatus"),
            connText: document.getElementById("connText"),
            cardHeader: document.getElementById("cardHeader"),
            previewTypeBadge: document.getElementById("previewTypeBadge"),
            preview: document.getElementById("previewContent"),
            jsonDebug: document.getElementById("jsonDebug"),
        };

        let active = false;
        let baseStartTime = null;
        let baseEndTime = null;
        let socket = null;

        // ---- Application ID History Management ----
        function getAppIdHistory() {
            try {
                const raw = localStorage.getItem("customRpcAppIdHistory");
                if (raw) {
                    const parsed = JSON.parse(raw);
                    if (Array.isArray(parsed)) return parsed;
                }
            } catch (e) { }
            return [];
        }

        function saveAppIdHistory(list) {
            try {
                localStorage.setItem("customRpcAppIdHistory", JSON.stringify(list));
            } catch (e) { }
        }

        function isIdInConfig(id) {
            return Object.values(configClientIds).includes(id);
        }

        function addIdToHistory(id) {
            if (!id || typeof id !== "string") return;
            const trimmed = id.trim();
            if (!trimmed || isIdInConfig(trimmed)) return;

            const history = getAppIdHistory().filter(item => item !== trimmed);
            history.unshift(trimmed); // most recent first
            if (history.length > 20) history.pop(); // keep last 20
            saveAppIdHistory(history);
            renderAppIdOptions();
        }

        function renderAppIdOptions() {
            // 1. Render Config IDs
            els.optgroupConfig.innerHTML = "";
            for (const [name, id] of Object.entries(configClientIds)) {
                const opt = document.createElement("option");
                opt.value = id;
                opt.textContent = `${name} (${id})`;
                els.optgroupConfig.appendChild(opt);
            }

            // 2. Render History IDs (excluding any that are in configClientIds)
            els.optgroupHistory.innerHTML = "";
            const history = getAppIdHistory().filter(hId => !isIdInConfig(hId));
            if (history.length === 0) {
                const emptyOpt = document.createElement("option");
                emptyOpt.disabled = true;
                emptyOpt.textContent = "— Sin IDs personalizados aún —";
                els.optgroupHistory.appendChild(emptyOpt);
            } else {
                history.forEach(hId => {
                    const opt = document.createElement("option");
                    opt.value = hId;
                    opt.textContent = `${hId} (Verificado)`;
                    els.optgroupHistory.appendChild(opt);
                });
            }

            syncAppIdSelectWithInput();
        }

        function syncAppIdSelectWithInput() {
            const currentVal = els.clientId.value.trim();
            let matched = false;

            // Check config options using .children (optgroup does not have .options)
            if (els.optgroupConfig && els.optgroupConfig.children) {
                for (const opt of els.optgroupConfig.children) {
                    if (opt.value === currentVal) {
                        els.appIdSelect.value = currentVal;
                        els.appIdBadge.textContent = opt.textContent.split(" (")[0];
                        els.appIdStatusHint.textContent = "Definido en config.json";
                        els.appIdStatusHint.style.color = "var(--success)";
                        matched = true;
                        break;
                    }
                }
            }

            // Check history options using .children
            if (!matched && els.optgroupHistory && els.optgroupHistory.children) {
                for (const opt of els.optgroupHistory.children) {
                    if (opt.value === currentVal) {
                        els.appIdSelect.value = currentVal;
                        els.appIdBadge.textContent = "Historial";
                        els.appIdStatusHint.textContent = "Historial verificado";
                        els.appIdStatusHint.style.color = "var(--warning)";
                        matched = true;
                        break;
                    }
                }
            }

            if (!matched) {
                els.appIdSelect.value = "custom";
                els.appIdBadge.textContent = "Custom";
                if (currentVal) {
                    els.appIdStatusHint.textContent = "ID personalizado nuevo";
                    els.appIdStatusHint.style.color = "#949ba4";
                } else {
                    els.appIdStatusHint.textContent = "Sin ID";
                    els.appIdStatusHint.style.color = "var(--danger)";
                }
            }
        }

        // App ID Dropdown Change Event
        els.appIdSelect.addEventListener("change", () => {
            const val = els.appIdSelect.value;
            if (val === "custom") {
                els.clientId.focus();
                syncAppIdSelectWithInput();
            } else {
                els.clientId.value = val;
                syncAppIdSelectWithInput();
                onFormChange();
            }
        });

        // App ID Input Change / Typing Event
        els.clientId.addEventListener("input", () => {
            syncAppIdSelectWithInput();
            onFormChange();
        });

        // Clear history button
        els.btnClearHistory.addEventListener("click", (e) => {
            e.preventDefault();
            if (confirm("¿Deseas vaciar el historial de IDs personalizados probados?")) {
                saveAppIdHistory([]);
                renderAppIdOptions();
            }
        });

        // Activity Type Getter & Setter
        function getActivityType() {
            const checked = document.querySelector('input[name="activityType"]:checked');
            return checked ? parseInt(checked.value, 10) : 0;
        }

        function setActivityType(val) {
            const radio = document.querySelector(`input[name="activityType"][value="${val}"]`);
            if (radio) {
                radio.checked = true;
            }
            toggleStreamField();
        }

        function toggleStreamField() {
            const type = getActivityType();
            if (type === 1) {
                els.streamUrlGroup.classList.remove("hidden");
            } else {
                els.streamUrlGroup.classList.add("hidden");
            }
        }

        // Music Timestamps calculation
        function updateMusicTimestamps() {
            const currentMs = parseTimeInput(els.musicCurrent ? els.musicCurrent.value : "01:23");
            const totalMs = parseTimeInput(els.musicTotal ? els.musicTotal.value : "03:45");
            const safeTotal = Math.max(totalMs, currentMs + 1000);

            const now = Date.now();
            baseStartTime = now - currentMs;
            baseEndTime = baseStartTime + safeTotal;
        }

        // Toggles visibility of sub-panels (mutually exclusive)
        els.startEnable.addEventListener("change", () => {
            els.startGroup.classList.toggle("hidden", !els.startEnable.checked);
            if (els.startEnable.checked) {
                if (els.endEnable.checked) {
                    els.endEnable.checked = false;
                    els.endGroup.classList.add("hidden");
                }
                if (els.musicBarEnable && els.musicBarEnable.checked) {
                    els.musicBarEnable.checked = false;
                    if (els.musicBarGroup) els.musicBarGroup.classList.add("hidden");
                }
                baseEndTime = null;
                if (!baseStartTime) {
                    baseStartTime = Date.now() - parseTimeInput(els.startOffset.value || "00:00:00");
                }
            } else {
                baseStartTime = null;
            }
            onFormChange();
        });

        els.endEnable.addEventListener("change", () => {
            els.endGroup.classList.toggle("hidden", !els.endEnable.checked);
            if (els.endEnable.checked) {
                if (els.startEnable.checked) {
                    els.startEnable.checked = false;
                    els.startGroup.classList.add("hidden");
                }
                if (els.musicBarEnable && els.musicBarEnable.checked) {
                    els.musicBarEnable.checked = false;
                    if (els.musicBarGroup) els.musicBarGroup.classList.add("hidden");
                }
                baseStartTime = null;
                if (!baseEndTime) {
                    baseEndTime = Date.now() + parseTimeInput(els.endOffset.value || "00:15:00");
                }
            } else {
                baseEndTime = null;
            }
            onFormChange();
        });

        if (els.musicBarEnable) {
            els.musicBarEnable.addEventListener("change", () => {
                if (els.musicBarEnable.checked) {
                    els.startEnable.checked = false;
                    els.startGroup.classList.add("hidden");
                    els.endEnable.checked = false;
                    els.endGroup.classList.add("hidden");

                    // Discord requiere tipo 2 (Listening to / Escuchando) para dibujar la barra de reproducción
                    setActivityType(2);

                    updateMusicTimestamps();
                } else {
                    baseStartTime = null;
                    baseEndTime = null;
                }
                if (els.musicBarGroup) els.musicBarGroup.classList.toggle("hidden", !els.musicBarEnable.checked);
                onFormChange();
            });
        }

        if (els.musicCurrent) {
            els.musicCurrent.addEventListener("input", () => {
                if (els.musicBarEnable && els.musicBarEnable.checked) {
                    updateMusicTimestamps();
                    onFormChange();
                }
            });
        }

        if (els.musicTotal) {
            els.musicTotal.addEventListener("input", () => {
                if (els.musicBarEnable && els.musicBarEnable.checked) {
                    updateMusicTimestamps();
                    onFormChange();
                }
            });
        }

        els.startOffset.addEventListener("input", () => {
            const offset = parseTimeInput(els.startOffset.value);
            baseStartTime = Date.now() - offset;
            onFormChange();
        });

        els.endOffset.addEventListener("input", () => {
            const offset = parseTimeInput(els.endOffset.value);
            baseEndTime = Date.now() + offset;
            onFormChange();
        });

        els.btn1Enable.addEventListener("change", () => {
            if (els.btn1Enable.checked && els.partyEnable && els.partyEnable.checked) {
                els.partyEnable.checked = false;
                if (els.partyGroup) els.partyGroup.classList.add("hidden");
            }
            els.btn1Group.classList.toggle("hidden", !els.btn1Enable.checked);
            onFormChange();
        });

        els.btn2Enable.addEventListener("change", () => {
            if (els.btn2Enable.checked && els.partyEnable && els.partyEnable.checked) {
                els.partyEnable.checked = false;
                if (els.partyGroup) els.partyGroup.classList.add("hidden");
            }
            els.btn2Group.classList.toggle("hidden", !els.btn2Enable.checked);
            onFormChange();
        });

        els.overrideEnable.addEventListener("change", () => {
            els.overrideGroup.classList.toggle("hidden", !els.overrideEnable.checked);
            onFormChange();
        });

        if (els.partyEnable) {
            els.partyEnable.addEventListener("change", () => {
                if (els.partyEnable.checked) {
                    if (els.btn1Enable && els.btn1Enable.checked) {
                        els.btn1Enable.checked = false;
                        if (els.btn1Group) els.btn1Group.classList.add("hidden");
                    }
                    if (els.btn2Enable && els.btn2Enable.checked) {
                        els.btn2Enable.checked = false;
                        if (els.btn2Group) els.btn2Group.classList.add("hidden");
                    }
                }
                if (els.partyGroup) els.partyGroup.classList.toggle("hidden", !els.partyEnable.checked);
                onFormChange();
            });
        }

        if (els.advancedTokensEnable) {
            els.advancedTokensEnable.addEventListener("change", () => {
                if (els.advancedTokensGroup) els.advancedTokensGroup.classList.toggle("hidden", !els.advancedTokensEnable.checked);
                onFormChange();
            });
        }

        if (els.partyCur) els.partyCur.addEventListener("input", onFormChange);
        if (els.partyMax) els.partyMax.addEventListener("input", onFormChange);
        if (els.partyId) els.partyId.addEventListener("input", onFormChange);
        if (els.allowJoin) els.allowJoin.addEventListener("change", onFormChange);
        if (els.allowSpectate) els.allowSpectate.addEventListener("change", onFormChange);
        if (els.joinSecret) els.joinSecret.addEventListener("input", onFormChange);
        if (els.spectateSecret) els.spectateSecret.addEventListener("input", onFormChange);
        if (els.matchSecret) els.matchSecret.addEventListener("input", onFormChange);

        els.activityTypeRadios.forEach(r => {
            r.addEventListener("change", () => {
                toggleStreamField();
                const type = getActivityType();
                if (type !== 2 && els.musicBarEnable && els.musicBarEnable.checked) {
                    // Discord solo muestra la barra de reproducción en tipo 2 (Listening)
                    els.musicBarEnable.checked = false;
                    if (els.musicBarGroup) els.musicBarGroup.classList.add("hidden");
                    baseStartTime = null;
                    baseEndTime = null;
                }
                onFormChange();
            });
        });

        function updatePartyStatusBanner() {
            if (!els.partyStatusBanner) return;
            const cur = parseInt(els.partyCur.value, 10) || 1;
            const max = parseInt(els.partyMax.value, 10) || 4;

            if (cur >= max) {
                els.partyStatusBanner.style.background = "rgba(250, 166, 26, 0.12)";
                els.partyStatusBanner.style.color = "var(--warning)";
                els.partyStatusBanner.style.borderColor = "rgba(250, 166, 26, 0.3)";
                els.partyStatusBanner.innerHTML = `<strong>Sala llena (${cur} de ${max}):</strong> El botón "Unirse" se mostrará en GRIS (deshabilitado) en Discord hasta que haya cupos libres.`;
            } else {
                const libres = max - cur;
                els.partyStatusBanner.style.background = "rgba(87, 242, 135, 0.1)";
                els.partyStatusBanner.style.color = "var(--success)";
                els.partyStatusBanner.style.borderColor = "rgba(87, 242, 135, 0.25)";
                els.partyStatusBanner.innerHTML = `<strong>Cupo disponible (${libres} libre${libres > 1 ? 's' : ''}):</strong> El botón "Unirse" saldrá CLICKABLE y activo para tus amigos en Discord.`;
            }
        }

        function syncIncompatibilities() {
            const hasButtons = (els.btn1Enable && els.btn1Enable.checked) || (els.btn2Enable && els.btn2Enable.checked);
            const multiplayerOn = Boolean(els.partyEnable && els.partyEnable.checked);

            // 1. Exclusión mutua estricta: Botones vs Multijugador (Discord Error 5005)
            // Si ambos llegasen a estar activos (por ejemplo al restaurar un preset o config guardada antigua),
            // se apagan automáticamente los botones para evitar enviar ambos a la vez.
            if (hasButtons && multiplayerOn) {
                els.btn1Enable.checked = false;
                els.btn2Enable.checked = false;
                if (els.btn1Group) els.btn1Group.classList.add("hidden");
                if (els.btn2Group) els.btn2Group.classList.add("hidden");
            }

            // Actualizar banner de cupo de la sala
            updatePartyStatusBanner();

            // 2. Incompatibilidad Tiempo Transcurrido vs Tiempo Restante (Discord solo muestra uno)
            if (els.startEnable.checked && els.endEnable.checked) {
                els.startEnable.checked = false;
                els.startGroup.classList.add("hidden");
                baseStartTime = null;
            }

            // 3. Activity Type Stream field
            toggleStreamField();
        }

        function onFormChange() {
            syncIncompatibilities();
            if (active) sendRPC();
            updatePreview();
        }

        // Time parsing helpers
        function parseTimeInput(val) {
            if (!val) return 0;
            const parts = val.trim().split(":").map(Number);
            if (parts.some(isNaN)) return 0;
            if (parts.length === 3) return (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000;
            if (parts.length === 2) return (parts[0] * 60 + parts[1]) * 1000;
            if (parts.length === 1) return parts[0] * 1000;
            return 0;
        }

        function msToTime(ms) {
            if (ms < 0) ms = 0;
            const totalSec = Math.floor(ms / 1000);
            const h = Math.floor(totalSec / 3600);
            const m = Math.floor((totalSec % 3600) / 60);
            const s = totalSec % 60;
            return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
        }

        // Build Payload
        function buildPayload() {
            const rpc = {};

            // 0. Application ID / Client ID
            const activeClientId = els.clientId.value.trim();
            if (activeClientId) {
                rpc.clientId = activeClientId;
                rpc.client_id = activeClientId;
            }

            // 1. Name & Activity Type
            const nameVal = els.name.value.trim();
            if (nameVal) rpc.name = nameVal;

            const type = getActivityType();
            rpc.type = type;
            rpc.activity_type = type;

            if (type === 1) {
                const streamUrl = els.streamUrl.value.trim();
                if (streamUrl) rpc.url = streamUrl;
            }

            // Status Display Type
            const sdt = els.statusDisplayType.value;
            if (sdt !== "") {
                rpc.status_display_type = parseInt(sdt, 10);
            }

            // Instance & PID
            rpc.instance = els.instance.checked;
            const pidVal = parseInt(els.pid.value, 10);
            if (!isNaN(pidVal) && pidVal > 0) {
                rpc.pid = pidVal;
            }

            // 2. Details & State
            const details = els.details.value.trim();
            if (details) rpc.details = details;

            const detailsUrl = els.detailsUrl.value.trim();
            if (detailsUrl) rpc.details_url = detailsUrl;

            const state = els.state.value.trim();
            if (state) rpc.state = state;

            const stateUrl = els.stateUrl.value.trim();
            if (stateUrl) rpc.state_url = stateUrl;

            // 3. Timestamps
            if (els.musicBarEnable && els.musicBarEnable.checked) {
                if (!baseStartTime || !baseEndTime) {
                    updateMusicTimestamps();
                }
                const startSec = Math.floor(baseStartTime / 1000);
                const endSec = Math.floor(baseEndTime / 1000);
                rpc.start = startSec;
                rpc.startTimestamp = baseStartTime;
                rpc.end = endSec;
                rpc.endTimestamp = baseEndTime;
            } else {
                if (els.startEnable.checked) {
                    const manualUnix = parseInt(els.startUnix.value, 10);
                    if (!isNaN(manualUnix) && manualUnix > 0) {
                        rpc.start = manualUnix;
                        rpc.startTimestamp = manualUnix * 1000;
                    } else {
                        if (!baseStartTime) {
                            const offset = parseTimeInput(els.startOffset.value);
                            baseStartTime = Date.now() - offset;
                        }
                        const startSec = Math.floor(baseStartTime / 1000);
                        rpc.start = startSec;
                        rpc.startTimestamp = baseStartTime;
                    }
                } else {
                    baseStartTime = null;
                }

                if (els.endEnable.checked) {
                    const manualUnix = parseInt(els.endUnix.value, 10);
                    if (!isNaN(manualUnix) && manualUnix > 0) {
                        rpc.end = manualUnix;
                        rpc.endTimestamp = manualUnix * 1000;
                    } else {
                        if (!baseEndTime) {
                            const offset = parseTimeInput(els.endOffset.value);
                            baseEndTime = Date.now() + offset;
                        }
                        const endSec = Math.floor(baseEndTime / 1000);
                        rpc.end = endSec;
                        rpc.endTimestamp = baseEndTime;
                    }
                } else {
                    baseEndTime = null;
                }
            }

            // 4. Assets
            const largeImg = els.largeImg.value.trim();
            const largeText = els.largeText.value.trim();
            const largeUrl = els.largeUrl.value.trim();
            if (largeImg) {
                rpc.large_image = largeImg;
                rpc.largeImageKey = largeImg;
            }
            if (largeText) {
                rpc.large_text = largeText;
                rpc.largeImageText = largeText;
            }
            if (largeUrl) rpc.large_url = largeUrl;

            const smallImg = els.smallImg.value.trim();
            const smallText = els.smallText.value.trim();
            const smallUrl = els.smallUrl.value.trim();
            if (smallImg) {
                rpc.small_image = smallImg;
                rpc.smallImageKey = smallImg;
            }
            if (smallText) {
                rpc.small_text = smallText;
                rpc.smallImageText = smallText;
            }
            if (smallUrl) rpc.small_url = smallUrl;

            // 5. Buttons
            const buttons = [];
            if (els.btn1Enable.checked) {
                const label = els.btn1Text.value.trim();
                const url = els.btn1Url.value.trim();
                if (label) {
                    buttons.push({ label, url: url || "https://discord.com" });
                }
            }
            if (els.btn2Enable.checked) {
                const label = els.btn2Text.value.trim();
                const url = els.btn2Url.value.trim();
                if (label) {
                    buttons.push({ label, url: url || "https://discord.com" });
                }
            }
            if (buttons.length > 0) {
                rpc.buttons = buttons;
            }

            // 6. Multiplayer: Party & Secrets (only if enabled)
            const hasButtons = buttons.length > 0;
            if (els.partyEnable && els.partyEnable.checked) {
                const cur = parseInt(els.partyCur.value, 10) || 1;
                const max = parseInt(els.partyMax.value, 10) || 4;
                const manualId = els.partyId ? els.partyId.value.trim() : "";
                const pid = manualId || "sala-publica-01";

                rpc.party_id = pid;
                rpc.partyId = pid;
                rpc.party_size = [cur, max];
                rpc.partySize = cur;
                rpc.partyMax = max;

                // Solo incluir secretos si NO hay botones interactivos web (Discord Error 5005)
                if (!hasButtons) {
                    const allowJoin = els.allowJoin ? els.allowJoin.checked : true;
                    const allowSpec = els.allowSpectate ? els.allowSpectate.checked : true;

                    if (allowJoin) {
                        const manualJoin = els.joinSecret ? els.joinSecret.value.trim() : "";
                        rpc.join = (manualJoin.length >= 2) ? manualJoin : `join_${pid}`;
                    }

                    if (allowSpec) {
                        const manualSpec = els.spectateSecret ? els.spectateSecret.value.trim() : "";
                        rpc.spectate = (manualSpec.length >= 2) ? manualSpec : `spec_${pid}`;
                    }

                    const manualMatch = els.matchSecret ? els.matchSecret.value.trim() : "";
                    if (manualMatch.length >= 2) {
                        rpc.match = manualMatch;
                    }
                }
            }

            // 8. Payload Override
            if (els.overrideEnable.checked) {
                const rawJson = els.payloadOverride.value.trim();
                if (rawJson) {
                    try {
                        const parsed = JSON.parse(rawJson);
                        rpc.payload_override = parsed;
                        els.jsonStatus.textContent = "JSON válido";
                        els.jsonStatus.style.color = "var(--success)";
                    } catch (e) {
                        els.jsonStatus.textContent = "Error de sintaxis JSON";
                        els.jsonStatus.style.color = "var(--danger)";
                    }
                }
            }

            return { source: "custom", priority: 4, persistent: true, rpc };
        }

        // WebSocket Connection
        function connect() {
            socket = new WebSocket("ws://127.0.0.1:6680");

            socket.onopen = () => {
                els.connStatus.className = "connection-badge connected";
                els.connText.textContent = "Bridge activo (puerto 6680)";
                try {
                    socket.send(JSON.stringify({ action: "query", source: "custom" }));
                } catch (e) { }
            };

            socket.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);

                    // Update config client IDs if bridge sent them
                    if (data.configClientIds && typeof data.configClientIds === "object") {
                        configClientIds = data.configClientIds;
                        renderAppIdOptions();
                    }

                    // Handle verification feedback from bridge
                    if (data.type === "presenceUpdateResult") {
                        if (data.success) {
                            const activeId = els.clientId.value.trim();
                            if (activeId && !isIdInConfig(activeId)) {
                                addIdToHistory(activeId);
                            }
                            syncAppIdSelectWithInput();
                            els.appIdStatusHint.textContent = "Conectado a Discord";
                            els.appIdStatusHint.style.color = "var(--success)";
                        } else {
                            els.appIdStatusHint.textContent = "Error al conectar con este Client ID";
                            els.appIdStatusHint.style.color = "var(--danger)";
                        }
                    }

                    if (data.type === "queryResponse" && data.source === "custom") {
                        if (data.active && data.persistent) {
                            active = true;
                            if (data.rpc) {
                                loadFromRpcObject(data.rpc);
                            }
                            syncButtonState(true);
                            updatePreview();
                        } else {
                            active = false;
                            syncButtonState(false);
                            updatePreview();
                        }
                    }
                } catch (e) { }
            };

            socket.onclose = () => {
                els.connStatus.className = "connection-badge disconnected";
                els.connText.textContent = "Desconectado — reconectando...";
                setTimeout(connect, 4000);
            };

            socket.onerror = () => { };
        }

        // Initialize Options & WebSocket
        renderAppIdOptions();
        connect();

        function syncButtonState(isActive) {
            active = isActive;
            if (active) {
                els.activate.classList.add("hidden");
                els.deactivate.classList.remove("hidden");
                els.activateBottom.classList.add("hidden");
                els.deactivateBottom.classList.remove("hidden");
            } else {
                els.activate.classList.remove("hidden");
                els.deactivate.classList.add("hidden");
                els.activateBottom.classList.remove("hidden");
                els.deactivateBottom.classList.add("hidden");
            }
        }

        function sendRPC() {
            if (!socket || socket.readyState !== WebSocket.OPEN) return;
            try {
                const payload = buildPayload();
                socket.send(JSON.stringify(payload));
            } catch (e) { }
        }

        function removeRPC() {
            if (!socket || socket.readyState !== WebSocket.OPEN) return;
            try {
                socket.send(JSON.stringify({ source: "custom", action: "remove" }));
            } catch (e) { }
        }

        function onFormChange() {
            if (active) {
                sendRPC();
            }
            updatePreview();
        }

        // Action Handlers
        function doActivate() {
            active = true;
            if (els.startEnable.checked) {
                const offset = parseTimeInput(els.startOffset.value);
                baseStartTime = Date.now() - offset;
            }
            if (els.endEnable.checked) {
                const offset = parseTimeInput(els.endOffset.value);
                baseEndTime = Date.now() + offset;
            }
            syncButtonState(true);
            sendRPC();
            updatePreview();
            saveConfig();
        }

        function doDeactivate() {
            active = false;
            baseStartTime = null;
            baseEndTime = null;
            syncButtonState(false);
            removeRPC();
            updatePreview();
            saveConfig();
        }

        els.activate.addEventListener("click", doActivate);
        els.activateBottom.addEventListener("click", doActivate);
        els.deactivate.addEventListener("click", doDeactivate);
        els.deactivateBottom.addEventListener("click", doDeactivate);

        // Save & Load Config
        function getConfig() {
            return {
                active,
                clientId: els.clientId.value,
                activityType: getActivityType(),
                streamUrl: els.streamUrl.value,
                name: els.name.value,
                statusDisplayType: els.statusDisplayType.value,
                pid: els.pid.value,
                instance: els.instance.checked,
                details: els.details.value,
                detailsUrl: els.detailsUrl.value,
                state: els.state.value,
                stateUrl: els.stateUrl.value,
                startEnable: els.startEnable.checked,
                startOffset: els.startOffset.value,
                startUnix: els.startUnix.value,
                endEnable: els.endEnable.checked,
                endOffset: els.endOffset.value,
                endUnix: els.endUnix.value,
                musicBarEnable: els.musicBarEnable ? els.musicBarEnable.checked : false,
                musicCurrent: els.musicCurrent ? els.musicCurrent.value : "01:23",
                musicTotal: els.musicTotal ? els.musicTotal.value : "03:45",
                largeImg: els.largeImg.value,
                largeText: els.largeText.value,
                largeUrl: els.largeUrl.value,
                smallImg: els.smallImg.value,
                smallText: els.smallText.value,
                smallUrl: els.smallUrl.value,
                partyEnable: els.partyEnable ? els.partyEnable.checked : false,
                partyCur: els.partyCur.value,
                partyMax: els.partyMax.value,
                partyId: els.partyId.value,
                allowJoin: els.allowJoin ? els.allowJoin.checked : true,
                allowSpectate: els.allowSpectate ? els.allowSpectate.checked : true,
                advancedTokensEnable: els.advancedTokensEnable ? els.advancedTokensEnable.checked : false,
                joinSecret: els.joinSecret.value,
                spectateSecret: els.spectateSecret.value,
                matchSecret: els.matchSecret.value,
                btn1Enable: els.btn1Enable.checked,
                btn1Text: els.btn1Text.value,
                btn1Url: els.btn1Url.value,
                btn2Enable: els.btn2Enable.checked,
                btn2Text: els.btn2Text.value,
                btn2Url: els.btn2Url.value,
                overrideEnable: els.overrideEnable.checked,
                payloadOverride: els.payloadOverride.value,
            };
        }

        function saveConfig() {
            try {
                localStorage.setItem("customRpcFullConfig", JSON.stringify(getConfig()));
            } catch (e) { }
        }

        function applyConfig(cfg) {
            if (!cfg) return;
            if (cfg.clientId !== undefined && cfg.clientId.trim() !== "") {
                els.clientId.value = cfg.clientId.trim();
                syncAppIdSelectWithInput();
            }
            if (cfg.activityType !== undefined) setActivityType(cfg.activityType);
            if (cfg.streamUrl !== undefined) els.streamUrl.value = cfg.streamUrl;
            if (cfg.name !== undefined) els.name.value = cfg.name;
            if (cfg.statusDisplayType !== undefined) els.statusDisplayType.value = cfg.statusDisplayType;
            if (cfg.pid !== undefined) els.pid.value = cfg.pid;
            if (cfg.instance !== undefined) els.instance.checked = !!cfg.instance;

            if (cfg.details !== undefined) els.details.value = cfg.details;
            if (cfg.detailsUrl !== undefined) els.detailsUrl.value = cfg.detailsUrl;
            if (cfg.state !== undefined) els.state.value = cfg.state;
            if (cfg.stateUrl !== undefined) els.stateUrl.value = cfg.stateUrl;

            els.startEnable.checked = !!cfg.startEnable;
            els.startGroup.classList.toggle("hidden", !cfg.startEnable);
            if (cfg.startOffset !== undefined) els.startOffset.value = cfg.startOffset;
            if (cfg.startUnix !== undefined) els.startUnix.value = cfg.startUnix;

            els.endEnable.checked = !!cfg.endEnable;
            els.endGroup.classList.toggle("hidden", !cfg.endEnable);
            if (cfg.endOffset !== undefined) els.endOffset.value = cfg.endOffset;
            if (cfg.endUnix !== undefined) els.endUnix.value = cfg.endUnix;

            if (cfg.musicBarEnable !== undefined) {
                if (els.musicBarEnable) els.musicBarEnable.checked = !!cfg.musicBarEnable;
                if (els.musicBarGroup) els.musicBarGroup.classList.toggle("hidden", !cfg.musicBarEnable);
            }
            if (cfg.musicCurrent !== undefined && els.musicCurrent) els.musicCurrent.value = cfg.musicCurrent;
            if (cfg.musicTotal !== undefined && els.musicTotal) els.musicTotal.value = cfg.musicTotal;

            if (cfg.largeImg !== undefined) els.largeImg.value = cfg.largeImg;
            if (cfg.largeText !== undefined) els.largeText.value = cfg.largeText;
            if (cfg.largeUrl !== undefined) els.largeUrl.value = cfg.largeUrl;

            if (cfg.smallImg !== undefined) els.smallImg.value = cfg.smallImg;
            if (cfg.smallText !== undefined) els.smallText.value = cfg.smallText;
            if (cfg.smallUrl !== undefined) els.smallUrl.value = cfg.smallUrl;

            if (cfg.partyEnable !== undefined) {
                if (els.partyEnable) els.partyEnable.checked = !!cfg.partyEnable;
                if (els.partyGroup) els.partyGroup.classList.toggle("hidden", !cfg.partyEnable);
            }
            if (cfg.partyCur !== undefined) els.partyCur.value = cfg.partyCur;
            if (cfg.partyMax !== undefined) els.partyMax.value = cfg.partyMax;
            if (cfg.partyId !== undefined) els.partyId.value = cfg.partyId;

            if (cfg.allowJoin !== undefined && els.allowJoin) els.allowJoin.checked = !!cfg.allowJoin;
            if (cfg.allowSpectate !== undefined && els.allowSpectate) els.allowSpectate.checked = !!cfg.allowSpectate;

            if (cfg.advancedTokensEnable !== undefined) {
                if (els.advancedTokensEnable) els.advancedTokensEnable.checked = !!cfg.advancedTokensEnable;
                if (els.advancedTokensGroup) els.advancedTokensGroup.classList.toggle("hidden", !cfg.advancedTokensEnable);
            }
            if (cfg.joinSecret !== undefined) els.joinSecret.value = cfg.joinSecret;
            if (cfg.spectateSecret !== undefined) els.spectateSecret.value = cfg.spectateSecret;
            if (cfg.matchSecret !== undefined) els.matchSecret.value = cfg.matchSecret;

            els.btn1Enable.checked = !!cfg.btn1Enable;
            els.btn1Group.classList.toggle("hidden", !cfg.btn1Enable);
            if (cfg.btn1Text !== undefined) els.btn1Text.value = cfg.btn1Text;
            if (cfg.btn1Url !== undefined) els.btn1Url.value = cfg.btn1Url;

            els.btn2Enable.checked = !!cfg.btn2Enable;
            els.btn2Group.classList.toggle("hidden", !cfg.btn2Enable);
            if (cfg.btn2Text !== undefined) els.btn2Text.value = cfg.btn2Text;
            if (cfg.btn2Url !== undefined) els.btn2Url.value = cfg.btn2Url;

            els.overrideEnable.checked = !!cfg.overrideEnable;
            els.overrideGroup.classList.toggle("hidden", !cfg.overrideEnable);
            if (cfg.payloadOverride !== undefined) els.payloadOverride.value = cfg.payloadOverride;

            toggleStreamField();
        }

        function loadFromRpcObject(rpc) {
            if (!rpc) return;
            const cid = rpc.clientId || rpc.client_id;
            if (cid && cid.trim()) {
                els.clientId.value = cid.trim();
                syncAppIdSelectWithInput();
            }

            if (typeof rpc.type === "number") setActivityType(rpc.type);
            else if (typeof rpc.activity_type === "number") setActivityType(rpc.activity_type);

            if (rpc.url) els.streamUrl.value = rpc.url;
            if (rpc.name) els.name.value = rpc.name;
            if (rpc.status_display_type !== undefined) els.statusDisplayType.value = String(rpc.status_display_type);
            if (rpc.pid) els.pid.value = rpc.pid;
            if (rpc.instance !== undefined) els.instance.checked = !!rpc.instance;

            if (rpc.details) els.details.value = rpc.details;
            if (rpc.details_url) els.detailsUrl.value = rpc.details_url;
            if (rpc.state) els.state.value = rpc.state;
            if (rpc.state_url) els.stateUrl.value = rpc.state_url;

            const largeKey = rpc.large_image || rpc.largeImageKey;
            if (largeKey) els.largeImg.value = largeKey;
            const largeTxt = rpc.large_text || rpc.largeImageText;
            if (largeTxt) els.largeText.value = largeTxt;
            if (rpc.large_url) els.largeUrl.value = rpc.large_url;

            const smallKey = rpc.small_image || rpc.smallImageKey;
            if (smallKey) els.smallImg.value = smallKey;
            const smallTxt = rpc.small_text || rpc.smallImageText;
            if (smallTxt) els.smallText.value = smallTxt;
            if (rpc.small_url) els.smallUrl.value = rpc.small_url;

            const hasParty = Boolean(rpc.partyEnable || rpc.party_id || rpc.partyId || (Array.isArray(rpc.party_size) && rpc.party_size.length >= 2) || (rpc.partySize !== undefined && rpc.partyMax !== undefined));
            if (els.partyEnable) {
                els.partyEnable.checked = hasParty;
                if (els.partyGroup) els.partyGroup.classList.toggle("hidden", !hasParty);
            }
            const pId = rpc.party_id || rpc.partyId;
            if (pId) els.partyId.value = pId;

            if (Array.isArray(rpc.party_size) && rpc.party_size.length >= 2) {
                els.partyCur.value = rpc.party_size[0];
                els.partyMax.value = rpc.party_size[1];
            } else if (rpc.partySize !== undefined && rpc.partyMax !== undefined) {
                els.partyCur.value = rpc.partySize;
                els.partyMax.value = rpc.partyMax;
            }

            if (rpc.join !== undefined) {
                if (els.allowJoin) els.allowJoin.checked = Boolean(rpc.join);
                if (els.joinSecret) els.joinSecret.value = rpc.join || "";
            }
            if (rpc.spectate !== undefined) {
                if (els.allowSpectate) els.allowSpectate.checked = Boolean(rpc.spectate);
                if (els.spectateSecret) els.spectateSecret.value = rpc.spectate || "";
            }
            if (rpc.match) {
                if (els.matchSecret) els.matchSecret.value = rpc.match;
            }
            const hasCustomTokens = Boolean((rpc.join && !rpc.join.startsWith("join_")) || (rpc.spectate && !rpc.spectate.startsWith("spec_")) || rpc.match);
            if (hasCustomTokens && els.advancedTokensEnable) {
                els.advancedTokensEnable.checked = true;
                if (els.advancedTokensGroup) els.advancedTokensGroup.classList.remove("hidden");
            }

            if (Array.isArray(rpc.buttons)) {
                if (rpc.buttons[0]) {
                    els.btn1Enable.checked = true;
                    els.btn1Group.classList.remove("hidden");
                    els.btn1Text.value = rpc.buttons[0].label || "";
                    els.btn1Url.value = rpc.buttons[0].url || "";
                }
                if (rpc.buttons[1]) {
                    els.btn2Enable.checked = true;
                    els.btn2Group.classList.remove("hidden");
                    els.btn2Text.value = rpc.buttons[1].label || "";
                    els.btn2Url.value = rpc.buttons[1].url || "";
                }
            }

            const hasStart = Boolean(rpc.start || rpc.startTimestamp);
            const hasEnd = Boolean(rpc.end || rpc.endTimestamp);

            if (hasStart && hasEnd) {
                const sTs = rpc.start ? rpc.start * 1000 : rpc.startTimestamp;
                const eTs = rpc.end ? rpc.end * 1000 : rpc.endTimestamp;
                baseStartTime = sTs;
                baseEndTime = eTs;
                const cur = Math.max(0, Date.now() - sTs);
                const tot = Math.max(1, eTs - sTs);
                if (els.musicCurrent) els.musicCurrent.value = msToTime(cur);
                if (els.musicTotal) els.musicTotal.value = msToTime(tot);
                if (els.musicBarEnable) {
                    els.musicBarEnable.checked = true;
                    if (els.musicBarGroup) els.musicBarGroup.classList.remove("hidden");
                }
                els.startEnable.checked = false;
                els.startGroup.classList.add("hidden");
                els.endEnable.checked = false;
                els.endGroup.classList.add("hidden");
            } else if (hasStart) {
                const ts = rpc.start ? rpc.start * 1000 : rpc.startTimestamp;
                const elapsed = Date.now() - ts;
                baseStartTime = ts;
                els.startOffset.value = msToTime(Math.max(0, elapsed));
                els.startEnable.checked = true;
                els.startGroup.classList.remove("hidden");
                if (els.musicBarEnable) {
                    els.musicBarEnable.checked = false;
                    if (els.musicBarGroup) els.musicBarGroup.classList.add("hidden");
                }
                els.endEnable.checked = false;
                els.endGroup.classList.add("hidden");
                baseEndTime = null;
            } else if (hasEnd) {
                const ts = rpc.end ? rpc.end * 1000 : rpc.endTimestamp;
                const remaining = ts - Date.now();
                baseEndTime = ts;
                els.endOffset.value = msToTime(Math.max(0, remaining));
                els.endEnable.checked = true;
                els.endGroup.classList.remove("hidden");
                if (els.musicBarEnable) {
                    els.musicBarEnable.checked = false;
                    if (els.musicBarGroup) els.musicBarGroup.classList.add("hidden");
                }
                els.startEnable.checked = false;
                els.startGroup.classList.add("hidden");
                baseStartTime = null;
            }

            if (rpc.payload_override) {
                els.overrideEnable.checked = true;
                els.overrideGroup.classList.remove("hidden");
                els.payloadOverride.value = JSON.stringify(rpc.payload_override, null, 2);
            }

            syncIncompatibilities();
        }

        function handleSaveClick(btn) {
            saveConfig();
            const prev = btn.textContent;
            btn.textContent = "Guardado";
            setTimeout(() => { btn.textContent = prev; }, 1400);
        }

        els.save.addEventListener("click", () => handleSaveClick(els.save));
        els.saveBottom.addEventListener("click", () => handleSaveClick(els.saveBottom));

        // Load Preset Example
        els.preset.addEventListener("click", () => {
            applyConfig({
                clientId: configClientIds["TETR.IO"] || "688741895307788290",
                activityType: 0,
                name: "TETR.IO",
                details: "SS Rank — Ranked Match",
                detailsUrl: "https://ch.tetr.io/",
                state: "In Game (1v1 Final Round)",
                stateUrl: "https://tetr.io/",
                startEnable: true,
                startOffset: "00:03:45",
                endEnable: false,
                largeImg: "https://media.tenor.com/FYcKtaceoOoAAAAM/tetris-tetrio.gif",
                largeText: "TETR.IO Live Match",
                largeUrl: "https://tetr.io/",
                smallImg: "https://ch.tetr.io/res/avatar/tetrio.png",
                smallText: "SS Rank",
                smallUrl: "https://ch.tetr.io/u/tetrio",
                partyEnable: true,
                partyId: "tetrio-match-48912",
                partyCur: 2,
                partyMax: 2,
                btn1Enable: true,
                btn1Text: "Ver Perfil de Jugador",
                btn1Url: "https://ch.tetr.io/",
                btn2Enable: true,
                btn2Text: "Mi Sitio Web",
                btn2Url: "https://ema28pro.github.io/",
                instance: true,
                statusDisplayType: "1",
            });
            baseStartTime = Date.now() - parseTimeInput("00:03:45");
            onFormChange();
        });

        // Copy JSON Button
        els.copyJson.addEventListener("click", () => {
            navigator.clipboard.writeText(els.jsonDebug.textContent).then(() => {
                els.copyJson.textContent = "¡Copiado!";
                setTimeout(() => { els.copyJson.textContent = "Copiar"; }, 1200);
            });
        });

        // Live Preview Renderer
        function updatePreview() {
            const payload = buildPayload();
            const rpc = payload.rpc;

            // Debug Outgoing JSON Viewer
            els.jsonDebug.textContent = JSON.stringify(payload, null, 2);

            const actType = getActivityType();
            const headerTitles = {
                0: "PLAYING A GAME",
                1: "STREAMING",
                2: "LISTENING TO",
                3: "WATCHING",
                5: "COMPETING IN"
            };
            const badgeTitles = {
                0: "PLAYING",
                1: "STREAMING",
                2: "LISTENING",
                3: "WATCHING",
                5: "COMPETING"
            };
            els.cardHeader.textContent = headerTitles[actType] || "PLAYING A GAME";
            els.previewTypeBadge.textContent = badgeTitles[actType] || "PLAYING";

            if (!active) {
                els.preview.innerHTML = '<div class="dc-empty">Configura tus campos y pulsa "Activar Estado" para enviar a Discord</div>';
                return;
            }

            const appName = rpc.name || els.appIdBadge.textContent || "Custom Status";
            const details = rpc.details || "";
            const state = rpc.state || "";
            const largeImg = rpc.large_image || "";
            const largeText = rpc.large_text || "";
            const largeUrl = rpc.large_url || "";
            const smallImg = rpc.small_image || "";
            const smallText = rpc.small_text || "";
            const smallUrl = rpc.small_url || "";

            let html = '<div class="dc-body">';
            html += '<div class="dc-main">';

            // Image block
            if (largeImg) {
                html += '<div class="dc-images">';
                const largeImgTag = `<img class="dc-large-img img-tooltip" src="${escapeHtml(largeImg)}" data-tooltip="${escapeHtml(largeText)}" onerror="this.style.display='none'" alt="">`;
                if (largeUrl) {
                    html += `<a href="${escapeHtml(largeUrl)}" target="_blank" rel="noopener">${largeImgTag}</a>`;
                } else {
                    html += largeImgTag;
                }

                if (smallImg) {
                    const smallImgTag = `<img class="dc-small-img img-tooltip" src="${escapeHtml(smallImg)}" data-tooltip="${escapeHtml(smallText)}" onerror="this.style.display='none'" alt="">`;
                    if (smallUrl) {
                        html += `<a href="${escapeHtml(smallUrl)}" target="_blank" rel="noopener">${smallImgTag}</a>`;
                    } else {
                        html += smallImgTag;
                    }
                }
                html += '</div>';
            }

            // Info text block
            html += '<div class="dc-info">';
            html += `<div class="dc-app-name">${escapeHtml(appName)}</div>`;

            if (details) {
                if (rpc.details_url) {
                    html += `<div class="dc-line"><a href="${escapeHtml(rpc.details_url)}" target="_blank" rel="noopener">${escapeHtml(details)}</a> <span class="link-icon">↗</span></div>`;
                } else {
                    html += `<div class="dc-line">${escapeHtml(details)}</div>`;
                }
            }

            if (state) {
                let partySuffix = "";
                if (Array.isArray(rpc.party_size) && rpc.party_size.length >= 2) {
                    partySuffix = ` (${rpc.party_size[0]} of ${rpc.party_size[1]})`;
                }
                const stateDisplay = escapeHtml(state + partySuffix);
                if (rpc.state_url) {
                    html += `<div class="dc-line dc-state-text"><a href="${escapeHtml(rpc.state_url)}" target="_blank" rel="noopener">${stateDisplay}</a> <span class="link-icon">↗</span></div>`;
                } else {
                    html += `<div class="dc-line dc-state-text">${stateDisplay}</div>`;
                }
            } else if (Array.isArray(rpc.party_size) && rpc.party_size.length >= 2) {
                html += `<div class="dc-line dc-state-text">Party (${rpc.party_size[0]} of ${rpc.party_size[1]})</div>`;
            }

            // Timestamps display (clean format matching official Discord client)
            if (rpc.start && rpc.end) {
                const startMs = rpc.start * 1000;
                const endMs = rpc.end * 1000;
                const totalDuration = Math.max(1, endMs - startMs);
                const currentPos = Math.max(0, Math.min(totalDuration, Date.now() - startMs));
                const percent = Math.min(100, Math.max(0, (currentPos / totalDuration) * 100));

                html += `
                    <div class="dc-music-timeline">
                        <span class="dc-music-time">${formatTimer(currentPos)}</span>
                        <div class="dc-music-bar">
                            <div class="dc-music-progress" style="width: ${percent.toFixed(1)}%;"></div>
                            <div class="dc-music-thumb" style="left: ${percent.toFixed(1)}%;"></div>
                        </div>
                        <span class="dc-music-time">${formatTimer(totalDuration)}</span>
                    </div>`;
            } else if (rpc.end) {
                const targetMs = rpc.end * 1000;
                const remaining = Math.max(0, targetMs - Date.now());
                html += `<div class="dc-time">${formatTimer(remaining)} remaining</div>`;
            } else if (rpc.start) {
                const startMs = rpc.start * 1000;
                const elapsed = Math.max(0, Date.now() - startMs);
                html += `<div class="dc-time">${formatTimer(elapsed)} elapsed</div>`;
            }

            html += '</div></div>'; // end dc-info & dc-main

            // Buttons
            if (Array.isArray(rpc.buttons) && rpc.buttons.length > 0) {
                html += '<div class="dc-buttons">';
                rpc.buttons.forEach(btn => {
                    const hasValidUrl = btn.url && (btn.url.startsWith("http://") || btn.url.startsWith("https://"));
                    const targetAttr = hasValidUrl ? 'target="_blank" rel="noopener noreferrer"' : 'onclick="return false;"';
                    const hrefAttr = hasValidUrl ? `href="${escapeHtml(btn.url)}"` : 'href="#"';
                    html += `
                        <a class="dc-btn" ${hrefAttr} ${targetAttr}>
                            <span>${escapeHtml(btn.label)}</span>
                            <svg viewBox="0 0 24 24"><path d="M10 5V3H5.75C4.23122 3 3 4.23122 3 5.75V18.25C3 19.7688 4.23122 21 5.75 21H18.25C19.7688 21 21 19.7688 21 18.25V14H19V18.25C19 18.6642 18.6642 19 18.25 19H5.75C5.33579 19 5 18.6642 5 18.25V5.75C5 5.33579 5.33579 5 5.75 5H10ZM14 3V5H17.59L8.29 14.29L9.71 15.71L19 6.41V10H21V3H14Z"/></svg>
                        </a>`;
                });
                html += '</div>';
            } else if (rpc.join || rpc.spectate) {
                // Native Discord Multiplayer Join/Spectate Buttons Mock
                html += '<div class="dc-buttons">';
                if (rpc.join) {
                    const isFull = Array.isArray(rpc.party_size) && rpc.party_size[0] >= rpc.party_size[1];
                    if (isFull) {
                        html += `
                            <div class="dc-btn dc-btn-disabled" title="Sala llena: el botón saldrá deshabilitado en Discord">
                                <span>Unirse (Sala Llena)</span>
                            </div>`;
                    } else {
                        html += `
                            <div class="dc-btn dc-btn-primary" title="Clickable en Discord para tus amigos">
                                <span>Unirse a la partida</span>
                            </div>`;
                    }
                }
                if (rpc.spectate) {
                    html += `
                        <div class="dc-btn" title="Clickable en Discord para tus amigos">
                            <span>Espectar</span>
                        </div>`;
                }
                html += '</div>';
            }

            html += '</div>'; // end dc-body
            els.preview.innerHTML = html;
        }

        function escapeHtml(str) {
            if (!str) return "";
            const d = document.createElement("div");
            d.textContent = str;
            return d.innerHTML;
        }

        function formatTimer(ms) {
            const totalSec = Math.floor(ms / 1000);
            const h = Math.floor(totalSec / 3600);
            const m = Math.floor((totalSec % 3600) / 60);
            const s = totalSec % 60;
            if (h > 0) return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
            return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
        }

        // Live input listeners
        document.addEventListener("input", (e) => {
            if (e.target && (e.target.tagName === "INPUT" || e.target.tagName === "SELECT" || e.target.tagName === "TEXTAREA")) {
                onFormChange();
            }
        });

        document.addEventListener("change", (e) => {
            if (e.target && (e.target.tagName === "INPUT" || e.target.tagName === "SELECT" || e.target.tagName === "TEXTAREA")) {
                onFormChange();
            }
        });

        // Try restoring saved config on load
        try {
            const saved = localStorage.getItem("customRpcFullConfig") || localStorage.getItem("customRpcConfig");
            if (saved) {
                applyConfig(JSON.parse(saved));
            }
        } catch (e) { }

        // Initial preview render & live timer tick
        updatePreview();
        setInterval(() => {
            if (active && (els.startEnable.checked || els.endEnable.checked || (els.musicBarEnable && els.musicBarEnable.checked))) {
                updatePreview();
            }
        }, 1000);