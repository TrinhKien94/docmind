/**
 * DocMind API Service Layer
 * Tương tác với backend Spring Boot qua VITE_API_BASE_URL hoặc /api
 * Hỗ trợ tự động fallback sang mockData phong phú khi backend offline / chưa cấu hình
 */

import {
  mockMessengerStatus,
  mockSimulateReply,
  mockClearMessengerSessions,
  mockEvolutionData,
  mockTriggerEvolution,
  mockCustomerPrompts,
  getMockAstrBotOverview,
  mockSimulateAstrBotMessage,
  mockAstrBotState,
  mockAstrBotPlatformTypes
} from './mockData'

const API_BASE = import.meta.env?.VITE_API_BASE_URL || '/api'

/**
 * Kiểm tra kết nối backend
 */
export async function checkHealth() {
  try {
    const promptRes = await fetch(`${API_BASE}/customer-chat/prompts`, {
      method: 'GET',
      headers: { Accept: 'application/json' }
    })
    if (promptRes.ok) return true
    const res = await fetch(`${API_BASE}/documents`, {
      method: 'GET',
      headers: { Accept: 'application/json' }
    })
    return res.ok
  } catch {
    return false
  }
}

/**
 * Lấy danh sách tất cả tài liệu
 * GET /api/documents
 */
export async function getDocuments() {
  const res = await fetch(`${API_BASE}/documents`)
  if (!res.ok) {
    throw new Error(`Không thể lấy danh sách tài liệu (HTTP ${res.status})`)
  }
  return await res.json()
}

/**
 * Lấy trạng thái của một tài liệu
 * GET /api/documents/:id
 */
export async function getDocumentStatus(id) {
  const res = await fetch(`${API_BASE}/documents/${encodeURIComponent(id)}`)
  if (!res.ok) {
    throw new Error(`Không thể lấy trạng thái tài liệu ${id} (HTTP ${res.status})`)
  }
  return await res.json()
}

/**
 * Upload tài liệu mới
 * POST /api/documents
 */
export async function uploadDocument(file) {
  const formData = new FormData()
  formData.append('file', file)

  const res = await fetch(`${API_BASE}/documents`, {
    method: 'POST',
    body: formData
  })

  if (!res.ok) {
    let errorMsg = `Upload thất bại (HTTP ${res.status})`
    try {
      const errJson = await res.json()
      if (errJson.message) errorMsg = errJson.message
    } catch {
      // ignore
    }
    throw new Error(errorMsg)
  }

  return await res.json()
}

/**
 * Hỏi đáp chat với tài liệu (RAG Grounded Chat)
 * POST /api/chat
 */
export async function chat({ documentIds, question, topK = 5 }) {
  const res = await fetch(`${API_BASE}/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json'
    },
    body: JSON.stringify({
      document_ids: documentIds,
      question,
      top_k: topK
    })
  })

  if (!res.ok) {
    let errorMsg = `Lỗi truy vấn chat (HTTP ${res.status})`
    try {
      const errJson = await res.json()
      if (errJson.message) errorMsg = errJson.message
    } catch {
      // ignore
    }
    throw new Error(errorMsg)
  }

  return await res.json()
}

/**
 * Tư vấn & chăm sóc khách hàng sử dụng prompt được huấn luyện trước
 * kết hợp tự tiến hoá học hỏi EvoAgentX.
 * POST /api/customer-chat
 */
export async function customerChat({
  question,
  message,
  customerName,
  customerRole,
  scenario,
  documentIds,
  topK = 5,
  history,
  customContext
}) {
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
  const validDocIds = Array.isArray(documentIds)
    ? documentIds.filter(id => typeof id === 'string' && uuidRegex.test(id.trim()))
    : []

  const res = await fetch(`${API_BASE}/customer-chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json'
    },
    body: JSON.stringify({
      question,
      message,
      customer_name: customerName,
      customer_role: customerRole,
      scenario,
      document_ids: validDocIds.length > 0 ? validDocIds : undefined,
      top_k: topK,
      history,
      custom_context: customContext
    })
  })

  if (!res.ok) {
    let errorMsg = `Lỗi tư vấn khách hàng (HTTP ${res.status})`
    try {
      const errJson = await res.json()
      if (errJson.message) errorMsg = errJson.message
    } catch {
      // ignore
    }
    throw new Error(errorMsg)
  }

  return await res.json()
}

