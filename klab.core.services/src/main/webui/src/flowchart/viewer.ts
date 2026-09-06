import "reflect-metadata";
import { Container, ContainerModule, decorate, injectable } from "inversify";
import { configureModelElement, configureViewerOptions, loadDefaultModules, LocalModelSource,
  TYPES, SGraphImpl, SGraphView, SNodeImpl, SPortImpl, SLabelImpl, SLabelView, SEdgeImpl,
  RectangularNodeView, MouseListener, moveFeature, selectFeature, edgeLayoutFeature, svg,
  type IView, type RenderingContext, type SModelElementImpl, type IActionDispatcher } from "sprotty";
import { FitToScreenAction, type Point } from "sprotty-protocol";

class ElkEdge extends SEdgeImpl { sections: { points: Point[]; arrow: boolean }[] = []; }
/** Render ELK's exact routes, including multi-section edges, without Sprotty re-routing them. */
class ElkEdgeView implements IView {
  render(edge: Readonly<ElkEdge>, context: RenderingContext) {
    return svg("g", { "class-sprotty-edge": true, "class-selected": edge.selected },
      ...edge.sections.flatMap(section => {
        const p = section.points;
        if (p.length < 2) return [];
        const paths = [svg("path", { d: p.map((point, i) => `${i ? "L" : "M"}${point.x},${point.y}`).join(" "),
          fill: "none" })];
        if (section.arrow) {
          const end = p[p.length - 1], previous = p[p.length - 2];
          const angle = Math.atan2(end.y - previous.y, end.x - previous.x);
          paths.push(svg("path", { "class-arrow": true, d: `M${end.x},${end.y} L${end.x - 9 * Math.cos(angle - .45)},${end.y - 9 * Math.sin(angle - .45)} L${end.x - 9 * Math.cos(angle + .45)},${end.y - 9 * Math.sin(angle + .45)} Z` }));
        }
        return paths;
      }), ...context.renderChildren(edge));
  }
}
decorate(injectable(), ElkEdgeView);

class Inspector extends MouseListener {
  constructor(private readonly inspect: (id: string) => void) { super(); }
  mouseDown(target: SModelElementImpl) {
    let element = target;
    while (element.type === "label" && "parent" in element) element = element.parent as SModelElementImpl;
    this.inspect(element.id);
    return [];
  }
}

export function createViewer(baseDiv: string, inspect: (id: string) => void) {
  const container = new Container();
  loadDefaultModules(container);
  container.load(new ContainerModule((bind, unbind, isBound, rebind) => {
    const context = { bind, unbind, isBound, rebind };
    bind(TYPES.ModelSource).to(LocalModelSource).inSingletonScope();
    bind(TYPES.MouseListener).toConstantValue(new Inspector(inspect));
    configureModelElement(context, "graph", SGraphImpl, SGraphView);
    configureModelElement(context, "node", SNodeImpl, RectangularNodeView, { disable: [moveFeature] });
    configureModelElement(context, "port", SPortImpl, RectangularNodeView, { enable: [selectFeature] });
    configureModelElement(context, "label", SLabelImpl, SLabelView, { disable: [edgeLayoutFeature] });
    configureModelElement(context, "edge", ElkEdge, ElkEdgeView);
    configureViewerOptions(context, { baseDiv, hiddenDiv: `${baseDiv}-hidden`, popupDiv: `${baseDiv}-popup`,
      needsClientLayout: false, needsServerLayout: false, zoomLimits: { min: .05, max: 4 } });
  }));
  const source = container.get<LocalModelSource>(TYPES.ModelSource);
  const dispatcher = container.get<IActionDispatcher>(TYPES.IActionDispatcher);
  return { source, fit: () => dispatcher.dispatch(FitToScreenAction.create([], { padding: 24, maxZoom: 1 })),
    dispose: () => container.unbindAll() };
}
