# OpenAPI contract

[openapi.yaml](openapi.yaml) is the checked-in OpenAPI 3.1 contract for Orbit 1.1.0's 37 paths and 51 operations. It covers authentication, account/session lifecycle, workspaces/settings/team/invitations, projects/tasks/comments/export, atomic bulk task updates, private saved views, and notifications. Response records and normalized controller paths have been reviewed against the implementation. [API.md](../docs/API.md) explains behavior and [ACCOUNT_SECURITY.md](../docs/ACCOUNT_SECURITY.md) explains security and delivery operations.

The contract is a repository artifact; Orbit does not expose a Swagger UI or an automatically generated OpenAPI endpoint. Structural validation checks the specification and unique operation IDs. It does not execute HTTP calls or prove that code still matches an edited contract. Review endpoint/DTO/security changes together and run the Java/browser workflow checks.

## Validate locally

Python 3.10 or newer with pip and venv is required only for this optional development check. No Python package is part of the Java runtime. From the repository root:

Windows PowerShell:

```powershell
python -m venv .openapi-venv
.\.openapi-venv\Scripts\python.exe -m pip install -r api\requirements-validation.txt
.\.openapi-venv\Scripts\python.exe api\validate.py
```

Linux/macOS:

```sh
python3 -m venv .openapi-venv
.openapi-venv/bin/python -m pip install -r api/requirements-validation.txt
.openapi-venv/bin/python api/validate.py
```

CI runs this validation using the pinned validation requirements. Dependabot checks `requirements-validation.txt` weekly through the `/api` pip configuration. The virtual environment is ignored by Git and excluded from the production Docker build.

## Client integration

Orbit uses a same-origin HttpOnly cookie, not a bearer-token API. Retrieve `GET /api/auth/csrf`, retain the anonymous session cookie, and send the returned header/token for every mutation, including public sign-in/register/recovery operations. Refresh CSRF after sign-in/logout. A generated client must preserve cookies and this rotation flow; schema generation alone does not implement it. Browsers cannot directly read the HttpOnly cookie.

The session-revocation ID returned by `/api/account/sessions` is an opaque database row identifier, not a cookie credential. Workspace resource IDs and notification IDs do not bypass membership checks. Account/invitation tokens are sensitive, purpose-bound, expiring single-use credentials; never publish them as examples. Reconcile 409 versions explicitly before resubmitting mutations. Creates/comments have no idempotency key, and mail acceptance is not proof of delivery.
