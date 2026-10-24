import {test, expect} from "@playwright/test";
import type {UiNode, UiSnapshot} from "../src/services/application-ui";

test("authentication, workflow input, CSS, reconnect and page rebuild retain server state", async ({page}) => {
  let launches = 0, posts = 0, tickets = 0, sequence = 0, lastInput: any;
  let closes = 0;
  const root: UiNode = {id: "root", type: "container", properties: {theme: "dark", locale: "es"}, events: [], children: [
    {id: "header", type: "header", properties: {title: "Mi aplicación"}, events: [], children: []},
    {id: "body", type: "container", properties: {layout: "column", gap: "12px", class: "workflow"}, events: [], children: [
      {id: "status", type: "text", properties: {label: "Preparado"}, events: [], children: []},
      {id: "name", type: "input", properties: {label: "Nombre", value: "", maxlength: 80, placeholder: "Full name", dense: true}, events: ["change"], children: []},
      {id: "run", type: "button", properties: {label: "Ejecutar"}, events: ["click"], children: []},
      {id: "include", type: "checkbox", properties: {label: "Include data", value: false}, events: ["change"], children: []},
      {id: "choice", type: "select", properties: {label: "Choice", value: "alpha", options: ["alpha", "beta"]}, events: ["change"], children: []},
      {id: "amount", type: "number", properties: {label: "Amount", value: 1, min: 0, max: 10}, events: ["change"], children: []},
      {id: "progress", type: "progress", properties: {value: .5}, events: [], children: []},
      {id: "details-button", type: "button", properties: {label: "Details"}, events: ["click"], children: []},
    ]},
    {id: "footer", type: "footer", properties: {}, events: [], children: [
      {id: "footer-text", type: "text", properties: {label: "Workflow testing"}, events: [], children: []},
    ]},
    {id: "details", type: "dialog", properties: {title: "Details", hidden: true}, events: ["close"], children: [
      {id: "details-text", type: "text", properties: {label: "The application stays on the server."}, events: [], children: []},
    ]},
  ]};
  const snapshot: UiSnapshot = {protocolVersion: 1, revision: 1, root};
  let socket: any;
  await page.route("**/runtime/api/v1/ui/instances", async route => {
    posts++;
    const request = route.request().postDataJSON();
    expect(request.urn).toBe("examples.workflow");
    if (!request.resume) launches++;
    await route.fulfill({json: {id: "instance-1", urn: request.urn,
      state: {snapshot, lastAcceptedSequence: sequence}, stylesheets: {workflow: ".workflow { --ui-accent: #ffcc00; }"}}});
  });
  await page.route("**/runtime/api/v1/ui/instances/instance-1/ticket", route => {
    tickets++; return route.fulfill({json: {ticket: "single-use-fixture-ticket"}});
  });
  await page.routeWebSocket("**/runtime/ui-bus", ws => {
    socket = ws;
    ws.onMessage(data => {
      const frame = JSON.parse(String(data));
      if (frame.ticket) {
        ws.send(JSON.stringify({type: "snapshot", value: snapshot}));
        ws.send(JSON.stringify({type: "ready", value: {lastAcceptedSequence: sequence}}));
      } else if (frame.type === "input") {
        lastInput = frame.value;
        expect(lastInput.revision).toBe(snapshot.revision);
        expect(lastInput.sequence).toBe(sequence + 1);
        sequence++;
        const children = root.children[1].children;
        if (lastInput.nodeId === "name") children[1].properties.value = lastInput.value;
        if (lastInput.nodeId === "run") children[0].properties.label = `Hello ${children[1].properties.value}`;
        if (["include", "choice", "amount"].includes(lastInput.nodeId)) children.find(node => node.id === lastInput.nodeId)!.properties.value = lastInput.value;
        if (lastInput.nodeId === "details-button") root.children[3].properties.hidden = false;
        if (lastInput.nodeId === "details") { closes++; root.children[3].properties.hidden = true; }
        snapshot.revision++;
        ws.send(JSON.stringify({type: "snapshot", value: snapshot}));
        ws.send(JSON.stringify({type: "ack", value: sequence}));
      }
    });
  });
  await page.goto("/tests/application.html");
  await expect(page.getByRole("dialog")).toBeVisible();
  expect(posts).toBe(0);
  await page.locator("#fixture-login").evaluate((button: HTMLButtonElement) => button.click());
  await expect(page.getByRole("heading", {name: "Mi aplicación"})).toBeVisible();
  await expect(page.getByRole("button", {name: "Ejecutar", exact: true})).toBeEnabled();
  await expect(page.getByLabel("Nombre", {exact: true})).toHaveAttribute("maxlength", "80");
  await expect(page.getByLabel("Nombre", {exact: true})).toHaveAttribute("placeholder", "Full name");
  await page.getByLabel("Nombre", {exact: true}).fill("Alice");
  await expect.poll(() => sequence).toBe(1);
  await page.getByRole("button", {name: "Ejecutar", exact: true}).click();
  await expect(page.getByText("Hello Alice", {exact: true})).toBeVisible();
  await page.getByRole("checkbox", {name: "Include data"}).click();
  await expect.poll(() => lastInput.nodeId).toBe("include");
  expect(lastInput.value).toBe(true);
  await page.getByRole("combobox", {name: "Choice"}).click();
  await page.getByRole("option", {name: "beta", exact: true}).click();
  await expect.poll(() => lastInput.nodeId).toBe("choice");
  expect(lastInput.value).toBe("beta");
  await page.getByRole("spinbutton", {name: "Amount"}).fill("3");
  await expect.poll(() => lastInput.nodeId).toBe("amount");
  expect(lastInput.value).toBe(3);
  await page.getByRole("button", {name: "Details", exact: true}).click();
  await expect(page.getByRole("dialog")).toContainText("The application stays on the server.");
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(closes).toBe(1);
  await page.getByRole("button", {name: "Details", exact: true}).click();
  await page.getByRole("dialog").getByRole("button", {name: "Close", exact: true}).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  expect(closes).toBe(2);
  await expect(page.locator(".application-ui").first()).toHaveAttribute("data-theme", "dark");
  expect(await page.locator(".workflow").evaluate(element => getComputedStyle(element).getPropertyValue("--ui-accent").trim())).toBe("#ffcc00");
  await page.getByLabel("Nombre", {exact: true}).fill("Uncommitted draft");
  socket.close({code: 1006, reason: "fixture network loss before debounce"});
  await expect.poll(() => tickets).toBeGreaterThan(1);
  await expect(page.getByRole("button", {name: "Ejecutar", exact: true})).toBeEnabled();
  await expect(page.getByLabel("Nombre", {exact: true})).toHaveValue("Alice");
  expect(launches).toBe(1);
  await page.reload();
  await page.locator("#fixture-login").evaluate((button: HTMLButtonElement) => button.click());
  await expect(page.getByLabel("Nombre", {exact: true})).toHaveValue("Alice");
  await expect(page.getByText("Hello Alice", {exact: true})).toBeVisible();
  expect(launches).toBe(1);
  await page.setViewportSize({width: 390, height: 844});
  expect(await page.locator(".application-scaffold").evaluate(element => element.getBoundingClientRect().width)).toBeLessThanOrEqual(390);
  await page.screenshot({path: "../../../../target/ui-scaffolding-validation/application-dark-mobile.png", fullPage: true});
  root.properties.theme = "light"; snapshot.revision++;
  socket.send(JSON.stringify({type: "snapshot", value: snapshot}));
  await page.setViewportSize({width: 1280, height: 900});
  await expect(page.locator(".application-ui").first()).toHaveAttribute("data-theme", "light");
  await page.screenshot({path: "../../../../target/ui-scaffolding-validation/application-light-desktop.png", fullPage: true});
  root.children[1].children.push({id: "dynamic", type: "panel", properties: {tag: "main/one"}, events: [], children: [
    {id: "dynamic-field", type: "input", properties: {tag: "main/one/field", label: "Dynamic field", value: "Retained", readonly: true}, events: ["change"], children: []},
  ]});
  snapshot.revision++;
  socket.send(JSON.stringify({type: "snapshot", value: snapshot}));
  await expect(page.locator('[data-ui-tag="main/one/field"] input')).toHaveValue("Retained");
  await expect(page.getByLabel("Dynamic field")).toHaveAttribute("readonly", "");
  root.children[1].children.pop(); snapshot.revision++;
  socket.send(JSON.stringify({type: "snapshot", value: snapshot}));
  await expect(page.getByLabel("Dynamic field")).toHaveCount(0);
  await page.locator("#fixture-logout").click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.getByText("Hello Alice", {exact: true})).toHaveCount(0);
});

