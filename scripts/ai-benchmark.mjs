#!/usr/bin/env node
// Measures tokens/sec and JSON-validity rate of the configured model on this machine, using the real A1 prompt
// (backend/src/main/resources/prompts/profile-extract.md) and the same JSON schema the backend sends.
// Writes the result into docs/AI_DESIGN.md between the ai-benchmark markers.
//
// Usage: node scripts/ai-benchmark.mjs [--runs 10] [--no-write]
// Reads AI_BASE_URL, AI_MODEL, AI_API_KEY from the environment or the repo's .env file.

import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import os from "node:os";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
const runsAt = args.indexOf("--runs");
const runs = runsAt >= 0 ? Number(args[runsAt + 1]) || 10 : 10;
const write = !args.includes("--no-write");

// ---- config (.env is optional; real env vars win)
const env = { ...loadDotEnv(join(root, ".env")), ...process.env };
const baseUrl = (env.AI_BASE_URL || "http://localhost:11434/v1").replace(/\/+$/, "");
const model = env.AI_MODEL || "qwen3:4b-instruct-2507";
const apiKey = env.AI_API_KEY || "ollama";
const timeoutMs = (Number(env.AI_TIMEOUT_SECONDS) || 90) * 1000;

// ---- prompt + schema (kept identical to ProfileService.extractionSchema)
const TAGS = [
  "local_food", "street_food", "cafes", "nightlife", "history", "architecture", "museums", "art", "crafts", "markets",
  "nature", "beaches", "hiking", "wildlife", "water_sports", "adventure", "wellness", "photography", "music",
  "local_culture", "spiritual", "shopping",
];
const schema = {
  type: "object",
  additionalProperties: false,
  required: ["extraInterests", "mustHaves", "dealbreakers", "summary"],
  properties: {
    extraInterests: {
      type: "array",
      maxItems: 6,
      items: {
        type: "object",
        additionalProperties: false,
        required: ["tag", "weight", "evidence"],
        properties: {
          tag: { type: "string", enum: TAGS },
          weight: { type: "number", minimum: 0, maximum: 1 },
          evidence: { type: "string", maxLength: 120 },
        },
      },
    },
    mustHaves: { type: "array", maxItems: 5, items: { type: "string", maxLength: 80 } },
    dealbreakers: { type: "array", maxItems: 5, items: { type: "string", maxLength: 80 } },
    summary: { type: "string", maxLength: 240 },
  },
};

const SAMPLES = [
  { rated: "beaches, nightlife", text: "I love slow mornings in little cafes, then a pottery or weaving workshop with local artisans. No big parties." },
  { rated: "history, local_food", text: "Give me trekking trails, waterfalls and birding at dawn. I hate long bus rides and I need a vegetarian kitchen." },
  { rated: "none", text: "Live jazz bars, street food stalls at midnight and flea markets. I'm on a tight budget and must have wifi." },
  { rated: "nature, wellness", text: "Old Portuguese churches, quiet museums, and somewhere for yoga. Absolutely no crowds or touts." },
  { rated: "adventure", text: "Surfing lessons, a kayak trip through mangroves and a sunset photography walk. A pool is a must." },
];

const template = parsePrompt(readFileSync(join(root, "backend/src/main/resources/prompts/profile-extract.md"), "utf8"));

// ---- run
console.log(`Benchmarking ${model} at ${baseUrl} (${runs} runs, 1 warm-up)…`);
const results = [];
for (let i = -1; i < runs; i++) {
  const sample = SAMPLES[(i + SAMPLES.length) % SAMPLES.length];
  const messages = template.messages.map((m) => ({
    role: m.role,
    content: m.content.replaceAll("{{tags}}", TAGS.join(", ")).replaceAll("{{rated}}", sample.rated).replaceAll("{{description}}", sample.text),
  }));
  const r = await call(messages, template.temperature);
  if (i < 0) {
    console.log(`  warm-up: ${r.error ? "error: " + r.error : `${r.latencyMs} ms`}`);
    continue;
  }
  results.push(r);
  console.log(
    `  run ${i + 1}: ${r.error ? "ERROR " + r.error : `${r.latencyMs} ms, ${r.completionTokens ?? "?"} tokens, ${r.tps?.toFixed(1) ?? "?"} tok/s, ${r.valid ? "valid" : "INVALID: " + r.problems.join("; ")}`}`,
  );
}

const ok = results.filter((r) => !r.error);
const valid = ok.filter((r) => r.valid).length;
const tpsValues = ok.map((r) => r.tps).filter((v) => v != null);
const summary = {
  date: new Date().toISOString().slice(0, 10),
  model,
  host: `${os.type()} ${os.release()}, ${os.cpus()[0]?.model?.trim() ?? "unknown CPU"}, ${Math.round(os.totalmem() / 2 ** 30)} GB RAM`,
  runs: results.length,
  errors: results.length - ok.length,
  validity: ok.length ? valid / ok.length : 0,
  medianLatencyMs: median(ok.map((r) => r.latencyMs)),
  p90LatencyMs: percentile(ok.map((r) => r.latencyMs), 0.9),
  medianTps: median(tpsValues),
  medianPromptTokens: median(ok.map((r) => r.promptTokens).filter((v) => v != null)),
  medianCompletionTokens: median(ok.map((r) => r.completionTokens).filter((v) => v != null)),
};

console.log("\nSummary");
console.table(summary);

