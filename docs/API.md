# HTTP API

All endpoints are same-origin. Requests and responses use JSON unless export returns CSV. JSON bodies require `Content-Type: application/json`; unsupported request media returns 415, unacceptable response media returns 406, and unsupported methods return 405 with an `Allow` header. IDs are UUID strings; deadlines use `YYYY-MM-DD`; timestamps are ISO 8601. The machine-readable [OpenAPI 3.1 contract](../api/openapi.yaml) covers 51 operations and can be validated with [api/validate.py](../api/validate.py). It is a checked-in contract, not an automatically generated runtime documentation endpoint.

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
| `GET /api/auth/config` | Demo, registration, verification-required, and mail-enabled flags | Public |
| `POST /api/auth/register` | Create account `{name,email,password}` | Public + CSRF |
| `POST /api/auth/login` | Sign in `{email,password}` | Public + CSRF |
| `POST /api/auth/logout` | Invalidate session, 204 | CSRF |
| `GET /api/auth/me` | Current user | Signed in |
| `POST /api/auth/forgot-password` | `{email}`; generic 202 message | Public + CSRF |
| `POST /api/auth/reset-password` | `{token,password}`; 204, revokes all account sessions | Public + CSRF |
| `POST /api/auth/resend-verification` | `{email}`; generic 202 message | Public + CSRF |
| `POST /api/auth/verify-email` | `{token}`; 204 | Public + CSRF |
| `GET /api/account` | Current account | Signed in |
| `PATCH /api/account` | `{name}`; update display name | Signed in + CSRF |
| `POST /api/account/password` | `{currentPassword,newPassword}`; 204, revokes all account sessions | Signed in + CSRF |
| `POST /api/account/verification` | Generic 202 verification message | Signed in + CSRF |
| `GET /api/account/sessions` | Up to 100 nonexpired account sessions | Signed in |
| `DELETE /api/account/sessions/{id}` | Revoke an account-owned opaque session-row ID, 204 | Signed in + CSRF |
| `GET /api/workspaces` | Workspaces the actor belongs to | Signed in |
| `POST /api/workspaces` | Create workspace `{name}`; creator becomes owner | Signed in + CSRF |
| `GET /settings` | Workspace name, version, actor role, creation time | Member of workspace |
| `PATCH /settings` | Rename `{name,version}` | OWNER |
| `GET /members` | Workspace members | Member of workspace |
| `POST /members` | Add an existing account `{email,role}` | OWNER |
| `PATCH /members/{userId}` | Change role `{role}` | OWNER |
| `DELETE /members/{userId}` | Remove member and clear their assignments | OWNER |
| `GET /projects` | Projects with task progress | Member of workspace |
| `POST /projects` | Create `{name,description,color}` | OWNER or MEMBER |
| `PATCH /projects/{projectId}` | Edit `{name,description,color,status,version}` | OWNER or MEMBER |
| `GET /tasks` | Search/filter/page tasks | Member of workspace |
| `POST /tasks` | Create a task | OWNER or MEMBER |
| `POST /tasks/bulk` | Atomically change status, priority, assignment of 1–100 versioned tasks | OWNER or MEMBER |
| `GET /tasks/{taskId}` | Task details | Member of workspace |
| `PATCH /tasks/{taskId}` | Replace editable task fields with `version` | OWNER or MEMBER |
| `DELETE /tasks/{taskId}?version=N` | Delete current task version | OWNER or MEMBER |
| `GET /tasks/{taskId}/comments` | Task comments | Member of workspace |
| `POST /tasks/{taskId}/comments` | Add `{body}` | OWNER or MEMBER |
| `GET /saved-views` | List up to 100 actor-owned private views | Any member, including VIEWER |
| `POST /saved-views` | Save label and filters, 201 | Any member, including VIEWER |
| `GET /saved-views/{viewId}` | Read own saved view | View owner with current membership |
| `PATCH /saved-views/{viewId}` | Replace own label/filters using current version | View owner with current membership |
| `DELETE /saved-views/{viewId}` | Delete own saved view, 204 | View owner with current membership |
| `GET /activity` | Page activity events | Member of workspace |
| `GET /overview` | Summary counts, projects, upcoming tasks, activity | Member of workspace |
| `GET /export` | Download workspace tasks as formula-safe CSV | Member of workspace |
| `GET /invitations` | Latest 100 invitations, newest first | OWNER |
| `POST /invitations` | `{email,role}`; email invitation, 201 | OWNER; enabled mail |
| `DELETE /invitations/{invitationId}` | Revoke an unaccepted invitation, 204 | OWNER |
| `GET /api/invitations/preview?token=...` | Preview recipient, workspace name, role, expiry | Public token possession |
| `POST /api/invitations/accept` | `{token}`; workspace membership | Signed in with the invited email + CSRF |
| `GET /api/notifications` | Paged inbox with unread count | Signed in |
| `PATCH /api/notifications/{id}/read` | Mark one visible account-owned notification read, 204 | Signed in + CSRF |
| `POST /api/notifications/read-all` | Mark all currently visible notifications read, 204 | Signed in + CSRF |

