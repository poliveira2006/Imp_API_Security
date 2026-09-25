/* ============================================================
   ScanMalware — frontend conectado ao backend Java.
   O layout foi mantido do frontend enviado pelo usuário.

   Fluxo real:
   1. POST /api/scan { url }
   2. O backend Java consulta a ScanMalware API e aguarda o resultado
   3. O frontend recebe o JSON final e monta a tela
   ============================================================ */
(() => {
  "use strict";

  const $ = (sel, ctx = document) => ctx.querySelector(sel);
  const $$ = (sel, ctx = document) => Array.from(ctx.querySelectorAll(sel));
  const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------------- Tema ---------------- */
  const root = document.documentElement;
  const themeToggle = $("#themeToggle");
  const prefersLight = window.matchMedia("(prefers-color-scheme: light)").matches;
  root.setAttribute("data-theme", prefersLight ? "light" : "dark");
  themeToggle.addEventListener("click", () => {
    const next = root.getAttribute("data-theme") === "dark" ? "light" : "dark";
    root.setAttribute("data-theme", next);
    $('meta[name="theme-color"]').setAttribute("content", next === "dark" ? "#0A0E1A" : "#F8FAFC");
  });

  /* ---------------- Nav com blur ao rolar ---------------- */
  const nav = $("#nav");
  const onScroll = () => nav.classList.toggle("is-scrolled", window.scrollY > 12);
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  /* ---------------- Reveal ao entrar na viewport ---------------- */
  const io = new IntersectionObserver(
    (entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) {
          e.target.classList.add("is-visible");
          io.unobserve(e.target);
        }
      });
    },
    { threshold: 0.12 }
  );
  $$(".reveal").forEach((el) => io.observe(el));

  /* ---------------- Contadores das stats ---------------- */
  const statObserver = new IntersectionObserver(
    (entries) => {
      entries.forEach((e) => {
        if (!e.isIntersecting) return;
        statObserver.unobserve(e.target);
        const target = parseFloat(e.target.dataset.count);
        const decimals = parseInt(e.target.dataset.decimals || "0", 10);
        const dur = prefersReducedMotion ? 0 : 1200;
        const start = performance.now();
        const tick = (now) => {
          const p = dur === 0 ? 1 : Math.min((now - start) / dur, 1);
          const eased = 1 - Math.pow(1 - p, 3);
          const val = target * eased;
          e.target.textContent = decimals
            ? val.toFixed(decimals)
            : Math.round(val).toLocaleString("pt-BR");
          if (p < 1) requestAnimationFrame(tick);
        };
        requestAnimationFrame(tick);
      });
    },
    { threshold: 0.4 }
  );
  $$('[data-count]').forEach((el) => statObserver.observe(el));

  /* ============================================================
     Níveis de risco
     ============================================================ */
  const RISK_LEVELS = {
    safe:   { label: "Seguro",      color: "var(--risk-safe)",   title: "Nenhuma ameaça indicada pelo veredito" },
    low:    { label: "Baixo Risco", color: "var(--risk-low)",    title: "Risco baixo" },
    medium: { label: "Risco Médio", color: "var(--risk-medium)", title: "Atenção recomendada" },
    high:   { label: "Alto Risco",  color: "var(--risk-high)",   title: "Risco elevado" },
    error:  { label: "Erro",        color: "var(--risk-medium)", title: "Não foi possível concluir a análise" }
  };

  const levelForResult = (dados) => {
    const nivel = String(dados?.nivel || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
    if (nivel === "seguro") return "safe";
    if (nivel === "baixo") return "low";
    if (nivel === "medio") return "medium";
    if (nivel === "alto") return "high";
    if (nivel === "erro") return "error";

    const verdict = String(dados?.verdict || "").toLowerCase();
    if (verdict === "safe") return "safe";
    if (verdict === "low") return "low";
    if (verdict === "medium") return "medium";
    if (verdict === "high") return "high";

    const score = Number(dados?.riskScore ?? 0);
    if (score >= 70) return "high";
    if (score >= 40) return "medium";
    if (score > 0) return "low";
    return "safe";
  };

  /* ============================================================
     Referências da página
     ============================================================ */
  const form = $("#scanForm");
  const input = $("#urlInput");
  const errorEl = $("#urlError");
  const scanBtn = $("#scanBtn");
  const apiStatus = $("#apiStatus");

  const progressSection = $("#progressSection");
  const resultSection = $("#resultSection");
  const alertSection = $("#alertSection");
  const progressFill = $("#progressFill");
  const progressUrl = $("#progressUrl");
  const progressEta = $("#progressEta");
  const timeline = $("#timeline");

  const state = { scanning: false, timers: [] };
  const clearTimers = () => {
    state.timers.forEach((timer) => clearTimeout(timer));
    state.timers = [];
  };
  const later = (fn, ms) => {
    const timer = setTimeout(fn, ms);
    state.timers.push(timer);
    return timer;
  };

  /* ---------------- Status do backend ---------------- */
  function setApiStatus(online) {
    apiStatus.classList.toggle("is-offline", !online);
    apiStatus.querySelector(".txt").textContent = online ? "API Online" : "API Offline";
  }

  async function verificarBackend() {
    try {
      const response = await fetch("/api/status", { cache: "no-store" });
      if (!response.ok) throw new Error("Backend indisponível");
      setApiStatus(true);
    } catch {
      setApiStatus(false);
    }
  }

  /* ---------------- Validação de URL ---------------- */
  function normalizeUrl(raw) {
    let value = raw.trim();
    if (!value) return null;
    if (!/^https?:\/\//i.test(value)) value = "https://" + value;

    try {
      const url = new URL(value);
      if (!url.hostname || !url.hostname.includes(".")) return null;
      return url;
    } catch {
      return null;
    }
  }

  function setError(message) {
    errorEl.textContent = message ? "⚠ " + message : "";
  }

  /* ---------------- Fluxo de scan ---------------- */
  async function startScan(rawUrl) {
    const url = normalizeUrl(rawUrl);
    if (!url) {
      setError("URL inválida. Verifique o endereço e tente novamente.");
      input.focus();
      return;
    }

    setError("");
    clearTimers();
    state.scanning = true;
    scanBtn.disabled = true;

    resultSection.classList.add("hidden");
    alertSection.classList.add("hidden");
    progressSection.classList.remove("hidden");

    progressUrl.textContent = url.href;
    progressEta.textContent = "consultando API";
    setTimelineStep(0);
    setProgress(10);

    later(() => {
      setTimelineStep(1);
      setProgress(45);
    }, 900);
    later(() => setProgress(72), 2500);
    later(() => setProgress(90), 5000);

    try {
      const response = await fetch("/api/scan", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url: url.href })
      });

      let dados = {};
      try {
        dados = await response.json();
      } catch {
        throw new Error("O servidor retornou uma resposta inválida.");
      }

      if (!response.ok) {
        throw new Error(dados.erro || "Não foi possível realizar o scan.");
      }

      setApiStatus(true);
      setProgress(100);
      setTimelineStep(2);
      progressEta.textContent = "concluído";

      later(() => {
        progressSection.classList.add("hidden");
        renderResult(dados);
        addHistory(dados);
        finishScan();
        resultSection.scrollIntoView({
          behavior: prefersReducedMotion ? "auto" : "smooth",
          block: "start"
        });
      }, 450);
    } catch (error) {
      console.error("Erro no scan:", error);
      setApiStatus(false);
      progressSection.classList.add("hidden");
      showError("error", "Não foi possível concluir a análise", error.message || "Erro desconhecido.");
      finishScan();
    }
  }

  function finishScan() {
    state.scanning = false;
    scanBtn.disabled = false;
  }

  function setProgress(percent) {
    progressFill.style.width = percent + "%";
  }

  function setTimelineStep(index) {
    $$(".tl-step", timeline).forEach((step, i) => {
      step.classList.toggle("is-done", i < index);
      step.classList.toggle("is-active", i === index);
    });

    $$(".tl-line", timeline).forEach((line, i) => {
      line.style.setProperty("--fill", i < index ? "100%" : i === index ? "50%" : "0%");
    });
  }

  /* ---------------- Renderização do resultado real ---------------- */
  function renderResult(dados) {
    const level = levelForResult(dados);
    const meta = RISK_LEVELS[level];
    const score = Math.max(0, Math.min(100, Number(dados.riskScore ?? 0)));
    const url = dados.url || "";

    resultSection.classList.remove("hidden");
    $("#shotUrl").textContent = url || "URL não disponível";
    $("#openTab").href = url || "#";

    /* Screenshot */
    const skeleton = $("#shotSkeleton");
    const image = $("#shotImg");
    const screenshot = dados.screenshot || "";

    skeleton.style.display = "block";
    image.style.display = "none";

    if (screenshot) {
      image.onload = () => {
        skeleton.style.display = "none";
        image.style.display = "block";
      };
      image.onerror = () => {
        skeleton.style.display = "none";
        image.style.display = "none";
      };
      image.src = screenshot;
    } else {
      skeleton.style.display = "none";
      image.style.display = "none";
    }

    /* Veredito */
    const verdict = $("#verdict");
    verdict.textContent = dados.nivel || dados.verdict || meta.label;
    verdict.dataset.level = level;

    /* Score */
    animateGauge(score, level);
    $("#gaugeTitle").textContent = meta.title;

    const status = String(dados.status || "").toLowerCase();
    $("#gaugeText").textContent = status
      ? `Status da análise: ${dados.status}. Score informado pela API: ${score}/100.`
      : `Score informado pela API: ${score}/100.`;

    /* Indicadores disponíveis no backend */
    renderIndicators(dados, level, score);

    /* Análise de IA real, quando disponível */
    $("#aiContent").innerHTML = renderMarkdown(
      dados.aiAnalysis || "A API não retornou uma análise de IA para este scan."
    );
  }

  function renderIndicators(dados, level, score) {
    const list = $("#indicators");
    const scoreType = score >= 70 ? "bad" : score >= 40 ? "warn" : "ok";
    const statusType = /completed/i.test(dados.status || "") ? "ok" : /failed|error/i.test(dados.status || "") ? "bad" : "warn";
    const verdictType = level === "high" ? "bad" : level === "medium" ? "warn" : level === "safe" || level === "low" ? "ok" : "warn";
    const screenshotType = dados.screenshot ? "ok" : "warn";

    const items = [
      [statusType, "Status da análise", dados.status || "Não informado"],
      [scoreType, "Score de risco", `${score}/100`],
      [verdictType, "Veredito", dados.nivel || dados.verdict || "Não informado"],
      [screenshotType, "Captura de tela", dados.screenshot ? "Disponível" : "Indisponível"]
    ];

    list.innerHTML = items.map(([type, label, note]) => {
      const icon =
        type === "ok"
          ? '<path d="M20 6 9 17l-5-5"/>'
          : type === "warn"
          ? '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>'
          : '<path d="M18 6 6 18M6 6l12 12"/>';

      return `<li>
        <span class="ico" data-t="${type}">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icon}</svg>
        </span>
        <span>${escapeHtml(label)}</span>
        <span class="desc">${escapeHtml(note)}</span>
      </li>`;
    }).join("");
  }

  function animateGauge(score, level) {
    const gauge = $("#gauge");
    const num = $("#gaugeNum");
    gauge.style.setProperty("--gcolor", RISK_LEVELS[level].color);
    gauge.setAttribute("aria-label", `Score de risco ${score} de 100`);

    const duration = prefersReducedMotion ? 0 : 1000;
    const start = performance.now();
    const from = parseInt(num.textContent, 10) || 0;

    const step = (now) => {
      const progress = duration === 0 ? 1 : Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      const value = Math.round(from + (score - from) * eased);
      num.textContent = value;
      gauge.style.setProperty("--val", value);
      if (progress < 1) requestAnimationFrame(step);
    };

    requestAnimationFrame(step);
  }

  /* ---------------- Markdown simples da análise de IA ---------------- */
  function renderMarkdown(markdown) {
    const safeText = String(markdown || "").trim();
    if (!safeText) return "<p>Nenhuma análise adicional foi retornada.</p>";

    return safeText
      .split("\n\n")
      .map((block) => {
        const lines = block.split("\n");
        if (lines.every((line) => line.trim().startsWith("- "))) {
          const items = lines
            .map((line) => `<li>${inline(escapeHtml(line.replace(/^- /, "")))}</li>`)
            .join("");
          return `<ul>${items}</ul>`;
        }
        return `<p>${inline(escapeHtml(block))}</p>`;
      })
      .join("");

    function inline(text) {
      return text
        .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
        .replace(/`(.+?)`/g, "<code>$1</code>");
    }
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  /* ---------------- Alertas ---------------- */
  function showError(kind, title, text) {
    const box = $("#alertBox");
    box.dataset.kind = kind;
    $("#alertTitle").textContent = title;
    $("#alertText").textContent = text;

    const ico = kind === "offline"
      ? '<path d="M2 8.5a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 20h.01"/><path d="m2 2 20 20" stroke-width="1.5"/>'
      : '<path d="M12 9v4m0 4h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/>';

    $("#alertIco").innerHTML = `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ico}</svg>`;
    progressSection.classList.add("hidden");
    resultSection.classList.add("hidden");
    alertSection.classList.remove("hidden");
  }

  /* ============================================================
     Histórico real (localStorage)
     ============================================================ */
  const historyGrid = $("#historyGrid");
  const historyEmpty = $("#historyEmpty");
  let history = loadHistory();
  let activeFilter = "all";

  function loadHistory() {
    try {
      const saved = JSON.parse(localStorage.getItem("scanmalware_history") || "[]");
      return Array.isArray(saved) ? saved : [];
    } catch {
      return [];
    }
  }

  function saveHistory() {
    localStorage.setItem("scanmalware_history", JSON.stringify(history.slice(0, 12)));
  }

  function relTime(timestamp) {
    const diff = Math.max(0, Math.floor((Date.now() - timestamp) / 1000));
    if (diff < 60) return "agora";
    if (diff < 3600) return `há ${Math.floor(diff / 60)} min`;
    if (diff < 86400) return `há ${Math.floor(diff / 3600)} h`;
    return `há ${Math.floor(diff / 86400)} d`;
  }

  function renderHistory() {
    const items = history.filter((item) => {
      if (activeFilter === "all") return true;
      if (activeFilter === "safe") return item.level === "safe" || item.level === "low";
      if (activeFilter === "high") return item.level === "high";
      return true;
    });

    historyEmpty.classList.toggle("hidden", items.length > 0);
    historyGrid.classList.toggle("hidden", items.length === 0);

    historyGrid.innerHTML = items.map((item) => {
      const meta = RISK_LEVELS[item.level] || RISK_LEVELS.safe;
      return `<article class="hist-card">
        <div class="hist-top">
          <span class="favicon" aria-hidden="true">${escapeHtml((item.host || "?").charAt(0).toUpperCase())}</span>
          <div style="min-width:0">
            <div class="hist-domain" title="${escapeHtml(item.host || "")}">${escapeHtml(item.host || "URL")}</div>
            <div class="hist-time">${relTime(item.at)}</div>
          </div>
        </div>
        <span class="risk-badge" data-level="${item.level}">${meta.label} · ${Number(item.score || 0)}</span>
        <div class="hist-reveal">Scan concluído · score ${Number(item.score || 0)}/100 · ID ${escapeHtml(item.scanId || "não informado")}</div>
      </article>`;
    }).join("");
  }

  function addHistory(dados) {
    let host = "URL";
    try {
      host = new URL(dados.url).hostname.replace(/^www\./, "");
    } catch {
      host = dados.url || "URL";
    }

    const item = {
      host,
      level: levelForResult(dados),
      score: Number(dados.riskScore ?? 0),
      scanId: dados.scanId || "",
      at: Date.now()
    };

    history = [item, ...history.filter((old) => old.scanId !== item.scanId)].slice(0, 12);
    saveHistory();
    renderHistory();
  }

  $$(".filters button").forEach((button) => {
    button.addEventListener("click", () => {
      $$(".filters button").forEach((b) => {
        b.classList.remove("is-active");
        b.setAttribute("aria-selected", "false");
      });
      button.classList.add("is-active");
      button.setAttribute("aria-selected", "true");
      activeFilter = button.dataset.filter;
      renderHistory();
    });
  });

  renderHistory();

  /* ============================================================
     Bloco IA colapsável
     ============================================================ */
  const aiToggle = $("#aiToggle");
  aiToggle.addEventListener("click", () => {
    const block = $("#aiBlock");
    const open = block.classList.toggle("is-open");
    aiToggle.setAttribute("aria-expanded", String(open));
  });

  /* ============================================================
     Eventos
     ============================================================ */
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    if (state.scanning) return;
    startScan(input.value);
  });

  input.addEventListener("input", () => setError(""));

  $$(".chip").forEach((chip) => {
    chip.addEventListener("click", () => {
      input.value = chip.dataset.example;
      startScan(chip.dataset.example);
    });
  });

  $("#rescanBtn").addEventListener("click", () => {
    input.scrollIntoView({ behavior: prefersReducedMotion ? "auto" : "smooth", block: "center" });
    input.focus();
  });

  $("#alertRetry").addEventListener("click", () => {
    alertSection.classList.add("hidden");
    input.focus();
  });

  window.addEventListener("offline", () => {
    setApiStatus(false);
    showError("offline", "Sem conexão com o servidor", "Não foi possível alcançar o backend do ScanMalware.");
  });

  window.addEventListener("online", () => {
    verificarBackend();
  });

  verificarBackend();
})();
