#!/usr/bin/env python3
"""Resolves an extension source (unix1 code, gc1 code or https URL) to base64 of the
extension JSON for the -PbundledExtensionJsonB64 Gradle property. Prints exactly one
line of base64 on success; exits non-zero with a message on stderr on failure."""
import base64
import json
import sys
import urllib.request
import zlib


def fail(message: str) -> "None":
    print(message, file=sys.stderr)
    sys.exit(1)


def fetch(url: str) -> str:
    try:
        with urllib.request.urlopen(url, timeout=60) as response:
            return response.read().decode("utf-8")
    except Exception as error:  # noqa: BLE001 - one message for any network failure
        fail(f"network error fetching {url}: {error}")


def main() -> None:
    if len(sys.argv) != 2:
        fail("usage: resolve-extension.py <unix1-code|gc1-code|https-url>")
    source = sys.argv[1].strip()

    if source.startswith("https://"):
        extension_json = fetch(source)
    elif source.startswith("gc1:"):
        rest = source[len("gc1:") :]
        ref = "HEAD"
        if "@" in rest:
            rest, ref = rest.rsplit("@", 1)
        parts = rest.split("/")
        if len(parts) < 3:
            fail("gc1 code must be owner/repo/path")
        owner, repo, path = parts[0], parts[1], "/".join(parts[2:])
        if ".." in path.split("/") or path.startswith("/"):
            fail("gc1 path traversal rejected")
        extension_json = fetch(f"https://raw.githubusercontent.com/{owner}/{repo}/{ref}/{path}")
    elif source.startswith("unix1:"):
        extension_json = None
    else:
        fail("source must be a unix1 code, gc1 code or https URL")

    if extension_json is None:
        body = source[len("unix1:") :]
        if "." not in body:
            fail("unix1 code is missing the CRC trailer")
        payload = body.rsplit(".", 1)[0]
        try:
            compressed = base64.urlsafe_b64decode(payload + "=" * ((4 - len(payload) % 4) % 4))
            extension_json = zlib.decompressobj(-15).decompress(compressed, 512 * 1024 + 1).decode("utf-8")
            if len(extension_json) > 512 * 1024:
                fail("decompressed payload exceeds 512 KB")
        except Exception as error:  # noqa: BLE001
            fail(f"invalid unix1 code: {error}")

    try:
        json.loads(extension_json)
    except json.JSONDecodeError as error:
        fail(f"payload is not valid JSON: {error}")

    print(base64.b64encode(extension_json.encode("utf-8")).decode())


if __name__ == "__main__":
    main()
