#!/usr/bin/env node
// Seeds the classroom demo through the public REST API: a 5-day Goa trip with four travelers of clearly different
// budgets, paces and interests, a shortlist of real OpenStreetMap places with reasons and reactions, a price
// comparison, pre-computed (cached) AI results — trade-offs, suggestions and the draft itinerary — one decided
// change in the decision log, and one open proposal with partial votes, ready to be decided live.
//
// Usage: node scripts/seed.mjs [--reset]
//   --reset   delete the demo trip first (via `docker compose exec postgres psql`), then seed it again.
// Reads NEXT_PUBLIC_API_URL (default http://localhost:8080) from the environment or the repo's .env file.
// With AI on, the run takes a few minutes because every AI result is computed once and cached; with
// AI_ENABLED=false it takes under a minute and the app shows the rule-based results.

import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
const reset = args.includes("--reset");
const env = { ...loadDotEnv(join(root, ".env")), ...process.env };
const API = (env.SEED_API_URL || env.NEXT_PUBLIC_API_URL || "http://localhost:8080").replace(/\/+$/, "");
const APP = (env.APP_BASE_URL || "http://localhost:3000").replace(/\/+$/, "");

const PASSWORD = "meridian-demo";
const DOMAIN = "meridian.demo";
const TRIP_NAME = "Goa with friends";

// ---- the four travelers: different budgets, paces and interests --------------------------------------------

const PEOPLE = {
  asha: {
    name: "Asha",
    quiz: {
      interestRatings: { history: 4, architecture: 4, local_food: 4, markets: 3, museums: 2, beaches: 1, nightlife: 0 },
      pace: "balanced",
      budget: { stay: "mid", food: "mid", activities: "mid" },
      crowdTolerance: 40,
      hiddenGems: 70,
      dietary: [],
      accessNeeds: [],
      mustHaves: ["Fontainhas walk"],
      dealbreakers: [],
      idealTrip:
        "Old Portuguese quarters, long lunches of Goan fish curry and a local market or two. I'd rather skip the party beaches.",
    },
    trip: { budgetMin: 25000, budgetMax: 40000 },
  },
  meera: {
    name: "Meera",
    quiz: {
      interestRatings: { nature: 4, wellness: 4, beaches: 3, cafes: 3, local_food: 2, nightlife: 0, adventure: 0 },
      pace: "slow",
      budget: { stay: "low", food: "low", activities: "low" },
      crowdTolerance: 10,
      hiddenGems: 90,
      dietary: ["vegetarian"],
      accessNeeds: ["quiet_spaces"],
      mustHaves: [],
      dealbreakers: ["Loud clubs"],
      idealTrip:
        "Quiet beaches, yoga in the morning, vegetarian thalis and a walk through a spice plantation. No crowds, no rush.",
    },
    trip: { budgetMin: 12000, budgetMax: 20000 },
  },
  dev: {
    name: "Dev",
    quiz: {
      interestRatings: { adventure: 4, water_sports: 4, nightlife: 4, music: 3, beaches: 3, museums: 0 },
      pace: "packed",
      budget: { stay: "high", food: "mid", activities: "high" },
      crowdTolerance: 85,
      hiddenGems: 30,
      dietary: [],
      accessNeeds: [],
      mustHaves: ["Scuba diving"],
      dealbreakers: [],
      idealTrip:
        "Kayaking, scuba diving off Grande Island and a live gig at night. I want to fit in as much as possible.",
    },
    trip: { budgetMin: 45000, budgetMax: 70000 },
  },
  kabir: {
    name: "Kabir",
    quiz: {
      interestRatings: { photography: 4, history: 3, local_culture: 4, hiking: 3, beaches: 2, shopping: 0 },
      pace: "balanced",
      budget: { stay: "mid", food: "low", activities: "mid" },
      crowdTolerance: 50,
      hiddenGems: 60,
      dietary: [],
      accessNeeds: [],
      mustHaves: ["Sunset at a fort"],
      dealbreakers: [],
      idealTrip:
        "Photograph forts at sunset, walk through villages and eat where the locals eat.",
    },
    trip: { budgetMin: 20000, budgetMax: 30000 },
  },
};