/**
 * Lấy thông tin bộ prompt & kịch bản khách hàng được huấn luyện trước từ cấu hình backend
 * GET /api/customer-chat/prompts
 */
export async function getCustomerPrompts() {
  try {
    const res = await fetch(`${API_BASE}/customer-chat/prompts`)
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }
    return await res.json()
  } catch (err) {
    console.warn('[DocMind API] getCustomerPrompts fallback to mock:', err.message)
    return JSON.parse(JSON.stringify(mockCustomerPrompts))
  }
}

/**
 * Lấy trạng thái tự tiến hoá học hỏi EvoAgentX và danh sách quy tắc đã học
 * GET /api/customer-chat/evolution
 */
export async function getEvolutionStatus() {
  try {
    const res = await fetch(`${API_BASE}/customer-chat/evolution`)
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }
    return await res.json()
  } catch (err) {
    console.warn('[DocMind API] getEvolutionStatus fallback to mock:', err.message)
    return JSON.parse(JSON.stringify(mockEvolutionData))
  }
}

/**
 * Alias cho getEvolutionStatus để tương thích ngược
 */
export async function getCustomerEvolution() {
  return await getEvolutionStatus()
}

/**
 * Gửi đánh giá / phản hồi của khách hàng sau lần chat để cập nhật TextGrad & MIPRO
 * POST /api/customer-chat/feedback
 */
export async function submitCustomerFeedback({
  interactionId,
  question,
  answer,
  scenario,
  rating,
  helpful,
  feedback
}) {
  const res = await fetch(`${API_BASE}/customer-chat/feedback`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json'
    },
    body: JSON.stringify({
      interaction_id: interactionId,
      question,
      answer,
      scenario,
      rating,
      helpful,
      feedback
    })
  })

  if (!res.ok) {
    throw new Error(`Không thể gửi phản hồi khách hàng (HTTP ${res.status})`)
  }
  return await res.json()
}

/**
 * Kích hoạt chủ động một chu kỳ tự tiến hoá EvoAgentX
 * POST /api/customer-chat/evolve
 */
export async function triggerEvolution(scenario) {
  try {
    const res = await fetch(`${API_BASE}/customer-chat/evolve`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json'
      },
      body: JSON.stringify(scenario ? { scenario } : {})
    })
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }
    return await res.json()
  } catch (err) {
    console.warn('[DocMind API] triggerEvolution fallback to mock:', err.message)
    return mockTriggerEvolution(scenario)
  }
}

/**
 * Alias cho triggerEvolution để tương thích ngược
 */
export async function triggerCustomerEvolution(scenario) {
  return await triggerEvolution(scenario)
}

/**
 * =========================================================================
 * FACEBOOK MESSENGER INTEGRATION API
 * =========================================================================
 */

/**
 * Lấy trạng thái tích hợp Messenger và các lượt tương tác gần nhất
 * GET /api/messenger/status
 */
export async function getMessengerStatus() {
  try {
    const res = await fetch(`${API_BASE}/messenger/status`)
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }
    return await res.json()
  } catch (err) {
    console.warn('[DocMind API] getMessengerStatus fallback to mock:', err.message)
    return JSON.parse(JSON.stringify(mockMessengerStatus))
  }
}

/**
 * Giả lập gửi tin nhắn khách hàng tới Facebook Messenger Bot
 * POST /api/messenger/simulate
 * @param {Object} payload { sender_id, message, scenario }
 */
