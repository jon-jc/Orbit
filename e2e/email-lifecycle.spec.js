import { randomUUID } from "node:crypto";
import { test, expect, request as http } from "@playwright/test";

const mailpitUrl = process.env.ORBIT_MAILPIT_URL;
const unique = () => randomUUID().slice(0, 12);
const modal = (page) => page.locator("#modal");
const addressedTo = (message, recipient) =>
  message.To?.some(
    (to) =>
      String(to.Address || to.Email || "").toLowerCase() ===
      recipient.toLowerCase(),
  );

async function registerWorkspace(page, email, password, name, workspace) {
  await page.goto("/");
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await page.getByLabel("Your name", { exact: true }).fill(name);
  await page.getByLabel("Email address", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page
    .getByRole("button", { name: "Create your account", exact: true })
    .click();
  await modal(page)
    .getByLabel("Workspace name", { exact: true })
    .fill(workspace);
  await modal(page)
    .getByRole("button", { name: "Create workspace", exact: true })
    .click();
  await expect(page.locator(".app-shell")).toBeVisible();
}

async function waitForEmail(mail, recipient, subject) {
  let found;
  await expect
    .poll(
      async () => {
        const response = await mail.get("/api/v1/search", {
          params: { query: `to:${recipient}`, limit: 100 },
        });
        if (!response.ok()) return false;
        const result = await response.json();
        found = result.messages.find(
          (m) => m.Subject === subject && addressedTo(m, recipient),
        );
        return Boolean(found);
      },
      {
        timeout: 60_000,
        intervals: [250, 500, 1000, 2000],
        message: "The expected email must reach the isolated SMTP capture.",
      },
    )
    .toBe(true);
  const response = await mail.get(
    `/api/v1/message/${encodeURIComponent(found.ID)}`,
  );
  if (!response.ok()) throw new Error("The captured email could not be read.");
  return (await response.json()).Text;
}

function emailLink(text, route, origin) {
  const value = String(text)
    .match(/https?:\/\/[^\s<>"']+/g)
    ?.find((link) => link.includes(`#${route}?token=`));
  if (!value)
    throw new Error(
      "The captured email did not contain the expected account link.",
    );
  const link = new URL(value);
  if (link.origin !== origin)
    throw new Error("The account email used an unexpected public base URL.");
  // Tokens remain in memory; assertions and console output never include them.
  return link.href;
}

async function openEmailLink(page, link) {
  try {
    await page.goto(link);
  } catch {
    throw new Error("Unable to open the captured account email link.");
  }
}

async function removeCapturedMail(mail, recipients) {
  for (const recipient of recipients) {
    const response = await mail.get("/api/v1/search", {
      params: { query: `to:${recipient}`, limit: 100 },
    });
    if (!response.ok()) continue;
    const messages = (await response.json()).messages.filter((m) =>
      addressedTo(m, recipient),
    );
    // Mailpit deletes every message for an empty IDs body, so never submit one.
    if (messages.length)
      await mail.delete("/api/v1/messages", {
        data: { IDs: messages.map((m) => m.ID) },
      });
  }
}

async function requireMailEnabled(page) {
  const response = await page.request.get("/api/auth/config");
  expect(
    (await response.json()).mailEnabled,
    "Email tests require real SMTP delivery to the configured capture.",
  ).toBe(true);
}

test("captured verification and password-reset emails complete the account lifecycle", async ({
  page,
}) => {
  test.skip(
    !mailpitUrl,
    "Set ORBIT_MAILPIT_URL and enable SMTP delivery to run actual email lifecycle checks.",
  );
  test.setTimeout(120_000);
  await requireMailEnabled(page);
  const suffix = unique();
  const email = `mail-${suffix}@example.test`;
  const password = "OrbitMailInitial!2026";
  const newPassword = "OrbitMailUpdated!2026";
  const mail = await http.newContext({ baseURL: mailpitUrl });
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  try {
    await registerWorkspace(
      page,
      email,
      password,
      "Email Builder",
      `Email ${suffix}`,
    );
    const origin = new URL(page.url()).origin;
    const verification = await waitForEmail(
      mail,
      email,
      "Verify your Orbit email address",
    );
    await openEmailLink(page, emailLink(verification, "verify-email", origin));
    await page
      .getByRole("button", { name: "Verify my email", exact: true })
      .click();
    await expect(
      page.getByRole("heading", {
        name: "Your email is verified.",
        exact: true,
      }),
    ).toBeVisible();
    await page
      .getByRole("button", { name: "Continue to Orbit", exact: true })
      .click();
    await page.getByRole("link", { name: "Your account", exact: true }).click();
    await expect(
      page.getByText("Email verified", { exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await page
      .getByRole("button", { name: "Forgot your password?", exact: true })
      .click();
    await page.getByLabel("Email address", { exact: true }).fill(email);
    await page
      .getByRole("button", { name: "Send reset link", exact: true })
      .click();
    await expect(page.locator("#recovery-status")).toContainText(
      "If an account matches",
    );
    const reset = await waitForEmail(mail, email, "Reset your Orbit password");
    await openEmailLink(page, emailLink(reset, "reset-password", origin));
    await page.getByLabel("New password", { exact: true }).fill(newPassword);
    await page
      .getByLabel("Confirm new password", { exact: true })
      .fill(newPassword);
    await page
      .getByRole("button", { name: "Save new password", exact: true })
      .click();
    await expect(
      page.getByRole("heading", {
        name: "Good to have you back.",
        exact: true,
      }),
    ).toBeVisible();
    await page.getByLabel("Email address", { exact: true }).fill(email);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page
      .getByRole("button", { name: "Sign in to Orbit", exact: true })
      .click();
    await expect(page.locator("#auth-error")).toBeVisible();
    await page.getByLabel("Password", { exact: true }).fill(newPassword);
    await page
      .getByRole("button", { name: "Sign in to Orbit", exact: true })
      .click();
    await page.getByRole("link", { name: "Your account", exact: true }).click();
    await expect(
      page.getByText("Email verified", { exact: true }),
    ).toBeVisible();
    await waitForEmail(mail, email, "Your Orbit password was changed");
    expect(errors).toEqual([]);
  } finally {
    await removeCapturedMail(mail, [email]);
    await mail.dispose();
  }
});

test("an emailed invitation lets a new account register, accept viewer access, and appear in the owner roster", async ({
  page,
  browser,
}) => {
  test.skip(
    !mailpitUrl,
    "Set ORBIT_MAILPIT_URL and enable SMTP delivery to run actual invitation email checks.",
  );
  test.setTimeout(120_000);
  await requireMailEnabled(page);
  const suffix = unique();
  const ownerEmail = `invite-owner-${suffix}@example.test`;
  const invitedEmail = `invitee-${suffix}@example.test`;
  const password = "OrbitInvite!2026";
  const workspace = `Invited ${suffix}`;
  const mail = await http.newContext({ baseURL: mailpitUrl });
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  let invitee;
  try {
    await registerWorkspace(
      page,
      ownerEmail,
      password,
      "Invitation Owner",
      workspace,
    );
    const origin = new URL(page.url()).origin;
    await page
      .getByRole("link", { name: "Workspace settings", exact: true })
      .click();
    await page.getByLabel("Teammate email", { exact: true }).fill(invitedEmail);
    await page
      .getByLabel("Workspace access", { exact: true })
      .selectOption("VIEWER");
    await page
      .getByRole("button", { name: "Send invitation", exact: true })
      .click();
    await expect(page.locator(".invitation-table")).toContainText(invitedEmail);
    const invitation = await waitForEmail(
      mail,
      invitedEmail,
      "You're invited to an Orbit workspace",
    );
    invitee = await browser.newContext({ baseURL: origin });
    const guest = await invitee.newPage();
    guest.on("pageerror", (error) => errors.push(error.message));
    await openEmailLink(guest, emailLink(invitation, "invite", origin));
    await guest
      .getByRole("button", { name: "Create an account", exact: true })
      .click();
    await expect(
      guest.getByLabel("Email address", { exact: true }),
    ).toHaveValue(invitedEmail);
    await guest
      .getByLabel("Your name", { exact: true })
      .fill("Invited Builder");
    await guest.getByLabel("Password", { exact: true }).fill(password);
    await guest
      .getByRole("button", { name: "Create your account", exact: true })
      .click();
    await expect(
      guest.getByRole("button", { name: "Join workspace", exact: true }),
    ).toBeVisible();
    const verification = await waitForEmail(
      mail,
      invitedEmail,
      "Verify your Orbit email address",
    );
    await openEmailLink(guest, emailLink(verification, "verify-email", origin));
    await guest
      .getByRole("button", { name: "Verify my email", exact: true })
      .click();
    await guest
      .getByRole("button", { name: "Continue to Orbit", exact: true })
      .click();
    await guest
      .getByRole("button", { name: "Join workspace", exact: true })
      .click();
    await expect(
      guest
        .getByRole("combobox", { name: "Switch workspace", exact: true })
        .locator("option:checked"),
    ).toHaveText(workspace);
    await expect(
      guest.getByRole("button", { name: "New task", exact: true }),
    ).toHaveCount(0);
    await guest.getByRole("link", { name: "Team", exact: true }).click();
    await expect(guest.locator(".data-table")).toContainText(invitedEmail);
    await page.reload();
    await expect(
      page.locator(".invitation-table tr").filter({ hasText: invitedEmail }),
    ).toContainText("accepted");
    expect(errors).toEqual([]);
  } finally {
    if (invitee) await invitee.close();
    await removeCapturedMail(mail, [ownerEmail, invitedEmail]);
    await mail.dispose();
  }
});
