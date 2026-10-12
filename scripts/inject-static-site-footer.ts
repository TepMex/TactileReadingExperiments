import path from "path";
import { getDeployMetadata } from "./deploy-metadata";

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function footerHtml(deployedAt: string, commitMessage: string): string {
  return `<footer class="site-footer" aria-label="Build information">
  <p class="site-footer__line"><span class="site-footer__label">Deployed</span> ${escapeHtml(deployedAt)}</p>
  <p class="site-footer__line"><span class="site-footer__label">Commit</span> ${escapeHtml(commitMessage)}</p>
</footer>`;
}

const MARKER = "<!-- deploy-footer -->";

/** Inject or replace deploy footer in a static landing-page HTML file. */
export async function injectStaticSiteFooter(htmlPath: string): Promise<void> {
  const { deployedAt, commitMessage } = await getDeployMetadata();
  const footer = footerHtml(deployedAt, commitMessage);
  let html = await Bun.file(htmlPath).text();

  const block = `${MARKER}\n${footer}\n${MARKER}`;

  if (html.includes(MARKER)) {
    const start = html.indexOf(MARKER);
    const end = html.indexOf(MARKER, start + MARKER.length);
    // A lone marker is an insertion placeholder (opening and closing pair not required).
    const after = end === -1 ? start + MARKER.length : end + MARKER.length;
    html = html.slice(0, start) + block + html.slice(after);
  } else if (html.includes("</main>")) {
    html = html.replace("</main>", `${block}\n  </main>`);
  } else if (html.includes("</body>")) {
    html = html.replace("</body>", `${block}\n</body>`);
  } else {
    throw new Error(`No insertion point in ${htmlPath}`);
  }

  await Bun.write(htmlPath, html);
}

if (import.meta.main) {
  const paths = process.argv.slice(2);
  if (paths.length === 0) {
    console.error("Usage: bun run scripts/inject-static-site-footer.ts <index.html> [...]");
    process.exit(1);
  }
  for (const p of paths) {
    const resolved = path.resolve(p);
    await injectStaticSiteFooter(resolved);
    console.log(`Updated ${resolved}`);
  }
}
