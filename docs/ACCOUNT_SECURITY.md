# Account security and email operations

Orbit supports display-name updates, authenticated password changes, email verification, password recovery, and listing/revoking the current account's sessions. Email delivery uses a transactional, encrypted database outbox and SMTP. This guide describes the implemented backend and operator responsibilities; it does not imply MFA, SSO, account deletion, or an administrative account-management console.

## Account and session protocol

All writes require the session-bound CSRF token from `GET /api/auth/csrf`, including anonymous registration, sign-in, reset, and verification requests. Preserve the `ORBIT_SESSION` cookie. Sign-in rotates its identifier; obtain a fresh CSRF token afterward. The cookie is HttpOnly, SameSite=Lax, and Secure in production. Responses describe the user as `{id,name,email,emailVerified}`.

| Method and path | Request / successful response |
| --- | --- |
| `GET /api/auth/config` | `{demoEnabled,registrationEnabled,emailVerificationRequired,mailEnabled}` |
| `POST /api/auth/register` | `{name,email,password}` → 201 user; does not sign in |
| `POST /api/auth/login` | `{email,password}` → 200 user and rotated session |
| `POST /api/auth/logout` | No body → 204; invalidates current session |
| `GET /api/account` | 200 current user |
| `PATCH /api/account` | `{name}` → 200 updated user |
| `POST /api/account/password` | `{currentPassword,newPassword}` → 204 and revocation of all account sessions |
| `POST /api/auth/forgot-password` | `{email}` → 202 generic message |
| `POST /api/auth/reset-password` | `{token,password}` → 204; replaces password and revokes all account sessions |
| `POST /api/auth/resend-verification` | `{email}` → 202 generic message |
| `POST /api/auth/verify-email` | `{token}` → 204; marks email verified |
| `POST /api/account/verification` | No body → 202 generic message for the authenticated account |
| `GET /api/account/sessions` | Up to 100 nonexpired sessions, most recently used first |
| `DELETE /api/account/sessions/{id}` | 204; invalidates the browser session too when it revokes itself |

Session responses contain `{id,createdAt,lastAccessedAt,expiresAt,current}`. `id` is an opaque database row identifier, **not the cookie value or a sign-in credential**. Revocation uses that ID and is restricted to the signed-in account; another account's session ID returns 404. Timestamps are UTC instants. Session entries do not include device names, user agents, or geolocation.

Passwords require at least 12 characters and must fit within 72 UTF-8 bytes; BCrypt cost 12 is used. A correct current password is required for authenticated changes. Account display names are nonblank, at most 100 characters, and cannot contain U+0000. The email address is normalized to lowercase and cannot currently be changed through the API.

Password replacement increments an account authentication version and deletes its JDBC sessions. Subsequent authenticated requests also compare that version with the database, so a concurrent request saving an older security context does not make a revoked session valid again. Already executing requests cannot be recalled. A password-change notification is queued when email is enabled. Sign in again after a successful password change or reset.

## Verification, recovery, and existing accounts

Registration is enabled by default and can be disabled with `ORBIT_REGISTRATION_ENABLED=false` in production. The production profile defaults `ORBIT_EMAIL_VERIFICATION_REQUIRED=true`; local development defaults false. When verification is required, correct credentials for an unverified account receive 403 until the email link is consumed. Anonymous resend and verify endpoints remain available.

Migration V3 marks **all existing accounts unverified**. Legacy sessions without the new authentication version are invalidated; users must sign in again and verify when required. It does not send a bulk verification campaign or trust historical email addresses. Before upgrading a live service, configure and test SMTP, announce the verification requirement through your normal support process, and plan access recovery for accounts with unreachable email addresses. Newly seeded local demo accounts are verified; existing local data is not silently changed to verified. Explicitly disabling the requirement is an operator policy change, not an automatic migration fallback.

Verification links expire after 24 hours; password-reset links after 30 minutes. Tokens contain 256 random bits, are Base64 URL encoded without padding, and are stored only as SHA-256 hashes in the token table. They are single use, purpose bound, and checked against their expiry. Issuance is serialized with account-row locks. The default per-account/purpose send cooldown is one minute; repeated anonymous requests during that interval preserve the existing usable link. Issuing a later link invalidates earlier unconsumed links of that purpose. Invalid, expired, or used reset/verification links return 400 with the same explanation.

Forgot-password and anonymous resend responses are generic 202 messages whether or not the account exists. They reduce direct disclosure but are not a guarantee of identical response timing. Registration still reports duplicate email addresses with 409. The bounded per-instance authentication limiter also covers recovery, verification, and authenticated password requests; use shared ingress throttling and abuse monitoring for public deployments.

Links use the configured public origin and browser fragments: `/#reset-password?token=...` and `/#verify-email?token=...`. Fragments are not sent in ordinary page requests; the browser consumes the token through the protected API flow. They remain sensitive credentials. Do not copy them into logs, analytics, support tickets, or screenshots.

## Production SMTP configuration

Use an approved SMTP provider with a verified sender, delivery monitoring, and appropriate SPF/DKIM/DMARC policy. The application does not provision these DNS records or verify provider delivery. The `prod` profile requires an absolute HTTPS `ORBIT_PUBLIC_BASE_URL` with no credentials, path, query, or fragment, for example `https://orbit.example.com`.

