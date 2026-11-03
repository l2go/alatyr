// The live world on the home page. The world runs in a worker; this file only
// draws what the visitor sees and sends what the visitor asks for.
(function () {
  "use strict";

  const SVG = "http://www.w3.org/2000/svg";
  // The part of the island the map shows, in world units: the village and
  // the meadow east of it. North is up.
  const AREA = { x: -71400, y: 256650, scale: 3 };
  const FOLK = new Set(["tutor", "provisioner", "folk"]);

  document.addEventListener("DOMContentLoaded", () => {
    const root = document.querySelector("[data-live]");
    if (!root || !("Worker" in window) || !("WebAssembly" in window)) return;

    const startBox = root.querySelector(".live__start");
    const stage = root.querySelector(".live__stage");
    const map = root.querySelector("[data-live-map]");
    const targetText = root.querySelector("[data-live-target]");
    const dialogBox = root.querySelector("[data-live-dialog]");
    const nearList = root.querySelector("[data-live-near]");
    const agentBox = root.querySelector("[data-live-agent]");
    const errorBox = root.querySelector("[data-live-error]");
    const buttons = {};
    root.querySelectorAll("[data-verb]").forEach((b) => { buttons[b.dataset.verb] = b; });
    const logs = {
      visitor: root.querySelector('[data-log="visitor"]'),
      agent: root.querySelector('[data-log="agent"]'),
    };

    let worker = null;
    let view = null;
    let selected = 0;
    let ended = false;
    const motion = new Map();

    root.querySelector("[data-live-start]").addEventListener("click", (event) => {
      event.currentTarget.disabled = true;
      event.currentTarget.textContent = "Starting…";
      worker = new Worker("/alatyr/live/worker.js");
      worker.onmessage = (e) => receive(e.data);
      worker.onerror = () => fail("The world could not start in this browser.");
      worker.postMessage({ type: "start" });
    });

    agentBox.addEventListener("change", (event) => {
      if (worker) worker.postMessage({ type: "agent", on: event.currentTarget.checked });
    });

    root.querySelectorAll("[data-log-tab]").forEach((tab) => {
      tab.addEventListener("click", () => {
        root.querySelectorAll("[data-log-tab]").forEach((t) => t.setAttribute("aria-selected", String(t === tab)));
        Object.entries(logs).forEach(([who, list]) => { list.hidden = who !== tab.dataset.logTab; });
      });
    });

    Object.entries(buttons).forEach(([verb, button]) => {
      button.addEventListener("click", () => {
        if (verb === "rise") act({ verb });
        else if (verb === "walk") walkTo(selected);
        else if (selected) act({ verb, target: selected });
      });
    });

    map.addEventListener("click", (event) => {
      const hit = event.target.closest("[data-id]");
      if (hit) {
        selected = Number(hit.dataset.id);
        refreshControls();
        draw();
        return;
      }
      const point = map.createSVGPoint();
      point.x = event.clientX;
      point.y = event.clientY;
      const at = point.matrixTransform(map.getScreenCTM().inverse());
      act({ verb: "walk", x: Math.round(AREA.x + at.x * AREA.scale), y: Math.round(AREA.y + at.y * AREA.scale) });
    });

    function act(action) {
      if (worker && !ended) worker.postMessage({ type: "act", who: "visitor", action });
    }

    function walkTo(id) {
      const entry = motion.get(id);
      if (!entry) return;
      const [x, y] = position(entry, performance.now());
      act({ verb: "walk", x: Math.round(x), y: Math.round(y) });
    }

    function receive(msg) {
      if (msg.type === "ready") {
        startBox.hidden = true;
        stage.hidden = false;
        window.requestAnimationFrame(frame);
      } else if (msg.type === "error") {
        fail(msg.text);
      } else if (msg.type === "update") {
        const update = JSON.parse(msg.json);
        if (update.log) append(logs[msg.who], update.log);
        if (update.ended) finish(msg.who, update.ended);
        if (update.view && msg.who === "visitor") {
          view = update.view;
          track(view);
          refreshControls();
          showDialog(view.dialog);
          draw();
        }
      }
    }

    // A participant's session ended: say so, and stop offering what it can
    // no longer do.
    function finish(who, why) {
      append(logs[who], { kind: "error", text: "session ended: " + why });
      if (who === "agent") {
        agentBox.checked = false;
        agentBox.disabled = true;
        return;
      }
      ended = true;
      fail("Your session ended: " + why + ". Reload the page to enter again.");
      refreshControls();
    }

    function fail(text) {
      errorBox.hidden = false;
      errorBox.textContent = text;
    }

    function append(list, entry) {
      const item = document.createElement("li");
      item.className = "entry entry--" + entry.kind + (entry.stage ? " entry--" + entry.stage.toLowerCase() : "");
      if (entry.cause) {
        const cause = document.createElement("span");
        cause.className = "tag tag--cause";
        cause.textContent = "#" + entry.cause;
        item.append(cause, " ");
      }
      item.append(entry.text);
      list.prepend(item);
      while (list.children.length > 200) list.lastElementChild.remove();
    }

    // Remember where each object was and where it is going, so the map can
    // move it between the world's reports.
    function track(v) {
      const now = performance.now();
      const seen = new Set();
      [v.self].concat(v.objects).forEach((o) => {
        if (!o || !o.id) return;
        seen.add(o.id);
        const old = motion.get(o.id);
        // Movement restarts only when the world reports a new one; any
        // other change keeps the time the current movement began.
        const same = old && old.o.x === o.x && old.o.y === o.y && old.o.toX === o.toX && old.o.toY === o.toY && old.o.speed === o.speed;
        motion.set(o.id, { o, t: same ? old.t : now });
      });
      Array.from(motion.keys()).forEach((id) => { if (!seen.has(id)) motion.delete(id); });
      if (selected && !seen.has(selected)) selected = 0;
    }

    function position(entry, now) {
      const o = entry.o;
      const dx = o.toX - o.x;
      const dy = o.toY - o.y;
      const total = Math.hypot(dx, dy);
      if (!o.speed || total === 0) return [o.x, o.y];
      const done = Math.min(1, (o.speed * (now - entry.t) / 1000) / total);
      return [o.x + dx * done, o.y + dy * done];
    }

    function frame() {
      draw();
      window.requestAnimationFrame(frame);
    }

    function draw() {
      if (!view) return;
      const now = performance.now();
      while (map.firstChild) map.firstChild.remove();
      const ground = document.createElementNS(SVG, "rect");
      ground.setAttribute("class", "map__ground");
      ground.setAttribute("width", "900");
      ground.setAttribute("height", "600");
      map.append(ground);
      motion.forEach((entry, id) => {
        const o = entry.o;
        const [x, y] = position(entry, now);
        const cx = (x - AREA.x) / AREA.scale;
        const cy = (y - AREA.y) / AREA.scale;
        const g = document.createElementNS(SVG, "g");
        g.setAttribute("data-id", String(id));
        g.setAttribute("class", "map__obj map__obj--" + kind(o) + (o.dead ? " is-dead" : "") + (id === selected ? " is-selected" : ""));
        const mark = document.createElementNS(SVG, "circle");
        mark.setAttribute("cx", cx.toFixed(1));
        mark.setAttribute("cy", cy.toFixed(1));
        mark.setAttribute("r", o.role === "drop" ? "4" : id === view.self.id ? "9" : "6");
        g.append(mark);
        if (id === view.self.id || FOLK.has(o.role) || o.role === "player" || id === selected) {
          const label = document.createElementNS(SVG, "text");
          label.setAttribute("x", (cx + 11).toFixed(1));
          label.setAttribute("y", (cy + 4).toFixed(1));
          label.textContent = id === view.self.id ? "you" : o.name || "";
          g.append(label);
        }
        map.append(g);
      });
    }

    function kind(o) {
      if (view && o.id === view.self.id) return "self";
      if (FOLK.has(o.role)) return "folk";
      return o.role || "unknown";
    }

    function refreshControls() {
      const target = selected && motion.get(selected) && motion.get(selected).o;
      const dead = Boolean(view && view.self.dead);
      const off = ended || !view || !view.ready;
      buttons.rise.hidden = !dead;
      buttons.rise.disabled = off;
      buttons.walk.disabled = off || dead || !target;
      buttons.talk.disabled = off || dead || !target || !FOLK.has(target.role);
      buttons.strike.disabled = off || dead || !target || target.role !== "creature" || target.dead;
      buttons.take.disabled = off || dead || !target || target.role !== "drop";
      renderNear();
      targetText.textContent = target ? "Chosen: " + (target.name || target.role) + (target.dead ? " (fallen)" : "") : "Choose someone on the map or in the list, or click the ground to walk.";
    }

    // The same choices as the map, as buttons: nearest first.
    function renderNear() {
      const focused = nearList.contains(document.activeElement) ? document.activeElement.dataset.id : "";
      while (nearList.firstChild) nearList.firstChild.remove();
      if (!view) return;
      const me = motion.get(view.self.id);
      const [sx, sy] = me ? position(me, performance.now()) : [view.self.x, view.self.y];
      const near = Array.from(motion.values())
        .map((e) => e.o)
        .filter((o) => o.id !== view.self.id && (FOLK.has(o.role) || o.role === "creature" || o.role === "drop" || o.role === "player"))
        .sort((a, b) => Math.hypot(a.x - sx, a.y - sy) - Math.hypot(b.x - sx, b.y - sy))
        .slice(0, 8);
      near.forEach((o) => {
        const li = document.createElement("li");
        const b = document.createElement("button");
        b.type = "button";
        b.className = "live__pick";
        b.dataset.id = String(o.id);
        b.setAttribute("aria-pressed", String(o.id === selected));
        b.textContent = (o.name || o.role) + (o.dead ? " (fallen)" : "");
        b.addEventListener("click", () => {
          selected = o.id;
          refreshControls();
          draw();
        });
        li.append(b);
        nearList.append(li);
        if (focused === b.dataset.id) b.focus();
      });
    }

    function showDialog(d) {
      while (dialogBox.firstChild) dialogBox.firstChild.remove();
      dialogBox.hidden = !d;
      if (!d) return;
      const who = document.createElement("p");
      who.className = "live__speaker";
      who.textContent = d.speaker;
      dialogBox.append(who);
      (d.lines || []).forEach((line) => {
        const p = document.createElement("p");
        p.textContent = line;
        dialogBox.append(p);
      });
      (d.options || []).forEach((option) => {
        const b = document.createElement("button");
        b.type = "button";
        b.className = "btn btn--small btn--ghost";
        b.textContent = option.label;
        b.disabled = ended;
        b.addEventListener("click", () => act({ verb: "say", option: option.id }));
        dialogBox.append(b);
      });
    }
  });
})();
