# Security Notes

## Key Handling

- API keys can no longer be set from in-game chat.
- Store provider keys in `plugins/Ask/secrets.yml` on the server.
- The plugin creates empty `apiKey` entries for every supported key-based provider.
- Never paste API keys into Minecraft chat, Discord, issue trackers, or screenshots.

## Access Control

- `ai.use` defaults to `true`.
- `ai.admin` defaults to `op`.
- `security.allowPlayerRequests` can disable normal player access while still allowing admins.
- Grant `ai.use` only to trusted players or limited roles.

## Network Safety

- Remote providers must use `https://`.
- Plain `http://` is allowed only for local endpoints such as localhost Ollama.

## Privacy

- Player prompts are sent to the selected AI provider.
- For the best privacy, use a local provider such as Ollama.
- Keep prompts short and avoid entering personal, financial, or account data.
- Review and publish the included `PRIVACY_POLICY.md` and `TERMS_OF_SERVICE.md` for your server as needed.

## Logging

- Provider error bodies are not logged by the plugin.
- Gemini keys are sent in a header instead of the request URL.

## Output Safety

- Links are blocked by default.
- You can allow all links with `outputSafety.allowLinks: true`.
- You can keep general link blocking on and allow only specific domains with `outputSafety.allowedLinkDomains`.
