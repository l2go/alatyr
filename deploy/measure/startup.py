#!/usr/bin/env python3
"""Measures how long the server takes to start under several JVM configurations.

Usage: deploy/measure/startup.py --image IMAGE [--runs 5] [--configs g1,zgc,g1-aot,zgc-aot] [--output DIR]

For each configuration the stack starts on empty volumes. The first start loads the catalog into the empty
database; without an AOT cache it is reported as the cold start, and with one it writes the cache and is not
reported. Then the server runs in a new container on the loaded database, and restarts `runs` times. The time is the one the server reports in its own
"Platform ready in N ms" line, counted from the start of the JVM, so the time of Docker and of the health check
interval is not in it. After each start the script also reads the resident memory of the server process.

The result is a Markdown table (stdout, and DIR/startup.md) and DIR/startup.json. The measurement does not judge:
a person takes the decision from the numbers.
"""
import argparse
import datetime
import json
import os
import re
import statistics
import subprocess
import sys
import time

HEAP = "-Xms1g -Xmx3g"
READY = re.compile(r"Platform ready in (\d+) ms \(before main (\d+), login prepared (\d+), world started (\d+), "
                   r"login opened (\d+)\)")
AOT_FILE = "/var/lib/l2jfree/aot/server.aot"

CONFIGS = {
    "g1": {"gc": "-XX:+UseG1GC", "aot": False},
    "g1-no-recording": {"gc": "-XX:+UseG1GC", "aot": False, "env": {"L2JFREE_FLIGHT_RECORDER": "off"}},
    "zgc": {"gc": "-XX:+UseZGC", "aot": False},
    "g1-aot": {"gc": "-XX:+UseG1GC", "aot": True},
    "zgc-aot": {"gc": "-XX:+UseZGC", "aot": True},
}

HERE = os.path.dirname(os.path.abspath(__file__))
COMPOSE = ["docker", "compose", "-p", "l2jfree-measure", "-f", os.path.join(HERE, "..", "compose.yaml"),
           "-f", os.path.join(HERE, "compose.yaml")]


def run(args, env=None, check=True, timeout=900):
    full = dict(os.environ)
    full.update(env or {})
    return subprocess.run(args, env=full, check=check, timeout=timeout, text=True, capture_output=True)


def compose(*args, env=None, check=True, timeout=900):
    return run(COMPOSE + list(args), env=env, check=check, timeout=timeout)


def now():
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def ready_since(since):
    """The ready line that the server wrote after `since`. Only the end of the log is read: reading all of it at every
    poll cost more with every start of a container and slowed the server that was being measured."""
    logs = run(["docker", "logs", "--since", since, "--tail", "400", "l2jfree-measure-server-1"]).stdout
    for line in logs.splitlines():
        match = READY.search(line)
        if match:
            return match
    return None


def wait_for_ready(since, seconds=300):
    deadline = time.time() + seconds
    while time.time() < deadline:
        found = ready_since(since)
        if found:
            return found
        state = run(["docker", "inspect", "-f", "{{.State.Running}}", "l2jfree-measure-server-1"], check=False)
        if state.stdout.strip() == "false":
            raise SystemExit("the server stopped before it was ready:\n" + compose("logs", "--tail", "60", "server").stdout)
        time.sleep(3)
    marked = "\n".join(line for line in compose("logs", "--no-color", "--tail", "400", "server").stdout.splitlines()
                       if "Platform" in line or "ready" in line)
    raise SystemExit("the server was not ready after %d s; the lines about the platform:\n%s\n%s" % (
        seconds, marked, compose("ps", "-a").stdout))


def start_and_sample(*args, env=None):
    """Runs a compose command that starts the server and measures that start."""
    since = now()
    compose(*args, env=env)
    return sample(wait_for_ready(since))


def resident_megabytes():
    time.sleep(10)  # let the start settle: the JIT and the first collections run after the ports open
    status = run(["docker", "exec", "l2jfree-measure-server-1", "cat", "/proc/1/status"]).stdout
    kilobytes = int(re.search(r"VmRSS:\s+(\d+) kB", status).group(1))
    return round(kilobytes / 1024)


def sample(match):
    return {"total": int(match.group(1)), "main": int(match.group(2)), "login_prepared": int(match.group(3)),
            "world_started": int(match.group(4)), "login_opened": int(match.group(5)), "rss": resident_megabytes()}


