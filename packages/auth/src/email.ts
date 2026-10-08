import nodemailer from "nodemailer";

/**
 * Not wired up yet (magic link is postponed). Sends magic-link emails through a Gmail account (free).
 * Needs GMAIL_USER and GMAIL_APP_PASSWORD (a Google "app password", not your normal password).
 * Without them, in development the link is printed to the server console instead.
 */
export async function sendMagicLinkEmail(to: string, url: string) {
  const user = process.env.GMAIL_USER;
  const pass = process.env.GMAIL_APP_PASSWORD;

  if (!user || !pass) {
    if (process.env.NODE_ENV === "production") {
      throw new Error("Magic link email is not configured (GMAIL_USER / GMAIL_APP_PASSWORD).");
    }
    console.log(`\n[magic link] for ${to}:\n${url}\n`);
    return;
  }

  const transporter = nodemailer.createTransport({ service: "gmail", auth: { user, pass } });
  await transporter.sendMail({
    from: `"Brain Dump" <${user}>`,
    to,
    subject: "Your sign-in link",
    text: `Tap to sign in:\n\n${url}\n\nThis link expires in 5 minutes. If you didn't ask for it, ignore this email.`,
    html: `<p>Tap to sign in:</p><p><a href="${url}" style="display:inline-block;padding:10px 18px;background:#5b3fd6;color:#fff;border-radius:8px;text-decoration:none">Sign in</a></p><p style="color:#666;font-size:13px">This link expires in 5 minutes. If you didn't ask for it, ignore this email.</p>`,
  });
}
