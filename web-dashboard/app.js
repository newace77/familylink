/**
 * FamilyLink Plus 웹 대시보드 (열람 전용)
 *
 * Firestore 트리 (Android FirestoreSchema와 동일 계약):
 *   families/{familyCode}                          단일 문서: policy / deviceHealth / safeZones / paired
 *   families/{familyCode}/alerts|webLogs|staySessions  컬렉션: { data: <JSON 문자열>, timestampMillis }
 *
 * 모든 문서의 "data" 필드는 JSON 문자열이며 Android ModelJson이 만든 키 규칙을 따른다.
 * dailyLimitMinutes는 -1이 "제한 없음", placeName이 null/빈문자열이면 "위치 미확인".
 * 제어 버튼은 존재하지 않는다 — 열람 전용.
 */

// ▼▼ 한 번만: Firebase 콘솔에서 복사한 web 앱 config를 아래에 붙여넣으세요 ▼▼
const FIREBASE_CONFIG = {
  apiKey: "AIzaSyB-VJ_pn2-vxrNIgFfTc92XJHNsHf6Ka4g",
  authDomain: "familylink-plus-2026.firebaseapp.com",
  projectId: "familylink-plus-2026",
  storageBucket: "familylink-plus-2026.firebasestorage.app",
  messagingSenderId: "1052848737686",
  appId: "1:1052848737686:web:bfacf6e4db073a3a61e1b7"
};
// ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import {
  doc, collection, query, orderBy, limit, onSnapshot, getFirestore
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-firestore.js";

const CODE_STORAGE_KEY = "familylink_family_code";
window.__flBooted = true; // 계측: 모듈 파싱 실패 시 이 플래그가 없어 진단 메시지가 뜬다

/** 프라이빗 모드·스토리지 차단 환경에서도 죽지 않는 localStorage 래퍼 */
const safeStorage = {
  get(key) { try { return localStorage.getItem(key); } catch { return null; } },
  set(key, value) { try { localStorage.setItem(key, value); } catch {} },
  remove(key) { try { localStorage.removeItem(key); } catch {} }
};
const SCHEMA = {
  FAMILIES: "families", ALERTS: "alerts", WEB_LOGS: "webLogs",
  STAY_SESSIONS: "staySessions", DATA: "data", TIMESTAMP_MILLIS: "timestampMillis",
  PAIRED: "paired", CHILD_LINKED: "childLinked"
};
const LIMITS = { alerts: 100, webLogs: 200, staySessions: 200 };

const state = {
  code: null, unsub: [], linked: false,
  policy: null, health: null, safeZones: [],
  alerts: [], webLogs: [], staySessions: [], webSearch: ""
};

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
})[c]);

/* ---------- 포맷 ---------- */
function fmtTime(ms) {
  if (!ms) return "";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit"
  }).format(new Date(ms));
}
function fmtDuration(sec) {
  if (!sec || sec <= 0) return "";
  return `${Math.round(sec / 60)}분`;
}
function placeLabel(place) {
  // Review Focus 4: null/빈문자열 위치는 "위치 미확인"
  return (place && String(place).trim()) ? String(place) : "위치 미확인";
}

/* ---------- 데이터 파싱 ---------- */
function parseDocData(snapshotDoc, existing) {
  // modular SDK: 필드 접근은 snapshot.data() 맵으로만 가능하다 (브래킷 접근은 undefined)
  const raw = snapshotDoc?.data?.()?.[SCHEMA.DATA];
  if (typeof raw !== "string") return existing ?? null;
  try { return JSON.parse(raw); } catch { return existing ?? null; }
}

/* ---------- 시작 ---------- */
let db = null;

function initFirebase() {
  if (db || !FIREBASE_CONFIG) return FIREBASE_CONFIG !== null;
  try {
    db = getFirestore(initializeApp(FIREBASE_CONFIG));
    return true;
  } catch (e) {
    console.error(e);
    return false;
  }
}

function stopSync() {
  state.unsub.forEach((u) => { try { u(); } catch {} });
  state.unsub = [];
}

