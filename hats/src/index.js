// The supporter hat service. A payment arrives by webhook and is remembered under a keyed hash of the supporter's email;
// the supporter then claims their hat on the page by giving that email and their Minecraft username. A claim or a refund
// starts .github/workflows/supporters.yml, which changes supporters.json. This Worker never holds a token that can push
// code, and never stores an email or a donation message.
//
// KV (eventually consistent, within about a minute, which is fine at this volume):
//   paid:<email hash>  {tx: [transaction ids not refunded], uuid?, name?, changes?}
//   hat:<uuid>         [email hashes holding this hat], so one refund can't take a hat someone else also paid for
import { PAGE } from "./page.js";

const REPO = "GabePurc/horsing-around";
const WORKFLOW = "supporters.yml";
const GRANTS = new Set(["donation.created", "extra_purchase.created"]);
const REFUNDS = new Set(["donation.refunded", "extra_purchase.refunded"]);
const USERNAME = /^[A-Za-z0-9_]{1,16}$/;
const UUID = /^[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}$/i;
const HEADERS = { "user-agent": "horsing-around-hats" };
const LOOKUP_TIMEOUT_MS = 5000;
/** One wrong pick can be put right; more than that goes to the author, so a hat can't be passed around friends. */
const MAX_CHANGES = 1;
const MAX_BODY = 64 * 1024;

export default {
	async fetch(request, env) {
		const url = new URL(request.url);
		try {
			if (url.pathname === "/" && request.method === "GET") {
				return new Response(PAGE, {
					headers: {
						"content-type": "text/html; charset=utf-8",
						"content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src https://textures.minecraft.net; connect-src 'self'; base-uri 'none'; form-action 'none'",
						"referrer-policy": "no-referrer",
						"x-content-type-options": "nosniff",
					},
				});
			}
			if (url.pathname === "/webhook" && request.method === "POST") {
				return await webhook(request, env);
			}
			if (url.pathname === "/lookup" && request.method === "GET") {
				return await lookup(request, env, url);
			}
			if (url.pathname === "/claim" && request.method === "POST") {
				return await claim(request, env);
			}
			return json({ error: "not_found" }, 404);
		} catch (e) {
			// (A webhook answered with this is retried by Buy Me a Coffee, so nothing is lost.)
			await alert(env, `Hat service error on ${url.pathname}: ${e.message}`);
			return json({ error: "server" }, 500);
		}
	},
};

async function webhook(request, env) {
	if (!env.BMC_SIGNING_SECRET) {
		return json({ error: "not_configured" }, 503);
	}
	const raw = await request.text();
	if (raw.length > MAX_BODY || !(await signed(raw, request.headers.get("x-signature-sha256"), env.BMC_SIGNING_SECRET))) {
		return json({ error: "bad_signature" }, 401);
	}
	const event = JSON.parse(raw);
	const data = event.data ?? {};
	if (event.live_mode !== true) {
		const has = data.supporter_email ? "with" : "WITHOUT";
		await alert(env, `Test webhook received and verified: ${event.type}, ${has} a supporter email. Nothing was changed.`);
		return json({ ok: true, test: true });
	}
	if (!GRANTS.has(event.type) && !REFUNDS.has(event.type)) {
		return json({ ok: true, ignored: true });
	}
	const tx = data.transaction_id ?? (data.id === undefined ? "" : String(data.id));
	if (!data.supporter_email || !tx) {
		await alert(env, `A ${event.type} webhook had no supporter email or transaction id (attempt ${event.attempt}). That supporter can't claim a hat without help.`);
		return json({ ok: true, unusable: true });
	}
	const hash = await emailHash(data.supporter_email, env);
	const record = (await env.HATS.get(`paid:${hash}`, "json")) ?? { tx: [] };
	if (GRANTS.has(event.type)) {
		if (data.status === "refunded" || data.refunded === "true") {
			return json({ ok: true, ignored: true });
		}
		// (A retried delivery carries the same transaction, so this changes nothing the second time.)
		if (!record.tx.includes(tx)) {
			record.tx.push(tx);
			await env.HATS.put(`paid:${hash}`, JSON.stringify(record));
		}
		return json({ ok: true });
	}
	record.tx = record.tx.filter((id) => id !== tx);
	if (record.tx.length === 0 && record.uuid) {
		await releaseHat(env, hash, record.uuid, record.name);
		delete record.uuid;
		delete record.name;
	}
	await env.HATS.put(`paid:${hash}`, JSON.stringify(record));
	return json({ ok: true });
}

