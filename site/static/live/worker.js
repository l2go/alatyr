// Runs the Go world and its gateway as WebAssembly, away from the page's
// thread. The page sends actions; the world's participants send updates.
importScripts("wasm_exec.js");

const go = new Go();
let started = false;

async function start() {
  const wasm = await WebAssembly.instantiateStreaming(fetch("alatyr-live.wasm"), go.importObject);
  go.run(wasm.instance);
  const world = new Uint8Array(await (await fetch("world.zip")).arrayBuffer());
  const err = self.alatyr.start(world, (who, json) => postMessage({ type: "update", who, json }));
  if (err) throw new Error(err);
  started = true;
  postMessage({ type: "ready" });
}

onmessage = (event) => {
  const msg = event.data;
  if (msg.type === "start" && !started) {
    start().catch((e) => postMessage({ type: "error", text: String(e && e.message || e) }));
    return;
  }
  if (!started) return;
  if (msg.type === "act") {
    const err = self.alatyr.act(msg.who, JSON.stringify(msg.action));
    if (err) postMessage({ type: "error", text: err });
  } else if (msg.type === "agent") {
    self.alatyr.agent(Boolean(msg.on));
  }
};