All mutations require CSRF. Initial membership creation accepts MEMBER or VIEWER; an owner can subsequently promote another member to OWNER. Self-demotion and deleting an OWNER are rejected with 409.

Task create/edit fields are `title`, `description`, `projectId`, `status`, `priority`, `assigneeId`, and `dueDate`. `assigneeId` and `dueDate` can be null. PATCH also requires the current `version` and all required editable fields; it is not a JSON Merge Patch. Status is `BACKLOG`, `TODO`, `IN_PROGRESS`, `IN_REVIEW`, or `DONE`. Priority is `LOW`, `MEDIUM`, `HIGH`, or `URGENT`. Project status is `ACTIVE` or `ARCHIVED`. Archived projects remain editable and may contain tasks.

## Response objects and limits

| Object | Fields |
| --- | --- |
| User | `id`, `name`, `email`, `emailVerified` |
| Account session | `id`, `createdAt`, `lastAccessedAt`, `expiresAt`, `current` |
| Workspace | `id`, `name`, `role`, `createdAt` |
| Workspace settings | `id`, `name`, `version`, `role`, `createdAt` |
| Member | `id`, `name`, `email`, `role` |
| Project | `id`, `name`, `description`, `color`, `status`, `taskCount`, `completedTaskCount`, `createdAt`, `version` |
| Task | `id`, `title`, `description`, `projectId`, `projectName`, `projectColor`, `status`, `priority`, `assigneeId`, `assigneeName`, `dueDate`, `createdAt`, `updatedAt`, `version`, `commentCount` |
| Saved view | `id`, `workspaceId`, `label`, `q`, `status`, `priority`, `projectId`, `assigneeId`, `version`, `createdAt`, `updatedAt` |
| Comment | `id`, `authorId`, `authorName`, `body`, `createdAt` |
| Activity | `id`, `actorName`, `action`, `entityType`, `entityName`, `createdAt` |
| Overview | `totalTasks`, `completedTasks`, `inProgressTasks`, `overdueTasks`, `projects`, `recentActivity`, `upcomingTasks` |
| Page | `items`, `page`, `size`, `total`, `totalPages` |
| Invitation | `id`, `email`, `role`, `status`, `expiresAt`, `createdAt` |
| Invitation preview | `workspaceName`, `email`, `role`, `expiresAt` |
| Notification | `id`, `workspaceId`, `workspaceName`, `taskId`, `type`, `title`, `body`, `read`, `createdAt` |
| Inbox | Page fields plus `unreadCount` |

Project color is a six-digit hexadecimal value including `#`, such as `#7367f0`. Version/count/page fields are numbers. Unassigned task `assigneeId` and `assigneeName` are null; a task without a deadline has a null `dueDate`. Required text must be nonblank. Domain/display-name text rejects U+0000; dates must be valid dates in years 0001–9999.

| Input | Maximum length |
| --- | --- |
| User display name / workspace name | 100 characters |
| Email | 254 characters |
| Registration password | 12–72 characters, subject to BCrypt's byte limit |
| Project name / description | 120 / 2,000 characters |
| Task title / description | 200 / 10,000 characters |
| Comment body | 4,000 characters |
| Saved-view label / literal search | 80 / 200 characters |

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

The server supplies IDs, names, timestamps, counts, and the initial version. Send the complete editable object with that version to PATCH. Create endpoints return 201; successful edits return 200; deletion/logout/password replacement/token consumption return 204. Mail-request endpoints return 202 with `{message}`. Invitation acceptance returns 200 with the workspace.

## Atomic bulk tasks and private views

Bulk mutation accepts 1–100 distinct task IDs and a `versions` object containing exactly those IDs and their nonnegative current versions:

```json
{
  "taskIds": ["d7c5ac86-fbbb-414b-9e25-c2b8dbfa5716"],
  "versions": {"d7c5ac86-fbbb-414b-9e25-c2b8dbfa5716": 3},
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "clearAssignee": true
}
```

Provide at least one nonnull `status`, `priority`, `assigneeId`, or `clearAssignee:true`. A nonnull assignee and `clearAssignee:true` conflict and return 400. IDs and referenced assignees must belong to the workspace. The response is a task array in request order with incremented versions. A missing/inaccessible ID produces 404, or any stale version produces 409, with **no changes to any selected task, audit, or notifications**. Refresh all selected records and reconcile before retrying; never assume a partially successful batch.

A saved view stores only its creator's label and task filters:

