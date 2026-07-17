# Cloud services, update, and configuration architecture

Source status: current Confluence page version 28, last registry review 2026-07-14. This is a local implementation-oriented digest of `06 - Cloud Services, Update & Configuration Architecture`; Confluence remains authoritative.

## Purpose

This architecture page defines DCAM's cloud-provider boundary, device identity, Web Provisioning Portal boundary, remote configuration, kiosk requested-policy boundary, update-provider direction, and future WebServer/service-adapter direction.

It is not the source of truth for detailed provisioning screens, exact remote-config payload fields, SQLite table dictionaries, security/key management, Device Owner/Lock Task policy mechanics, in-app maintenance UX, self-update install mechanics, feature eligibility states, or update preconditions. Those details belong to their own authoritative documents.

## Authoritative references

| Topic | Authoritative source | Local summary |
|---|---|---|
| Device config file and device information | `04 - Device Configuration Requirements` | `serial_number`, `owner_name`, and `manufacture_date` are device information, not primary keys. |
| Provisioning business flow, screens, QR, API direction, states, and audit | `DCAM Device Provisioning Web Portal Design` | Web Portal QR provisioning flow is defined there. Draft details are not mirrored here. |
| Kiosk policy | `DCAM Android Device Owner & Kiosk Policy Design` | Device Owner/DPC, Lock Task, User Restrictions, Home/Launcher, Maintenance Mode, and no-external-EMM baseline are defined there. |
| In-app console and controlled maintenance | `DCAM In-App Operation, Device Settings & Media Console Design` | Setting hub, Controlled Mode, Maintenance Password Gate, and optional Play Store fallback UX are defined there. |
| Remote config validation and setting apply | `09 - System Settings Requirements` | Remote config provides requested values; DCAM validates, caches, and applies them only when runtime guards allow. |
| Kiosk requested-policy settings | `09 - System Settings Requirements` | Cloud/WebServer may publish requested kiosk settings; Android applies only when policy authority and runtime guards allow. |
| Device identity local persistence | `DCAM SQLite Database Design` | `dcam_cloud_device_id`, `android_id_hash`, provisioning state, and config cache belong in `dcam.db`. |
| Startup identity restore, provisioning, and kiosk verification | `DCAM Android Operation Design` | Startup handles local identity, recovery lookup, provisioning-required state, and kiosk policy verification. |
| Identity, provisioning, policy, and update security | `DCAM Security & Encryption Design` | Do not log original Android identifiers; provisioning, policy, update, and maintenance actions must be auditable. |
| Capability and eligibility | `DCAM Device Capability & Feature Eligibility Design` | Remote config cannot enable runtime behavior when the device lacks required capability. |
| AutoUpdate preconditions | `09 - System Settings Requirements` | Cloud/update flow must check the full AutoUpdate precondition set from System Settings. |
| Self-update download, validation, and install | `DCAM Self Update Design` | Cloudflare R2 is only an artifact provider; app/device policy owns update handling. |
| Update state priority | `DCAM State Machine Design` | Update has lower priority than recording, emergency, finalizing, and unsafe runtime states. |
| DCAM-BDMA compatibility | `DCAM-BDMA Data Contract` | App/data/media/encoder contract versions identify compatibility; `bdma_decoder_profile_id` is not used. |

## Approved decisions

| Area | Decision |
|---|---|
| Cloud provider | DCAM must not depend directly on one concrete cloud provider. |
| Device primary key | Firebase/WebServer primary key is `dcam_cloud_device_id`. |
| Recovery lookup key | `android_id_hash` is used to find an existing device record when local state is lost. |
| Serial number | `serial_number` is mutable device information, not a primary key. |
| Owner name | `owner_name` is mutable/semi-static device information, not a primary key. |
| Manufacture date | `manufacture_date` is semi-static device information in `YYYY-MM-DD` format, not a primary key. |
| Advertising ID | Advertising ID must not be used as the DCAM identity key. |
| Provisioning method | Default factory provisioning uses the Web Provisioning Portal QR flow. |
| Device Owner setup | Device Owner/DPC setup is separate from DCAM Web Portal business provisioning and requires Device POC validation. |
| External EMM / Managed Google Play | Not the current baseline; do not assume Android Management API or Managed Google Play policy-driven update. |
| Provisioning business flow | Detailed flow, screens, QR, backend API direction, states, and audit belong to the Web Portal Design. |
| Remote config | Provider publishes target config revision; DCAM fetches, validates, caches, and applies safely. Payload fields remain TBD. |
| Kiosk requested policy | Cloud/WebServer may publish requested kiosk settings, but Android validates policy authority and runtime guard before apply. |
| Capability boundary | Capability and eligibility rules belong to Device Capability Design; this page does not duplicate the state list. |
| App/contract compatibility | Firestore/WebServer may store app/version/data/media/encoder contract metadata for BDMA/support compatibility. |
| BDMA decoder profile | `bdma_decoder_profile_id` is not used; BDMA uses built-in compatibility logic based on app and contract versions. |
| App update | DCAM Self Update / APK update is the primary path for the current no-external-EMM baseline. |
| Manual Play Store fallback | Optional controlled fallback only if GMS/Play Store exists and the maintenance/factory process is approved. |
| Future model update | Model packages, if added, must be validated and checked for compatibility. |
| Future WebServer | Live Streaming, PTT, SOS, JT808, and optional server analytics go through adapters. |

## Device identity model

