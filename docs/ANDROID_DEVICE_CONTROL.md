# Android device-control foundation

## Current implementation

WASTI NEXUS includes an optional, user-enabled Android AccessibilityService. The service is intentionally passive: it does not continuously collect UI text, record screens, or autonomously run tasks. A caller must explicitly request a snapshot or a single action.

Supported primitives:
- Read exposed accessibility nodes in the active window (maximum 600 nodes per request).
- Redact text and content descriptions for password fields.
- Click an enabled accessible node by exact or partial visible label.
- Set text in an editable node identified by its view ID.
- Scroll an exposed scrollable node forward or backward.
- Dispatch a tap or swipe gesture.
- Invoke Back, Home, and Notifications global actions.

The service is not root access and does not bypass secure windows, app permissions, protected content, authentication, or OS policy. Some apps expose incomplete accessibility trees or block non-accessibility tools from sensitive controls. Android 16+ has additional sensitive-view protections.

## Enable on the phone

1. Open WASTI NEXUS settings.
2. Read the device-control disclosure and choose to open Android Accessibility settings.
3. Enable WASTI NEXUS Device Control yourself.
4. Return to NEXUS. Android may show an additional warning; review it before enabling.

The service can be disabled at any time in Android Settings. Do not enable it on a device you do not own or administer.

## Approval-gated chat actions

The chat composer now recognizes a small deterministic command set. Each recognized command opens an approval dialog before execution:

- `tap the Settings button`: click an enabled control only when its exact visible label matches uniquely (if the implementation cannot establish a unique target, it must fail closed).
- `scroll down` / `scroll up`
- `go back` / `go home`
- `open notifications`
- `read current screen`: display a bounded snapshot locally; it is not passed to a model.
- `read my notifications`: requires separately enabled Notification access and displays a bounded active-notification snapshot locally.

The notification listener does not cache posted events or persist content in the background. A user-requested notification snapshot can be saved in the encrypted conversation history. Neither screen text nor notification content from these deterministic commands is sent to a model.

The executor rejects high-impact click labels such as delete, send, submit, pay, transfer, install, allow, and permission. It does not type text, perform arbitrary coordinate taps, chain actions, make calls, send messages, install APKs, change permissions, or run background plans. Android accepting a click/gesture is not proof that the target app completed an operation. The user must inspect the result.

This is a narrow approved command executor, not a general autonomous agent. It does not claim reliable control of every third-party app or replace Android permission and security controls.

## Distribution

This feature is intended for the owner's private sideloaded build. Google Play restricts Accessibility API use for general assistants that autonomously plan and execute actions. NEXUS must not misdeclare itself as an accessibility tool or set isAccessibilityTool=true. A future Play-distributed build must disable this automation feature or be redesigned for a genuine disability-accessibility primary purpose and reviewed against current Play policies.


## Shared-storage file search

NEXUS can perform an on-demand search of user-visible shared storage after the owner grants Android's special All files access permission in Settings. The feature is not a silent background indexer.

- Natural-language requests such as "find my passport photo" and "search for my salary spreadsheet" trigger a bounded local scan.
- Search examines up to 12,000 filesystem entries and returns at most ten matches.
- Text/code formats are searched by filename and a bounded local text excerpt. PDF, Office, image, video and audio files currently return metadata matches only; no OCR, PDF text extraction or Office parsing is claimed.
- Android/data and Android/obb remain excluded, as do other apps' private sandboxes and inaccessible directories.
- File excerpts are marked as untrusted content in the model prompt. They are never executed as instructions.
- Remote model sharing is off by default. It requires a separate opt-in in NEXUS Settings. Without that opt-in, a file-search request must use a local model and fails closed if the selected route would send data remotely.
- The app does not persist file contents in chat history or automatically add them to long-term memory.
