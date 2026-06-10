/**
 * Sends email via Brevo (server-side only — API key never exposed to the browser).
 * Set in Netlify → Site configuration → Environment variables:
 *   BREVO_API_KEY, BREVO_FROM_EMAIL, BREVO_FROM_NAME (optional), BAYPORT_EMAIL_HOOK_SECRET
 */
export default async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "POST, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type, X-Bayport-Email-Secret",
      },
    });
  }

  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  const secret = process.env.BAYPORT_EMAIL_HOOK_SECRET || "";
  const provided = req.headers.get("x-bayport-email-secret") || "";
  if (!secret || provided !== secret) {
    return json({ error: "Unauthorized" }, 401);
  }

  const apiKey = (process.env.BREVO_API_KEY || "").trim();
  const fromEmail = (process.env.BREVO_FROM_EMAIL || "").trim();
  const fromName = (process.env.BREVO_FROM_NAME || "Bayport Veterinary Clinic").trim();

  if (!apiKey || !fromEmail) {
    return json(
      {
        error:
          "Email not configured on Netlify. Set BREVO_API_KEY and BREVO_FROM_EMAIL in Netlify Environment (verify sender at brevo.com).",
      },
      503,
    );
  }

  let body;
  try {
    body = await req.json();
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }

  const to = String(body.to || "").trim();
  const subject = String(body.subject || "Bayport Veterinary Clinic").trim();
  const message = String(body.message || body.text || "").trim();
  const html = String(body.html || "").trim();

  if (!to) {
    return json({ error: "Recipient email (to) is required" }, 400);
  }

  const htmlContent =
    html ||
    message
      .split(/\n\n+/)
      .map((p) => `<p style="margin:0 0 16px;font-family:Arial,sans-serif;line-height:1.6;">${escapeHtml(p).replace(/\n/g, "<br/>")}</p>`)
      .join("");

  try {
    const res = await fetch("https://api.brevo.com/v3/smtp/email", {
      method: "POST",
      headers: {
        accept: "application/json",
        "content-type": "application/json",
        "api-key": apiKey,
      },
      body: JSON.stringify({
        sender: { name: fromName, email: fromEmail },
        to: [{ email: to }],
        subject,
        htmlContent,
        textContent: message || undefined,
      }),
    });

    if (!res.ok) {
      const detail = await res.text();
      return json({ error: "Brevo send failed", detail: detail.slice(0, 500) }, 502);
    }

    return json({ status: "sent", to, provider: "brevo-netlify" }, 200);
  } catch (err) {
    return json({ error: err.message || "Send failed" }, 502);
  }
};

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
    },
  });
}
