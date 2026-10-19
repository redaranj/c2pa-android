#!/usr/bin/env python3
"""Build a static Dokka site from explicitly selected published releases."""

import html
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[2]
TAG_PATTERN = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?")
PACKAGE_INDEX = Path("c2pa-android/org.contentauth.c2pa/index.html")


def load_versions(filename: Path) -> list[str]:
    config = json.loads(filename.read_text(encoding="utf-8"))
    versions = config.get("versions") if isinstance(config, dict) else None
    if not isinstance(versions, list) or not versions:
        raise ValueError("Documentation versions must be a non-empty list.")
    for tag in versions:
        if not isinstance(tag, str) or not TAG_PATTERN.fullmatch(tag):
            raise ValueError("Documentation versions must be release tags such as 0.0.15.")
    if len(set(versions)) != len(versions):
        raise ValueError("Documentation versions must not contain duplicate tags.")
    return versions


def verify_release(tag: str) -> None:
    result = subprocess.run(
        ["gh", "api", f"repos/contentauth/c2pa-android/releases/tags/{tag}"],
        check=True, stdout=subprocess.PIPE, text=True,
    )
    release = json.loads(result.stdout)
    if (
        not isinstance(release, dict)
        or release.get("tag_name") != tag
        or release.get("draft") is not False
    ):
        raise ValueError(f"{tag} is not a published GitHub release.")


def export_release(root: Path, tag: str, destination: Path) -> None:
    commit = subprocess.run(
        ["git", "rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}"],
        cwd=root, check=True, capture_output=True, text=True,
    ).stdout.strip()
    archive = subprocess.run(
        ["git", "archive", "--format=zip", commit],
        cwd=root, check=True, capture_output=True,
    ).stdout
    with zipfile.ZipFile(io.BytesIO(archive)) as sources:
        for entry in sources.infolist():
            path = PurePosixPath(entry.filename)
            if (
                not path.parts
                or path.is_absolute()
                or ".." in path.parts
                or path.parts[0] == ".git"
                or any("\\" in part or ":" in part for part in path.parts)
                or stat.S_ISLNK(entry.external_attr >> 16)
            ):
                raise ValueError(f"Unsafe source archive in release {tag}.")
            if entry.is_dir():
                continue
            target = destination.joinpath(*path.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(sources.read(entry))
            if (entry.external_attr >> 16) & 0o111:
                target.chmod(0o755)
    required = [
        destination / "gradlew",
        destination / "gradle/wrapper/gradle-wrapper.jar",
        destination / "library/build.gradle.kts",
        destination / "library/MODULE.md",
    ]
    if not all(path.is_file() for path in required) or not list(
        (destination / "library/src/main/kotlin").rglob("*.kt")
    ):
        raise ValueError(f"Release {tag} is missing its Gradle wrapper or Kotlin API sources.")


def build_docs(stage: Path) -> Path:
    environment = os.environ.copy()
    for name in ("GH_TOKEN", "GITHUB_TOKEN"):
        environment.pop(name, None)
    subprocess.run(
        [
            "./gradlew", "--no-daemon", "--console=plain",
            ":library:dokkaGeneratePublicationHtml", "--rerun-tasks",
        ],
        cwd=stage, env=environment, check=True,
    )
    output = stage / "build/docs"
    if not (output / "index.html").is_file() or not (output / PACKAGE_INDEX).is_file():
        raise RuntimeError("Dokka did not generate the library and C2PA package landing pages.")
    return output


def render_docs(source: Path, output: Path, tag: str, versions: list[str], prefix: str) -> None:
    shutil.copytree(source, output)
    for page in output.rglob("*.html"):
        relative = page.relative_to(output)
        # Dokka loads this sidebar fragment separately; it is not a complete page.
        if relative == Path("navigation.html"):
            continue
        content = page.read_text(encoding="utf-8")
        if "</head>" not in content or 'id="library-version"' not in content:
            raise ValueError(f"Dokka page is missing its head or version slot: {relative}")
        assets = "../" * (len(relative.parts) - 1)
        additions = (
            f'<link rel="stylesheet" href="{assets}doc-version-selector.css">\n'
            f'<script defer src="{assets}doc-version-selector.js"'
            f' data-version="{html.escape(tag, quote=True)}"'
            f' data-versions="{html.escape(json.dumps(versions), quote=True)}"'
            f' data-prefix="{html.escape(prefix, quote=True)}"></script>\n'
        )
        page.write_text(content.replace("</head>", additions + "</head>"), encoding="utf-8")
    for asset in ("doc-version-selector.js", "doc-version-selector.css"):
        shutil.copy2(ROOT / ".github/scripts" / asset, output)


def build_site() -> Path:
    versions = load_versions(ROOT / "docs/versions.json")
    for tag in versions:
        verify_release(tag)
    build = ROOT / "build"
    build.mkdir(exist_ok=True)
    output = build / "docs-versioned"
    with tempfile.TemporaryDirectory(prefix="versioned-docs-", dir=build) as temporary:
        workspace = Path(temporary)
        site = workspace / "site"
        default_docs = None
        for tag in versions:
            stage = workspace / tag
            stage.mkdir()
            export_release(ROOT, tag, stage)
            generated = build_docs(stage)
            if tag == versions[0]:
                default_docs = generated
            render_docs(generated, site / "versions" / tag, tag, versions, f"versions/{tag}")
        if default_docs is None:
            raise RuntimeError("No default documentation was built.")
        default = workspace / "default"
        render_docs(default_docs, default, versions[0], versions, "")
        shutil.copytree(default, site, dirs_exist_ok=True)
        (site / ".nojekyll").touch()
        if output.exists():
            shutil.rmtree(output)
        shutil.move(str(site), str(output))
    return output


if __name__ == "__main__":
    print(f"Versioned Dokka documentation generated at: {build_site()}")