if (write) {
  const file = join(root, "docs/AI_DESIGN.md");
  const block = [
    "<!-- ai-benchmark:start -->",
    `_Last run ${summary.date} by \`scripts/ai-benchmark\` (${summary.runs} runs of the A1 \`${template.version}\` prompt after one warm-up)._`,
    "",
    "| Metric | Value |",
    "|---|---|",
    `| Model | \`${summary.model}\` |`,
    `| Host | ${summary.host} |`,
    `| JSON-validity rate (first attempt, schema + vocabulary checks) | ${(summary.validity * 100).toFixed(0)}% (${valid}/${ok.length}) |`,
    `| Transport errors | ${summary.errors} |`,
    `| Median generation speed | ${fmt(summary.medianTps, 1)} tokens/s |`,
    `| Median latency per call | ${fmt(summary.medianLatencyMs, 0)} ms (p90 ${fmt(summary.p90LatencyMs, 0)} ms) |`,
    `| Median prompt / completion tokens | ${fmt(summary.medianPromptTokens, 0)} / ${fmt(summary.medianCompletionTokens, 0)} |`,
    "",
    "Tokens/s is completion tokens divided by wall-clock latency, so it includes prompt processing — it is the speed a user actually waits for.",
    "<!-- ai-benchmark:end -->",
  ].join("\n");
  let doc = existsSync(file) ? readFileSync(file, "utf8") : "# AI design\n";
  doc = /<!-- ai-benchmark:start -->[\s\S]*<!-- ai-benchmark:end -->/.test(doc)
    ? doc.replace(/<!-- ai-benchmark:start -->[\s\S]*<!-- ai-benchmark:end -->/, block)
    : `${doc.trimEnd()}\n\n## Benchmark\n\n${block}\n`;
  writeFileSync(file, doc);
  console.log(`\nWrote results to ${file}`);
}

if (ok.length === 0) process.exitCode = 1;

// ---- helpers
async function call(messages, temperature) {
  const started = performance.now();
  try {
    const res = await fetch(`${baseUrl}/chat/completions`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${apiKey}` },
      body: JSON.stringify({
        model,
        messages,
        temperature,
        stream: false,
        response_format: { type: "json_schema", json_schema: { name: "profile", strict: true, schema } },
      }),
      signal: AbortSignal.timeout(timeoutMs),
    });
    const latencyMs = Math.round(performance.now() - started);
    if (!res.ok) return { error: `HTTP ${res.status}`, latencyMs };
    const body = await res.json();
    const content = clean(body.choices?.[0]?.message?.content ?? "");
    const completionTokens = body.usage?.completion_tokens ?? null;
    const problems = validate(content);
    return {
      latencyMs,
      promptTokens: body.usage?.prompt_tokens ?? null,
      completionTokens,
      tps: completionTokens ? completionTokens / (latencyMs / 1000) : null,
      valid: problems.length === 0,
      problems,
    };
  } catch (e) {
    return { error: e.name === "TimeoutError" ? "timeout" : e.message, latencyMs: Math.round(performance.now() - started) };
  }
}

/** Mirrors ProfileValidator.validateExtraction. */
function validate(content) {
  let x;
  try {
    x = JSON.parse(content);
  } catch {
    return ["not JSON"];
  }
  const p = [];
  if (!Array.isArray(x.extraInterests)) p.push("extraInterests missing");
  if (!Array.isArray(x.mustHaves)) p.push("mustHaves missing");
  if (!Array.isArray(x.dealbreakers)) p.push("dealbreakers missing");
  if (typeof x.summary !== "string" || !x.summary.trim() || x.summary.length > 240) p.push("bad summary");
  if (p.length) return p;
  if (x.extraInterests.length > 6) p.push("too many interests");
  for (const i of x.extraInterests) {
    if (!TAGS.includes(i.tag)) p.push(`unknown tag ${i.tag}`);
    if (typeof i.weight !== "number" || i.weight < 0 || i.weight > 1) p.push("bad weight");
    if (typeof i.evidence !== "string" || !i.evidence.trim() || i.evidence.length > 120) p.push("bad evidence");
  }
  for (const k of ["mustHaves", "dealbreakers"]) {
    if (x[k].length > 5 || x[k].some((s) => typeof s !== "string" || !s.trim() || s.length > 80)) p.push(`bad ${k}`);
  }
  return p;
}

function clean(s) {
  s = s.replace(/<think>[\s\S]*?<\/think>/g, "").trim();
  const fence = s.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/);
  return fence ? fence[1] : s;
}

/** Same format as PromptTemplate.parse in the backend. */
function parsePrompt(source) {
  const text = source.replace(/\r\n/g, "\n");
  const end = text.indexOf("\n---\n", 4);
  const front = Object.fromEntries(
    text.slice(4, end).split("\n").map((l) => l.split(":").map((s) => s.trim())).filter((kv) => kv.length >= 2),
  );
  const messages = [];
  let current = null;
  for (const line of text.slice(end + 5).split("\n")) {
    const m = line.match(/^## (system|user|assistant)\s*$/);
    if (m) {
      if (current) messages.push({ ...current, content: current.content.trim() });
      current = { role: m[1], content: "" };
    } else if (current) current.content += line + "\n";
  }
  if (current) messages.push({ ...current, content: current.content.trim() });
  return { version: front.version, temperature: Number(front.temperature ?? 0.3), messages };
}

function loadDotEnv(path) {
  if (!existsSync(path)) return {};
  const out = {};
  for (const raw of readFileSync(path, "utf8").split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#") || !line.includes("=")) continue;
    const i = line.indexOf("=");
    out[line.slice(0, i).trim()] = line.slice(i + 1).replace(/\s+#.*$/, "").trim();
  }
  return out;
}

function median(xs) {
  return percentile(xs, 0.5);
}

function percentile(xs, q) {
  if (!xs.length) return null;
  const s = [...xs].sort((a, b) => a - b);
  return s[Math.min(s.length - 1, Math.floor(q * s.length))];
}

function fmt(v, digits) {
  return v == null ? "n/a" : v.toFixed(digits);
}
