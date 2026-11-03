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
    const motion = new Map();

    root.querySelector("[data-live-start]").addEventListener("click", (event) => {
      event.currentTarget.disabled = true;
      event.currentTarget.textContent = "Starting…";
      worker = new Worker("/alatyr/live/worker.js");
      worker.onmessage = (e) => receive(e.data);
      worker.onerror = () => fail("The world could not start in this browser.");
      worker.postMessage({ type: "start" });
    });

    root.querySelector("[data-live-agent]").addEventListener("change", (event) => {
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
      if (worker) worker.postMessage({ type: "act", who: "visitor", action });
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
        if (update.view && msg.who === "visitor") {
          view = update.view;
          track(view);
          refreshControls();
          showDialog(view.dialog);
          draw();
        }
      }
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
        motion.set(o.id, { o, t: now });
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
      buttons.rise.hidden = !dead;
      buttons.talk.disabled = dead || !target || !FOLK.has(target.role);
      buttons.strike.disabled = dead || !target || target.role !== "creature" || target.dead;
      buttons.take.disabled = dead || !target || target.role !== "drop";
      targetText.textContent = target ? "Chosen: " + (target.name || target.role) + (target.dead ? " (fallen)" : "") : "Choose someone on the map, or click the ground to walk.";
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
        b.addEventListener("click", () => act({ verb: "say", option: option.id }));
        dialogBox.append(b);
      });
    }
  });
})();
