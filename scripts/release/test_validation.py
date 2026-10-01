import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import runpy
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

sys.dont_write_bytecode = True
root = Path(__file__).resolve().parents[2]
def module(name):
 spec = importlib.util.spec_from_file_location(name, root / 'scripts/release' / (name + '.py'))
 result = importlib.util.module_from_spec(spec)
 spec.loader.exec_module(result)
 return result
identity = module('validate')
registry = module('registry')

class Identity(unittest.TestCase):
 def setUp(self):
  self.commit = 'a' * 40
  self.files = {
   'pom.xml': '<project xmlns="http://maven.apache.org/POM/4.0.0"><version>1.1.0</version></project>',
   'package.json': json.dumps({'version': '1.1.0'}),
   'package-lock.json': json.dumps({'version': '1.1.0', 'packages': {'': {'version': '1.1.0'}}}),
   'start.sh': 'target/orbit-1.1.0.jar', 'start.cmd': 'target/orbit-1.1.0.jar',
  }
  self.paths = ['pom.xml', 'README.md', '.env.example']
 def git(self, *args):
  if args[0] == 'rev-parse': return self.commit
  if args[0] == 'show': return self.files[args[1].split(':', 1)[1]]
  if args[0] == 'ls-tree': return '\n'.join(self.paths)
  raise AssertionError(args)
 def validate(self, sha=None):
  with patch.object(identity, 'git', self.git), patch.object(identity.subprocess, 'run'), patch.dict(os.environ, {'GITHUB_SHA': sha or self.commit}):
   return identity.validate('v1.1.0')
 def test_exact_source_and_versions_pass(self):
  self.assertEqual(self.validate()['commit'], self.commit)
 def test_dispatch_oidc_wrong_commit_rejected(self):
  with self.assertRaises(ValueError): self.validate('b' * 40)
 def test_pom_mismatch_rejected(self):
  self.files['pom.xml'] = self.files['pom.xml'].replace('1.1.0', '1.0.0')
  with self.assertRaises(ValueError): self.validate()
 def test_node_metadata_mismatch_rejected(self):
  self.files['package.json'] = json.dumps({'version': '1.0.0'})
  with self.assertRaises(ValueError): self.validate()
 def test_launcher_mismatch_rejected(self):
  self.files['start.cmd'] = 'target/orbit-1.0.0.jar'
  with self.assertRaises(ValueError): self.validate()
 def test_private_env_archive_rejected(self):
  self.paths.append('nested/.env')
  with self.assertRaises(ValueError): self.validate()
 def test_generated_archive_rejected(self):
  self.paths.append('dist/release.json')
  with self.assertRaises(ValueError): self.validate()
 def test_invalid_tags_rejected_before_git(self):
  for tag in ['v01.1.0', 'v1.1', 'v1.1.0-rc1', 'v1.1.0; echo bad']:
   with self.assertRaises(ValueError): identity.validate(tag)
 def test_nonmain_ancestry_rejected(self):
  with patch.object(identity, 'git', self.git), patch.dict(os.environ, {'GITHUB_SHA': self.commit}), patch.object(identity.subprocess, 'run', side_effect=subprocess.CalledProcessError(1, ['git'])):
   with self.assertRaises(subprocess.CalledProcessError): identity.validate('v1.1.0')

class Registry(unittest.TestCase):
 def invoke(self, status):
  token = io.BytesIO(b'{"token":"synthetic-only"}')
  error = HTTPError('https://ghcr.io/example', status, 'fixture', {}, None)
  with patch.dict(os.environ, {'GITHUB_ACTOR': 'fixture', 'GH_TOKEN': 'synthetic-only'}), patch.object(registry, 'urlopen', side_effect=[token, error]):
   return registry.manifest('owner/orbit', '1.1.0')
 def test_only_definite_404_is_absent(self):
  self.assertIsNone(self.invoke(404))
 def test_authorization_failure_is_not_absent(self):
  with self.assertRaises(HTTPError): self.invoke(401)
 def test_server_failure_is_not_absent(self):
  with self.assertRaises(HTTPError): self.invoke(500)

class Checksums(unittest.TestCase):
 def test_final_checksum_excludes_itself_and_is_repeatable(self):
  cwd = Path.cwd()
  with tempfile.TemporaryDirectory(prefix='orbit-release-check-') as directory:
   os.chdir(directory)
   try:
    Path('dist').mkdir()
    Path('dist/orbit-1.1.0.jar').write_bytes(b'fixture')
    Path('dist/SHA256SUMS').write_text('stale file')
    with contextlib.redirect_stdout(io.StringIO()): runpy.run_path(root / 'scripts/release/checksums.py')
    actual = Path('dist/SHA256SUMS').read_text()
    self.assertEqual(actual, hashlib.sha256(b'fixture').hexdigest() + '  orbit-1.1.0.jar\n')
    with contextlib.redirect_stdout(io.StringIO()): runpy.run_path(root / 'scripts/release/checksums.py')
    self.assertEqual(Path('dist/SHA256SUMS').read_text(), actual)
   finally: os.chdir(cwd)

if __name__ == '__main__':
 unittest.main()
