import test from 'node:test'
import assert from 'node:assert/strict'
import os from 'node:os'
import path from 'node:path'
import { mkdtemp, mkdir, writeFile, rm, realpath, symlink } from 'node:fs/promises'
import { extractConfiguredRootDir, handleFindFiles, handleReadFile, handleSearchContent } from './handlers.js'

async function withTempRoot(run: (rootDir: string) => Promise<void>) {
  const tempDir = await mkdtemp(path.join(os.tmpdir(), 'knowledge-cli-'))
  const previousRoot = process.env.QA_CLI_ROOT_DIR

  process.env.QA_CLI_ROOT_DIR = tempDir
  try {
    await run(tempDir)
  } finally {
    if (previousRoot === undefined) {
      delete process.env.QA_CLI_ROOT_DIR
    } else {
      process.env.QA_CLI_ROOT_DIR = previousRoot
    }
    await rm(tempDir, { recursive: true, force: true })
  }
}

test('extractConfiguredRootDir reads only knowledge-cli scope rootDir', () => {
  assert.equal(
    extractConfiguredRootDir('extensions:\n  other:\n    rootDir: /tmp/wrong\n  knowledge-cli:\n    x-opsfactory:\n      scope:\n        rootDir: ../../../../knowledge-service/data/artifacts/src_1\n        sourceId: src_1\n'),
    '../../../../knowledge-service/data/artifacts/src_1',
  )
  assert.equal(
    extractConfiguredRootDir('extensions: {other: {rootDir: /tmp/wrong}, knowledge-cli: {x-opsfactory: {scope: {rootDir: ../../../../knowledge-service/data/artifacts/src_1, sourceId: src_1}}}}\n'),
    '../../../../knowledge-service/data/artifacts/src_1',
  )
})

test('find_files lists matching files within the configured root', async () => {
  await withTempRoot(async (rootDir) => {
    const resolvedRoot = await realpath(rootDir)
    await mkdir(path.join(rootDir, 'config'), { recursive: true })
    await writeFile(path.join(rootDir, 'config', 'app.yaml'), 'name: app\n', 'utf8')
    await writeFile(path.join(rootDir, 'config', 'note.txt'), 'hello\n', 'utf8')

    const result = JSON.parse(await handleFindFiles({ pathPrefix: 'config', glob: '*.yaml' }))

    assert.equal(result.rootDir, resolvedRoot)
    assert.equal(result.total, 1)
    assert.equal(result.files[0].path, path.join(resolvedRoot, 'config', 'app.yaml'))
  })
})

test('find_files rejects unsafe glob patterns', async () => {
  await withTempRoot(async () => {
    await assert.rejects(
      handleFindFiles({ glob: '../*.md' }),
      /Invalid glob pattern/,
    )
    await assert.rejects(
      handleFindFiles({ glob: '!*.md' }),
      /Invalid glob pattern/,
    )
  })
})

test('find_files reports truncated results when limit is reached', async () => {
  await withTempRoot(async (rootDir) => {
    await writeFile(path.join(rootDir, 'a.md'), 'a\n', 'utf8')
    await writeFile(path.join(rootDir, 'b.md'), 'b\n', 'utf8')
    await writeFile(path.join(rootDir, 'c.md'), 'c\n', 'utf8')

    const result = JSON.parse(await handleFindFiles({ glob: '*.md', limit: 2 }))

    assert.equal(result.total, 2)
    assert.equal(result.truncated, true)
  })
})

test('search_content finds text hits and returns absolute file paths', async () => {
  await withTempRoot(async (rootDir) => {
    const resolvedRoot = await realpath(rootDir)
    await mkdir(path.join(rootDir, 'logs'), { recursive: true })
    const filePath = path.join(resolvedRoot, 'logs', 'service.log')
    await writeFile(filePath, 'INFO startup\nERROR failed to bind port\n', 'utf8')

    const result = JSON.parse(await handleSearchContent({ query: 'failed to bind', pathPrefix: 'logs' }))

    assert.equal(result.rootDir, resolvedRoot)
    assert.equal(result.total, 1)
    assert.equal(result.hits[0].path, filePath)
    assert.equal(result.hits[0].line, 2)
  })
})

test('search_content handles queries that start with a dash', async () => {
  await withTempRoot(async (rootDir) => {
    const resolvedRoot = await realpath(rootDir)
    const filePath = path.join(resolvedRoot, 'dash.md')
    await writeFile(filePath, '- starts with dash\nnormal line\n', 'utf8')

    const result = JSON.parse(await handleSearchContent({ query: '- starts', glob: '*.md' }))

    assert.equal(result.total, 1)
    assert.equal(result.hits[0].path, filePath)
    assert.equal(result.hits[0].line, 1)
  })
})

test('search_content reports truncated results when limit is reached', async () => {
  await withTempRoot(async (rootDir) => {
    await writeFile(path.join(rootDir, 'one.md'), 'needle 1\nneedle 2\nneedle 3\n', 'utf8')

    const result = JSON.parse(await handleSearchContent({ query: 'needle', glob: '*.md', limit: 2 }))

    assert.equal(result.total, 2)
    assert.equal(result.truncated, true)
  })
})