// Searched on OpenStreetMap (Nominatim, limited to the trip's area); the coordinates are used for a map pin only
// when the search finds nothing.
const SHORTLIST = [
  { q: "Fort Aguada", category: "SIGHTS", by: "kabir", reason: "Sunset photos from the ramparts", at: [15.492, 73.7737] },
  { q: "Fontainhas", category: "SIGHTS", by: "asha", reason: "The Latin quarter: a slow walk past old houses", at: [15.497, 73.833] },
  { q: "Basilica of Bom Jesus", category: "SIGHTS", by: "asha", reason: "Old Goa's baroque churches", at: [15.5009, 73.9116] },
  { q: "Sahakari Spice Farm", category: "ACTIVITIES", by: "meera", reason: "Spice plantation walk and a vegetarian lunch", at: [15.4144, 74.009] },
  { q: "Chapora Fort", category: "SIGHTS", by: "kabir", reason: "Views over the Chapora river", at: [15.606, 73.7366] },
  { q: "Anjuna Flea Market", category: "SIGHTS", by: "asha", reason: "Wednesday market, crafts and food", at: [15.5733, 73.74] },
  { q: "Mapusa Market", category: "SIGHTS", by: "kabir", reason: "Where locals shop on Fridays", at: [15.5916, 73.809] },
  { q: "Grande Island", category: "ACTIVITIES", by: "dev", reason: "Scuba diving and snorkelling", at: [15.35, 73.78] },
  { q: "Palolem Beach", category: "SIGHTS", by: "dev", reason: "Kayaking in the bay", at: [15.01, 74.023] },
  { q: "Butterfly Beach", category: "SIGHTS", by: "meera", reason: "A quiet cove you reach by boat", at: [15.0217, 74.001] },
  { q: "Cabo de Rama", category: "SIGHTS", by: "kabir", reason: "Clifftop fort, almost no crowds", at: [15.0886, 73.9196] },
  { q: "Hilltop Vagator", category: "ACTIVITIES", by: "dev", reason: "Live music night", at: [15.604, 73.736] },
  { q: "Ritz Classic Panjim", category: "FOOD", by: "asha", reason: "Fish thali the locals queue for", at: [15.4989, 73.8262] },
  { q: "Vinayak Family Restaurant Assagao", category: "FOOD", by: "kabir", reason: "Goan home cooking", at: [15.58, 73.745] },
  { q: "Café Bodega Panjim", category: "FOOD", by: "meera", reason: "Calm garden café with vegetarian food", at: [15.4985, 73.824] },
  { q: "Panjim Inn", category: "STAYS", by: "asha", reason: "Heritage guesthouse in Fontainhas", at: [15.4975, 73.8325] },
];

const REACTIONS = [
  ["meera", "Hilltop Vagator", "NO_WAY", "Too loud and too late for me"],
  ["asha", "Hilltop Vagator", "OK", null],
  ["kabir", "Hilltop Vagator", "OK", null],
  ["dev", "Grande Island", "LOVE", null],
  ["meera", "Grande Island", "OK", "I'll snorkel, not dive"],
  ["kabir", "Fort Aguada", "LOVE", null],
  ["asha", "Fort Aguada", "LOVE", null],
  ["dev", "Fontainhas", "OK", null],
  ["meera", "Butterfly Beach", "LOVE", null],
  ["dev", "Butterfly Beach", "LOVE", null],
  ["kabir", "Cabo de Rama", "LOVE", null],
  ["meera", "Sahakari Spice Farm", "LOVE", null],
  ["dev", "Sahakari Spice Farm", "NO_WAY", "A bit touristy"],
  ["asha", "Ritz Classic Panjim", "LOVE", null],
  ["meera", "Ritz Classic Panjim", "NO_WAY", "Mostly fish; little for vegetarians"],
];

// ---- run ---------------------------------------------------------------------------------------------------

main().catch((e) => {
  console.error(`\n✗ Seed failed: ${e.message}`);
  process.exit(1);
});

