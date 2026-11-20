#!/usr/bin/env node
/** stdio adapter for this application's stateless Streamable HTTP endpoint. Node.js >=22. */
const MAX_INPUT = 65536
const MAX_OUTPUT = 3 * 1024 * 1024
const token = process.env.ACCOUNTING_MCP_TOKEN
let endpoint
try {
  endpoint = new URL(process.env.ACCOUNTING_MCP_URL)
  if (endpoint.username || endpoint.password || endpoint.search || endpoint.hash) throw new Error()
  if (endpoint.protocol !== 'https:' && !(endpoint.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(endpoint.hostname))) throw new Error()
  if (!token || /[\r\n]/.test(token)) throw new Error()
} catch {
  console.error('Set ACCOUNTING_MCP_URL (HTTPS, or loopback HTTP) and ACCOUNTING_MCP_TOKEN. No credentials in the URL.')
  process.exit(1)
}

let buffer = Buffer.alloc(0)
let protocolVersion
const pending = new Map()
let active = 0
function send(message) { process.stdout.write(JSON.stringify(message) + '\n') }
function fail(id, code, message) { send({ jsonrpc: '2.0', id, error: { code, message } }) }

async function forward(message) {
  const hasId = Object.hasOwn(message, 'id')
  if (message.method === 'notifications/cancelled') {
    pending.get(message.params?.requestId)?.abort()
    return
  }
  if (active >= 4) { if (hasId) fail(message.id, -32000, 'Too many in-flight requests; retry later with unchanged arguments.'); return }
  if (hasId && pending.has(message.id)) { fail(message.id, -32600, 'Duplicate in-flight JSON-RPC ID'); return }
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 30000)
  if (hasId) pending.set(message.id, controller)
  active++
  try {
    const headers = { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream', Authorization: `Bearer ${token}` }
    if (protocolVersion) headers['MCP-Protocol-Version'] = protocolVersion
    // Never follow a redirect with credentials and never automatically retry writes.
    const response = await fetch(endpoint, { method: 'POST', headers, body: JSON.stringify(message), signal: controller.signal, redirect: 'error' })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    if (response.status === 202 || response.status === 204) return
    if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('Expected stateless JSON response')
    let length = 0
    const chunks = []
    for await (const chunk of response.body) {
      length += chunk.length
      if (length > MAX_OUTPUT) { controller.abort(); throw new Error('Response too large') }
      chunks.push(chunk)
    }
    const result = JSON.parse(Buffer.concat(chunks).toString('utf8'))
    if (!hasId) return
    if (result.jsonrpc !== '2.0' || result.id !== message.id) throw new Error('Invalid response ID')
    if (message.method === 'initialize' && result.result?.protocolVersion) protocolVersion = result.result.protocolVersion
    send(result)
  } catch (error) {
    // No upstream body, URL or token is copied into diagnostics.
    const status = /^HTTP [0-9]{3}$/.test(error.message) ? ` (${error.message})` : ''
    if (hasId) fail(message.id, -32000, `MCP request failed${status}. A write may have committed; retry the identical arguments and clientRequestId.`)
    else console.error('MCP notification delivery failed')
  } finally {
    clearTimeout(timeout)
    if (hasId) pending.delete(message.id)
    active--
  }
}

process.stdin.on('data', chunk => {
  buffer = Buffer.concat([buffer, chunk])
  let boundary
  while ((boundary = buffer.indexOf(10)) !== -1) {
    const line = buffer.subarray(0, boundary)
    buffer = buffer.subarray(boundary + 1)
    if (!line.length) continue
    if (line.length > MAX_INPUT) { fail(null, -32600, 'Message exceeds 64 KiB'); continue }
    try {
      const message = JSON.parse(line.toString('utf8'))
      if (!message || Array.isArray(message) || message.jsonrpc !== '2.0' || typeof message.method !== 'string'
          || (Object.hasOwn(message, 'id') && !['number', 'string'].includes(typeof message.id))) {
        fail(null, -32600, 'Expected a JSON-RPC request or notification'); continue
      }
      void forward(message)
    } catch { fail(null, -32700, 'Invalid JSON') }
  }
  if (buffer.length > MAX_INPUT) { console.error('Input exceeds 64 KiB without a newline'); process.exit(1) }
})
process.stdin.on('end', () => { for (const controller of pending.values()) controller.abort() })
process.stdout.on('error', () => process.exit(1))
