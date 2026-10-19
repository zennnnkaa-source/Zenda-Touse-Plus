#!/usr/bin/env python3
"""Heuristic Android project audit with optional Gradle quality gates.

Uses only the Python standard library. Structural success is not a substitute for
compilation, tests, security review, or device verification.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Iterable

IGNORED_DIRS = {
    ".git", ".gradle", ".idea", ".kotlin", "build", "out", "captures",
    "node_modules", "target", "dist", "__pycache__", ".cxx", ".externalNativeBuild",
}
SOURCE_SUFFIXES = {".kt", ".java", ".kts", ".gradle", ".xml", ".properties", ".toml", ".json", ".pro"}
GRADLE_NAMES = {"build.gradle", "build.gradle.kts"}

SECRET_PATTERNS = [
    ("Google API key-like token", re.compile(r"\bAIza[0-9A-Za-z_-]{30,}\b")),
    ("GitHub token-like value", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{30,}\b")),
    ("private-key header", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")),
    ("live/test service secret-like value", re.compile(r"\b(?:sk|pk)_(?:live|test)_[0-9a-zA-Z]{16,}\b")),
    ("possible hard-coded credential", re.compile(
        r"(?i)\b(?:api[_-]?key|client[_-]?secret|access[_-]?token|password)\b\s*[:=]\s*[\"'][^\"']{12,}[\"']"
    )),
]
PLACEHOLDER_WORDS = ("your_", "your-", "changeme", "change_me", "example", "placeholder", "dummy", "insert_key", "xxx")
PLACEHOLDER_PATTERNS = [
    re.compile(r"(?i)\bTODO\b"),
    re.compile(r"(?i)\bFIXME\b"),
    re.compile(r"(?i)NotImplementedError|UnsupportedOperationException\s*\("),
    re.compile(r"(?i)throw\s+NotImplemented"),
]

class Audit:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.notes: list[str] = []

    def error(self, message: str) -> None:
        self.errors.append(message)

    def warn(self, message: str) -> None:
        self.warnings.append(message)

    def note(self, message: str) -> None:
        self.notes.append(message)


def iter_files(root: Path) -> Iterable[Path]:
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        try:
            rel_parts = path.relative_to(root).parts
        except ValueError:
            continue
        if any(part in IGNORED_DIRS for part in rel_parts):
            continue
        if path.is_symlink():
            continue
        yield path


def read_text(path: Path) -> str | None:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return None


def relative(path: Path, root: Path) -> str:
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return str(path)


def check_structure(root: Path, audit: Audit) -> None:
    if not root.exists() or not root.is_dir():
        audit.error(f"Project root does not exist or is not a directory: {root}")
        return

    files = list(iter_files(root))
    names = {p.name for p in files}
    settings_files = [p for p in (root / "settings.gradle", root / "settings.gradle.kts") if p.is_file()]
    build_files = [p for p in files if p.name in GRADLE_NAMES]
    app_plugin_files: list[Path] = []
    android_plugin_files: list[Path] = []
    manifests = [p for p in files if p.name == "AndroidManifest.xml" and "src" in p.parts]
    main_sources: list[Path] = []
    test_sources: list[Path] = []

    if not settings_files:
        audit.error("Missing settings.gradle or settings.gradle.kts at the project root.")
    if not build_files:
        audit.error("No Gradle build.gradle/build.gradle.kts files were found.")
    for build_file in build_files:
        text = read_text(build_file) or ""
        if re.search(r"com\.android\.application", text):
            app_plugin_files.append(build_file)
        if re.search(r"com\.android\.(?:application|library|test)", text):
            android_plugin_files.append(build_file)

    if not app_plugin_files:
        audit.warn("Could not identify a Gradle module applying com.android.application. This may be a library-only, multiplatform, or nonstandard project; verify manually.")
    if android_plugin_files and not manifests:
        audit.error("Android Gradle plugin configuration was found, but no AndroidManifest.xml under a src directory was found.")
    elif not manifests:
        audit.warn("No AndroidManifest.xml under a src directory was found; project type may be nonstandard or incomplete.")

    for path in files:
        if path.suffix.lower() not in {".kt", ".java"}:
            continue
        if "src" in path.parts and "main" in path.parts:
            main_sources.append(path)
        if "src" in path.parts and any(part in {"test", "androidTest"} for part in path.parts):
            test_sources.append(path)

    if not main_sources:
        audit.error("No Kotlin or Java source files were found under a src/main tree.")
    if not test_sources:
        audit.warn("No Kotlin/Java test files were found under src/test or src/androidTest. Confirm whether tests are needed for this app.")

    wrapper_paths = {
        "gradlew": root / "gradlew",
        "gradlew.bat": root / "gradlew.bat",
        "gradle/wrapper/gradle-wrapper.jar": root / "gradle" / "wrapper" / "gradle-wrapper.jar",
        "gradle/wrapper/gradle-wrapper.properties": root / "gradle" / "wrapper" / "gradle-wrapper.properties",
    }
    missing_wrapper = [name for name, path in wrapper_paths.items() if not path.is_file()]
    if missing_wrapper:
        audit.warn("Gradle Wrapper files appear incomplete/missing: " + ", ".join(missing_wrapper) + ". A reproducible standalone build may not be possible until the wrapper is added.")
    else:
        audit.note("Gradle Wrapper files are present; wrapper execution has not yet been verified.")

    # Validate XML well-formedness for manifests and Android resource XML.
    xml_files = [p for p in files if p.suffix.lower() == ".xml" and (p.name == "AndroidManifest.xml" or "res" in p.parts)]
    for path in xml_files:
        try:
            ET.parse(path)
        except (ET.ParseError, OSError) as exc:
            audit.error(f"Malformed XML in {relative(path, root)}: {exc}")

    # Heuristic scan for likely unfinished implementation markers.
    placeholder_hits: list[str] = []
    secret_hits: list[str] = []
    scanned = 0
    max_file_size = 2 * 1024 * 1024
    for path in files:
        if path.suffix.lower() not in SOURCE_SUFFIXES or path.stat().st_size > max_file_size:
            continue
        content = read_text(path)
        if content is None:
            continue
        scanned += 1
        rel = relative(path, root)
        for line_no, line in enumerate(content.splitlines(), start=1):
            normalized = line.lower()
            for label, pattern in SECRET_PATTERNS:
                if pattern.search(line):
                    if any(word in normalized for word in PLACEHOLDER_WORDS):
                        continue
                    secret_hits.append(f"{rel}:{line_no} ({label})")
                    break
            if any(pattern.search(line) for pattern in PLACEHOLDER_PATTERNS):
                placeholder_hits.append(f"{rel}:{line_no}")

    for hit in secret_hits:
        audit.warn(f"Review possible hard-coded secret at {hit}. The scan is heuristic; it does not print the matched value.")
    for hit in placeholder_hits:
        audit.warn(f"Review possible unfinished implementation marker at {hit}.")

    # Flag local SDK configuration only as a packaging note, not a project error.
    if (root / "local.properties").exists():
        audit.warn("local.properties exists. Do not include it in a project ZIP if it contains a machine-specific SDK path.")

    audit.note(f"Scanned {scanned} source/config files and {len(xml_files)} Android XML files.")
    audit.note("This audit checks structure and selected heuristics only; it does not resolve dependencies or compile the app.")


def find_wrapper(root: Path) -> Path | None:
    if os.name == "nt":
        candidates = [root / "gradlew.bat", root / "gradlew"]
    else:
        candidates = [root / "gradlew", root / "gradlew.bat"]
    return next((p for p in candidates if p.is_file()), None)


def run_gradle(root: Path, tasks: list[str]) -> int:
    wrapper = find_wrapper(root)
    if wrapper is None:
        print("ERROR: Cannot run Gradle: wrapper was not found at the project root.", file=sys.stderr)
        return 2
    print(f"\n> Running: {wrapper.name} {' '.join(tasks)}", flush=True)
    if os.name == "nt":
        command = ["cmd.exe", "/c", str(wrapper), *tasks]
    elif os.access(wrapper, os.X_OK):
        command = [str(wrapper), *tasks]
    else:
        # Gradle wrapper scripts commonly lose their executable bit in ZIP transfers.
        command = ["bash", str(wrapper), *tasks]
    try:
        result = subprocess.run(command, cwd=root, check=False)
    except FileNotFoundError as exc:
        print(f"ERROR: Could not start Gradle wrapper: {exc}", file=sys.stderr)
        return 2
    return result.returncode


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", default=".", help="Android project root (default: current directory)")
    parser.add_argument("--build", action="store_true", help="After structural audit, run lint, unit tests, and assembleDebug")
    parser.add_argument("--instrumented", action="store_true", help="Also run connectedDebugAndroidTest; implies --build")
    parser.add_argument("--release", action="store_true", help="Also run assembleRelease; implies --build")
    args = parser.parse_args()

    root = Path(args.root).expanduser().resolve()
    audit = Audit()
    check_structure(root, audit)

    print(f"Android project audit: {root}")
    for message in audit.notes:
        print(f"INFO: {message}")
    for message in audit.warnings:
        print(f"WARN: {message}")
    for message in audit.errors:
        print(f"ERROR: {message}")
    print(f"\nStructural result: {len(audit.errors)} error(s), {len(audit.warnings)} warning(s).")

    if audit.errors:
        print("Structural errors must be resolved or explicitly justified before build checks.")
        return 1

    if args.build or args.instrumented or args.release:
        task_groups = [["lint"], ["testDebugUnitTest"], ["assembleDebug"]]
        if args.instrumented:
            task_groups.append(["connectedDebugAndroidTest"])
        if args.release:
            task_groups.append(["assembleRelease"])
        for tasks in task_groups:
            code = run_gradle(root, tasks)
            if code != 0:
                print(f"FAIL: Gradle task group {' '.join(tasks)} exited with code {code}.", file=sys.stderr)
                return code or 1
        print("PASS: All requested Gradle task groups exited successfully.")
    else:
        print("NOTE: No Gradle build or tests were run. Add --build to request lint, unit tests, and a debug build.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
