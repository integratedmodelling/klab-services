import {test, expect} from "@playwright/test";
import type {UiNode, UiSnapshot} from "../src/services/application-ui";

test("authentication, workflow input, CSS, reconnect and page rebuild retain server state", async ({page}) => {
  let launches = 0, posts = 0, tickets = 0, sequence = 0, lastInput: any;
  let closes = 0;
  const root: UiNode = {id: "root", type: "container", properties: {theme: "dark", locale: "es"}, events: [], children: [
    {id: "header", type: "header", properties: {title: "Mi aplicación"}, events: [], children: []},
    {id: "body", type: "container", properties: {layout: "column", gap: "12px", class: "workflow"}, events: [], children: [
      {id: "status", type: "text", properties: {label: "Preparado"}, events: [], children: []},
      {id: "name", type: "input", properties: {label: "Nombre", value: ""}, events: ["change"], children: []},
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
  await page.locator("#fixture-logout").click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.getByText("Hello Alice", {exact: true})).toHaveCount(0);
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
