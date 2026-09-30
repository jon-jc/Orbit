import { randomUUID } from "node:crypto";
import { test, expect } from "@playwright/test";

const demoWorkspace = "Northstar Studio";
const unique = () => randomUUID().slice(0, 12);
const dialog = (page) => page.locator("#modal");

async function navigateMobile(page, name) {
  await page
    .getByRole("button", { name: "Open navigation", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Open navigation", exact: true }),
  ).toHaveAttribute("aria-expanded", "true");
  await page
    .locator("#workspace-navigation")
    .getByRole("link", { name, exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Open navigation", exact: true }),
  ).toHaveAttribute("aria-expanded", "false");
  await expect
    .poll(() =>
      page.locator("#workspace-navigation").evaluate((el) => el.inert),
    )
    .toBe(true);
}

async function expectNoPageOverflow(page) {
  const widths = await page.evaluate(() => ({
    page: document.documentElement.scrollWidth,
    viewport: document.documentElement.clientWidth,
  }));
  expect(widths.page).toBeLessThanOrEqual(widths.viewport);
}

async function csrfHeaders(request) {
  const response = await request.get("/api/auth/csrf");
  expect(response.ok()).toBe(true);
  const csrf = await response.json();
  return { [csrf.headerName]: csrf.token };
}

async function workspaceByName(request, name) {
  const response = await request.get("/api/workspaces");
  expect(response.ok()).toBe(true);
  const workspace = (await response.json()).find((item) => item.name === name);
  expect(workspace, `Workspace ${name} must exist`).toBeTruthy();
  return workspace;
}

async function cleanupTask(request, workspaceId, title) {
  const response = await request.get(`/api/workspaces/${workspaceId}/tasks`, {
    params: { q: title, size: 100 },
  });
  if (!response.ok()) return;
  const headers = await csrfHeaders(request);
  for (const task of (await response.json()).items.filter(
    (item) => item.title === title,
  )) {
    const removed = await request.delete(
      `/api/workspaces/${workspaceId}/tasks/${task.id}?version=${task.version}`,
      { headers },
    );
    expect(removed.status()).toBe(204);
  }
}

test("demo work persists through task creation, assignment, edits, comments, reload, and deletion", async ({
  page,
}) => {
  const title = `Browser smoke ${unique()}`;
  const description = "A real browser verifies the complete product workflow.";
  const comment = "<b>Literal markup stays text</b> — ready for review.";
  const dueDate = new Date(Date.now() + 7 * 86_400_000)
    .toISOString()
    .slice(0, 10);
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  let workspace;

  try {
    await page.goto("/");
    await page
      .getByRole("button", { name: "Explore the demo workspace", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "Hello, Alex." }),
    ).toBeVisible();
    workspace = await workspaceByName(page.request, demoWorkspace);

    await page.getByRole("button", { name: "New task", exact: true }).click();
    await dialog(page).getByLabel("Task name", { exact: true }).fill(title);
    await dialog(page)
      .getByLabel("Description", { exact: true })
      .fill(description);
    await dialog(page)
      .getByLabel("Project", { exact: true })
      .selectOption({ label: "Website relaunch" });
    await dialog(page)
      .getByLabel("Assignee", { exact: true })
      .selectOption({ label: "Jordan Lee" });
    await dialog(page)
      .getByLabel("Priority", { exact: true })
      .selectOption("HIGH");
    await dialog(page).getByLabel("Due date", { exact: true }).fill(dueDate);
    await dialog(page)
      .getByRole("button", { name: "Create task", exact: true })
      .click();
    await expect(dialog(page)).not.toBeVisible();

    await page.getByRole("link", { name: "My workspace", exact: true }).click();
    await page
      .getByRole("searchbox", { name: "Search tasks", exact: true })
      .fill(title);
    await page.getByRole("button", { name: title, exact: true }).click();
    await expect(
      dialog(page).getByLabel("Due date", { exact: true }),
    ).toHaveValue(dueDate);
    await dialog(page)
      .getByLabel("Status", { exact: true })
      .selectOption("IN_PROGRESS");
    await dialog(page)
      .getByRole("button", { name: "Save changes", exact: true })
      .click();
    await expect(dialog(page)).not.toBeVisible();

    await page.getByRole("button", { name: title, exact: true }).click();
    await dialog(page)
      .getByRole("textbox", { name: "Add a comment", exact: true })
      .fill(comment);
    await dialog(page)
      .getByRole("button", { name: "Comment", exact: true })
      .click();
    await expect(dialog(page).locator(".comment-body")).toHaveText(comment);
    await expect(dialog(page).locator(".comment-body b")).toHaveCount(0);
    expect(new URLSearchParams(page.url().split("?")[1]).has("task")).toBe(
      true,
    );
    await page.reload();
    await expect(dialog(page)).toBeVisible();
    await expect(
      dialog(page).getByLabel("Task name", { exact: true }),
    ).toHaveValue(title);
    await expect(
      dialog(page).getByLabel("Description", { exact: true }),
    ).toHaveValue(description);
    await expect(
      dialog(page).getByLabel("Status", { exact: true }),
    ).toHaveValue("IN_PROGRESS");
    await expect(
      dialog(page).getByLabel("Priority", { exact: true }),
    ).toHaveValue("HIGH");
    await expect(
      dialog(page)
        .getByLabel("Assignee", { exact: true })
        .locator("option:checked"),
    ).toHaveText("Jordan Lee");
    await expect(
      dialog(page).getByLabel("Due date", { exact: true }),
    ).toHaveValue(dueDate);
    await expect(dialog(page).locator(".comment-body")).toHaveText(comment);

    await dialog(page)
      .getByRole("button", { name: "Delete", exact: true })
      .click();
    await page
      .locator("#confirm-modal")
      .getByRole("button", { name: "Delete task", exact: true })
      .click();
    await expect(dialog(page)).not.toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Good work, in motion.", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: title, exact: true }),
    ).toHaveCount(0);
    const remaining = await page.request.get(
      `/api/workspaces/${workspace.id}/tasks`,
      { params: { q: title } },
    );
    expect((await remaining.json()).total).toBe(0);
    expect(errors).toEqual([]);
  } finally {
    // A failed assertion should not leave test tasks in the shared demo workspace.
    if (workspace) await cleanupTask(page.request, workspace.id, title);
  }
});

