<template>
  <div class="page">
    <header class="topbar">
      <h1>🚨 110 告警系统 · 警情处置台</h1>
      <div class="operator">
        <label>操作人（X-Operator-Id）</label>
        <input v-model="operatorId.value" placeholder="op-web" />
      </div>
    </header>

    <div v-if="toast" :class="['toast', toast.kind]">{{ toast.text }}</div>

    <nav class="tabs">
      <button v-for="t in tabs" :key="t.key" :class="{ active: active === t.key }" @click="active = t.key">
        {{ t.label }}
      </button>
    </nav>

    <!-- ============ 登记警单 ============ -->
    <section v-show="active === 'create'" class="card">
      <h2>警单登记 <small class="hint">POST /api/v1/incidents · 同 channel+sourceId 防重 · Idempotency-Key 自动生成</small></h2>
      <form class="grid" @submit.prevent="createIncident">
        <label>接入渠道
          <select v-model="createF.channel">
            <option>PHONE</option><option>SMS</option><option>APP</option><option>DEVICE</option>
          </select>
        </label>
        <label>来源编号 sourceId *
          <input v-model="createF.sourceId" required maxlength="64" placeholder="如 phone-20261101001" />
        </label>
        <label>优先级
          <select v-model="createF.priority">
            <option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>CRITICAL</option>
          </select>
        </label>
        <label>警情类型 *
          <input v-model="createF.type" required maxlength="32" placeholder="ROBBERY" />
        </label>
        <label class="span2">描述
          <input v-model="createF.description" maxlength="512" placeholder="接警描述…" />
        </label>
        <fieldset class="span2">
          <legend>初始定位（可选，提供即参与推荐圈选）</legend>
          <label>经度 x<input v-model.number="createF.x" type="number" step="any" placeholder="116.397428" /></label>
          <label>纬度 y<input v-model.number="createF.y" type="number" step="any" placeholder="39.90923" /></label>
          <label>定位源
            <select v-model="createF.source"><option>GPS</option><option>CELL</option><option>AGPS</option><option>WIFI</option></select>
          </label>
          <span class="hint">observedAt 由前端自动填充当前时间</span>
        </fieldset>
        <button class="primary span2" type="submit">登记警单</button>
      </form>
      <pre v-if="lastResult.create" class="result">{{ lastResult.create }}</pre>
    </section>

    <!-- ============ 警单列表与操作 ============ -->
    <section v-show="active === 'detail'" class="card">
      <h2>警单操作 <small class="hint">左侧：GET /api/v1/incidents?status=（更新时间倒序）· 右侧：GET/PATCH/close</small></h2>
      <div class="detail-layout">
        <!-- 左：警单列表 -->
        <div class="list-pane">
          <div class="row" style="margin-bottom:8px">
            <select v-model="listF.status" style="flex:1">
              <option value="">全部状态</option>
              <option>PENDING_VERIFICATION</option><option>ACCEPTED</option><option>DISPATCHING</option>
              <option>PENDING_REVIEW</option><option>CLOSED</option><option>CANCELLED</option>
            </select>
            <button @click="loadList" :disabled="listF.loading">刷新</button>
          </div>
          <div style="max-height:460px; overflow-y:auto;">
            <table class="tbl" style="font-size:12px">
              <thead><tr><th>来源</th><th>状态</th><th>级别</th><th>版本</th></tr></thead>
              <tbody>
                <tr v-for="i in listF.items" :key="i.incidentId"
                    :class="{ selected: i.incidentId === detail.incidentId }"
                    style="cursor:pointer" @click="selectIncident(i.incidentId)">
                  <td :title="i.incidentId">{{ i.sourceId }}</td>
                  <td><span :class="['st', i.status]">{{ i.status }}</span></td>
                  <td>{{ i.priority }}</td>
                  <td>v{{ i.version }}</td>
                </tr>
                <tr v-if="!listF.items.length"><td colspan="4" style="text-align:center;color:#93a1b5">{{ listF.loading ? '加载中…' : '无警单' }}</td></tr>
              </tbody>
            </table>
          </div>
          <div class="hint" style="margin-top:6px">
            命中 {{ listF.items.length }} / 共 {{ listF.total }} 条
            <template v-if="listF.total > listF.items.length">
              — <a href="javascript:void(0)" @click="listMore">继续加载</a>
            </template>
          </div>
        </div>

        <!-- 右：详情 + 操作 -->
        <div class="detail-pane">
          <form class="row" @submit.prevent="loadIncident">
            <input v-model="detail.incidentId" placeholder="警单 incidentId（UUID），点击左侧列表自动填入" />
            <button class="primary" type="submit">查询</button>
          </form>
          <template v-if="detail.data">
            <pre class="result">{{ detail.data }}</pre>
            <div class="grid actions">
              <fieldset class="span2">
                <legend>核实 / 修改 / 撤销 / 重开 · PATCH /api/v1/incidents/{id}（乐观锁）</legend>
                <label>动作
                  <select v-model="patchF.action">
                    <option value="VERIFY">VERIFY（核实受理，需 verifiedAddress+reason）</option>
                    <option value="UPDATE">UPDATE（字段修改）</option>
                    <option value="CANCEL">CANCEL（撤销警单）</option>
                    <option value="REOPEN">REOPEN（重开）</option>
                  </select>
                </label>
                <label>expectedVersion *
                  <input v-model.number="patchF.expectedVersion" type="number" min="0" required />
                </label>
                <label>reason *
                  <input v-model="patchF.reason" placeholder="核实/撤销/重开必填" />
                </label>
                <label>verifiedAddress<br />
                  <input v-model="patchF.verifiedAddress" placeholder="核实受理必填" />
                </label>
                <label>优先级
                  <select v-model="patchF.priority"><option value="">(不变)</option><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select>
                </label>
                <label class="span2">描述
                  <input v-model="patchF.description" placeholder="覆盖描述（可空）" />
                </label>
                <button class="primary" @click.prevent="patchIncident" :disabled="busy">提交 PATCH</button>
              </fieldset>
              <fieldset class="span2">
                <legend>结案 · POST /api/v1/incidents/{id}/close</legend>
                <label>expectedVersion *
                  <input v-model.number="closeF.expectedVersion" type="number" min="0" required />
                </label>
                <label class="span2">结案结论 *
                  <input v-model="closeF.conclusion" required placeholder="处置结论摘要" />
                </label>
                <button class="primary" @click.prevent="closeIncident" :disabled="busy">结案</button>
              </fieldset>
            </div>
            <div class="hint">派遣下发/回执请到「派遣/回执」页签；列表数据每次操作后自动刷新</div>
            <pre v-if="lastResult.mutation" class="result">{{ lastResult.mutation }}</pre>
          </template>
        </div>
      </div>
    </section>

    <!-- ============ 派遣与回执 ============ -->
    <section v-show="active === 'dispatch'" class="card">
      <h2>派遣 / 回执 / 取消 <small class="hint">原子预占 20 额度 · deadline 范围 30–600s · 终端不得自声明 TIMEOUT</small></h2>
      <form class="grid" @submit.prevent="createDispatch">
        <label class="span2">警单 incidentId *<input v-model="dispatchF.incidentId" required /></label>
        <label>expectedVersion *<input v-model.number="dispatchF.expectedVersion" type="number" min="0" required /></label>
        <label>接单时限 deadlineSeconds<input v-model.number="dispatchF.deadlineSeconds" type="number" min="30" max="600" placeholder="60" /></label>
        <label class="span2">警力 unitId（逗号分隔,1–5 个）*
          <input v-model="dispatchF.unitIdsCsv" required placeholder="unit-a,unit-b" />
        </label>
        <label class="span2">备注<input v-model="dispatchF.note" placeholder="派遣备注" /></label>
        <button class="primary span2" type="submit" :disabled="busy">创建派遣</button>
      </form>

      <form class="grid" @submit.prevent="sendReceipt">
        <legend>终端回执 · POST /api/v1/dispatches/{id}/receipts</legend>
        <label>dispatchId *<input v-model="receiptF.dispatchId" required /></label>
        <label>commandId *<input v-model="receiptF.commandId" required /></label>
        <label>目标状态 *
          <select v-model="receiptF.targetStatus">
            <option>ACCEPTED</option><option>REJECTED</option><option>ENROUTE</option>
            <option>ARRIVED</option><option>FEEDBACK_DONE</option>
          </select>
        </label>
        <label>expectedVersion *<input v-model.number="receiptF.expectedVersion" type="number" min="0" required /></label>
        <label class="span2">备注<input v-model="receiptF.note" /></label>
        <button class="primary span2" type="submit" :disabled="busy">上报回执</button>
      </form>

      <form class="grid" @submit.prevent="cancelDispatch">
        <legend>取消派遣 · POST /api/v1/dispatches/{id}/cancel</legend>
        <label>dispatchId *<input v-model="cancelF.dispatchId" required /></label>
        <label>expectedVersion *<input v-model.number="cancelF.expectedVersion" type="number" min="0" required /></label>
        <label class="span2">取消理由 *<input v-model="cancelF.reason" required /></label>
        <button class="primary span2" type="submit" :disabled="busy">取消派遣</button>
      </form>
      <pre v-if="lastResult.dispatch" class="result">{{ lastResult.dispatch }}</pre>
    </section>

    <!-- ============ 警力 ============ -->
    <section v-show="active === 'unit'" class="card">
      <h2>警力状态登记 <small class="hint">POST /api/v1/units · upsert 按 unitId 幂等 · 预占期间禁止抬升额度（R04）</small></h2>
      <form class="grid" @submit.prevent="upsertUnit">
        <label>unitId *<input v-model="unitF.unitId" required maxlength="64" /></label>
        <label>名称 *<input v-model="unitF.name" required maxlength="64" /></label>
        <label>可用额度 availability *<input v-model.number="unitF.availability" type="number" min="0" max="100" required /></label>
        <label>能力（逗号分隔）<input v-model="unitF.capabilitiesCsv" placeholder="PATROL,TRAFFIC" /></label>
        <label>经度 x<input v-model.number="unitF.x" type="number" step="any" /></label>
        <label>纬度 y<input v-model.number="unitF.y" type="number" step="any" /></label>
        <label>定位源
          <select v-model="unitF.source"><option>GPS</option><option>CELL</option><option>AGPS</option><option>WIFI</option></select>
        </label>
        <div class="span2 hint">提供坐标即携带定位（observedAt 自动填充）；location.observedAt 超过 120s 未更新将标记 UNIT_LOCATION_STALE</div>
        <button class="primary span2" type="submit" :disabled="busy">登记警力</button>
      </form>
      <pre v-if="lastResult.unit" class="result">{{ lastResult.unit }}</pre>
    </section>

    <!-- ============ 推荐预览 ============ -->
    <section v-show="active === 'preview'" class="card">
      <h2>警力推荐预览 <small class="hint">POST /api/v1/incidents/recommendations-preview · incidentId 与坐标二选一 · Redis 缓存 15s</small></h2>
      <form class="grid" @submit.prevent="preview">
        <label>警单 incidentId<input v-model="previewF.incidentId" placeholder="以警单定位为中心" /></label>
        <label>半径 radiusMeters<input v-model.number="previewF.radiusMeters" type="number" min="1" max="10000" placeholder="500" /></label>
        <label>中心经度 x<input v-model.number="previewF.x" type="number" step="any" /></label>
        <label>中心纬度 y<input v-model.number="previewF.y" type="number" step="any" /></label>
        <label>定位源
          <select v-model="previewF.source"><option>GPS</option><option>CELL</option><option>AGPS</option><option>WIFI</option></select>
        </label>
        <button class="primary" type="submit" :disabled="busy">预览推荐</button>
      </form>
      <table v-if="previewItems.length" class="tbl">
        <thead><tr><th>unitId</th><th>距离 (m)</th><th>可用额度</th><th>定位上报时间</th><th>告警</th></tr></thead>
        <tbody>
          <tr v-for="i in previewItems" :key="i.unitId">
            <td>{{ i.unitId }}</td><td>{{ i.distanceMeters }}</td><td>{{ i.availability }}</td>
            <td>{{ i.observedAt }}</td><td>{{ (i.warnings || []).join('、') || '—' }}</td>
          </tr>
        </tbody>
      </table>
      <pre v-if="lastResult.preview" class="result">{{ lastResult.preview }}</pre>
    </section>

    <!-- ============ 审计 ============ -->
    <section v-show="active === 'audit'" class="card">
      <h2>审计查询 <small class="hint">GET /api/v1/audits/{targetType}/{targetId}</small></h2>
      <form class="row" @submit.prevent="audit">
        <input v-model="auditF.targetId" placeholder="目标对象 id（警单/派遣 UUID）" required />
        <select v-model="auditF.targetType">
          <option>INCIDENT</option><option>DISPATCH</option><option>UNIT</option>
        </select>
        <button class="primary" type="submit">查询</button>
      </form>
      <table v-if="auditItems.length" class="tbl">
        <thead><tr><th>时间</th><th>操作人</th><th>动作</th><th>明细</th></tr></thead>
        <tbody>
          <tr v-for="(a, idx) in auditItems" :key="idx">
            <td>{{ a.occurredAt }}</td><td>{{ a.actorId }}</td><td>{{ a.action }}</td><td>{{ a.detail }}</td>
          </tr>
        </tbody>
      </table>
      <pre v-if="lastResult.audit" class="result">{{ lastResult.audit }}</pre>
    </section>

    <footer class="foot">
      前端 alarm-web（Vue3+Vite，nginx 静态托管 + /api 反代）→ alarm-server（k8s profile，MySQL/Redis/Kafka = middleware）
    </footer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { api, errText } from './api'
