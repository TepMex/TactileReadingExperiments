import { describe, expect, test } from "bun:test";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { Glob } from "bun";
import { injectStaticSiteFooter } from "./inject-static-site-footer";

const MARKER = "<!-- deploy-footer -->";

function markerCount(html: string): number {
  return html.split(MARKER).length - 1;
}

describe("injectStaticSiteFooter", () => {
  test("landing pages ship a closed marker pair", async () => {
    const glob = new Glob("*/site/index.html");
    const files: string[] = [];
    for await (const file of glob.scan(".")) {
      files.push(file);
    }
    for (const file of files) {
      const html = await Bun.file(file).text();
      expect(markerCount(html), file).toBe(2);
    }
  });

  test("replaces a single placeholder marker", async () => {
    const dir = await mkdtemp(path.join(tmpdir(), "footer-"));
    const htmlPath = path.join(dir, "index.html");
    await Bun.write(
      htmlPath,
      `<main>\n  <!-- deploy-footer -->\n</main>\n`,
    );
    process.env.DEPLOYED_AT = "2026-09-23T00:00:00 UTC";
    await injectStaticSiteFooter(htmlPath);
    const html = await Bun.file(htmlPath).text();
    expect(markerCount(html)).toBe(2);
    expect(html).toContain("2026-09-23T00:00:00 UTC");
    expect(html).toContain("<footer class=\"site-footer\"");
    await injectStaticSiteFooter(htmlPath);
    const again = await Bun.file(htmlPath).text();
    expect(markerCount(again)).toBe(2);
    expect(again.match(/<footer class="site-footer"/g)?.length).toBe(1);
    await rm(dir, { recursive: true });
  });

  test("replaces an existing marker pair", async () => {
    const dir = await mkdtemp(path.join(tmpdir(), "footer-"));
    const htmlPath = path.join(dir, "index.html");
    await Bun.write(
      htmlPath,
      `<main>\n  <!-- deploy-footer -->\n  <footer>old</footer>\n  <!-- deploy-footer -->\n</main>\n`,
    );
    process.env.DEPLOYED_AT = "2026-09-23T01:00:00 UTC";
    await injectStaticSiteFooter(htmlPath);
    const html = await Bun.file(htmlPath).text();
    expect(html).not.toContain("<footer>old</footer>");
    expect(html).toContain("2026-09-23T01:00:00 UTC");
    expect(markerCount(html)).toBe(2);
    await rm(dir, { recursive: true });
  });
});
