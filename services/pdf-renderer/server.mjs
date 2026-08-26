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

if (!token) throw new Error("PDF_RENDERER_TOKEN is required");

function render(input, output) {
  return new Promise((resolve, reject) => {
    execFile(chrome, ["--headless=new", "--disable-gpu", "--disable-dev-shm-usage",
      "--no-sandbox", "--no-pdf-header-footer", "--user-data-dir=/tmp/chrome-profile",
      `--print-to-pdf=${output}`, `file://${input}`], { timeout: 60000 }, (error) => error ? reject(error) : resolve());
  });
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
    let work;
    try {
      const payload = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      const html = Buffer.from(payload.htmlBase64 || "", "base64");
      if (!html.length) throw new Error("HTML payload is empty");
      work = await mkdtemp(join(tmpdir(), "finals-pdf-"));
      const input = join(work, "document.html"), output = join(work, "document.pdf");
      await writeFile(input, html, { flag: "wx" });
      await render(input, output);
      const pdf = await readFile(output);
      response.writeHead(200, { "Content-Type": "application/pdf", "Content-Length": pdf.length });
      response.end(pdf);
    } catch (error) {
      response.writeHead(500, { "Content-Type": "application/json" });
      response.end(JSON.stringify({ error: "PDF_RENDER_FAILED" }));
    } finally {
      if (work) await rm(work, { recursive: true, force: true });
    }
  });
});

server.listen(port, host, () => process.stdout.write(`PDF renderer listening on http://${host}:${port}\n`));
