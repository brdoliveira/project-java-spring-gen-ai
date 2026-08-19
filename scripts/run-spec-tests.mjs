#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const reportPath = resolve(root, '.spec/verification/test-results.json');
const useExistingReports = process.env.SPEC_USE_EXISTING_REPORTS === 'true';

function runMaven(args) {
  const wrapper = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
  const result = spawnSync(wrapper, args, { cwd: root, shell: true, stdio: 'inherit' });
  return result.status ?? 1;
}

let commandStatus = 0;
if (!useExistingReports) {
  commandStatus = runMaven(['-B', '-ntp', 'test']);
  if (commandStatus === 0) {
    commandStatus = runMaven(['-B', '-ntp', '-f', 'posture-service/pom.xml', 'test']);
  }
}

function walk(directory, predicate) {
  if (!existsSync(directory)) return [];
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? walk(path, predicate) : predicate(path) ? [path] : [];
  });
}

function decodeXml(value) {
  return value
    .replaceAll('&quot;', '"')
    .replaceAll('&apos;', "'")
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&amp;', '&');
}

const surefireResults = new Map();
for (const reportsDirectory of [
  resolve(root, 'target/surefire-reports'),
  resolve(root, 'posture-service/target/surefire-reports'),
]) {
  for (const xmlPath of walk(reportsDirectory, (path) => /^TEST-.+\.xml$/.test(relative(reportsDirectory, path)))) {
    const xml = readFileSync(xmlPath, 'utf8');
    const cases = xml.matchAll(/<testcase\s+name="([^"]+)"\s+classname="([^"]+)"[^>]*?(?:\/>|>([\s\S]*?)<\/testcase>)/g);
    for (const match of cases) {
      const body = match[3] ?? '';
      const failure = /<(?:failure|error)\b[^>]*>([\s\S]*?)<\/(?:failure|error)>/.exec(body);
      const skipped = /<skipped\b/.test(body);
      const result = {
        status: skipped ? 'pending' : failure ? 'failed' : 'passed',
        failureMessages: failure ? [decodeXml(failure[1]).trim()] : [],
      };
      const testName = decodeXml(match[1]);
      surefireResults.set(`${decodeXml(match[2])}#${testName}`, result);
      surefireResults.set(`${decodeXml(match[2])}#${testName.replace(/\(.*\)$/, '')}`, result);
    }
  }
}

const assertions = [];
for (const testRoot of [resolve(root, 'src/test'), resolve(root, 'posture-service/src/test')]) {
  for (const javaPath of walk(testRoot, (path) => extname(path) === '.java')) {
    const source = readFileSync(javaPath, 'utf8');
    const packageName = /package\s+([\w.]+)\s*;/.exec(source)?.[1] ?? '';
    const className = /\bclass\s+(\w+)/.exec(source)?.[1];
    if (!className) continue;
    const displayNames = source.matchAll(/@DisplayName\("((?:[^"\\]|\\.)*)"\)[\s\S]{0,500}?\b(?:void|[\w<>?, ]+)\s+(\w+)\s*\(/g);
    for (const match of displayNames) {
      const title = match[1].replaceAll('\\"', '"');
      if (!title.includes('@spec:') && !title.includes('@principle:')) continue;
      const key = `${packageName}.${className}#${match[2]}`;
      const result = surefireResults.get(key) ?? {
        status: 'failed',
        failureMessages: [`No Surefire result found for ${key}`],
      };
      assertions.push({ ancestorTitles: [className], title, ...result });
    }
  }
}

if (assertions.length === 0) {
  assertions.push({
    ancestorTitles: ['SpecVerification'],
    title: 'No annotated Java tests were discovered',
    status: 'failed',
    failureMessages: ['Expected at least one @spec annotation in a JUnit @DisplayName'],
  });
}

const failed = assertions.filter((result) => result.status === 'failed');
const pending = assertions.filter((result) => result.status === 'pending');
const report = {
  numTotalTestSuites: 1,
  numPassedTestSuites: failed.length === 0 && commandStatus === 0 ? 1 : 0,
  numFailedTestSuites: failed.length === 0 && commandStatus === 0 ? 0 : 1,
  numPendingTestSuites: 0,
  numTotalTests: assertions.length,
  numPassedTests: assertions.length - failed.length - pending.length,
  numFailedTests: failed.length,
  numPendingTests: pending.length,
  numTodoTests: 0,
  success: failed.length === 0 && commandStatus === 0,
  testResults: [{
    name: 'Maven Surefire',
    status: failed.length === 0 && commandStatus === 0 ? 'passed' : 'failed',
    assertionResults: assertions,
  }],
};

mkdirSync(dirname(reportPath), { recursive: true });
writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`);
console.log(`${report.success ? 'PASS' : 'FAIL'} ${assertions.length} annotated Maven test(s)`);
process.exitCode = report.success ? 0 : 1;
