import {createApp, defineComponent, h, provide, ref, shallowRef} from "vue";
import {Quasar} from "quasar";
import ApplicationScaffold from "./components/ApplicationScaffold.vue";
import {applicationFilesKey} from "./services/application-widgets";
import type {InstanceState, UiSnapshot, WidgetInput} from "./services/application-ui";
import "quasar/dist/quasar.css";
import "@quasar/extras/material-icons/material-icons.css";
import "./styles/application-ui.css";

declare global {
  interface Window {
    ide: {input(frame: string): void; file?(id: string, request: string): void; save?(name: string, content: string): void};
    receive(frame: {type: string; value: any}): void;
    disconnected(): void;
    applicationUiFileReply(id: string, response: string): void;
  }
}
const snapshot = shallowRef<UiSnapshot>();
const connected = ref(false);
const epoch = ref(0);
const status = document.getElementById("status")!;
let instance: InstanceState | undefined;
let supported = true, pending = false, sequence = 0, revision = 0, generation = 0;
let queue: WidgetInput[] = [];
let styles: HTMLStyleElement[] = [];
const files = new Map<string, {resolve(value: any): void; reject(error: Error): void; timer: ReturnType<typeof setTimeout>}>();
let requestId = 0;
function file(request: unknown): Promise<any> {
  if (!connected.value || !window.ide?.file) return Promise.reject(new Error("Application file transfer unavailable"));
  const id = String(++requestId);
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { files.delete(id); reject(new Error("Application file transfer timed out")); }, 35000);
    files.set(id, {resolve, reject, timer});
    try { window.ide.file!(id, JSON.stringify(request)); }
    catch (error) { clearTimeout(timer); files.delete(id); reject(error); }
  });
}
window.applicationUiFileReply = (id, json) => {
  const waiting = files.get(id); if (!waiting) return;
  files.delete(id); clearTimeout(waiting.timer);
  try { const response = JSON.parse(json); if (response.error) throw new Error(response.error); waiting.resolve(response.value); }
  catch (error) { waiting.reject(error instanceof Error ? error : new Error(String(error))); }
};
window.disconnected = () => {
  connected.value = false; pending = false; queue = []; generation++; epoch.value++;
  for (const waiting of files.values()) { clearTimeout(waiting.timer); waiting.reject(new Error("Application disconnected")); }
  files.clear();
};
function replace(value: UiSnapshot): void {
  if (value.protocolVersion !== 1) throw new Error("Unsupported application UI protocol");
  revision = value.revision; snapshot.value = value;
}
function drain(): void {
  if (!connected.value || pending || !queue.length) return;
  pending = true;
  window.ide.input(JSON.stringify({type: "input", value: {...queue.shift()!, sequence: ++sequence, revision}}));
}
function input(event: WidgetInput): void {
  if (!connected.value) return;
  if (queue.length >= 16) { status.textContent = "Too many pending actions"; return; }
  queue.push(event); drain();
}
window.receive = frame => {
  try {
    if (frame.type === "instance") {
      window.disconnected(); instance = frame.value;
      supported = !instance?.toolkit || instance.toolkit.protocolVersion === 1 && instance.toolkit.id === "quasar";
      snapshot.value = undefined; epoch.value++;
      if (!supported) throw new Error("This application uses a custom toolkit. Open its runtime web interface.");
      styles.forEach(style => style.remove()); styles = [];
      for (const css of Object.values(instance?.stylesheets || {})) {
        const style = document.createElement("style"); style.textContent = css; document.head.append(style); styles.push(style);
      }
      if (instance?.state.snapshot) replace(instance.state.snapshot);
    } else if (frame.type === "snapshot" && supported) replace(frame.value);
    else if (frame.type === "ready" && supported) {
      sequence = frame.value.lastAcceptedSequence; connected.value = true; status.textContent = "Connected";
    } else if (frame.type === "ack") { sequence = Math.max(sequence, frame.value); pending = false; drain(); }
    else if (frame.type === "resync") {
      queue = []; pending = false; sequence = frame.value.lastAcceptedSequence; epoch.value++;
      if (frame.value.snapshot) replace(frame.value.snapshot);
      status.textContent = "The interface changed. Please repeat the last action.";
    } else if (frame.type === "error") { queue = []; pending = false; status.textContent = String(frame.value); }
    else if (frame.type === "disconnected") { window.disconnected(); status.textContent = "Disconnected"; }
    else if (frame.type === "stopped") { window.disconnected(); snapshot.value = undefined; status.textContent = "Application stopped"; }
  } catch (error) { window.disconnected(); status.textContent = error instanceof Error ? error.message : String(error); }
};
const app = createApp(defineComponent({setup() {
  provide(applicationFilesKey, {
    async upload(nodeId, selected) {
      if (selected.size > 4194304) throw new Error("File exceeds the upload limit");
      const current = generation;
      const bytes = new Uint8Array(await selected.arrayBuffer());
      let binary = "";
      for (let index = 0; index < bytes.length; index += 16384) binary += String.fromCharCode(...bytes.subarray(index, index + 16384));
      const handle = await file({operation: "upload", nodeId, name: selected.name, mediaType: selected.type || "application/octet-stream", content: btoa(binary)});
      if (current !== generation) throw new Error("Application attachment changed");
      return handle;
    },
    async download(handle) {
      const current = generation;
      const result = await file({operation: "download", handle});
      if (current !== generation) throw new Error("Application attachment changed");
      if (typeof window.ide.save === "function") { window.ide.save(result.name, result.content); return; }
      const bytes = Uint8Array.from(atob(result.content), c => c.charCodeAt(0));
      const url = URL.createObjectURL(new Blob([bytes], {type: "application/octet-stream"}));
      const anchor = document.createElement("a"); anchor.href = url; anchor.download = result.name; anchor.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
  });
  return () => snapshot.value ? h(ApplicationScaffold, {key: epoch.value, snapshot: snapshot.value, disabled: !connected.value, onInput: input}) : null;
}}));
app.use(Quasar); app.mount("#app");
window.addEventListener("beforeunload", () => { window.disconnected(); app.unmount(); styles.forEach(style => style.remove()); });