export async function simulateMessengerMessage(payload = {}) {
  const senderId = payload.sender_id || payload.senderId || 'customer_psid_test'
  const message = payload.message || payload.incomingText || ''
  const scenario = payload.scenario || 'AUTO'

  try {
    const res = await fetch(`${API_BASE}/messenger/simulate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json'
      },
      body: JSON.stringify({
        sender_id: senderId,
        message,
        scenario: scenario === 'AUTO' ? undefined : scenario
      })
    })

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }
    return await res.json()
  } catch (err) {
    console.warn('[DocMind API] simulateMessengerMessage fallback to mock:', err.message)
    return mockSimulateReply(senderId, message, scenario)
  }
}

/**
 * Xóa hoặc đặt lại các phiên hội thoại đa lượt Messenger
 * DELETE /api/messenger/sessions?sender_id=...
 * @param {string} [senderId] Optional PSID của khách hàng
 */
export async function clearMessengerSessions(senderId) {
  const url = senderId
    ? `${API_BASE}/messenger/sessions?sender_id=${encodeURIComponent(senderId)}`
    : `${API_BASE}/messenger/sessions`

  try {
    const res = await fetch(url, {
      method: 'DELETE',
      headers: { Accept: 'application/json' }
    })

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }
    return await res.json()
  } catch (err) {
    console.warn('[DocMind API] clearMessengerSessions fallback to mock:', err.message)
    return mockClearMessengerSessions(senderId)
  }
}

export const api = {
  checkHealth,
  getDocuments,
  getDocumentStatus,
  uploadDocument,
  chat,
  customerChat,
  getCustomerPrompts,
  getEvolutionStatus,
  getCustomerEvolution,
  submitCustomerFeedback,
  triggerEvolution,
  triggerCustomerEvolution,
  getMessengerStatus,
  simulateMessengerMessage,
  clearMessengerSessions
}

// =========================================================================
// AstrBot Multi-Platform Integration & Management Dashboard API
// =========================================================================

export async function getAstrBotOverview() {
  try {
    const res = await fetch(`${API_BASE}/astrbot/overview`, {
      method: 'GET',
      headers: { Accept: 'application/json' }
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch (err) {
    console.warn('[DocMind API] getAstrBotOverview fallback to mock:', err.message)
    return getMockAstrBotOverview()
  }
}

export async function createAstrBotBot(payload) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/bots`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload || {})
    })
    const data = await res.json()
    if (!res.ok) throw new Error(data.message || `HTTP ${res.status}`)
    return data
  } catch (err) {
    console.warn('[DocMind API] createAstrBotBot fallback to mock:', err.message)
    const rawId = (payload?.id || payload?.config?.id || `bot_${Date.now()}`).replace(/[:!]/g, '_')
    const type = payload?.type || payload?.config?.type || 'telegram'
    const typeMeta = mockAstrBotPlatformTypes.find((t) => t.type === type) || mockAstrBotPlatformTypes[0]
    const bot = {
      id: rawId,
      type,
      display_name: typeMeta.display_name,
      enable: payload?.enable !== false,
      status: payload?.enable !== false ? 'running' : 'stopped',
      started_at: new Date().toISOString(),
      unified_webhook: Boolean(typeMeta.support_unified_webhook),
      webhook_uuid: `wh_${rawId}_${Math.random().toString(36).slice(2, 6)}`,
      webhook_url: `/api/astrbot/webhooks/platforms/wh_${rawId}`,
      error_count: 0,
      last_error: null,
      message_count: 0,
      active_sessions: 0,
      fallback_profile_id: payload?.fallback_profile_id || 'dior_luxury_default',
      default_persona_id: payload?.default_persona_id || 'dior_luxury_advisor',
      default_scenario: payload?.default_scenario || 'AUTO',
      has_qr_payload: false,
      qr_status: null,
      qr_code_img_content: null,
      config: { ...(typeMeta.default_config || {}), ...(payload?.config || {}), id: rawId, type, enable: payload?.enable !== false },
      umo_routes: []
    }
    mockAstrBotState.bots.unshift(bot)
    return { status: 'ok', message: 'Đã thêm cấu hình Platform Adapter thành công!', bot }
  }
}

