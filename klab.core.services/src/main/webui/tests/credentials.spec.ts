import { test, expect } from "@playwright/test";

test("credential editor reads only metadata and preserves write-only secrets when saving", async ({ page }) => {
  const schemas = [{ id: "basic", label: "Username and password", mechanism: "basic", fields: [
    { name: "username", label: "Username", secret: false, required: true },
    { name: "password", label: "Password", secret: true, required: true },
  ] }];
  let sets = [{ key: "set-1", host: "https://github.com/org", description: "Repository", schema: "basic",
    parameters: { username: "git" }, configuredFields: ["username", "password"],
    privileges: { public: false, allowedUsers: ["alice"], allowedGroups: [], excludedUsers: [], excludedGroups: [] } }];
  const writes: any[] = [];
  await page.route("**/credentials/schemata", route => route.fulfill({ json: schemas }));
  await page.route(/\/credentials(?:\?.*)?$/, async route => {
    const request = route.request();
    if (request.method() === "POST") {
      const payload = request.postDataJSON(); writes.push(payload);
      sets = [{ ...sets[0], description: payload.credentials.name }];
      return route.fulfill({ json: sets[0] });
    }
    if (request.method() === "DELETE") { sets = []; return route.fulfill({ json: true }); }
    return route.fulfill({ json: sets });
  });
  await page.goto("/tests/credentials.html");
  await page.getByRole("button", { name: "Edit", exact: true }).click();
  await expect(page.getByLabel("Password *")).toHaveValue("");
  await expect(page.getByLabel("Username *")).toHaveValue("git");
  await page.getByLabel("Name", { exact: true }).fill("Renamed repository");
  await page.getByRole("button", { name: "Save credential set", exact: true }).click();
  await expect.poll(() => writes.length).toBe(1);
  expect(writes[0].credentials.id).toBe("set-1");
  expect(writes[0].credentials.parameters).toEqual({ username: "git" });
  expect(writes[0].credentials.privileges.allowedUsers).toEqual(["alice"]);
  await expect(page.getByText("Renamed repository", { exact: true })).toBeVisible();
  page.on("dialog", dialog => dialog.accept());
  await page.getByRole("button", { name: "Delete", exact: true }).click();
  await expect(page.getByText("No credential sets configured.")).toBeVisible();
});
