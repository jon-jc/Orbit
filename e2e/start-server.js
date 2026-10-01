import { readFile, stat } from "node:fs/promises";
import { spawn } from "node:child_process";
import { join } from "node:path";

const pom = (await readFile(join(process.cwd(), "pom.xml"), "utf8"))
  .replace(/<parent>[\s\S]*?<\/parent>/, "")
  .split("<dependencies>")[0];
const artifact = pom.match(/<artifactId>([\w.-]+)<\/artifactId>/)?.[1];
const version = pom.match(/<version>([\w.-]+)<\/version>/)?.[1];
if (!artifact || !version)
  throw new Error(
    "Browser startup requires explicit root POM artifactId and version.",
  );
const jar = join(process.cwd(), "target", `${artifact}-${version}.jar`);
try {
  if (!(await stat(jar)).isFile()) throw new Error();
} catch {
  throw new Error(
    `Build Orbit with Maven before running browser tests: ${artifact}-${version}.jar is missing from target.`,
  );
}
const server = spawn(
  "java",
  ["-jar", jar, "--spring.profiles.active=local", "--server.address=127.0.0.1"],
  { stdio: "inherit" },
);
for (const signal of ["SIGINT", "SIGTERM"])
  process.on(signal, () => server.kill(signal));
server.on("error", (error) => {
  console.error(error.message);
  process.exitCode = 1;
});
server.on("exit", (code, signal) => {
  process.exitCode = code ?? (signal ? 1 : 0);
});
