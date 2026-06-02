---
title: MCP Server
description: Render accessible PDFs from an AI agent using the built-in Model Context Protocol server.
---

The API ships a built-in [Model Context Protocol](https://modelcontextprotocol.io) (MCP) server, so
AI agents such as Claude Code and Codex can generate accessible PDF/A-3a + PDF/UA documents directly
as a tool call — no glue code required.

The server is exposed at `/mcp` and offers three tools:

| Tool | Description | Required input |
| --- | --- | --- |
| `render_template` | Render a pdf-ua-api JSON [template](/templates/structure/) | `template` |
| `render_html` | Render raw HTML | `html` |
| `render_url` | Fetch a URL and render its HTML | `url` |

Each tool returns the same payload: the document UUID, the content type, a file name, and the PDF
itself as a base64 string (`pdfBase64`).

## Endpoint & transport

- **URL:** `http://<host>:8080/mcp` (the path is `/mcp`)
- **Transport:** HTTP + SSE (the agent opens an SSE stream on `GET /mcp` and posts messages back).
- **Auth & limits:** the `/mcp` endpoint sits behind the same envelope as the render endpoints — the
  configured [authentication](/authentication/) and [rate limits](/rate-limiting/) apply. When an
  `API_KEY` is set, send it as `Authorization: Bearer <API_KEY>`. When neither `API_KEY` nor JWT is
  configured, the endpoint is open (handy for local testing).

## Running the server

The MCP server is always on; it is part of the standard application, so any way you run the API also
serves `/mcp`.

```bash
# open (no auth) — good for local testing
docker run -p 8080:8080 ghcr.io/bambamboole/pdf-ua-api:latest

# protected with an API key
docker run -p 8080:8080 \
  -e API_KEY="super-secret-key" \
  ghcr.io/bambamboole/pdf-ua-api:latest
```

Confirm it is reachable — the SSE stream opens and emits an `endpoint` event:

```bash
curl -N http://localhost:8080/mcp
# event: endpoint
# data: ?sessionId=...
```

Add `-H "Authorization: Bearer super-secret-key"` if you started the server with an `API_KEY`.

## Connect Claude Code

Claude Code talks to remote MCP servers over SSE directly. Register the server with `claude mcp add`:

```bash
# local, open server
claude mcp add --transport sse pdf-ua-api http://localhost:8080/mcp

# remote / protected server
claude mcp add --transport sse pdf-ua-api https://your-host/mcp \
  --header "Authorization: Bearer super-secret-key"
```

Then verify and use it:

```bash
claude mcp list          # pdf-ua-api should report "connected"
```

Inside a Claude Code session, run `/mcp` to see the `render_template`, `render_html`, and
`render_url` tools, then ask it to “render this HTML to an accessible PDF” and Claude will call the
tool and save the returned base64 PDF.

Use `--scope user` to make the server available across all your projects, or `--scope project` to
share it with your team via a committed `.mcp.json`.

## Connect Codex

Codex launches MCP servers as local processes (stdio), so reach the HTTP/SSE endpoint through the
[`mcp-remote`](https://www.npmjs.com/package/mcp-remote) bridge. Add the server to
`~/.codex/config.toml`:

```toml
# local, open server
[mcp_servers.pdf-ua-api]
command = "npx"
args = ["-y", "mcp-remote", "http://localhost:8080/mcp"]
```

For a protected server, pass the bearer token as a header (and keep the secret in `env`):

```toml
[mcp_servers.pdf-ua-api]
command = "npx"
args = ["-y", "mcp-remote", "https://your-host/mcp", "--header", "Authorization: Bearer ${PDF_UA_API_KEY}"]
env = { PDF_UA_API_KEY = "super-secret-key" }
```

Restart Codex; the three render tools then appear in its tool list.

## Local development

To run the server straight from a checkout while you iterate:

```bash
# starts http://localhost:8080 with /mcp open (no API_KEY set)
./gradlew run

# disable rate limiting if you are hammering it during testing
RATE_LIMIT_ENABLED=false ./gradlew run
```

Point Claude Code or Codex at `http://localhost:8080/mcp` as shown above. To remove the server again
from Claude Code, run `claude mcp remove pdf-ua-api`.

## Tool reference

### `render_html`

```json
{
  "html": "<html lang=\"en\"><head><title>Doc</title></head><body><h1>Hi</h1></body></html>",
  "baseUrl": "https://example.com",
  "embedColorProfile": true
}
```

### `render_url`

```json
{
  "url": "https://example.com",
  "embedColorProfile": true
}
```

### `render_template`

Takes a `template` object (see [Template structure](/templates/structure/)) plus optional per-block
`data` overrides:

```json
{
  "template": {
    "version": 2,
    "rows": [{ "blocks": [{ "type": "text", "text": "Hello from MCP" }] }]
  }
}
```

All three tools also accept an optional `attachments` array to embed files in the PDF/A-3 container.
On a validation error the tool result is flagged as an error with a structured `error` payload (for
example `validation_failed` with the offending issues), so the agent can correct the input and retry.