| Field | Role | Storage |
|---|---|---|
| `dcam_cloud_device_id` | Firebase/WebServer primary key | Firebase/WebServer and `dcam.db` |
| `android_id_hash` | Recovery lookup key when local DB/CSON is lost | Firebase/WebServer lookup index and `dcam.db` |
| `serial_number` | Mutable device information | `dcam_config.cson`, `dcam.db` mirror, Firebase/WebServer |
| `owner_name` | Mutable/semi-static owner/customer/agency display | `dcam_config.cson`, `dcam.db` mirror, Firebase/WebServer |
| `manufacture_date` | Semi-static manufacture date, ISO `YYYY-MM-DD` | `dcam_config.cson`, `dcam.db` mirror, Firebase/WebServer |
| `serial_history` | Historical serial values | Firebase/WebServer; optional DB mirror |
| `firebase_installation_id` | Current app-install instance metadata | Firebase/WebServer and `dcam.db` metadata |

## First install and identity recovery

Default provisioning for new BodyCamera devices is the Web Provisioning Portal QR flow. Detailed portal screens, QR payload direction, backend API direction, provisioning states, audit, and error handling are outside this page and remain owned by the Web Portal Design.

DCAM uses a stable server-side device record. If local `dcam.db` or `dcam_config.cson` is lost, `android_id_hash` is the recovery lookup key. The original Android system identifier must not be logged.

Web Portal QR provisioning is DCAM business provisioning. It does not make the Android app Device Owner. Device Owner/DPC setup is a separate deployment/POC track.

## Remote configuration flow

Remote config payload fields are still TBD. The approved baseline is:

- Cloud/WebServer publishes a target config revision.
- DCAM fetches remote config by `dcam_cloud_device_id`.
- DCAM validates values locally before applying them.
- DCAM caches accepted config in `dcam.db`.
- DCAM applies settings only when runtime guards allow it.
- Remote config must not override hardware capability or eligibility rules.
- Kiosk policy values from cloud are requested policy only; Android policy managers must validate Device Owner/DPC authority, Lock Task allowlist, User Restriction support, and runtime guard before applying.
- Unsafe or unsupported values must be rejected or deferred without breaking local recording/capture.

## Provider abstraction

Provider abstraction exists to keep DCAM independent from one cloud implementation and to permit future provider changes.

Concrete providers, Firebase, custom REST/WebServer, Cloudflare R2, and local-only fallback must sit behind adapters. UI, ViewModel, use cases, and core business logic should not depend directly on provider SDKs.

## AutoUpdate alignment

AutoUpdate must use the full precondition list from `09 - System Settings Requirements`. Update work has lower priority than recording, emergency, finalizing, and unsafe runtime states.

The current no-external-EMM baseline uses DCAM Self Update / APK update as the primary path. Cloudflare R2 or another provider is only an artifact source; it does not own install policy, validation, rollback, or runtime safety.

Manual Google Play Store update is an optional controlled fallback only when the target device has GMS/Play Store and Product/Security approve the maintenance/factory process. It must run through Admin/Maintenance Controlled Mode after the Maintenance Password Gate, must update only DCAM/approved apps, must not use a personal Google account for production maintenance, and must restore kiosk policy afterward.

## Open items

The approved cloud architecture leaves these items TBD or owned by other documents:

- Exact remote config payload fields.
- Config profile schema and rollout algorithm.
- FCM/direct/topic wake-up policy.
- Exact polling interval.
- Web Portal detailed UI wireframe.
- Provisioning API exact contract.
- QR payload signature and expiration format.
- Rollback UI and audit detail.
- Self-update manifest, checksum/signature validation, install mechanism, rollback, and failure handling.
- Exact DCAM-as-DPC / Device Owner setup method and policy-safe update/maintenance window contract.
- Manual Play Store fallback availability and maintenance/factory Google account handling.
- Device capability detection matrix on real BodyCamera hardware.

## Current code change list

This section is a local repository delta, not an official requirement source.

Current code must move toward the approved cloud architecture in these areas:

- Introduce an application-owned cloud/config/update boundary instead of letting platform code or logging-specific classes become the cloud abstraction.
- Model device identity with `dcam_cloud_device_id`, `android_id_hash`, mutable `serial_number`, serial history, Firebase installation metadata, and provisioning state.
- Add `owner_name`, `manufacture_date`, app/data/media/encoder contract metadata, and no-`bdma_decoder_profile_id` compatibility handling to identity/support metadata.
- Store identity, provisioning state, remote-config cache, and operational settings in `dcam.db`; keep `dcam_config.cson` limited to device information.
- Add startup identity restore and recovery lookup flow before normal operation when local identity state is missing.
- Add provisioning-required state and Web Provisioning Portal QR handoff; do not hardcode portal/API details until the Web Portal Design is approved.
- Implement remote config fetch, validation, cache, apply/defer/reject behavior, and audit logging around `dcam_cloud_device_id`.
- Ensure remote config cannot enable unsupported runtime behavior when device capability/eligibility says no.
- Ensure requested kiosk policy and in-app console settings apply only through policy managers and runtime guards.
- Add an update service/use case that checks System Settings AutoUpdate preconditions and State Machine priority before any download/install work.
- Keep artifact providers behind adapters; implement separate validation/install/rollback policy after Self Update Design is approved.
- Keep future WebServer services such as live streaming, PTT, SOS, JT808, model updates, and analytics behind adapters and outside capture-critical paths.
