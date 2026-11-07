// Runs the Worker against an in-memory KV and a stubbed network: node --test test/ (from hats/).
import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { beforeEach, test } from "node:test";
import worker from "../src/index.js";

const SECRET = "signing-secret";
const STEVE = "11111111-1111-4111-8111-111111111111";
const ALEX = "22222222-2222-4222-8222-222222222222";
const PROFILES = {
	Steve: { id: STEVE.replaceAll("-", ""), name: "Steve" },
	steve: { id: STEVE.replaceAll("-", ""), name: "Steve" },
	Alex: { id: ALEX.replaceAll("-", ""), name: "Alex" },
};

let env;
let dispatched;
let alerts;
let gitHubStatus;

function memoryKv() {
	const store = new Map();
	return {
		store,
		get: async (key, type) => {
			const value = store.get(key);
			return value === undefined ? null : type === "json" ? JSON.parse(value) : value;
		},
		put: async (key, value) => void store.set(key, value),
		delete: async (key) => void store.delete(key),
	};
}

beforeEach(() => {
	dispatched = [];
	alerts = [];
	gitHubStatus = 204;
	env = {
		HATS: memoryKv(),
		BMC_SIGNING_SECRET: SECRET,
		EMAIL_PEPPER: "pepper",
		GITHUB_TOKEN: "token",
		DISCORD_WEBHOOK: "https://discord.test/hook",
	};
	globalThis.fetch = async (input, init) => {
		const url = String(input);
		if (url.startsWith("https://api.minecraftservices.com/")) {
			const profile = PROFILES[url.split("/").pop()];
			return profile ? Response.json(profile) : new Response("", { status: 404 });
		}
		if (url.startsWith("https://sessionserver.mojang.com/")) {
			const textures = btoa(JSON.stringify({ textures: { SKIN: { url: "http://textures.minecraft.net/texture/abc" } } }));
			return Response.json({ properties: [{ name: "textures", value: textures }] });
		}
		if (url.startsWith("https://api.github.com/")) {
			if (gitHubStatus === 204) {
				dispatched.push(JSON.parse(init.body).inputs);
			}
			return new Response(null, { status: gitHubStatus });
		}
		if (url === env.DISCORD_WEBHOOK) {
			alerts.push(JSON.parse(init.body).content);
			return new Response(null, { status: 204 });
		}
		throw new Error(`unexpected request: ${url}`);
	};
});

function send(path, init) {
	return worker.fetch(new Request(`https://hats.test${path}`, init), env);
}

function hook(type, data, { live = true, secret = SECRET } = {}) {
	const body = JSON.stringify({ event_id: 1, type, live_mode: live, created: 1, attempt: 1, data });
	const signature = createHmac("sha256", secret).update(body).digest("hex");
	return send("/webhook", { method: "POST", body, headers: { "x-signature-sha256": signature } });
}

const payment = (email, tx = "pi_1") => ({ supporter_email: email, transaction_id: tx, status: "succeeded", refunded: "false", support_note: "love the mod" });

function claim(email, name) {
	return send("/claim", { method: "POST", body: JSON.stringify({ email, name }) });
}

test("the page is served, and other paths aren't", async () => {
	const page = await send("/");
	assert.equal(page.status, 200);
	assert.match(await page.text(), /Claim your cowboy hat/);
	assert.equal((await send("/nope")).status, 404);
});

test("a webhook without the right signature changes nothing", async () => {
	assert.equal((await hook("donation.created", payment("a@b.c"), { secret: "wrong" })).status, 401);
	const body = JSON.stringify({ type: "donation.created", live_mode: true, data: payment("a@b.c") });
	assert.equal((await send("/webhook", { method: "POST", body })).status, 401);
	assert.equal((await send("/webhook", { method: "POST", body, headers: { "x-signature-sha256": "zz" } })).status, 401);
	assert.equal(env.HATS.store.size, 0);
});

test("before the signing secret is set, webhooks fail so they get retried", async () => {
	delete env.BMC_SIGNING_SECRET;
	assert.equal((await hook("donation.created", payment("a@b.c"))).status, 503);
});

test("a dashboard test event reports back and changes nothing", async () => {
	assert.equal((await hook("donation.created", payment("a@b.c"), { live: false })).status, 200);
	assert.equal(env.HATS.store.size, 0);
	assert.match(alerts[0], /Test webhook received/);
	assert.ok(!alerts[0].includes("a@b.c"));
});

test("a claim without a payment is refused", async () => {
	const response = await claim("a@b.c", "Steve");
	assert.equal(response.status, 404);
	assert.equal((await response.json()).error, "no_payment");
	assert.deepEqual(dispatched, []);
});

test("paying then claiming adds the hat, whatever the email's case", async () => {
	await hook("donation.created", payment("Fan@Example.com"));
	const response = await claim(" fan@example.COM ", "steve");
	assert.equal(response.status, 200);
	assert.equal((await response.json()).name, "Steve");
	assert.deepEqual(dispatched, [{ operation: "add", uuid: STEVE, name: "Steve" }]);
});

test("nothing readable about the supporter is stored", async () => {
	await hook("donation.created", payment("fan@example.com"));
	await claim("fan@example.com", "Steve");
	const everything = JSON.stringify([...env.HATS.store]);
	assert.ok(!everything.includes("fan@example.com"));
	assert.ok(!everything.includes("love the mod"));
});

