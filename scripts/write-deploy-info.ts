import path from "path";
import { getDeployMetadata } from "./deploy-metadata";

function escapeTsString(value: string): string {
  return JSON.stringify(value);
}

/** Write `src/lib/deploy-info.ts` for a Bun/React app (imported as @/lib/deploy-info). */
export async function writeDeployInfoTs(targetFile: string): Promise<void> {
  const { deployedAt, commitMessage } = await getDeployMetadata();
  const body = `/** Generated at build/dev time — do not edit by hand. */\nexport const DEPLOYED_AT = ${escapeTsString(deployedAt)};\nexport const COMMIT_MESSAGE = ${escapeTsString(commitMessage)};\n`;
  await Bun.write(targetFile, body);
}

if (import.meta.main) {
  const projectDir = process.argv[2];
  if (!projectDir) {
    console.error("Usage: bun run scripts/write-deploy-info.ts <project-dir>");
    process.exit(1);
  }
  const target = path.join(path.resolve(projectDir), "src/lib/deploy-info.ts");
  await writeDeployInfoTs(target);
  console.log(`Wrote ${target}`);
}
