import io
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
import zipfile

import generate_versioned_docs as docs


VERSIONS = ["0.0.15", "0.0.14"]
PAGE = '<html><head><title>C2PA</title></head><body><div id="library-version"></div></body></html>'
NAVIGATION = '<div class="toc--part"><a href="index.html">C2PA</a></div>'


def make_dokka(output):
    for relative in [Path("index.html"), docs.PACKAGE_INDEX, docs.PACKAGE_INDEX.parent / "-reader/index.html"]:
        page = output / relative
        page.parent.mkdir(parents=True, exist_ok=True)
        page.write_text(PAGE, encoding="utf-8")
    (output / "navigation.html").write_text(NAVIGATION, encoding="utf-8")
    (output / "scripts").mkdir()
    (output / "scripts/main.js").write_text("// Dokka fixture", encoding="utf-8")
    return output


class VersionedDocsTests(unittest.TestCase):
    def test_versions_preserve_default_and_order(self):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / "versions.json"
            config.write_text(json.dumps({"versions": VERSIONS}))
            self.assertEqual(docs.load_versions(config), VERSIONS)
            config.write_text('{"versions": ["0.0.16-beta.1", "0.0.15"]}')
            self.assertEqual(docs.load_versions(config)[0], "0.0.16-beta.1")

    def test_invalid_versions_fail(self):
        invalid = [
            {}, [], {"versions": "0.0.15"},
            *({"versions": versions} for versions in [
                [], ["0.0.15", "0.0.15"], ["main"], ["../0.0.15"],
                ["--help"], ["v0.0.15"], ["0.0.15\n"], [15], [None],
            ]),
        ]
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / "versions.json"
            for value in invalid:
                with self.subTest(value=value):
                    config.write_text(json.dumps(value))
                    with self.assertRaises(ValueError):
                        docs.load_versions(config)

    @patch.object(docs.subprocess, "run")
    def test_only_matching_published_releases_are_accepted(self, run):
        run.return_value = Mock(stdout=json.dumps({"tag_name": VERSIONS[0], "draft": False}))
        docs.verify_release(VERSIONS[0])
        run.assert_called_once_with(
            ["gh", "api", f"repos/contentauth/c2pa-android/releases/tags/{VERSIONS[0]}"],
            check=True, stdout=subprocess.PIPE, text=True,
        )
        for release in [
            [], {"tag_name": VERSIONS[0]}, {"tag_name": VERSIONS[0], "draft": True},
            {"tag_name": VERSIONS[1], "draft": False},
        ]:
            with self.subTest(release=release):
                run.return_value = Mock(stdout=json.dumps(release))
                with self.assertRaises(ValueError):
                    docs.verify_release(VERSIONS[0])
        run.side_effect = subprocess.CalledProcessError(1, ["gh", "api"])
        with self.assertRaises(subprocess.CalledProcessError):
            docs.verify_release(VERSIONS[0])

    def make_archive(self, filename="gradlew", mode=stat.S_IFREG | 0o755):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as archive:
            archive.writestr("gradle/wrapper/gradle-wrapper.jar", b"fixture")
            archive.writestr("library/build.gradle.kts", "// fixture")
            archive.writestr("library/MODULE.md", "# Module c2pa-android")
            archive.writestr("library/src/main/kotlin/Reader.kt", "class Reader")
            entry = zipfile.ZipInfo(filename)
            entry.external_attr = mode << 16
            archive.writestr(entry, "#!/bin/sh\nexit 0\n")
        return buffer.getvalue()

    @patch.object(docs.subprocess, "run")
    def test_export_resolves_exact_tag_and_preserves_wrapper_permissions(self, run):
        run.side_effect = [Mock(stdout="a" * 40 + "\n"), Mock(stdout=self.make_archive())]
        with tempfile.TemporaryDirectory() as directory:
            stage = Path(directory)
            docs.export_release(docs.ROOT, VERSIONS[0], stage)
            self.assertTrue((stage / "gradlew").stat().st_mode & stat.S_IXUSR)
            self.assertTrue((stage / "library/src/main/kotlin/Reader.kt").is_file())
        self.assertEqual(
            run.call_args_list[0].args[0],
            ["git", "rev-parse", "--verify", f"refs/tags/{VERSIONS[0]}^{{commit}}"],
        )
        self.assertEqual(run.call_args_list[1].args[0], ["git", "archive", "--format=zip", "a" * 40])

    @patch.object(docs.subprocess, "run")
    def test_unsafe_archives_are_rejected(self, run):
        for filename, mode in [
            ("../escape", stat.S_IFREG), ("/escape", stat.S_IFREG),
            ("library/..\\escape", stat.S_IFREG), ("C:/escape", stat.S_IFREG),
            (".git/config", stat.S_IFREG), ("link", stat.S_IFLNK),
        ]:
            with self.subTest(filename=filename), tempfile.TemporaryDirectory() as directory:
                run.side_effect = [
                    Mock(stdout="a" * 40), Mock(stdout=self.make_archive(filename, mode)),
                ]
                with self.assertRaisesRegex(ValueError, "Unsafe source archive"):
                    docs.export_release(docs.ROOT, VERSIONS[0], Path(directory))

    @patch.object(docs.subprocess, "run")
    def test_missing_wrapper_is_rejected(self, run):
        run.side_effect = [Mock(stdout="a" * 40), Mock(stdout=self.make_archive("README.md"))]
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "missing its Gradle wrapper"):
                docs.export_release(docs.ROOT, VERSIONS[0], Path(directory))

    @patch.dict(os.environ, {"GH_TOKEN": "documentation-test-value", "GITHUB_TOKEN": "documentation-test-value"})
    @patch.object(docs.subprocess, "run")
    def test_build_uses_release_dokka_without_github_tokens(self, run):
        with tempfile.TemporaryDirectory() as directory:
            stage = Path(directory)
            generated = make_dokka(stage / "build/docs")
            self.assertEqual(docs.build_docs(stage), generated)
            self.assertEqual(
                run.call_args.args[0],
                ["./gradlew", "--no-daemon", "--console=plain",
                 ":library:dokkaGeneratePublicationHtml", "--rerun-tasks"],
            )
            self.assertEqual(run.call_args.kwargs["cwd"], stage)
            self.assertTrue(run.call_args.kwargs["check"])
            self.assertNotIn("GH_TOKEN", run.call_args.kwargs["env"])
            self.assertNotIn("GITHUB_TOKEN", run.call_args.kwargs["env"])

    @patch.object(docs.subprocess, "run")
    def test_missing_dokka_output_fails(self, run):
        with tempfile.TemporaryDirectory() as directory:
            stage = Path(directory)
            with self.assertRaisesRegex(RuntimeError, "landing pages"):
                docs.build_docs(stage)
            (stage / "build/docs").mkdir(parents=True)
            (stage / "build/docs/index.html").write_text(PAGE)
            with self.assertRaisesRegex(RuntimeError, "landing pages"):
                docs.build_docs(stage)

    def test_assets_are_relative_on_default_and_deep_version_pages(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = make_dokka(root / "source")
            for prefix in ["", f"versions/{VERSIONS[0]}", f"versions/{VERSIONS[1]}"]:
                with self.subTest(prefix=prefix):
                    output = root / (prefix or "default")
                    docs.render_docs(source, output, VERSIONS[0], VERSIONS, prefix)
                    self.assertIn('src="doc-version-selector.js"', (output / "index.html").read_text())
                    for relative in [docs.PACKAGE_INDEX, docs.PACKAGE_INDEX.parent / "-reader/index.html"]:
                        page = (output / relative).read_text()
                        assets = "../" * (len(relative.parts) - 1)
                        self.assertIn(f'src="{assets}doc-version-selector.js"', page)
                        self.assertIn(f'data-prefix="{prefix}"', page)
                        self.assertIn('data-versions="[&quot;0.0.15&quot;, &quot;0.0.14&quot;]"', page)
                    self.assertTrue((output / "doc-version-selector.css").is_file())
                    self.assertTrue((output / "doc-version-selector.js").is_file())
            self.assertEqual((source / "index.html").read_text(), PAGE)

    def test_dokka_navigation_fragment_and_existing_assets_are_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = make_dokka(root / "source")
            output = root / "output"
            docs.render_docs(source, output, VERSIONS[0], VERSIONS, "")
            self.assertEqual((output / "navigation.html").read_text(), NAVIGATION)
            self.assertEqual((output / "scripts/main.js").read_text(), "// Dokka fixture")

    def test_missing_head_or_version_slot_fails_explicitly(self):
        for content in ["<head></head><body>No slot</body>", '<div id="library-version"></div>']:
            with self.subTest(content=content), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                source = make_dokka(root / "source")
                (source / "index.html").write_text(content)
                with self.assertRaisesRegex(ValueError, "missing its head or version slot"):
                    docs.render_docs(source, root / "output", VERSIONS[0], VERSIONS, "")

    def configure_root(self, root):
        (root / "docs").mkdir()
        (root / "docs/versions.json").write_text(json.dumps({"versions": VERSIONS}))
        scripts = root / ".github/scripts"
        scripts.mkdir(parents=True)
        for asset in ["doc-version-selector.css", "doc-version-selector.js"]:
            shutil.copy2(docs.ROOT / ".github/scripts" / asset, scripts)
        previous = root / "build/docs-versioned"
        previous.mkdir(parents=True)
        (previous / "previous.html").write_text("Existing site")
        working = root / "build/docs"
        working.mkdir()
        (working / "working.html").write_text("Working-tree site")

    def test_failed_release_build_preserves_previous_site_and_cleans_workspace(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.configure_root(root)
            def build(stage):
                if stage.name == VERSIONS[1]:
                    raise subprocess.CalledProcessError(1, ["./gradlew"])
                return make_dokka(stage / "build/docs")
            with (
                patch.object(docs, "ROOT", root),
                patch.object(docs, "verify_release"),
                patch.object(docs, "export_release"),
                patch.object(docs, "build_docs", side_effect=build),
            ):
                with self.assertRaises(subprocess.CalledProcessError):
                    docs.build_site()
            self.assertEqual((root / "build/docs-versioned/previous.html").read_text(), "Existing site")
            self.assertEqual(list((root / "build").glob("versioned-docs-*")), [])

    def test_complete_site_has_default_and_both_versions_without_touching_working_docs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.configure_root(root)
            with (
                patch.object(docs, "ROOT", root),
                patch.object(docs, "verify_release") as verify,
                patch.object(docs, "export_release") as export,
                patch.object(docs, "build_docs", side_effect=lambda stage: make_dokka(stage / "build/docs")),
            ):
                output = docs.build_site()
            self.assertEqual(output, root / "build/docs-versioned")
            self.assertTrue((output / ".nojekyll").is_file())
            self.assertFalse((output / "previous.html").exists())
            for prefix, tag in [("", VERSIONS[0]), *(("versions/" + tag, tag) for tag in VERSIONS)]:
                page = (output / prefix / docs.PACKAGE_INDEX).read_text()
                self.assertIn(f'data-version="{tag}"', page)
                self.assertIn(f'data-prefix="{prefix}"', page)
            self.assertEqual([call.args[0] for call in verify.call_args_list], VERSIONS)
            self.assertEqual([call.args[1] for call in export.call_args_list], VERSIONS)
            self.assertEqual((root / "build/docs/working.html").read_text(), "Working-tree site")
            self.assertEqual(list((root / "build").glob("versioned-docs-*")), [])


if __name__ == "__main__":
    unittest.main()