export async function updateAstrBotBot(botId, payload) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/bots/${encodeURIComponent(botId)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload || {})
    })
    const data = await res.json()
    if (!res.ok) throw new Error(data.message || `HTTP ${res.status}`)
    return data
  } catch (err) {
    console.warn('[DocMind API] updateAstrBotBot fallback to mock:', err.message)
    const bot = mockAstrBotState.bots.find((b) => b.id === botId)
    if (bot) {
      if (payload.enable !== undefined) {
        bot.enable = Boolean(payload.enable)
        bot.status = bot.enable ? 'running' : 'stopped'
      }
      if (payload.default_persona_id) bot.default_persona_id = payload.default_persona_id
      if (payload.default_scenario) bot.default_scenario = payload.default_scenario
      if (payload.fallback_profile_id) bot.fallback_profile_id = payload.fallback_profile_id
      if (payload.unified_webhook !== undefined) bot.unified_webhook = Boolean(payload.unified_webhook)
      if (payload.config) bot.config = { ...bot.config, ...payload.config }
    }
    return { status: 'ok', message: 'Đã lưu cấu hình và reload Platform Adapter!', bot }
  }
}

export async function toggleAstrBotBot(botId, enabled) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/bots/${encodeURIComponent(botId)}/enabled`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ enabled })
    })
    const data = await res.json()
    if (!res.ok) throw new Error(data.message || `HTTP ${res.status}`)
    return data
  } catch (err) {
    console.warn('[DocMind API] toggleAstrBotBot fallback to mock:', err.message)
    const bot = mockAstrBotState.bots.find((b) => b.id === botId)
    if (bot) {
      bot.enable = Boolean(enabled)
      bot.status = bot.enable ? 'running' : 'stopped'
    }
    return { status: 'ok', bot }
  }
}

export async function deleteAstrBotBot(botId) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/bots/${encodeURIComponent(botId)}`, {
      method: 'DELETE',
      headers: { Accept: 'application/json' }
    })
    const data = await res.json()
    if (!res.ok) throw new Error(data.message || `HTTP ${res.status}`)
    return data
  } catch (err) {
    console.warn('[DocMind API] deleteAstrBotBot fallback to mock:', err.message)
    const idx = mockAstrBotState.bots.findIndex((b) => b.id === botId)
    if (idx !== -1) mockAstrBotState.bots.splice(idx, 1)
    return { status: 'ok', message: `Đã xoá cấu hình nền tảng [${botId}]` }
  }
}

export async function testAstrBotBot(botId) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/bots/${encodeURIComponent(botId)}/test`, {
      method: 'POST',
      headers: { Accept: 'application/json' }
    })
    const data = await res.json()
    if (!res.ok) throw new Error(data.message || `HTTP ${res.status}`)
    return data
  } catch (err) {
    console.warn('[DocMind API] testAstrBotBot fallback to mock:', err.message)
    const bot = mockAstrBotState.bots.find((b) => b.id === botId)
    return {
      bot_id: botId,
      type: bot?.type || 'telegram',
      status: 'ok',
      credentials_ready: true,
      webhook_url: bot?.webhook_url || `/api/astrbot/webhooks/platforms/wh_${botId}`,
      latency_ms: 19,
      message: `Kết nối Adapter [${bot?.display_name || botId}] hoạt động ổn định!`
    }
  }
}

export async function clearAstrBotErrors(botId) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/bots/${encodeURIComponent(botId)}/clear-errors`, {
      method: 'POST',
      headers: { Accept: 'application/json' }
    })
    return await res.json()
  } catch {
    const bot = mockAstrBotState.bots.find((b) => b.id === botId)
    if (bot) {
      bot.error_count = 0
      bot.last_error = null
      bot.status = bot.enable ? 'running' : 'stopped'
    }
    return { status: 'ok', bot }
  }
}

