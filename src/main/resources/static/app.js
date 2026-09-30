const $ = (selector, scope = document) => scope.querySelector(selector);
const app = $("#app");
const modal = $("#modal");
const confirmModal = $("#confirm-modal");
const STATUS = {
  BACKLOG: "Backlog",
  TODO: "To do",
  IN_PROGRESS: "In progress",
  IN_REVIEW: "In review",
  DONE: "Done",
};
const PRIORITY = {
  LOW: "Low",
  MEDIUM: "Medium",
  HIGH: "High",
  URGENT: "Urgent",
};
const COLORS = {
  purple: "#7c6af2",
  green: "#82a55a",
  blue: "#538dba",
  orange: "#d79b4f",
  pink: "#bd7093",
};
const paths = {
  grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  layers:
    '<path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 12 9 5 9-5M3 16l9 5 9-5"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  checkCircle: '<circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/>',
  tasks:
    '<rect x="4" y="3" width="16" height="18" rx="3"/><path d="m8 9 1 1 2-2m-3 7 1 1 2-2m3-5h3m-3 6h3"/>',
  people:
    '<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6m2 3a5 5 0 0 1 3 5v2"/>',
  activity: '<path d="M3 12h4l3-8 4 16 3-8h4"/>',
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  arrow: '<path d="M4 12h16m-6-6 6 6-6 6"/>',
  chevron: '<path d="m9 5 7 7-7 7"/>',
  calendar:
    '<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M16 3v4M8 3v4M3 11h18"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  flag: '<path d="M5 21V4m0 0c4-3 8 3 14 0v9c-6 3-10-3-14 0"/>',
  comment:
    '<path d="M21 11a8.5 8.5 0 0 1-9 9 10 10 0 0 1-4-1l-5 2 2-5a10 10 0 0 1-1-4 8.5 8.5 0 0 1 9-9 8.5 8.5 0 0 1 8 8Z"/>',
  list: '<path d="M9 5h12M9 12h12M9 19h12M3 5h1M3 12h1M3 19h1"/>',
  board:
    '<rect x="3" y="4" width="5" height="16" rx="1"/><rect x="10" y="4" width="5" height="12" rx="1"/><rect x="17" y="4" width="4" height="15" rx="1"/>',
  logout: '<path d="M9 4H4v16h5m5-15 6 7-6 7M8 12h12"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
  edit: '<path d="m15 4 5 5-11 11H4v-5L15 4Z"/><path d="m12 7 5 5"/>',
  download: '<path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/>',
  shield:
    '<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z"/><path d="m8 12 3 3 5-6"/>',
  sparkles:
    '<path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z"/>',
  folder:
    '<path d="M3 7V5a2 2 0 0 1 2-2h5l3 3h6a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z"/>',
  more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v.5"/>',
  trash: '<path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7"/>',
  archive:
    '<rect x="3" y="3" width="18" height="4" rx="1"/><path d="M5 7v14h14V7M9 11h6"/>',
  alert: '<path d="m12 3 10 18H2L12 3Z"/><path d="M12 9v5m0 3v.5"/>',
  settings:
    '<path d="m9 3-1 3-3 1v4l2 1-1 3 3 3 3-1 1 2h4l1-3 3-1v-4l-2-1 1-3-3-3-3 1-1-2H9Z"/><circle cx="12" cy="12" r="3"/>',
  bell: '<path d="M5 17h14l-2-3V9a5 5 0 0 0-10 0v5l-2 3Zm5 4h4"/>',
  mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 7 9 6 9-6"/>',
  link: '<path d="m9 15 6-6M8 9l-3 3a4 4 0 0 0 6 6l3-3m-4-6 3-3a4 4 0 0 1 6 6l-3 3"/>',
};
const icon = (name) =>
  `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.layers}</svg>`;
const esc = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const initials = (name) =>
  String(name || "?")
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((x) => x[0])
    .join("")
    .toUpperCase();
const avatar = (name, cls = "") =>
  `<span role="img" class="avatar ${cls}" title="${esc(name)}" aria-label="${esc(name)}">${esc(initials(name))}</span>`;
const state = {
  user: null,
  csrf: null,
  demo: false,
  workspace: null,
  workspaces: [],
  projects: [],
  members: [],
  page: "overview",
  taskView: "board",
  tasks: null,
  activity: null,
  overview: null,
  filters: { q: "", status: "", priority: "", projectId: "", assigneeId: "" },
  request: 0,
  modalRequest: 0,
  taskModal: null,
  registrationEnabled: true,
  emailVerificationRequired: false,
  account: null,
  notifications: null,
  invitations: [],
  unreadNotifications: 0,
  mailEnabled: false,
  pendingInvite: null,
  sessions: [],
};
const PAGES = [
  "overview",
  "projects",
  "tasks",
  "team",
  "activity",
  "account",
  "settings",
  "notifications",
];
function route() {
  const [page, query = ""] = location.hash.replace(/^#/, "").split("?");
  const params = new URLSearchParams(query);
  const outside = new URLSearchParams(location.search);
  if (outside.has("resetToken"))
    return {
      page: "reset-password",
      token: outside.get("resetToken"),
      workspace: null,
      task: null,
    };
  if (outside.has("verifyToken"))
    return {
      page: "verify-email",
      token: outside.get("verifyToken"),
      workspace: null,
      task: null,
    };
  if (outside.has("inviteToken"))
    return {
      page: "invite",
      token: outside.get("inviteToken"),
      workspace: null,
      task: null,
    };
  return {
    page: page || "overview",
    workspace: params.get("workspace"),
    task: params.get("task"),
    token: params.get("token"),
  };
}
function rememberedWorkspace() {
  try {
    return localStorage.getItem(`orbit.workspace.${state.user.id}`);
  } catch {
    return null;
  }
}
function rememberWorkspace() {
  if (!state.user || !state.workspace) return;
  try {
    localStorage.setItem(
      `orbit.workspace.${state.user.id}`,
      state.workspace.id,
    );
  } catch {
    /* Storage may be disabled; URL links still work. */
  }
}
function writeRoute(page = state.page, task = null, replace = true) {
  const params = new URLSearchParams();
  if (state.workspace) params.set("workspace", state.workspace.id);
  if (task) params.set("task", task);
  const hash = `#${page}${params.size ? `?${params}` : ""}`;
  const url = `${location.pathname}${hash}`;
  if (replace) history.replaceState(null, "", url);
  else history.pushState(null, "", url);
}
function clearTokenRoute() {
  history.replaceState(null, "", location.pathname);
}
const writable = () => state.workspace && state.workspace.role !== "VIEWER";
const owner = () => state.workspace?.role === "OWNER";
const base = () => `/api/workspaces/${encodeURIComponent(state.workspace.id)}`;
const options = (values, current, blank = "") =>
  `${blank ? `<option value="">${esc(blank)}</option>` : ""}${Object.entries(
    values,
  )
    .map(
      ([value, label]) =>
        `<option value="${esc(value)}"${String(current) === value ? " selected" : ""}>${esc(label)}</option>`,
    )
    .join("")}`;
const projectOptions = (current) =>
  `<option value="">Select a project</option>${state.projects
    .filter((p) => p.status === "ACTIVE" || p.id === current)
    .map(
      (p) =>
        `<option value="${esc(p.id)}"${p.id === current ? " selected" : ""}>${esc(p.name)}${p.status === "ARCHIVED" ? " (archived)" : ""}</option>`,
    )
    .join("")}`;
const memberOptions = (current) =>
  `<option value="">Unassigned</option>${state.members.map((m) => `<option value="${esc(m.id)}"${m.id === current ? " selected" : ""}>${esc(m.name)}</option>`).join("")}`;
const dateObj = (value) =>
  new Date(value?.length === 10 ? `${value}T12:00:00` : value);
const formatDate = (value) =>
  value
    ? dateObj(value).toLocaleDateString(undefined, {
        month: "short",
        day: "numeric",
      })
    : "No due date";
const formatTime = (value) =>
  new Date(value).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};
const isOverdue = (task) =>
  task.dueDate && task.dueDate < today() && task.status !== "DONE";
const duePill = (task) =>
  task.dueDate
    ? `<span class="due-pill${isOverdue(task) ? " overdue" : ""}">${esc(formatDate(task.dueDate))}</span>`
    : "";
const statusClass = (status) =>
  String(status).toLowerCase().replaceAll("_", "-");
const priorityPill = (priority) =>
  `<span class="priority-pill ${String(priority).toLowerCase()}">${icon("flag")}${esc(PRIORITY[priority] || priority)}</span>`;
