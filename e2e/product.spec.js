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
    await dialog(page)
      .getByRole("button", { name: "Close dialog", exact: true })
      .click();

    await page.reload();
    await page
      .getByRole("searchbox", { name: "Search tasks", exact: true })
      .fill(title);
    await page.getByRole("button", { name: title, exact: true }).click();
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
      page.getByRole("heading", { name: "Nothing here just yet", exact: true }),
    ).toBeVisible();
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