test("installed toolkit module receives canonical snapshots and emits through the standard bus", async ({page}) => {
  let input: any;
  const snapshot: UiSnapshot = {protocolVersion: 1, revision: 1, root: {id: "root", type: "container", properties: {label: "Installed toolkit"}, events: [], children: []}};
  await page.route("**/runtime/api/v1/ui/instances", route => route.fulfill({json: {id: "custom", urn: "examples.workflow", state: {snapshot, lastAcceptedSequence: 0}, stylesheets: {},
    toolkit: {id: "custom", protocolVersion: 1, types: ["container"], layouts: ["column"]}, rendererUrl: "public/ui/components/custom/1.0.0/renderer.js"}}));
  await page.route("**/runtime/api/v1/ui/instances/custom/ticket", route => route.fulfill({json: {ticket: "custom-ticket"}}));
  await page.route("**/runtime/public/ui/components/custom/1.0.0/renderer.js", route => route.fulfill({contentType: "text/javascript", body: `
    import {h} from '/src/plugin-api.ts';
    export default {props: ['snapshot', 'disabled', 'context'], emits: ['input'], setup(props, {emit}) {
      return () => h('button', {disabled: props.disabled, onClick: () => emit('input', {nodeId:'root', name:'click', value:null})}, props.snapshot.root.properties.label);
    }};` }));
  await page.routeWebSocket("**/runtime/ui-bus", ws => ws.onMessage(data => {
    const frame = JSON.parse(String(data));
    if (frame.ticket) { ws.send(JSON.stringify({type: "snapshot", value: snapshot})); ws.send(JSON.stringify({type: "ready", value: {lastAcceptedSequence: 0}})); }
    else { input = frame.value; }
  }));
  await page.goto("/tests/application.html");
  await page.locator("#fixture-login").evaluate((button: HTMLButtonElement) => button.click());
  await page.getByRole("button", {name: "Installed toolkit", exact: true}).click();
  await expect.poll(() => input?.nodeId).toBe("root");
  expect(input.sequence).toBe(1);
  expect(input.revision).toBe(1);
  expect(input.name).toBe("click");
});