import { operatorId } from './state'

const tabs = [
  { key: 'create', label: '① 登记警单' },
  { key: 'detail', label: '② 警单操作' },
  { key: 'dispatch', label: '③ 派遣/回执' },
  { key: 'unit', label: '④ 警力登记' },
  { key: 'preview', label: '⑤ 推荐预览' },
  { key: 'audit', label: '⑥ 审计' }
]
const active = ref('create')
const busy = ref(false)
const toast = ref(null)

function showToast(text, kind = 'ok') {
  toast.value = { text: `${text}`, kind }
  setTimeout(() => { toast.value = null }, 5000)
}
const nowIso = () => new Date().toISOString()

const lastResult = reactive({ create: '', mutation: '', dispatch: '', unit: '', preview: '', audit: '' })
const put = (slot, value) => { lastResult[slot] = value }

/* ---------- 登记警单 ---------- */
const createF = reactive({
  channel: 'PHONE', sourceId: '', priority: 'HIGH', type: 'ROBBERY',
  description: '', x: null, y: null, source: 'GPS'
})
async function createIncident() {
  busy.value = true
  const body = {
    channel: createF.channel,
    sourceId: createF.sourceId,
    priority: createF.priority,
    type: createF.type,
    description: createF.description || null,
    location: (createF.x !== null && createF.y !== null)
      ? { x: createF.x, y: createF.y, source: createF.source, observedAt: nowIso() }
      : null
  }
  try {
    const { data, status } = await api.post('/incidents', body)
    put('create', `HTTP ${status}\n` + JSON.stringify(data, null, 2))
    showToast(`警单已登记：${data.incidentId}（状态 ${data.status} v${data.version}）`)
    loadList()
  } catch (e) {
    showToast(errText(e), 'err')
    put('create', 'ERROR: ' + errText(e))
  } finally { busy.value = false }
}

