import http from "node:http";
import { execFile } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

const host = process.env.PDF_RENDERER_HOST || "127.0.0.1";
const port = Number(process.env.PDF_RENDERER_PORT || 8787);
const token = process.env.PDF_RENDERER_TOKEN || "";
const chrome = process.env.CHROME_PATH || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
// The backend accepts up to 8 MiB of HTML; Base64 and JSON make the request larger.
const maxBody = 12 * 1024 * 1024;
const configuredConcurrency = Number(process.env.PDF_RENDERER_MAX_CONCURRENCY || 2);
const maxConcurrency = Number.isSafeInteger(configuredConcurrency) && configuredConcurrency > 0
  ? configuredConcurrency : 2;
const renderCsp = "default-src 'none'; img-src data: blob:; style-src 'unsafe-inline'; font-src data:; script-src 'none'; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";
let activeRenders = 0;

if (!token) throw new Error("PDF_RENDERER_TOKEN is required");

function render(input, output, profile) {
  return new Promise((resolve, reject) => {
    execFile(chrome, ["--headless=new", "--disable-gpu", "--disable-dev-shm-usage",
      "--no-sandbox", "--disable-javascript", "--host-resolver-rules=MAP * ~NOTFOUND",
      "--no-pdf-header-footer", `--user-data-dir=${profile}`,
      `--print-to-pdf=${output}`, `file://${input}`], { timeout: 60000 }, (error) => error ? reject(error) : resolve());
  });
}

function hardenHtml(html) {
  const policy = `<meta http-equiv="Content-Security-Policy" content="${renderCsp}">`;
  const head = /<head(?:\s[^>]*)?>/i;
  return head.test(html) ? html.replace(head, match => `${match}${policy}`) : `${policy}${html}`;
}

const server = http.createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/health") {
    response.writeHead(200, { "Content-Type": "application/json" });
    response.end(JSON.stringify({ status: "ok" }));
    return;
  }
  if (request.method !== "POST" || request.url !== "/render") {
    response.writeHead(404).end(); return;
  }
  if (request.headers["x-renderer-token"] !== token) {
    response.writeHead(401).end(); return;
  }
  let size = 0; const chunks = [];
  request.on("data", (chunk) => {
    size += chunk.length;
    if (size > maxBody) request.destroy(); else chunks.push(chunk);
  });
  request.on("end", async () => {
    if (activeRenders >= maxConcurrency) {
      response.writeHead(429, { "Content-Type": "application/json", "Retry-After": "2" });
      response.end(JSON.stringify({ error: "PDF_RENDERER_BUSY" }));
      return;
    }
    let work;
    activeRenders++;
    try {
      const payload = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      const html = Buffer.from(payload.htmlBase64 || "", "base64");
      if (!html.length) throw new Error("HTML payload is empty");
      work = await mkdtemp(join(tmpdir(), "finals-pdf-"));
      const input = join(work, "document.html"), output = join(work, "document.pdf");
      await writeFile(input, hardenHtml(html.toString("utf8")), { flag: "wx" });
      await render(input, output, join(work, "chrome-profile"));
      const pdf = await readFile(output);
      response.writeHead(200, { "Content-Type": "application/pdf", "Content-Length": pdf.length });
      response.end(pdf);
    } catch (error) {
      response.writeHead(500, { "Content-Type": "application/json" });
      response.end(JSON.stringify({ error: "PDF_RENDER_FAILED" }));
    } finally {
      activeRenders--;
      if (work) await rm(work, { recursive: true, force: true });
    }
  });
});

server.listen(port, host, () => process.stdout.write(`PDF renderer listening on http://${host}:${port}\n`));
