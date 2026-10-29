"""Reproduce review findings against an isolated, disposable H2 application.

Run after mvn -B verify: python scripts/review-probes.py
These are review probes: observed=true confirms a problem, not acceptance.
"""
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/evidence/2026-10-29"
BASE = "http://127.0.0.1:18089/api/v1"
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(method, path, body=None, key="probe", actor="REVIEW"):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method, headers={
        "Content-Type": "application/json", "X-Operator-Id": actor,
        "Idempotency-Key": key})
    try:
        with OPENER.open(req, timeout=10) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def create(source):
    code, result = call("POST", "/incidents", {
        "channel": "APP", "sourceId": source, "priority": "HIGH", "type": "TEST"
    }, source)
    assert code == 201, (code, result)
    return result["incidentId"]


def verify(incident, key):
    return call("PATCH", "/incidents/" + incident, {
        "expectedVersion": 0, "action": "VERIFY", "reason": "review",
        "verifiedAddress": "synthetic address"}, key)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    findings = []
    log_path = OUT / "review-probe-app.log"
    with log_path.open("w", encoding="utf-8") as log:
        proc = subprocess.Popen([
            "java", "-jar", str(ROOT / "alarm-server/target/alarm-server-0.1.0-SNAPSHOT.jar"),
            "--server.port=18089", "--server.address=127.0.0.1",
            "--spring.datasource.url=jdbc:h2:mem:review_probe;DB_CLOSE_DELAY=-1;MODE=LEGACY"
        ], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        try:
            for _ in range(100):
                if proc.poll() is not None:
                    raise RuntimeError("Application exited; inspect probe log")
                try:
                    call("GET", "/incidents/nonexistent")
                    break
                except (OSError, TimeoutError):
                    time.sleep(0.2)
            else:
                raise RuntimeError("Application startup timed out")
            a, b = create("review-a"), create("review-b")
            assert verify(a, "shared-verify")[0] == 200
            status, replay = verify(b, "shared-verify")
            _, actual_b = call("GET", "/incidents/" + b)
            findings.append({"id": "R02", "observed": status == 200 and
                replay.get("incidentId") == a and actual_b["status"] == "PENDING_VERIFICATION",
                "response": replay, "secondIncidentStatus": actual_b["status"]})
            unit = {"unitId": "REVIEW-U", "name": "synthetic unit", "availability": 20,
                "capabilities": ["PATROL"], "location": {"x": 116.39, "y": 39.9,
                "source": "GPS", "observedAt": "2026-10-29T15:00:00Z"}}
            assert call("POST", "/units", unit)[0] == 200
            code, dispatch = call("POST", "/incidents/" + a + "/dispatches",
                {"expectedVersion": 1, "unitIds": ["REVIEW-U"]}, "dispatch-a")
            assert code == 201, dispatch
            task = dispatch["dispatches"][0]
            # A telemetry upsert restores capacity while an active reservation exists.
            assert call("POST", "/units", unit)[0] == 200
            assert verify(b, "verify-b")[0] == 200
            code, second = call("POST", "/incidents/" + b + "/dispatches",
                {"expectedVersion": 1, "unitIds": ["REVIEW-U"]}, "dispatch-b")
            findings.append({"id": "R04", "observed": code == 201,
                "secondDispatchStatus": code, "secondDispatch": second})
            code, receipt = call("POST", "/dispatches/" + task["dispatchId"] + "/receipts",
                {"commandId": task["commandId"], "targetStatus": "TIMEOUT", "expectedVersion": 0})
            findings.append({"id": "R05", "observed": code == 200,
                "receiptStatus": code, "receipt": receipt})
            assert call("POST", "/ops/outbox/run", {})[0] == 200
            time.sleep(0.2)
            lines = log_path.read_text(encoding="utf-8", errors="replace").splitlines()
            changed = [line for line in lines if "eventType=DispatchStatusChanged" in line
                       and task["dispatchId"] in line]
            findings.append({"id": "R03", "observed": bool(changed) and
                any('"aggregateVersion":0' in line for line in changed),
                "databaseVersionReturned": receipt.get("version"), "eventLog": changed})
            (OUT / "review-probes.json").write_text(json.dumps({
                "baseline": "ead3ff52f70155e8a649785f0f20b3bcd753f1f2",
                "note": "observed=true confirms a review defect; synthetic data only",
                "findings": findings}, ensure_ascii=False, indent=2), encoding="utf-8")
            print(json.dumps(findings, ensure_ascii=False, indent=2))
        finally:
            proc.terminate()
            try:
                proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=5)


if __name__ == "__main__":
    main()
