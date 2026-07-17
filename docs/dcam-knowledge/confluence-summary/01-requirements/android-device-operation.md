# Android device operation requirements

Source status: current Confluence page version 10, last registry review 2026-07-14. This is a local implementation-oriented digest; the [Confluence requirement](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496661) remains authoritative.

## Approved operating model

DCAM is a dedicated BodyCamera application. When device policy permits, Android boots into or starts DCAM, DCAM enters dedicated operation, the operator logs in or uses emergency override where allowed, capture/settings/device functions remain inside the app, required services keep local data ready, and BDMA later imports according to the Data Contract.

| Area | Requirement |
|---|---|
| Dedicated screen | Main operation uses the primary screen for the dedicated-device experience while preserving required Android system indicators when policy allows. |
| Device Owner / DPC | DCAM must support Device Owner/DPC-capable policy behavior if target firmware and factory process allow it. POC is required. |
| No external EMM baseline | Current baseline must not depend on external EMM, Android Management API, or Managed Google Play policy-driven update. |
| Lock Task / restrictions | Normal field operation may require Lock Task Mode and approved User Restrictions when deployment profile requires kiosk. |
| Home/launcher | DCAM may be configured as the Home/Launcher app, but Home/Launcher does not replace Lock Task Mode. |
| Boot startup | DCAM can start after `BOOT_COMPLETED` when device policy and permissions allow it; manual startup remains available for maintenance/fallback. |
| In-app console | DCAM must provide in-app operation/settings/device/media screens when users cannot leave the app for Android Settings or external file managers. |
| File/media visibility | Storage summary, media list/viewer, and evidence-safe file visibility are required; file/media actions are read-only unless a future approved design changes this. |
| Exit control | Dedicated operation should prevent accidental exit; temporary kiosk exit must use controlled Maintenance Mode and a Maintenance Password Gate when required. |
| Foreground operation | Recording, emergency recording, finalization, location, storage monitoring, recovery, and device work use Android foreground/background mechanisms appropriate to their lifetime. |
| Lifecycle | Handle start, pause, resume, stop, restart, boot complete, screen on/off, and device reconnect. |
| Operator session | Normal recording/capture evidence requires an active operator session. Emergency recording may use `EMERGENCY_OVERRIDE_ADMIN` when policy allows. |
| Feature eligibility | DCAM evaluates capability/eligibility before optional runtime modules initialize. |
| Permissions | Camera, microphone, location, storage/media, notification, NFC if used, install-package if Self Update uses it, and device-policy permissions must not crash the app when denied/unavailable. |
| Power | Account for battery optimization, screen/wake behavior, and Android background-execution limits. |
| Self Update | DCAM Self Update / APK update is the primary update path for the current no-EMM baseline; optional Play Store fallback requires controlled maintenance and approval. |
| Recovery | Define behavior after app crash, service kill, reboot, abnormal power loss, missing Device Owner state, interrupted update, DB/storage corruption, or policy restore failure. |

## Implementation boundary

The requirement intentionally does not prescribe the exact Activity flags, BroadcastReceiver, Foreground Service ownership, Device Owner provisioning flow, Lock Task feature flags, User Restriction profile, WakeLock, maintenance credential policy, Self Update install mechanism, or battery-optimization exemptions. Those choices require Technical Design and real-device policy validation.

Current source intentionally keeps Android status and navigation bars visible so operators can see device indicators such as battery, Wi-Fi/network state, GPS/location status, notifications, and navigation controls. Runtime permission handling, lifecycle-bound CameraX, and a recording foreground notification/service exist. Boot startup, Home role, Device Owner/DPC policy, Lock Task/User Restrictions, controlled maintenance, in-app console scope, operator-session gate, screen/power policy, durable recording ownership, Self Update, feature eligibility runtime pruning, exact dedicated-screen behavior, and crash/reboot recovery remain open.
