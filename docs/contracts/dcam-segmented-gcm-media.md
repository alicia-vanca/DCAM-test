# DCAM Segmented AES-256-GCM Media Contract

Contract ID: `dcam-segmented-gcm/1`

This is an internal binary revision. It does not change the `_enc` suffix and does not create an `_enc2` filename.

## Required behavior

- DCAM writes encrypted media while recording without a file-sized plaintext temporary file.
- Complete MP4 fragments recover exactly as they do for plaintext fragmented MP4.
- Sequential tail DATA records are visible when their individual GCM authentication succeeds; they do not wait for a global COMMIT.
- COMMIT is used when an update cannot be one sequential tail DATA generation: non-tail rewrites, multi-record changes, or truncate changes.
- BDMA ExternalMediaDecrypt detects this format from file content, never from filename.
- A detected segmented-GCM file with invalid metadata or authentication never falls back to legacy CTR.
- Legacy `_enc` files without the segmented-GCM family magic retain the existing whole-file AES-256-CTR decoder.

## Integer encoding and limits

- All integers are unsigned big-endian unless stated otherwise.
- Java `long` fields must fit in the non-negative signed `long` range.
- Header and record reserved bytes must be zero.
- Block size must be a power of two from 4 KiB through 1 MiB.
- Implementations must bound lengths before allocation, multiplication, seek, or write.

## File header: 96 bytes

| Offset | Bytes | Field | Value |
| ---: | ---: | --- | --- |
| 0 | 16 | primary magic | ASCII `DCAM-GCM-MEDIA\r\n` |
| 16 | 2 | format revision | `1` |
| 18 | 2 | header bytes | `96` |
| 20 | 4 | flags | `0` |
| 24 | 4 | logical block bytes | power of two, 4096..1048576 |
| 28 | 4 | PBKDF2 iterations | 100000..2000000; DCAM default `200000` |
| 32 | 2 | KDF ID | `1` = PBKDF2-HMAC-SHA256 over UTF-8 password bytes |
| 34 | 2 | cipher ID | `1` = AES-256-GCM |
| 36 | 2 | nonce bytes | `12` |
| 38 | 2 | tag bytes | `16` |
| 40 | 16 | KDF salt | cryptographically random per file |
| 56 | 16 | file ID | cryptographically random per file |
| 72 | 16 | mirror magic | same 16 bytes as primary magic |
| 88 | 4 | reserved | zero |
| 92 | 4 | header CRC32 | CRC32 of bytes 0..91 |

Content detection:

`Magic` is the fixed 16-byte file signature, not a key or authentication tag. `Primary` is the copy at byte 0; `mirror` is the duplicate at byte 72. The duplicate lets a reader still classify the file as segmented GCM when only one copy is damaged.

1. If primary magic or mirror magic is present, classify the file as segmented GCM and require the complete header to validate.
2. If neither magic is present, classify the file as legacy CTR.
3. Once classified as segmented GCM, any header, record, KDF, tag, sequence, or FINAL failure is fatal. Do not attempt CTR.
4. If both magic copies are absent, content-only detection cannot distinguish a damaged segmented-GCM file from legacy raw CTR; the reader follows legacy CTR. Preventing that downgrade requires an external authenticated discriminator or a changed legacy/container convention.

Both magic copies and the CRC are format checks, not authentication. Every record authenticates the exact 96 header bytes as AAD.

## Key derivation

- Password bytes are the exact UTF-8 encoding of the configured password. No Unicode normalization is applied.
- Derive one 32-byte file key with PBKDF2-HMAC-SHA256 using the header salt and iteration count.
- DCAM clears temporary password bytes, derived keys, and intermediate PBKDF2 state after use.
- A new random salt creates a new file key even when the configured password is unchanged.

## Record layout

Every record is append-only:

```text
[96-byte record header][16-byte GCM tag][ciphertext]
```

Tag precedes ciphertext so a writer can compute one bounded block in RAM and persist authenticated metadata before ciphertext without a second disk file.

### Writer ordering and durability

- One writer owns the physical file. It appends records in sequence and finishes all bytes of record N before starting record N+1.
- Encryption buffering is bounded by one logical block plus record metadata; no file-sized plaintext staging file is permitted.
- CHECKPOINT persistence must be no weaker than the current plaintext recorder fragment-boundary policy. Encryption must not defer a completed fragment behind bytes from a later fragment.
- FINAL and all preceding records must be forced to durable storage before DCAM writes its completion marker or publishes the `_enc` file.

### Record header: 96 bytes

| Offset | Bytes | Field |
| ---: | ---: | --- |
| 0 | 4 | ASCII record magic `DCRD` |
| 4 | 2 | record-header bytes = `96` |
| 6 | 1 | record type |
| 7 | 1 | flags = `0` |
| 8 | 4 | total record bytes = 96 + 16 + plaintext bytes |
| 12 | 8 | sequence; starts at 1 and increases exactly by 1 |
| 20 | 8 | transaction ID |
| 28 | 8 | logical block index |
| 36 | 4 | plaintext/ciphertext bytes |
| 40 | 4 | related DATA record count |
| 44 | 8 | resulting logical plaintext length |
| 52 | 12 | unique random GCM nonce |
| 64 | 28 | reserved = zero |
| 92 | 4 | record-header CRC32 of bytes 0..91 |

GCM input:

- Key: derived 32-byte file key.
- Nonce: record nonce.
- AAD: exact 96-byte file header followed by exact 96-byte record header.
- Plaintext: DATA block bytes, or empty for COMMIT, CHECKPOINT, and FINAL.
- Physical output: 16-byte tag followed by ciphertext.
- A nonce must never repeat under one file key. Readers reject duplicate nonces.

