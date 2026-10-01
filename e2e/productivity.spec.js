import { randomUUID } from "node:crypto";
import { test, expect } from "@playwright/test";

const password = "Productivity!2026";
const unique = () => randomUUID().slice(0, 12);

async function headers(request) {
  const response = await request.get("/api/auth/csrf");
  expect(response.ok()).toBe(true);
  const token = await response.json();
  return { [token.headerName]: token.token };
}
async function mutate(request, method, path, data, status = 200) {
  const response = await request[method](path, {
    headers: await headers(request),
    data,
  });
  expect(response.status()).toBe(status);
  return status === 204 ? null : response.json();
}
async function signIn(request, email) {
  await mutate(request, "post", "/api/auth/login", { email, password });
}
async function fixture(page, { second = false, count = 3 } = {}) {
  const tag = unique();
  const email = `productivity-${tag}@example.test`;
  const user = await mutate(
    page.request,
    "post",
    "/api/auth/register",
    { name: "Taylor Morgan", email, password },
    201,
  );
  await signIn(page.request, email);
  const workspace = await mutate(
    page.request,
    "post",
    "/api/workspaces",
    { name: `Focus ${tag}` },
    201,
  );
  const endpoint = `/api/workspaces/${workspace.id}`;
  const project = await mutate(
    page.request,
    "post",
    `${endpoint}/projects`,
    {
      name: `Release ${tag}`,
      description: "Browser productivity regression",
      color: "#7c6af2",
    },
    201,
  );
  const tasks = [];
  for (let index = 0; index < count; index++)
    tasks.push(
      await mutate(
        page.request,
        "post",
        `${endpoint}/tasks`,
        {
          title: `Sentinel ${tag} ${index + 1}`,
          description: "Real persistent task",
          projectId: project.id,
          status: "TODO",
          priority: "LOW",
          assigneeId: null,
          dueDate: null,
        },
        201,
      ),
    );
  const other = second
    ? await mutate(
        page.request,
        "post",
        "/api/workspaces",
        { name: `Other ${tag}` },
        201,
      )
    : null;
  await page.goto(`/#tasks?workspace=${workspace.id}`);
  await expect(
    page.getByRole("heading", { name: "Good work, in motion." }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: tasks[0]?.title, exact: true }),
  ).toBeVisible();
  return { tag, email, user, workspace, project, tasks, endpoint, other };
}
async function task(request, endpoint, id) {
  const response = await request.get(`${endpoint}/tasks/${id}`);
  expect(response.ok()).toBe(true);
  return response.json();
}
async function cleanup(request, data) {
  if (!data) return;
  for (const original of data.tasks) {
    const response = await request.get(`${data.endpoint}/tasks/${original.id}`);
    if (!response.ok()) continue;
    const current = await response.json();
    await mutate(
      request,
      "delete",
      `${data.endpoint}/tasks/${current.id}?version=${current.version}`,
      undefined,
      204,
    );
  }
}
async function hold(page, matches) {
  let caught;
  const seen = new Promise((resolve) => {
    caught = resolve;
  });
  let resume;
  const gate = new Promise((resolve) => {
    resume = resolve;
  });
  let finished;
  const done = new Promise((resolve) => {
    finished = resolve;
  });
  const handler = async (route) => {
    const response = await route.fetch();
    caught();
    await gate;
    await route.fulfill({ response });
    finished();
  };
  await page.route(matches, handler, { times: 1 });
  return {
    seen,
    async release() {
      resume();
      await done;
      await page.unroute(matches, handler);
      await page.evaluate(
        () =>
          new Promise((resolve) =>
            requestAnimationFrame(() => requestAnimationFrame(resolve)),
          ),
      );
    },
  };
}
test.beforeEach(async ({ page }) => {
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  page.__orbitErrors = errors;
});
test.afterEach(async ({ page }) => {
  expect(page.__orbitErrors).toEqual([]);
});

