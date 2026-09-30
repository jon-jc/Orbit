# HTTP API

All endpoints are same-origin. Requests and responses use JSON unless export returns CSV. IDs are UUID strings; deadlines use `YYYY-MM-DD`; timestamps are ISO 8601.

## Session protocol

1. Request `GET /api/auth/csrf` and preserve the session cookie.
2. Send the returned token in the returned header name on every mutation, including registration, login, and logout.
3. Registration creates an account without signing it in. Login returns the user and rotates the session cookie.
4. Retrieve a new CSRF token after login and logout.
5. Send the session cookie on subsequent requests. A fresh browser origin signs in separately.

The browser client already follows this flow. A local shell example with `curl` and `jq`:

```sh
# Run only against your local development server; these are public demo credentials.
BASE=http://localhost:8080
TOKEN=$(curl -fsS -c cookies.txt "$BASE/api/auth/csrf" | jq -r .token)
curl -fsS -b cookies.txt -c cookies.txt \
  -H 'Content-Type: application/json' -H "X-CSRF-TOKEN: $TOKEN" \
  -d '{"email":"alex@orbit.local","password":"OrbitDemo!2026"}' \
  "$BASE/api/auth/login"
TOKEN=$(curl -fsS -b cookies.txt -c cookies.txt "$BASE/api/auth/csrf" | jq -r .token)
curl -fsS -b cookies.txt "$BASE/api/workspaces"
# Delete cookies.txt after the session test; it contains a credential.
```

## Endpoints

Prefix workspace routes with `/api/workspaces/{workspaceId}`.

| Method and path | Behavior | Required access |
| --- | --- | --- |
| `GET /api/auth/csrf` | Get session CSRF token/header name | Public |
| `GET /api/auth/config` | Demo-enabled flag | Public |
| `POST /api/auth/register` | Create account `{name,email,password}` | Public + CSRF |
| `POST /api/auth/login` | Sign in `{email,password}` | Public + CSRF |
| `POST /api/auth/logout` | Invalidate session, 204 | CSRF |
| `GET /api/auth/me` | Current user | Signed in |
| `GET /api/workspaces` | Workspaces the actor belongs to | Signed in |
| `POST /api/workspaces` | Create workspace `{name}`; creator becomes owner | Signed in + CSRF |
| `GET /members` | Workspace members | Member of workspace |
| `POST /members` | Add an existing account `{email,role}` | OWNER |
| `PATCH /members/{userId}` | Change role `{role}` | OWNER |
| `DELETE /members/{userId}` | Remove member and clear their assignments | OWNER |
| `GET /projects` | Projects with task progress | Member of workspace |
| `POST /projects` | Create `{name,description,color}` | OWNER or MEMBER |
| `PATCH /projects/{projectId}` | Edit `{name,description,color,status,version}` | OWNER or MEMBER |
| `GET /tasks` | Search/filter/page tasks | Member of workspace |
| `POST /tasks` | Create a task | OWNER or MEMBER |
| `GET /tasks/{taskId}` | Task details | Member of workspace |
| `PATCH /tasks/{taskId}` | Replace editable task fields with `version` | OWNER or MEMBER |
| `DELETE /tasks/{taskId}?version=N` | Delete current task version | OWNER or MEMBER |
| `GET /tasks/{taskId}/comments` | Task comments | Member of workspace |
| `POST /tasks/{taskId}/comments` | Add `{body}` | OWNER or MEMBER |
| `GET /activity` | Page activity events | Member of workspace |
| `GET /overview` | Summary counts, projects, upcoming tasks, activity | Member of workspace |
| `GET /export` | Download workspace tasks as formula-safe CSV | Member of workspace |

All mutations require CSRF. Initial membership creation accepts MEMBER or VIEWER; an owner can subsequently promote another member to OWNER. Self-demotion and deleting an OWNER are rejected with 409.