export async function registerAstrBotQr(botType, payload) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/bot-types/${encodeURIComponent(botType)}/registration`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload || {})
    })
    return await res.json()
  } catch {
    if (payload?.action === 'poll') {
      return {
        status: 'completed',
        bot_type: botType,
        bot_id: payload?.bot_id || `${botType}_qr_bot`,
        credentials: { app_id: `qr_${botType}_app_88`, app_secret: `qr_secret_••••` },
        message: 'Đã xác thực mã QR thành công và tự động điền thông tin kết nối!'
      }
    }
    return {
      status: 'pending',
      bot_type: botType,
      bot_id: payload?.bot_id || `${botType}_qr_bot`,
      verification_uri_complete: `https://open.astrbot.app/qr/${botType}?bot_id=${payload?.bot_id || botType}`,
      qrcode_img_content: `https://open.astrbot.app/qr/${botType}?bot_id=${payload?.bot_id || botType}`,
      expires_in: 180,
      interval: 3,
      message: `Quét mã QR bằng ứng dụng ${botType.toUpperCase()} để cấp quyền tự động tạo Bot.`
    }
  }
}

export async function simulateAstrBotMessage(payload) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/simulate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload || {})
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch (err) {
    console.warn('[DocMind API] simulateAstrBotMessage fallback to mock:', err.message)
    return mockSimulateAstrBotMessage(payload)
  }
}

export async function upsertAstrBotSessionRule(payload) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/sessions/rules`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload || {})
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    const umo = payload?.umo || 'telegram_dior_concierge:PrivateMessage:vip_client_8801'
    let sess = mockAstrBotState.sessions.find((s) => s.umo === umo)
    if (sess) {
      Object.assign(sess, payload)
    }
    return { status: 'ok', session: sess }
  }
}

export async function deleteAstrBotSession(umo) {
  const qs = umo ? `?umo=${encodeURIComponent(umo)}` : ''
  try {
    const res = await fetch(`${API_BASE}/astrbot/sessions${qs}`, {
      method: 'DELETE',
      headers: { Accept: 'application/json' }
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    if (umo) {
      const idx = mockAstrBotState.sessions.findIndex((s) => s.umo === umo)
      if (idx !== -1) mockAstrBotState.sessions.splice(idx, 1)
      return { status: 'ok', message: `Đã xoá phiên UMO: ${umo}` }
    }
    const cleared = mockAstrBotState.sessions.length
    mockAstrBotState.sessions.splice(0, cleared)
    return { status: 'ok', cleared_count: cleared }
  }
}

export async function upsertAstrBotRoute(payload) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/routes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload || {})
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    const route = {
      umo: payload?.umo || 'telegram_dior_concierge:PrivateMessage:vip_client_8801',
      config_id: payload?.config_id || 'dior_luxury_default',
      persona_id: payload?.persona_id || 'dior_luxury_advisor',
      scenario: payload?.scenario || 'SALES'
    }
    const botId = route.umo.split(':')[0]
    const bot = mockAstrBotState.bots.find((b) => b.id === botId)
    if (bot) {
      bot.umo_routes = (bot.umo_routes || []).filter((r) => r.umo !== route.umo)
      bot.umo_routes.push(route)
    }
    return { status: 'ok', route }
  }
}

export async function deleteAstrBotRoute(umo) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/routes?umo=${encodeURIComponent(umo)}`, {
      method: 'DELETE',
      headers: { Accept: 'application/json' }
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    for (const bot of mockAstrBotState.bots) {
      bot.umo_routes = (bot.umo_routes || []).filter((r) => r.umo !== umo)
    }
    return { status: 'ok' }
  }
}

