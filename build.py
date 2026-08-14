#!/usr/bin/env python3
"""Builds the app via gradlew, setting JAVA_HOME to Android Studio's bundled JBR
since there's no system JDK on this machine.
"""

import argparse
import datetime
import json
import os
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent
APK_DIR = REPO_ROOT / "app/build/outputs/apk/fdroid/release"
BASELINE_FILE = REPO_ROOT / ".apk-size-baseline.json"


def human_size(num_bytes: int) -> str:
    size = float(num_bytes)
    for unit in ("B", "KiB", "MiB", "GiB"):
        if size < 1024 or unit == "GiB":
            return f"{size:.1f}{unit}" if unit != "B" else f"{int(size)}{unit}"
        size /= 1024
    return f"{size:.1f}GiB"


def run_gradle(task: str, extra_args: list[str]) -> None:
    subprocess.run([str(REPO_ROOT / "gradlew"), task, *extra_args], check=True, cwd=REPO_ROOT)


def find_apk() -> Path:
    apks = list(APK_DIR.glob("**/*.apk"))
    if not apks:
        print(f"No APK found under {APK_DIR}", file=sys.stderr)
        sys.exit(1)

    # Gradle's up-to-date check already guarantees this file matches the
    # current source; pick the newest to disambiguate version-tagged subdirs.
    return max(apks, key=lambda p: p.stat().st_mtime)


def print_size(save: bool) -> None:
    run_gradle("assembleFdroidRelease", [])

    apk = find_apk()
    num_bytes = apk.stat().st_size

    print(f"APK: {apk}")
    print(f"Size: {human_size(num_bytes)} ({num_bytes} bytes)")

    history = json.loads(BASELINE_FILE.read_text()) if BASELINE_FILE.exists() else []
    baseline_bytes = history[-1]["bytes"] if history else None

    if baseline_bytes is not None:
        diff = num_bytes - baseline_bytes
        if diff < 0:
            print(f"Change: -{human_size(-diff)} vs baseline")
        elif diff > 0:
            print(f"Change: +{human_size(diff)} vs baseline")
        else:
            print("Change: no change vs baseline")
    else:
        print("No baseline saved yet (run with --save to set one).")

    if save:
        if num_bytes == baseline_bytes:
            print("Size unchanged, baseline not updated.")
            return

        history.append(
            {
                "bytes": num_bytes,
                "human": human_size(num_bytes),
                "apk": str(apk.relative_to(REPO_ROOT)),
                "saved_at": datetime.datetime.now(datetime.timezone.utc)
                .isoformat(timespec="seconds"),
            }
        )
        BASELINE_FILE.write_text(json.dumps(history, indent=2) + "\n")
        print(f"Saved {num_bytes} bytes as new baseline entry.")


TASK_ALIASES = {
    "debug": "assembleFdroidDebug",
    "check": "compileFdroidDebugKotlin",
    "release": "assembleFdroidRelease",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)

    task_group = parser.add_mutually_exclusive_group()
    task_group.add_argument("--debug", action="store_const", dest="task", const="debug", help="assembleFdroidDebug (default)")
    task_group.add_argument("--check", action="store_const", dest="task", const="check", help="compileFdroidDebugKotlin (fast type-check, no packaging)")
    task_group.add_argument("--release", action="store_const", dest="task", const="release", help="assembleFdroidRelease")
    task_group.add_argument("--size", action="store_const", dest="task", const="size", help="build release, print APK size + diff vs saved baseline")
    task_group.add_argument("task_positional", nargs="?", metavar="task", help="any gradle task, passed through as-is")

    parser.add_argument(
        "--save",
        action="store_true",
        help="with --size, save the current APK size as a new baseline entry",
    )
    parser.add_argument(
        "gradle_args",
        nargs=argparse.REMAINDER,
        help="extra arguments passed through to gradlew",
    )

    args = parser.parse_args()
    if args.task is None:
        args.task = args.task_positional or "debug"
    return args


def main() -> None:
    os.environ.setdefault("JAVA_HOME", "/opt/android-studio/jbr")

    args = parse_args()

    if args.task == "size":
        print_size(save=args.save or "--save" in args.gradle_args)
    else:
        task = TASK_ALIASES.get(args.task, args.task)
        run_gradle(task, args.gradle_args)


if __name__ == "__main__":
    main()
