# Bundled filter list licenses and attribution

VibeCast's ad blocking ships with a snapshot of two community-maintained filter lists.
The snapshot is stored in `adblock/src/main/assets/filters/` and is refreshed at most
once per day on device through WorkManager, with validation before the bundled copy is
replaced.

## EasyList

- Source: <https://easylist.to/>
- License: GNU General Public License, version 3 (GPL-3.0) — see
  <https://easylist.to/about.html> and <https://www.gnu.org/licenses/gpl-3.0.txt>
- Copyright: The EasyList authors

VibeCast parses a subset of the EasyList/ABP syntax (`||domain^` anchors, wildcard
patterns, separator carets, `$third-party`, `$script`, `$image`, `$media`, `$popup`
resource options and `@@` exceptions). Rules outside that subset are skipped during
parsing; they are never partially applied.

## EasyPrivacy

- Source: <https://easylist.to/>
- License: GNU General Public License, version 3 (GPL-3.0) — see
  <https://easylist.to/about.html> and <https://www.gnu.org/licenses/gpl-3.0.txt>
- Copyright: The EasyPrivacy authors

## Relationship to this project

The filter lists are third-party data, not code. VibeCast does not redistribute or
modify the EasyList project itself; it consumes published list files, indexes the
subset it can interpret, and credits the upstream projects here and in the app's
Settings screen. If you redistribute APKs built from this source, keep this
attribution file and the notices in `README.md` intact, in line with GPL-3.0 for the
list data.

## Custom rules

Users can add their own rules in Settings. Custom rules follow the same ABP subset
syntax and are stored locally on the device only.