test("real shell preserves the service base on a direct application route and gates launch", async ({page}) => {
  let launches = 0;
  await page.route("**/runtime/apps/examples.workflow", route => route.fulfill({contentType: "text/html", body:
    '<!doctype html><html><head><base href="/runtime/"></head><body><div id="app"></div><script type="module" src="/src/main.ts"></script></body></html>'}));
  await page.route("**/runtime/public/ui/config", route => route.fulfill({json: {
    title: "Runtime", subtitle: "Applications", serviceType: "RUNTIME", logoUrl: "klab-logo.svg",
    authentication: {enabled: false}, links: [], pages: [], panels: [], modules: {},
  }}));
  await page.route("**/runtime/public/status", route => route.fulfill({json: {}}));
  await page.route("**/runtime/public/capabilities", route => route.fulfill({json: {}}));
  await page.route("**/runtime/api/v1/ui/instances", route => { launches++; return route.fulfill({status: 401}); });
  await page.goto("/runtime/apps/examples.workflow");
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.getByRole("heading", {name: "Sign in to open examples.workflow"})).toBeVisible();
  expect(await page.evaluate(() => new URL(document.baseURI).pathname)).toBe("/runtime/");
  expect(launches).toBe(0);
});


test("extended widgets, form commands, safe content and HTTP file handles", async ({page}) => {
  const errors: string[] = [];
  page.on("pageerror", error => errors.push(String(error)));
  const node = (id: string, type: string, properties: Record<string, any>, events: string[] = ["change"]): UiNode =>
    ({id, type, properties: {tag: `main/${id}`, ...properties}, events, children: []});
  const children = [
    node("name", "input", {label: "Form name", value: "Ada", field: "name", debounce: 0}),
    node("save", "submit", {label: "Save form"}, ["submit"]),
    node("reset", "reset", {label: "Reset form"}, ["reset"]),
    node("switch", "switch", {label: "Enabled", value: false}),
    node("radio", "radio", {label: "Region", options: ["eu", "af"], value: "eu"}),
    node("multi", "multiselect", {label: "Regions", options: ["eu", "af"], value: ["eu"]}),
    node("textarea", "textarea", {label: "Notes", value: "", debounce: 0}),
    node("date", "date", {label: "Day", value: "2024-02-29", debounce: 0}),
    node("time", "time", {label: "Hour", value: "12:30", debounce: 0}),
    node("color", "color", {label: "Tint", value: "#112233"}),
    node("slider", "slider", {label: "Level", value: 5, min: 0, max: 10}),
    node("range", "range", {label: "Interval", value: {min: 2, max: 8}, min: 0, max: 10}),
    node("list", "list", {label: "Accounts", items: [{id: "a", label: "Alice"}, {id: "b", label: "Bob"}], value: "a"}, ["select", "activate"]),
    node("table", "table", {label: "Measurements", items: [{id: "a", label: "Alpha"}, {id: "b", label: "Beta"}], columns: ["label"], pagesize: 1}, ["select", "activate", "sort", "page"]),
    node("tree", "tree", {label: "Hierarchy", items: [{id: "parent", label: "Parent", children: [{id: "child", label: "Child"}]}], expanded: ["parent"]}, ["select", "activate", "expand"]),
    node("tabs", "tabs", {label: "Sections", options: ["overview", "details"], value: "overview"}),
    node("steps", "stepper", {label: "Steps", options: ["prepare", "finish"], value: "prepare"}),
    node("pages", "pagination", {label: "Pages", min: 1, max: 3, value: 1}),
    node("crumbs", "breadcrumbs", {label: "Path", options: ["home", "accounts"], value: "home"}),
    node("markdown", "markdown", {label: '**Safe** <img src=x onerror="alert(1)"> [bad](javascript:alert(1)) [space]( javascript:alert(1))'}, []),
    node("image", "image", {src: "https://example.org/image.png", alt: "Example"}, []),
    node("icon", "icon", {label: "Ready", icon: "check"}, []),
    node("separator", "separator", {}, []), node("badge", "badge", {label: "New"}, []),
    node("spinner", "spinner", {label: "Working"}, []),
    node("upload", "upload", {label: "Upload CSV", accept: ".csv", maxbytes: 1024}),
    node("download", "download", {label: "Download CSV"}, ["click"]),
    node("audio", "audio", {label: "Audio", src: "https://example.org/audio.mp3"}, ["play", "pause", "ended"]),
    node("video", "video", {label: "Video", src: "https://example.org/video.mp4"}, ["play", "pause", "ended"]),
    node("iconbutton", "iconbutton", {label: "Refresh data", icon: "refresh"}, ["click"]),
    node("link", "link", {label: "Help", href: "javascript:alert(1)"}, ["click"]),
  ];
  const snapshot: UiSnapshot = {protocolVersion: 1, revision: 1, root: node("root", "container", {layout: "column"}, [])};
  snapshot.root.children = children;
  let sequence = 0;
  const inputs: any[] = [];
  const handle = {id: "opaque-handle", name: "data.csv", mediaType: "text/csv", size: 4};
  let uploads = 0, downloads = 0;
  await page.route("https://example.org/**", route => route.abort());
  await page.route("**/api/v1/ui/instances", route => route.fulfill({json: {id: "catalog", urn: "examples.workflow", state: {snapshot, lastAcceptedSequence: sequence}, stylesheets: {}}}));
  await page.route("**/catalog/ticket", route => route.fulfill({json: {ticket: "catalog-ticket"}}));
  await page.route("**/catalog/files/upload?**", async route => {
    uploads++; expect(route.request().postDataBuffer()?.toString()).toBe("a,b\n");
    await route.fulfill({json: handle});
  });
  await page.route("**/catalog/files/opaque-handle", async route => {
    downloads++; await route.fulfill({json: {name: "data.csv", mediaType: "text/csv", content: Buffer.from("a,b\n").toString("base64")}});
  });
  await page.routeWebSocket("**/runtime/ui-bus", ws => ws.onMessage(data => {
    const frame = JSON.parse(String(data));
    if (frame.ticket) {
      ws.send(JSON.stringify({type: "snapshot", value: snapshot}));
      ws.send(JSON.stringify({type: "ready", value: {lastAcceptedSequence: sequence}}));
    } else if (frame.type === "input") {
      const event = frame.value; inputs.push(event); sequence++;
      const target = children.find(child => child.id === event.nodeId)!;
      if (["change", "select"].includes(event.name)) target.properties.value = event.value;
      if (target.id === "upload") children.find(child => child.id === "download")!.properties.value = event.value;
      if (target.id === "reset") children[0].properties.value = "Ada";
      if (event.name === "sort") Object.assign(target.properties, {sort: event.value.column, descending: event.value.descending});
      if (event.name === "page") target.properties.page = event.value;
      snapshot.revision++;
      ws.send(JSON.stringify({type: "snapshot", value: snapshot})); ws.send(JSON.stringify({type: "ack", value: sequence}));
    }
  }));
  await page.goto("/tests/application.html");
  await page.locator("#fixture-login").evaluate((button: HTMLButtonElement) => button.click());
  await expect(page.getByRole("button", {name: "Save form"})).toBeEnabled();
  await page.getByRole("switch", {name: "Enabled"}).click();
  await expect.poll(() => inputs.at(-1)?.value).toBe(true);
  await page.getByRole("radio", {name: "af", exact: true}).click();
  await expect.poll(() => inputs.at(-1)?.value).toBe("af");
  await page.getByLabel("Notes", {exact: true}).fill("A note");
  await expect.poll(() => inputs.at(-1)?.value).toBe("A note");
  await page.getByLabel("Form name", {exact: true}).fill("Grace");
  await expect.poll(() => children[0].properties.value).toBe("Grace");
  await page.getByRole("button", {name: "Save form"}).click();
  await expect.poll(() => inputs.at(-1)?.name).toBe("submit");
  expect(inputs.at(-1).value).toBeNull();
  await page.getByRole("button", {name: "Reset form"}).click();
  await expect(page.getByLabel("Form name", {exact: true})).toHaveValue("Ada");
  await page.getByText("Bob", {exact: true}).click();
  await expect.poll(() => inputs.at(-1)?.value).toBe("b");
  await page.getByRole("tab", {name: "details", exact: true}).click();
  await expect.poll(() => inputs.at(-1)?.value).toBe("details");
  await page.getByText("Child", {exact: true}).click();
  await expect.poll(() => inputs.at(-1)?.value).toBe("child");
  await page.getByRole("columnheader").filter({hasText: "label"}).click();
  await expect.poll(() => inputs.at(-1)?.name).toBe("sort");
  expect(inputs.at(-1).value.column).toBe("label");
  await expect(page.locator('[data-ui-tag="main/markdown"] strong')).toHaveText("Safe");
  await expect(page.locator('[data-ui-tag="main/markdown"] img')).toHaveCount(0);
  await expect(page.locator('[data-ui-tag="main/markdown"] a')).toHaveCount(0);
  await expect(page.getByRole("button", {name: "Help", exact: true})).not.toHaveAttribute("href");
  await page.getByLabel("Upload CSV").setInputFiles({name: "data.csv", mimeType: "text/csv", buffer: Buffer.from("a,b\n")});
  await expect.poll(() => uploads).toBe(1);
  await expect.poll(() => inputs.at(-1)?.value?.id).toBe("opaque-handle");
  const downloaded = page.waitForEvent("download");
  await page.getByRole("button", {name: "Download CSV"}).click();
  expect((await downloaded).suggestedFilename()).toBe("data.csv");
  expect(downloads).toBe(1);
  expect(errors).toEqual([]);
});


