<template>
  <div class="view-page">
    <PageHeader title="MCP 连接" subtitle="让你的 AI 助手帮你记账、查账，每个连接单独授权。">
      <template #actions>
        <button class="button" type="button" :disabled="!ledger.state.ledgers.length" @click="openCreate"><Plus />新建连接</button>
      </template>
    </PageHeader>
    <MessageBanner :message="message.text" :type="message.type" @dismissed="clear" />

    <section class="card connection-guide">
      <div class="guide-icon"><PlugZap /></div>
      <div class="guide-copy">
        <h2>连接你习惯使用的 Agent</h2>
        <p>创建连接后，将服务地址和专用令牌填入客户端的 MCP 设置。支持远程 Streamable HTTP，也可通过本地 stdio 桥接接入。</p>
        <div class="endpoint-row"><code>{{ endpoint }}</code><button class="button secondary compact" type="button" @click="copy(endpoint)">复制地址</button></div>
        <p class="muted-text">授权方式：Bearer Token。仅支持浏览器 OAuth 授权的客户端暂不可直接连接。</p>
      </div>
    </section>

    <div class="connection-list-heading"><h2>我的连接</h2><button class="button secondary compact" :disabled="loading" @click="load">刷新</button></div>
    <div v-if="loading && !connections.length" class="loading-state" role="status">正在加载连接…</div>
    <div v-else-if="loadFailed" class="empty-state"><p>连接列表加载失败</p><button class="button secondary" @click="load">重试</button></div>
    <div v-else-if="!connections.length" class="empty-state"><PlugZap /><h2>给助手一份专属授权</h2><p>选择账本和操作权限，就能开始在对话里记账。</p></div>
    <div v-else class="connection-list">
      <article v-for="connection in connections" :key="connection.id" class="card connection-card">
        <div class="connection-title"><h2>{{ connection.name }}</h2><span class="badge" :class="connection.status === 'ACTIVE' ? 'success' : 'neutral'">{{ statusLabel(connection.status) }}</span></div>
        <p>{{ connection.writable ? '记账与查询' : '只读查询' }} · {{ connection.ledgerIds.map(ledgerName).join('、') }}</p>
        <p v-if="connection.defaultLedgerId" class="muted-text">默认账本：{{ ledgerName(connection.defaultLedgerId) }}</p>
        <dl class="connection-meta">
          <div><dt>令牌</dt><dd><code>{{ connection.tokenPrefix }}…</code></dd></div>
          <div><dt>有效期至</dt><dd>{{ formatTime(connection.expiresAt) }}</dd></div>
          <div><dt>最近调用</dt><dd>{{ connection.lastUsedAt ? formatTime(connection.lastUsedAt) : '尚未使用' }}</dd></div>
        </dl>
        <div class="row-actions">
          <button class="button secondary compact" :disabled="busy" @click="showCalls(connection)">调用记录</button>
          <button v-if="connection.status === 'ACTIVE'" class="button secondary compact" :disabled="busy" @click="openRotate(connection)">更换令牌</button>
          <button v-if="connection.status === 'ACTIVE'" class="button danger compact" :disabled="busy" @click="revoke(connection)">撤销连接</button>
        </div>
      </article>
    </div>

    <UiModal v-model="connectionOpen" :title="secretOpen ? '保存连接信息' : rotating ? '更换令牌' : '新建 MCP 连接'" :subtitle="secretOpen ? '完整令牌只在这里显示一次。关闭后可通过更换令牌重新连接。' : ''" :size="secretOpen ? 'lg' : 'md'">
      <form v-if="!secretOpen && !rotating" class="grid" @submit.prevent="create">
        <div class="form-field"><label for="mcp-name">连接名称</label><input id="mcp-name" v-model="form.name" class="input" placeholder="例如：个人助手、工作电脑" maxlength="80" required /></div>
        <fieldset class="ledger-choices"><legend>允许访问的账本</legend>
          <label v-for="item in ledger.state.ledgers" :key="item.id"><input v-model="form.ledgerIds" type="checkbox" :value="item.id" /><span>{{ item.name }}</span><small>{{ item.myRole === 'VIEWER' ? '仅查看' : '可记账' }}</small></label>
        </fieldset>
        <div class="form-field"><label for="mcp-default">默认账本</label><select id="mcp-default" v-model="form.defaultLedgerId" class="select"><option :value="null">每次由助手确认</option><option v-for="item in selectedLedgers" :key="item.id" :value="item.id">{{ item.name }}</option></select></div>
        <div class="form-field"><label for="mcp-permission">操作权限</label><select id="mcp-permission" v-model="form.writable" class="select"><option :value="false">只读查询</option><option :value="true">记账与查询</option></select><small>实际操作仍受你在各账本中的成员权限限制。</small></div>
        <div class="form-field"><label for="mcp-expiry">有效期</label><select id="mcp-expiry" v-model="form.expiresInDays" class="select"><option :value="30">30 天</option><option :value="90">90 天</option><option :value="365">365 天</option></select></div>
        <MessageBanner :message="modalMessage.text" :type="modalMessage.type" />
        <div class="row-actions"><button class="button" type="submit" :disabled="busy || !form.ledgerIds.length">{{ busy ? '创建中…' : '创建连接' }}</button><button class="button secondary" type="button" :disabled="busy" @click="createOpen = false">取消</button></div>
      </form>
      <div v-else-if="!secretOpen && rotating" class="grid">
        <p>更换「{{ rotating.name }}」的令牌后，原令牌立即失效，需要将新令牌更新到客户端。有效期保持不变。</p>
        <MessageBanner :message="modalMessage.text" :type="modalMessage.type" />
        <div class="row-actions"><button class="button" :disabled="busy" @click="rotate">{{ busy ? '更换中…' : '确认更换' }}</button><button class="button secondary" :disabled="busy" @click="connectionOpen = false">取消</button></div>
      </div>
      <div v-else-if="issued" class="grid">
        <div class="form-field"><label for="mcp-url">服务地址 · Streamable HTTP</label><input id="mcp-url" class="input" readonly :value="endpoint" /></div>
        <div class="form-field"><label for="mcp-token">专用令牌</label><textarea id="mcp-token" class="input token-value" rows="2" readonly :value="issued.token" autocomplete="off" spellcheck="false" /><button class="button secondary" @click="copy(issued.token)"><Copy />复制令牌</button></div>
        <p>认证选择 Bearer Token，或添加请求头 <code>Authorization: Bearer &lt;专用令牌&gt;</code>。将令牌保存在客户端的连接设置中。</p>
        <details class="bridge-help" open>
          <summary>Streamable HTTP · JSON 配置示例</summary>
          <p>使用常见的 <code>mcpServers</code> 格式；<code>type: "http"</code> 表示 Streamable HTTP。</p>
          <pre>{{ httpConfig }}</pre>
          <button class="button secondary compact" type="button" @click="copy(httpConfig)">复制 HTTP 配置示例</button>
          <p class="muted-text">将 &lt;专用令牌&gt; 替换为上方令牌。不同客户端的配置层级和传输字段可能不同，请按客户端说明调整。</p>
        </details>
        <details class="bridge-help"><summary>客户端只支持本地命令（stdio）？</summary><p>使用项目内的 Node.js 桥接脚本。将下面脚本路径替换为本机绝对路径；环境变量由客户端传给进程。需要 Node.js 22 或以上。</p><pre>{{ stdioConfig }}</pre><button class="button secondary compact" @click="copy(stdioConfig)">复制 stdio 配置示例</button><p class="muted-text">不同客户端配置格式可能不同；示例中的令牌占位符需替换为上方专用令牌。</p></details>
        <MessageBanner :message="secretMessage.text" :type="secretMessage.type" />
        <div class="row-actions"><button class="button" @click="secretOpen = false">我已保存</button></div>
      </div>
    </UiModal>

    <UiModal v-model="callsOpen" :title="`${callsName} · 最近调用`" size="lg">
      <p v-if="callsLoading" role="status">正在加载…</p>
      <div v-else-if="callsFailed"><p>调用记录加载失败</p><button class="button secondary" @click="loadCalls">重试</button></div>
      <p v-else-if="!calls.length">还没有调用记录。连接后，让助手查询可用账本即可验证接入。</p>
      <ol v-else class="call-list"><li v-for="call in calls" :key="call.id"><div><strong>{{ toolLabel(call.toolName) }}</strong><span class="badge" :class="call.outcome === 'ERROR' ? 'neutral' : 'success'">{{ call.outcome === 'CREATED' ? '已入账' : call.outcome === 'ERROR' ? '失败' : '成功' }}</span></div><small>{{ formatTime(call.createdAt) }}<template v-if="call.ledgerId"> · {{ ledgerName(call.ledgerId) }}</template><template v-if="call.transactionId"> · 账目 #{{ call.transactionId }}</template></small><code v-if="call.errorCode">{{ call.errorCode }}</code></li></ol>
      <p class="muted-text">显示最近 100 条。账目创建来源长期保留，其他调用记录保留 90 天。</p>
    </UiModal>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onDeactivated, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { Copy, PlugZap, Plus } from 'lucide-vue-next'
