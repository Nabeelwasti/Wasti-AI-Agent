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

## Integration boundary

The service exposes the in-process current service instance for app callers. Invoke its methods on the Android main thread. A snapshot returns a fresh, bounded list of UI nodes. The service does not automatically feed screen content into local or remote model prompts. A future tool executor must request a fresh snapshot only after a user-started task, keep screen context local by default, require confirmation for consequential actions, and record an auditable action result without storing screen text.

This commit provides the Android control foundation and setup UX. It does not yet claim a complete autonomous agent loop, screenshot capture, MediaProjection, app launching, or reliable control of every third-party app.

## Distribution

This feature is intended for the owner's private sideloaded build. Google Play restricts Accessibility API use for general assistants that autonomously plan and execute actions. NEXUS must not misdeclare itself as an accessibility tool or set isAccessibilityTool=true. A future Play-distributed build must disable this automation feature or be redesigned for a genuine disability-accessibility primary purpose and reviewed against current Play policies.
