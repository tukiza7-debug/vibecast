# Extension codes

Extension codes are compact strings that identify one extension (or a repository of
extensions) so it can be shared as plain text: in a chat message, a file, a QR code or
a URL parameter. Both apps understand the same three formats; the encoder/decoder lives
once in `core` and is shared by AddOnX and VibeCast.

| Prefix   | Carries                        | Needs network | Best for                         |
|----------|--------------------------------|---------------|----------------------------------|
| `unix1:` | the full extension JSON        | no            | offline sharing, single file     |
| `gc1:`   | pointer to one file in a repo  | yes           | publishing one extension         |
| `gx1:`   | pointer to a repository index  | yes           | collections that update together |

## `unix1:` — self-contained code

```
unix1:<payload>.<crc32>
```

- `<payload>`: the extension JSON, compressed with raw DEFLATE (no zlib header,
  best compression) and then Base64url-encoded **without padding**.
- `<crc32>`: eight lowercase hex digits — the CRC32 of the payload *string* (the
  base64url text, ASCII), used as an integrity trailer.

Encoding order matters: JSON → deflate(raw) → base64url(no padding) → append `.` +
CRC32 of the base64url string.

Limits enforced before anything is stored:

- payload text longer than 700,000 characters is rejected before decompression;
- the decompressed JSON must not exceed **512 KB** (checked incrementally while
  inflating, so a decompression bomb cannot allocate first and fail later);
- a CRC32 mismatch produces "CRC32 mismatch: the code is corrupted", never a partial
  import.

Example (abbreviated payload):

```
unix1:H4sIAAAAAAAA_5yVbW_bMBCG34VfwZVJJ7ZJ2iStE3fS_mvQoEtKVFVJKqkQR2nM7o4gy...9f3a1c2b
```

Round-trip is stable: `decode(encode(json)) == json`. Decoding validates the CRC,
inflates the payload and then runs the same schema validation as a file import, so a
code can never smuggle in a shape that a JSON file could not.

## `gc1:` — single file in a Git repository

```
gc1:<owner>/<repo>/<path>[@<ref>]
```

- Fetched from `https://raw.githubusercontent.com/<owner>/<repo>/<ref>/<path>`
  (plain `HEAD` when `@<ref>` is absent).
- `<ref>` is pinned when present: the same code always resolves to the same content,
  and background update checks only follow new refs when the publisher ships a new
  code.
- Validation: owner matches GitHub's username rules, repo/path segments are
  non-empty, `..` segments and absolute paths are rejected, backslashes are rejected.

Example:

```
gc1:example/catalog/extensions/example-movies.json@v1.2.0
```

## `gx1:` — repository with an index

```
gx1:<owner>/<repo>[@<ref>]
```

Fetched and parsed from `<repo-root>/index.json`:

```json
{
  "schemaVersion": 1,
  "name": "Example catalog",
  "extensions": [
    { "file": "extensions/example-movies.json", "sha256": "…" },
    { "file": "extensions/example-tv.json" }
  ]
}
```

- `schemaVersion` must be `1`.
- `file` entries are relative to the repository root; absolute paths and `..` are
  rejected. `sha256` is optional; when present the downloaded file is verified.
- The app can add all entries at once or let you pick; "follow this repository"
  auto-adds future entries as the index grows.

Example:

```
gx1:example/catalog@main
```

## Security model

- Codes never carry executable code. Both code formats end at the same declarative
  schema validator as a JSON file import.
- Non-signature import paths (codes, files, clipboard, deep links, shared text) all
  surface a confirmation sheet: extension name, id, version, the domains it will
  contact and where the code came from. Nothing is stored before you confirm.
- The direct AddOnX-to-VibeCast path (signature-protected ContentProvider) skips the
  sheet only because both apps are signed with the same release key; it shows a
  snackbar with Undo instead.
- Updates never downgrade: an import with the same id but a lower version than the
  installed one is refused with an explanation instead of silently overwriting.
- Every network fetch has an explicit timeout and a typed failure reason; failures
  surface as error states, never as crashes.

## Test coverage

`core/src/test/…/codes/` covers: round-trips for all three types, corrupted CRC,
truncated payloads, decompression bombs, unknown prefixes, invalid schema payloads,
path traversal, malformed `index.json`, duplicate id/version downgrades and ref
pinning. The same fixtures back the app-level import tests.