/* ---------- 警单查询 / PATCH / 结案 ---------- */
const detail = reactive({ incidentId: '', data: null })
const patchF = reactive({ action: 'VERIFY', expectedVersion: 0, reason: '', verifiedAddress: '', priority: '', description: '' })
const closeF = reactive({ expectedVersion: 0, conclusion: '' })

/* ---------- 警单列表（左栏） ---------- */
const PAGE_SIZE = 50
const listF = reactive({ items: [], total: 0, status: '', loading: false })
async function loadList() {
  listF.loading = true
  try {
    const params = { limit: PAGE_SIZE, offset: 0 }
    if (listF.status) params.status = listF.status
    const { data } = await api.get('/incidents', { params })
    listF.items = data.items || []
    listF.total = data.total
  } catch (e) {
    showToast('警单列表加载失败：' + errText(e), 'err')
  } finally { listF.loading = false }
}
async function listMore() {
  listF.loading = true
  try {
    const params = { limit: PAGE_SIZE, offset: listF.items.length }
    if (listF.status) params.status = listF.status
    const { data } = await api.get('/incidents', { params })
    listF.items = listF.items.concat(data.items || [])
    listF.total = data.total
  } catch (e) {
    showToast(errText(e), 'err')
  } finally { listF.loading = false }
}
function selectIncident(incidentId) {
  detail.incidentId = incidentId
  loadIncident()
}
/** 操作面板数据变化后刷新列表与详情（保持乐观锁 version 最新）。 */
async function refreshAfterMutation() {
  loadList()
  if (detail.incidentId) await loadIncident()
}
onMounted(loadList)

