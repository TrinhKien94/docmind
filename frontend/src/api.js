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
  mockCustomerPrompts
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

export default api