test('search_content limits hits by glob when provided', async () => {
  await withTempRoot(async (rootDir) => {
    const resolvedRoot = await realpath(rootDir)
    const markdownPath = path.join(resolvedRoot, 'knowledge.md')
    const yamlPath = path.join(resolvedRoot, 'config.yaml')
    await writeFile(markdownPath, '用户基本信息\n', 'utf8')
    await writeFile(yamlPath, '用户基本信息\n', 'utf8')

    const result = JSON.parse(await handleSearchContent({ query: '用户基本信息', glob: '*.md' }))

    assert.equal(result.total, 1)
    assert.equal(result.hits[0].path, markdownPath)
  })
})

test('search_content searches files hidden by ignore rules', async () => {
  await withTempRoot(async (rootDir) => {
    const resolvedRoot = await realpath(rootDir)
    const artifactDir = path.join(rootDir, 'knowledge-service', 'data', 'artifacts', 'src_1', 'doc_1')
    await mkdir(artifactDir, { recursive: true })
    await writeFile(path.join(rootDir, '.gitignore'), 'knowledge-service/data/**\n', 'utf8')
    const contentPath = path.join(resolvedRoot, 'knowledge-service', 'data', 'artifacts', 'src_1', 'doc_1', 'content.md')
    await writeFile(contentPath, '## 接口日志信息表(t_staticlog)\n', 'utf8')

    const result = JSON.parse(await handleSearchContent({ query: 't_staticlog', glob: '*.md' }))

    assert.equal(result.total, 1)
    assert.equal(result.hits[0].path, contentPath)
  })
})

test('search_content rejects unsafe glob patterns', async () => {
  await withTempRoot(async () => {
    await assert.rejects(
      handleSearchContent({ query: '用户基本信息', glob: '../*.md' }),
      /Invalid glob pattern/,
    )
    await assert.rejects(
      handleSearchContent({ query: '用户基本信息', glob: '!*.md' }),
      /Invalid glob pattern/,
    )
  })
})

test('read_file returns numbered content for the requested line range', async () => {
  await withTempRoot(async (rootDir) => {
    const resolvedRoot = await realpath(rootDir)
    const filePath = path.join(resolvedRoot, 'run.log')
    await writeFile(filePath, 'line1\nline2\nline3\nline4\n', 'utf8')

    const result = JSON.parse(await handleReadFile({ path: filePath, startLine: 2, endLine: 3 }))

    assert.equal(result.path, filePath)
    assert.equal(result.startLine, 2)
    assert.equal(result.endLine, 3)
    assert.match(result.content, /2\s+line2/)
    assert.match(result.content, /3\s+line3/)
  })
})

test('read_file truncates line ranges that exceed the maximum window', async () => {
  await withTempRoot(async (rootDir) => {
    const resolvedRoot = await realpath(rootDir)
    const filePath = path.join(resolvedRoot, 'large.md')
    const content = Array.from({ length: 260 }, (_, index) => `line${index + 1}`).join('\n')
    await writeFile(filePath, content, 'utf8')

    const result = JSON.parse(await handleReadFile({ path: filePath, startLine: 10, endLine: 260 }))

    assert.equal(result.path, filePath)
    assert.equal(result.startLine, 10)
    assert.equal(result.endLine, 209)
    assert.equal(result.requestedEndLine, 260)
    assert.equal(result.truncated, true)
    assert.equal(result.truncatedReason, 'line_limit')
    assert.equal(result.nextStartLine, 210)
    assert.match(result.message, /内容已被截断/)
    assert.match(result.message, /实际返回到第 209 行/)
    assert.doesNotMatch(result.content, /210\s+line210/)
  })
})

test('read_file truncates output that exceeds the character budget', async () => {
  await withTempRoot(async (rootDir) => {
    const resolvedRoot = await realpath(rootDir)
    const filePath = path.join(resolvedRoot, 'wide.md')
    const content = Array.from({ length: 40 }, (_, index) => `line${index + 1} ${'x'.repeat(1000)}`).join('\n')
    await writeFile(filePath, content, 'utf8')

    const result = JSON.parse(await handleReadFile({ path: filePath, startLine: 1, endLine: 40 }))

    assert.equal(result.path, filePath)
    assert.equal(result.startLine, 1)
    assert.equal(result.truncated, true)
    assert.equal(result.truncatedReason, 'char_limit')
    assert.equal(result.nextStartLine, result.endLine + 1)
    assert.ok(result.content.length <= 24_000)
    assert.match(result.message, /内容已被截断/)
  })
})

test('read_file rejects paths outside the configured root', async () => {
  await withTempRoot(async (rootDir) => {
    const outsideFile = path.join(path.dirname(rootDir), 'outside.txt')
    await writeFile(outsideFile, 'outside\n', 'utf8')

    await assert.rejects(
      handleReadFile({ path: outsideFile }),
      /escapes configured rootDir/,
    )

    await rm(outsideFile, { force: true })
  })
})

test('find_files rejects pathPrefix symlink escapes', async () => {
  await withTempRoot(async (rootDir) => {
    const outsideDir = await mkdtemp(path.join(os.tmpdir(), 'knowledge-cli-outside-'))
    await writeFile(path.join(outsideDir, 'outside.md'), 'outside\n', 'utf8')
    await symlink(outsideDir, path.join(rootDir, 'link-out'))

    await assert.rejects(
      handleFindFiles({ pathPrefix: 'link-out' }),
      /escapes configured rootDir/,
    )

    await rm(outsideDir, { recursive: true, force: true })
  })
})