async function loadIncident() {
  busy.value = true
  try {
    const { data } = await api.get(`/incidents/${detail.incidentId.trim()}`)
    detail.data = JSON.stringify(data, null, 2)
    patchF.expectedVersion = closeF.expectedVersion = data.version
    showToast(`已加载警单，当前版本 v${data.version}`)
  } catch (e) {
    showToast(errText(e), 'err'); detail.data = null
  } finally { busy.value = false }
}

async function patchIncident() {
  busy.value = true
  const body = { action: patchF.action, expectedVersion: patchF.expectedVersion, reason: patchF.reason || null }
  if (patchF.verifiedAddress) body.verifiedAddress = patchF.verifiedAddress
  if (patchF.priority) body.priority = patchF.priority
  if (patchF.description) body.description = patchF.description
  try {
    const { data } = await api.patch(`/incidents/${detail.incidentId.trim()}`, body)
    put('mutation', JSON.stringify(data, null, 2))
    showToast(`PATCH 成功：${data.status} v${data.version}`)
    await refreshAfterMutation()
  } catch (e) {
    showToast(errText(e), 'err'); put('mutation', 'ERROR: ' + errText(e))
  } finally { busy.value = false }
}

async function closeIncident() {
  busy.value = true
  try {
    const { data } = await api.post(`/incidents/${detail.incidentId.trim()}/close`,
      { expectedVersion: closeF.expectedVersion, conclusion: closeF.conclusion })
    put('mutation', JSON.stringify(data, null, 2))
    showToast(`结案成功：${data.status} v${data.version}`)
    await refreshAfterMutation()
  } catch (e) {
    showToast(errText(e), 'err'); put('mutation', 'ERROR: ' + errText(e))
  } finally { busy.value = false }
}