async function registerAndCreateWorkspace(
  page,
  { email, password, name, workspace },
) {
  await page.goto("/");
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await page
    .getByRole("textbox", { name: "Your name", exact: true })
    .fill(name);
  await page
    .getByRole("textbox", { name: "Email address", exact: true })
    .fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page
    .getByRole("button", { name: "Create your account", exact: true })
    .click();
  await dialog(page)
    .getByLabel("Workspace name", { exact: true })
    .fill(workspace);
  await dialog(page)
    .getByRole("button", { name: "Create workspace", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "New task", exact: true }),
  ).toBeVisible();
}

async function signInThroughUi(page, email, password) {
  await page
    .getByRole("textbox", { name: "Email address", exact: true })
    .fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page
    .getByRole("button", { name: "Sign in to Orbit", exact: true })
    .click();
  await expect(page.locator(".app-shell")).toBeVisible();
}

async function apiLogin(request, email, password) {
  const response = await request.post("/api/auth/login", {
    headers: await csrfHeaders(request),
    data: { email, password },
  });
  expect(response.status()).toBe(200);
}

async function holdApiResponse(page, pattern, method = "GET", override) {
  let arrived, release, delivered;
  const arrivedPromise = new Promise((resolve) => {
    arrived = resolve;
  });
  const releasedPromise = new Promise((resolve) => {
    release = resolve;
  });
  const deliveredPromise = new Promise((resolve) => {
    delivered = resolve;
  });
  const handler = async (route) => {
    if (route.request().method() !== method) return route.continue();
    const response = await route.fetch();
    arrived();
    await releasedPromise;
    await route.fulfill(override || { response });
    delivered();
  };
  await page.route(pattern, handler);
  return {
    arrived: arrivedPromise,
    delivered: deliveredPromise,
    release,
    close: async () => {
      release();
      await page.unroute(pattern, handler);
    },
  };
}

async function flushBrowserFrames(page) {
  await page.evaluate(
    () =>
      new Promise((resolve) => {
        requestAnimationFrame(() => requestAnimationFrame(resolve));
      }),
  );
}

