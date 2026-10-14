import { readFileSync } from "node:fs";

const { ANTHROPIC_API_KEY, LINKEDIN_ACCESS_TOKEN, DRY_RUN, GITHUB_EVENT_PATH, GITHUB_REPOSITORY } = process.env;

// 1. Read what was pushed
const event = JSON.parse(readFileSync(GITHUB_EVENT_PATH, "utf8"));
const commits = (event.commits || []).map((c) => `- ${c.message.split("\n")[0]}`).join("\n");
const repoName = GITHUB_REPOSITORY.split("/")[1];
const repoUrl = `https://github.com/${GITHUB_REPOSITORY}`;

if (!commits) {
  console.log("No commits found in this push. Nothing to post.");
  process.exit(0);
}

// 2. Ask Claude to write the post
const prompt = `Write a LinkedIn post for a developer who just pushed code to GitHub.

Project: ${repoName}
Commit messages:
${commits}

Rules:
- Professional but friendly, first person, simple English
- 80 to 140 words
- Say what was built or improved and what was learned
- Do not invent features that are not in the commit messages
- End with 3 to 4 relevant hashtags
- Return only the post text`;

const claudeRes = await fetch("https://api.anthropic.com/v1/messages", {
  method: "POST",
  headers: {
    "content-type": "application/json",
    "x-api-key": ANTHROPIC_API_KEY,
    "anthropic-version": "2023-06-01",
  },
  body: JSON.stringify({
    model: "claude-sonnet-5-5",
    max_tokens: 600,
    messages: [{ role: "user", content: prompt }],
  }),
});

if (!claudeRes.ok) throw new Error(`Claude API error: ${claudeRes.status} ${await claudeRes.text()}`);
const claudeData = await claudeRes.json();
const post = `${claudeData.content[0].text.trim()}\n\n${repoUrl}`;

console.log("---- Generated post ----\n" + post + "\n------------------------");

if (DRY_RUN !== "false") {
  console.log("DRY_RUN is on. Nothing was posted to LinkedIn.");
  process.exit(0);
}

// 3. Find your LinkedIn member id
const meRes = await fetch("https://api.linkedin.com/v2/userinfo", {
  headers: { Authorization: `Bearer ${LINKEDIN_ACCESS_TOKEN}` },
});
if (!meRes.ok) throw new Error(`LinkedIn userinfo error: ${meRes.status} ${await meRes.text()}`);
const me = await meRes.json();

// 4. Publish the post
const postRes = await fetch("https://api.linkedin.com/v2/ugcPosts", {
  method: "POST",
  headers: {
    Authorization: `Bearer ${LINKEDIN_ACCESS_TOKEN}`,
    "Content-Type": "application/json",
    "X-Restli-Protocol-Version": "2.0.0",
  },
  body: JSON.stringify({
    author: `urn:li:person:${me.sub}`,
    lifecycleState: "PUBLISHED",
    specificContent: {
      "com.linkedin.ugc.ShareContent": {
        shareCommentary: { text: post },
        shareMediaCategory: "NONE",
      },
    },
    visibility: { "com.linkedin.ugc.MemberNetworkVisibility": "PUBLIC" },
  }),
});

if (!postRes.ok) throw new Error(`LinkedIn post error: ${postRes.status} ${await postRes.text()}`);
console.log("Posted to LinkedIn successfully.");
