import type {ServiceApi} from "./api";

export interface UiNode {
  id: string;
  type: string;
  properties: Record<string, any>;
  events: string[];
  children: UiNode[];
}
export interface UiSnapshot { protocolVersion: number; revision: number; root: UiNode }
export interface ResumeState { snapshot: UiSnapshot | null; lastAcceptedSequence: number }
export interface InstanceState { id: string; urn: string; state: ResumeState; stylesheets: Record<string, string>;
  toolkit?: {id: string; protocolVersion: number; types: string[]; layouts: string[]}; rendererUrl?: string }
export interface WidgetInput { nodeId: string; name: string; value: unknown }

/** One live attachment. Disconnected input is deliberately never replayed. */
export class ApplicationConnection {
  private socket?: WebSocket;
  private heartbeat?: ReturnType<typeof setInterval>;
  private retry?: ReturnType<typeof setTimeout>;
  private stopped = false;
  private generation = 0;
  private sequence = 0;
  private revision = 0;
  private pending = false;
  private ready = false;
  private queue: WidgetInput[] = [];
  constructor(private api: ServiceApi, private id: string,
    private snapshot: (snapshot: UiSnapshot, rebuild?: boolean) => void,
    private status: (connected: boolean, error: string) => void) {}

  async connect(): Promise<void> {
    const generation = ++this.generation;
    this.ready = false;
    this.status(false, "Connecting…");
    try {
      const grant = await this.api.request<{ticket: string}>(`api/v1/ui/instances/${this.id}/ticket`, {method: "POST"});
      if (this.stopped || generation !== this.generation) return;
      const url = new URL("ui-bus", document.baseURI);
      url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
      const socket = this.socket = new WebSocket(url);
      let initialSnapshot = true;
      socket.onopen = () => socket.send(JSON.stringify({ticket: grant.ticket}));
      socket.onmessage = ({data}) => {
        if (generation !== this.generation || this.stopped) return;
        try {
          const frame = JSON.parse(data);
          if (frame.type === "snapshot") {
            this.replace(frame.value, initialSnapshot);
            initialSnapshot = false;
          }
          else if (frame.type === "ready") {
            this.sequence = frame.value.lastAcceptedSequence;
            this.ready = true;
            this.status(true, "");
          } else if (frame.type === "ack") {
            this.sequence = Math.max(this.sequence, frame.value);
            this.pending = false;
            this.drain();
          } else if (frame.type === "resync") {
            this.queue = []; this.pending = false;
            this.sequence = frame.value.lastAcceptedSequence;
            if (frame.value.snapshot) this.replace(frame.value.snapshot, true);
            this.status(true, "The interface changed. Please repeat the last action.");
          } else if (frame.type === "error") {
            this.pending = false; this.queue = [];
            this.status(true, String(frame.value));
          }
        } catch { this.status(false, "Invalid application response"); socket.close(); }
      };
      socket.onclose = () => {
        if (generation !== this.generation || this.stopped) return;
        clearInterval(this.heartbeat);
        this.ready = false; this.pending = false; this.queue = [];
        this.status(false, "Disconnected. Reconnecting to the retained application…");
        this.retry = setTimeout(() => void this.connect(), 1500);
      };
      socket.onerror = () => socket.close();
      this.heartbeat = setInterval(() => {
        if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({type: "ping"}));
      }, 20000);
    } catch (error) {
      if (!this.stopped && generation === this.generation) {
        this.status(false, error instanceof Error ? error.message : "Application attachment failed");
        // HTTP authorization/expiry failures require an explicit retry or renewed authentication.
      }
    }
  }

  send(event: WidgetInput): void {
    if (!this.ready || this.stopped) return;
    if (this.queue.length >= 16) { this.status(true, "Too many pending actions"); return; }
    this.queue.push(event); this.drain();
  }
  private drain(): void {
    if (this.pending || !this.ready || this.socket?.readyState !== WebSocket.OPEN) return;
    const event = this.queue.shift();
    if (!event) return;
    this.pending = true;
    this.socket.send(JSON.stringify({type: "input", value: {...event, sequence: ++this.sequence, revision: this.revision}}));
  }
  private replace(snapshot: UiSnapshot, rebuild = false): void {
    if (snapshot.protocolVersion !== 1) throw new Error("Unsupported UI protocol");
    this.revision = snapshot.revision;
    this.snapshot(snapshot, rebuild);
  }
  close(): void {
    this.stopped = true; this.generation++;
    clearInterval(this.heartbeat); clearTimeout(this.retry);
    this.socket?.close(); this.queue = [];
  }
}

/** Only validated portable keys become inline CSS; arbitrary property names are ignored. */
export function layoutStyle(properties: Record<string, any>): Record<string, string | number> {
  const style: Record<string, string | number> = {};
  const p = properties;
  const lengths: Record<string, string> = {width: "width", height: "height", minwidth: "minWidth", maxwidth: "maxWidth",
    minheight: "minHeight", maxheight: "maxHeight", gap: "gap", rowgap: "rowGap", columngap: "columnGap",
    basis: "flexBasis", fontsize: "fontSize", radius: "borderRadius", padding: "padding", margin: "margin"};
  for (const [key, css] of Object.entries(lengths)) if (p[key] !== undefined)
    style[css] = typeof p[key] === "number" ? `${p[key]}px` : String(p[key]);
  if (p.layout) {
    style.display = "flex";
    style.flexDirection = `${p.layout}${p.direction === "reverse" ? "-reverse" : ""}`;
  }
  const aliases: Record<string, string> = {grow: "flexGrow", shrink: "flexShrink", order: "order", wrap: "flexWrap",
    justify: "justifyContent", align: "alignItems", self: "alignSelf", aligncontent: "alignContent",
    overflow: "overflow", overflowx: "overflowX", overflowy: "overflowY"};
  for (const [key, css] of Object.entries(aliases)) if (p[key] !== undefined)
    style[css] = p[key] === "start" ? "flex-start" : p[key] === "end" ? "flex-end" : p[key];
  if (p.fill) style.flexGrow = p.grow ?? 1;
  if (p.fill) style.alignSelf = p.self ?? "stretch";
  if (p.invisible) style.visibility = "hidden";
  return style;
}