test("account and workspace settings persist, individual sessions end, and password changes revoke every session", async ({
  page,
  browser,
}) => {
  const suffix = unique();
  const email = `security-${suffix}@example.test`;
  const password = "OrbitInitial!2026";
  const nextPassword = "OrbitUpdated!2026";
  const workspace = `Security ${suffix}`;
  const renamed = `Renamed ${suffix}`;
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await registerAndCreateWorkspace(page, {
    email,
    password,
    name: "Casey Builder",
    workspace,
  });
  await page.getByRole("link", { name: "Your account", exact: true }).click();
  await page.getByLabel("Display name", { exact: true }).fill("Casey Rivera");
  await page.getByRole("button", { name: "Save profile", exact: true }).click();
  await expect(page.getByLabel("Display name", { exact: true })).toHaveValue(
    "Casey Rivera",
  );
  await page
    .getByRole("link", { name: "Workspace settings", exact: true })
    .click();
  await page
    .locator("#settings-form")
    .getByLabel("Workspace name", { exact: true })
    .fill(renamed);
  await page
    .getByRole("button", { name: "Save workspace", exact: true })
    .click();
  await expect(
    page
      .getByRole("combobox", { name: "Switch workspace", exact: true })
      .locator("option:checked"),
  ).toHaveText(renamed);
  const secondName = `Second ${suffix}`;
  const creation = await holdApiResponse(page, "**/api/workspaces", "POST");
  try {
    await page
      .getByRole("button", { name: "Create workspace", exact: true })
      .click();
    await dialog(page)
      .getByLabel("Workspace name", { exact: true })
      .fill(secondName);
    await dialog(page)
      .getByRole("button", { name: "Create workspace", exact: true })
      .click();
    await creation.arrived;
    await expect(dialog(page)).toBeVisible();
    creation.release();
    await expect(dialog(page)).not.toBeVisible();
    await expect(
      page
        .getByRole("combobox", { name: "Switch workspace", exact: true })
        .locator("option:checked"),
    ).toHaveText(secondName);
    await expect(page.locator("#settings-name")).toHaveValue(secondName);
  } finally {
    await creation.close();
  }
  const firstWorkspace = await workspaceByName(page.request, renamed);
  const secondWorkspace = await workspaceByName(page.request, secondName);
  const oldSettings = await holdApiResponse(
    page,
    `**/api/workspaces/${secondWorkspace.id}/settings`,
  );
  const newMetadata = await holdApiResponse(
    page,
    `**/api/workspaces/${firstWorkspace.id}/projects`,
  );
  try {
    await page
      .getByRole("link", { name: "Workspace settings", exact: true })
      .click();
    await oldSettings.arrived;
    await page
      .getByRole("combobox", { name: "Switch workspace", exact: true })
      .selectOption({ label: renamed });
    await newMetadata.arrived;
    oldSettings.release();
    await oldSettings.delivered;
    await flushBrowserFrames(page);
    await expect(page.locator("#settings-form")).toHaveCount(0);
    newMetadata.release();
    await expect(page.locator("#settings-name")).toHaveValue(renamed);
  } finally {
    await oldSettings.close();
    await newMetadata.close();
  }
  const failedMetadata = await holdApiResponse(
    page,
    `**/api/workspaces/${secondWorkspace.id}/projects`,
    "GET",
    {
      status: 503,
      contentType: "application/problem+json",
      body: JSON.stringify({ detail: "A delayed metadata request failed." }),
    },
  );
  try {
    await page
      .getByRole("combobox", { name: "Switch workspace", exact: true })
      .selectOption({ label: secondName });
    await failedMetadata.arrived;
    await page
      .getByRole("combobox", { name: "Switch workspace", exact: true })
      .selectOption({ label: renamed });
    await expect(page.locator("#settings-name")).toHaveValue(renamed);
    failedMetadata.release();
    await failedMetadata.delivered;
    await flushBrowserFrames(page);
    await expect(page.locator("#settings-name")).toHaveValue(renamed);
    await expect(page.locator(".inline-error")).toHaveCount(0);
  } finally {
    await failedMetadata.close();
  }
  await expect(
    page
      .locator("#settings-form")
      .getByLabel("Workspace name", { exact: true }),
  ).toHaveValue(renamed);
  await page.evaluate(() => history.replaceState(null, "", "/#overview"));
  await page.reload();
  await expect(
    page
      .getByRole("combobox", { name: "Switch workspace", exact: true })
      .locator("option:checked"),
  ).toHaveText(renamed);

  const second = await browser.newContext({
    baseURL: new URL(page.url()).origin,
  });
  try {
    await apiLogin(second.request, email, password);
    await page.getByRole("link", { name: "Your account", exact: true }).click();
    const otherSession = page
      .getByRole("listitem")
      .filter({ hasText: "Signed-in session" })
      .first();
    await otherSession
      .getByRole("button", { name: "End session", exact: true })
      .click();
    await page
      .locator("#confirm-modal")
      .getByRole("button", { name: "End session", exact: true })
      .click();
    await expect(
      page.getByRole("listitem").filter({ hasText: "Signed-in session" }),
    ).toHaveCount(0);
    expect((await second.request.get("/api/auth/me")).status()).toBe(401);
    await apiLogin(second.request, email, password);
    await page.getByLabel("Current password", { exact: true }).fill(password);
    await page.getByLabel("New password", { exact: true }).fill(nextPassword);
    await page
      .getByLabel("Confirm new password", { exact: true })
      .fill(nextPassword);
    await page
      .getByRole("button", { name: "Update password", exact: true })
      .click();
    await expect(
      page.getByRole("heading", {
        name: "Good to have you back.",
        exact: true,
      }),
    ).toBeVisible();
    expect((await second.request.get("/api/auth/me")).status()).toBe(401);
    await page
      .getByRole("textbox", { name: "Email address", exact: true })
      .fill(email);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page
      .getByRole("button", { name: "Sign in to Orbit", exact: true })
      .click();
    await expect(page.locator("#auth-error")).toBeVisible();
    await signInThroughUi(page, email, nextPassword);
    await page.getByRole("link", { name: "Your account", exact: true }).click();
    await expect(page.getByLabel("Display name", { exact: true })).toHaveValue(
      "Casey Rivera",
    );
    await page
      .getByRole("listitem")
      .filter({ hasText: "Current session" })
      .getByRole("button", { name: "End session", exact: true })
      .click();
    await page
      .locator("#confirm-modal")
      .getByRole("button", { name: "End session", exact: true })
      .click();
    await expect(
      page.getByRole("heading", {
        name: "Good to have you back.",
        exact: true,
      }),
    ).toBeVisible();
    expect(errors).toEqual([]);
  } finally {
    await second.close();
  }
});