/* ---------- 派遣 / 回执 / 取消 ---------- */
const dispatchF = reactive({ incidentId: '', expectedVersion: 0, unitIdsCsv: '', note: '', deadlineSeconds: 60 })
const receiptF = reactive({ dispatchId: '', commandId: '', targetStatus: 'ACCEPTED', expectedVersion: 0, note: '' })
const cancelF = reactive({ dispatchId: '', expectedVersion: 0, reason: '' })

async function createDispatch() {
  busy.value = true
  const unitIds = dispatchF.unitIdsCsv.split(',').map(s => s.trim()).filter(Boolean)
  const body = { expectedVersion: dispatchF.expectedVersion, unitIds, note: dispatchF.note || null,
                 deadlineSeconds: dispatchF.deadlineSeconds || null }
  try {
    const { data } = await api.post(`/incidents/${dispatchF.incidentId.trim()}/dispatches`, body)
    put('dispatch', JSON.stringify(data, null, 2))
    showToast(`派遣已创建：${data.dispatches.length} 个任务，incidentVersion=${data.incidentVersion}`)
  } catch (e) {
    showToast(errText(e), 'err'); put('dispatch', 'ERROR: ' + errText(e))
  } finally { busy.value = false }
}
async function sendReceipt() {
  busy.value = true
  try {
    const { data } = await api.post(`/dispatches/${receiptF.dispatchId.trim()}/receipts`,
      { commandId: receiptF.commandId.trim(), targetStatus: receiptF.targetStatus,
        expectedVersion: receiptF.expectedVersion, note: receiptF.note || null, occurredAt: nowIso() })
    put('dispatch', JSON.stringify(data, null, 2))
    showToast(`回执成功：${data.status} v${data.version}`)
  } catch (e) {
    showToast(errText(e), 'err'); put('dispatch', 'ERROR: ' + errText(e))
  } finally { busy.value = false }
}
async function cancelDispatch() {
  busy.value = true
  try {
    const { data } = await api.post(`/dispatches/${cancelF.dispatchId.trim()}/cancel`,
      { reason: cancelF.reason, expectedVersion: cancelF.expectedVersion })
    put('dispatch', JSON.stringify(data, null, 2))
    showToast(`已取消：${data.status} v${data.version}`)
  } catch (e) {
    showToast(errText(e), 'err'); put('dispatch', 'ERROR: ' + errText(e))
  } finally { busy.value = false }
}