test("a shop purchase counts too", async () => {
	await hook("extra_purchase.created", payment("a@b.c"));
	assert.equal((await claim("a@b.c", "Steve")).status, 200);
});

test("claiming the same player again does nothing more", async () => {
	await hook("donation.created", payment("a@b.c"));
	await claim("a@b.c", "Steve");
	const again = await claim("a@b.c", "Steve");
	assert.equal(again.status, 200);
	assert.equal(dispatched.length, 1);
});

test("a retried webhook doesn't count twice", async () => {
	await hook("donation.created", payment("a@b.c"));
	await hook("donation.created", payment("a@b.c"));
	await claim("a@b.c", "Steve");
	await hook("donation.refunded", payment("a@b.c"));
	assert.deepEqual(dispatched.at(-1), { operation: "remove", uuid: STEVE, name: "Steve" });
});

test("one mistaken pick can be moved, and no more", async () => {
	await hook("donation.created", payment("a@b.c"));
	await claim("a@b.c", "Steve");
	assert.equal((await claim("a@b.c", "Alex")).status, 200);
	assert.deepEqual(dispatched.slice(1), [{ operation: "remove", uuid: STEVE, name: "Steve" }, { operation: "add", uuid: ALEX, name: "Alex" }]);
	const third = await claim("a@b.c", "Steve");
	assert.equal(third.status, 409);
	assert.equal((await third.json()).name, "Alex");
	assert.equal(dispatched.length, 3);
	assert.equal(alerts.length, 1);
});

test("a refund removes the hat", async () => {
	await hook("donation.created", payment("a@b.c"));
	await claim("a@b.c", "Steve");
	await hook("donation.refunded", payment("a@b.c"));
	assert.deepEqual(dispatched.at(-1), { operation: "remove", uuid: STEVE, name: "Steve" });
	assert.equal((await claim("a@b.c", "Steve")).status, 404);
});

test("a refund keeps the hat while another payment stands", async () => {
	await hook("donation.created", payment("a@b.c", "pi_1"));
	await hook("donation.created", payment("a@b.c", "pi_2"));
	await claim("a@b.c", "Steve");
	await hook("donation.refunded", payment("a@b.c", "pi_1"));
	assert.equal(dispatched.length, 1);
});

test("a refund keeps a hat someone else also paid for", async () => {
	await hook("donation.created", payment("a@b.c", "pi_1"));
	await hook("donation.created", payment("d@e.f", "pi_2"));
	await claim("a@b.c", "Steve");
	await claim("d@e.f", "Steve");
	await hook("donation.refunded", payment("a@b.c", "pi_1"));
	assert.ok(!dispatched.some((inputs) => inputs.operation === "remove"));
	await hook("donation.refunded", payment("d@e.f", "pi_2"));
	assert.deepEqual(dispatched.at(-1), { operation: "remove", uuid: STEVE, name: "Steve" });
});

test("a refund before any claim just cancels the payment", async () => {
	await hook("donation.created", payment("a@b.c"));
	await hook("donation.refunded", payment("a@b.c"));
	assert.deepEqual(dispatched, []);
	assert.equal((await claim("a@b.c", "Steve")).status, 404);
});

test("if GitHub refuses, the claim fails, can be retried, and I hear about it", async () => {
	await hook("donation.created", payment("a@b.c"));
	gitHubStatus = 401;
	assert.equal((await claim("a@b.c", "Steve")).status, 500);
	assert.match(alerts[0], /HTTP 401/);
	gitHubStatus = 204;
	assert.equal((await claim("a@b.c", "Steve")).status, 200);
	assert.equal(dispatched.length, 1);
});

test("if GitHub refuses a refund's removal, the webhook fails so it gets retried", async () => {
	await hook("donation.created", payment("a@b.c"));
	await claim("a@b.c", "Steve");
	gitHubStatus = 500;
	assert.equal((await hook("donation.refunded", payment("a@b.c"))).status, 500);
	gitHubStatus = 204;
	assert.equal((await hook("donation.refunded", payment("a@b.c"))).status, 200);
	assert.deepEqual(dispatched.at(-1), { operation: "remove", uuid: STEVE, name: "Steve" });
});

test("a payment with no email tells me instead of vanishing", async () => {
	const response = await hook("donation.created", { transaction_id: "pi_1" });
	assert.equal(response.status, 200);
	assert.match(alerts[0], /no supporter email/);
});

test("lookup finds a player and their skin over https", async () => {
	const found = await (await send("/lookup?name=Steve")).json();
	assert.equal(found.id, STEVE);
	assert.equal(found.skin, "https://textures.minecraft.net/texture/abc");
	assert.equal((await send("/lookup?name=Nobody")).status, 404);
});

test("a name that couldn't be a username never reaches Mojang", async () => {
	assert.equal((await send("/lookup?name=" + encodeURIComponent("../../evil"))).status, 404);
	assert.equal((await claim("a@b.c", "not a name!")).status, 404);
});

test("a malformed claim is refused", async () => {
	assert.equal((await send("/claim", { method: "POST", body: "{" })).status, 400);
	assert.equal((await claim("no-at-sign", "Steve")).status, 400);
});

test("rate limits answer 429", async () => {
	env.CLAIM_LIMIT = { limit: async () => ({ success: false }) };
	env.LOOKUP_LIMIT = { limit: async () => ({ success: false }) };
	assert.equal((await claim("a@b.c", "Steve")).status, 429);
	assert.equal((await send("/lookup?name=Steve")).status, 429);
});