test("selected tasks update together and a stale version leaves every requested change unapplied", async ({
  page,
}) => {
  let data;
  try {
    data = await fixture(page);
    await page.getByLabel("Select loaded tasks", { exact: true }).check();
    await expect(
      page.getByRole("region", { name: "Update selected tasks" }),
    ).toContainText("3 selected");
    await page.locator("#bulk-status").selectOption("IN_PROGRESS");
    await page.locator("#bulk-priority").selectOption("HIGH");
    await page.locator("#bulk-assignee").selectOption(data.user.id);
    await page
      .getByRole("button", { name: "Apply changes", exact: true })
      .click();
    await expect(page.locator("#bulk-region")).toBeEmpty();
    for (const original of data.tasks)
      expect(
        await task(page.request, data.endpoint, original.id),
      ).toMatchObject({
        status: "IN_PROGRESS",
        priority: "HIGH",
        assigneeId: data.user.id,
      });

    await page.getByLabel("Select loaded tasks", { exact: true }).check();
    const concurrent = await task(
      page.request,
      data.endpoint,
      data.tasks[0].id,
    );
    await mutate(
      page.request,
      "patch",
      `${data.endpoint}/tasks/${concurrent.id}`,
      { ...concurrent, title: `${concurrent.title} changed elsewhere` },
    );
    const before = await Promise.all(
      data.tasks.map((item) => task(page.request, data.endpoint, item.id)),
    );
    await page.locator("#bulk-status").selectOption("DONE");
    await page.locator("#bulk-priority").selectOption("URGENT");
    await page.locator("#bulk-assignee").selectOption("__clear");
    await page
      .getByRole("button", { name: "Apply changes", exact: true })
      .click();
    await expect(page.getByRole("alert")).toContainText(
      "None of your changes were applied",
    );
    const after = await Promise.all(
      data.tasks.map((item) => task(page.request, data.endpoint, item.id)),
    );
    expect(after).toEqual(before);
    await expect(
      page.getByRole("button", { name: "Apply changes", exact: true }),
    ).toBeDisabled();
    await page
      .getByRole("button", { name: "Refresh tasks", exact: true })
      .click();
    await expect(page.locator("#bulk-region")).toBeEmpty();
    await expect(
      page.getByRole("button", {
        name: `${concurrent.title} changed elsewhere`,
        exact: true,
      }),
    ).toBeVisible();
    await page.getByLabel("Select loaded tasks", { exact: true }).check();
    await page.locator("#bulk-status").selectOption("DONE");
    await page.locator("#bulk-assignee").selectOption("__clear");
    await page
      .getByRole("button", { name: "Apply changes", exact: true })
      .click();
    await expect(page.locator("#bulk-region")).toBeEmpty();
    for (const item of data.tasks)
      expect(await task(page.request, data.endpoint, item.id)).toMatchObject({
        status: "DONE",
        assigneeId: null,
      });
    const heldBatch = await hold(
      page,
      (url) => url.pathname === `${data.endpoint}/tasks/bulk`,
    );
    const currentFirst = await task(
      page.request,
      data.endpoint,
      data.tasks[0].id,
    );
    await page
      .getByLabel(`Select ${currentFirst.title}`, { exact: true })
      .check();
    await page.locator("#bulk-status").selectOption("IN_REVIEW");
    await page
      .getByRole("button", { name: "Apply changes", exact: true })
      .click();
    await heldBatch.seen;
    await page
      .getByRole("searchbox", { name: "Search tasks", exact: true })
      .fill(data.tasks[1].title);
    await expect(page.locator(".results-note")).toContainText("1 task match");
    await page
      .getByLabel(`Select ${data.tasks[1].title}`, { exact: true })
      .check();
    await page.locator("#bulk-priority").selectOption("MEDIUM");
    await heldBatch.release();
    await expect(
      page.getByLabel(`Select ${data.tasks[1].title}`, { exact: true }),
    ).toBeChecked();
    await expect(page.locator("#bulk-priority")).toHaveValue("MEDIUM");
    await expect(
      page.getByRole("button", { name: "Apply changes", exact: true }),
    ).toBeEnabled();
    await page
      .getByRole("button", { name: "Apply changes", exact: true })
      .click();
    await expect(page.locator("#bulk-region")).toBeEmpty();
    expect(
      await task(page.request, data.endpoint, data.tasks[0].id),
    ).toMatchObject({ status: "IN_REVIEW", priority: "HIGH" });
    expect(
      await task(page.request, data.endpoint, data.tasks[1].id),
    ).toMatchObject({ status: "DONE", priority: "MEDIUM" });
  } finally {
    await cleanup(page.request, data);
  }
});

