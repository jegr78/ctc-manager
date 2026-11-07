import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "test-feedback.py"
MAVEN = r'''#!/usr/bin/env python3
import json,os,sys,time,re
from pathlib import Path
args=sys.argv[1:];mode=os.environ.get("CTC_FEEDBACK_FIXTURE","pass")
print("Picked up JAVA_TOOL_OPTIONS: "+os.environ.get("JAVA_TOOL_OPTIONS",""),flush=True)
Path("target").mkdir(exist_ok=True)
Path("target/command.json").write_text(json.dumps(args))
def heap(name):
 values=re.findall(r"-Xmx(\d+)m",os.environ.get(name,""))
 return int(values[-1]) if values else None
Path("target/budgets.json").write_text(json.dumps({"testHeapMiB":heap("JAVA_TOOL_OPTIONS"),"mavenHeapMiB":heap("MAVEN_OPTS")}))
Path("target/launcher.json").write_text(json.dumps({"skipRc":os.environ.get("MAVEN_SKIP_RC"),"basedir":os.environ.get("MAVEN_BASEDIR")}))
if mode=="changed":Path("fixture.txt").write_text("changed during Maven")
if mode=="untracked-change":Path("new-fixture.py").write_text("changed untracked source")
if mode=="stubborn":
 import subprocess,signal
 child=subprocess.Popen([sys.executable,"-c","import signal,time;from pathlib import Path;signal.signal(signal.SIGTERM,signal.SIG_IGN);Path('target/stubborn-ready').write_text('ready');time.sleep(120)"])
 Path("target/stubborn-pid").write_text(str(child.pid))
 while not Path("target/stubborn-ready").exists():time.sleep(0.01)
 Path("target/ready").write_text("ready")
 while True:time.sleep(1)
if mode=="wait":
 Path("target/ready").write_text("ready")
 while True:time.sleep(1)
full="verify" in args;browser="-Pe2e" in args
rows=[("surefire-reports","org.ctc.ExampleTest",0)]
if "integration-test" in args or full:
 if mode!="missing-it" and (not browser or full or mode=="browser-mask" or any("ExampleIT" in arg for arg in args)):
  rows.append(("failsafe-reports","org.ctc.ExampleIT",int(mode in {"deferred","browser-mask"})))
 if browser and mode!="missing-browser":rows.append(("failsafe-reports","org.ctc.e2e.ExampleE2ETest",0))
for folder,name,failures in rows if mode!="none" else []:
 p=Path("target")/folder;p.mkdir(exist_ok=True)
 payload='<testcase name="example" classname="'+name+'">'+('<failure/>' if failures or mode=="hidden-case-failure" else '')+('<error/>' if mode=="hidden-case-error" else '')+('<skipped/>' if mode in {"skip","hidden-case-skip"} else '')+'</testcase>'
 if mode=="nested-counters":payload='<testcase name="sameMethod" classname="Nested$H2"/><testcase name="sameMethod" classname="Nested$MariaDb"/>'
 if mode=="empty-cases":payload=''
 raw='<testsuite name="'+name+'" tests="'+('2' if mode=="overstated-cases" else '1')+'" failures="'+str(failures)+'" errors="0" skipped="'+str(int(mode=="skip"))+'">'+payload+'</testsuite>'
 p.joinpath("TEST-"+name+".xml").write_text('<broken' if mode=="malformed" else raw)
if mode=="summary":
 p=Path("target/failsafe-reports");p.mkdir(exist_ok=True)
 p.joinpath("failsafe-summary.xml").write_text('<failsafe-summary><failures>1</failures><errors>0</errors></failsafe-summary>')
if mode in {"summary-crash","summary-timeout"}:
 p=Path("target/failsafe-reports");p.mkdir(exist_ok=True)
 p.joinpath("failsafe-summary.xml").write_text('<failsafe-summary result="'+('254' if mode=="summary-crash" else 'null')+'" timeout="'+('true' if mode=="summary-timeout" else 'false')+'"><failures>0</failures><errors>0</errors><failureMessage>'+('synthetic fork error' if mode=="summary-crash" else '')+'</failureMessage></failsafe-summary>')
if full and mode!="missing-gates":
 p=Path("target/site/jacoco");p.mkdir(parents=True,exist_ok=True)
 p.joinpath("jacoco.xml").write_text('<report><counter type="LINE" covered="81" missed="19"/></report>' if mode=="low-coverage" else '<report><counter type="LINE" covered="90" missed="10"/></report>')
 Path("target/spotbugsXml.xml").write_text('<BugCollection><Plugin id="findsecbugs"/><Errors errors="0" missingClasses="0"/>'+('<BugInstance/>' if mode=="spotbugs" else '')+'</BugCollection>')
 if mode=="missing-plugin":Path("target/spotbugsXml.xml").write_text('<BugCollection><Errors errors="0" missingClasses="0"/></BugCollection>')
 if mode=="missing-classes":Path("target/spotbugsXml.xml").write_text('<BugCollection><Plugin id="findsecbugs"/><Errors errors="0" missingClasses="1"/></BugCollection>')
 if mode=="analysis-errors":Path("target/spotbugsXml.xml").write_text('<BugCollection><Plugin id="findsecbugs"/><Errors errors="1" missingClasses="0"/></BugCollection>')
sys.exit(3 if mode=="exit" else 0)
'''

