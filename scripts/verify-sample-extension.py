#!/usr/bin/env python3
"""Verifies the sample extension's declared endpoints against the live Internet
Archive API. Used by CI; requires full internet access.

Steps mirror the extension JSON: search -> detail -> episodes -> one video HEAD.
Prints one PASS line per check and exits non-zero on the first failure.
"""
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SAMPLE = ROOT / "sample-extensions" / "internet-archive.json"
USER_AGENT = "vibecast-ci/1.0 (sample extension verification)"


def fail(message: str) -> "None":
    print(f"FAIL: {message}")
    sys.exit(1)


def ok(message: str) -> None:
    print(f"PASS: {message}")


def fetch(url: str, timeout: int = 30, method: str = "GET"):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT}, method=method)
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.status, response.headers, response.read()


def render(template: str, query: str = "", page: str = "1", url: str = "") -> str:
    return template.replace("{query}", urllib.parse.quote_plus(query)).replace(
        "{page}", page
    ).replace("{url}", urllib.parse.quote(url, safe=""))


def main() -> None:
    extension = json.loads(SAMPLE.read_text(encoding="utf-8"))
    if extension.get("schemaVersion") != 1:
        fail(f"unexpected schemaVersion {extension.get('schemaVersion')}")
    ok("sample extension schemaVersion is 1")

    search_url = render(extension["search"]["request"]["url"], query="western", page="1")
    status, _, body = fetch(search_url)
    if status != 200:
        fail(f"search returned HTTP {status}")
    docs = json.loads(body).get("response", {}).get("docs", [])
    if not docs:
        fail("search returned zero docs")
    first = docs[0]
    if "title" not in first or "identifier" not in first:
        fail(f"search docs missing mapped fields: {sorted(first)[:6]}")
    ok(f"search found {len(docs)} docs, first identifier {first['identifier']!r}")

    # The item's url field is produced by the extension's own mapping template.
    url_template = extension["search"]["fields"]["url"].get("template", "")
    episodes_source = extension["episodes"]["source"]
    filter_field = episodes_source.get("filterField")
    filter_equals = episodes_source.get("filterEquals")
    filter_suffix = episodes_source.get("filterSuffix", "").lower()

    def file_matches(file_entry: dict) -> bool:
        if filter_field is None:
            return True
        value = str(file_entry.get(filter_field, ""))
        if filter_equals is not None and value != filter_equals:
            return False
        if filter_suffix and not value.lower().endswith(filter_suffix):
            return False
        return True

    metadata = None
    chosen = None
    for doc in docs[:10]:
        doc_url = url_template.replace("{value}", doc["identifier"])
        status, _, body = fetch(doc_url)
        if status != 200:
            continue
        candidate = json.loads(body)
        files = candidate.get("files", [])
        if candidate.get("metadata", {}).get("title") and any(file_matches(f) for f in files):
            metadata = candidate
            chosen = doc
            break
    if metadata is None:
        formats = sorted({str(f.get("format")) for doc in docs[:5] for f in json.loads(
            fetch(url_template.replace("{value}", doc["identifier"]))[2]).get("files", [])})
        names = sorted({str(f.get("name"))[-8:] for doc in docs[:3] for f in json.loads(
            fetch(url_template.replace("{value}", doc["identifier"]))[2]).get("files", [])})
        fail(f"no doc among the first 10 matches the episodes filter; observed formats={formats[:12]} name-endings={names[:12]}")
    title = metadata["metadata"]["title"]
    mpeg4 = [f for f in metadata["files"] if file_matches(f)]
    ok(f"detail title {title!r}; {len(mpeg4)} episodes match the filter")

    episode = mpeg4[0]
    video_url = f"https://archive.org/download/{chosen['identifier']}/{urllib.parse.quote(episode['name'])}"
    try:
        status, headers, _ = fetch(video_url, method="HEAD", timeout=45)
    except Exception as error:  # noqa: BLE001 - reported verbatim below
        fail(f"video HEAD request failed: {error}")
    if status not in (200, 302):
        fail(f"video HEAD returned HTTP {status}")
    content_type = headers.get("Content-Type", "")
    ok(f"video reachable: HTTP {status} content-type={content_type!r}")

    if extension["video"]["strategy"] != "direct":
        fail("sample video strategy changed away from 'direct'")
    ok("sample extension endpoints verified live")


if __name__ == "__main__":
    main()