function palette(color) {
  if (!/^#[0-9a-f]{6}$/i.test(color || "")) return "purple";
  const r = parseInt(color.slice(1, 3), 16),
    g = parseInt(color.slice(3, 5), 16),
    b = parseInt(color.slice(5, 7), 16);
  if (g > r && g > b) return "green";
  if (b > r && g > r) return "blue";
  if (r > b && g > b) return "orange";
  if (r > g && r > b) return "pink";
  return "purple";
}
class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}
async function csrf() {
  const result = await api("/api/auth/csrf");
  state.csrf = result;
}
async function api(url, { method = "GET", body, signal } = {}) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (!["GET", "HEAD"].includes(method)) {
    if (!state.csrf) await csrf();
    headers[state.csrf.headerName || "X-CSRF-TOKEN"] = state.csrf.token;
  }
  let response;
  try {
    response = await fetch(url, {
      method,
      headers,
      credentials: "same-origin",
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
    });
  } catch (error) {
    if (error.name === "AbortError") throw error;
    throw new ApiError(
      "Unable to reach Orbit. Check your connection and try again.",
      0,
    );
  }
  const contentType = response.headers.get("content-type") || "";
  let result = null;
  if (response.status !== 204)
    result = contentType.includes("json")
      ? await response.json()
      : await response.text();
  if (!response.ok) {
    let message =
      result?.detail ||
      result?.message ||
      (response.status === 401
        ? "Your email or password is incorrect."
        : `Request failed (${response.status}). Please try again.`);
    if (result?.errors) {
      const errors = Array.isArray(result.errors)
        ? result.errors
        : Object.values(result.errors);
      message += ` ${errors
        .map((e) => (typeof e === "string" ? e : e.message || ""))
        .filter(Boolean)
        .join(" ")}`;
    }
    throw new ApiError(message, response.status);
  }
  return result;
}
function toast(message, error = false) {
  const el = document.createElement("div");
  el.className = `toast${error ? " error" : ""}`;
  el.innerHTML = `${icon(error ? "alert" : "checkCircle")}<span>${esc(message)}</span>`;
  $("#toast-region").append(el);
  setTimeout(() => el.remove(), 5000);
}
function loading() {
  return '<div class="skeleton-grid" aria-hidden="true"><div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div></div><div class="skeleton big" aria-hidden="true"></div><span class="sr-only" role="status">Loading workspace…</span>';
}
function empty(title, copy, action = "", label = "", name = "layers") {
  return `<div class="empty-state"><div class="empty-icon">${icon(name)}</div><h2>${esc(title)}</h2><p>${esc(copy)}</p>${action ? `<button class="primary" data-action="${esc(action)}">${icon("plus")}${esc(label)}</button>` : ""}</div>`;
}
function pageHeader(title, subtitle, actions = "", eyebrow = "") {
  return `<div class="page-header"><div>${eyebrow ? `<div class="eyebrow">${esc(eyebrow)}</div>` : ""}<h1>${esc(title)}</h1><p>${esc(subtitle)}</p></div>${actions ? `<div class="page-header-actions">${actions}</div>` : ""}</div>`;
}
function authLayout(content) {
  return `<div class="auth-layout"><section class="auth-story" aria-label="About Orbit"><div class="brand"><div class="orbit-mark" aria-hidden="true"></div>orbit</div><div class="auth-story-content"><div class="eyebrow">A little clarity goes a long way</div><h1>Great work.<br>Better rhythm.<br><em>Your orbit.</em></h1><p>A thoughtful home for your projects, your people, and the next thing that matters.</p><div class="auth-orbits" aria-hidden="true"><div class="orbital-path"></div><div class="orbital-path"></div><div class="orbital-sun"></div><div class="orbital-moon"></div><div class="orbital-tag">${icon("checkCircle")}Room to do your best work</div></div></div><div class="auth-story-footer"><span>Built for teams that care.</span><span>WORK, IN FOCUS.</span></div></section><main class="auth-form-side"><div class="auth-form-wrap">${content}</div></main></div>`;
}
function authView(mode = "login", invitedEmail = "") {
  state.request++;
  state.user = null;
  state.workspace = null;
  state.taskModal = null;
  modal.close();
  const canRegister = state.registrationEnabled;
  if (mode === "register" && !canRegister) mode = "login";
  const invitation = state.pendingInvite?.preview;
  app.innerHTML = authLayout(
    `<div class="eyebrow">Welcome to your workspace</div><h2>${mode === "register" ? "Make room for great work." : "Good to have you back."}</h2><p>${invitation ? `Sign in with ${esc(invitation.email)} to join ${esc(invitation.workspaceName)}.` : mode === "register" ? "Start with an account. Build your workspace from there." : "Sign in and pick up where your team left off."}</p><div class="auth-tabs" role="group" aria-label="Account access"><button data-action="auth-login" class="${mode === "login" ? "active" : ""}">Sign in</button>${canRegister ? `<button data-action="auth-register" class="${mode === "register" ? "active" : ""}">Create account</button>` : ""}</div><form id="auth-form" class="auth-form">${mode === "register" ? '<div class="field"><label for="auth-name">Your name</label><input id="auth-name" name="name" required maxlength="100" autocomplete="name" placeholder="Alex Morgan"></div>' : ""}<div class="field"><label for="auth-email">Email address</label><input id="auth-email" name="email" type="email" required maxlength="254" autocomplete="username" placeholder="you@yourteam.com" value="${esc(invitedEmail || invitation?.email || "")}"></div><div class="field"><label for="auth-password">Password</label><input id="auth-password" name="password" type="password" required ${mode === "register" ? 'minlength="12" ' : ""}maxlength="72" autocomplete="${mode === "register" ? "new-password" : "current-password"}" placeholder="${mode === "register" ? "At least 12 characters" : "Your password"}">${mode === "register" ? "<small>Use 12–72 characters. Emoji can reach the size limit sooner.</small>" : ""}</div><div id="auth-error" class="auth-error" role="alert" hidden></div><button class="primary" type="submit">${mode === "register" ? "Create your account" : "Sign in to Orbit"}${icon("arrow")}</button></form>${mode === "login" && state.mailEnabled ? '<div class="auth-help"><button class="text-button" data-action="forgot-password">Forgot your password?</button><button class="text-button" data-action="resend-verification">Resend verification</button></div>' : ""}${state.demo && !invitation ? `<div class="auth-divider">OR, TAKE A LOOK AROUND</div><button class="demo-button" data-action="demo">${icon("sparkles")}Explore the demo workspace</button><p class="auth-footnote">A working studio, ready to explore.<br>Demo changes are shared and saved locally.</p>` : `<p class="auth-footnote">${canRegister ? "Your workspace, securely within reach." : "New accounts are managed by your administrator."}</p>`}<div id="auth-status" class="auth-status" role="status"></div>`,
  );
  $("#auth-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form));
    setBusy(form, true);
    $("#auth-error").hidden = true;
    try {
      if (mode === "register") {
        validatePassword(values.password);
        await api("/api/auth/register", { method: "POST", body: values });
        if (state.emailVerificationRequired) {
          authNotice(
            "Check your inbox.",
            "Confirm your email address with the link we sent, then sign in to get started.",
            values.email,
          );
          return;
        }
      }
      state.user = await api("/api/auth/login", {
        method: "POST",
        body: { email: values.email, password: values.password },
      });
      await csrf();
      await loadWorkspaces();
      if (
        mode === "register" &&
        !state.workspaces.length &&
        route().page !== "invite"
      )
        workspaceForm();
    } catch (error) {
      if ($("#auth-error")) {
        $("#auth-error").textContent = error.message;
        $("#auth-error").hidden = false;
      }
    } finally {
      if (form.isConnected) setBusy(form, false);
    }
  });
  $("#auth-name, #auth-email")?.focus({ preventScroll: true });
}
function validatePassword(password) {
  if (password.length < 12 || new TextEncoder().encode(password).length > 72)
    throw new Error(
      "Use at least 12 characters within the password size limit. Try fewer emoji or accented characters for a very long password.",
    );
}
function authNotice(title, copy, email = "") {
  app.innerHTML = authLayout(
    `<div class="eyebrow">A little account care</div><h2>${esc(title)}</h2><p>${esc(copy)}</p>${email ? `<div class="account-email">${icon("mail")}<span>${esc(email)}</span></div>` : ""}<button class="primary auth-wide" data-action="auth-login">Back to sign in ${icon("arrow")}</button>${state.mailEnabled ? `<button class="text-button" data-action="resend-verification" data-email="${esc(email)}">Need a new verification link?</button>` : ""}<div id="auth-status" class="auth-status" role="status"></div>`,
  );
}
function recoveryForm(kind = "forgot-password", initialEmail = "") {
  if (!state.mailEnabled) {
    app.innerHTML = authLayout(
      '<div class="eyebrow">Account care</div><h2>Email delivery needs a little setup.</h2><p>Account emails are currently unavailable. Ask an administrator to enable email delivery, then request a fresh link.</p><button class="primary" data-action="auth-login">Back to sign in ' +
        icon("arrow") +
        "</button>",
    );
    return;
  }
  const verification = kind === "resend-verification";
  app.innerHTML = authLayout(
    `<div class="eyebrow">${verification ? "Confirm your email" : "Find your way back"}</div><h2>${verification ? "Request a fresh link." : "We’ll help you get back in."}</h2><p>${verification ? "Request a new email verification link for your Orbit account." : "Enter your account email. We’ll send a link to choose a new password."}</p><form id="recovery-form" class="auth-form"><div class="field"><label for="recovery-email">Email address</label><input id="recovery-email" name="email" type="email" required maxlength="254" autocomplete="email" placeholder="you@yourteam.com" value="${esc(initialEmail)}"></div><div class="auth-error" data-form-error role="alert" tabindex="-1" hidden></div><button class="primary" type="submit">${verification ? "Send verification link" : "Send reset link"} ${icon("mail")}</button></form><div id="recovery-status" class="auth-status" role="status"></div><button class="text-button" data-action="auth-login">Back to sign in ${icon("arrow")}</button>`,
  );
  $("#recovery-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    setBusy(form, true);
    $("[data-form-error]", form).hidden = true;
    try {
      const result = await api(
        verification
          ? "/api/auth/resend-verification"
          : "/api/auth/forgot-password",
        {
          method: "POST",
          body: { email: new FormData(form).get("email").trim() },
        },
      );
      $("#recovery-status").textContent =
        `${result.message} Check your inbox; another link can be sent after a minute.`;
      form.reset();
    } catch (error) {
      formError(error, form);
    } finally {
      if (form.isConnected) setBusy(form, false);
    }
  });
  $("#recovery-email")?.focus({ preventScroll: true });
}
function tokenForm(kind, token) {
  const reset = kind === "reset-password";
  if (!token) {
    app.innerHTML = authLayout(
      `<div class="eyebrow">A small interruption</div><h2>This link needs another try.</h2><p>The link is incomplete. Request a fresh email to continue.</p><button class="primary" data-action="${reset ? "forgot-password" : "resend-verification"}">Request a new link ${icon("mail")}</button>`,
    );
    return;
  }
  app.innerHTML = authLayout(
    `<div class="eyebrow">${reset ? "A fresh start" : "One quick step"}</div><h2>${reset ? "Choose your new password." : "Confirm it’s really you."}</h2><p>${reset ? "A memorable phrase makes a strong password. You’ll sign in again after saving." : "Verify your email address and bring your workspace into focus."}</p><form id="token-form" class="auth-form">${reset ? '<div class="field"><label for="reset-password">New password</label><input id="reset-password" name="password" type="password" required minlength="12" maxlength="72" autocomplete="new-password"></div><div class="field"><label for="reset-confirm">Confirm new password</label><input id="reset-confirm" name="confirmation" type="password" required minlength="12" maxlength="72" autocomplete="new-password"></div>' : ""}<div class="auth-error" data-form-error role="alert" tabindex="-1" hidden></div><button class="primary" type="submit">${reset ? "Save new password" : "Verify my email"} ${icon("shield")}</button></form><button class="text-button" data-action="${reset ? "forgot-password" : "resend-verification"}">Need a new link?</button>`,
  );
  $("#token-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form));
    setBusy(form, true);
    $("[data-form-error]", form).hidden = true;
    try {
      if (reset) {
        validatePassword(values.password);
        if (values.password !== values.confirmation)
          throw new Error("The passwords need to match.");
      }
      await api(reset ? "/api/auth/reset-password" : "/api/auth/verify-email", {
        method: "POST",
        body: reset ? { token, password: values.password } : { token },
      });
      clearTokenRoute();
      if (reset) {
        await signedOut("Your password is updated. Sign in with the new one.");
      } else {
        if (state.user) state.user = await api("/api/auth/me");
        app.innerHTML = authLayout(
          `<div class="eyebrow">All confirmed</div><h2>Your email is verified.</h2><p>You’re ready for your next good thing.</p><button class="primary" data-action="auth-continue">${state.user ? "Continue to Orbit" : "Sign in to Orbit"} ${icon("arrow")}</button>`,
        );
      }
    } catch (error) {
      formError(error, form);
    } finally {
      if (form.isConnected) setBusy(form, false);
    }
  });
  $("#token-form input, #token-form button[type='submit']")?.focus({
    preventScroll: true,
  });
}
async function specialRoute() {
  const current = route();
  if (current.page === "reset-password" || current.page === "verify-email") {
    tokenForm(current.page, current.token);
    return true;
  }
  if (current.page === "invite") {
    await invitationScreen(current.token);
    return true;
  }
  return false;
}