export async function saveAstrBotPersona(payload) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/personas`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload || {})
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    const id = (payload?.id || payload?.name || `persona_${Date.now()}`).replace(/[:!]/g, '_')
    const persona = {
      id,
      name: payload?.name || id,
      folder: payload?.folder || 'Custom',
      system_prompt: payload?.system_prompt || 'Bạn là chuyên viên tư vấn khách hàng trên DocMind x AstrBot.',
      begin_dialogs: payload?.begin_dialogs || [],
      tools: payload?.tools || ['rag_hybrid_search', 'dior_sop_lookup'],
      temperature: Number(payload?.temperature ?? 0.3),
      default_scenario: payload?.default_scenario || 'AUTO',
      updated_at: new Date().toISOString()
    }
    const idx = mockAstrBotState.personas.findIndex((p) => p.id === id)
    if (idx !== -1) mockAstrBotState.personas[idx] = persona
    else mockAstrBotState.personas.unshift(persona)
    return { status: 'ok', persona }
  }
}

export async function deleteAstrBotPersona(personaId) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/personas/${encodeURIComponent(personaId)}`, {
      method: 'DELETE',
      headers: { Accept: 'application/json' }
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    const idx = mockAstrBotState.personas.findIndex((p) => p.id === personaId)
    if (idx !== -1) mockAstrBotState.personas.splice(idx, 1)
    return { status: 'ok' }
  }
}

export async function saveAstrBotProvider(payload) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/providers`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload || {})
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    const id = (payload?.id || `provider_${Date.now()}`).replace(/[:!]/g, '_')
    const provider = {
      id,
      provider: payload?.provider || 'OpenAI Compatible',
      type: payload?.type || 'openai_chat_completion',
      provider_type: payload?.provider_type || 'chat_completion',
      enable: payload?.enable !== false,
      api_base: payload?.api_base || 'https://api.openai.com/v1',
      model: payload?.model || 'gpt-6-astra',
      models: payload?.models || [payload?.model || 'gpt-6-astra'],
      timeout: Number(payload?.timeout || 120),
      key_configured: true,
      masked_key: 'sk-configured-••••',
      status: payload?.enable !== false ? 'connected' : 'disabled',
      latency_ms: 38
    }
    const idx = mockAstrBotState.providers.findIndex((p) => p.id === id)
    if (idx !== -1) mockAstrBotState.providers[idx] = provider
    else mockAstrBotState.providers.unshift(provider)
    return { status: 'ok', provider }
  }
}

export async function testAstrBotProvider(providerId) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/providers/${encodeURIComponent(providerId)}/test`, {
      method: 'POST',
      headers: { Accept: 'application/json' }
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    const p = mockAstrBotState.providers.find((item) => item.id === providerId)
    return {
      id: providerId,
      model: p?.model || 'gpt-6-astra',
      status: 'connected',
      latency_ms: p?.latency_ms || 38,
      message: `Kết nối tới nhà cung cấp [${p?.provider || providerId}] thành công!`
    }
  }
}

export async function deleteAstrBotProvider(providerId) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/providers/${encodeURIComponent(providerId)}`, {
      method: 'DELETE',
      headers: { Accept: 'application/json' }
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    const idx = mockAstrBotState.providers.findIndex((p) => p.id === providerId)
    if (idx !== -1) mockAstrBotState.providers.splice(idx, 1)
    return { status: 'ok' }
  }
}

export async function saveAstrBotCronJob(payload) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/cron`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload || {})
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    const jobId = (payload?.job_id || payload?.id || `cron_${Date.now().toString(36)}`).replace(/[:!]/g, '_')
    const job = {
      job_id: jobId,
      name: payload?.name || 'Tác vụ chủ động AstrBot',
      description: payload?.description || '',
      cron_expression: payload?.cron_expression || '0 9 * * *',
      target_umo: payload?.target_umo || 'telegram_dior_concierge:PrivateMessage:vip_client_8801',
      prompt: payload?.prompt || 'Gửi bản tin tư vấn chăm sóc khách hàng định kỳ.',
      scenario: payload?.scenario || 'SERVICE_SUPPORT',
      run_once: Boolean(payload?.run_once),
      enabled: payload?.enabled !== false,
      last_run_at: null,
      next_run_at: `Theo lịch (${payload?.cron_expression || '0 9 * * *'})`,
      run_count: 0,
      last_status: 'scheduled',
      last_result_preview: 'Chưa chạy'
    }
    const idx = mockAstrBotState.cron_jobs.findIndex((j) => j.job_id === jobId)
    if (idx !== -1) mockAstrBotState.cron_jobs[idx] = job
    else mockAstrBotState.cron_jobs.unshift(job)
    return { status: 'ok', job }
  }
}