```json
{
  "label": "My urgent reviews",
  "q": "launch",
  "status": "IN_REVIEW",
  "priority": "URGENT",
  "projectId": null,
  "assigneeId": "d7c5ac86-fbbb-414b-9e25-c2b8dbfa5716"
}
```

Any current member, including VIEWER, can maintain their own views. Other members' view IDs return 404, even to workspace owners. The limit is 100 per user/workspace; exceeding it returns 409. Labels are nonblank and trimmed; search text preserves literal whitespace, with an empty string stored as null. The response always includes all filter keys, which may be null, plus workspace/ID/timestamps/version. PATCH requires `version` and replaces the full filter set: an omitted or null filter clears it. Referenced projects/assignees must remain within the workspace.

Removing a member deletes their private views and clears that assignee from remaining members' views while incrementing affected view versions. A previously open editor must reconcile the resulting 409. Filter URLs and view IDs grant no access by themselves.

## Accounts, tokens, and invitations

Account email is immutable. Password replacement requires 12–72 characters and no more than 72 UTF-8 bytes. Change/reset revokes all account sessions, including the current browser; sign in and obtain fresh CSRF afterward. Session-list IDs are opaque revocation row identifiers, never cookie credentials; another account's identifier returns 404. See [account security](ACCOUNT_SECURITY.md) for migration, delivery, and recovery behavior.

Reset links expire after 30 minutes, verification links after 24 hours, invitations after seven days. Tokens are 43-character Base64 URL strings from 256 random bits, single use, and stored as SHA-256 hashes. Mail action links use fragments. Anonymous forgot/resend always return generic 202 messages; this does not guarantee delivery or identical timing. Default per-account/purpose send cooldown is one minute. Invalid/expired/used account-action tokens return 400. Production verification policy can deny sign-in with 403 until verified.

Invitation creation accepts only MEMBER or VIEWER and requires configured mail. An already registered member yields 409; invitations can otherwise target an unregistered email. The owner has a rolling limit of 100 invitations per day. Reissuing for the same workspace/email revokes previous unaccepted invitations. Listing reports PENDING, ACCEPTED, REVOKED, or EXPIRED. Revoking a used/revoked invitation yields 409. Preview/accept deny unavailable/expired/revoked/used tokens with 404; the inviter must still be an owner. Acceptance requires the signed-in email to match the target or returns 403. It does not overwrite an existing member's current role. The token itself is never returned in the invitation-management response.

Workspace rename uses `{name,version}` and returns incremented settings. Owners alone can rename; stale versions return 409. Example:

```json
{"name":"Launch operations","version":0}
```

## Notification audience

Notifications have types ASSIGNED, COMMENT, and MEMBERSHIP. Assignment notifies the assignee; comments notify existing participants and the assignee. A mutation's actor does not receive a notification for their own action. Inbox reads join current workspace membership, so removing access hides historical notifications for that workspace. Read/unread changes are account scoped; foreign or no-longer-visible notification IDs return 404. Task deletion retains the notification with `taskId:null`. Notification text is a snapshot, and the app rechecks access before opening a task link. There is no live push subscription.

## Filtering and paging

Tasks accept `q`, `status`, `priority`, `projectId`, `assigneeId`, `page`, and `size`. Search is a literal, case-insensitive substring, at most 200 characters; SQL wildcard characters are ordinary text. Pages start at zero and size must be 1–100. The default task size is 50; activity and notifications default to 30. Responses are `{items,page,size,total,totalPages}`; inbox adds `unreadCount` across all currently visible items, independently of the page. A page past the last page has no items. Projects/members are arrays; invitations and account sessions are bounded arrays of at most 100.

## Errors and conflicts

| Status | Meaning |
| --- | --- |
| 400 | Invalid JSON, field values, enum, date, UUID, paging, or missing version |
| 401 | Sign-in required or invalid credentials |
| 403 | Insufficient role, verification/registration policy, email mismatch, or missing/invalid CSRF token |
| 404 | Object absent or workspace not accessible to this user |
| 405 | Unsupported HTTP method; `Allow` lists supported methods |
| 406 | Requested response media type is not acceptable |
| 409 | Duplicate/conflicting state or stale optimistic version |
| 413 | API request body exceeds the 64 KiB limit |
| 415 | Unsupported request media type; JSON bodies require `application/json` |
| 429 | Authentication limit (with `Retry-After`) or daily invitation limit |
| 500 | Unexpected failure; report request ID |
| 503 | Invitation creation requires enabled email delivery |

Errors use ProblemDetail-style JSON containing `status`, `title`, `detail`, and `requestId`; field validation can add `errors`. Never retry a stale mutation unchanged: fetch the current object, show the conflict to the user, then submit a deliberate edit using its new version. Network retries of create/comment operations can duplicate data because the API does not provide idempotency keys.