HARNESS = "import sys,runpy;from pathlib import Path;from unittest.mock import patch;home=Path(sys.argv.pop(1));script=sys.argv[1];sys.argv=sys.argv[1:];\nwith patch('pathlib.Path.home',return_value=home):runpy.run_path(script,run_name='__main__')"

class FeedbackWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.directory=tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root=Path(self.directory.name)
        (self.root/"scripts").mkdir()
        shutil.copy2(SCRIPT,self.root/"scripts/test-feedback.py")
        (self.root/"mvnw").write_text(MAVEN);(self.root/"mvnw").chmod(0o755)
        (self.root/".gitignore").write_text("target/\n.test-feedback/\nuser-home/\n")
        (self.root/"fixture.txt").write_text("original")
        subprocess.run(["git","init","-q"],cwd=self.root,check=True)
        subprocess.run(["git","add","."],cwd=self.root,check=True)
        subprocess.run(["git","-c","user.name=Fixture","-c","user.email=fixture@example.invalid","-c","commit.gpgsign=false","-c","core.hooksPath=/dev/null","commit","-qm","fixture"],cwd=self.root,check=True)

    def fixture_env(self,fixture):
        environment=dict(os.environ,CTC_FEEDBACK_FIXTURE=fixture,
                         CTC_FEEDBACK_LOCK_PATH=str(self.root/".test-feedback/host.lock"))
        for name in ["MAVEN_ARGS","MAVEN_OPTS","JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","MAVEN_EXT_CLASS_PATH"]:
            environment.pop(name,None)
        return environment

    def invocation(self,*arguments):
        return [os.sys.executable,"-c",HARNESS,str(self.root/"user-home"),"scripts/test-feedback.py",*arguments]

    def run_cli(self,*arguments,fixture="pass",extra_env=None):
        environment=self.fixture_env(fixture)
        environment.update(extra_env or {})
        return subprocess.run(self.invocation(*arguments),cwd=self.root,
                              env=environment,text=True,capture_output=True)

    def record(self):
        paths=list((self.root/".test-feedback").glob("*/run.json"))
        self.assertEqual(len(paths),1)
        return json.loads(paths[0].read_text())

    def test_nested_same_named_cases_are_counted_from_actual_elements(self):
        result=self.run_cli("unit","ExampleTest",fixture="nested-counters")
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertEqual(self.record()["totals"]["unit"]["tests"],2)

    def test_case_outcomes_and_missing_cases_cannot_hide_behind_successful_suite_headers(self):
        for fixture in ["hidden-case-failure","hidden-case-error","hidden-case-skip","empty-cases","overstated-cases"]:
            with self.subTest(fixture=fixture):
                result=self.run_cli("unit","ExampleTest",fixture=fixture)
                self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)

    def test_deferred_integration_failure_must_fail_even_when_maven_exits_zero(self):
        result=self.run_cli("integration","ExampleIT","--unit","ExampleTest",fixture="deferred")
        self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
        record=self.record()
        self.assertEqual(record["mavenExitCode"],0)
        self.assertEqual(record["totals"]["integration"]["failures"],1)
        self.assertFalse(record["passed"])

    def test_later_browser_success_cannot_mask_earlier_integration_failure(self):
        result=self.run_cli("browser","ExampleE2ETest","--unit","ExampleTest",fixture="browser-mask")
        self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
        record=self.record()
        self.assertEqual(record["mavenExitCode"],0)
        self.assertEqual(record["totals"]["integration"]["failures"],1)
        self.assertEqual(record["totals"]["browser"]["tests"],1)
        self.assertFalse(record["passed"])

    def test_nonzero_maven_status_cannot_pass_with_green_reports(self):
        result=self.run_cli("unit","ExampleTest",fixture="exit")
        self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertEqual(self.record()["mavenExitCode"],3)
        self.assertFalse(self.record()["passed"])

    def test_empty_skipped_malformed_and_missing_stage_reports_cannot_pass(self):
        for fixture in ["none","skip","malformed","missing-it","summary","summary-crash","summary-timeout"]:
            with self.subTest(fixture=fixture):
                result=self.run_cli("integration","ExampleIT","--unit","ExampleTest",fixture=fixture)
                self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
        records=[json.loads(path.read_text()) for path in (self.root/".test-feedback").glob("*/run.json")]
        self.assertEqual(len(records),7)
        self.assertTrue(all(not item["passed"] and item["mavenExitCode"]==0 for item in records))

    def test_old_report_is_archived_and_cannot_change_a_fresh_unit_result(self):
        old=self.root/"target/failsafe-reports/TEST-old.xml";old.parent.mkdir(parents=True)
        old.write_text('<testsuite name="old" tests="1" failures="1" errors="0" skipped="0"/>')
        result=self.run_cli("unit","ExampleTest")
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        record=self.record();self.assertEqual(record["totals"]["integration"]["tests"],0)
        archived=list((self.root/".test-feedback").glob("*/previous-reports/failsafe-reports/TEST-old.xml"))
        self.assertEqual(len(archived),1)
        self.assertFalse(old.exists())
        self.assertEqual(record["evidenceKind"],"focused")
        self.assertIn("Focused evidence only",result.stdout)

    def test_full_mode_preserves_all_phases_and_security_gates(self):
        result=self.run_cli("full")
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        command=json.loads((self.root/"target/command.json").read_text())
        self.assertEqual(command,["clean","verify","-Pe2e","-Ddocker.available=true","--no-transfer-progress"])
        record=self.record();self.assertEqual(record["evidenceKind"],"canonical")
        self.assertTrue(all(count["tests"]>0 for count in record["totals"].values()))
        self.assertEqual(record["gates"],{"lineCoveragePercent":90.0,"spotbugsBugs":0})

    def test_full_mode_rejects_focused_filters_dirty_source_and_missing_gates(self):
        for arguments in [("full","ExampleTest"),("full","--unit","ExampleTest"),("full","-DskipTests")]:
            result=self.run_cli(*arguments)
            self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
            self.assertFalse((self.root/"target/command.json").exists())
        result=self.run_cli("full",fixture="missing-gates")
        self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertFalse(self.record()["passed"])
        (self.root/"fixture.txt").write_text("not committed")
        result=self.run_cli("full")
        self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn("clean reviewed source",result.stderr)

    def test_literal_selector_cannot_execute_shell_code(self):
        selector="ExampleIT;touch injected"
        result=self.run_cli("integration",selector,"--unit","ExampleTest")
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        command=json.loads((self.root/"target/command.json").read_text())
        self.assertIn("-Dit.test="+selector,command)
        self.assertFalse((self.root/"injected").exists())

    def test_changed_tracked_and_untracked_sources_invalidate_evidence(self):
        result=self.run_cli("unit","ExampleTest",fixture="changed")
        self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertFalse(self.record()["passed"])
        (self.root/"new-fixture.py").write_text("before run")
        result=self.run_cli("unit","ExampleTest",fixture="untracked-change")
        self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
        records=[json.loads(path.read_text()) for path in (self.root/".test-feedback").glob("*/run.json")]
        self.assertEqual(len(records),2)
        self.assertTrue(all(not item["passed"] for item in records))

    def test_concurrent_cooperating_run_fails_without_starting_maven(self):
        import fcntl
        lock=self.root/".test-feedback/host.lock";lock.parent.mkdir()
        with lock.open("a") as stream:
            fcntl.flock(stream,fcntl.LOCK_EX|fcntl.LOCK_NB)
            result=self.run_cli("unit","ExampleTest")
        self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn("host lock",result.stderr)
        self.assertFalse((self.root/"target/command.json").exists())

    def test_full_mode_rejects_coverage_and_static_analysis_failures(self):
        for fixture in ["low-coverage","spotbugs","missing-plugin","missing-classes","analysis-errors","missing-browser"]:
            with self.subTest(fixture=fixture):
                result=self.run_cli("full",fixture=fixture)
                self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
        records=[json.loads(path.read_text()) for path in (self.root/".test-feedback").glob("*/run.json")]
        self.assertTrue(all(not item["passed"] and item["mavenExitCode"]==0 for item in records))

    def test_stale_gates_cannot_make_an_unchecked_full_run_pass(self):
        result=self.run_cli("full")
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        result=self.run_cli("full",fixture="missing-gates")
        self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)

    def test_interrupt_stops_its_maven_child_and_cannot_leave_a_green_record(self):
        import signal
        import time
        environment=self.fixture_env("wait")
        process=subprocess.Popen(self.invocation("unit","ExampleTest"),
                                 cwd=self.root,env=environment,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        try:
            for _ in range(100):
                if (self.root/"target/ready").exists():break
                time.sleep(0.02)
            else:self.fail("Fixture Maven did not start")
            process.send_signal(signal.SIGTERM)
            stdout,stderr=process.communicate(timeout=10)
            self.assertEqual(process.returncode,130,stdout+stderr)
            record=self.record();self.assertFalse(record["passed"])
            with self.assertRaises(ProcessLookupError):os.kill(record["mavenPid"],0)
        finally:
            if process.poll() is None:process.kill();process.wait()

    def test_child_heaps_are_bounded_and_explicit_overrides_are_recorded(self):
        result=self.run_cli("unit","ExampleTest")
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertEqual(json.loads((self.root/"target/budgets.json").read_text()),
                         {"testHeapMiB":1024,"mavenHeapMiB":512})
        self.assertEqual(json.loads((self.root/"target/launcher.json").read_text()),
                         {"skipRc":"true","basedir":str(self.root)})
        result=self.run_cli("unit","ExampleTest","--test-heap-mib","768","--maven-heap-mib","384")
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertEqual(json.loads((self.root/"target/budgets.json").read_text()),
                         {"testHeapMiB":768,"mavenHeapMiB":384})

    def test_ambient_filters_cannot_produce_canonical_success(self):
        for name,value in [("MAVEN_ARGS","-Dtest=*,!SomeRegressionTest"),
                           ("MAVEN_OPTS","-Dmaven.test.skip=true"),
                           ("JAVA_TOOL_OPTIONS","-Dgroups=unit"),
                           ("JDK_JAVA_OPTIONS","-Dspotbugs.skip=true"),
                           ("_JAVA_OPTIONS","-Dit.test=OnlyOneIT"),
                           ("MAVEN_ARGS","-Duser.timezone=UTC# -Dtest=*,!SomeRegressionTest"),
                           ("JAVA_TOOL_OPTIONS","-Duser.timezone=UTC# -Dgroups=unit"),
                           ("MAVEN_OPTS",r"-Duser.timezone=UTC\ -Dtest=*,!SomeRegressionTest")]:
            with self.subTest(variable=name):
                result=self.run_cli("full",extra_env={name:value})
                self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
                self.assertFalse((self.root/"target/command.json").exists())

    def test_interruption_kills_a_stubborn_descendant_after_its_leader_exits(self):
        import signal
        import time
        environment=self.fixture_env("stubborn")
        process=subprocess.Popen(self.invocation("unit","ExampleTest"),
                                 cwd=self.root,env=environment,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        child=None
        try:
            for _ in range(200):
                if (self.root/"target/ready").exists():break
                time.sleep(0.02)
            else:self.fail("Stubborn fixture did not start")
            child=int((self.root/"target/stubborn-pid").read_text())
            process.send_signal(signal.SIGTERM)
            stdout,stderr=process.communicate(timeout=10)
            self.assertEqual(process.returncode,130,stdout+stderr)
            try:
                os.kill(child,0)
            except ProcessLookupError:
                pass
            else:
                state=subprocess.check_output(["ps","-p",str(child),"-o","stat="],text=True).strip()
                self.assertTrue(state.startswith("Z"),"Owned descendant is still running: "+state)
            self.assertFalse(self.record()["passed"])
        finally:
            if process.poll() is None:process.kill();process.wait()
            if child:
                try:os.kill(child,signal.SIGKILL)
                except ProcessLookupError:pass

    def test_project_and_user_configuration_cannot_hide_test_filters(self):
        config=self.root/".mvn/maven.config";config.parent.mkdir()
        config.write_text("-Dtest=*,!SomeRegressionTest")
        result=self.run_cli("full")
        self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn("ambient Maven",result.stderr)
        self.assertFalse((self.root/"target/command.json").exists())
        config.unlink()
        settings=self.root/"user-home/.m2/settings.xml";settings.parent.mkdir(parents=True)
        settings.write_text('<settings><profiles><profile><properties><skipTests>true</skipTests></properties></profile></profiles></settings>')
        result=self.run_cli("full")
        self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
        self.assertIn("ambient Maven",result.stderr)
        self.assertFalse((self.root/"target/command.json").exists())

    def test_allowed_configuration_never_records_sensitive_values(self):
        secret="synthetic-sensitive-value"
        result=self.run_cli("full",extra_env={"JAVA_TOOL_OPTIONS":"-Djavax.net.ssl.trustStorePassword="+secret})
        self.assertEqual(result.returncode,0,result.stdout+result.stderr)
        record=self.record()
        self.assertIn("property:javax.net.ssl.trustStorePassword",record["ambientConfiguration"]["JAVA_TOOL_OPTIONS"])
        self.assertNotIn(secret,json.dumps(record)+result.stdout+result.stderr)

    def test_focused_mode_cannot_silently_switch_pom_or_add_maven_goals(self):
        for value in ["-f alternate/pom.xml","clean verify","-Pother-profile"]:
            with self.subTest(option=value):
                result=self.run_cli("unit","ExampleTest",extra_env={"MAVEN_ARGS":value})
                self.assertNotEqual(result.returncode,0,result.stdout+result.stderr)
                self.assertFalse((self.root/"target/command.json").exists())

if __name__=="__main__":unittest.main()