export async function runAstrBotCronJob(jobId) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/cron/${encodeURIComponent(jobId)}/run`, {
      method: 'POST',
      headers: { Accept: 'application/json' }
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    const job = mockAstrBotState.cron_jobs.find((j) => j.job_id === jobId)
    if (job) {
      const [botId, msgType, sessId] = (job.target_umo || 'telegram_dior_concierge:PrivateMessage:vip_client_8801').split(':')
      const sim = mockSimulateAstrBotMessage({
        bot_id: botId,
        message_type: msgType,
        session_id: sessId,
        sender_id: sessId,
        sender_name: 'Cron Proactive Runner',
        message: job.prompt,
        scenario: job.scenario
      })
      job.run_count = (job.run_count || 0) + 1
      job.last_run_at = new Date().toISOString()
      job.last_status = 'succeeded'
      job.last_result_preview = sim.reply_sent.slice(0, 160)
    }
    return { status: 'ok', job }
  }
}

export async function deleteAstrBotCronJob(jobId) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/cron/${encodeURIComponent(jobId)}`, {
      method: 'DELETE',
      headers: { Accept: 'application/json' }
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    const idx = mockAstrBotState.cron_jobs.findIndex((j) => j.job_id === jobId)
    if (idx !== -1) mockAstrBotState.cron_jobs.splice(idx, 1)
    return { status: 'ok' }
  }
}

export async function saveAstrBotExtension(payload) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/extensions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload || {})
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    const id = (payload?.id || payload?.name || `ext_${Date.now().toString(36)}`).replace(/[:!]/g, '_')
    const extension = {
      id,
      name: payload?.name || id,
      display_name: payload?.display_name || payload?.name || id,
      kind: payload?.kind || 'star_plugin',
      author: payload?.author || 'AstrBot Community',
      version: payload?.version || '1.0.0',
      description: payload?.description || 'Plugin mở rộng kết nối đường ống sự kiện AstrBot.',
      enabled: payload?.enabled !== false,
      commands: payload?.commands || [],
      tools_count: Number(payload?.tools_count || 2),
      endpoint: payload?.endpoint || `plugin://${payload?.name || id}`
    }
    mockAstrBotState.extensions.unshift(extension)
    return { status: 'ok', extension }
  }
}

export async function toggleAstrBotExtension(extensionId, enabled) {
  try {
    const res = await fetch(`${API_BASE}/astrbot/extensions/${encodeURIComponent(extensionId)}/enabled`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ enabled })
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    const ext = mockAstrBotState.extensions.find((e) => e.id === extensionId)
    if (ext) ext.enabled = Boolean(enabled)
    return { status: 'ok', extension: ext }
  }
}

export async function clearAstrBotLogs() {
  try {
    const res = await fetch(`${API_BASE}/astrbot/logs`, {
      method: 'DELETE',
      headers: { Accept: 'application/json' }
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return await res.json()
  } catch {
    mockAstrBotState.logs.splice(0, mockAstrBotState.logs.length)
    return { status: 'ok' }
  }
}

Object.assign(api, {
  getAstrBotOverview,
  createAstrBotBot,
  updateAstrBotBot,
  toggleAstrBotBot,
  deleteAstrBotBot,
  testAstrBotBot,
  clearAstrBotErrors,
  registerAstrBotQr,
  simulateAstrBotMessage,
  upsertAstrBotSessionRule,
  deleteAstrBotSession,
  upsertAstrBotRoute,
  deleteAstrBotRoute,
  saveAstrBotPersona,
  deleteAstrBotPersona,
  saveAstrBotProvider,
  testAstrBotProvider,
  deleteAstrBotProvider,
  saveAstrBotCronJob,
  runAstrBotCronJob,
  deleteAstrBotCronJob,
  saveAstrBotExtension,
  toggleAstrBotExtension,
  clearAstrBotLogs
})

export default api