test("personal views persist while URL filters, history, keyboard search and mobile controls remain usable", async ({
  page,
  browser,
}) => {
  let data;
  let viewer;
  try {
    data = await fixture(page);
    await page.locator("#filter-priority").selectOption("LOW");
    await page.locator("#filter-status").selectOption("TODO");
    await page.locator("#filter-project").selectOption(data.project.id);
    await page
      .getByRole("searchbox", { name: "Search tasks", exact: true })
      .fill(`Sentinel ${data.tag}`);
    await expect(page.locator(".results-note")).toContainText("3 tasks match");
    await page.getByRole("button", { name: "List", exact: true }).click();
    await page.getByRole("button", { name: "Save view", exact: true }).click();
    await expect(page.getByLabel("View name", { exact: true })).toBeFocused();
    await page.getByLabel("View name", { exact: true }).fill("Release focus");
    await page
      .locator("#saved-view-form")
      .getByRole("button", { name: "Save view", exact: true })
      .click();
    await expect(page.locator("#modal")).not.toBeVisible();
    const views = await (
      await page.request.get(`${data.endpoint}/saved-views`)
    ).json();
    expect(views).toHaveLength(1);
    expect(views[0]).toMatchObject({
      label: "Release focus",
      q: `Sentinel ${data.tag}`,
      priority: "LOW",
      status: "TODO",
      projectId: data.project.id,
    });
    const link = page.url();
    expect(new URLSearchParams(link.split("?")[1]).get("layout")).toBe("list");
    await page.reload();
    await expect(page.locator("#task-search")).toHaveValue(
      `Sentinel ${data.tag}`,
    );
    await expect(page.locator("#filter-priority")).toHaveValue("LOW");
    await expect(
      page.getByRole("button", { name: "List", exact: true }),
    ).toHaveAttribute("aria-pressed", "true");
    await page.getByRole("button", { name: "All tasks", exact: true }).click();
    await expect(page.locator("#filter-status")).toHaveValue("");
    await page.goBack();
    await expect(page.locator("#filter-status")).toHaveValue("TODO");
    await page
      .getByRole("button", { name: "Release focus", exact: true })
      .click();
    await page.getByRole("button", { name: "Edit view", exact: true }).click();
    await page.getByLabel("View name", { exact: true }).fill("Launch focus");
    await page.getByRole("button", { name: "Save name", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Launch focus", exact: true }),
    ).toBeVisible();
    await page.locator("#filter-priority").selectOption("HIGH");
    await page
      .getByRole("button", { name: "Update saved filters", exact: true })
      .click();
    await expect(page.locator(".active-view-actions")).toContainText(
      "Saved filters applied",
    );

    viewer = await browser.newContext({ baseURL: new URL(page.url()).origin });
    const viewerEmail = `viewer-${unique()}@example.test`;
    await mutate(
      viewer.request,
      "post",
      "/api/auth/register",
      { name: "Private Viewer", email: viewerEmail, password },
      201,
    );
    await signIn(viewer.request, viewerEmail);
    await mutate(
      page.request,
      "post",
      `${data.endpoint}/members`,
      { email: viewerEmail, role: "VIEWER" },
      201,
    );
    expect(
      await (await viewer.request.get(`${data.endpoint}/saved-views`)).json(),
    ).toEqual([]);
    expect(
      (
        await viewer.request.get(`${data.endpoint}/saved-views/${views[0].id}`)
      ).status(),
    ).toBe(404);
    const viewerPage = await viewer.newPage();
    await viewerPage.goto(link);
    await expect(viewerPage.locator("#filter-status")).toHaveValue("TODO");
    await expect(viewerPage.locator("[data-task-select]")).toHaveCount(0);
    await viewerPage
      .getByRole("button", { name: "Save view", exact: true })
      .click();
    await viewerPage
      .getByLabel("View name", { exact: true })
      .fill("Viewer focus");
    await viewerPage
      .locator("#saved-view-form")
      .getByRole("button", { name: "Save view", exact: true })
      .click();
    await expect(
      viewerPage.getByRole("button", { name: "Viewer focus", exact: true }),
    ).toBeVisible();

    const lastColumn = await task(
      page.request,
      data.endpoint,
      data.tasks[2].id,
    );
    await mutate(
      page.request,
      "patch",
      `${data.endpoint}/tasks/${lastColumn.id}`,
      { ...lastColumn, status: "DONE" },
    );
    await page.setViewportSize({ width: 390, height: 844 });
    await page.getByRole("button", { name: "All tasks", exact: true }).click();
    await page.getByRole("button", { name: "Board", exact: true }).click();
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 844 });
      await page.evaluate(() =>
        Promise.all(
          document
            .getAnimations()
            .map((animation) => animation.finished.catch(() => {})),
        ),
      );
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth),
      ).toBe(width);
    }
    expect(
      await page
        .locator(".sidebar")
        .evaluate((element) => element.getBoundingClientRect().right),
    ).toBeLessThanOrEqual(0);
    await page.locator("#page-content").focus();
    await page.keyboard.press("/");
    await expect(page.locator("#task-search")).toBeFocused();
    await page.locator("#page-content").focus();
    await page.keyboard.press("Control+k");
    await expect(
      page.getByRole("combobox", {
        name: "Search commands and tasks",
        exact: true,
      }),
    ).toBeFocused();
    await page
      .getByRole("combobox", { name: "Search commands and tasks", exact: true })
      .fill(data.tasks[1].title);
    await expect(
      page.getByRole("option", {
        name: data.tasks[1].title + " " + data.project.name,
        exact: true,
      }),
    ).toBeVisible();
    await page.keyboard.press("Enter");
    await expect(page.getByLabel("Task name", { exact: true })).toHaveValue(
      data.tasks[1].title,
    );
    await page.keyboard.press("Escape");
    await page.locator("#page-content").focus();
    await page.keyboard.press("n");
    await expect(page.getByLabel("Task name", { exact: true })).toHaveValue("");
    await page.keyboard.press("Escape");
    await page.locator("#page-content").focus();
    await page.keyboard.press("?");
    await expect(
      page.getByRole("heading", {
        name: "A little less reaching",
        exact: true,
      }),
    ).toBeVisible();
    await page.locator("#single-key-shortcuts").uncheck();
    await page.keyboard.press("Escape");
    await page.reload();
    await expect(
      page.getByRole("heading", { name: "Good work, in motion." }),
    ).toBeVisible();
    await page.locator("#page-content").focus();
    await page.keyboard.press("n");
    await expect(page.locator("#modal")).not.toBeVisible();
    await page.keyboard.press("/");
    await expect(page.locator("#task-search")).not.toBeFocused();
    await page.keyboard.press("?");
    await expect(page.locator("#modal")).not.toBeVisible();
    await page.keyboard.press("Control+k");
    await page
      .getByRole("combobox", { name: "Search commands and tasks", exact: true })
      .fill("Keyboard shortcuts");
    await page.keyboard.press("Enter");
    await expect(page.locator("#single-key-shortcuts")).not.toBeChecked();
    await page.locator("#single-key-shortcuts").check();
    await page.keyboard.press("Escape");
    await page.locator("#page-content").focus();
    await page.keyboard.press("n");
    await expect(page.getByLabel("Task name", { exact: true })).toHaveValue("");
    await page.keyboard.press("Escape");
    await page
      .getByLabel(`Select ${data.tasks[0].title}`, { exact: true })
      .check();
    await expect(page.locator("#bulk-status")).toBeVisible();
    const widths = await page.evaluate(() => [
      document.documentElement.scrollWidth,
      document.documentElement.clientWidth,
    ]);
    expect(widths[0]).toBeLessThanOrEqual(widths[1]);
    await page
      .getByRole("button", { name: "Clear task selection", exact: true })
      .click();
    await page
      .getByRole("button", { name: "Launch focus", exact: true })
      .click();
    await page.getByRole("button", { name: "Edit view", exact: true }).click();
    await page
      .getByRole("button", { name: "Delete view", exact: true })
      .click();
    await page
      .locator("#confirm-modal")
      .getByRole("button", { name: "Delete view", exact: true })
      .click();
    await expect(
      page.getByRole("button", { name: "Launch focus", exact: true }),
    ).toHaveCount(0);
    expect(
      await (await page.request.get(`${data.endpoint}/saved-views`)).json(),
    ).toEqual([]);
  } finally {
    await viewer?.close();
    await cleanup(page.request, data);
  }
});