/* ---------- 警力 ---------- */
const unitF = reactive({ unitId: '', name: '', availability: 100, capabilitiesCsv: '', x: null, y: null, source: 'GPS' })
async function upsertUnit() {
  busy.value = true
  const body = {
    unitId: unitF.unitId.trim(), name: unitF.name.trim(), availability: unitF.availability,
    capabilities: unitF.capabilitiesCsv.split(',').map(s => s.trim()).filter(Boolean),
    location: (unitF.x !== null && unitF.y !== null)
      ? { x: unitF.x, y: unitF.y, source: unitF.source, observedAt: nowIso() }
      : null
  }
  try {
    const { data } = await api.post('/units', body)
    put('unit', JSON.stringify(data, null, 2))
    showToast(`警力已登记：${data.unitId} v${data.version}`)
  } catch (e) {
    showToast(errText(e), 'err'); put('unit', 'ERROR: ' + errText(e))
  } finally { busy.value = false }
}

/* ---------- 推荐预览 ---------- */
const previewF = reactive({ incidentId: '', x: null, y: null, source: 'GPS', radiusMeters: 500 })
const previewItems = ref([])
async function preview() {
  busy.value = true
  const body = { remark: null, incidentId: previewF.incidentId.trim() || null,
                 radiusMeters: previewF.radiusMeters || null }
  if (previewF.incidentId.trim()) { delete body.remark }
  else if (previewF.x !== null && previewF.y !== null) {
    body.location = { x: previewF.x, y: previewF.y, source: previewF.source, observedAt: nowIso() }
  }
  try {
    const { data } = await api.post('/incidents/recommendations-preview', body)
    previewItems.value = data.items || []
    showToast(`推荐 ${previewItems.value.length} 个候选`)
  } catch (e) {
    showToast(errText(e), 'err'); put('preview', 'ERROR: ' + errText(e))
  } finally { busy.value = false }
}

/* ---------- 审计 ---------- */
const auditF = reactive({ targetType: 'INCIDENT', targetId: '' })
const auditItems = ref([])
async function audit() {
  busy.value = true
  try {
    const { data } = await api.get(`/audits/${auditF.targetType}/${auditF.targetId.trim()}`)
    auditItems.value = data || []
    showToast(`审计流水 ${auditItems.value.length} 条`)
  } catch (e) {
    showToast(errText(e), 'err'); put('audit', 'ERROR: ' + errText(e))
  } finally { busy.value = false }
}
</script>