## Record types

### `1` DATA

DATA contains a complete logical-block generation, not a byte delta.

Sequential autocommit DATA:

- transaction ID = `0`.
- related count = `0`.
- block index must be the current logical tail block.
- resulting logical length must equal block start plus plaintext bytes.
- If rewriting a partial tail block, payload includes the complete existing prefix plus newly appended bytes.
- A complete authenticated autocommit DATA record is immediately visible after crash.

Transactional DATA:

- transaction ID is non-zero and equals the sequence of the first DATA record in that transaction.
- resulting logical length = `0`.
- Transactional DATA records are contiguous and invisible until the matching COMMIT.
- If one transaction contains multiple generations of the same block, the highest-sequence DATA generation wins when COMMIT activates the transaction.

### `2` COMMIT

A zero-DATA COMMIT has `related count = 0`: it authenticates a state change but commits no DATA records. `Zero-DATA` does not mean logical length is zero.

- Plaintext bytes = `0`.
- Transaction ID matches the pending transaction.
- Related count equals the exact number of contiguous pending DATA records.
- Resulting logical length becomes active atomically with all pending block generations.
- A zero-DATA COMMIT may keep or reduce current logical length, but must never increase it or re-expose bytes hidden by an earlier truncate; its transaction ID equals its own sequence.
- A crash before a valid COMMIT leaves previous committed block generations and logical length active.
- A zero-DATA truncate may end inside the final logical block; readers expose only the required prefix of that authenticated block generation.

### `3` CHECKPOINT

- Plaintext bytes = `0` and transaction ID = `0`.
- Resulting logical length equals current logical length.
- DCAM appends CHECKPOINT after a complete MP4 `moof` + `mdat` fragment and closes any partial crypto block at that boundary.
- CHECKPOINT is a live-reader boundary. It does not control visibility of earlier valid autocommit DATA.
- Closing a partial tail block before CHECKPOINT preserves that generation. Later DATA may replace the same tail block with the complete preserved prefix plus bytes from the next fragment; the earlier generation remains recoverable unless and until the replacement record authenticates.

### `4` FINAL

- Plaintext bytes = `0` and transaction ID = `0`.
- No transaction may be pending.
- Resulting logical length equals current logical length.
- Logical blocks from zero through the final block must have exact coverage for that length.
- FINAL must be the last record with no trailing bytes.
- Published segmented-GCM files require one valid FINAL. Interrupted DCAM staging files may omit FINAL and use recovery.

## Recovery state machine

1. Validate file header and derive key.
2. Scan physical records in strict sequence order.
3. Validate record structure, CRC, unique nonce, GCM tag, type-specific fields, and length bounds.
4. Apply autocommit DATA immediately.
5. Hold transactional DATA until matching COMMIT; ignore only an incomplete final transaction during DCAM interrupted recovery.
6. Track latest authenticated CHECKPOINT for live playback.
7. For published media, require authenticated FINAL and physical EOF immediately after it.
8. Reconstruct logical bytes from the latest committed generation of each block.
9. DCAM runs the existing fragmented-MP4 finalizer against this logical random-access view. BDMA writes the exact logical bytes in block order.

### Interrupted DCAM staging recovery

These allowances apply only when DCAM opens its own unpublished staging file after interruption. BDMA and every published-file reader remain fail-strict.

1. Scan append-only records from the file header in sequence.
2. At the first incomplete or invalid record, accept no later bytes and truncate the physical log to that record start. Earlier tag-valid autocommit DATA remains active.
3. If EOF follows tag-valid transactional DATA without its COMMIT, discard the transaction and truncate to its first DATA record.
4. Keep the latest authenticated CHECKPOINT as the live-play boundary.
5. Run the existing fragmented-MP4 interrupted finalizer against the recovered logical view; it alone trims any incomplete MP4 fragment.
6. Append fresh records with the next sequence and new nonces; never reuse bytes or nonces from the discarded tail.

## BDMA ExternalMediaDecrypt dispatch

```text
if primary or mirror DCAM-GCM magic is present:
    parse segmented-GCM contract
    require valid FINAL
    verify every referenced record
    decrypt exact logical media bytes
else:
    use legacy raw AES-256-CTR decoder
```

Once content detection classifies a file as segmented GCM, a wrong password, modified header, modified record metadata, modified ciphertext, missing interior record, duplicate nonce, invalid COMMIT, missing FINAL, or trailing bytes must fail decryption without CTR fallback.

## Interoperability proof

Both repositories carry the same deterministic encrypted fixture and plaintext fixture. DCAM regenerates the encrypted fixture from explicit salt, file ID, nonces, records, and password. BDMA `SegmentedAesGcmCryptoService` detects and decrypts that fixture. ExternalMediaDecrypt regression runs the fixture through content dispatch, rejects tampering without AES-CTR fallback, and separately verifies legacy AES-CTR.

Canonical vector:

- Password: `contract-pass`.
- Derived 32-byte key: `1925c3ce56e5eb7a668e82ec0b625ff348147827a4cf34c76a98b172484fa7e5`; independently reproduced with .NET PBKDF2-HMAC-SHA256.
- Encrypted fixture: 9,276 bytes; SHA-256 `9d53238c68e15fa1ba326edfbcfde43f14eec15febe6f04b7b112596c3d12883`.
- Plaintext fixture: 4,200 bytes; SHA-256 `ff6a7b63623fba846c3e2079c4e004b3c03dbbd382e67a4b1ce8a2f7181d7d5e`.
- DCAM paths: `app/src/test/resources/crypto/dcam-segmented-gcm-contract.bin` and `.plain`.
- BDMA paths: `docs/contracts/vectors/dcam-segmented-gcm-contract.bin` and `.plain`.