Task create/edit fields are `title`, `description`, `projectId`, `status`, `priority`, `assigneeId`, and `dueDate`. `assigneeId` and `dueDate` can be null. PATCH also requires the current `version` and all required editable fields; it is not a JSON Merge Patch. Status is `BACKLOG`, `TODO`, `IN_PROGRESS`, `IN_REVIEW`, or `DONE`. Priority is `LOW`, `MEDIUM`, `HIGH`, or `URGENT`. Project status is `ACTIVE` or `ARCHIVED`. Archived projects remain editable and may contain tasks.

## Response objects and limits

| Object | Fields |
| --- | --- |
| User | `id`, `name`, `email` |
| Workspace | `id`, `name`, `role`, `createdAt` |
| Member | `id`, `name`, `email`, `role` |
| Project | `id`, `name`, `description`, `color`, `status`, `taskCount`, `completedTaskCount`, `createdAt`, `version` |
| Task | `id`, `title`, `description`, `projectId`, `projectName`, `projectColor`, `status`, `priority`, `assigneeId`, `assigneeName`, `dueDate`, `createdAt`, `updatedAt`, `version`, `commentCount` |
| Comment | `id`, `authorId`, `authorName`, `body`, `createdAt` |
| Activity | `id`, `actorName`, `action`, `entityType`, `entityName`, `createdAt` |
| Overview | `totalTasks`, `completedTasks`, `inProgressTasks`, `overdueTasks`, `projects`, `recentActivity`, `upcomingTasks` |
| Page | `items`, `page`, `size`, `total`, `totalPages` |

Project color is a six-digit hexadecimal value including `#`, such as `#7367f0`. Version/count/page fields are numbers. Unassigned task `assigneeId` and `assigneeName` are null; a task without a deadline has a null `dueDate`. Required text must be nonblank. Text fields reject U+0000; dates must be valid dates in years 0001–9999.

| Input | Maximum length |
| --- | --- |
| User display name / workspace name | 100 characters |
| Email | 254 characters |
| Registration password | 12–72 characters, subject to BCrypt's byte limit |
| Project name / description | 120 / 2,000 characters |
| Task title / description | 200 / 10,000 characters |
| Comment body | 4,000 characters |

Creating a task:

```json
{
  "title": "Review launch checklist",
  "description": "Confirm accessibility and rollback steps.",
  "projectId": "d7c5ac86-fbbb-414b-9e25-c2b8dbfa5716",
  "status": "TODO",
  "priority": "HIGH",
  "assigneeId": null,
  "dueDate": "2026-10-10"
}
```

The server supplies IDs, names, timestamps, counts, and the initial version. Send the complete editable object with that version to PATCH. Create endpoints return 201; successful edits return 200; deletion/logout return 204.

## Filtering and paging

Tasks accept `q`, `status`, `priority`, `projectId`, `assigneeId`, `page`, and `size`. Search is a literal, case-insensitive substring; SQL wildcard characters are ordinary text. Pages start at zero and size must be 1–100. The default task size is 50; activity defaults to 30. Responses are `{items,page,size,total,totalPages}`. A page past the last page has no items. Projects and members are arrays rather than paged responses.

## Errors and conflicts

| Status | Meaning |
| --- | --- |
| 400 | Invalid JSON, field values, enum, date, UUID, paging, or missing version |
| 401 | Sign-in required or invalid credentials |
| 403 | Insufficient workspace role or missing/invalid CSRF token |
| 404 | Object absent or workspace not accessible to this user |
| 409 | Duplicate/conflicting state or stale optimistic version |
| 413 | API request body exceeds the 64 KiB limit |
| 429 | Authentication rate limit reached; follow `Retry-After` |
| 500 | Unexpected failure; report request ID |

Errors use ProblemDetail-style JSON containing `status`, `title`, `detail`, and `requestId`; field validation can add `errors`. Never retry a stale mutation unchanged: fetch the current object, show the conflict to the user, then submit a deliberate edit using its new version. Network retries of create/comment operations can duplicate data because the API does not provide idempotency keys.
