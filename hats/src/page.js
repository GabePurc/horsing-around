// The claim page: one file, no outside scripts or fonts. The only outside request is the player's skin, from Mojang.
export const PAGE = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>Claim your cowboy hat</title>
<style>
:root { color-scheme: light dark; --bg: #f4efe6; --card: #fffdf8; --ink: #2b211a; --muted: #6d5d4f; --line: #d8cbb8; --accent: #8b4a1f; --on-accent: #fff; --bad: #a32c1e; --good: #2f6b34; }
@media (prefers-color-scheme: dark) { :root { --bg: #1d1713; --card: #29211b; --ink: #f2e9dc; --muted: #b7a692; --line: #4a3d31; --accent: #d08a4d; --on-accent: #1d1713; --bad: #ef8a7c; --good: #8fce94; } }
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--ink); font: 16px/1.5 system-ui, sans-serif; }
main { max-width: 30rem; margin: 0 auto; padding: 2rem 1rem 3rem; }
h1 { margin: 0 0 .25rem; font-size: 1.75rem; line-height: 1.2; }
.lead { margin: 0 0 1.5rem; color: var(--muted); }
form, .done { background: var(--card); border: 1px solid var(--line); border-radius: .75rem; padding: 1.25rem; }
label { display: block; margin: 0 0 .25rem; font-weight: 600; }
.hint { margin: .25rem 0 1rem; color: var(--muted); font-size: .875rem; }
input { width: 100%; padding: .625rem .75rem; font: inherit; color: inherit; background: transparent; border: 1px solid var(--muted); border-radius: .5rem; }
button { width: 100%; min-height: 2.75rem; padding: .625rem 1rem; font: inherit; font-weight: 600; color: var(--on-accent); background: var(--accent); border: 0; border-radius: .5rem; cursor: pointer; }
button:disabled { opacity: .6; cursor: progress; }
button.quiet { color: var(--ink); background: transparent; border: 1px solid var(--muted); margin-top: .5rem; }
:focus-visible { outline: 3px solid var(--accent); outline-offset: 2px; }
.who { display: flex; align-items: center; gap: 1rem; margin-bottom: 1rem; }
canvas { flex: none; width: 64px; height: 64px; image-rendering: pixelated; border-radius: .25rem; background: var(--line); }
.who strong { display: block; font-size: 1.125rem; overflow-wrap: anywhere; }
.message { margin: 1rem 0 0; color: var(--bad); }
.message:empty { display: none; }
.done h2 { margin: 0 0 .5rem; color: var(--good); font-size: 1.25rem; }
.done p { margin: 0 0 .5rem; }
.small { margin-top: 1.5rem; color: var(--muted); font-size: .875rem; }
a { color: var(--accent); }
[hidden] { display: none !important; }
</style>
</head>
<body>
<main>
<h1>Claim your cowboy hat</h1>
<p class="lead">Thanks for supporting Horsing Around! Tell me who you are in Minecraft and the hat is yours.</p>

<form id="form" novalidate>
<div id="details">
<label for="email">Email</label>
<input id="email" type="email" autocomplete="email" required>
<p class="hint">The one you used on Buy Me a Coffee, so I can match your payment.</p>
<label for="name">Minecraft username</label>
<input id="name" autocomplete="off" autocapitalize="off" spellcheck="false" maxlength="16" required>
<p class="hint">Java Edition. Your exact name, as it shows in game.</p>
<button id="find" type="submit">Find me</button>
</div>
<div id="confirm" hidden>
<div class="who">
<canvas id="head" width="64" height="64" aria-hidden="true"></canvas>
<div>Is this you?<strong id="found"></strong></div>
</div>
<p class="hint">Each email gets one hat, and it can't be moved afterwards, so check this is the right player.</p>
<button id="claim" type="button">Yes, give me the hat</button>
<button id="back" class="quiet" type="button">No, go back</button>
</div>
<p id="message" class="message" role="alert"></p>
</form>

<div id="done" class="done" hidden tabindex="-1">
<h2>The hat is on its way</h2>
<p><strong id="doneName"></strong> gets the hat within about 10 minutes. You'll find it in your horse settings, in any colour you like.</p>
<p>Don't see it yet? Restart your game.</p>
</div>

<p class="small">Your username goes on a <a href="https://github.com/GabePurc/horsing-around/blob/main/supporters.json">public list</a> of supporters. Your email is only compared against a scrambled copy of the one on your payment, and is never stored in readable form. Stuck? Ask on <a href="https://discord.gg/ppbN4JwFpc">the Discord</a>.</p>
</main>
<script>
const $ = (id) => document.getElementById(id);
const ERRORS = {
	not_found: "I can't find that Minecraft username. Check the spelling.",
	mojang_unavailable: "Minecraft's name lookup isn't answering right now. Try again in a few minutes.",
	no_payment: "I can't find a payment with that email. If you only just paid, wait a minute and try again. Otherwise, check it's the email you used on Buy Me a Coffee.",
	slow_down: "That's a lot of tries. Wait a minute and try again.",
	bad_request: "Please check your email and username.",
};
let chosen = null;
function say(text) { $("message").textContent = text; }
function problem(result) {
	if (result.error === "already_claimed") {
		return "Your hat is already on " + result.name + ". To move it, ask on the Discord.";
	}
	return ERRORS[result.error] || "Something went wrong on my end. Please try again in a few minutes.";
}
async function ask(url, options) {
	try {
		const response = await fetch(url, options);
		return await response.json();
	} catch {
		return { error: "network" };
	}
}
function drawHead(skin) {
	const canvas = $("head");
	const context = canvas.getContext("2d");
	context.clearRect(0, 0, 64, 64);
	if (!skin) {
		return;
	}
	const image = new Image();
	image.onload = () => {
		context.imageSmoothingEnabled = false;
		context.drawImage(image, 8, 8, 8, 8, 0, 0, 64, 64);
		context.drawImage(image, 40, 8, 8, 8, 0, 0, 64, 64);
	};
	image.src = skin;
}
$("form").addEventListener("submit", async (event) => {
	event.preventDefault();
	const email = $("email").value.trim();
	const name = $("name").value.trim();
	if (!email.includes("@") || !name) {
		say("Please enter both your email and your username.");
		return;
	}
	say("");
	$("find").disabled = true;
	const result = await ask("/lookup?name=" + encodeURIComponent(name));
	$("find").disabled = false;
	if (!result.name) {
		say(problem(result));
		return;
	}
	chosen = result.name;
	$("found").textContent = result.name;
	drawHead(result.skin);
	$("details").hidden = true;
	$("confirm").hidden = false;
	$("claim").focus();
});
$("back").addEventListener("click", () => {
	say("");
	$("confirm").hidden = true;
	$("details").hidden = false;
	$("name").focus();
});
$("claim").addEventListener("click", async () => {
	say("");
	$("claim").disabled = true;
	const result = await ask("/claim", {
		method: "POST",
		headers: { "content-type": "application/json" },
		body: JSON.stringify({ email: $("email").value.trim(), name: chosen }),
	});
	$("claim").disabled = false;
	if (!result.ok) {
		say(problem(result));
		return;
	}
	$("doneName").textContent = result.name;
	$("form").hidden = true;
	$("done").hidden = false;
	$("done").focus();
});
</script>
</body>
</html>
`;
