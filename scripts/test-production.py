#!/usr/bin/env python3
"""Disposable PostgreSQL/Mailpit production-profile checks; never external SMTP."""
import base64
from http.client import HTTPException
from http.cookiejar import CookieJar
import json
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import HTTPCookieProcessor, Request, build_opener, urlopen


def docker(*args):
    result = subprocess.run(["docker", *args], text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f"Docker {args[0]} operation failed.")
    return result.stdout.strip()


class Client:
    def __init__(self, base):
        self.base = base
        self.cookies = CookieJar()
        self.open = build_opener(HTTPCookieProcessor(self.cookies))
        self.headers = {}

    def api(self, method, path, body=None, expected=200):
        headers = {"Accept": "application/json"}
        if method != "GET":
            headers.update(self.headers)
        data = None
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        try:
            response = self.open.open(Request(self.base + path, data=data, headers=headers, method=method), timeout=15)
        except HTTPError as error:
            response = error
        with response:
            if response.status != expected:
                raise RuntimeError(f"Unexpected HTTP {response.status} for {method} {path}; expected {expected}.")
            payload = response.read()
            return json.loads(payload) if payload and expected < 400 else None

    def csrf(self):
        value = self.api("GET", "/api/auth/csrf")
        self.headers = {value["headerName"]: value["token"]}

    def session(self):
        return next(cookie.value for cookie in self.cookies if cookie.name == "ORBIT_SESSION")


def wait(check, message, seconds=120):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        try:
            value = check()
            if value:
                return value
        except (RuntimeError, URLError, HTTPException, OSError, ValueError, KeyError, StopIteration):
            pass
        time.sleep(1)
    raise RuntimeError(message)


def host_url(container, port):
    value = docker("port", container, f"{port}/tcp")
    return "http://" + value.splitlines()[0]


def mail_token(base, email, subject, fragment):
    def captured():
        with urlopen(base + "/api/v1/messages", timeout=10) as response:
            messages = json.load(response)["messages"]
        for message in messages:
            if message["Subject"] != subject or not any(recipient["Address"] == email for recipient in message["To"]):
                continue
            with urlopen(base + "/api/v1/message/" + message["ID"], timeout=10) as response:
                text = json.load(response)["Text"]
            match = re.search(r"https://orbit-release\.example\.test/#" + fragment + r"\?token=([A-Za-z0-9_-]{43})", text)
            if not match:
                raise RuntimeError("Captured mail has an incorrect HTTPS origin or token shape.")
            return match.group(1)
        return None
    return wait(captured, "Expected action email was not delivered to the isolated sink.", 60)