test("real assignment and comment notifications open task links, survive reload, and mark read on mobile", async ({
  page,
  browser,
}) => {
  const suffix = unique();
  const email = `actor-${suffix}@example.test`;
  const recipientEmail = `recipient-${suffix}@example.test`;
  const password = "OrbitNotify!2026";
  const title = `Assigned ${suffix}`;
  const workspaceName = `Inbox ${suffix}`;
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await registerAndCreateWorkspace(page, {
    email,
    password,
    name: "Notification Actor",
    workspace: workspaceName,
  });
  const workspace = await workspaceByName(page.request, workspaceName);
  const recipient = await browser.newContext({
    baseURL: new URL(page.url()).origin,
    viewport: { width: 390, height: 844 },
  });
  try {
    const registered = await recipient.request.post("/api/auth/register", {
      headers: await csrfHeaders(recipient.request),
      data: { name: "Notification Recipient", email: recipientEmail, password },
    });
    expect(registered.status()).toBe(201);
    const user = await registered.json();
    const member = await page.request.post(
      `/api/workspaces/${workspace.id}/members`,
      {
        headers: await csrfHeaders(page.request),
        data: { email: recipientEmail, role: "MEMBER" },
      },
    );
    expect(member.status()).toBe(201);
    const projectResponse = await page.request.post(
      `/api/workspaces/${workspace.id}/projects`,
      {
        headers: await csrfHeaders(page.request),
        data: {
          name: `Notifications ${suffix}`,
          description: "A real assignment flow.",
          color: "#7c6af2",
        },
      },
    );
    expect(projectResponse.status()).toBe(201);
    const project = await projectResponse.json();
    const taskResponse = await page.request.post(
      `/api/workspaces/${workspace.id}/tasks`,
      {
        headers: await csrfHeaders(page.request),
        data: {
          title,
          description: "Follow this work from a real notification.",
          projectId: project.id,
          status: "TODO",
          priority: "MEDIUM",
          assigneeId: user.id,
          dueDate: null,
        },
      },
    );
    expect(taskResponse.status()).toBe(201);
    const task = await taskResponse.json();
    const recipientPage = await recipient.newPage();
    recipientPage.on("pageerror", (error) => errors.push(error.message));
    await recipientPage.goto("/");
    await signInThroughUi(recipientPage, recipientEmail, password);
    await navigateMobile(recipientPage, "Inbox");
    await expect(recipientPage.locator(".notification-row.unread")).toHaveCount(
      2,
    );
    await recipientPage
      .locator(".notification-row")
      .filter({ hasText: "Your workspace access changed" })
      .getByRole("button", {
        name: "Mark Your workspace access changed as read",
        exact: true,
      })
      .click();
    await expect(recipientPage.locator(".notification-row.unread")).toHaveCount(
      1,
    );
    await expectNoPageOverflow(recipientPage);
    await recipientPage
      .locator(".notification-row")
      .filter({ hasText: title })
      .getByRole("button", { name: "You were assigned a task", exact: true })
      .click();
    await expect(
      dialog(recipientPage).getByLabel("Task name", { exact: true }),
    ).toHaveValue(title);
    expect(recipientPage.url()).toContain(`task=${task.id}`);
    await recipientPage.reload();
    await expect(
      dialog(recipientPage).getByLabel("Task name", { exact: true }),
    ).toHaveValue(title);
    await dialog(recipientPage)
      .getByRole("button", { name: "Close dialog", exact: true })
      .click();
    const comment = await page.request.post(
      `/api/workspaces/${workspace.id}/tasks/${task.id}/comments`,
      {
        headers: await csrfHeaders(page.request),
        data: { body: `A useful update ${suffix}` },
      },
    );
    expect(comment.status()).toBe(201);
    await navigateMobile(recipientPage, "Inbox");
    await expect(recipientPage.locator(".notification-row")).toHaveCount(3);
    await expect(recipientPage.locator(".notification-row.unread")).toHaveCount(
      1,
    );
    await recipientPage
      .getByRole("button", { name: "Mark all as read", exact: true })
      .click();
    await expect(recipientPage.locator(".notification-row.unread")).toHaveCount(
      0,
    );
    const inbox = await recipient.request.get("/api/notifications");
    expect((await inbox.json()).unreadCount).toBe(0);
    await expectNoPageOverflow(recipientPage);
    expect(errors).toEqual([]);
  } finally {
    await cleanupTask(page.request, workspace.id, title);
    await recipient.close();
  }
});

