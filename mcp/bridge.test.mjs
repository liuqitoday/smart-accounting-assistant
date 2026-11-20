import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { createInterface } from 'node:readline'

test('stdio bridge preserves request identity, forwards authentication, negotiates version and reports HTTP errors', async t => {
  const received = []
  let reject = false
  const server = createServer(async (req, res) => {
    let body = ''
    for await (const chunk of req) body += chunk
    const message = JSON.parse(body)
    received.push({ headers: req.headers, message })
    if (reject) { res.writeHead(401).end('private upstream content'); return }
    if (!Object.hasOwn(message, 'id')) { res.writeHead(202).end(); return }
    res.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify({ jsonrpc: '2.0', id: message.id,
      result: message.method === 'initialize' ? { protocolVersion: '2025-06-18' } : { content: [], isError: false } }))
  })
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')
  t.after(() => server.close())
  const child = spawn(process.execPath, [new URL('./bridge.mjs', import.meta.url).pathname], {
    env: { ...process.env, ACCOUNTING_MCP_URL: `http://127.0.0.1:${server.address().port}/mcp`, ACCOUNTING_MCP_TOKEN: 'test-private-token' },
    stdio: ['pipe', 'pipe', 'pipe']
  })
  t.after(() => child.kill())
  const lines = createInterface({ input: child.stdout })
  async function request(message) {
    const answer = once(lines, 'line')
    child.stdin.write(JSON.stringify(message) + '\n')
    return JSON.parse((await answer)[0])
  }
  const initialized = await request({ jsonrpc: '2.0', id: 1, method: 'initialize', params: {} })
  assert.equal(initialized.result.protocolVersion, '2025-06-18')
  const call = { jsonrpc: '2.0', id: 'write', method: 'tools/call', params: { name: 'accounting_create_transaction', arguments: { amount: '35.00', clientRequestId: 'bc82594b-c302-49b1-9335-9c5b85abfd77' } } }
  assert.equal((await request(call)).result.isError, false)
  assert.equal(received[1].headers.authorization, 'Bearer test-private-token')
  assert.equal(received[1].headers['mcp-protocol-version'], '2025-06-18')
  assert.deepEqual(received[1].message, call)
  reject = true
  const failed = await request({ ...call, id: 3 })
  assert.match(failed.error.message, /HTTP 401/)
  assert.match(failed.error.message, /may have committed/)
  assert.ok(!JSON.stringify(failed).includes('test-private-token'))
  assert.ok(!JSON.stringify(failed).includes('private upstream'))
  assert.equal(received.length, 3, 'bridge must not retry a potentially committed write')
})

test('bridge refuses non-loopback plaintext and credential URLs', async () => {
  for (const url of ['http://example.com/mcp', 'https://user:password@example.com/mcp', 'https://example.com/mcp?token=secret']) {
    const child = spawn(process.execPath, [new URL('./bridge.mjs', import.meta.url).pathname], {
      env: { ...process.env, ACCOUNTING_MCP_URL: url, ACCOUNTING_MCP_TOKEN: 'test-token' }
    })
    assert.equal((await once(child, 'exit'))[0], 1)
  }
})
