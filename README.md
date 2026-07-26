# Ask

Ask is an open-source Minecraft AI chat plugin for Paper servers.

Players can ask questions directly with `/Ask <message>`, while admins can manage provider settings with the legacy `/AI` admin commands.

## Features

- Direct player chat command: `/Ask <message>`
- Legacy compatibility: `/AI ask <message>`
- Multiple providers: OpenAI, OpenRouter, Grok, Together, DeepSeek, Anthropic, Gemini, and Ollama
- Server-side `secrets.yml` key storage
- Admin-only provider/model/reload controls
- Short Minecraft-friendly replies with chat splitting
- Basic rate limits and output safety filtering
- Private replies sent only to the player who asked

## Commands

### Player Commands

- `/Ask <message>`
- `/AI ask <message>`
- `/AI reset`
- `/AI status`

### Admin Commands

- `/AI API`
- `/AI provider <provider>`
- `/AI model <model>`
- `/AI reload`

## Setup

1. Build the plugin with:

```bash
gradle build
```

2. Put the jar from `build/libs/` into your Paper server `plugins` folder.
3. Start the server once.
4. Open `plugins/Ask/secrets.yml`.
5. Paste your provider key under the matching `apiKey:` entry.
6. Run `/AI reload` or restart the server.

Example:

```yml
providers:
  openai:
    apiKey: "your-key-here"
```

## Security Notes

- API keys cannot be set from Minecraft chat.
- Replies are private to the player who asked.
- Admin actions require `ai.admin`.
- Player access can be limited in `config.yml`.
- Hosted AI providers receive player prompts. For stronger privacy, use Ollama locally.

See [SECURITY.md](./SECURITY.md), [PRIVACY_POLICY.md](./PRIVACY_POLICY.md), and [TERMS_OF_SERVICE.md](./TERMS_OF_SERVICE.md) for more details.

## Requirements

- Paper `1.20.6+`
- Java `21`

## License

MIT
