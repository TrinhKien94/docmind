/**
 * DocMind API Service Layer
 * Tương tác với backend Spring Boot (cổng 8090) qua /api
 */

const API_BASE = '/api'

export const api = {
  /**
   * Kiểm tra kết nối backend
   */
  async checkHealth() {
    try {
      const res = await fetch(`${API_BASE}/documents`, {
        method: 'GET',
        headers: { Accept: 'application/json' }
      })
      return res.ok
    } catch {
      return false
    }
  },

  /**
   * Lấy danh sách tất cả tài liệu
   * GET /api/documents
   */
  async getDocuments() {
    const res = await fetch(`${API_BASE}/documents`)
    if (!res.ok) {
      throw new Error(`Không thể lấy danh sách tài liệu (HTTP ${res.status})`)
    }
    return await res.json()
  },

  /**
   * Lấy trạng thái của một tài liệu
   * GET /api/documents/:id
   */
  async getDocumentStatus(id) {
    const res = await fetch(`${API_BASE}/documents/${encodeURIComponent(id)}`)
    if (!res.ok) {
      throw new Error(`Không thể lấy trạng thái tài liệu ${id} (HTTP ${res.status})`)
    }
    return await res.json()
  },

  /**
   * Upload tài liệu mới
   * POST /api/documents
   * multipart/form-data: file
   */
  async uploadDocument(file) {
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
  },

  /**
   * Hỏi đáp chat với tài liệu (RAG Grounded Chat)
   * POST /api/chat
   * Body: { document_ids: string[], question: string, top_k?: number }
   */
  async chat({ documentIds, question, topK = 5 }) {
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
  },

  /**
   * Tư vấn & chăm sóc khách hàng sử dụng prompt được huấn luyện trước
   * (người dùng đóng vai trò khách hàng mua hàng hoặc sử dụng dịch vụ)
   * POST /api/customer-chat
   */
  async customerChat({
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
  },

  /**
   * Lấy thông tin bộ prompt & kịch bản khách hàng được huấn luyện trước
   * GET /api/customer-chat/prompts
   */
  async getCustomerPrompts() {
    const res = await fetch(`${API_BASE}/customer-chat/prompts`)
    if (!res.ok) {
      throw new Error(`Không thể lấy thông tin prompt khách hàng (HTTP ${res.status})`)
    }
    return await res.json()
  }
}
