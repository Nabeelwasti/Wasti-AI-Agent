# WASTI NEXUS chat workspace

## Available in Android 0.5.5

- **Conversation history:** sessions are saved after each completed assistant turn.
- **Encryption at rest:** AES-256-GCM with a non-exportable Android Keystore key; writes use AtomicFile.
- **History actions:** search by title or message, open, rename, delete and export an individual conversation as Markdown.
- **Markdown display:** headings, bold, italic, inline code, fenced code, quotes, ordered/unordered lists and separators. HTML is not executed; generated links are not automatically opened.
- **Message controls:** select/copy assistant output and copy/edit user prompts.
- **Generation controls:** local token streaming, remote single-model SSE, Stop/cancellation and truthful post-response collaboration traces.
- **Attachments:** bounded text/code files and online-only JPEG/PNG/WebP image attachments.

## Storage boundaries

- No application-imposed session count, turn count or 5 MB history cap. History grows until the device's private storage or Android process resources become the real constraint; extremely large histories can still be limited by available storage and memory.
- History is local-only and is not silently uploaded to GitHub, Cloudflare or an inference provider.
- The app loads and saves history on an IO dispatcher. AtomicFile backup recovery is attempted before reporting an empty history.
- Search includes local and remote conversation turns. The complete saved conversation is displayed; remote prompts no longer silently discard turns after the first ten.
- Attachments themselves are not retained in the session database; only the prompt and attachment names are saved.
- Markdown export is readable plaintext written to the destination the user selects. Protect that exported file like any other personal document.
- Deleting a session removes it from the encrypted local database. It does not delete any separately exported Markdown file.
- There is no cross-device encrypted history import/restore yet. Do not promise chat-history recovery after uninstall, device loss or Keystore invalidation.

## Streaming boundaries

- Local inference streams tokens.
- Remote SSE is enabled only for an explicitly selected single model.
- Auto and Hybrid remote requests remain non-streaming. They must complete routing/fallback, independent answers, peer review and synthesis before a final answer and truthful collaboration trace can be shown.
- Stop cancels the client HTTP request and the Worker propagates cancellation to the upstream fetch. A configured provider must still honor HTTP request cancellation.

## Not yet implemented

- Auto/Hybrid live progress streaming and resumable streams.
- Biometric/app-lock gate for chat history.
- Encrypted chat-history sync/restore across devices.
- PDF extraction, audio input/output, offline vision, image generation, and training.
- Tool/action execution with permission scopes, user approval, sandboxing and audit logs.