def measure(name, image, runs):
    config = CONFIGS[name]
    options = "%s %s" % (HEAP, config["gc"])
    env = dict({"L2JFREE_IMAGE": image}, **config.get("env", {}))
    print("== %s: %s" % (name, options), file=sys.stderr)
    compose("down", "-v", "--remove-orphans", check=False)
    compose("up", "-d", "--wait", "db", env=env)
    result = {"options": options, "cold": None, "fresh": None, "restarts": []}
    # the first start loads the catalog into the empty database. Without a cache it is the cold start that is
    # reported; with one it also writes the cache when the JVM exits, so it is not comparable and not reported.
    loading = options + (" -XX:AOTCacheOutput=" + AOT_FILE if config["aot"] else "")
    cold = start_and_sample("up", "-d", "--no-deps", "server", env=dict(env, L2JFREE_JAVA_OPTS=loading))
    compose("stop", "-t", "120", "server", env=env)
    if config["aot"]:
        listing = run(["docker", "run", "--rm", "-v", "l2jfree-measure_aot:/aot", "--entrypoint", "ls", image,
                       "-l", "/aot"]).stdout
        if "server.aot" not in listing:
            raise SystemExit("the training run wrote no AOT cache:\n" + listing)
        options += " -XX:AOTCache=" + AOT_FILE
        result["options"] = options
    else:
        result["cold"] = cold
    env = dict(env, L2JFREE_JAVA_OPTS=options)
    # a new container on the loaded database
    result["fresh"] = start_and_sample("up", "-d", "--no-deps", "--force-recreate", "server", env=env)
    for _ in range(runs):
        result["restarts"].append(start_and_sample("restart", "-t", "60", "server", env=env))
    compose("down", "-v", "--remove-orphans", check=False)
    return result


def median(values):
    return int(statistics.median(values))


def seconds(millis):
    return "%.1f s" % (millis / 1000)


def table(results):
    rows = ["| Configuration | Cold start | New container | Restart, median | Restart, range | Before main | World | Resident memory |",
            "|---|---:|---:|---:|---:|---:|---:|---:|"]
    for name, result in results.items():
        if "error" in result:
            rows.append("| %s | failed: %s | | | | | | |" % (name, result["error"].splitlines()[0]))
            continue
        restarts = [w["total"] for w in result["restarts"]]
        rows.append("| %s | %s | %s | %s | %s - %s | %s | %s | %d MiB |" % (
            name, seconds(result["cold"]["total"]) if result["cold"] else "-", seconds(result["fresh"]["total"]),
            seconds(median(restarts)), seconds(min(restarts)), seconds(max(restarts)),
            seconds(median([w["main"] for w in result["restarts"]])),
            seconds(median([w["world_started"] for w in result["restarts"]])),
            median([w["rss"] for w in result["restarts"]])))
    return "\n".join(rows)


def diagnose(image, runs):
    """Why a restart is slower than a new container: the same stop followed by three kinds of start, G1 only."""
    options = "%s %s" % (HEAP, CONFIGS["g1"]["gc"])
    env = {"L2JFREE_IMAGE": image, "L2JFREE_JAVA_OPTS": options}
    print("== diagnose: %s" % options, file=sys.stderr)
    compose("down", "-v", "--remove-orphans", check=False)
    compose("up", "-d", "--wait", "db", env=env)
    start_and_sample("up", "-d", "--no-deps", "server", env=env)
    results = {"restart at once": [], "start after 30 s": [], "new container at once": []}
    for _ in range(runs):
        for kind in results:
            compose("stop", "-t", "60", "server", env=env)
            if kind == "start after 30 s":
                time.sleep(30)
            if kind == "new container at once":
                results[kind].append(start_and_sample("up", "-d", "--no-deps", "--force-recreate", "server", env=env))
            else:
                results[kind].append(start_and_sample("start", "server", env=env))
    compose("down", "-v", "--remove-orphans", check=False)
    return results


def diagnosis_table(results):
    rows = ["| Start after the stop | Median | Range |", "|---|---:|---:|"]
    for kind, samples in results.items():
        totals = [s["total"] for s in samples]
        rows.append("| %s | %s | %s - %s |" % (kind, seconds(median(totals)), seconds(min(totals)), seconds(max(totals))))
    return "\n".join(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--image", required=True)
    parser.add_argument("--runs", type=int, default=5)
    parser.add_argument("--configs", default=",".join(CONFIGS))
    parser.add_argument("--output", default=".")
    parser.add_argument("--diagnose", action="store_true", help="only compare the kinds of start after a stop")
    args = parser.parse_args()
    if args.diagnose:
        os.makedirs(args.output, exist_ok=True)
        results = diagnose(args.image, args.runs)
        with open(os.path.join(args.output, "restart.json"), "w") as out:
            json.dump(results, out, indent=2)
        markdown = diagnosis_table(results)
        with open(os.path.join(args.output, "restart.md"), "w") as out:
            out.write(markdown + "\n")
        print(markdown)
        return
    names = args.configs.split(",")
    unknown = [n for n in names if n not in CONFIGS]
    if unknown:
        parser.error("unknown configuration: " + ", ".join(unknown))
    os.makedirs(args.output, exist_ok=True)
    results = {}
    for name in names:
        # a configuration that fails is reported, and the others are still measured
        try:
            results[name] = measure(name, args.image, args.runs)
        except SystemExit as failure:
            results[name] = {"options": "", "error": str(failure)}
            compose("down", "-v", "--remove-orphans", check=False)
        with open(os.path.join(args.output, "startup.json"), "w") as out:
            json.dump(results, out, indent=2)
    markdown = table(results)
    with open(os.path.join(args.output, "startup.md"), "w") as out:
        out.write(markdown + "\n")
    print(markdown)
    if any("error" in result for result in results.values()):
        sys.exit(1)


if __name__ == "__main__":
    main()
