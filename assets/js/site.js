// Small enhancements only: the page reads the same without this file.
document.documentElement.classList.add("js");

// For the ones who open the console.
(function greet() {
  const gold = "color:#f6d58e;font-family:monospace;line-height:1.15";
  const soft = "color:#c9b994;font-family:Georgia,serif;font-size:12px";
  const citadel = [
    "            *",
    "           /^\\",
    "      *   |[ ]|   *",
    "     /^\\  |   |  /^\\",
    "    |[ ]|_|[ ]|_|[ ]|",
    "    |   |   _   |   |",
    "  __|___|__| |__|___|__",
  ].join("\n");
  console.log("%c" + citadel, gold);
  console.log("%cALATYR — a Lineage II server, revived.\n" +
    "24 people built it. 24 days rebuilt it. 24 clients play it on every merge.\n" +
    "Curious? Type 42 on the page, or read /alatyr/numbers/\n" +
    "Source: https://github.com/l2go/alatyr", soft);
})();

document.addEventListener("DOMContentLoaded", () => {
  const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  const reveals = document.querySelectorAll(".reveal");
  if (reduced || !("IntersectionObserver" in window)) {
    reveals.forEach((el) => el.classList.add("is-in"));
  } else {
    const seen = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-in");
          seen.unobserve(entry.target);
        }
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });
    reveals.forEach((el) => seen.observe(el));
  }

  const bar = document.querySelector(".bar");
  const progress = document.querySelector(".progress");
  const prose = document.querySelector(".prose");
  let ticking = false;
  const onScroll = () => {
    if (bar) bar.classList.toggle("is-solid", window.scrollY > 40);
    if (progress && prose) {
      const start = prose.offsetTop;
      const span = Math.max(1, prose.offsetHeight - window.innerHeight * 0.6);
      const done = Math.min(1, Math.max(0, (window.scrollY - start + window.innerHeight * 0.3) / span));
      progress.style.transform = "scaleX(" + done.toFixed(4) + ")";
    }
    ticking = false;
  };
  window.addEventListener("scroll", () => {
    if (!ticking) {
      ticking = true;
      window.requestAnimationFrame(onScroll);
    }
  }, { passive: true });
  onScroll();

  // Type 4 and then 2 anywhere outside a field: the citadel answers.
  let last = "";
  let toast = null;
  let timer = 0;
  document.addEventListener("keydown", (event) => {
    const target = event.target;
    if (event.ctrlKey || event.metaKey || event.altKey) return;
    if (target && (target.isContentEditable || /^(input|textarea|select)$/i.test(target.tagName))) return;
    const key = event.key;
    if (last === "4" && key === "2") {
      answer();
      last = "";
      return;
    }
    last = key;
  });

  function answer() {
    const root = document.documentElement;
    if (!reduced) {
      root.classList.remove("is-flare");
      void root.offsetWidth;
      root.classList.add("is-flare");
    }
    if (!toast) {
      toast = document.createElement("div");
      toast.className = "toast";
      toast.setAttribute("role", "status");
      const strong = document.createElement("strong");
      strong.textContent = "FINAL = 42.";
      const text = document.createTextNode(" The answer was in the client's name all along. ");
      const link = document.createElement("a");
      link.href = "/alatyr/numbers/";
      link.textContent = "The numbers";
      toast.append(strong, text, link);
      document.body.appendChild(toast);
    }
    window.clearTimeout(timer);
    window.requestAnimationFrame(() => toast.classList.add("is-on"));
    timer = window.setTimeout(() => {
      toast.classList.remove("is-on");
      root.classList.remove("is-flare");
    }, 4200);
  }
});
