import type {ElkExtendedEdge, ElkLabel, ElkNode, ElkPort} from "elkjs/lib/elk-api";
import type {SGraph, SModelElement} from "sprotty-protocol";

export type Metadata = Record<string, unknown>;

export interface ChartNode extends ElkNode {
    metadata?: Metadata;
    children?: ChartNode[];
    ports?: (ElkPort & { role: "INPUT" | "OUTPUT"; metadata?: Metadata })[];
    edges?: (ElkExtendedEdge & { metadata?: Metadata; container?: string })[];
}

export interface FlowChart {
    root: ChartNode;
    metadata?: Metadata
}

export interface DiagramSelection {
    id: string;
    kind: string;
    metadata: Metadata
}

/** Work on a copy. ELK and Sprotty must never mutate the fetched transport document. */
export async function layoutChart(chart: FlowChart, signal?: AbortSignal): Promise<FlowChart> {
    const copy = structuredClone(chart);
    if (!copy.root?.id) throw new Error("The URL did not return a FlowChart with a root node");
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d")!;
    context.font = "14px sans-serif";
    const measure = (labels: ElkLabel[] = []) => labels.forEach(label => {
        label.width = Math.max(label.width || 0, context.measureText(label.text || "").width);
        label.height = Math.max(label.height || 0, 18);
        // ELK expects absent optional IDs rather than JSON null.
        if (label.id == null) delete label.id;
    });

    function prepare(node: ChartNode) {
        measure(node.labels);
        node.width = Math.max(node.width || 0, 140, ...(node.labels || []).map(l => (l.width || 0) + 32));
        node.height = Math.max(node.height || 0, 60);
        node.layoutOptions = {
            "elk.padding": "[top=36,left=24,bottom=24,right=24]",
            "elk.nodeLabels.placement": "[H_CENTER,V_TOP,INSIDE]",
            "elk.portConstraints": "FIXED_SIDE", ...node.layoutOptions
        };
        for (const port of node.ports || []) {
            port.width = Math.max(8, port.width || 0);
            port.height = Math.max(8, port.height || 0);
            port.layoutOptions = {"elk.port.side": port.role === "INPUT" ? "WEST" : "EAST", ...port.layoutOptions};
            measure(port.labels);
        }
        for (const edge of node.edges || []) {
            delete edge.sections;
            measure(edge.labels);
        }
        node.children?.forEach(prepare);
    }

    prepare(copy.root);
    copy.root.layoutOptions = {
        "elk.algorithm": "layered", "elk.direction": "RIGHT",
        "elk.hierarchyHandling": "INCLUDE_CHILDREN", ...copy.root.layoutOptions
    };
    const {default: ELK} = await import("elkjs/lib/elk-api.js");
    const {default: workerUrl} = await import("elkjs/lib/elk-worker.min.js?url");
    const elk = new ELK({workerUrl});
    let abort: (() => void) | undefined;
    try {
        const pending = elk.layout({
            id: `layout-${crypto.randomUUID()}`,
            layoutOptions: {...copy.root.layoutOptions}, children: [copy.root]
        });
        const cancelled = new Promise<never>((_, reject) => {
            abort = () => reject(new DOMException("Layout cancelled", "AbortError"));
            if (signal?.aborted) abort();
            else signal?.addEventListener("abort", abort, {once: true});
        });
        const envelope = await Promise.race([pending, cancelled]);
        copy.root = envelope.children![0] as ChartNode;
        copy.root.x = 0;
        copy.root.y = 0;
    } finally {
        if (abort) signal?.removeEventListener("abort", abort);
        elk.terminateWorker();
    }
    return copy;
}

/** Flatten coordinates, not topology: preserve every node, port, edge section and metadata entry. */
export function toSprotty(chart: FlowChart): { model: SGraph; elements: Map<string, DiagramSelection> } {
    const elements = new Map<string, DiagramSelection>();
    const children: (SModelElement & Record<string, unknown>)[] = [];
    const origins = new Map<string, { x: number; y: number }>();

    function origin(node: ChartNode, x: number, y: number) {
        origins.set(node.id, {x, y});
        node.children?.forEach(child => origin(child, x + (child.x || 0), y + (child.y || 0)));
    }

    origin(chart.root, 0, 0);
    let serial = 0;
    // Generated IDs have a per-conversion namespace; transport IDs remain unchanged.
    const prefix = `flowchart-${crypto.randomUUID()}:`;
    const labels = (items: ElkLabel[] = [], x = 0, y = 0): SModelElement[] => items.map(label => ({
        type: "label", id: prefix + serial++, text: label.text || "",
        position: {x: x + (label.x || 0), y: y + (label.y || 0) + 14},
        size: {width: label.width || 0, height: label.height || 18},
    }));

    function node(value: ChartNode, x: number, y: number) {
        elements.set(value.id, {id: value.id, kind: "node", metadata: value.metadata || {}});
        const ports = (value.ports || []).map(port => {
            elements.set(port.id, {id: port.id, kind: `port (${port.role})`, metadata: port.metadata || {}});
            return {
                type: "port", id: port.id, cssClasses: [port.role?.toLowerCase() || "input"],
                position: {x: port.x || 0, y: port.y || 0}, size: {width: port.width || 8, height: port.height || 8},
                children: labels(port.labels)
            };
        });
        children.push({
            type: "node", id: value.id, position: {x, y},
            size: {width: value.width || 140, height: value.height || 60},
            cssClasses: [value.children?.length ? "compound" : "leaf"], children: [...labels(value.labels), ...ports]
        });
        for (const child of value.children || []) node(child, x + (child.x || 0), y + (child.y || 0));
        for (const edge of value.edges || []) {
            elements.set(edge.id, {id: edge.id, kind: "link", metadata: edge.metadata || {}});
            const container = origins.get(edge.container || value.id) || {x, y};
            children.push({
                type: "edge", id: edge.id, sourceId: edge.sources[0], targetId: edge.targets[0],
                sections: (edge.sections || []).map(section => ({
                    points: [section.startPoint, ...(section.bendPoints || []), section.endPoint]
                        .map(point => ({x: container.x + point.x, y: container.y + point.y})),
                    arrow: !section.outgoingSections?.length,
                })), children: labels(edge.labels, container.x, container.y)
            });
        }
    }

    node(chart.root, 0, 0);
    return {model: {type: "graph", id: prefix + "graph", children}, elements};
}
