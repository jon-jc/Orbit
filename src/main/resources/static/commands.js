export function createCommandPalette({
  dialog,
  state,
  api,
  esc,
  icon,
  navigate,
  createTask,
  createProject,
  createWorkspace,
  openTask,
  applyView,
  help,
}) {
  let generation = 0;
  let aborter;
  let timer;
  let entries = [];
  let active = 0;
  let trigger;
  const labels = {
    overview: "Overview",
    tasks: "My workspace",
    projects: "Projects",
    team: "Team",
    activity: "Activity",
    notifications: "Inbox",
    settings: "Workspace settings",
    account: "Your account",
  };
  function valid(context, request) {
    return (
      dialog.open &&
      generation === request &&
      state.user?.id === context.user &&
      state.workspace?.id === context.workspace
    );
  }
  function close() {
    generation++;
    aborter?.abort();
    clearTimeout(timer);
    if (dialog.open) dialog.close();
  }
  function draw(items, message = "") {
    entries = items;
    active = 0;
    dialog.querySelector("#command-results").innerHTML = items
      .map(
        (item, index) =>
          `<div role="option" id="command-${index}" aria-selected="${index === 0}" data-command-index="${index}" class="command-option${index === 0 ? " active" : ""}">${icon(item.icon || "arrow")}<span><strong>${esc(item.label)}</strong>${item.detail ? `<small>${esc(item.detail)}</small>` : ""}</span>${item.shortcut ? `<kbd>${esc(item.shortcut)}</kbd>` : ""}</div>`,
      )
      .join("");
    dialog.querySelector("#command-feedback").textContent =
      message ||
      `${items.length} ${items.length === 1 ? "result" : "results"}. Use arrow keys to choose.`;
    syncActive();
  }
  function syncActive() {
    const input = dialog.querySelector("#command-input");
    if (entries.length)
      input.setAttribute("aria-activedescendant", `command-${active}`);
    else input.removeAttribute("aria-activedescendant");
    dialog.querySelectorAll("[data-command-index]").forEach((option, index) => {
      option.classList.toggle("active", index === active);
      option.setAttribute("aria-selected", String(index === active));
    });
    dialog
      .querySelector(`#command-${active}`)
      ?.scrollIntoView({ block: "nearest" });
  }
  function actions(query) {
    const list = Object.entries(labels).map(([page, label]) => ({
      label: `Go to ${label}`,
      detail: "Navigate",
      icon: page === "notifications" ? "bell" : "grid",
      run: () => navigate(page),
    }));
    if (state.workspace && state.workspace.role !== "VIEWER") {
      list.unshift(
        {
          label: "Create a task",
          icon: "plus",
          shortcut: "N",
          run: createTask,
        },
        { label: "Create a project", icon: "layers", run: createProject },
      );
    }
    list.push(
      { label: "Create a workspace", icon: "plus", run: createWorkspace },
      { label: "Keyboard shortcuts", icon: "info", shortcut: "?", run: help },
    );
    for (const view of state.savedViews || [])
      list.push({
        label: view.label,
        detail: "Your private saved view",
        icon: "filter",
        run: () => applyView(view.id),
      });
    return list.filter(
      (item) =>
        !query ||
        item.label.toLocaleLowerCase().includes(query.toLocaleLowerCase()),
    );
  }
  async function search(query) {
    clearTimeout(timer);
    aborter?.abort();
    const request = ++generation;
    const context = { user: state.user?.id, workspace: state.workspace?.id };
    const local = actions(query);
    draw(
      local,
      query.length >= 2 && context.workspace ? "Searching your workspace…" : "",
    );
    if (query.length < 2 || !context.workspace) return;
    aborter = new AbortController();
    timer = setTimeout(async () => {
      try {
        const params = new URLSearchParams({ q: query, page: "0", size: "8" });
        const data = await api(
          `/api/workspaces/${encodeURIComponent(context.workspace)}/tasks?${params}`,
          { signal: aborter.signal },
        );
        if (!valid(context, request)) return;
        const found = data.items.map((task) => ({
          label: task.title,
          detail: task.projectName,
          icon: "tasks",
          run: () => openTask(task.id),
        }));
        found.push({
          label: `Search all tasks for “${query}”`,
          detail: `${data.total} matching ${data.total === 1 ? "task" : "tasks"}`,
          icon: "search",
          run: () => {
            state.activeSavedView = null;
            state.selectedTasks.clear();
            state.filters = {
              q: query,
              status: "",
              priority: "",
              projectId: "",
              assigneeId: "",
            };
            navigate("tasks");
          },
        });
        draw([...local, ...found]);
      } catch (error) {
        if (!valid(context, request) || error.name === "AbortError") return;
        draw(local, `Task search couldn’t load. ${error.message}`);
      }
    }, 180);
  }
  async function choose(index) {
    const item = entries[index];
    if (!item) return;
    close();
    await item.run();
  }
  function open() {
    if (!state.user) return;
    if (dialog.open) return close();
    trigger = document.activeElement;
    dialog.innerHTML = `<div class="command-head"><h2 id="command-title">Find your next move</h2><button class="icon-button" id="command-close" aria-label="Close commands">${icon("close")}</button></div><div class="command-search">${icon("search")}<label class="sr-only" for="command-input">Search commands and tasks</label><input id="command-input" type="text" placeholder="Search commands, tasks, or saved views…" maxlength="200" autocomplete="off" role="combobox" aria-autocomplete="list" aria-expanded="true" aria-controls="command-results"></div><div id="command-results" role="listbox" aria-label="Commands and tasks"></div><p id="command-feedback" class="command-feedback" role="status"></p><div class="command-foot"><span><kbd>↑</kbd><kbd>↓</kbd> to move <kbd>↵</kbd> to open</span><span><kbd>esc</kbd> to close</span></div>`;
    dialog.showModal();
    dialog.querySelector("#command-input").focus();
    dialog
      .querySelector("#command-input")
      .addEventListener("input", (event) => search(event.target.value.trim()));
    dialog.querySelector("#command-close").onclick = close;
    search("");
  }
  dialog.addEventListener("click", (event) => {
    const option = event.target.closest("[data-command-index]");
    if (option) choose(Number(option.dataset.commandIndex));
    if (event.target === dialog) {
      const r = dialog.getBoundingClientRect();
      if (
        event.clientX < r.left ||
        event.clientX > r.right ||
        event.clientY < r.top ||
        event.clientY > r.bottom
      )
        close();
    }
  });
  dialog.addEventListener("keydown", (event) => {
    if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
      event.preventDefault();
      if (!entries.length) return;
      active =
        event.key === "Home"
          ? 0
          : event.key === "End"
            ? entries.length - 1
            : (active + (event.key === "ArrowDown" ? 1 : -1) + entries.length) %
              entries.length;
      syncActive();
    }
    if (event.key === "Enter") {
      event.preventDefault();
      choose(active);
    }
  });
  dialog.addEventListener("close", () => {
    generation++;
    aborter?.abort();
    clearTimeout(timer);
    if (trigger?.isConnected && document.activeElement === document.body)
      trigger.focus({ preventScroll: true });
  });
  return { open, close };
}