test("packaged IDE renderer uses the shared Quasar catalog and bounded file bridge", async ({page}) => {
  const {readFileSync} = await import("node:fs");
  const {fileURLToPath} = await import("node:url");
  const bundle = (name: string) => readFileSync(fileURLToPath(new URL(`../../../../target/generated-resources/webui/META-INF/klab/ui-embedded/${name}`, import.meta.url)), "utf8");
  const errors: string[] = []; page.on("pageerror", error => errors.push(String(error)));
  const child = (id: string, type: string, properties: Record<string, any>, events = ["change"]): UiNode => ({id, type, properties, events, children: []});
  const root: UiNode = child("root", "container", {theme: "dark"}, []);
  root.children = [child("toggle", "switch", {label: "Enabled", value: false}),
    child("slider", "slider", {label: "Level", value: 5, min: 0, max: 10}),
    child("range", "range", {label: "Interval", value: {min: 1, max: 8}, min: 0, max: 10}),
    child("tabs", "tabs", {label: "Sections", options: ["one", "two"], value: "one"}),
    child("form", "submit", {label: "Save form"}, ["submit"]),
    child("upload", "upload", {label: "Upload CSV", accept: ".csv", maxbytes: 1024}),
    child("download", "download", {label: "Download CSV"}, ["click"]),
    child("markdown", "markdown", {label: "**Shared renderer**"}, []),
    child("draft", "input", {label: "Retained value", value: "Server value", debounce: 2000})];
  const snapshot: UiSnapshot = {protocolVersion: 1, revision: 1, root};
  const handle = {id: "opaque-file", name: "data.csv", mediaType: "text/csv", size: 4};
  const inputs: any[] = [], transfers: any[] = [];
  await page.exposeFunction("fixtureInput", async (raw: string) => {
    const event = JSON.parse(raw).value; inputs.push(event);
    const target = root.children.find(n => n.id === event.nodeId)!;
    if (event.name === "change") target.properties.value = event.value;
    if (target.id === "upload") root.children.find(n => n.id === "download")!.properties.value = event.value;
    snapshot.revision++;
    await page.evaluate(({snapshot, sequence}) => {
      window.receive({type: "snapshot", value: snapshot}); window.receive({type: "ack", value: sequence});
    }, {snapshot, sequence: event.sequence});
  });
  await page.exposeFunction("fixtureFile", async (id: string, raw: string) => {
    const request = JSON.parse(raw); transfers.push(request);
    if (request.operation === "upload") {
      expect(request.nodeId).toBe("upload"); expect(Buffer.from(request.content, "base64").toString()).toBe("a,b\n");
    }
    await page.evaluate(({id, value}) => window.applicationUiFileReply(id, JSON.stringify({value})), {id,
      value: request.operation === "upload" ? handle : {name: "data.csv", mediaType: "text/csv", content: Buffer.from("a,b\n").toString("base64")}});
  });
  await page.addInitScript(() => {
    window.ide = {input: raw => { void (window as any).fixtureInput(raw); },
      file: (id, raw) => { void (window as any).fixtureFile(id, raw); }};
  });
  await page.route("**/embedded-fixture", route => route.fulfill({contentType: "text/html", body:
    `<html><head><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; font-src data:; img-src data: https:; media-src https: blob:; connect-src 'none'"><style>${bundle("renderer.css")}</style></head><body><div id="status"></div><main id="app"></main><script>${bundle("renderer.js").replaceAll("</script", "<\\/script")}</script></body></html>`}));
  await page.goto("/embedded-fixture");
  await page.evaluate(snapshot => {
    window.receive({type: "instance", value: {id: "same-instance", state: {snapshot}, stylesheets: {}}});
    window.receive({type: "ready", value: {lastAcceptedSequence: 0}});
  }, snapshot);
  await expect(page.locator("strong")).toHaveText("Shared renderer");
  await page.getByRole("switch", {name: "Enabled"}).click();
  await expect.poll(() => inputs.at(-1)?.value).toBe(true);
  await page.getByRole("slider", {name: "Level", exact: true}).press("ArrowRight");
  await expect.poll(() => inputs.at(-1)?.value).toBe(6);
  await page.getByRole("tab", {name: "two", exact: true}).click();
  await expect.poll(() => inputs.at(-1)?.value).toBe("two");
  await page.getByRole("button", {name: "Save form"}).click();
  await expect.poll(() => inputs.at(-1)?.name).toBe("submit");
  await page.getByLabel("Upload CSV").setInputFiles({name: "data.csv", mimeType: "text/csv", buffer: Buffer.from("a,b\n")});
  await expect.poll(() => inputs.at(-1)?.value?.id).toBe("opaque-file");
  const saved = page.waitForEvent("download"); await page.getByRole("button", {name: "Download CSV"}).click();
  expect((await saved).suggestedFilename()).toBe("data.csv");
  expect(transfers.map(t => t.operation)).toEqual(["upload", "download"]);
  await page.getByLabel("Retained value", {exact: true}).fill("Uncommitted draft");
  await page.evaluate(() => window.receive({type: "disconnected", value: {}}));
  await expect(page.getByRole("switch", {name: "Enabled"})).toBeDisabled();
  await expect(page.getByLabel("Retained value", {exact: true})).toHaveValue("Server value");
  expect(errors).toEqual([]);
});
