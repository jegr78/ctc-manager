#!/usr/bin/env python3
"""Run explicit Maven scopes and reject deferred or stale test failures."""
import argparse
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import signal
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def ambient_configuration(strict):
    """Never certify hidden selectors, skipped gates or alternate Maven goals."""
    safe_flags = {"-B", "--batch-mode", "-ntp", "--no-transfer-progress", "-o", "--offline",
                  "-nsu", "--no-snapshot-updates", "-C", "--strict-checksums", "-e", "--errors"}
    safe_properties = {"java.io.tmpdir", "file.encoding", "user.timezone", "style.color",
                       "javax.net.ssl.trustStore", "javax.net.ssl.trustStoreType",
                       "javax.net.ssl.trustStorePassword", "javax.net.ssl.keyStore",
                       "javax.net.ssl.keyStoreType", "javax.net.ssl.keyStorePassword"}
    def critical(name):
        value = name.lower()
        return (value in {"test", "it.test", "groups", "excludedgroups", "argline"}
                or any(part in value for part in ["skip", "includes", "excludes", "failure.ignore",
                                                   "failifno", "surefire", "failsafe", "jacoco", "spotbugs"])
                or value.endswith(".groups") or value.endswith(".excludedgroups")
                or value == "maven.ext.class.path")

    recorded = {}
    sources = {name: os.environ.get(name, "") for name in
               ["MAVEN_ARGS", "MAVEN_OPTS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"]}
    for relative in [".mvn/maven.config", ".mvn/jvm.config"]:
        path = ROOT / relative
        if path.exists():
            sources[relative] = path.read_text()
    for name, raw in sources.items():
        if name in {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"} and any(c in raw for c in "\r\n"):
            raise ValueError("Multiline ambient JVM options are unsupported in " + name)
        if name == ".mvn/maven.config":
            raw = "\n".join(line for line in raw.splitlines() if not line.lstrip().startswith("#"))
        if any(critical(key) for key in re.findall(r"-D([A-Za-z0-9_.-]+)", raw)):
            raise ValueError("Unsafe ambient Maven configuration in " + name)
        try:
            options = shlex.split(raw, comments=False)
        except ValueError:
            raise ValueError("Malformed ambient Maven configuration in " + name) from None
        accepted = []
        for option in options:
            if option.startswith("-D"):
                key = option[2:].split("=", 1)[0]
                if critical(key) or strict and key not in safe_properties:
                    raise ValueError("Unsafe ambient Maven configuration in " + name)
                accepted.append("property:" + key)
            elif (option in safe_flags or re.fullmatch(r"-Xm[sx]\d+[mMgG]", option)
                  or re.fullmatch(r"-XX:(MaxRAMPercentage=\d+(\.\d+)?|MaxMetaspaceSize=\d+[mMgG]|[+]UseG1GC|[+]UseSerialGC|[+]ExitOnOutOfMemoryError)", option)
                  or option in {"--sun-misc-unsafe-memory-access=allow", "--enable-native-access=ALL-UNNAMED"}):
                accepted.append(option.split("=", 1)[0] if "=" in option else option)
            else:
                raise ValueError("Unsafe ambient Maven configuration in " + name)
        recorded[name] = accepted
    settings = Path.home() / ".m2/settings.xml"
    if settings.exists():
        keys = []
        for element in ET.parse(settings).getroot().iter():
            if element.tag.rsplit("}", 1)[-1] == "properties":
                keys += [child.tag.rsplit("}", 1)[-1] for child in element]
        if any(critical(key) for key in keys):
            raise ValueError("Unsafe ambient Maven configuration in user settings")
        recorded["userSettingsPropertyNames"] = keys
    if os.environ.get("MAVEN_EXT_CLASS_PATH"):
        raise ValueError("Unreviewed ambient Maven extension configuration")
    return recorded


def stop_owned_group(proc):
    """Wait on the whole group, even after its Maven leader has exited."""
    previous = {kind: signal.signal(kind, signal.SIG_IGN) for kind in [signal.SIGINT, signal.SIGTERM]}
    try:
        try:
            os.killpg(proc.pid, signal.SIGTERM)
        except ProcessLookupError:
            return
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            proc.poll()
            try:
                os.killpg(proc.pid, 0)
            except ProcessLookupError:
                break
            time.sleep(0.05)
        else:
            try:
                os.killpg(proc.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        proc.wait(timeout=5)
    finally:
        for kind, handler in previous.items():
            signal.signal(kind, handler)


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def source_state():
    untracked = {}
    for raw in git("ls-files", "--others", "--exclude-standard", "-z").split(b"\0"):
        if raw:
            path = ROOT / os.fsdecode(raw)
            content = os.fsencode(os.readlink(path)) if path.is_symlink() else path.read_bytes()
            untracked[os.fsdecode(raw)] = hashlib.sha256(content).hexdigest()
    return dict(head=git("rev-parse", "HEAD").decode().strip(),
                tree=git("rev-parse", "HEAD^{tree}").decode().strip(),
                status=git("status", "--porcelain").decode(), untracked=untracked,
                diffSha256=hashlib.sha256(git("diff", "HEAD", "--binary")).hexdigest())


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["unit", "integration", "browser", "full"])
    parser.add_argument("selector", nargs="?")
    parser.add_argument("--unit", help="Required associated unit selector for integration/browser feedback")
    parser.add_argument("--integration", help="Associated integration selector in browser mode")
    parser.add_argument("--docker", action="store_true", help="Enable real MariaDB cases")
    parser.add_argument("--offline", action="store_true", help="Require a warm dependency cache")
    parser.add_argument("--maven-repo", type=Path)
    parser.add_argument("--test-heap-mib", type=int, default=1024, help="Requested test/compiler heap, default 1024 MiB")
    parser.add_argument("--maven-heap-mib", type=int, default=512, help="Maven heap, default 512 MiB")
    args = parser.parse_args()
    if not 256 <= args.test_heap_mib <= 4096 or not 256 <= args.maven_heap_mib <= 2048:
        parser.error("test heap must be 256..4096 MiB and Maven heap 256..2048 MiB")
    if args.mode == "full" and (args.selector or args.unit or args.integration):
        parser.error("full mode does not accept focused selectors")
    if args.mode != "full" and not args.selector:
        parser.error("a focused selector is required")
    if args.mode in {"integration", "browser"} and not args.unit:
        parser.error("focused integration/browser checks require --unit with associated unit tests")
    if args.mode != "browser" and args.integration:
        parser.error("--integration belongs to browser mode")
    if args.mode == "unit" and args.unit:
        parser.error("unit mode uses its positional selector")
    for value in [args.selector, args.unit, args.integration]:
        if value and (value.startswith("-") or "!" in value or any(ord(c) < 32 for c in value)):
            parser.error("selectors must be positive patterns without control characters")
    return args


def command_for(args):
    command = ["./mvnw"]
    if args.mode == "full":
        command += ["clean", "verify", "-Pe2e", "-Ddocker.available=true"]
    elif args.mode == "unit":
        command += ["test", "-Dtest=" + args.selector]
    else:
        command += ["integration-test"]
        if args.mode == "browser":
            command += ["-Pe2e"]
        command += ["-Dit.test=" + ",".join(s for s in [args.integration, args.selector] if s)]
        if args.unit:
            command += ["-Dtest=" + args.unit]
    if args.docker and args.mode != "full":
        command += ["-Ddocker.available=true"]
    if args.mode != "full":
        command += ["-Dctc.test.forks=1"]
    command += ["--no-transfer-progress"]
    if args.offline:
        command += ["--offline"]
    if args.maven_repo:
        command += ["-Dmaven.repo.local=" + str(args.maven_repo.resolve())]
    return command


def reports():
    totals = {group: dict(tests=0, failures=0, errors=0, skipped=0)
              for group in ["unit", "integration", "browser"]}
    suites = []
    for folder in ["surefire-reports", "failsafe-reports"]:
        for path in sorted((ROOT / "target" / folder).glob("TEST-*.xml")):
            suite = ET.parse(path).getroot()
            if suite.tag != "testsuite":
                raise ValueError("Unexpected test-report format: " + path.name)
            name = suite.attrib["name"]
            group = "unit" if folder == "surefire-reports" else (
                "browser" if name.startswith("org.ctc.e2e.") else "integration")
            counts = {key: int(suite.attrib[key]) for key in totals[group]}
            if any(value < 0 for value in counts.values()):
                raise ValueError("Negative report counter: " + path.name)
            cases = suite.findall("testcase")
            if not cases or counts["tests"] > len(cases):
                raise ValueError("Missing test cases: " + path.name)
            counts["tests"] = len(cases)
            for counter, element in [("failures", "failure"), ("errors", "error"), ("skipped", "skipped")]:
                counts[counter] = max(counts[counter], sum(case.find(element) is not None for case in cases))
            for key, value in counts.items():
                totals[group][key] += value
            suites.append(dict(name=name, group=group, **counts))
    for path in (ROOT / "target/failsafe-reports").glob("failsafe-summary*.xml"):
        summary = ET.parse(path).getroot()
        if (any(int(summary.findtext(key, "0")) != 0 for key in ["failures", "errors", "skipped"])
                or summary.get("result") not in {None, "null", "0"}
                or summary.get("timeout", "false") != "false"
                or (summary.findtext("failureMessage") or "").strip()):
            raise ValueError("Failsafe reports deferred failures or an incomplete fork: " + path.name)
    return totals, suites


def full_gates():
    coverage = ET.parse(ROOT / "target/site/jacoco/jacoco.xml").getroot()
    line = next(counter for counter in coverage.findall("counter") if counter.get("type") == "LINE")
    covered, missed = int(line.attrib["covered"]), int(line.attrib["missed"])
    if covered < 0 or missed < 0 or covered + missed == 0 or covered / (covered + missed) < 0.82:
        raise ValueError("Complete coverage must meet the existing 82% minimum")
    path = ROOT / "target/spotbugsXml.xml"
    bugs = ET.parse(path).getroot()
    errors = bugs.find("Errors")
    if (bugs.findall("BugInstance") or errors is None or int(errors.get("errors", "0"))
            or int(errors.get("missingClasses", "0")) or "findsecbugs" not in path.read_text().lower()):
        raise ValueError("Complete SpotBugs/FindSecBugs evidence is missing or failed")
    return dict(lineCoveragePercent=round(100 * covered / (covered + missed), 2), spotbugsBugs=0)


def run(args, output):
    ambient = ambient_configuration(args.mode == "full")
    before = source_state()
    if args.mode == "full" and before["status"]:
        raise ValueError("Commit a clean reviewed source before full verification")
    command = command_for(args)
    record = dict(mode=args.mode, evidenceKind="canonical" if args.mode == "full" else "focused",
                  ambientConfiguration=ambient,
                  launcherPolicy=dict(additionalRcFiles=False, projectBasedir=str(ROOT)),
                  sourceBefore=before, command=command,
                  requestedJvmBudget=dict(testHeapMiB=args.test_heap_mib, mavenHeapMiB=args.maven_heap_mib),
                  startedUtc=datetime.datetime.now(datetime.timezone.utc).isoformat())
    output.mkdir(parents=True)
    metadata = output / "run.json"
    proc = None
    try:
        prior_artifacts = ["surefire-reports", "failsafe-reports", "test-perf"]
        if args.mode == "full":
            prior_artifacts += ["site/jacoco", "spotbugsXml.xml"]
        for name in prior_artifacts:
            existing = ROOT / "target" / name
            if existing.is_symlink():
                raise ValueError("Refusing a symlink report directory: " + name)
            if existing.exists():
                archived = output / "previous-reports" / name
                archived.parent.mkdir(parents=True, exist_ok=True)
                shutil.move(str(existing), archived)
        print("Scope: " + args.mode + "; source " + before["head"], flush=True)
        print(shlex.join(command), flush=True)
        with (output / "maven.log").open("w") as log:
            environment = dict(os.environ)
            environment["MAVEN_SKIP_RC"] = "true"
            environment["MAVEN_BASEDIR"] = str(ROOT)
            environment["JAVA_TOOL_OPTIONS"] = (environment.get("JAVA_TOOL_OPTIONS", "")
                                                + " -Xmx" + str(args.test_heap_mib) + "m").strip()
            environment["MAVEN_OPTS"] = (environment.get("MAVEN_OPTS", "")
                                         + " -Xmx" + str(args.maven_heap_mib) + "m").strip()
            proc = subprocess.Popen(command, cwd=ROOT, env=environment, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, text=True, start_new_session=True)
            record["mavenPid"] = proc.pid
            metadata.write_text(json.dumps(record, indent=2) + "\n")
            for line in proc.stdout:
                echoed = re.search(r"(?:NOTE: )?Picked up (JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS):", line)
                if echoed:
                    line = line[:echoed.start()] + echoed.group(1) + " configured (values omitted)\n"
                log.write(line)
                print(line, end="", flush=True)
            record["mavenExitCode"] = proc.wait()
        totals, suites = reports()
        record.update(totals=totals, suites=suites, sourceAfter=source_state())
        required = {"unit"}
        if args.mode in {"integration", "full"} or args.integration:
            required.add("integration")
        if args.mode in {"browser", "full"}:
            required.add("browser")
        failures = [name for name, count in totals.items()
                    if any(count[key] for key in ["failures", "errors", "skipped"])]
        missing = [name for name in required if totals[name]["tests"] == 0]
        if record["mavenExitCode"] or failures or missing or record["sourceAfter"] != before:
            raise ValueError("Verification failed: Maven status, failed/skipped or missing cases, or changed source")
        if args.mode == "full":
            record["gates"] = full_gates()
        record["passed"] = True
        print(json.dumps(dict(scope=args.mode, totals=totals, passed=True)), flush=True)
        if args.mode != "full":
            print("Focused evidence only. Finalization requires complete reviewed exact-head CI.")
        else:
            print("Canonical local evidence complete. Exact-head reviewed CI is still required before merge.")
        return 0
    except BaseException as error:
        if proc is not None:
            stop_owned_group(proc)
        message = "Interrupted" if isinstance(error, KeyboardInterrupt) else str(error)
        record.update(passed=False, error=message)
        print("FAILED: " + message, file=sys.stderr)
        return 130 if isinstance(error, KeyboardInterrupt) else 1
    finally:
        record["endedUtc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        metadata.write_text(json.dumps(record, indent=2) + "\n")


def main():
    args = arguments()
    lock = Path(os.environ.get("CTC_FEEDBACK_LOCK_PATH", str(Path.home() / ".cache/ctc-manager/test-feedback.lock")))
    lock.parent.mkdir(parents=True, exist_ok=True)
    with lock.open("a") as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            print("Another feedback run owns this host lock; wait for its result.", file=sys.stderr)
            return 2
        stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S.%fZ")
        output = ROOT / ".test-feedback" / (stamp + "-" + str(os.getpid()))
        try:
            return run(args, output)
        except Exception as error:
            print("FAILED: " + str(error), file=sys.stderr)
            return 1


def interrupted(_signum, _frame):
    raise KeyboardInterrupt


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, interrupted)
    raise SystemExit(main())
