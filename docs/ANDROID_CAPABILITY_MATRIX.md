# WASTI NEXUS Android capability and permission matrix

Updated: 2026-11-28

This document is the source of truth for Android access requests. NEXUS must request or open a permission only when a shipped feature actually uses it. Android has no supported grant-all switch. A permission being technically requestable does not make it necessary or appropriate.

## Implemented user-initiated capabilities

| Capability | Current implementation | Access model |
|---|---|---|
| Read visible UI controls | AccessibilityService bounded snapshot; explicit chat command can show up to 40 labels locally | User manually enables service; screen text is not sent to AI servers by this command |
| Approved device actions | Exact visible-label click, scroll up/down, Back, Home, notification shade, local screen-text snapshot | User manually enables service and confirms each supported chat command; destructive/high-impact click labels are blocked. No typing, coordinates, arbitrary workflows or background loop |
| Back, Home, notifications shade | Accessibility global actions | User manually enables service |
| Screenshot | AccessibilityService.takeScreenshot one-shot API | User manually enables service; secure windows can block capture |
| Camera | TakePicturePreview system camera intent | User starts capture; no direct CAMERA permission |
| Photos | Android photo picker | Per-selection URI access |
| Files | On-demand shared-storage search with bounded filename/text matching plus local PDF/modern Office extraction and image OCR/labels | Optional MANAGE_EXTERNAL_STORAGE; user must grant Android All files access in Settings. Android/data and Android/obb are excluded. |
| File context in chat | Natural-language file-search requests append bounded local excerpts to the active prompt | On-device route by default; sending excerpts to remote brains requires a separate opt-in in Settings |
| Voice input | Android speech-recognition activity | User starts recognition; speech provider may require its own network/service |
| Alarm | Android Clock ACTION_SET_ALARM | User reviews and saves in Clock |
| NEXUS reminders | Local reminder manager; strict natural-language parser with confirmation; SharedPreferences persistence; AlarmManager inexact alarms; notification channel; boot/time-change rescheduling; delete/cancel | User creates and deletes reminders in NEXUS; Android notification permission controls visible alerts; alarms may be delayed by Doze/battery policy |
| Calendar event | Calendar ACTION_INSERT | User reviews and saves in Calendar |
| Contacts | Read-only on-demand address-book search (up to 1,000 phone entries); contact actions open system dialer or SMS composer | Runtime READ_CONTACTS permission requested only when user opens Contacts & calling; NEXUS does not upload or persist the address book |
| SMS compose | Opens system SMS composer with selected contact number | ACTION_SENDTO handoff; user reviews and sends in the messaging app; no SEND_SMS permission |
| SMS inbox/history | Not read inside NEXUS | Android restricts READ_SMS/RECEIVE_SMS; NEXUS does not request default SMS role or installer allowlisting |
| Call history | Not read inside NEXUS; Phone app can be opened | READ_CALL_LOG is restricted; NEXUS does not request default dialer role or installer allowlisting |
| Contact-based calling | Opens system dialer with selected number | ACTION_DIAL handoff; user places the call; no CALL_PHONE permission |
| Notification content | On-demand snapshot of up to 25 active notifications through NotificationListenerService; no event cache or background persistence | User manually enables Notification access and approves the chat command; content is shown locally and saved only in the user-requested encrypted chat history |
| One-time location | Foreground-only current location fix; approximate accuracy; opens Maps via geo intent | Runtime ACCESS_COARSE_LOCATION requested only when user opens My location and taps Get current location; no background permission, tracking or history |\n| Wi-Fi / overlay / usage / battery settings | Settings shortcuts only | Android Settings remains the authority; no settings are silently changed |

## Not requested and why

- SMS and call-log permissions: no SMS or dialer core feature exists. Android and distribution channels apply additional restrictions; do not declare these permissions without a complete role/installer strategy and working product flow.
- Premium SMS: not a normal permission for a general AI assistant; never attempt to enable it.
- Device administrator / device owner: not needed for personal device automation. Device-owner provisioning is for managed devices and is not a general runtime permission.
- REQUEST_INSTALL_PACKAGES: no app-installer feature exists. NEXUS does not silently install APKs.
- SYSTEM_ALERT_WINDOW: no floating overlay feature is implemented.
- WRITE_SETTINGS: no system-setting mutation feature is implemented.
- Notification listener: supported for explicit on-demand reading only; the service does not persist notification events or forward content to online models.
- Usage access: a settings shortcut exists, but NEXUS does not collect app-usage history.
- Background location, health/body sensors, physical activity, nearby devices, microphone recording, phone state and unrestricted data: no shipped feature currently requires these broad data permissions. Use narrower intents, pickers, or future feature-specific consent flows.
- Exact alarm scheduling is not enabled. NEXUS reminders use inexact AlarmManager scheduling and can be delayed by Android power management; they are not a replacement for safety-critical alarms.

## Approved device action boundaries

- Supported exact chat commands: `tap the Settings button`, `scroll down`, `scroll up`, `go back`, `go home`, `open notifications`, and `read current screen`, and `read my notifications` (requires separately enabled Notification access). The app displays an action summary and waits for a separate approval before execution.
- Label-click actions require an exact visible text/content-description match and a clickable, enabled control. Destructive/high-impact labels (delete/remove/send/submit/pay/transfer/install/allow/permission/credentials, etc.) are rejected by the parser.
- Screen text and notification content are returned in the chat locally and are not passed to a model by these commands. Password fields remain redacted by the AccessibilityService. Secure windows and app-specific accessibility restrictions still apply.
- Android accepting a click/gesture does not prove the target app completed the requested business operation. The user must inspect the screen after each action. No chained or autonomous action plans are supported.

## Shared-storage search boundaries

- The user must explicitly grant Android's special All files access in Settings. NEXUS does not grant it silently.
- Search is on demand, bounded to 12,000 filesystem entries and at most ten results.
- Plain-text/code formats are searched for filename and bounded text matches. PDF (first 100 pages), DOCX/XLSX/PPTX and ODT/ODS/ODP are extracted locally; JPEG/PNG/WebP receive on-device OCR and coarse visual labels. Legacy DOC/XLS/PPT, scanned-PDF OCR, HEIC/HEIF shared-storage search, video and audio remain metadata-only/unsupported.
- Android/data, Android/obb, other apps' private sandboxes, caches and inaccessible external volumes are excluded.
- Excerpts are marked as untrusted data in the model context. They are not executed as instructions.
- Remote file-context sharing is disabled by default. It requires a separate opt-in; otherwise the request must route to an on-device model.
- The app does not silently index the full contents of all files into long-term memory.

## Reminder scheduling limits

- Supported chat syntax: `Remind me to <task> in 15 minutes`, `... in 2 hours`, `... in 3 days`, `... tomorrow at 5:30 pm`, and `... today at 8 am`. The parsed reminder is shown in a confirmation dialog before saving. Unsupported or ambiguous phrasing is not executed.
- Reminders are stored locally and restored after reboot/time/time-zone changes. Deleting a reminder cancels its pending alarm.
- Scheduling uses `AlarmManager.setAndAllowWhileIdle` (inexact). Android can defer alerts for Doze, battery restrictions, OEM task management, or force-stop. Notifications require the user to grant Android notification permission. This is not suitable for medical, emergency, financial-deadline, or other safety-critical alerts.
- There is no exact-alarm special access request and no claim of exact-to-the-minute delivery.

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