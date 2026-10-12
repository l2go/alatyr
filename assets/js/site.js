// Small enhancements only: the page reads the same without this file.
document.documentElement.classList.add("js");

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
});
