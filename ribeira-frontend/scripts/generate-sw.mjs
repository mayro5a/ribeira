// Gera out/sw.js após `next build` (ADR-0006): pré-armazena todos os arquivos do
// export estático e versiona o cache pelo hash do conteúdo.
import { createHash } from "node:crypto";
import { readFile, readdir, writeFile } from "node:fs/promises";
import { join, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";

const OUT = fileURLToPath(new URL("../out", import.meta.url));
const TEMPLATE = fileURLToPath(new URL("./sw-template.js", import.meta.url));
const EXCLUDED = [/\.map$/, /^sw\.js$/, /^404/, /^_not-found/];

async function listFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = await Promise.all(
    entries.map((entry) => {
      const path = join(dir, entry.name);
      return entry.isDirectory() ? listFiles(path) : [path];
    }),
  );
  return files.flat();
}

const files = (await listFiles(OUT))
  .map((file) => relative(OUT, file).split(sep).join("/"))
  .filter((file) => !EXCLUDED.some((pattern) => pattern.test(file)))
  .sort();

const hash = createHash("sha256");
for (const file of files) {
  hash.update(file);
  hash.update(await readFile(join(OUT, file)));
}
const version = hash.digest("hex").slice(0, 16);
const urls = files.map((file) => `/${file}`);

const template = await readFile(TEMPLATE, "utf8");
const sw = template
  .replace("__VERSION__", version)
  .replace("__PRECACHE__", JSON.stringify(urls, null, 2));
await writeFile(join(OUT, "sw.js"), sw);
console.log(`Service Worker gerado: versão ${version}, ${urls.length} arquivos pré-armazenados.`);