function setBusy(form, busy) {
  form
    .querySelectorAll('button[type="submit"],button.primary')
    .forEach((button) => {
      button.disabled = busy;
    });
  form.setAttribute("aria-busy", String(busy));
}
async function demoLogin() {
  const button = $('[data-action="demo"]');
  button.disabled = true;
  $("#auth-status").textContent = "Opening Northstar Studio…";
  try {
    state.user = await api("/api/auth/login", {
      method: "POST",
      body: { email: "alex@orbit.local", password: "OrbitDemo!2026" },
    });
    await csrf();
    await loadWorkspaces();
  } catch (error) {
    if ($("#auth-error")) {
      $("#auth-error").textContent = error.message;
      $("#auth-error").hidden = false;
      button.disabled = false;
      $("#auth-status").textContent = "";
    }
  }
}
async function loadWorkspaces() {
  state.workspaces = await api("/api/workspaces");
  if (state.pendingInvite && route().page !== "invite") {
    const params = new URLSearchParams({ token: state.pendingInvite.token });
    history.replaceState(null, "", `${location.pathname}#invite?${params}`);
  }
  if (await specialRoute()) return;
  const current = route();
  const oldId =
    current.workspace || state.workspace?.id || rememberedWorkspace();
  state.workspace =
    state.workspaces.find((w) => w.id === oldId) || state.workspaces[0] || null;
  state.page = PAGES.includes(current.page) ? current.page : "overview";
  rememberWorkspace();
  writeRoute(state.page, current.task);
  shell();
  if (state.workspace) {
    await loadWorkspace();
    if (current.task) await openTask(current.task, false);
  } else if (["account", "notifications"].includes(state.page))
    await loadPage();
  else
    $("#page-content").innerHTML =
      pageHeader("A fresh start.", "Your best work needs a place to begin.") +
      `<div class="panel">${empty("Welcome to Orbit", "Create a workspace, add your projects, and bring your team into the picture.", "workspace-create", "Create a workspace", "sparkles")}</div>`;
  refreshUnread();
}
function shell() {
  ++state.request;
  const nav = [
    ["overview", "grid", "Overview"],
    ["projects", "layers", "Projects"],
    ["tasks", "tasks", "My workspace"],
    ["team", "people", "Team"],
    ["activity", "activity", "Activity"],
    ["notifications", "bell", "Inbox"],
    ["settings", "settings", "Workspace settings"],
    ["account", "shield", "Your account"],
  ];
  app.innerHTML = `<div class="app-shell"><button class="mobile-overlay" data-action="menu-close" aria-label="Close navigation"></button><aside id="workspace-navigation" class="sidebar" aria-label="Workspace navigation"><div class="brand"><div class="orbit-mark" aria-hidden="true"></div>orbit<span>WORKSPACE</span></div><div class="workspace-picker"><div class="workspace-monogram">${esc(initials(state.workspace?.name || "O"))}</div><label class="sr-only" for="workspace-select">Switch workspace</label><select id="workspace-select">${state.workspaces.length ? state.workspaces.map((w) => `<option value="${esc(w.id)}"${w.id === state.workspace?.id ? " selected" : ""}>${esc(w.name)}</option>`).join("") : "<option>Your workspace</option>"}</select></div><p class="sidebar-label">YOUR SPACE</p><nav class="nav">${nav.map(([key, i, label]) => `<a href="#${key}" data-page="${key}" class="${state.page === key ? "active" : ""}"${state.page === key ? ' aria-current="page"' : ""}>${icon(i)}<span>${label}</span></a>`).join("")}</nav><div class="sidebar-space"></div><div class="workspace-note">${icon("sparkles")}<h3>A little more headspace.</h3><p>Less noise. More room for what matters.</p></div><button class="workspace-create" data-action="workspace-create">${icon("plus")}Create workspace</button><div class="profile">${avatar(state.user.name, "dark")}<div><strong>${esc(state.user.name)}</strong><small>${esc(state.workspace?.role?.toLowerCase() || "Your account")}</small></div><button class="icon-button" data-action="logout" aria-label="Sign out" title="Sign out">${icon("logout")}</button></div></aside><div class="main-shell"><header class="topbar"><button class="mobile-menu" data-action="menu-toggle" aria-label="Open navigation" aria-expanded="false" aria-controls="workspace-navigation">${icon("menu")}</button><div class="breadcrumb"><span>${esc(state.workspace?.name || "Workspace")}</span>${icon("chevron")}<strong id="breadcrumb-page">${nav.find((x) => x[0] === state.page)?.[2] || "Overview"}</strong></div><div class="topbar-right"><form id="global-search-form" class="global-search"><label class="sr-only" for="global-search">Search all tasks</label>${icon("search")}<input id="global-search" placeholder="Search your tasks…" type="search" maxlength="200"><kbd>/</kbd></form>${writable() ? `<button class="primary" data-action="task-create">${icon("plus")}New task</button>` : ""}<button id="notification-button" class="icon-button notification-trigger" data-action="view-notifications" aria-label="Inbox" title="Inbox">${icon("bell")}<span id="notification-count" class="notification-count" hidden></span></button>${avatar(state.user.name)}</div></header><main id="page-content" class="content" tabindex="-1">${loading()}</main></div></div>`;
  $("#global-search-form").addEventListener("submit", (event) => {
    event.preventDefault();
    state.filters.q = $("#global-search").value.trim();
    navigate("tasks");
  });
  $("#workspace-select").addEventListener("change", async (event) => {
    state.workspace = state.workspaces.find((w) => w.id === event.target.value);
    state.filters = {
      q: "",
      status: "",
      priority: "",
      projectId: "",
      assigneeId: "",
    };
    state.tasks = null;
    state.projects = [];
    state.members = [];
    modal.close();
    rememberWorkspace();
    writeRoute();
    shell();
    await loadWorkspace();
  });
  syncDrawer();
  updateUnreadBadge();
}
function syncDrawer() {
  const small = window.matchMedia("(max-width:700px)").matches;
  const open = small && $(".app-shell")?.classList.contains("menu-open");
  if ($(".sidebar")) $(".sidebar").inert = small && !open;
  if ($(".main-shell")) $(".main-shell").inert = Boolean(open);
  $(".mobile-menu")?.setAttribute("aria-expanded", String(Boolean(open)));
}
function setMenu(open) {
  $(".app-shell")?.classList.toggle("menu-open", open);
  syncDrawer();
  if (open) $("#workspace-select")?.focus();
  else $(".mobile-menu")?.focus({ preventScroll: true });
}
async function loadWorkspace() {
  const workspaceId = state.workspace.id;
  const userId = state.user?.id;
  try {
    const [projects, members] = await Promise.all([
      api(`${base()}/projects`),
      api(`${base()}/members`),
    ]);
    if (state.workspace?.id !== workspaceId || state.user?.id !== userId)
      return;
    state.projects = projects;
    state.members = members;
    await loadPage();
  } catch (error) {
    if (state.workspace?.id === workspaceId && state.user?.id === userId)
      pageError(error);
  }
}
function navigate(page) {
  if (!state.user) return;
  state.page = page;
  writeRoute(page, null, false);
  modal.close();
  $(".app-shell")?.classList.remove("menu-open");
  syncDrawer();
  $(".mobile-menu")?.setAttribute("aria-expanded", "false");
  document.querySelectorAll("[data-page]").forEach((el) => {
    el.classList.toggle("active", el.dataset.page === page);
    if (el.dataset.page === page) el.setAttribute("aria-current", "page");
    else el.removeAttribute("aria-current");
  });
  const names = {
    overview: "Overview",
    projects: "Projects",
    tasks: "My workspace",
    team: "Team",
    activity: "Activity",
    notifications: "Inbox",
    settings: "Workspace settings",
    account: "Your account",
  };
  $("#breadcrumb-page").textContent = names[page];
  document.title = `${names[page]} · Orbit`;
  if (state.workspace || ["account", "notifications"].includes(page))
    loadPage();
  else
    $("#page-content").innerHTML =
      pageHeader(
        "A fresh start.",
        "Create a workspace to bring your team into focus.",
      ) +
      `<div class="panel">${empty("A home for your work", "Create your first workspace to manage its settings and team.", "workspace-create", "Create workspace", "folder")}</div>`;
  $("#page-content")?.focus({ preventScroll: true });
}
function pageError(error) {
  if (!$("#page-content")) return;
  if (error.status === 401) {
    state.csrf = null;
    authView();
    toast("Your session ended. Sign in to continue.", true);
    return;
  }
  $("#page-content").innerHTML =
    pageHeader("A small interruption.", "Your workspace is still here.") +
    `<div class="inline-error" role="alert">${icon("alert")}<span>${esc(error.message)}</span><button data-action="reload">Try again</button></div>`;
}
async function loadPage(soft = false) {
  if (!state.workspace && !["account", "notifications"].includes(state.page))
    return;
  const id = ++state.request;
  const page = state.page;
  const endpoint = state.workspace ? base() : null;
  if (!soft) $("#page-content").innerHTML = loading();
  try {
    if (page === "account") {
      const [data, sessions] = await Promise.all([
        api("/api/account"),
        api("/api/account/sessions"),
      ]);
      if (id !== state.request) return;
      state.account = data;
      state.sessions = sessions;
      state.user = { ...state.user, ...data };
      renderAccount();
    }
    if (page === "settings") {
      const settings = await api(`${endpoint}/settings`);
      if (id !== state.request) return;
      const invitations =
        settings.role === "OWNER" ? await api(`${endpoint}/invitations`) : [];
      if (id !== state.request) return;
      state.workspaceSettings = settings;
      state.invitations = invitations;
      renderSettings();
    }
    if (page === "notifications") {
      const data = await api("/api/notifications?page=0&size=30");
      if (id !== state.request) return;
      state.notifications = data;
      state.unreadNotifications = data.unreadCount;
      renderNotifications();
      updateUnreadBadge();
    }
    if (page === "overview") {
      const data = await api(`${endpoint}/overview`);
      if (id !== state.request) return;
      state.overview = data;
      renderOverview();
    }
    if (page === "projects") {
      const data = await api(`${endpoint}/projects`);
      if (id !== state.request) return;
      state.projects = data;
      renderProjects();
    }
    if (page === "tasks") {
      const params = new URLSearchParams({ page: "0", size: "50" });
      Object.entries(state.filters).forEach(([key, value]) => {
        if (value) params.set(key, value);
      });
      const data = await api(`${endpoint}/tasks?${params}`);
      if (id !== state.request) return;
      state.tasks = data;
      renderTasks();
    }
    if (page === "team") {
      const data = await api(`${endpoint}/members`);
      if (id !== state.request) return;
      state.members = data;
      renderTeam();
    }
    if (page === "activity") {
      const data = await api(`${endpoint}/activity?page=0&size=30`);
      if (id !== state.request) return;
      state.activity = data;
      renderActivity();
    }
  } catch (error) {
    if (id === state.request) pageError(error);
  }
}
function projectCard(p, compact = false) {
  const percent = p.taskCount
    ? Math.round((p.completedTaskCount / p.taskCount) * 100)
    : 0;
  return `<article class="project-card${p.status === "ARCHIVED" ? " project-archived" : ""}"><div class="project-card-top"><div class="project-symbol palette-${palette(p.color)}">${icon("layers")}</div>${writable() ? `<button class="icon-button" data-action="project-edit" data-id="${esc(p.id)}" aria-label="Edit ${esc(p.name)}" title="Edit project">${icon("more")}</button>` : ""}${p.status === "ARCHIVED" ? '<span class="archived-badge">Archived</span>' : ""}</div><h3><button data-action="project-tasks" data-id="${esc(p.id)}">${esc(p.name)}</button></h3><p class="project-description">${esc(p.description || "A little space for your next big idea.")}</p><div class="project-progress"><div class="progress-label"><span>Progress</span><strong>${percent}%</strong></div><progress value="${percent}" max="100" aria-label="${esc(p.name)} completion: ${percent}%"></progress></div><div class="project-footer"><span class="task-count">${icon("tasks")}${p.completedTaskCount} / ${p.taskCount} tasks</span><button class="text-button" data-action="project-tasks" data-id="${esc(p.id)}" aria-label="View ${esc(p.name)} tasks">${icon("arrow")}</button></div></article>`;
}
function activityRows(items) {
  return items
    .map(
      (item) =>
        `<li class="activity-row">${avatar(item.actorName)}<div class="activity-copy"><strong>${esc(item.actorName)}</strong> ${esc(activityAction(item.action))} <span class="entity">${esc(item.entityName || item.entityType?.toLowerCase())}</span><time datetime="${esc(item.createdAt)}">${esc(formatTime(item.createdAt))}</time></div></li>`,
    )
    .join("");
}
function activityAction(action) {
  const labels = {
    CREATED: "created",
    UPDATED: "updated",
    DELETED: "deleted",
    COMMENTED: "commented on",
    ARCHIVED: "archived",
    MEMBER_ADDED: "added",
    MEMBER_REMOVED: "removed",
    MEMBER_ROLE_UPDATED: "changed the role of",
    WORKSPACE_CREATED: "created",
    PROJECT_CREATED: "created",
    PROJECT_UPDATED: "updated",
    TASK_CREATED: "created",
    TASK_UPDATED: "updated",
    TASK_DELETED: "deleted",
    COMMENT_ADDED: "commented on",
    ROLE_CHANGED: "changed the role of",
  };
  return (
    labels[action] ||
    String(action || "updated")
      .toLowerCase()
      .replaceAll("_", " ")
  );
}
function upcomingRow(task) {
  return `<div class="upcoming-row">${writable() ? `<button class="task-check${task.status === "DONE" ? " checked" : ""}" data-action="task-complete" data-id="${esc(task.id)}" aria-label="${task.status === "DONE" ? "Reopen" : "Complete"} ${esc(task.title)}">${task.status === "DONE" ? icon("check") : ""}</button>` : `<span class="status-dot ${statusClass(task.status)}"></span>`}<div class="upcoming-title"><button data-action="task-open" data-id="${esc(task.id)}">${esc(task.title)}</button><small>${esc(task.projectName)} · ${esc(STATUS[task.status])}</small></div>${duePill(task)}${task.assigneeName ? avatar(task.assigneeName, "small-avatar") : ""}</div>`;
}
function renderOverview() {
  const data = state.overview;
  const active = data.projects.filter((p) => p.status === "ACTIVE");
  const first = state.user.name.trim().split(" ")[0];
  const doneRate = data.totalTasks
    ? Math.round((data.completedTasks / data.totalTasks) * 100)
    : 0;
  const stats = [
    [
      "Total tasks",
      data.totalTasks,
      "tasks",
      `${data.projects.length} ${data.projects.length === 1 ? "project" : "projects"} across your workspace`,
    ],
    [
      "In progress",
      data.inProgressTasks,
      "clock",
      "Good things are taking shape",
    ],
    [
      "Completed",
      data.completedTasks,
      "checkCircle",
      `${doneRate}% of all tasks complete`,
    ],
    [
      "Overdue",
      data.overdueTasks,
      "calendar",
      data.overdueTasks
        ? "A little attention goes a long way"
        : "Nothing past its due date",
    ],
  ];
  $("#page-content").innerHTML =
    pageHeader(
      `Hello, ${first}.`,
      "Here’s the big picture. Let’s make a little progress.",
      `<span class="date-chip">${icon("calendar")}${esc(new Date().toLocaleDateString(undefined, { weekday: "short", month: "short", day: "numeric" }))}</span>`,
      "YOUR DAY, IN FOCUS",
    ) +
    `<section class="welcome-banner"><div><h2>Less busywork.<br>More meaningful momentum.</h2><p>Everything your team is moving forward, in one place.</p><button class="text-button" data-action="view-tasks">See what’s happening ${icon("arrow")}</button></div><div class="banner-art" aria-hidden="true"><span class="planet"></span><span class="ring"></span><span class="ring two"></span><span class="moon"></span><span class="star">✦</span><span class="dot"></span></div></section><div class="stats-grid">${stats.map(([label, value, i, note], idx) => `<article class="stat-card"><div class="stat-top"><span>${label}</span><span class="stat-icon">${icon(i)}</span></div><div class="stat-value">${value}</div><div class="stat-note${idx === 3 && value ? " negative" : ""}">${esc(note)}</div></article>`).join("")}</div><div class="dashboard-grid"><div class="dashboard-col"><section><div class="section-header"><h2>Your projects <span>${active.length} active</span></h2><button class="text-button" data-action="view-projects">View all ${icon("arrow")}</button></div>${
      active.length
        ? `<div class="project-grid dashboard-projects">${active
            .slice(0, 4)
            .map((p) => projectCard(p, true))
            .join("")}</div>`
        : `<div class="panel">${empty("Start something good", "A project brings related tasks into focus.", writable() ? "project-create" : "", "Create project")}</div>`
    }</section><section class="dashboard-tasks"><div class="section-header"><h2>Coming up next</h2><button class="text-button" data-action="view-tasks">All tasks ${icon("arrow")}</button></div><div class="panel upcoming-panel">${data.upcomingTasks.length ? data.upcomingTasks.slice(0, 6).map(upcomingRow).join("") : empty("A little breathing room", "Tasks with upcoming due dates will appear here.", "", "", "calendar")}</div></section></div><div class="dashboard-col"><section><div class="section-header"><h2>Team activity</h2><button class="text-button" data-action="view-activity">View all ${icon("arrow")}</button></div><div class="panel activity-panel">${data.recentActivity.length ? `<ol class="activity-feed">${activityRows(data.recentActivity.slice(0, 6))}</ol>` : empty("Your story starts here", "Updates from your team will appear as work moves forward.", "", "", "activity")}</div></section><section class="focus-card"><span class="focus-orbit" aria-hidden="true"></span>${icon("sparkles")}<h3>Good work is a team sport.</h3><p>Keep everyone in the loop, with a little less back and forth.</p><button class="text-button" data-action="view-team">Meet your team ${icon("arrow")}</button></section></div></div><div class="bottom-note"><span>${icon("shield")}Your team’s work, in a secure space.</span><span>A little clarity. A lot of possibility.</span></div>`;
}
function renderProjects() {
  const active = state.projects.filter((p) => p.status === "ACTIVE"),
    archived = state.projects.filter((p) => p.status === "ARCHIVED");
  $("#page-content").innerHTML =
    pageHeader(
      "Ideas, taking shape.",
      "A home for every project your team is moving forward.",
      writable()
        ? `<button class="primary" data-action="project-create">${icon("plus")}New project</button>`
        : "",
      "YOUR PROJECTS",
    ) +
    `<div class="section-header"><h2>Active projects <span>${active.length}</span></h2></div>${active.length ? `<div class="project-grid">${active.map((p) => projectCard(p)).join("")}</div>` : `<div class="panel">${empty("Your next idea belongs here", "Create a project and turn a big idea into a few clear next steps.", writable() ? "project-create" : "", "Create project")}</div>`}${archived.length ? `<section class="dashboard-tasks"><div class="section-header"><h2>Archived <span>${archived.length}</span></h2></div><div class="project-grid">${archived.map((p) => projectCard(p)).join("")}</div></section>` : ""}`;
}
function taskCard(task) {
  return `<article class="task-card"><div class="task-card-head"><span class="project-tag" title="${esc(task.projectName)}">${esc(task.projectName)}</span>${priorityPill(task.priority)}</div><button class="task-card-title" data-action="task-open" data-id="${esc(task.id)}">${esc(task.title)}</button>${task.description ? `<p class="task-card-desc">${esc(task.description)}</p>` : ""}<div class="task-card-footer">${duePill(task)}${task.commentCount ? `<span class="comment-count">${icon("comment")}${task.commentCount}</span>` : ""}${task.assigneeName ? avatar(task.assigneeName, "small-avatar") : '<span class="muted small">Unassigned</span>'}</div>${writable() ? `<label class="sr-only" for="status-${esc(task.id)}">Status of ${esc(task.title)}</label><select class="quick-status" id="status-${esc(task.id)}" data-task-status="${esc(task.id)}">${options(STATUS, task.status)}</select>` : ""}</article>`;
}
function taskTable(items) {
  return `<div class="panel table-scroll"><table class="data-table"><thead><tr><th scope="col">Task</th><th scope="col">Status</th><th scope="col">Priority</th><th scope="col">Assignee</th><th scope="col">Due date</th></tr></thead><tbody>${items.map((t) => `<tr><td><button class="task-name" data-action="task-open" data-id="${esc(t.id)}">${esc(t.title)}</button><span class="task-project">${esc(t.projectName)}</span></td><td><span class="status-label"><span class="status-dot ${statusClass(t.status)}"></span>${esc(STATUS[t.status])}</span></td><td>${priorityPill(t.priority)}</td><td><div class="member-cell">${t.assigneeName ? `${avatar(t.assigneeName, "small-avatar")}${esc(t.assigneeName)}` : '<span class="muted small">Unassigned</span>'}</div></td><td>${duePill(t) || '<span class="muted small">—</span>'}</td></tr>`).join("")}</tbody></table></div>`;
}
function renderTasks() {
  const focusId = document.activeElement?.id;
  const cursor = document.activeElement?.selectionStart;
  const tasks = state.tasks;
  const f = state.filters;
  const filtered = Object.values(f).some(Boolean);
  $("#page-content").innerHTML =
    pageHeader(
      "Good work, in motion.",
      "The next steps, the moving pieces, and everything coming together.",
      `<a class="secondary" href="${base()}/export" download="orbit-tasks.csv">${icon("download")}Export CSV</a>`,
      "YOUR WORKSPACE",
    ) +
    `<div class="toolbar"><div class="filters"><div class="search-field">${icon("search")}<label class="sr-only" for="task-search">Search tasks</label><input id="task-search" type="search" placeholder="Find a task…" maxlength="200" value="${esc(f.q)}" data-filter="q"></div><label class="sr-only" for="filter-project">Project</label><select class="filter-select" id="filter-project" data-filter="projectId"><option value="">All projects</option>${state.projects.map((p) => `<option value="${esc(p.id)}"${p.id === f.projectId ? " selected" : ""}>${esc(p.name)}</option>`).join("")}</select><label class="sr-only" for="filter-status">Status</label><select class="filter-select" id="filter-status" data-filter="status">${options(STATUS, f.status, "All statuses")}</select><label class="sr-only" for="filter-priority">Priority</label><select class="filter-select" id="filter-priority" data-filter="priority">${options(PRIORITY, f.priority, "All priorities")}</select><label class="sr-only" for="filter-assignee">Assignee</label><select class="filter-select" id="filter-assignee" data-filter="assigneeId"><option value="">Everyone</option>${state.members.map((m) => `<option value="${esc(m.id)}"${m.id === f.assigneeId ? " selected" : ""}>${esc(m.name)}</option>`).join("")}</select></div><div class="view-toggle" role="group" aria-label="Task view"><button data-action="task-board" class="${state.taskView === "board" ? "active" : ""}" aria-pressed="${state.taskView === "board"}">${icon("board")}Board</button><button data-action="task-list" class="${state.taskView === "list" ? "active" : ""}" aria-pressed="${state.taskView === "list"}">${icon("list")}List</button></div></div><div class="results-note"><span>${tasks.total} ${tasks.total === 1 ? "task" : "tasks"}${filtered ? " match your filters" : " across your workspace"} · ${tasks.items.length} loaded</span>${filtered ? '<button data-action="filters-clear">Clear filters</button>' : ""}</div>${
      tasks.items.length
        ? state.taskView === "list"
          ? taskTable(tasks.items)
          : `<div class="board">${Object.entries(STATUS)
              .map(([key, label]) => {
                const items = tasks.items.filter((t) => t.status === key);
                return `<section class="board-column" aria-label="${esc(label)} tasks"><div class="board-heading"><span class="status-dot ${statusClass(key)}"></span><h2>${label}</h2><span class="count" title="Loaded tasks in this status">${items.length}</span>${writable() ? `<button class="icon-button" data-action="task-create" data-status="${key}" aria-label="Add ${esc(label.toLowerCase())} task">${icon("plus")}</button>` : ""}</div>${items.length ? items.map(taskCard).join("") : '<div class="column-empty">A little room for what’s next.</div>'}</section>`;
              })
              .join("")}</div>`
        : `<div class="panel">${empty(filtered ? "Nothing here just yet" : "Every project starts with a next step", filtered ? "Try a different search or clear your filters." : "Create your first task and give that idea a little momentum.", !filtered && writable() ? "task-create" : "", "Create a task", "tasks")}</div>`
    }${tasks.items.length < tasks.total ? `<div class="load-more"><button class="secondary" data-action="tasks-more">Load more tasks ${icon("arrow")}</button><span>${tasks.items.length} of ${tasks.total}</span></div>` : ""}`;
  if (focusId && $(`#${CSS.escape(focusId)}`)) {
    const field = $(`#${CSS.escape(focusId)}`);
    field.focus({ preventScroll: true });
    if (cursor !== null && field.type === "search")
      field.setSelectionRange(cursor, cursor);
  }
}
function renderTeam() {
  $("#page-content").innerHTML =
    pageHeader(
      "Better, together.",
      "The people bringing your projects to life.",
      owner()
        ? `<button class="primary" data-action="member-add">${icon("plus")}Add member</button>`
        : "",
      "YOUR TEAM",
    ) +
    `<div class="panel table-scroll"><table class="data-table"><thead><tr><th scope="col">Team member</th><th scope="col">Role</th><th scope="col">Access</th>${owner() ? '<th scope="col">Manage</th>' : ""}</tr></thead><tbody>${state.members.map((m) => `<tr><td><div class="member-cell">${avatar(m.name)}<div><strong>${esc(m.name)}${m.id === state.user.id ? ' <span class="muted small">(you)</span>' : ""}</strong><small>${esc(m.email)}</small></div></div></td><td>${owner() && m.id !== state.user.id ? `<label class="sr-only" for="role-${esc(m.id)}">Role for ${esc(m.name)}</label><select class="role-select" id="role-${esc(m.id)}" data-member-role="${esc(m.id)}">${options({ OWNER: "Owner", MEMBER: "Member", VIEWER: "Viewer" }, m.role)}</select>` : `<span class="role-pill ${m.role.toLowerCase()}">${esc(m.role.toLowerCase())}</span>`}</td><td><span class="small">${m.role === "OWNER" ? "Workspace and team management" : m.role === "MEMBER" ? "Projects, tasks, and comments" : "View projects and tasks"}</span></td>${owner() ? `<td>${m.role !== "OWNER" && m.id !== state.user.id ? `<button class="icon-button" data-action="member-remove" data-id="${esc(m.id)}" aria-label="Remove ${esc(m.name)}">${icon("trash")}</button>` : "—"}</td>` : ""}</tr>`).join("")}</tbody></table></div><div class="team-note">${icon("info")}Add people who already have an Orbit account. Members can edit work; viewers have read access.</div>`;
}
function renderActivity() {
  const a = state.activity;
  $("#page-content").innerHTML =
    pageHeader(
      "A little progress, every day.",
      "A shared record of the work, updates, and decisions in your workspace.",
      "",
      "THE STORY SO FAR",
    ) +
    `<div class="panel activity-full">${a.items.length ? `<ol class="activity-feed">${activityRows(a.items)}</ol>` : empty("Your story starts here", "Your team’s updates will appear as work moves forward.", "", "", "activity")}</div>${a.items.length < a.total ? `<div class="load-more"><button class="secondary" data-action="activity-more">Load older updates ${icon("arrow")}</button><span>${a.items.length} of ${a.total}</span></div>` : ""}`;
}
async function loadMore(type, button) {
  button.disabled = true;
  const data = type === "tasks" ? state.tasks : state.activity;
  const ws = state.workspace.id;
  const page = state.page;
  const params = new URLSearchParams({
    page: String(data.page + 1),
    size: String(data.size),
  });
  if (type === "tasks")
    Object.entries(state.filters).forEach(([key, value]) => {
      if (value) params.set(key, value);
    });
  try {
    const next = await api(`${base()}/${type}?${params}`);
    if (ws !== state.workspace.id || page !== state.page) return;
    const merged = { ...next, items: [...data.items, ...next.items] };
    if (type === "tasks") {
      state.tasks = merged;
      renderTasks();
    } else {
      state.activity = merged;
      renderActivity();
    }
  } catch (error) {
    toast(error.message, true);
    if (button.isConnected) button.disabled = false;
  }
}
function openModal(content) {
  ++state.modalRequest;
  modal.innerHTML = content;
  if (!modal.open) modal.showModal();
  modal
    .querySelector(
      "input:not([disabled]), textarea:not([disabled]), select:not([disabled]), button",
    )
    ?.focus();
}
function modalHead(title, eyebrow = "") {
  return `<div class="modal-head"><div>${eyebrow ? `<div class="eyebrow">${esc(eyebrow)}</div>` : ""}<h2 id="modal-title">${esc(title)}</h2></div><button class="icon-button" data-action="modal-close" aria-label="Close dialog">${icon("close")}</button></div>`;
}
function formError(error, form) {
  const el = $("[data-form-error]", form);
  if (el) {
    el.textContent = error.message;
    el.hidden = false;
    el.focus();
  } else toast(error.message, true);
}
function confirmAction(title, message, label = "Confirm", danger = false) {
  return new Promise((resolve) => {
    confirmModal.innerHTML = `<div class="modal-head"><h2 id="confirm-title">${esc(title)}</h2><button class="icon-button" data-confirm="no" aria-label="Cancel">${icon("close")}</button></div><div class="modal-body"><p class="confirm-text">${esc(message)}</p></div><div class="modal-footer"><button class="secondary" data-confirm="no">Cancel</button><button class="${danger ? "danger-button" : "primary"}" data-confirm="yes">${esc(label)}</button></div>`;
    confirmModal.showModal();
    const handler = (event) => {
      const button = event.target.closest("[data-confirm]");
      if (!button) return;
      finish(button.dataset.confirm === "yes");
    };
    const cancel = (event) => {
      event.preventDefault();
      finish(false);
    };
    const finish = (value) => {
      confirmModal.removeEventListener("click", handler);
      confirmModal.removeEventListener("cancel", cancel);
      confirmModal.close();
      resolve(value);
    };
    confirmModal.addEventListener("click", handler);
    confirmModal.addEventListener("cancel", cancel);
  });
}
function workspaceForm() {
  openModal(
    `${modalHead("A place for your team.", "CREATE WORKSPACE")}<form id="workspace-form"><div class="modal-body"><div class="field"><label for="workspace-name">Workspace name</label><input id="workspace-name" name="name" required minlength="2" maxlength="100" placeholder="e.g. Northstar Studio" autocomplete="organization"><small>Bring your projects and your people together here.</small></div><p class="form-message" data-form-error role="alert" tabindex="-1" hidden></p></div><div class="modal-footer"><button class="secondary" type="button" data-action="modal-close">Cancel</button><button class="primary" type="submit">Create workspace ${icon("arrow")}</button></div></form>`,
  );
  $("#workspace-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    setBusy(form, true);
    try {
      const workspace = await api("/api/workspaces", {
        method: "POST",
        body: { name: new FormData(form).get("name").trim() },
      });
      state.workspace = workspace;
      state.workspaces = [...state.workspaces, workspace];
      state.filters = {
        q: "",
        status: "",
        priority: "",
        projectId: "",
        assigneeId: "",
      };
      state.tasks = null;
      state.projects = [];
      state.members = [];
      rememberWorkspace();
      writeRoute();
      shell();
      modal.close();
      toast("Your workspace is ready.");
      await loadWorkspace();
    } catch (error) {
      formError(error, form);
      setBusy(form, false);
    }
  });
}
function projectForm(id) {
  const project = state.projects.find((p) => p.id === id);
  if (!writable()) return;
  const p = project || { name: "", description: "", color: COLORS.purple };
  openModal(
    `${modalHead(project ? "A little shape. A little focus." : "Make room for your next idea.", project ? "EDIT PROJECT" : "NEW PROJECT")}<form id="project-form"><div class="modal-body"><div class="form-grid"><div class="field full"><label for="project-name">Project name</label><input id="project-name" name="name" required maxlength="120" value="${esc(p.name)}" placeholder="e.g. Website refresh"></div><div class="field full"><label for="project-description">What’s the goal?</label><textarea id="project-description" name="description" maxlength="2000" placeholder="A little context goes a long way…">${esc(p.description)}</textarea></div><fieldset class="field full color-field"><legend class="small muted">Project color</legend><div class="color-options">${Object.entries(
      COLORS,
    )
      .map(
        ([name, value]) =>
          `<label class="color-option" title="${name}"><input type="radio" name="color" value="${value}"${p.color.toLowerCase() === value ? " checked" : ""} aria-label="${name}"><span class="color-swatch ${name}"></span></label>`,
      )
      .join(
        "",
      )}${!Object.values(COLORS).includes(p.color.toLowerCase()) ? `<label class="color-option" title="Keep current color"><input type="radio" name="color" value="${esc(p.color)}" checked aria-label="Keep current color"><span class="color-swatch ${palette(p.color)}"></span></label>` : ""}</div></fieldset><p class="form-message" data-form-error role="alert" tabindex="-1" hidden></p></div></div><div class="modal-footer">${project ? `<button class="secondary" type="button" data-action="project-archive" data-id="${esc(project.id)}">${icon("archive")}${project.status === "ARCHIVED" ? "Restore" : "Archive"}</button>` : ""}<button class="secondary" type="button" data-action="modal-close">Cancel</button><button class="primary" type="submit">${project ? "Save changes" : "Create project"} ${icon("arrow")}</button></div></form>`,
  );
  $("#project-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form));
    values.name = values.name.trim();
    setBusy(form, true);
    try {
      await api(`${base()}/projects${project ? `/${project.id}` : ""}`, {
        method: project ? "PATCH" : "POST",
        body: project
          ? { ...values, status: project.status, version: project.version }
          : values,
      });
      modal.close();
      toast(project ? "Project updated." : "A new project, a fresh start.");
      await loadWorkspace();
    } catch (error) {
      if (error.status === 409) {
        state.projects = await api(`${base()}/projects`);
        formError(
          new Error(
            "This project changed since you opened it. Close and reopen it to review the latest changes.",
          ),
          form,
        );
      } else formError(error, form);
      setBusy(form, false);
    }
  });
}
async function archiveProject(id) {
  const project = state.projects.find((p) => p.id === id);
  const archived = project.status === "ACTIVE";
  if (
    !(await confirmAction(
      archived ? "Archive this project?" : "Restore this project?",
      archived
        ? "The project and its tasks stay in your workspace. You can restore it at any time."
        : "This project will appear with your active projects again.",
      archived ? "Archive project" : "Restore project",
    ))
  )
    return;
  try {
    await api(`${base()}/projects/${id}`, {
      method: "PATCH",
      body: {
        name: project.name,
        description: project.description,
        color: project.color,
        status: archived ? "ARCHIVED" : "ACTIVE",
        version: project.version,
      },
    });
    modal.close();
    toast(archived ? "Project archived." : "Project restored.");
    await loadWorkspace();
  } catch (error) {
    toast(error.message, true);
  }
}
async function openTask(id, updateLink = true) {
  if (updateLink) writeRoute(state.page, id, false);
  openModal(
    `${modalHead("Finding your task…")}<div class="loading-state" role="status"><span class="spinner"></span>Loading the latest details</div>`,
  );
  const ws = state.workspace.id;
  const request = state.modalRequest;
  try {
    const task = await api(`${base()}/tasks/${encodeURIComponent(id)}`);
    if (
      !modal.open ||
      state.workspace.id !== ws ||
      request !== state.modalRequest
    )
      return;
    taskForm(task);
  } catch (error) {
    if (modal.open && request === state.modalRequest) {
      openModal(
        `${modalHead("A small interruption.")}<div class="modal-body"><div class="inline-error" role="alert">${icon("alert")}${esc(error.message)}</div></div>`,
      );
    }
  }
}
async function copyTaskLink() {
  if (!state.taskModal || !state.workspace) return;
  const params = new URLSearchParams({
    workspace: state.workspace.id,
    task: state.taskModal.id,
  });
  const url = `${location.origin}${location.pathname}#tasks?${params}`;
  try {
    await navigator.clipboard.writeText(url);
    toast("Task link copied.");
  } catch {
    toast("Copy the task link from your browser’s address bar.", true);
  }
}
function taskForm(task = null, status = "TODO") {
  if (!task && !writable()) return;
  const t = task || {
    title: "",
    description: "",
    projectId:
      state.filters.projectId ||
      state.projects.find((p) => p.status === "ACTIVE")?.id ||
      "",
    status,
    priority: "MEDIUM",
    assigneeId: null,
    dueDate: null,
  };
  state.taskModal = task;
  const canEdit = writable();
  const disabled = canEdit ? "" : " disabled";
  openModal(
    `${modalHead(task ? "The details that move work forward." : "A clear next step.", task ? "TASK DETAILS" : "NEW TASK")}<form id="task-form"><div class="modal-body">${!canEdit ? '<div class="readonly-note">You have view access to this workspace.</div>' : ""}${!task && !state.projects.some((p) => p.status === "ACTIVE") ? '<div class="readonly-note">Create an active project before adding a task.</div>' : ""}<div class="form-grid"><div class="field full"><label for="task-title">Task name</label><input id="task-title" name="title" required maxlength="200" value="${esc(t.title)}" placeholder="What needs to happen?"${disabled}></div><div class="field full"><label for="task-description">Description</label><textarea id="task-description" name="description" maxlength="10000" placeholder="Add the context your team needs…"${disabled}>${esc(t.description)}</textarea></div><div class="field full"><label for="task-project">Project</label><select id="task-project" name="projectId" required${disabled}>${projectOptions(t.projectId)}</select></div><div class="field"><label for="task-status">Status</label><select id="task-status" name="status"${disabled}>${options(STATUS, t.status)}</select></div><div class="field"><label for="task-priority">Priority</label><select id="task-priority" name="priority"${disabled}>${options(PRIORITY, t.priority)}</select></div><div class="field"><label for="task-assignee">Assignee</label><select id="task-assignee" name="assigneeId"${disabled}>${memberOptions(t.assigneeId)}</select></div><div class="field"><label for="task-due">Due date</label><input id="task-due" name="dueDate" type="date" value="${esc(t.dueDate || "")}"${disabled}></div><p class="form-message" data-form-error role="alert" tabindex="-1" hidden></p></div></div><div class="modal-footer">${task && canEdit ? `<button class="danger-button" type="button" data-action="task-delete" data-id="${esc(task.id)}">${icon("trash")}Delete</button>` : ""}${task ? `<button class="secondary" type="button" data-action="task-copy-link">${icon("link")}Copy link</button>` : ""}<button class="secondary" type="button" data-action="modal-close">${canEdit ? "Cancel" : "Close"}</button>${canEdit ? `<button class="primary" type="submit">${task ? "Save changes" : "Create task"} ${icon("arrow")}</button>` : ""}</div></form>${task ? `<section class="modal-body comment-section"><h3>Conversation <span id="comment-total"></span></h3><div id="comments"><div class="loading-state" role="status"><span class="spinner"></span>Loading conversation</div></div>${canEdit ? `<form id="comment-form" class="comment-form"><label class="sr-only" for="comment-body">Add a comment</label><textarea id="comment-body" name="body" required maxlength="4000" placeholder="Add an update, a question, or a little context…"></textarea><button class="primary" type="submit">${icon("comment")}Comment</button></form><p id="comment-error" class="form-message" role="alert" hidden></p>` : ""}</section>` : ""}`,
  );
  $("#task-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!canEdit) return;
    const form = event.currentTarget;
    const body = Object.fromEntries(new FormData(form));
    body.title = body.title.trim();
    body.assigneeId = body.assigneeId || null;
    body.dueDate = body.dueDate || null;
    if (task) body.version = task.version;
    setBusy(form, true);
    try {
      await api(`${base()}/tasks${task ? `/${task.id}` : ""}`, {
        method: task ? "PATCH" : "POST",
        body,
      });
      modal.close();
      toast(task ? "Your changes are saved." : "Your next step is ready.");
      await loadWorkspace();
    } catch (error) {
      if (error.status === 409) {
        formError(
          new Error(
            "This task changed while you were editing. Your input is still here. Close and reopen the task to review the latest version before saving.",
          ),
          form,
        );
      } else formError(error, form);
      setBusy(form, false);
    }
  });
  if (task) {
    loadComments(task.id);
    $("#comment-form")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const body = new FormData(form).get("body").trim();
      if (!body) {
        $("#comment-error").textContent =
          "Add a little context before sending your comment.";
        $("#comment-error").hidden = false;
        return;
      }
      setBusy(form, true);
      $("#comment-error").hidden = true;
      try {
        await api(`${base()}/tasks/${task.id}/comments`, {
          method: "POST",
          body: { body },
        });
        form.reset();
        await loadComments(task.id);
        toast("Comment added.");
      } catch (error) {
        $("#comment-error").textContent = error.message;
        $("#comment-error").hidden = false;
      } finally {
        if (form.isConnected) setBusy(form, false);
      }
    });
  }
}
async function loadComments(id) {
  try {
    const comments = await api(`${base()}/tasks/${id}/comments`);
    if (state.taskModal?.id !== id || !$("#comments")) return;
    $("#comment-total").textContent = `(${comments.length})`;
    $("#comments").innerHTML = comments.length
      ? comments
          .map(
            (c) =>
              `<article class="comment-row">${avatar(c.authorName)}<div><div class="comment-meta"><strong>${esc(c.authorName)}</strong><time datetime="${esc(c.createdAt)}">${esc(formatTime(c.createdAt))}</time></div><p class="comment-body">${esc(c.body)}</p></div></article>`,
          )
          .join("")
      : '<p class="small muted">Start the conversation. A little context helps everyone.</p>';
  } catch (error) {
    if (state.taskModal?.id === id && $("#comments"))
      $("#comments").innerHTML =
        `<p class="form-message" role="alert">${esc(error.message)}</p>`;
  }
}
async function deleteTask(id) {
  const task = state.taskModal;
  if (!task || task.id !== id) return;
  if (
    !(await confirmAction(
      "Delete this task?",
      `“${task.title}” and its comments will be permanently removed.`,
      "Delete task",
      true,
    ))
  )
    return;
  try {
    await api(`${base()}/tasks/${id}?version=${task.version}`, {
      method: "DELETE",
    });
    modal.close();
    toast("Task deleted.");
    await loadWorkspace();
  } catch (error) {
    toast(
      error.status === 409
        ? "This task changed. Close and reopen it before deleting."
        : error.message,
      true,
    );
  }
}
function editableTask(task) {
  const {
    title,
    description,
    projectId,
    status,
    priority,
    assigneeId,
    dueDate,
    version,
  } = task;
  return {
    title,
    description,
    projectId,
    status,
    priority,
    assigneeId: assigneeId || null,
    dueDate: dueDate || null,
    version,
  };
}
async function changeTaskStatus(id, status, element) {
  const known =
    state.tasks?.items.find((t) => t.id === id) ||
    state.overview?.upcomingTasks.find((t) => t.id === id);
  if (!known) return;
  if (element) element.disabled = true;
  try {
    await api(`${base()}/tasks/${id}`, {
      method: "PATCH",
      body: {
        ...editableTask(known),
        status: status || (known.status === "DONE" ? "TODO" : "DONE"),
      },
    });
    toast(
      status
        ? "Task status updated."
        : known.status === "DONE"
          ? "Task reopened."
          : "One more thing, done.",
    );
    await loadWorkspace();
  } catch (error) {
    toast(
      error.status === 409
        ? "Someone updated this task. Review the latest version and try again."
        : error.message,
      true,
    );
    await loadWorkspace();
  }
}
function memberForm() {
  if (!owner()) return;
  openModal(
    `${modalHead("Bring someone into the picture.", "ADD TEAM MEMBER")}<form id="member-form"><div class="modal-body"><div class="form-grid"><div class="field full"><label for="member-email">Their Orbit account email</label><input id="member-email" name="email" type="email" required maxlength="254" placeholder="teammate@yourteam.com"><small>They need to create an Orbit account first. No email invitation is sent.</small></div><div class="field full"><label for="member-role">Workspace role</label><select id="member-role" name="role">${options({ MEMBER: "Member — edit projects, tasks, and comments", VIEWER: "Viewer — read access only" }, "MEMBER")}</select></div><p class="form-message" data-form-error role="alert" tabindex="-1" hidden></p></div></div><div class="modal-footer"><button class="secondary" type="button" data-action="modal-close">Cancel</button><button class="primary" type="submit">Add to workspace ${icon("arrow")}</button></div></form>`,
  );
  $("#member-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    setBusy(form, true);
    try {
      await api(`${base()}/members`, {
        method: "POST",
        body: Object.fromEntries(new FormData(form)),
      });
      modal.close();
      toast("A new teammate, a little more possibility.");
      await loadWorkspace();
    } catch (error) {
      formError(error, form);
      setBusy(form, false);
    }
  });
}
async function changeMemberRole(id, role, element) {
  const member = state.members.find((m) => m.id === id);
  element.disabled = true;
  try {
    await api(`${base()}/members/${id}`, { method: "PATCH", body: { role } });
    toast(`${member.name} now has ${role.toLowerCase()} access.`);
    await loadWorkspace();
  } catch (error) {
    toast(error.message, true);
    element.value = member.role;
    element.disabled = false;
  }
}
async function removeMember(id) {
  const member = state.members.find((m) => m.id === id);
  if (
    !(await confirmAction(
      "Remove this teammate?",
      `${member.name} will lose access to this workspace. Their account stays active.`,
      "Remove member",
      true,
    ))
  )
    return;
  try {
    await api(`${base()}/members/${id}`, { method: "DELETE" });
    toast("Member removed from the workspace.");
    await loadWorkspace();
  } catch (error) {
    toast(error.message, true);
  }
}
function renderAccount() {
  const a = state.account;
  $("#page-content").innerHTML =
    pageHeader(
      "Your space. Your details.",
      "Keep your account up to date and your access secure.",
      "",
      "YOUR ACCOUNT",
    ) +
    `<div class="settings-grid"><section class="panel settings-card"><div class="settings-heading">${icon("people")}<div><h2>A familiar face.</h2><p>Choose the name your teammates see.</p></div></div><form id="profile-form" class="settings-form"><div class="field"><label for="profile-name">Display name</label><input id="profile-name" name="name" required maxlength="100" value="${esc(a.name)}" autocomplete="name"></div><div class="field"><span class="field-label">Email address</span><p class="account-email">${icon("mail")}<span>${esc(a.email)}</span></p><small>Your account email stays with this identity.</small></div><p class="form-message" data-form-error role="alert" tabindex="-1" hidden></p><button class="primary" type="submit">Save profile ${icon("check")}</button></form></section><section class="panel settings-card"><div class="settings-heading">${icon("shield")}<div><h2>A little account protection.</h2><p>${a.emailVerified ? "Your email address is confirmed." : "Verify your email to protect access to your account."}</p></div></div><div class="verification-state ${a.emailVerified ? "verified" : ""}">${icon(a.emailVerified ? "checkCircle" : "mail")}<span>${a.emailVerified ? "Email verified" : "Email not verified"}</span></div>${!a.emailVerified ? (state.mailEnabled ? '<button class="secondary" data-action="account-verify">Send verification email</button><p id="verification-status" class="small muted" role="status"></p>' : '<p class="small muted">Account emails are currently unavailable. Ask an administrator to enable email delivery.</p>') : ""}<p class="security-note">Your password is protected. Changing it signs you out on every device.</p></section><section class="panel settings-card full-card"><div class="settings-heading">${icon("shield")}<div><h2>A stronger next chapter.</h2><p>Update your password and keep your account yours.</p></div></div><form id="password-form" class="settings-form"><div class="form-grid"><div class="field full"><label for="current-password">Current password</label><input id="current-password" name="currentPassword" type="password" required maxlength="72" autocomplete="current-password"></div><div class="field"><label for="new-password">New password</label><input id="new-password" name="newPassword" type="password" required minlength="12" maxlength="72" autocomplete="new-password"><small>Use 12–72 characters. A memorable phrase works well.</small></div><div class="field"><label for="confirm-password">Confirm new password</label><input id="confirm-password" name="confirmation" type="password" required minlength="12" maxlength="72" autocomplete="new-password"></div><p class="form-message" data-form-error role="alert" tabindex="-1" hidden></p></div><button class="primary" type="submit">Update password ${icon("shield")}</button></form></section></div>`;
  $("#profile-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    setBusy(form, true);
    $("[data-form-error]", form).hidden = true;
    try {
      state.account = await api("/api/account", {
        method: "PATCH",
        body: { name: new FormData(form).get("name").trim() },
      });
      state.user = { ...state.user, ...state.account };
      shell();
      if (state.workspace) await loadWorkspace();
      else await loadPage();
      toast("Your profile is up to date.");
    } catch (error) {
      formError(error, form);
      if (form.isConnected) setBusy(form, false);
    }
  });
  $("#password-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form));
    setBusy(form, true);
    $("[data-form-error]", form).hidden = true;
    try {
      validatePassword(values.newPassword);
      if (values.newPassword !== values.confirmation)
        throw new Error("The new passwords need to match.");
      await api("/api/account/password", {
        method: "POST",
        body: {
          currentPassword: values.currentPassword,
          newPassword: values.newPassword,
        },
      });
      await signedOut(
        "Your password is updated. Sign in again with the new one.",
      );
    } catch (error) {
      formError(error, form);
      if (form.isConnected) setBusy(form, false);
    }
  });
  $(".settings-grid").insertAdjacentHTML(
    "beforeend",
    `<section class="panel settings-card full-card"><div class="settings-heading">${icon("shield")}<div><h2>Keep your access in view.</h2><p>Recent active sessions. End any session that no longer needs access.</p></div></div><div class="session-list" role="list">${state.sessions.map((s) => `<div class="session-row" role="listitem" data-current="${Boolean(s.current)}"><div class="session-symbol">${icon("shield")}</div><div class="session-copy"><h3>${s.current ? "Current session" : "Signed-in session"}${s.current ? '<span class="role-pill">This browser</span>' : ""}</h3><p>Last active ${esc(formatTime(s.lastAccessedAt))}</p><small>Started ${esc(formatTime(s.createdAt))} · Expires ${esc(formatTime(s.expiresAt))}</small></div><button class="secondary" data-action="session-revoke" data-id="${esc(s.id)}">End session</button></div>`).join("")}</div>${state.sessions.length === 100 ? '<p class="small muted">Showing up to 100 recent active sessions.</p>' : ""}</section>`,
  );
}
async function revokeSession(id) {
  const session = state.sessions.find((s) => s.id === id);
  if (!session) return;
  if (
    !(await confirmAction(
      "End this session?",
      session.current
        ? "This signs you out of the current browser. You can sign in again at any time."
        : "This session will need to sign in again before accessing your account.",
      "End session",
    ))
  )
    return;
  try {
    await api(`/api/account/sessions/${encodeURIComponent(id)}`, {
      method: "DELETE",
    });
    if (session.current) await signedOut("This session has ended.");
    else {
      await loadPage();
      toast("Session ended.");
    }
  } catch (error) {
    toast(error.message, true);
  }
}
async function requestAccountVerification(button) {
  button.disabled = true;
  try {
    const result = await api("/api/account/verification", { method: "POST" });
    $("#verification-status").textContent =
      `${result.message} Check your inbox; another link can be sent after a minute.`;
    toast("Check your inbox for your verification link.");
  } catch (error) {
    toast(error.message, true);
  } finally {
    if (button.isConnected) button.disabled = false;
  }
}
function renderSettings() {
  const settings = state.workspaceSettings;
  const canManage = settings.role === "OWNER";
  state.workspace = {
    ...state.workspace,
    name: settings.name,
    role: settings.role,
  };
  const pending = state.invitations.filter((i) => i.status === "PENDING");
  $("#page-content").innerHTML =
    pageHeader(
      "A space that fits your team.",
      "A little care for the workspace you’re building together.",
      "",
      "WORKSPACE SETTINGS",
    ) +
    `<div class="settings-grid"><section class="panel settings-card"><div class="settings-heading">${icon("folder")}<div><h2>Your shared space.</h2><p>${canManage ? "Give your workspace a name that feels like home." : "Workspace owners manage the shared name and invitations."}</p></div></div><form id="settings-form" class="settings-form"><div class="field"><label for="settings-name">Workspace name</label><input id="settings-name" name="name" required maxlength="100" value="${esc(settings.name)}"${canManage ? "" : " disabled"}></div><p class="small muted">Created ${esc(formatDate(settings.createdAt))} · ${esc(settings.role.toLowerCase())} access</p><p class="form-message" data-form-error role="alert" tabindex="-1" hidden></p>${canManage ? `<button class="primary" type="submit">Save workspace ${icon("check")}</button>` : ""}</form></section><section class="panel settings-card"><div class="settings-heading">${icon("people")}<div><h2>Make room for your people.</h2><p>${canManage ? "Invite a teammate by email. They can join after signing in or creating an account." : "Your workspace owners can invite new teammates."}</p></div></div>${canManage && state.mailEnabled ? `<form id="invitation-form" class="settings-form"><div class="field"><label for="invitation-email">Teammate email</label><input id="invitation-email" name="email" type="email" required maxlength="254" autocomplete="email" placeholder="teammate@yourteam.com"></div><div class="field"><label for="invitation-role">Workspace access</label><select id="invitation-role" name="role">${options({ MEMBER: "Member — can edit work", VIEWER: "Viewer — can read work" }, "MEMBER")}</select></div><p class="form-message" data-form-error role="alert" tabindex="-1" hidden></p><button class="primary" type="submit">Send invitation ${icon("mail")}</button></form>` : canManage ? '<p class="small muted">Invitations need email delivery. Ask an administrator to enable it. You can directly add an existing registered account from Team.</p><button class="secondary" data-action="view-team">Manage team</button>' : '<button class="secondary" data-action="view-team">View team</button>'}</section>${canManage ? `<section class="panel settings-card full-card"><div class="section-header"><h2>Invitations <span>${pending.length} pending</span></h2></div>${state.invitations.length ? `<div class="table-scroll"><table class="data-table invitation-table"><thead><tr><th scope="col">Email</th><th scope="col">Access</th><th scope="col">Status</th><th scope="col">Expires</th><th scope="col">Manage</th></tr></thead><tbody>${state.invitations.map((i) => `<tr><td>${esc(i.email)}</td><td>${esc(i.role.toLowerCase())}</td><td><span class="role-pill">${esc(i.status.toLowerCase())}</span></td><td>${esc(formatDate(i.expiresAt))}</td><td>${i.status === "PENDING" ? `<button class="icon-button" data-action="invitation-revoke" data-id="${esc(i.id)}" aria-label="Revoke invitation for ${esc(i.email)}">${icon("close")}</button>` : "—"}</td></tr>`).join("")}</tbody></table></div>` : '<p class="small muted">Invitations appear here when you invite someone into the picture.</p>'}</section>` : ""}</div>`;
  $("#settings-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!canManage) return;
    const form = event.currentTarget;
    setBusy(form, true);
    $("[data-form-error]", form).hidden = true;
    try {
      state.workspaceSettings = await api(`${base()}/settings`, {
        method: "PATCH",
        body: {
          name: new FormData(form).get("name").trim(),
          version: settings.version,
        },
      });
      state.workspace = { ...state.workspace, ...state.workspaceSettings };
      state.workspaces = state.workspaces.map((w) =>
        w.id === state.workspace.id ? { ...w, name: state.workspace.name } : w,
      );
      shell();
      await loadPage();
      toast("Your workspace has a fresh name.");
    } catch (error) {
      formError(
        error.status === 409
          ? new Error(
              "This workspace changed while you were editing. Refresh its settings before saving again.",
            )
          : error,
        form,
      );
      if (form.isConnected) setBusy(form, false);
    }
  });
  $("#invitation-form")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    setBusy(form, true);
    $("[data-form-error]", form).hidden = true;
    try {
      await api(`${base()}/invitations`, {
        method: "POST",
        body: Object.fromEntries(new FormData(form)),
      });
      toast("Invitation queued for email delivery.");
      await loadPage();
    } catch (error) {
      formError(error, form);
      if (form.isConnected) setBusy(form, false);
    }
  });
}
async function revokeInvitation(id) {
  const invitation = state.invitations.find((i) => i.id === id);
  if (
    !invitation ||
    !(await confirmAction(
      "Revoke this invitation?",
      `The invitation for ${invitation.email} will no longer work.`,
      "Revoke invitation",
      true,
    ))
  )
    return;
  try {
    await api(`${base()}/invitations/${encodeURIComponent(id)}`, {
      method: "DELETE",
    });
    toast("Invitation revoked.");
    await loadPage();
  } catch (error) {
    toast(error.message, true);
  }
}
function updateUnreadBadge() {
  const badge = $("#notification-count");
  if (!badge) return;
  badge.textContent =
    state.unreadNotifications > 99 ? "99+" : String(state.unreadNotifications);
  badge.hidden = state.unreadNotifications === 0;
  $("#notification-button")?.setAttribute(
    "aria-label",
    state.unreadNotifications
      ? `Inbox, ${state.unreadNotifications} unread notifications`
      : "Inbox",
  );
}
async function refreshUnread() {
  if (!state.user) return;
  try {
    const data = await api("/api/notifications?page=0&size=1");
    if (!state.user) return;
    state.unreadNotifications = data.unreadCount;
    updateUnreadBadge();
  } catch {
    /* The inbox itself reports request errors when opened. */
  }
}
function renderNotifications() {
  const data = state.notifications;
  $("#page-content").innerHTML =
    pageHeader(
      "The updates that matter.",
      "Assignments, conversations, and workspace access, in one thoughtful inbox.",
      data.unreadCount
        ? `<button class="secondary" data-action="notifications-read-all">${icon("checkCircle")}Mark all as read</button>`
        : "",
      "YOUR INBOX",
    ) +
    `<div class="results-note"><span>${data.unreadCount} unread · ${data.total} total updates</span><button data-action="notifications-refresh">Refresh inbox</button></div><div class="panel notification-list">${data.items.length ? data.items.map((n) => `<article class="notification-row ${n.read ? "" : "unread"}"><div class="notification-symbol">${icon(n.type === "TASK_ASSIGNED" || n.type === "ASSIGNED" ? "tasks" : "comment")}</div><div class="notification-copy"><button class="notification-title" data-action="notification-open" data-id="${esc(n.id)}">${esc(n.title)}</button><p>${esc(n.body)}</p><div class="notification-meta"><span>${esc(n.workspaceName)}</span><time datetime="${esc(n.createdAt)}">${esc(formatTime(n.createdAt))}</time>${!n.read ? '<span class="unread-label">Unread</span>' : ""}</div></div>${!n.read ? `<button class="icon-button" data-action="notification-read" data-id="${esc(n.id)}" aria-label="Mark ${esc(n.title)} as read">${icon("check")}</button>` : ""}</article>`).join("") : empty("A little peace and quiet.", "Assignments, conversation updates, and changes to workspace access will appear here.", "", "", "bell")}</div>${data.items.length < data.total ? `<div class="load-more"><button class="secondary" data-action="notifications-more">Load older notifications ${icon("arrow")}</button><span>${data.items.length} of ${data.total}</span></div>` : ""}`;
}
async function readNotification(id, rerender = true) {
  const item = state.notifications?.items.find((n) => n.id === id);
  if (!item || item.read) return;
  await api(`/api/notifications/${encodeURIComponent(id)}/read`, {
    method: "PATCH",
  });
  item.read = true;
  state.notifications.unreadCount = Math.max(
    0,
    state.notifications.unreadCount - 1,
  );
  state.unreadNotifications = state.notifications.unreadCount;
  if (rerender && state.page === "notifications") renderNotifications();
  updateUnreadBadge();
}
async function openNotification(id) {
  const item = state.notifications?.items.find((n) => n.id === id);
  if (!item) return;
  try {
    await readNotification(id, false);
    state.workspaces = await api("/api/workspaces");
    const workspace = state.workspaces.find((w) => w.id === item.workspaceId);
    if (!workspace)
      throw new Error("You no longer have access to this workspace.");
    state.workspace = workspace;
    state.filters = {
      q: "",
      status: "",
      priority: "",
      projectId: "",
      assigneeId: "",
    };
    state.page = item.taskId ? "tasks" : "overview";
    rememberWorkspace();
    writeRoute(state.page, item.taskId);
    shell();
    await loadWorkspace();
    if (item.taskId) await openTask(item.taskId, false);
  } catch (error) {
    toast(error.message, true);
    if (state.page === "notifications") renderNotifications();
  }
}
async function loadMoreNotifications(button) {
  button.disabled = true;
  const current = state.notifications;
  try {
    const data = await api(
      `/api/notifications?page=${current.page + 1}&size=${current.size}`,
    );
    if (state.page !== "notifications") return;
    state.notifications = { ...data, items: [...current.items, ...data.items] };
    state.unreadNotifications = data.unreadCount;
    renderNotifications();
    updateUnreadBadge();
  } catch (error) {
    toast(error.message, true);
    if (button.isConnected) button.disabled = false;
  }
}
async function invitationScreen(token) {
  modal.close();
  if (!token) {
    app.innerHTML = authLayout(
      '<div class="eyebrow">A small interruption</div><h2>This invitation is incomplete.</h2><p>Ask the workspace owner for a new invitation.</p><button class="primary" data-action="auth-continue">Continue to Orbit</button>',
    );
    return;
  }
  app.innerHTML = authLayout(
    '<div class="eyebrow">A place for you</div><h2>Finding your invitation…</h2><div class="loading-state" role="status"><span class="spinner"></span>Checking the latest details</div>',
  );
  try {
    const preview = await api(
      `/api/invitations/preview?token=${encodeURIComponent(token)}`,
    );
    state.pendingInvite = { token, preview };
    const matches =
      state.user &&
      state.user.email.toLowerCase() === preview.email.toLowerCase();
    app.innerHTML = authLayout(
      `<div class="eyebrow">Great work starts together</div><h2>You’re invited to ${esc(preview.workspaceName)}.</h2><p>A workspace invitation for ${esc(preview.email)}.</p><div class="invitation-summary"><span class="role-pill">${esc(preview.role.toLowerCase())} access</span><span class="small muted">Expires ${esc(formatDate(preview.expiresAt))}</span></div>${matches ? '<form id="accept-invitation-form" class="auth-form"><div class="auth-error" data-form-error role="alert" tabindex="-1" hidden></div><button class="primary" type="submit">Join workspace ' + icon("arrow") + "</button></form>" : state.user ? `<div class="auth-error">You’re signed in as ${esc(state.user.email)}. Use the invited account to accept.</div><button class="primary auth-wide" data-action="invitation-sign-out">Sign out and switch account ${icon("logout")}</button>` : `<div class="auth-form"><button class="primary" data-action="auth-login">Sign in to accept ${icon("arrow")}</button>${state.registrationEnabled ? '<button class="secondary" data-action="auth-register">Create an account</button>' : ""}</div>`}<button class="text-button" data-action="invitation-dismiss">Maybe later</button>`,
    );
    $("#accept-invitation-form")?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      setBusy(form, true);
      try {
        const workspace = await api("/api/invitations/accept", {
          method: "POST",
          body: { token },
        });
        state.workspace = workspace;
        state.pendingInvite = null;
        clearTokenRoute();
        rememberWorkspace();
        await loadWorkspaces();
        toast(`Welcome to ${workspace.name}.`);
      } catch (error) {
        formError(error, form);
        if (form.isConnected) setBusy(form, false);
      }
    });
  } catch (error) {
    app.innerHTML = authLayout(
      `<div class="eyebrow">A small interruption</div><h2>This invitation needs another try.</h2><p>${esc(error.message)}</p><p>Ask the workspace owner for a fresh invitation.</p><button class="primary" data-action="invitation-dismiss">Continue to Orbit ${icon("arrow")}</button>`,
    );
  }
}
async function signedOut(message, preserveRoute = false) {
  state.request++;
  state.user = null;
  state.account = null;
  state.workspace = null;
  state.workspaces = [];
  state.csrf = null;
  state.notifications = null;
  state.unreadNotifications = 0;
  if (!preserveRoute) {
    state.pendingInvite = null;
    clearTokenRoute();
  }
  await csrf();
  if (preserveRoute && (await specialRoute())) return;
  authView();
  document.title = "Orbit · Work in focus";
  if (message) toast(message);
}