function startSync(code) {
  if (!initFirebase()) {
    $("childLine").textContent = "setup 누락: app.js의 FIREBASE_CONFIG를 먼저 채워 주세요";
    alert("FIREBASE_CONFIG가 설정되지 않았습니다. README.md의 배포 가이드를 따라 app.js를 편집하세요.");
    return;
  }
  stopSync();
  state.code = code;
  state.linked = false;
  $("childLine").textContent = `코드 ${code} · 연결 확인 중…`;

  const rootQ = doc(db, SCHEMA.FAMILIES, code);
  state.unsub.push(onSnapshot(rootQ, (snap) => {
    if (!snap.exists()) {
      $("childLine").textContent = `코드 ${code}: 가족을 찾을 수 없습니다. 코드 확인`;
      return;
    }
    const data = snap.data();
    const paired = data[SCHEMA.PAIRED];
    if (paired && paired[SCHEMA.CHILD_LINKED]) {
      state.linked = true;
      $("childLine").textContent = `실시간 관찰 중`;
    }
    renderSummary(data);
  }, (err) => {
    $("childLine").textContent = `연결 실패: ${err.code ?? err.message}`;
  }));

  const listenColl = (key, holder, idOf, render) => {
    const q = query(
      collection(db, SCHEMA.FAMILIES, code, key),
      orderBy(SCHEMA.TIMESTAMP_MILLIS, "desc"),
      limit(LIMITS[key] ?? 200)
    );
    state.unsub.push(onSnapshot(q, (sn) => {
      const items = [];
      sn.forEach((d) => {
        const parsed = parseDocData(d, null);
        if (parsed) items.push({ ...parsed, _ms: d.data()?.[SCHEMA.TIMESTAMP_MILLIS] ?? parsed.timestampMillis ?? 0 });
      });
      state[holder] = items;
      render();
    }, () => {}));
  };

  listenColl(SCHEMA.WEB_LOGS, "webLogs", (l) => l.id, renderWebLogs);
  listenColl(SCHEMA.STAY_SESSIONS, "staySessions", (s) => s.sessionId, renderStaySessions);
  listenColl(SCHEMA.ALERTS, "alerts", (a) => a.alertId, renderAlerts);
}

function renderSummary(rootData) {
  const policy = parseRootField(rootData.policy, state.policy);
  const health = parseRootField(rootData.deviceHealth, state.health);
  state.safeZones = parseRootFieldArray(rootData.safeZones, state.safeZones);
  state.policy = policy; state.health = health;

  const box = $("summary");
  if (!policy && !health) {
    box.innerHTML = `<span class="empty">(대기: 문서 없음)</span>`;
    return;
  }
  const name = policy?.childName || "우리 아이";
  const used = health?.totalUsedMinutesToday ?? 0;
  const limit = (policy?.dailyLimitMinutes ?? -1) > 0 ? policy.dailyLimitMinutes : null;
  const locked = policy?.isImmediateLocked === true;
  box.innerHTML = `
    <span>${esc(name)} · 오늘 <b style="color:#fff">${used}분</b>${limit ? ` / ${limit}분` : " (제한 없음)"}</span>
    <span>${health ? `${health.batteryPercent}% · ${ringerLabel(health.ringerState)} · ${networkLabel(health.networkState)}` : ""}</span>
    ${locked ? `<span class="badge locked">🔒 즉시잠금중</span>` : ""}
    ${state.safeZones.length ? `<span>안심존 ${state.safeZones.length}곳</span>` : ""}
  `;
  if (policy) {
    $("childLine").textContent = `${name} · ${timeWindow(policy)} · 실시간 열람`;
  }
}
function parseRootField(raw, existing) {
  if (typeof raw !== "string") return existing ?? null;
  try { return JSON.parse(raw); } catch { return existing ?? null; }
}
function parseRootFieldArray(raw, existing) {
  if (typeof raw !== "string") return existing ?? [];
  try { const v = JSON.parse(raw); return Array.isArray(v) ? v : existing ?? []; }
  catch { return existing ?? []; }
}
const ringerLabels = { NORMAL: "소리", VIBRATE: "무음(진동)", SILENT: "무음" };
function ringerLabel(v) { return ringerLabels[v] ?? v ?? "?"; }
const networkLabels = { WIFI: "와이파이", MOBILE_DATA: "데이터", OFFLINE: "오프라인", NONE: "오프라인" };
function networkLabel(v) { return networkLabels[v] ?? v ?? "?"; }
function timeWindow(policy) {
  const two = (n) => String(n ?? 0).padStart(2, "0");
  return `${two(policy.allowedStartHour)}:${two(policy.allowedStartMinute)}~${two(policy.allowedEndHour)}:${two(policy.allowedEndMinute)}`;
}