import PageHeader from '@/components/PageHeader.vue'
import UiModal from '@/components/UiModal.vue'
import MessageBanner from '@/components/MessageBanner.vue'
import { useLedgerStore } from '@/stores/ledger'
import { useConfirm } from '@/composables/useConfirm'
import { useMessage } from '@/composables/useMessage'
import { mcpApi, type McpConnection, type McpIssued, type McpCall } from '@/api/mcp'

const ledger = useLedgerStore()
const { confirm } = useConfirm()
const { message, showSuccess, showError, clear } = useMessage()
const { message: modalMessage, showError: modalError, clear: clearModal } = useMessage()
const { message: secretMessage, show: secretNotice, clear: clearSecretNotice } = useMessage()
const connections = ref<McpConnection[]>([])
const loading = ref(false), loadFailed = ref(false), busy = ref(false)
const createOpen = ref(false), secretOpen = ref(false), callsOpen = ref(false)
const issued = ref<McpIssued | null>(null)
const rotating = ref<McpConnection | null>(null)
// Keep the same modal history entry when creation advances to one-time credentials.
const connectionOpen = computed({ get: () => createOpen.value || secretOpen.value,
  set: (open: boolean) => { if (!open) { createOpen.value = false; secretOpen.value = false } } })
const calls = ref<McpCall[]>([]), callsName = ref(''), callsId = ref('')
const callsLoading = ref(false), callsFailed = ref(false)
const form = reactive({ name: '', ledgerIds: [] as number[], defaultLedgerId: null as number | null, writable: false, expiresInDays: 90 })
const selectedLedgers = computed(() => ledger.state.ledgers.filter(item => form.ledgerIds.includes(item.id)))
const endpoint = new URL('/mcp', window.location.origin).href
const httpConfig = JSON.stringify({ mcpServers: { accounting: { type: 'http', url: endpoint, headers: { Authorization: 'Bearer <专用令牌>' } } } }, null, 2)
const stdioConfig = JSON.stringify({ mcpServers: { accounting: { command: 'node', args: ['/absolute/path/to/accounting-assistant-opensource/mcp/bridge.mjs'], env: { ACCOUNTING_MCP_URL: endpoint, ACCOUNTING_MCP_TOKEN: '<专用令牌>' } } } }, null, 2)
let active = true
let generation = 0

