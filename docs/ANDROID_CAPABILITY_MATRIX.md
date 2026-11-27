# WASTI NEXUS Android capability and permission matrix

Updated: 2026-11-27

This document is the source of truth for Android access requests. NEXUS must request or open a permission only when a shipped feature actually uses it. Android has no supported grant-all switch. A permission being technically requestable does not make it necessary or appropriate.

## Implemented user-initiated capabilities

| Capability | Current implementation | Access model |
|---|---|---|
| Read visible UI controls | AccessibilityService bounded snapshot | User manually enables service in Android Settings |
| Click, type, scroll, tap, swipe | AccessibilityNodeInfo actions and dispatchGesture | User manually enables service; no background action loop |
| Back, Home, notifications shade | Accessibility global actions | User manually enables service |
| Screenshot | AccessibilityService.takeScreenshot one-shot API | User manually enables service; secure windows can block capture |
| Camera | TakePicturePreview system camera intent | User starts capture; no direct CAMERA permission |
| Photos | Android photo picker | Per-selection URI access |
| Files | Storage Access Framework OpenDocument / OpenDocumentTree | Per-selection URI access; no all-files permission |
| Voice input | Android speech-recognition activity | User starts recognition; speech provider may require its own network/service |
| Alarm | Android Clock ACTION_SET_ALARM | User reviews and saves in Clock |
| Calendar event | Calendar ACTION_INSERT | User reviews and saves in Calendar |
| Contacts | Opens Contacts app | User selects/shares manually; no contacts database permission |
| Wi-Fi / location / overlay / usage / notification-listener / battery settings | Settings shortcuts only | Android Settings remains the authority; no settings are silently changed |

## Not requested and why

- MANAGE_EXTERNAL_STORAGE / All files access: not needed; use Storage Access Framework and app-private storage.
- SMS and call-log permissions: no SMS or dialer core feature exists. Google Play sharply restricts these permissions to qualifying default handlers and approved use cases.
- Premium SMS: not a normal app permission for a general AI assistant; never attempt to enable it.
- Device administrator / device owner: not needed for personal device automation. Device-owner provisioning is for managed enterprise devices and is not a general runtime permission.
- REQUEST_INSTALL_PACKAGES: no app-store/installer core feature exists. NEXUS does not silently install APKs.
- SYSTEM_ALERT_WINDOW: no floating overlay feature is implemented. A settings shortcut is provided for future optional use only.
- WRITE_SETTINGS: no system-setting mutation feature is implemented.
- Notification listener: a settings shortcut exists, but NEXUS does not read notification content yet.
- Usage access: a settings shortcut exists, but NEXUS does not collect app-usage history.
- Background location, contacts database, calendar database, health/body sensors, physical activity, nearby devices, microphone recording, phone state and unrestricted data: no shipped feature currently requires these broad data permissions. Use narrower intents, pickers, or future feature-specific consent flows.
- Exact alarm scheduling and persistent reminders: not implemented as a NEXUS-owned scheduler. The current alarm shortcut hands off to Clock for user-confirmed setup.

## Safety and confirmation rules

1. User must initiate each device task.
2. Do not scrape accessibility events into logs or memory.
3. Password fields are redacted from accessibility snapshots.
4. Never bypass secure-window restrictions, permission dialogs, authentication, app security, or Android privacy controls.
5. Before sending a message, placing a call, deleting/modifying files, installing software, changing device settings, making purchases, or sharing personal data, show the exact action and require a separate user confirmation.
6. Keep screen snapshots local by default. Never send them to a hosted model without a clear per-task disclosure and explicit user choice.
7. Stop on an unknown screen, unexpected result, permission denial, timeout, or action failure.
8. Do not claim that an accessibility primitive is integrated into the AI tool loop until the tool executor, approval gate, result observation, cancellation, and device tests are implemented.

## Distribution note

The current AccessibilityService is for private owner-controlled sideloaded development. Google Play policy restricts Accessibility API automation and prohibits general apps from using it to autonomously initiate, plan, and execute actions. Do not set isAccessibilityTool=true unless the app's primary purpose genuinely qualifies as a disability accessibility tool. Review the current Play policies before any Play submission.

## Device validation still required

A successful CI build does not prove behavior on every Android device. Test on the target Redmi device and record Android/HyperOS versions, service enable/disable behavior, screen reader conflicts, screenshot behavior, gesture results, battery restrictions, and app-specific accessibility-tree limitations.