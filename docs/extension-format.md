# Extension format reference

An extension is a single JSON document. It contains no executable code — every step
is data that the shared engine in `core` interprets identically inside AddOnX (when
you test a step) and inside VibeCast (when you play). The schema lives in
`core/src/main/kotlin/…/core/model/Extension.kt`; this page is the human summary.

The runnable example used throughout is
[`sample-extensions/internet-archive.json`](../sample-extensions/internet-archive.json),
which streams public-domain films from the Internet Archive's official JSON APIs.
Its endpoints are exercised live in CI (`ci.yml` runs the sample-extension check) and
offline in unit tests through saved fixtures.

## Top-level shape

```json
{
  "schemaVersion": 1,
  "id": "org.archive.publicdomain",
  "name": "Internet Archive Public Domain",
  "version": 1,
  "lang": "en",
  "baseUrl": "https://archive.org",
  "headers": { "User-Agent": "…" },
  "search":   { "…": "…" },
  "latest":   { "…": "…" },
  "popular":  { "…": "…" },
  "detail":   { "…": "…" },
  "episodes": { "…": "…" },
  "video":    { "…": "…" }
}
```

- `schemaVersion` — `1` today. An importer refuses unknown versions with the
  supported range in the error.
- `id` — reverse-DNS-like string, stable per source. A lower `version` can never
  overwrite a higher installed one.
- `headers` — default headers merged into every step request (steps can add more).
- `latest` / `popular` are optional; everything else is required.

## List steps (`search`, `latest`, `popular`, `detail`, `episodes`)

Each list step has three parts: `request`, `source`, `fields`.

### `request`

```json
{ "url": "https://archive.org/advancedsearch.php?q={query}&rows=20&page={page}&output=json",
  "method": "GET",
  "headers": { "Referer": "https://archive.org/" } }
```

`{query}` is URL-escaped automatically, `{page}` is the 1-based page number for
infinite scrolling, and `{url}` passes the item URL through (used by `detail`,
`episodes` and `video`).

### `source`

Two kinds:

- HTML: `{ "type": "html", "selector": "div.item" }` — every element matching the
  Jsoup CSS selector becomes one item.
- JSON: `{ "type": "json", "path": "$.response.docs[*]" }` — a JSONPath expression;
  optional filters keep only matching elements.

Item filters (all optional, combinable, case-insensitive except `filterEquals`):

- `filterField` — the field to test: an item selector for HTML sources or a
  JSONPath (relative to the item) for JSON sources. Required when any filter
  criterion is present.
- `filterEquals` — keep items whose field equals this value exactly.
- `filterSuffix` — keep items whose field ends with this value.
- `filterContains` — keep items whose field contains this value.

```json
"source": {
  "type": "json",
  "path": "$.files[*]",
  "filterField": "name",
  "filterSuffix": ".mp4"
}
```

### `fields`

Maps extracted values onto item fields: `title`, `cover`, `url`, `synopsis` (detail),
`name` and (optional) `number` (episodes).

Per field:

- HTML: `selector` + optional `attribute` (defaults to text content).
- JSON: `path` (JSONPath relative to the item).
- `rootPath` — evaluate relative to a sub-document instead of the item.
- `regex` + `regexGroup` — post-process the extracted text.
- `template` — build the final value; `{value}` is the extracted text and `{value2}`
  a second extraction via the nested `value2` object.

```json
"url": {
  "path": "$.name",
  "template": "https://archive.org/download/{value2}/{value}",
  "value2": { "rootPath": "$.metadata.identifier" }
}
```

## Video step

```json
"video": {
  "request": { "url": "{url}" },
  "strategy": "direct",
  "timeoutMs": 15000,
  "quality": { "template": "Original" },
  "subtitles": []
}
```

Strategies, in order of reliability:

1. `direct` — the request URL is itself the media (mp4/m3u8/mpd); MIME is inferred
   from the URL when the server does not say.
2. `html` — fetch the page, extract the media URL with `selector` (+ optional
   `regex`).
3. `regex` — run `pattern` over the raw page/script text.
4. `iframe` — collect iframe/embed sources from the page.
5. `webviewSniff` — load the page in a hidden WebView with ad blocking enabled and
   capture the first media request (m3u8/mp4/mpd) together with its request headers.
   Requires a device; the desktop test panel degrades to a precise error.

The result is a stream URL plus MIME type, per-stream headers (Referer/User-Agent/
cookies, applied to that stream and its segments/keys only), an optional quality
label and optional subtitle tracks.

## Validation and errors

Import validates the whole document before anything is stored: unknown
`schemaVersion`, missing steps, empty selectors/paths, unknown strategy names and
size caps produce one message per issue with the offending step and field. During
execution a failing step reports which step failed and why (HTTP status, empty
result, bad selector, regex that matched nothing) instead of returning half-populated
items. Content safety: extensions are expected to point at sources they are allowed
to use; DRM-protected and Cloudflare-gated streams are detected and reported as
"unsupported" rather than bypassed.

### Dry-run

`ExtensionEngine.dryRun(extension)` checks structure without touching the network:
every request template must resolve with sample `{query}`/`{page}`/`{url}` values,
every CSS selector must parse, every regex must compile and every `filterField`
must carry a criterion. The engine returns one issue per defect, named by step and
field, so AddOnX can show a full fix-list before the first request is ever made.

### URL resolution

Relative URLs follow RFC 3986: root-relative (`/watch/1`), dot-segment
(`../a/./b`), query-only (`?page=2` keeps the base path) and protocol-relative
(`//cdn.host/v.mp4` inherits the base scheme) forms are all normalized the same
way in the engine, the codes and both apps.