watch(() => form.ledgerIds.slice(), ids => { if (form.defaultLedgerId && !ids.includes(form.defaultLedgerId)) form.defaultLedgerId = null })
watch(secretOpen, open => { if (!open) { issued.value = null; clearSecretNotice() } })
onActivated(() => { active = true; void load() })
function clearSensitive() { active = false; generation++; issued.value = null; secretOpen.value = false; createOpen.value = false; callsOpen.value = false }
onDeactivated(clearSensitive)
onBeforeUnmount(clearSensitive)

async function load() {
  loading.value = true; loadFailed.value = false
  try { const result = await mcpApi.list(); if (active) connections.value = result }
  catch (error) { if (active) { loadFailed.value = true; showError(error) } }
  finally { loading.value = false }
}
function openCreate() {
  clearModal()
  rotating.value = null
  Object.assign(form, { name: '', ledgerIds: [], defaultLedgerId: null, writable: false, expiresInDays: 90 })
  createOpen.value = true
}
async function create() {
  if (busy.value) return
  busy.value = true; clearModal()
  const owner = generation
  try {
    const result = await mcpApi.create({ ...form, name: form.name.trim(), ledgerIds: [...form.ledgerIds] })
    if (!active || owner !== generation) return
    issued.value = result; createOpen.value = false; secretOpen.value = true
    await load()
  } catch (error) { if (active && owner === generation) modalError(error) }
  finally { busy.value = false }
}
async function revoke(connection: McpConnection) {
  if (!await confirm({ title: '撤销连接', message: `撤销「${connection.name}」后，该令牌将无法继续访问账本。`, confirmText: '撤销连接', danger: true })) return
  busy.value = true
  try { await mcpApi.revoke(connection.id); showSuccess('连接已撤销'); await load() }
  catch (error) { showError(error) } finally { busy.value = false }
}
function openRotate(connection: McpConnection) { clearModal(); rotating.value = connection; createOpen.value = true }
async function rotate() {
  if (busy.value || !rotating.value) return
  busy.value = true
  const owner = generation
  try {
    const result = await mcpApi.rotate(rotating.value.id)
    if (!active || owner !== generation) return
    issued.value = result; createOpen.value = false; secretOpen.value = true; await load()
  } catch (error) { if (active && owner === generation) modalError(error) } finally { busy.value = false }
}
async function showCalls(connection: McpConnection) { callsId.value = connection.id; callsName.value = connection.name; calls.value = []; callsOpen.value = true; await loadCalls() }
async function loadCalls() {
  callsLoading.value = true; callsFailed.value = false
  const id = callsId.value
  try { const result = await mcpApi.calls(id); if (active && id === callsId.value) calls.value = result }
  catch { callsFailed.value = true } finally { callsLoading.value = false }
}
async function copy(value: string) {
  try { await navigator.clipboard.writeText(value); if (secretOpen.value) secretNotice('success', '已复制'); else showSuccess('已复制') }
  catch { if (secretOpen.value) secretNotice('error', '复制失败，请选中文本手动复制'); else showError(null, '复制失败，请选中文本手动复制') }
}
function ledgerName(id: number) { return ledger.state.ledgers.find(l => l.id === id)?.name || `账本 #${id}（已不可访问）` }
function statusLabel(status: string) { return ({ ACTIVE: '可用', REVOKED: '已撤销', EXPIRED: '已过期' } as Record<string, string>)[status] || status }
function formatTime(value: string) { return new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) }
function toolLabel(tool: string) { return ({ accounting_get_context: '获取可用账本', accounting_get_ledger_options: '获取记账选项', accounting_create_transaction: '新增账目', accounting_list_transactions: '查询账单', accounting_get_transaction: '查看账目详情', accounting_get_statistics: '查询统计' } as Record<string, string>)[tool] || tool }
</script>