async function main() {
  console.log(`Seeding the Meridian demo against ${API}`);
  await health();
  if (reset) purge();

  const users = {};
  for (const [key, p] of Object.entries(PEOPLE)) users[key] = await ensureUser(p.name, `${key}@${DOMAIN}`);
  const existing = (await call(users.asha, "GET", "/api/trips")).find((t) => t.name === TRIP_NAME);
  if (existing) {
    console.log(`\nThe demo trip already exists (${APP}/trips/${existing.id}). Run with --reset to seed it again.`);
    return;
  }

  step("Travel-style profiles (onboarding quiz, A1)");
  for (const [key, p] of Object.entries(PEOPLE)) {
    const job = await call(users[key], "POST", "/api/profile/quiz", p.quiz);
    const done = await waitJob(users[key], job);
    log(`${p.name}: ${done.result?.provenance?.source === "ai" ? "AI" : "rules"} profile`);
  }

  step("Trip, invite link and members");
  const start = addDays(new Date(), 30);
  const trip = await call(users.asha, "POST", "/api/trips", {
    name: TRIP_NAME,
    destinationName: "Goa, India",
    destinationLat: 15.4,
    destinationLng: 73.95,
    startDate: iso(start),
    endDate: iso(addDays(start, 4)),
    currency: "INR",
    budgetTotal: 160000,
  });
  const invite = await call(users.asha, "POST", `/api/trips/${trip.id}/invites`);
  for (const key of ["meera", "dev", "kabir"]) {
    await call(users[key], "POST", `/api/invites/${invite.token}/accept`);
  }
  for (const [key, p] of Object.entries(PEOPLE)) {
    await call(users[key], "PUT", `/api/trips/${trip.id}/preferences/me`, {
      ...p.trip,
      mustHaves: p.quiz.mustHaves,
      budgetVisible: false,
    });
  }
  log(`${TRIP_NAME}: ${iso(start)} for 5 days, 4 members, private budgets`);

  step("Shortlist from OpenStreetMap");
  const items = {};
  for (const s of SHORTLIST) {
    const by = users[s.by];
    let body;
    let found = null;
    try {
      const res = await call(by, "GET", `/api/trips/${trip.id}/places/search?q=${encodeURIComponent(s.q)}`);
      found = res.places?.[0] ?? null;
    } catch (e) {
      log(`  search failed for ${s.q} (${e.message}); dropping a pin instead`);
    }
    if (found) body = { kind: "PLACE", placeId: found.id, category: s.category, reason: s.reason };
    else body = { kind: "PIN", title: s.q, lat: s.at[0], lng: s.at[1], category: s.category, reason: s.reason };
    try {
      const item = await call(by, "POST", `/api/trips/${trip.id}/shortlist`, body);
      items[s.q] = item.id;
      log(`${s.q} → ${found ? `OSM "${found.name}"` : "map pin"} (added by ${PEOPLE[s.by].name})`);
    } catch (e) {
      // two searches can land on the same OSM place
      log(`${s.q}: skipped (${e.message})`);
    }
  }
  for (const [who, title, value, reason] of REACTIONS) {
    if (!items[title]) continue;
    await call(users[who], "PUT", `/api/trips/${trip.id}/shortlist/${items[title]}/reaction`, { value, reason });
  }
  log(`${Object.keys(items).length} places, ${REACTIONS.length} reactions`);

  step("Price comparison");
  const cmp = await call(users.asha, "POST", `/api/trips/${trip.id}/comparisons`, {
    kind: "STAY",
    title: "Where to stay in Panjim",
  });
  const hours = (h) => new Date(Date.now() - h * 3600_000).toISOString();
  const rows = [
    { provider: "Panjim Inn (direct)", title: "Heritage double room, 4 nights", price: 22400, checkedAt: hours(2),
      sourceUrl: "https://www.panjiminn.com/", notes: "Breakfast included" },
    { provider: "Booking.com", title: "Guesthouse in Fontainhas, 4 nights", price: 18900, checkedAt: hours(5),
      sourceUrl: "https://www.booking.com/searchresults.html?ss=Panaji" },
    { provider: "Airbnb", title: "2-bedroom flat near Miramar, 4 nights", price: 26500, checkedAt: hours(30),
      sourceUrl: "https://www.airbnb.com/s/Panaji--Goa/homes", notes: "Price checked yesterday" },
  ];
  for (const r of rows) {
    await call(users.asha, "POST", `/api/trips/${trip.id}/comparisons/${cmp.id}/rows`, {
      ...r, currency: "INR", sponsored: false, adapter: "manual",
    });
  }
  log("3 stay options, one of them stale (> 24 h)");

  step("AI results, computed once and cached (A3, A2)");
  const t = await waitJob(users.kabir, await call(users.kabir, "POST",
    `/api/trips/${trip.id}/group-fit/tradeoffs?regenerate=false`));
  log(`Trade-offs: ${t.result?.provenance?.source ?? "?"}`);
  for (const category of ["SIGHTS", "FOOD", "ACTIVITIES"]) {
    const job = await call(users.asha, "POST", `/api/trips/${trip.id}/recommendations/ai`, {
      category, gems: null, avoidCrowds: false, regenerate: false,
    });
    const done = await waitJob(users.asha, job);
    log(`Suggestions (${category.toLowerCase()}): ${done.result?.provenance?.source ?? done.status}`);
  }

  step("Draft itinerary (A4, one day per AI call)");
  const gen = await waitJob(users.asha, await call(users.asha, "POST", `/api/trips/${trip.id}/itinerary/generate`, {}));
  if (gen.status !== "SUCCEEDED") throw new Error(`itinerary generation ${gen.status}: ${gen.error}`);
  let plan = gen.result;
  log(plan.days.map((d) => `day ${d.index}: ${d.stops.length} stops (${d.provenance.source})`).join(", "));

  step("A change the group already decided");
  const busiest = [...plan.days].sort((a, b) => b.stops.length - a.stops.length)[0];
  const quietest = [...plan.days].sort((a, b) => a.stops.length - b.stops.length)[0];
  const moved = busiest.stops[busiest.stops.length - 1];
  const past = await call(users.dev, "POST", `/api/trips/${trip.id}/proposals`, {
    edit: { type: "MOVE", itemId: moved.shortlistItemId, day: quietest.index },
    reason: `Day ${busiest.index} is the fullest and day ${quietest.index} has room.`,
  });
  await call(users.meera, "POST", `/api/trips/${trip.id}/comments`, {
    targetType: "PROPOSAL", targetId: past.id, body: "Fine by me, as long as that day doesn't start too early.",
  });
  await call(users.asha, "PUT", `/api/trips/${trip.id}/proposals/${past.id}/vote`, { value: "APPROVE" });
  const decided = await call(users.kabir, "PUT", `/api/trips/${trip.id}/proposals/${past.id}/vote`, { value: "APPROVE" });
  log(`${decided.summary}: ${decided.status} (${decided.tally.approvals} of ${decided.tally.members})`);
  await waitForSummaries(users.asha, trip.id);

  step("Consensus gate: two members approve the plan");
  await call(users.meera, "PUT", `/api/trips/${trip.id}/itinerary/approval`, { value: "APPROVE" });
  await call(users.kabir, "PUT", `/api/trips/${trip.id}/itinerary/approval`, { value: "APPROVE" });
  log("Meera and Kabir approve; 3 of 4 are needed to lock");

  step("An open proposal with partial votes (to decide live)");
  plan = await call(users.asha, "GET", `/api/trips/${trip.id}/itinerary`);
  const onPlan = (id) => plan.days.some((d) => d.stops.some((s) => s.shortlistItemId === id));
  let open;
  if (items["Hilltop Vagator"] && onPlan(items["Hilltop Vagator"])) {
    open = { edit: { type: "REMOVE", itemId: items["Hilltop Vagator"] },
      reason: "Too loud for most of us, and it runs late into the night." };
  } else {
    const day = plan.days.find((d) => d.stops.length > 1) ?? plan.days[0];
    const other = plan.days.find((d) => d.index !== day.index) ?? day;
    open = { edit: { type: "MOVE", itemId: day.stops[0].shortlistItemId, day: other.index },
      reason: "It fits better with the other stops on that day." };
  }
  const proposal = await call(users.meera, "POST", `/api/trips/${trip.id}/proposals`, open);
  await call(users.kabir, "PUT", `/api/trips/${trip.id}/proposals/${proposal.id}/vote`, { value: "REJECT" });
  log(`"${proposal.summary}": Meera approves, Kabir rejects; Asha and Dev have not voted`);

  console.log(`
✓ Demo ready: ${APP}/trips/${trip.id}

  Sign in (password for everyone: ${PASSWORD})
    asha@${DOMAIN}   organizer · balanced · mid budget · heritage and food
    meera@${DOMAIN}  slow · budget · vegetarian · avoids crowds
    dev@${DOMAIN}    packed · splurge · adventure and nightlife
    kabir@${DOMAIN}  balanced · photography, forts, local life

  Live step: as Asha and Dev in two windows, approve the open proposal (it applies at 3 of 4),
  then approve the plan; at 3 of 4 the "Lock itinerary" button opens for Asha.`);
}

