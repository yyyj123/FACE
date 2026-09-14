from __future__ import annotations

import json
import sys
import urllib.request
from pathlib import Path


tree_path = Path(sys.argv[1] if len(sys.argv) > 1 else r"E:\face\backend-next\target\m6-dependency-tree.json")
root = json.loads(tree_path.read_text(encoding="utf-8"))
dependencies: set[tuple[str, str]] = set()
stack = [root]
visited_nodes = 0
while stack:
    node = stack.pop()
    visited_nodes += 1
    group = node.get("groupId")
    artifact = node.get("artifactId")
    version = node.get("version")
    scope = node.get("scope")
    if group and artifact and version and scope != "test":
        dependencies.add((f"{group}:{artifact}", str(version)))
    stack.extend(node.get("children") or [])
    if visited_nodes > 100_000:
        raise RuntimeError("Maven dependency tree exceeded the safety limit.")

ordered = sorted(dependencies)
payload = {
    "queries": [
        {"package": {"ecosystem": "Maven", "name": name}, "version": version}
        for name, version in ordered
    ]
}
request = urllib.request.Request(
    "https://api.osv.dev/v1/querybatch",
    data=json.dumps(payload, separators=(",", ":")).encode("utf-8"),
    headers={"Content-Type": "application/json", "User-Agent": "face-m6-release-verifier/1.0"},
    method="POST",
)
with urllib.request.urlopen(request, timeout=120) as response:
    result = json.load(response)

vulnerable = []
for dependency, entry in zip(ordered, result.get("results", []), strict=True):
    vulns = entry.get("vulns") or []
    if vulns:
        vulnerable.append((dependency[0], dependency[1], ",".join(vuln.get("id", "UNKNOWN") for vuln in vulns)))

print(f"M6_MAVEN_OSV_DEPENDENCIES={len(ordered)}")
print(f"M6_MAVEN_OSV_VULNERABLE={len(vulnerable)}")
for name, version, identifiers in vulnerable:
    print(f"M6_MAVEN_OSV_FINDING={name}:{version}:{identifiers}")
if vulnerable:
    raise SystemExit(2)
print("M6_MAVEN_OSV=PASS")