test("profile results cannot replace another account and pending confirmations cannot act in a different workspace", async ({
  page,
  browser,
}) => {
  let data;
  let alternate;
  try {
    data = await fixture(page, { second: true });
    await page.getByRole("link", { name: "Projects", exact: true }).click();
    await page
      .getByRole("button", { name: `Edit ${data.project.name}`, exact: true })
      .click();
    await page
      .locator("#project-form")
      .getByRole("button", { name: "Archive", exact: true })
      .click();
    await expect(page.locator("#confirm-modal")).toBeVisible();
    await page.evaluate((id) => {
      location.hash = `projects?workspace=${id}`;
    }, data.other.id);
    await expect(
      page.getByRole("combobox", { name: "Switch workspace", exact: true }),
    ).toHaveValue(data.other.id);
    await page
      .locator("#confirm-modal")
      .getByRole("button", { name: "Archive project", exact: true })
      .click();
    const originalProject = (
      await (await page.request.get(`${data.endpoint}/projects`)).json()
    ).find((project) => project.id === data.project.id);
    expect(originalProject.status).toBe("ACTIVE");
    expect(
      (
        await page.request.get(`/api/workspaces/${data.other.id}/projects`)
      ).ok(),
    ).toBe(true);
    await expect(
      page.getByRole("combobox", { name: "Switch workspace", exact: true }),
    ).toHaveValue(data.other.id);

    alternate = await browser.newContext({
      baseURL: new URL(page.url()).origin,
    });
    const otherEmail = `different-${unique()}@example.test`;
    await mutate(
      alternate.request,
      "post",
      "/api/auth/register",
      { name: "Different Person", email: otherEmail, password },
      201,
    );
    await page.getByRole("link", { name: "Your account", exact: true }).click();
    await expect(page.getByLabel("Display name", { exact: true })).toHaveValue(
      "Taylor Morgan",
    );
    const profile = await hold(page, (url) => url.pathname === "/api/account");
    await page
      .getByLabel("Display name", { exact: true })
      .fill("Held Previous Person");
    await page
      .getByRole("button", { name: "Save profile", exact: true })
      .click();
    await profile.seen;
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await expect(
      page.getByRole("heading", {
        name: "Good to have you back.",
        exact: true,
      }),
    ).toBeVisible();
    await page
      .getByRole("textbox", { name: "Email address", exact: true })
      .fill(otherEmail);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page
      .getByRole("button", { name: "Sign in to Orbit", exact: true })
      .click();
    await page.getByRole("link", { name: "Your account", exact: true }).click();
    await expect(page.getByLabel("Display name", { exact: true })).toHaveValue(
      "Different Person",
    );
    await profile.release();
    await expect(page.getByLabel("Display name", { exact: true })).toHaveValue(
      "Different Person",
    );
    await expect(page.locator(".account-email")).toContainText(otherEmail);
    await expect(page.locator(".profile strong")).toHaveText(
      "Different Person",
    );
    await mutate(page.request, "post", "/api/auth/logout", undefined, 204);
    await signIn(page.request, data.email);
  } finally {
    await alternate?.close();
    await cleanup(page.request, data);
  }
});