def main(image, report_path):
    identity = secrets.token_hex(8)
    prefix = "orbit-production-check-" + identity
    label = "io.orbit.production-check=" + identity
    network = prefix + "-network"
    volume = prefix + "-data"
    db, mail, app = (prefix + "-" + name for name in ("db", "mail", "app"))
    containers = []
    checks = []
    with tempfile.TemporaryDirectory(prefix="orbit-production-check-") as scratch:
        env_path = Path(scratch) / "app.env"
        password = secrets.token_urlsafe(32)
        key = base64.b64encode(secrets.token_bytes(32)).decode()
        environment = {
            "SPRING_PROFILES_ACTIVE": "prod",
            "DB_URL": f"jdbc:postgresql://{db}:5432/orbit_check",
            "DB_USERNAME": "orbit_check", "DB_PASSWORD": password,
            "ORBIT_PUBLIC_BASE_URL": "https://orbit-release.example.test",
            "ORBIT_EMAIL_VERIFICATION_REQUIRED": "true",
            "ORBIT_REGISTRATION_ENABLED": "true", "ORBIT_MAIL_ENABLED": "true",
            "ORBIT_MAIL_HOST": mail, "ORBIT_MAIL_PORT": "1025",
            "ORBIT_MAIL_STARTTLS": "false", "ORBIT_MAIL_FROM": "orbit@example.test",
            "ORBIT_MAIL_ENCRYPTION_KEY": key,
        }

        def env_file(values, path):
            path.write_text("".join(f"{name}={value}\n" for name, value in values.items()), encoding="utf-8")
            path.chmod(0o600)

        def owned(kind, name):
            try:
                value = json.loads(docker(kind, "inspect", name))[0]
                labels = value["Config"]["Labels"] if kind == "container" else value["Labels"]
                return labels.get("io.orbit.production-check") == identity
            except RuntimeError:
                return False

        def start_app():
            env_file(environment, env_path)
            docker("run", "-d", "--name", app, "--label", label, "--network", network,
                   "--env-file", str(env_path), "--publish", "127.0.0.1::8080",
                   "--read-only", "--tmpfs", "/tmp:rw,size=128m,mode=1777",
                   "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true", image)
            if app not in containers:
                containers.append(app)
            base = host_url(app, 8080)
            client = Client(base)
            config = wait(lambda: client.api("GET", "/api/auth/config"), "Production application did not become ready.")
            if config["demoEnabled"] or not config["mailEnabled"] or not config["emailVerificationRequired"]:
                raise RuntimeError("Production feature configuration mismatch.")
            return client

        try:
            docker("network", "create", "--label", label, network)
            docker("volume", "create", "--label", label, volume)
            db_env = Path(scratch) / "database.env"
            env_file({"POSTGRES_DB": "orbit_check", "POSTGRES_USER": "orbit_check", "POSTGRES_PASSWORD": password}, db_env)
            docker("run", "-d", "--name", db, "--label", label, "--network", network,
                   "--env-file", str(db_env), "--volume", volume + ":/var/lib/postgresql/data", "postgres:17-alpine")
            containers.append(db)
            wait(lambda: docker("exec", db, "pg_isready", "-h", "127.0.0.1", "-U", "orbit_check", "-d", "orbit_check"), "PostgreSQL did not become ready.", 60)
            docker("run", "-d", "--name", mail, "--label", label, "--network", network,
                   "--publish", "127.0.0.1::8025", "axllent/mailpit:v1.31.3")
            containers.append(mail)
            mail_base = host_url(mail, 8025)
            client = start_app()
            with urlopen(client.base + "/api/auth/csrf", timeout=15) as response:
                cookie = ";".join(response.headers.get_all("Set-Cookie", []))
            if not all(re.search(pattern, cookie, re.I) for pattern in (r"ORBIT_SESSION=", r"\bSecure\b", r"\bHttpOnly\b", r"SameSite=Lax")):
                raise RuntimeError("Production default session cookie attributes mismatch.")
            checks.append("Secure/HttpOnly/SameSite=Lax production default cookie")
            if not owned("container", app):
                raise RuntimeError("Refusing to replace an application outside this fixture.")
            docker("rm", "-f", app)
            # Only this disposable HTTP loopback fixture disables Secure after checking it.
            environment["ORBIT_SESSION_SECURE"] = "false"
            client = start_app()
            client.csrf()
            email = "production-" + identity + "@example.test"
            original, replacement = ("Probe!" + secrets.token_hex(20) for _ in range(2))
            user = client.api("POST", "/api/auth/register", {"name": "Production probe", "email": email, "password": original}, 201)
            client.api("POST", "/api/auth/login", {"email": email, "password": original}, 403)
            token = mail_token(mail_base, email, "Verify your Orbit email address", "verify-email")
            client.api("POST", "/api/auth/verify-email", {"token": token}, 204)
            account = client.api("POST", "/api/auth/login", {"email": email, "password": original})
            if account["id"] != user["id"] or not account["emailVerified"]:
                raise RuntimeError("Captured verification did not unlock the correct account.")
            client.csrf()
            workspace = client.api("POST", "/api/workspaces", {"name": "Production persistence"}, 201)
            path = "/api/workspaces/" + workspace["id"]
            project = client.api("POST", path + "/projects", {"name": "Release probe", "description": "Isolated fixture", "color": "#7367f0"}, 201)
            body = {"title": "Persist across restart", "description": "Unicode café, 漢字", "projectId": project["id"], "status": "IN_PROGRESS", "priority": "HIGH", "assigneeId": user["id"], "dueDate": "2026-10-10"}
            task = client.api("POST", path + "/tasks", body, 201)
            task_path = path + "/tasks/" + task["id"]
            client.api("POST", task_path + "/comments", {"body": "Production comment persists."}, 201)
            updated = client.api("POST", path + "/tasks/bulk", {"taskIds": [task["id"]], "versions": {task["id"]: task["version"]}, "priority": "URGENT"})
            task = updated[0]
            body["priority"] = "URGENT"
            filters = {"label": "Release saved view", "q": "Persist", "status": "IN_PROGRESS", "priority": "URGENT", "projectId": project["id"], "assigneeId": user["id"]}
            view = client.api("POST", path + "/saved-views", filters, 201)
            view_path = path + "/saved-views/" + view["id"]
            filters["label"] = "Versioned release view"
            view = client.api("PATCH", view_path, {**filters, "version": view["version"]})
            session = client.session()
            docker("restart", app)
            # Docker can allocate a new ephemeral host port when restarting.
            # Cookie scope is the same loopback host; preserve the exact jar.
            client.base = host_url(app, 8080)
            wait(lambda: client.api("GET", "/api/auth/config"), "Restarted application did not become ready.")
            if client.api("GET", "/api/auth/me")["id"] != user["id"] or client.session() != session:
                raise RuntimeError("Exact JDBC session/identity did not persist across restart.")
            saved = client.api("GET", task_path)
            if saved["description"] != body["description"] or len(client.api("GET", task_path + "/comments")) != 1:
                raise RuntimeError("Task/comment data did not persist across restart.")
            if saved["priority"] != "URGENT" or saved["version"] != task["version"]:
                raise RuntimeError("Bulk-updated task priority/version did not persist.")
            persisted_view = client.api("GET", view_path)
            if persisted_view != view or any(persisted_view[name] != value for name, value in filters.items()):
                raise RuntimeError("Exact private saved-view filters/version did not persist.")
            client.api("PATCH", task_path, {**body, "status": "DONE", "version": saved["version"]})
            checks.append("SMTP verification gate, task/comment/bulk mutation, exact session/task/private-view filters and versions after app-only restart")
            client.api("POST", "/api/auth/forgot-password", {"email": email}, 202)
            token = mail_token(mail_base, email, "Reset your Orbit password", "reset-password")
            client.api("POST", "/api/auth/reset-password", {"token": token, "password": replacement}, 204)
            client.api("GET", "/api/auth/me", expected=401)
            client.csrf()
            client.api("POST", "/api/auth/login", {"email": email, "password": original}, 401)
            client.api("POST", "/api/auth/login", {"email": email, "password": replacement})
            client.api("GET", task_path)
            checks.append("Captured SMTP reset revokes sessions, rejects old password, and preserves data")
            config = json.loads(docker("inspect", app))[0]
            if docker("exec", app, "id", "-u") != "10001" or not config["HostConfig"]["ReadonlyRootfs"] or config["HostConfig"]["PortBindings"].get("9091/tcp"):
                raise RuntimeError("Container user, filesystem, or management exposure mismatch.")
            for endpoint in ("health", "health/liveness", "health/readiness"):
                health = json.loads(docker("exec", app, "curl", "-fsS", "http://127.0.0.1:9091/actuator/" + endpoint))
                if health["status"] != "UP":
                    raise RuntimeError("Management health check did not report UP.")
            checks.append("UID 10001, read-only filesystem, private management, liveness/readiness UP, demo disabled")
            report = {"status": "passed", "checks": checks, "fixture": "Isolated PostgreSQL 17 and local-only Mailpit; HTTPS link origin. Secure cookie default checked first; only functional HTTP loopback fixture overrides it."}
            output = Path(report_path)
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
            print("Production-profile smoke passed: SMTP lifecycle, persistence, health, and container restrictions.")
        finally:
            for name in reversed(containers):
                if owned("container", name):
                    docker("rm", "-f", name)
            if owned("volume", volume):
                docker("volume", "rm", volume)
            if owned("network", network):
                docker("network", "rm", network)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("Usage: python3 scripts/test-production.py IMAGE REPORT.json")
    try:
        main(*sys.argv[1:])
    except (RuntimeError, ValueError, URLError, HTTPException, OSError) as error:
        sys.exit(f"Production-profile smoke failed: {error}")
