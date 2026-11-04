#!/usr/bin/env node
import {readFile, rm, writeFile} from 'node:fs/promises';
import {join} from 'node:path';
import {spawn} from 'node:child_process';
import {parseArgs} from 'node:util';
function pinDependencyProperties(sourcePom, dependencyVersions) {
  let pinned = sourcePom;
  for (const [property, version] of Object.entries(dependencyVersions).sort(([left], [right]) => left.localeCompare(right))) {
    if (!/^[A-Za-z0-9_.-]+$/.test(property)) throw new Error(`dependency property is invalid: ${property}`);
    if (typeof version !== 'string' || version.length === 0 || /SNAPSHOT/i.test(version)) {
      throw new Error(`dependency ${property} must use an immutable version`);
    }
    const escaped = property.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const pattern = new RegExp(`(<${escaped}>\\s*)[^<]*(\\s*</${escaped}>)`, 'g');
    const activePom = pinned.replace(/<!--[\s\S]*?-->/g, (comment) => ' '.repeat(comment.length));
    const matches = [...activePom.matchAll(pattern)];
    if (matches.length !== 1) {
      throw new Error(`POM must declare ${property} exactly once; found ${matches.length}`);
    }
    const [match] = matches;
    pinned = `${pinned.slice(0, match.index)}${match[1]}${version}${match[2]}${pinned.slice(match.index + match[0].length)}`;
  }
  return pinned;
}

const {values} = parseArgs({options: {
  mode: {type: 'string'}, source: {type: 'string'}, sourceSha: {type: 'string'},
  resolvedSet: {type: 'string'}, mavenRepository: {type: 'string'}
}, strict: true});
for (const key of ['mode', 'source', 'sourceSha', 'resolvedSet', 'mavenRepository']) {
  if (!values[key]) throw new Error(`--${key} is required`);
}
if (!['build', 'test'].includes(values.mode)) throw new Error('mode must be build or test');
if (!/^[a-f0-9]{40}$/.test(values.sourceSha)) throw new Error('reference source must be a full SHA');
const set = JSON.parse(await readFile(values.resolvedSet, 'utf8'));
if (set.testHarnesses?.references?.sha !== values.sourceSha ||
    set.testHarnesses?.references?.repository !== 'The-Pipeline-Framework/pipelineframework-reference-implementations') {
  throw new Error('cloud reference source does not match the resolved full-train set');
}
const versionProperties = {
  coordinationBom: 'pipelineframework.bom.version',
  compiler: 'pipelineframework.compiler.version',
  connectors: 'pipelineframework.connectors.version',
  runtime: 'pipelineframework.runtime.version'
};
const pins = {};
for (const [component, property] of Object.entries(versionProperties)) {
  const version = (component === 'coordinationBom' ? set.coordinationBom : set.components?.[component])?.mavenVersion;
  if (typeof version !== 'string' || !/^[0-9]+\.[0-9]+\.[0-9]+(?:-[a-zA-Z0-9.-]+)?$/.test(version) || version.endsWith('-SNAPSHOT')) {
    throw new Error(`cloud suite requires an immutable ${component} Maven version`);
  }
  pins[property] = version;
}
if (values.mode === 'test' && !/^https:\/\/[a-z0-9]+\.lambda-url\.[a-z0-9-]+\.on\.aws\/?$/.test(process.env.AWS_LAMBDA_ORCHESTRATOR_URL ?? '')) {
  throw new Error('cloud test requires the deployed AWS Lambda Function URL; skipping is forbidden');
}
const pom = join(values.source, 'pom.xml');
await writeFile(pom, pinDependencyProperties(await readFile(pom, 'utf8'), pins));
const args = ['-B', '--no-transfer-progress', `-Dmaven.repo.local=${values.mavenRepository}`,
  ...Object.entries(pins).map(([property, version]) => `-D${property}=${version}`)];
const command = values.mode === 'build' ? 'bash' : './mvnw';
const commandArgs = values.mode === 'build'
  ? ['./search/build-lambda-modular.sh', '-DskipTests', '-Dquarkus.container-image.build=false']
  : [...args, '-f', 'search/pom.xml', '-pl', 'orchestrator-svc', '-am',
    '-DskipUnitTests=true', '-Dfailsafe.failIfNoSpecifiedTests=false',
    '-Dquarkus.container-image.build=false', '-Dit.test=AwsLambdaModularEndToEndIT', 'verify'];
const reports = join(values.source, 'search/orchestrator-svc/target/failsafe-reports');
if (values.mode === 'test') {
  await rm(reports, {recursive: true, force: true});
}
const child = spawn(command, commandArgs, {cwd: values.source, stdio: 'inherit', shell: false,
  env: {...process.env, MAVEN_ARGS: args.join(' ')}});
process.exitCode = await new Promise((resolve, reject) => {
  child.once('error', reject);
  child.once('exit', (code, signal) => signal ? reject(new Error(`cloud suite terminated by ${signal}`)) : resolve(code ?? 1));
});
if (values.mode === 'test' && process.exitCode === 0) {
  const report = await readFile(join(reports,
    'TEST-org.pipelineframework.search.orchestrator.service.AwsLambdaModularEndToEndIT.xml'), 'utf8');
  const suite = report.match(/<testsuite\b[^>]*>/)?.[0] ?? '';
  const count = (attribute) => Number(suite.match(new RegExp(`\\b${attribute}="([0-9]+)"`))?.[1] ?? NaN);
  if (!(count('tests') >= 3 && count('skipped') === 0 && count('failures') === 0 && count('errors') === 0)) {
    throw new Error('AWS modular E2E evidence must contain at least three passing, unskipped tests');
  }
}