async function logout() {
  try {
    await api("/api/auth/logout", { method: "POST" });
    await signedOut("You’re signed out. See you soon.");
  } catch (error) {
    toast(error.message, true);
  }
}
document.addEventListener("click", async (event) => {
  const target = event.target.closest("[data-action]");
  if (!target) return;
  const action = target.dataset.action;
  const id = target.dataset.id;
  if (action === "auth-login") authView("login");
  if (action === "auth-register") authView("register");
  if (action === "forgot-password")
    recoveryForm("forgot-password", $("#auth-email")?.value || "");
  if (action === "resend-verification")
    recoveryForm(
      "resend-verification",
      target.dataset.email || $("#auth-email")?.value || "",
    );
  if (action === "auth-continue") {
    clearTokenRoute();
    if (state.user) loadWorkspaces();
    else authView();
  }
  if (action === "invitation-dismiss") {
    state.pendingInvite = null;
    clearTokenRoute();
    if (state.user) loadWorkspaces();
    else authView();
  }
  if (action === "invitation-sign-out") {
    try {
      await api("/api/auth/logout", { method: "POST" });
      await signedOut("", true);
    } catch (error) {
      toast(error.message, true);
    }
  }
  if (action === "account-verify") requestAccountVerification(target);
  if (action === "session-revoke") revokeSession(id);
  if (action === "invitation-revoke") revokeInvitation(id);
  if (action === "notification-open") openNotification(id);
  if (action === "notification-read") {
    try {
      await readNotification(id);
    } catch (error) {
      toast(error.message, true);
    }
  }
  if (action === "notifications-read-all") {
    target.disabled = true;
    try {
      await api("/api/notifications/read-all", { method: "POST" });
      await loadPage();
    } catch (error) {
      toast(error.message, true);
      if (target.isConnected) target.disabled = false;
    }
  }
  if (action === "notifications-more") loadMoreNotifications(target);
  if (action === "notifications-refresh") loadPage();
  if (action === "demo") demoLogin();
  if (action === "logout") logout();
  if (action === "modal-close") {
    modal.close();
    state.taskModal = null;
  }
  if (action === "workspace-create") workspaceForm();
  if (action === "project-create") projectForm();
  if (action === "project-edit") projectForm(id);
  if (action === "project-archive") archiveProject(id);
  if (action === "project-tasks") {
    state.filters = {
      q: "",
      status: "",
      priority: "",
      projectId: id,
      assigneeId: "",
    };
    navigate("tasks");
  }
  if (action === "task-create") taskForm(null, target.dataset.status || "TODO");
  if (action === "task-open") openTask(id);
  if (action === "task-copy-link") copyTaskLink();
  if (action === "task-delete") deleteTask(id);
  if (action === "task-complete") changeTaskStatus(id, null, target);
  if (action === "task-board") {
    state.taskView = "board";
    renderTasks();
  }
  if (action === "task-list") {
    state.taskView = "list";
    renderTasks();
  }
  if (action === "tasks-more") loadMore("tasks", target);
  if (action === "activity-more") loadMore("activity", target);
  if (action === "filters-clear") {
    state.filters = {
      q: "",
      status: "",
      priority: "",
      projectId: "",
      assigneeId: "",
    };
    loadPage();
  }
  if (action === "member-add") memberForm();
  if (action === "member-remove") removeMember(id);
  if (action === "reload") {
    if (state.workspace) loadWorkspace();
    else loadPage();
  }
  if (action.startsWith("view-")) navigate(action.slice(5));
  if (action === "menu-toggle")
    setMenu(!$(".app-shell").classList.contains("menu-open"));
  if (action === "menu-close") setMenu(false);
});
document.addEventListener("click", (event) => {
  const nav = event.target.closest("[data-page]");
  if (nav) {
    event.preventDefault();
    navigate(nav.dataset.page);
  }
});
let searchTimer;
document.addEventListener("input", (event) => {
  if (event.target.dataset.filter === "q") {
    state.filters.q = event.target.value;
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => loadPage(true), 320);
  }
});
document.addEventListener("change", (event) => {
  const el = event.target;
  if (el.dataset.filter && el.dataset.filter !== "q") {
    state.filters[el.dataset.filter] = el.value;
    clearTimeout(searchTimer);
    loadPage(true);
  }
  if (el.dataset.taskStatus)
    changeTaskStatus(el.dataset.taskStatus, el.value, el);
  if (el.dataset.memberRole)
    changeMemberRole(el.dataset.memberRole, el.value, el);
});
document.addEventListener("keydown", (event) => {
  if (
    event.key === "/" &&
    state.user &&
    !modal.open &&
    !confirmModal.open &&
    !["INPUT", "TEXTAREA", "SELECT"].includes(event.target.tagName)
  ) {
    event.preventDefault();
    $("#global-search")?.focus();
  }
});
document.addEventListener("keydown", (event) => {
  if (
    event.key === "Escape" &&
    !modal.open &&
    !confirmModal.open &&
    $(".app-shell")?.classList.contains("menu-open")
  )
    setMenu(false);
});
window.matchMedia("(max-width:700px)").addEventListener("change", syncDrawer);
modal.addEventListener("close", () => {
  state.taskModal = null;
  if (state.user && route().task) writeRoute(state.page);
});
modal.addEventListener("click", (event) => {
  if (event.target !== modal) return;
  const r = modal.getBoundingClientRect();
  if (
    event.clientX < r.left ||
    event.clientX > r.right ||
    event.clientY < r.top ||
    event.clientY > r.bottom
  )
    modal.close();
});
window.addEventListener("hashchange", async () => {
  if (await specialRoute()) return;
  const current = route();
  if (!state.user || !PAGES.includes(current.page)) return;
  if (current.workspace && current.workspace !== state.workspace?.id) {
    state.workspace =
      state.workspaces.find((w) => w.id === current.workspace) ||
      state.workspace;
    rememberWorkspace();
    state.page = current.page;
    state.filters = {
      q: "",
      status: "",
      priority: "",
      projectId: "",
      assigneeId: "",
    };
    shell();
    if (state.workspace) await loadWorkspace();
  } else if (current.page !== state.page) {
    state.page = current.page;
    shell();
    await loadPage();
  }
  if (current.task && current.task !== state.taskModal?.id)
    await openTask(current.task, false);
  else if (!current.task && state.taskModal) modal.close();
});
async function boot() {
  try {
    await csrf();
    const config = await api("/api/auth/config");
    state.demo = Boolean(config.demoEnabled);
    state.registrationEnabled = config.registrationEnabled !== false;
    state.emailVerificationRequired = Boolean(config.emailVerificationRequired);
    state.mailEnabled = Boolean(config.mailEnabled);
    try {
      state.user = await api("/api/auth/me");
    } catch (error) {
      if (error.status !== 401) throw error;
    }
    if (await specialRoute()) return;
    if (state.user) await loadWorkspaces();
    else authView();
  } catch (error) {
    app.innerHTML = `<div class="boot-screen"><div><h2>Orbit needs a moment.</h2><p class="muted">${esc(error.message)}</p><button class="secondary" data-action="boot-retry">Try again ${icon("arrow")}</button></div></div>`;
  }
}
document.addEventListener("click", (event) => {
  if (event.target.closest('[data-action="boot-retry"]')) boot();
});
boot();