// ---- helpers -----------------------------------------------------------------------------------------------

async function health() {
  try {
    const res = await fetch(`${API}/api/health`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
  } catch (e) {
    throw new Error(`the API at ${API} is not reachable (${e.message}). Start it with docker compose up.`);
  }
}

/** Deletes the demo trip; the decision log is append-only, so the purge has to say so explicitly. */
function purge() {
  step("Reset: deleting the previous demo trip");
  // Decisions keep their proposals and stops keep their shortlist places on purpose (no cascade), so those go first.
  const trips = `SELECT tm.trip_id FROM trip_members tm JOIN users u ON u.id = tm.user_id WHERE u.email LIKE '%@${DOMAIN}'`;
  const sql = `BEGIN; SET LOCAL meridian.purge = 'on';
    DELETE FROM decision_log WHERE trip_id IN (${trips});
    DELETE FROM itineraries WHERE trip_id IN (${trips});
    DELETE FROM trips WHERE id IN (${trips}); COMMIT;`;
  const user = env.POSTGRES_USER || "meridian";
  const db = env.POSTGRES_DB || "meridian";
  try {
    const out = execFileSync("docker", ["compose", "exec", "-T", "postgres", "psql", "-U", user, "-d", db,
      "-v", "ON_ERROR_STOP=1", "-c", sql], { cwd: root, encoding: "utf8" });
    log(out.split("\n").find((l) => l.startsWith("DELETE")) ?? "done");
  } catch (e) {
    throw new Error(`could not reset through docker compose (${e.message.split("\n")[0]})`);
  }
}

async function ensureUser(displayName, email) {
  try {
    return await auth("/api/auth/login", { email, password: PASSWORD });
  } catch (e) {
    if (e.status !== 401 && e.status !== 404) throw e;
    return auth("/api/auth/signup", { email, password: PASSWORD, displayName });
  }
}

async function auth(path, body) {
  const res = await request(null, "POST", path, body);
  return { token: res.token, id: res.user.id, name: res.user.displayName };
}

function call(user, method, path, body) {
  return request(user.token, method, path, body);
}

async function request(token, method, path, body) {
  const headers = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${API}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  if (res.status === 204) return null;
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const err = new Error(`${method} ${path} → ${res.status} ${data?.detail ?? data?.title ?? ""}`.trim());
    err.status = res.status;
    throw err;
  }
  return data;
}

