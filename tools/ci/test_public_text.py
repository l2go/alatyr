"""Exercise the public-text boundary against temporary tracked files."""

import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


CHECKER = Path(__file__).with_name("check-public-text.py").resolve()


class PublicTextTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)

    def write(self, path, data, tracked=True):
        destination = self.root / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        if tracked:
            subprocess.run(["git", "--literal-pathspecs", "-C", str(self.root), "add", "--", path], check=True)

    def scan(self):
        return subprocess.run(
            [sys.executable, "-I", str(CHECKER), "--root", str(self.root)],
            capture_output=True, text=True,
        )

    def test_english_text_passes(self):
        self.write("README.md", b"An original world.\n")
        self.assertEqual(self.scan().returncode, 0)

    def test_both_range_boundaries_fail_in_any_text_file(self):
        for code in (0x0400, 0x0410, 0x04FF, 0x0500, 0x052F):
            with self.subTest(code=code):
                self.write("content/unusual.extension", ("line one\nprivate-content-" + chr(code)).encode())
                result = self.scan()
                self.assertEqual(result.returncode, 1)
                self.assertIn('"content/unusual.extension":2: a non-English letter in a public file', result.stderr)
                self.assertNotIn("private-content", result.stderr)
                self.assertNotIn(chr(code), result.stderr)

    def test_adjacent_code_points_are_outside_the_required_range(self):
        self.write("adjacent.txt", (chr(0x03FF) + chr(0x0530)).encode())
        self.assertEqual(self.scan().returncode, 0)

    def assert_safe_path_diagnostic(self, path):
        self.write(path, ("private-content-" + chr(0x0410)).encode())
        result = self.scan()
        self.assertEqual(result.returncode, 1)
        self.assertEqual(result.stdout, "")
        expected = f"{json.dumps(path, ensure_ascii=True)}:1: a non-English letter in a public file"
        self.assertEqual(result.stderr, expected + "\n")
        self.assertEqual(result.stderr.splitlines(), [expected])
        self.assertFalse(result.stderr.startswith("::"))
        self.assertNotIn("\x1b", result.stderr)
        self.assertNotIn("private-content", result.stderr)
        self.assertNotIn(chr(0x0410), result.stderr)

    def test_line_feed_in_path_cannot_inject_workflow_commands(self):
        self.assert_safe_path_diagnostic("page\n::error::forged\n::add-mask::marker\n.txt")

    def test_carriage_return_in_path_is_escaped(self):
        self.assert_safe_path_diagnostic("page\r::error::forged.txt")

    def test_escape_in_path_is_escaped(self):
        self.assert_safe_path_diagnostic("page\x1b[31m.txt")

    def test_error_command_at_start_of_path_is_quoted(self):
        self.assert_safe_path_diagnostic("::error::forged.txt")

    def test_mask_command_at_start_of_path_is_quoted(self):
        self.assert_safe_path_diagnostic("::add-mask::marker.txt")

    def test_text_with_a_byte_order_mark_is_checked(self):
        for encoding in ("utf-8-sig", "utf-16", "utf-32"):
            with self.subTest(encoding=encoding):
                self.write("encoded.txt", chr(0x0410).encode(encoding))
                self.assertEqual(self.scan().returncode, 1)

    def test_binary_and_untracked_files_are_not_text_in_the_commit(self):
        self.write("image.bin", b"\0" + chr(0x0410).encode())
        self.write("local.txt", chr(0x0410).encode(), tracked=False)
        self.assertEqual(self.scan().returncode, 0)

    def test_symlinks_do_not_read_outside_the_tree(self):
        self.write("local.txt", chr(0x0410).encode(), tracked=False)
        (self.root / "linked.txt").symlink_to(self.root / "local.txt")
        subprocess.run(["git", "-C", str(self.root), "add", "linked.txt"], check=True)
        self.assertEqual(self.scan().returncode, 0)

    def test_a_failing_checkout_is_not_a_pass(self):
        (self.root / "not-a-repository").mkdir()
        result = subprocess.run(
            [sys.executable, "-I", str(CHECKER), "--root", str(self.root.parent)],
            capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 1)

    def test_candidate_scripts_are_only_data(self):
        self.write("tools/ci/check-public-text.py", b"raise SystemExit('candidate code executed')\n")
        self.write("site.html", chr(0x0410).encode())
        result = self.scan()
        self.assertEqual(result.returncode, 1)
        self.assertIn('"site.html":1: a non-English letter in a public file', result.stderr)
        self.assertNotIn("candidate code executed", result.stderr)


if __name__ == "__main__":
    unittest.main()