test("delayed command search and saved-view responses cannot cross workspaces or repaint a signed-out account", async ({
  page,
}) => {
  let data;
  try {
    data = await fixture(page, { second: true });
    await page.keyboard.press("Control+k");
    const search = await hold(
      page,
      (url) =>
        url.pathname === `${data.endpoint}/tasks` &&
        url.searchParams.get("q") === data.tasks[0].title,
    );
    await page
      .getByRole("combobox", { name: "Search commands and tasks", exact: true })
      .fill(data.tasks[0].title);
    await search.seen;
    await page.keyboard.press("Escape");
    await page
      .getByRole("combobox", { name: "Switch workspace", exact: true })
      .selectOption(data.other.id);
    await expect(
      page.getByRole("heading", { name: "Good work, in motion." }),
    ).toBeVisible();
    await search.release();
    await expect(page.locator("#command-dialog")).not.toBeVisible();
    await page.keyboard.press("Control+k");
    await page
      .getByRole("combobox", { name: "Search commands and tasks", exact: true })
      .fill(data.tasks[0].title);
    await expect(
      page.getByRole("option", { name: /Search all tasks for/ }),
    ).toContainText("0 matching tasks");
    await expect(
      page.getByRole("option", {
        name: data.tasks[0].title + " " + data.project.name,
        exact: true,
      }),
    ).toHaveCount(0);
    await page.keyboard.press("Escape");
    await page
      .getByRole("combobox", { name: "Switch workspace", exact: true })
      .selectOption(data.workspace.id);
    await expect(
      page.getByRole("button", { name: data.tasks[0].title, exact: true }),
    ).toBeVisible();

    const saved = await hold(
      page,
      (url) => url.pathname === `${data.endpoint}/saved-views`,
    );
    await page.getByRole("button", { name: "Save view", exact: true }).click();
    await page
      .getByLabel("View name", { exact: true })
      .fill("Held private view");
    await page
      .locator("#saved-view-form")
      .getByRole("button", { name: "Save view", exact: true })
      .click();
    await saved.seen;
    await page
      .locator("#saved-view-form")
      .getByRole("button", { name: "Cancel", exact: true })
      .click();
    await page
      .getByRole("combobox", { name: "Switch workspace", exact: true })
      .selectOption(data.other.id);
    await expect(
      page.getByRole("heading", { name: "Good work, in motion." }),
    ).toBeVisible();
    await saved.release();
    await expect(
      page.getByRole("button", { name: "Held private view", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("combobox", { name: "Switch workspace", exact: true }),
    ).toHaveValue(data.other.id);

    const signoutSearch = await hold(
      page,
      (url) =>
        url.pathname === `/api/workspaces/${data.other.id}/tasks` &&
        url.searchParams.has("q"),
    );
    await page.keyboard.press("Control+k");
    await page
      .getByRole("combobox", { name: "Search commands and tasks", exact: true })
      .fill("Sentinel");
    await signoutSearch.seen;
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await expect(
      page.getByRole("heading", {
        name: "Good to have you back.",
        exact: true,
      }),
    ).toBeVisible();
    await signoutSearch.release();
    await expect(page.locator("#command-dialog")).not.toBeVisible();
    await expect(page.locator("#workspace-navigation")).toHaveCount(0);
    await signIn(page.request, data.email);
  } finally {
    await cleanup(page.request, data);
  }
});