async function lookup(request, env, url) {
	if (!(await allowed(env.LOOKUP_LIMIT, request))) {
		return json({ error: "slow_down" }, 429);
	}
	const profile = await mojangProfile(url.searchParams.get("name") ?? "");
	const tried = { "x-lookup-tried": profile.tried };
	if (profile.error) {
		return json({ error: profile.error }, profile.error === "not_found" ? 404 : 503, tried);
	}
	return json({ name: profile.name, id: profile.id, skin: await skinUrl(profile) }, 200, tried);
}

async function claim(request, env) {
	if (!(await allowed(env.CLAIM_LIMIT, request))) {
		return json({ error: "slow_down" }, 429);
	}
	const raw = await request.text();
	if (raw.length > 2048) {
		return json({ error: "bad_request" }, 400);
	}
	let body;
	try {
		body = JSON.parse(raw);
	} catch {
		return json({ error: "bad_request" }, 400);
	}
	const email = typeof body.email === "string" ? body.email : "";
	if (!email.includes("@") || typeof body.name !== "string") {
		return json({ error: "bad_request" }, 400);
	}
	// The id comes from Mojang, never from the page.
	const profile = await mojangProfile(body.name);
	if (profile.error) {
		return json({ error: profile.error }, profile.error === "not_found" ? 404 : 503);
	}
	const hash = await emailHash(email, env);
	const record = await env.HATS.get(`paid:${hash}`, "json");
	if (!record || record.tx.length === 0) {
		return json({ error: "no_payment" }, 404);
	}
	if (record.uuid === profile.id) {
		return json({ ok: true, name: profile.name, already: true });
	}
	if (record.uuid) {
		if ((record.changes ?? 0) >= MAX_CHANGES) {
			await alert(env, `A supporter holding the hat for ${record.name} asked to move it to ${profile.name}, past the limit. Move it by hand if that's right.`);
			return json({ error: "already_claimed", name: record.name }, 409);
		}
		await releaseHat(env, hash, record.uuid, record.name);
		record.changes = (record.changes ?? 0) + 1;
	}
	await dispatch(env, "add", profile.id, profile.name);
	const holders = (await env.HATS.get(`hat:${profile.id}`, "json")) ?? [];
	if (!holders.includes(hash)) {
		holders.push(hash);
		await env.HATS.put(`hat:${profile.id}`, JSON.stringify(holders));
	}
	record.uuid = profile.id;
	record.name = profile.name;
	await env.HATS.put(`paid:${hash}`, JSON.stringify(record));
	return json({ ok: true, name: profile.name });
}

/** Takes this supporter's claim off a hat, and removes the hat if no one else paid for it. */
async function releaseHat(env, hash, uuid, name) {
	const others = ((await env.HATS.get(`hat:${uuid}`, "json")) ?? []).filter((holder) => holder !== hash);
	if (others.length === 0) {
		await dispatch(env, "remove", uuid, name);
		await env.HATS.delete(`hat:${uuid}`);
	} else {
		await env.HATS.put(`hat:${uuid}`, JSON.stringify(others));
	}
}

/** Starts the workflow that edits supporters.json. A failure throws: the caller's request fails and can be tried again. */
async function dispatch(env, operation, uuid, name) {
	const response = await fetch(`https://api.github.com/repos/${REPO}/actions/workflows/${WORKFLOW}/dispatches`, {
		method: "POST",
		headers: {
			accept: "application/vnd.github+json",
			authorization: `Bearer ${env.GITHUB_TOKEN}`,
			"user-agent": "horsing-around-hats",
			"x-github-api-version": "2022-11-28",
		},
		body: JSON.stringify({ ref: "main", inputs: { operation, uuid, name } }),
	});
	if (!response.ok) {
		// (401 here most likely means the token has expired.)
		throw new Error(`GitHub refused to start the supporters workflow: HTTP ${response.status}`);
	}
}

/**
 * Who a username belongs to. Mojang can refuse requests from Cloudflare's addresses, so a refusal moves on to the next
 * source; only a clear "no such player" ends the search. `tried` lists each source's answer, for diagnosing a failure.
 */