/* ---------- 렌더: 체류지 ---------- */
function renderStaySessions() {
  const target = $("stayList");
  if (!state.staySessions.length) {
    target.innerHTML = `<span class="empty">(아직 기록 없음 — 데모 모드면 부모 앱 확인)</span>`;
    return;
  }
  target.innerHTML = state.staySessions.slice(0, 30).map((s) => {
    let arrive = new Date(s.arrivedAtMillis ?? s._ms ?? Date.now());
    const departed = s.departedAtMillis > 0 ? fmtTime(s.departedAtMillis) : "머무는 중";
    const apps = (s.appUsages ?? []).map((u) =>
      `<span class="pill">${esc(u.appName || u.packageName)} ${u.usageMinutes ?? 0}분</span>`
    ).join("");
    const webs = (s.webLogs ?? []).length;
    return `<div class="item">
      <div class="title">${esc(placeLabel(s.placeName))}</div>
      <div class="meta">${fmtTime(arrive.getTime())} ~ ${departed}${s.address ? " · " + esc(s.address) : ""}</div>
      ${apps ? `<div class="body">${apps}</div>` : ""}
      ${webs ? `<div class="meta">웹 기록 ${webs}건 포함</div>` : ""}
    </div>`;
  }).join("");
}

/* ---------- 렌더: 웹·유튜브 ---------- */
function renderWebLogs() {
  const target = $("webList");
  const q = state.webSearch.trim().toLowerCase();
  const logs = state.webLogs.filter((l) => !q ||
    (l.title ?? "").toLowerCase().includes(q) ||
    (l.urlOrQuery ?? "").toLowerCase().includes(q) ||
    placeLabel(l.placeName).toLowerCase().includes(q));
  if (!logs.length) {
    target.innerHTML = state.webLogs.length
      ? `<span class="empty">(검색 결과 없음)</span>`
      : `<span class="empty">(아직 기록 없음)</span>`;
    return;
  }
  target.innerHTML = logs.slice(0, 50).map((l) => {
    let arrive = new Date(l.timestampMillis ?? l._ms ?? 0);
    const isYT = l.contentType === "YOUTUBE_VIDEO" || l.contentType === "YOUTUBE_SHORTS";
    const icon = isYT ? "▶" : (l.contentType === "SEARCH_QUERY" ? "🔍" : "🌐");
    return `<div class="item">
      <div class="title">${icon} ${esc(l.title || l.urlOrQuery || "-")}</div>
      <div class="meta">${esc(l.browserOrAppName || l.sourcePackage)} · ${fmtTime(arrive.getTime())}
        · ${esc(placeLabel(l.placeName))}${l.durationSeconds ? ` · ${fmtDuration(l.durationSeconds)}` : ""}</div>
      <div class="body" style="word-break:break-all;">${esc(l.urlOrQuery || "")}</div>
    </div>`;
  }).join("");
}

/* ---------- 렌더: 알림 ---------- */
function renderAlerts() {
  const target = $("alertList");
  if (!state.alerts.length) {
    target.innerHTML = `<span class="empty">(아직 알림 없음)</span>`;
    return;
  }
  target.innerHTML = state.alerts.slice(0, 30).map((a) => {
    let t = new Date(a.timestampMillis ?? a._ms ?? 0);
    return `<div class="item">
      <div class="title">${esc(a.title || "(제목 없음)")}${a.isAcknowledged ? ' <span class="pill">확인됨</span>' : ""}</div>
      <div class="meta">${fmtTime(t.getTime())}${a.placeName ? " · " + esc(placeLabel(a.placeName)) : ""}</div>
      <div class="body">${esc(a.message || "")}</div>
    </div>`;
  }).join("");
}

/* ---------- 게이트 ---------- */
function enterDashboard(code) {
  if (!/^[0-9]{6}$/.test(code)) {
    $("gateMsg").textContent = "6자리 숫자를 입력해 주세요";
    return;
  }
  safeStorage.set(CODE_STORAGE_KEY, code);
  $("gate").hidden = true;
  $("gateMsg").textContent = "";
  startSync(code);
}

$("changeCodeBtn").addEventListener("click", () => {
  safeStorage.remove(CODE_STORAGE_KEY);
  stopSync();
  $("gate").hidden = false;
});

$("search").addEventListener("input", (e) => { state.webSearch = e.target.value; renderWebLogs(); });

$("connectBtn").addEventListener("click", () => enterDashboard($("codeInput").value.trim()));
$("codeInput").addEventListener("keydown", (e) => {
  if (e.key === "Enter") enterDashboard($("codeInput").value.trim());
});
$("codeInput").addEventListener("input", (e) => {
  e.target.value = e.target.value.replace(/\D/g, "").slice(0, 6);
});

window.addEventListener("DOMContentLoaded", () => {
  const saved = safeStorage.get(CODE_STORAGE_KEY);
  if (saved) {
    $("gate").hidden = true;
    startSync(saved);
  } else {
    $("gate").hidden = false;
  }
});