<style scoped>
.connection-guide { display: flex; gap: 18px; margin-bottom: 28px; }
.guide-icon { width: 48px; height: 48px; flex: 0 0 auto; display: grid; place-items: center; border-radius: 14px; background: #dcedf7; }
.guide-copy { min-width: 0; }
.guide-copy h2, .connection-card h2 { margin: 0; font-size: 18px; }
.guide-copy p { line-height: 1.7; }
.endpoint-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.endpoint-row code { overflow-wrap: anywhere; }
.connection-list-heading, .connection-title { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.connection-list-heading h2 { font-size: 18px; }
.connection-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 350px), 1fr)); gap: 18px; }
.connection-card { min-width: 0; }
.connection-title h2 { overflow-wrap: anywhere; }
.connection-meta { display: grid; gap: 10px; margin: 20px 0; font-size: 13px; }
.connection-meta > div { display: flex; justify-content: space-between; gap: 12px; }
.connection-meta dt { color: var(--text-muted, #777); flex-shrink: 0; }
.connection-meta dd { margin: 0; text-align: right; overflow-wrap: anywhere; }
.ledger-choices { border: 1px solid var(--border, #ded8ce); border-radius: 12px; padding: 14px; }
.ledger-choices label { display: flex; gap: 10px; align-items: center; padding: 8px 0; }
.ledger-choices small { margin-left: auto; color: var(--text-muted, #777); }
.ledger-choices input { accent-color: #eb5e28; }
.token-value { font-family: monospace; overflow-wrap: anywhere; resize: vertical; }
.bridge-help p { line-height: 1.7; }
.bridge-help summary { cursor: pointer; font-weight: 600; }
.bridge-help pre { background: #f4f1eb; border-radius: 12px; padding: 16px; overflow: auto; max-width: 100%; font-size: 12px; }
.call-list { list-style: none; margin: 0; padding: 0; }
.call-list li { display: grid; gap: 8px; padding: 14px 0; border-bottom: 1px solid var(--border, #ded8ce); }
.call-list li > div { display: flex; justify-content: space-between; gap: 10px; }
@media (max-width: 600px) { .guide-icon { display: none; } .connection-meta > div { flex-wrap: wrap; } .connection-card .row-actions { flex-wrap: wrap; } }
</style>
