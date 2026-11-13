import assert from "node:assert/strict";
import { test } from "node:test";
import { repositoryChecks, runChecks } from "./plugins/repository-checks.js";
import { fileURLToPath } from "node:url";

const directory = fileURLToPath(new URL("../", import.meta.url));

async function fixture(run, session) {
  const toolHooks = new Map();
  const sessionHooks = new Map();
  const requests = [];
  const notices = [];
  const reports = [];
  const ctx = {
    tool: { hook: async (name, hook) => toolHooks.set(name, hook) },
    session: {
      get: async () => session ?? { location: { directory } },
      context: async () => [{ type: "user", text: "Fix this task" }],
      hook: async (name, hook) => sessionHooks.set(name, hook),
      synthetic: async (input) => notices.push(input),
      instructions: { entry: { put: async (input) => reports.push(input) } },
    },
  };
  await repositoryChecks(async (command, payload) => {
    requests.push({ command, payload });
    return run(command, payload);
  }).setup(ctx);
  return { toolHooks, sessionHooks, requests, notices, reports };
}

const push = { tool: "shell", sessionID: "session", id: "call", input: { command: "git push", workdir: ".claude" } };

test("checks run after the push and preserve its output on failure", async () => {
  const f = await fixture(() => ({ block: true, message: "Maven failed" }));
  assert.equal(f.toolHooks.has("execute.before"), false);
  const result = { ...push, status: "completed", result: { content: [{ type: "text", text: "Pushed" }] } };
  await f.toolHooks.get("execute.after")(result);
  assert.deepEqual(f.requests, [{ command: "post-tool-use", payload: { ...push.input, directory } }]);
  assert.deepEqual(result.result.content, [{ type: "text", text: "Pushed" }, { type: "text", text: "Maven failed" }]);
});

test("an unexpected checker error allows the push and reports the error", async () => {
  const f = await fixture(() => { throw new Error("Invalid check response"); });
  const result = { ...push, status: "completed", result: { content: [] } };
  await f.toolHooks.get("execute.after")(result);
  assert.match(result.result.content[0].text, /Invalid check response.*Report this failure/);
});

test("other tools do not start the shell gate", async () => {
  const f = await fixture(() => assert.fail("Unexpected check"));
  await f.toolHooks.get("execute.after")({ ...push, tool: "read" });
  assert.deepEqual(f.requests, []);
});

test("compound commit and push commands run checks after completion", async () => {
  const f = await fixture(() => ({ block: false, message: "Maven passed; log: maven.log" }));
  const input = { command: 'git add README.md && git commit -m "test" && git push origin HEAD' };
  const result = { ...push, input, status: "completed", result: { content: [{ type: "text", text: "Pushed" }] } };
  await f.toolHooks.get("execute.after")(result);
  assert.deepEqual(f.requests, [{ command: "post-tool-use", payload: { ...input, directory } }]);
  assert.deepEqual(result.result.content, [
    { type: "text", text: "Pushed" }, { type: "text", text: "Maven passed; log: maven.log" },
  ]);
});

test("successful push checks appear in the tool result", async () => {
  const f = await fixture(() => ({ block: false, message: "Maven passed; log: maven.log" }));
  const result = { ...push, status: "completed", result: { content: [{ type: "text", text: "Pushed" }] } };
  await f.toolHooks.get("execute.after")(result);
  assert.deepEqual(result.result.content, [
    { type: "text", text: "Pushed" }, { type: "text", text: "Maven passed; log: maven.log" },
  ]);
});

test("failed shell commands still report checks without replacing the shell error", async () => {
  const f = await fixture(() => ({ block: false, message: "Build failed; log: build.log" }));
  const result = { ...push, status: "error", error: { message: "Push failed" } };
  await f.toolHooks.get("execute.after")(result);
  assert.deepEqual(result.error, { message: "Push failed" });
  assert.deepEqual(f.reports, [{ sessionID: "session", key: "sonar-analyzer-commons.checks", value: "Build failed; log: build.log" }]);
});

test("failed CI adds a continuation in the same session", async () => {
  const f = await fixture(() => ({ block: true, message: "Fix the failed build" }));
  await f.sessionHooks.get("experimental.idle")({ sessionID: "session", background: [] });
  assert.equal(f.requests[0].command, "stop");
  assert.deepEqual(f.requests[0].payload.messages, [{ type: "user", text: "Fix this task" }]);
  assert.deepEqual(f.notices, [{
    sessionID: "session", text: "Fix the failed build", description: "Repository checks require action",
    metadata: { source: "sonar-analyzer-commons.checks" },
  }]);
});

test("complete checks report status without starting another model turn", async () => {
  const f = await fixture(() => ({ block: false, message: "Complete: all checks passed" }));
  await f.sessionHooks.get("experimental.idle")({ sessionID: "session" });
  assert.deepEqual(f.notices, []);
  assert.deepEqual(f.reports, [{ sessionID: "session", key: "sonar-analyzer-commons.checks", value: "Complete: all checks passed" }]);
});

test("child sessions do not receive the completion gate", async () => {
  const f = await fixture(() => assert.fail("Unexpected check"), { parentID: "parent", location: { directory } });
  await f.sessionHooks.get("experimental.idle")({ sessionID: "child" });
  assert.deepEqual(f.requests, []);
});

test("an idle checker error is reported without an endless model loop", async () => {
  const f = await fixture(() => { throw new Error("Python unavailable"); });
  await f.sessionHooks.get("experimental.idle")({ sessionID: "session" });
  assert.match(f.reports[0].value, /Incomplete:.*Python unavailable/);
  assert.deepEqual(f.notices, []);
});

test("the plugin and Python adapter exchange a real non-push request", async () => {
  assert.deepEqual(await runChecks("post-tool-use", { command: "git status", directory }), { block: false, message: "" });
});

test("Python is not loaded from relative working-directory entries", async () => {
  const previous = process.env.PATH;
  try {
    process.env.PATH = ".";
    const f = await fixture(runChecks);
    const result = { ...push, status: "completed", result: { content: [] } };
    await f.toolHooks.get("execute.after")(result);
    assert.deepEqual(result.result.content, [{ type: "text", text: "Skipped repository checks: Python 3 is unavailable." }]);
  } finally {
    process.env.PATH = previous;
  }
});
