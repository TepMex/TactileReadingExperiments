import { $ } from "bun";

export type DeployMetadata = {
  deployedAt: string;
  commitMessage: string;
};

/** ISO-like local timestamp for display (UTC, no sub-second noise). */
function formatDeployedAt(date: Date): string {
  return date.toISOString().replace(/\.\d{3}Z$/, " UTC");
}

async function gitOne(args: string[]): Promise<string> {
  const result = await $`git ${args}`.quiet().nothrow();
  if (result.exitCode !== 0) return "";
  return result.text().trim();
}

/** Latest commit subject on the current branch (empty if git is unavailable). */
export async function getCommitMessage(): Promise<string> {
  const message = await gitOne(["log", "-1", "--format=%s"]);
  return message || "unknown commit";
}

/**
 * Deployment timestamp: CI sets DEPLOYED_AT; otherwise build time in UTC.
 */
export function getDeployedAt(): string {
  const fromEnv = process.env.DEPLOYED_AT?.trim();
  if (fromEnv) return fromEnv;
  return formatDeployedAt(new Date());
}

export async function getDeployMetadata(): Promise<DeployMetadata> {
  const [deployedAt, commitMessage] = await Promise.all([
    Promise.resolve(getDeployedAt()),
    getCommitMessage(),
  ]);
  return { deployedAt, commitMessage };
}