/** Polls an AI job until it finishes (AI calls on a laptop can take a while). */
async function waitJob(user, job, timeoutMs = 15 * 60_000) {
  const deadline = Date.now() + timeoutMs;
  let last = "";
  for (;;) {
    const j = await call(user, "GET", `/api/ai/jobs/${job.id}`);
    if (j.message !== last && j.status === "RUNNING") {
      last = j.message;
      process.stdout.write(`    … ${j.message} (${j.percent}%)\n`);
    }
    if (!["QUEUED", "RUNNING"].includes(j.status)) return j;
    if (Date.now() > deadline) throw new Error(`AI job ${job.feature} did not finish in time`);
    await sleep(1000);
  }
}

/** Waits for A6 to finish the decision-log summaries started by votes. */
async function waitForSummaries(user, tripId, timeoutMs = 5 * 60_000) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const list = await call(user, "GET", `/api/trips/${tripId}/decisions`);
    if (!list.some((d) => d.summaryPending)) {
      for (const d of list) log(`Decision log: ${d.summary} [${d.provenance.source}]`);
      return;
    }
    if (Date.now() > deadline) return log("Decision summary still being written; it will appear in the app");
    await sleep(1000);
  }
}

function step(title) {
  console.log(`\n▸ ${title}`);
}

function log(line) {
  console.log(`  ${line}`);
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function addDays(d, n) {
  const out = new Date(d);
  out.setDate(out.getDate() + n);
  return out;
}

function iso(d) {
  return d.toISOString().slice(0, 10);
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