<style>
* { box-sizing: border-box; }
body { margin: 0; font-family: "Segoe UI", "Microsoft YaHei", sans-serif; background: #f2f5f9; }
.page { max-width: 1080px; margin: 0 auto; padding: 16px 20px 40px; }
.topbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.topbar h1 { font-size: 20px; margin: 8px 0; }
.operator { display: flex; align-items: center; gap: 8px; }
.operator label { font-size: 13px; color: #555; }
.operator input { width: 140px; padding: 6px 8px; }
.tabs { display: flex; gap: 6px; flex-wrap: wrap; margin: 12px 0; }
.tabs button { border: 1px solid #c9d4e3; background: #fff; padding: 8px 14px; border-radius: 6px; cursor: pointer; font-size: 14px; }
.tabs button.active { background: #1f5eff; border-color: #1f5eff; color: #fff; }
.card { background: #fff; border: 1px solid #dbe3ee; border-radius: 10px; padding: 16px 20px; margin-bottom: 16px; }
.card h2 { font-size: 16px; margin: 4px 0 14px; }
.hint { font-size: 12px; color: #7a879c; font-weight: 400; }
.grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px 14px; }
.grid label { display: flex; flex-direction: column; font-size: 13px; color: #444; gap: 4px; }
.grid legend { font-size: 13px; font-weight: 600; color: #1f5eff; margin-bottom: 6px; }
fieldset { border: 1px dashed #c9d4e3; border-radius: 8px; padding: 10px 12px; margin: 0; display: flex; flex-direction: column; gap: 10px; }
.grid .span2 { grid-column: span 4; display: flex; gap: 14px; flex-wrap: wrap; }
.grid label, fieldset label { flex: 1 1 180px; }
.row { display: flex; gap: 10px; margin-bottom: 10px; }
.row input { flex: 1; }
input, select { padding: 7px 9px; border: 1px solid #c9d4e3; border-radius: 6px; font-size: 13px; background: #fff; }
input:disabled, select:disabled { background: #eef1f6; }
button { padding: 8px 16px; border-radius: 6px; border: 1px solid #c9d4e3; background: #fff; cursor: pointer; font-size: 14px; }
button.primary { background: #1f5eff; border-color: #1f5eff; color: #fff; }
button:disabled { opacity: .55; cursor: not-allowed; }
.result { background: #0d1b2a; color: #9ad1ff; padding: 12px; border-radius: 8px; font-size: 12px; overflow-x: auto; max-height: 320px; white-space: pre-wrap; }
.tbl { width: 100%; border-collapse: collapse; font-size: 13px; margin-top: 10px; }
.tbl th, .tbl td { border: 1px solid #dbe3ee; padding: 6px 10px; text-align: left; }
.tbl th { background: #f2f6fc; }
.detail-layout { display: grid; grid-template-columns: 340px 1fr; gap: 18px; align-items: start; }
@media (max-width: 900px) { .detail-layout { grid-template-columns: 1fr; } }
.list-pane { background: #fbfcfe; border: 1px solid #dbe3ee; border-radius: 8px; padding: 10px; }
.list-pane .tbl { margin-top: 0; }
.list-pane td, .list-pane th { padding: 5px 6px; }
.tbl tbody tr.selected { background: #e8f0ff; outline: 1px solid #1f5eff inset; }
.st { font-size: 11px; padding: 2px 6px; border-radius: 4px; white-space: nowrap; }
.st.PENDING_VERIFICATION { background: #fff3d6; color: #8a6100; }
.st.ACCEPTED { background: #e1f0ff; color: #0b5cc7; }
.st.DISPATCHING { background: #ffe8ec; color: #c01b41; }
.st.PENDING_REVIEW { background: #ece7ff; color: #5b34c4; }
.st.CLOSED { background: #e2efe4; color: #1f7a33; }
.st.CANCELLED { background: #ececec; color: #666; }
.tbl th, .tbl td { border: 1px solid #dbe3ee; padding: 6px 10px; text-align: left; }
.tbl th { background: #f2f6fc; }
.toast { position: fixed; top: 18px; left: 50%; transform: translateX(-50%); padding: 10px 22px; border-radius: 8px; font-size: 14px; z-index: 9; box-shadow: 0 4px 16px rgba(0,0,0,.18); }
.toast.ok { background: #123f1f; color: #b8fbd0; }
.toast.err { background: #4a1215; color: #ffd2d3; }
.foot { text-align: center; color: #93a1b5; font-size: 12px; margin-top: 22px; }
</style>