| Setting | Meaning |
| --- | --- |
| `ORBIT_MAIL_ENABLED` | Enable delivery; local default false, reference production Compose default true |
| `ORBIT_MAIL_HOST`, `ORBIT_MAIL_PORT` | SMTP host and port; port defaults 587 |
| `ORBIT_MAIL_USERNAME`, `ORBIT_MAIL_PASSWORD` | Provider credentials; SMTP authentication is enabled when username is nonblank |
| `ORBIT_MAIL_FROM` | A valid single sender email address; required when enabled |
| `ORBIT_MAIL_STARTTLS` | Default true; STARTTLS is both enabled and required |
| `ORBIT_MAIL_ENCRYPTION_KEY` | Base64 encoding of exactly 32 random bytes; required when enabled |
| `ORBIT_PUBLIC_BASE_URL` | Production HTTPS origin used for action links |
| `orbit.accounts.reset-token-ttl` | Default `30m`; allowed range one minute to seven days |
| `orbit.accounts.verification-token-ttl` | Default `24h`; same allowed range |
| `orbit.accounts.token-send-cooldown` | Default `1m`; allowed range one second to one hour |
| `orbit.mail.dispatch-delay-ms` | Default `5000`; background dispatch interval |

Enabled mail fails startup for missing/invalid host, port, sender, or encryption key. Enabled verification with registration also requires enabled email. Configuration validation does not contact the SMTP provider; prove delivery with a controlled test before opening signup. SMTP connect, read, and write timeouts are each ten seconds. Server certificate identity checking is enabled. The supplied transport supports SMTP with STARTTLS; do not assume it configures implicit TLS on port 465.

Generate a new key in a secure environment and store it in your secret manager. Linux/macOS with OpenSSL:

```sh
openssl rand -base64 32
```

Windows PowerShell:

```powershell
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
[Convert]::ToBase64String($bytes)
```

These commands intentionally display the new secret so an operator can provision it; do not run them in recorded logs or shared terminals. Keep the key stable across replicas and restarts while messages remain queued. Store a protected recovery copy separately from database backups. There is currently no key-version or automated rotation workflow: drain or deliberately retire old queued messages before changing the key, and test the transition.

## Durable outbox behavior

Email intent commits in the same transaction as the associated account action. Bodies, including action links, are encrypted using AES-256-GCM with a fresh nonce and authenticated message metadata. Recipients, subjects, status, attempt counts, and timestamps remain queryable plaintext metadata. Outbox encryption is not whole-database encryption; backups still contain sensitive account and workspace data.

The dispatcher claims up to ten due messages per pass. Atomic claims and five-minute leases permit multiple replicas; an expired lease is retried after a crash. Before SMTP it checks linked account/invitation state and cancels expired, consumed, replaced, revoked, or no-longer-authorized action messages. A concurrent state change after that check can still make a delivered link unusable; token consumption remains authoritative.

Failed delivery uses increasing delays starting at 30 seconds, with a six-hour cap, and marks the message FAILED after eight attempts. Successful messages are deleted seven days after sending; CANCELLED rows are deleted once their creation time is older than seven days. This cleanup runs even when delivery is disabled. Failed messages remain for investigation. SMTP is **at least once**: a crash after provider acceptance but before recording SENT can produce a duplicate email. Action tokens remain single use.

Monitor `orbit.mail.pending` and `orbit.mail.delivery` metrics, queue age, FAILED rows, and provider failures. The pending gauge includes PENDING, SENDING, and FAILED rows. Errors retain a failure class and message ID rather than complete provider exceptions or mail bodies. Investigate SMTP/network/certificate/credential settings and the encryption key before considering a retry. The product currently has no operator retry endpoint; direct outbox changes require a reviewed operational procedure. Do not blindly resend expired action links.

When mail is disabled, enqueue is a no-op: no message is delivered or stored for later automatic delivery. Local recovery/verification therefore requires enabling a test sink. Generic 202 responses do not guarantee that a message exists, was delivered, or reached an inbox. Retention of account tokens and account security events is not automated; define a reviewed retention procedure for your environment.

## Local Mailpit without external delivery

The optional development Compose file starts a local capture sink with no external relay configuration:

```sh
docker compose -f compose.dev.yaml up -d
```

Open [http://127.0.0.1:8025](http://127.0.0.1:8025) to inspect captured messages. SMTP listens only on host loopback port 1025. Set a new test encryption key as described above and start Orbit with mail enabled. PowerShell example after packaging:

```powershell
$env:ORBIT_MAIL_ENABLED = 'true'
$env:ORBIT_MAIL_HOST = '127.0.0.1'
$env:ORBIT_MAIL_PORT = '1025'
$env:ORBIT_MAIL_STARTTLS = 'false'
$env:ORBIT_MAIL_FROM = 'orbit@example.test'
# Set ORBIT_MAIL_ENCRYPTION_KEY to a fresh test-only 32-byte Base64 key.
java -jar .\target\orbit-1.0.0.jar `
  --spring.profiles.active=local `
  --orbit.accounts.public-base-url=http://127.0.0.1:8080
```

Linux/macOS use the same variables with `export`, then `java -jar target/orbit-1.0.0.jar --spring.profiles.active=local --orbit.accounts.public-base-url=http://127.0.0.1:8080`. The direct public-origin argument also makes alternate local ports explicit. Use disposable accounts and data. Disable STARTTLS only for this isolated sink; production keeps it enabled. Stop the sink with `docker compose -f compose.dev.yaml down`.

Before activating a recovered database, revoke restored sessions and outstanding account tokens as appropriate, review the mail outbox, and keep SMTP disabled until replay is deliberately approved. Restore verification should use an isolated database with no public traffic or external mail. See [RUNBOOK.md](RUNBOOK.md) for recovery and deployment controls.