async function mojangProfile(name) {
	if (!USERNAME.test(name)) {
		return { error: "not_found", tried: "" };
	}
	const tried = [];
	for (const source of LOOKUPS) {
		const answer = await askSource(source, name);
		tried.push(`${source.label}=${answer.status}`);
		if (answer.missing) {
			return { error: "not_found", tried: tried.join(",") };
		}
		if (answer.profile && UUID.test(answer.profile.id) && answer.profile.name?.toLowerCase() === name.toLowerCase()) {
			return { ...answer.profile, id: dashed(answer.profile.id), tried: tried.join(",") };
		}
	}
	return { error: "mojang_unavailable", tried: tried.join(",") };
}

const fromMojang = async (response) => {
	if (response.status === 404 || response.status === 204) {
		return { missing: true };
	}
	if (!response.ok) {
		return {};
	}
	const profile = await response.json();
	return { profile: { id: profile.id, name: profile.name } };
};

const LOOKUPS = [
	{ label: "minecraftservices", url: (name) => `https://api.minecraftservices.com/minecraft/profile/lookup/name/${name}`, read: fromMojang },
	{ label: "mojang", url: (name) => `https://api.mojang.com/users/profiles/minecraft/${name}`, read: fromMojang },
	{
		label: "playerdb",
		url: (name) => `https://playerdb.co/api/player/minecraft/${name}`,
		read: async (response) => {
			const body = await response.json();
			if (body.code === "minecraft.invalid_username") {
				return { missing: true };
			}
			const player = body.data?.player;
			return player ? { profile: { id: player.id, name: player.username, skin: player.skin_texture } } : {};
		},
	},
];

async function askSource(source, name) {
	try {
		const response = await fetch(source.url(name), { headers: HEADERS, signal: AbortSignal.timeout(LOOKUP_TIMEOUT_MS) });
		return { status: response.status, ...(await source.read(response)) };
	} catch {
		return { status: 0 };
	}
}

/** The player's skin for the page's preview, or null: the preview can do without it. */
async function skinUrl(profile) {
	if (profile.skin) {
		return httpsOnly(profile.skin);
	}
	try {
		const response = await fetch(`https://sessionserver.mojang.com/session/minecraft/profile/${profile.id.replaceAll("-", "")}`, {
			headers: HEADERS,
			signal: AbortSignal.timeout(LOOKUP_TIMEOUT_MS),
		});
		if (!response.ok) {
			return null;
		}
		const session = await response.json();
		const textures = session.properties?.find((property) => property.name === "textures");
		return httpsOnly(JSON.parse(atob(textures.value)).textures?.SKIN?.url);
	} catch {
		return null;
	}
}

function httpsOnly(url) {
	return url ? url.replace(/^http:/, "https:") : null;
}

function dashed(id) {
	return id.includes("-") ? id : `${id.slice(0, 8)}-${id.slice(8, 12)}-${id.slice(12, 16)}-${id.slice(16, 20)}-${id.slice(20)}`;
}

async function allowed(limiter, request) {
	if (!limiter) {
		return true;
	}
	const { success } = await limiter.limit({ key: request.headers.get("cf-connecting-ip") ?? "unknown" });
	return success;
}

async function hmacKey(secret, usage) {
	return crypto.subtle.importKey("raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, [usage]);
}

/** Constant time, by leaving the comparison to crypto.subtle.verify. */
async function signed(raw, signature, secret) {
	if (!signature || !/^[0-9a-fA-F]{64}$/.test(signature)) {
		return false;
	}
	const bytes = Uint8Array.from(signature.match(/../g), (pair) => parseInt(pair, 16));
	return crypto.subtle.verify("HMAC", await hmacKey(secret, "verify"), bytes, new TextEncoder().encode(raw));
}

/** Keyed, so the stored hashes can't be checked against a list of known addresses without the secret. */
async function emailHash(email, env) {
	const mac = await crypto.subtle.sign("HMAC", await hmacKey(env.EMAIL_PEPPER, "sign"), new TextEncoder().encode(email.trim().toLowerCase()));
	return [...new Uint8Array(mac)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/** To the author's private Discord channel. Never carries an email or a donation message. */
async function alert(env, content) {
	if (!env.DISCORD_WEBHOOK) {
		return;
	}
	try {
		await fetch(env.DISCORD_WEBHOOK, {
			method: "POST",
			headers: { "content-type": "application/json" },
			body: JSON.stringify({ content, allowed_mentions: { parse: [] } }),
		});
	} catch {
		// An alert that can't be sent must not fail the request it's about.
	}
}

function json(body, status = 200, headers = {}) {
	return new Response(JSON.stringify(body), {
		status,
		headers: { "content-type": "application/json", "x-content-type-options": "nosniff", ...headers },
	});
}
