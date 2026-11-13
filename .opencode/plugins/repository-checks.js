import { execFile } from "node:child_process";
import { constants } from "node:fs";
import { access } from "node:fs/promises";
import { delimiter, isAbsolute, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../../", import.meta.url));
const script = fileURLToPath(new URL("../hooks.py", import.meta.url));
const id = "sonar-analyzer-commons.checks";
const pushTimeout = 3_600_000;
const stopTimeout = 60_000;

/** Resolve Python from absolute tool directories before starting a process. */
async function pythonExecutable() {
  const name = process.platform === "win32" ? "python3.exe" : "python3";
  const candidates = await Promise.all((process.env.PATH ?? "").split(delimiter).filter(isAbsolute).map(async (directory) => {
    const candidate = join(directory, name);
    try {
      await access(candidate, constants.X_OK);
      return candidate;
    } catch {
      return undefined;
    }
  }));
  return candidates.find((candidate) => candidate !== undefined);
}

/** Run the Python adapter and read its decision. */
export async function runChecks(command, payload) {
  const python = await pythonExecutable();
  if (!python) return { block: false, message: "Skipped repository checks: Python 3 is unavailable." };
  return new Promise((resolve, reject) => {
    const child = execFile(python, [script, command], {
      cwd: root,
      timeout: command === "post-tool-use" ? pushTimeout : stopTimeout,
      maxBuffer: 1024 * 1024,
    }, (error, stdout, stderr) => {
      if (error) {
        reject(new Error(`Repository check unavailable: ${stderr.trim() || error.message}`));
        return;
      }
      try {
        const result = JSON.parse(stdout);
        if (typeof result.block !== "boolean" || typeof result.message !== "string") {
          throw new TypeError("Invalid repository check response");
        }
        resolve(result);
      } catch (error) {
        reject(error);
      }
    });
    child.stdin.on("error", reject);
    child.stdin.end(JSON.stringify(payload));
  });
}

/** Create the OpenCode plugin with a separate check runner for tests. */
export function repositoryChecks(run = runChecks) {
  return {
    id,
    async setup(ctx) {
      await ctx.tool.hook("execute.after", async (event) => {
        if (event.tool !== "shell") return;
        let message;
        try {
          const session = await ctx.session.get({ sessionID: event.sessionID });
          const result = await run("post-tool-use", { ...event.input, directory: session.location.directory });
          message = result.message;
        } catch (error) {
          message = `Repository check unavailable: ${error.message}. Report this failure in your final reply.`;
        }
        if (message && event.status === "completed") {
          event.result.content = [...event.result.content, { type: "text", text: message }];
        } else if (message) {
          await ctx.session.instructions.entry.put({ sessionID: event.sessionID, key: id, value: message });
        }
      });

      await ctx.session.hook("experimental.idle", async (event) => {
        try {
          const session = await ctx.session.get({ sessionID: event.sessionID });
          if (session.parentID) return;
          const messages = await ctx.session.context({ sessionID: event.sessionID });
          const result = await run("stop", {
            session_id: event.sessionID,
            directory: session.location.directory,
            messages,
          });
          if (result.message) {
            await ctx.session.instructions.entry.put({ sessionID: event.sessionID, key: id, value: result.message });
          }
          if (result.block) {
            await ctx.session.synthetic({
              sessionID: event.sessionID,
              text: result.message,
              description: "Repository checks require action",
              metadata: { source: id },
            });
          }
        } catch (error) {
          await ctx.session.instructions.entry.put({
            sessionID: event.sessionID,
            key: id,
            value: `Incomplete: Repository check unavailable: ${error.message}`,
          });
        }
      });
    },
  };
}

export default repositoryChecks();