test("a new account creates a workspace and project through the mobile product, then signs in again", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const suffix = unique();
  const email = `orbit-e2e-${suffix}@example.test`;
  const password = "OrbitE2e!2026-Strong";
  const workspaceName = `E2E Studio ${suffix}`;
  const projectName = `Launch ${suffix}`;
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));

  await page.goto("/");
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await page
    .getByRole("textbox", { name: "Your name", exact: true })
    .fill("E2E Builder");
  await page
    .getByRole("textbox", { name: "Email address", exact: true })
    .fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page
    .getByRole("button", { name: "Create your account", exact: true })
    .click();
  await dialog(page)
    .getByLabel("Workspace name", { exact: true })
    .fill(workspaceName);
  await dialog(page)
    .getByRole("button", { name: "Create workspace", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Hello, E2E." }),
  ).toBeVisible();
  await expectNoPageOverflow(page);

  await navigateMobile(page, "Projects");
  await page.getByRole("button", { name: "New project", exact: true }).click();
  await dialog(page)
    .getByLabel("Project name", { exact: true })
    .fill(projectName);
  await dialog(page)
    .getByLabel("What’s the goal?", { exact: true })
    .fill("Ship a usable product with a clear, shared plan.");
  await dialog(page).getByRole("radio", { name: "green", exact: true }).check();
  await dialog(page)
    .getByRole("button", { name: "Create project", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: projectName, exact: true }),
  ).toBeVisible();
  await expectNoPageOverflow(page);

  await page.reload();
  await page.getByRole("button", { name: projectName, exact: true }).click();
  await expect(
    page
      .getByRole("combobox", { name: "Project", exact: true })
      .locator("option:checked"),
  ).toHaveText(projectName);
  await expectNoPageOverflow(page);

  await page
    .getByRole("button", { name: "Open navigation", exact: true })
    .click();
  await page
    .locator("#workspace-navigation")
    .getByRole("button", { name: "Sign out", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Good to have you back." }),
  ).toBeVisible();
  await page
    .getByRole("textbox", { name: "Email address", exact: true })
    .fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page
    .getByRole("button", { name: "Sign in to Orbit", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Open navigation", exact: true }),
  ).toBeVisible();
  await navigateMobile(page, "Projects");
  await expect(
    page.getByRole("button", { name: projectName, exact: true }),
  ).toBeVisible();
  await expectNoPageOverflow(page);
  expect(errors).toEqual([]);

  // No account/workspace deletion endpoint exists. Keep the uniquely named test
  // account isolated and archive its project so subsequent runs have no active work.
  const workspace = await workspaceByName(page.request, workspaceName);
  const projects = await page.request.get(
    `/api/workspaces/${workspace.id}/projects`,
  );
  const project = (await projects.json()).find(
    (item) => item.name === projectName,
  );
  const archived = await page.request.patch(
    `/api/workspaces/${workspace.id}/projects/${project.id}`,
    {
      headers: await csrfHeaders(page.request),
      data: {
        name: project.name,
        description: project.description,
        color: project.color,
        status: "ARCHIVED",
        version: project.version,
      },
    },
  );
  expect(archived.status()).toBe(200);
});
