<template>
  <div class="app-shell">
    <!-- Notification Toast -->
    <transition name="toast">
      <div v-if="toastMessage" class="toast-banner" :class="toastType">
        <span>{{ toastMessage }}</span>
        <button class="toast-close" @click="toastMessage = ''">×</button>
      </div>
    </transition>

    <div class="main-layout">
      <!-- Left Sidebar: Upload, Documents List, Status Tracker -->
      <DocumentSidebar 
        :documents="documents"
        :selected-doc-ids="selectedDocIds"
        :is-uploading="isUploading"
        :is-connected="isConnected"
        @upload-file="handleUpload"
        @toggle-select="handleToggleSelect"
        @toggle-select-all="handleToggleSelectAll"
        @refresh-documents="fetchDocuments"
        @view-detail="inspectingDoc = $event"
        @toggle-demo="toggleDemoMode"
      />

      <!-- Right Main: Chat Q&A with Citations -->
      <ChatArea 
        ref="chatAreaRef"
        :selected-docs="selectedDocuments"
        :all-docs="documents"
        :messages="messages"
        :is-asking="isAsking"
        :evolution-gen="evolutionGen"
        :customer-scenarios="customerScenarios"
        @ask-question="handleAskQuestion"
        @submit-feedback="handleSubmitFeedback"
        @clear-chat="handleClearChat"
      />
    </div>

    <!-- Document Detail Inspector Modal -->
    <DocumentDetailModal 
      v-if="inspectingDoc"
      :doc="inspectingDoc"
      @close="inspectingDoc = null"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { api } from './api'
import { initialMockDocuments, sampleQA } from './mockData'
import DocumentSidebar from './components/DocumentSidebar.vue'
import ChatArea from './components/ChatArea.vue'
import DocumentDetailModal from './components/DocumentDetailModal.vue'

const documents = ref([])
const selectedDocIds = ref([])
const messages = ref([])
const isUploading = ref(false)
const isAsking = ref(false)
const isConnected = ref(false)
const inspectingDoc = ref(null)
const toastMessage = ref('')
const toastType = ref('info')
const chatAreaRef = ref(null)
const evolutionGen = ref(0)
const customerScenarios = ref([])
const defaultCustomerRole = ref('Khách hàng mua hàng hoặc sử dụng dịch vụ')

const pollingTimers = new Map()

const selectedDocuments = computed(() => {
  return documents.value.filter(d => selectedDocIds.value.includes(d.document_id))
})

const showToast = (msg, type = 'info', timeout = 4000) => {
  toastMessage.value = msg
  toastType.value = type
  setTimeout(() => {
    if (toastMessage.value === msg) {
      toastMessage.value = ''
    }
  }, timeout)
}

const syncCustomerBackendConfig = async () => {
  try {
    const [evo, prompts] = await Promise.allSettled([
      api.getCustomerEvolution(),
      api.getCustomerPrompts()
    ])
    if (evo.status === 'fulfilled' && evo.value && typeof evo.value.evolution_generation === 'number') {
      evolutionGen.value = evo.value.evolution_generation
    }
    if (prompts.status === 'fulfilled' && prompts.value) {
      if (Array.isArray(prompts.value.scenarios)) {
        customerScenarios.value = prompts.value.scenarios
      }
      if (prompts.value.default_customer_role) {
        defaultCustomerRole.value = prompts.value.default_customer_role
      }
    }
  } catch {
    // ignore
  }
}

/**
 * Kiểm tra backend và tải dữ liệu ban đầu
 */
const initApp = async () => {
  try {
    const online = await api.checkHealth()
    if (online) {
      isConnected.value = true
      await fetchDocuments()
      await syncCustomerBackendConfig()
      showToast('Đã kết nối thành công tới DocMind Backend (cổng 8090)', 'success')
    } else {
      useDemoData()
      showToast('Backend chưa chạy. Đang hiển thị giao diện ở Chế độ Demo trực quan.', 'info')
    }
  } catch {
    useDemoData()
  }
}

const useDemoData = () => {
  isConnected.value = false
  documents.value = JSON.parse(JSON.stringify(initialMockDocuments))
  // Tự động tick chọn các tài liệu READY
  selectedDocIds.value = documents.value
    .filter(d => d.status === 'READY')
    .map(d => d.document_id)
}

const toggleDemoMode = async () => {
  if (!isConnected.value) {
    // Thử kết nối lại API thật
    try {
      showToast('Đang thử kết nối tới backend localhost:8090...', 'info')
      const online = await api.checkHealth()
      if (online) {
        isConnected.value = true
        await fetchDocuments()
        await syncCustomerBackendConfig()
        showToast('Kết nối backend thành công!', 'success')
      } else {
        showToast('Không thể kết nối localhost:8090. Vẫn giữ Chế độ Demo.', 'error')
      }
    } catch (e) {
      showToast(`Kết nối thất bại: ${e.message}`, 'error')
    }
  } else {
    // Chuyển sang Demo
    useDemoData()
    showToast('Đã chuyển sang Chế độ Demo (dữ liệu mẫu)', 'info')
  }
}

/**
 * Lấy danh sách tài liệu từ backend
 */
const fetchDocuments = async () => {
  if (!isConnected.value) {
    showToast('Đã làm mới dữ liệu demo', 'info', 2000)
    return
  }

  try {
    const list = await api.getDocuments()
    documents.value = list

    // Duy trì lựa chọn hoặc tự động chọn các tài liệu READY mới
    if (selectedDocIds.value.length === 0) {
      selectedDocIds.value = list
        .filter(d => d.status === 'READY')
        .map(d => d.document_id)
    }

    // Tự động poll các tài liệu đang ở trạng thái PARSING
    list.filter(d => d.status === 'PARSING').forEach(d => {
      startPolling(d.document_id)
    })
  } catch (err) {
    console.error('Fetch docs error:', err)
    showToast(`Lỗi lấy danh sách tài liệu: ${err.message}`, 'error')
  }
}

/**
 * Bắt đầu poll trạng thái tài liệu
 */
const startPolling = (docId) => {
  if (pollingTimers.has(docId)) return

  const timer = setInterval(async () => {
    if (!isConnected.value) {
      clearInterval(timer)
      pollingTimers.delete(docId)
      return
    }

    try {
      const statusRes = await api.getDocumentStatus(docId)
      const idx = documents.value.findIndex(d => d.document_id === docId)
      if (idx !== -1) {
        documents.value[idx] = { ...documents.value[idx], ...statusRes }

        // Nếu đã hoàn thành (READY hoặc FAILED), dừng poll
        if (statusRes.status === 'READY' || statusRes.status === 'FAILED') {
          clearInterval(timer)
          pollingTimers.delete(docId)

          if (statusRes.status === 'READY') {
            showToast(`Tài liệu "${statusRes.filename}" đã phân tích xong!`, 'success')
            if (!selectedDocIds.value.includes(docId)) {
              selectedDocIds.value.push(docId)
            }
          } else {
            showToast(`Tài liệu "${statusRes.filename}" phân tích thất bại!`, 'error')
          }
        }
      }
    } catch (e) {
      console.warn('Poll error for doc', docId, e)
    }
  }, 2000)

  pollingTimers.set(docId, timer)
}

/**
 * Xử lý Upload file
 */
const handleUpload = async (file) => {
  isUploading.value = true

  if (!isConnected.value) {
    // Giả lập upload trên Demo Mode
    setTimeout(() => {
      const newDocId = `doc-demo-${Date.now()}`
      const newDoc = {
        document_id: newDocId,
        filename: file.name,
        status: 'PARSING',
        pages: Math.floor(Math.random() * 15) + 2,
        engine_breakdown: {},
        created_at: new Date().toISOString()
      }
      documents.value.unshift(newDoc)
      isUploading.value = false
      showToast(`Đã tải lên "${file.name}". Đang bóc tách block...`, 'info')

      // Giả lập chuyển đổi trạng thái sang READY sau 4 giây
      setTimeout(() => {
        const target = documents.value.find(d => d.document_id === newDocId)
        if (target) {
          target.status = 'READY'
          target.engine_breakdown = { pymupdf: target.pages }
          selectedDocIds.value.push(newDocId)
          showToast(`Tài liệu "${file.name}" đã phân tích sẵn sàng!`, 'success')
        }
      }, 4000)
    }, 1200)
    return
  }

  try {
    const res = await api.uploadDocument(file)
    showToast(`Đã tải lên "${file.name}". Job ID: ${res.job_id || 'đang xử lý'}`, 'success')

    // Thêm tạm vào danh sách và bắt đầu poll
    const existingIndex = documents.value.findIndex(d => d.document_id === res.document_id)
    if (existingIndex !== -1) {
      documents.value[existingIndex].status = res.status || 'PARSING'
    } else {
      documents.value.unshift({
        document_id: res.document_id,
        filename: file.name,
        status: res.status || 'PARSING',
        pages: 0,
        engine_breakdown: {},
        created_at: new Date().toISOString()
      })
    }

    startPolling(res.document_id)
  } catch (err) {
    showToast(`Upload thất bại: ${err.message}`, 'error')
  } finally {
    isUploading.value = false
  }
}

/**
 * Chọn / bỏ chọn tài liệu
 */
const handleToggleSelect = (docId) => {
  const doc = documents.value.find(d => d.document_id === docId)
  if (doc && doc.status !== 'READY') return // Chỉ cho phép chọn tài liệu đã READY

  const idx = selectedDocIds.value.indexOf(docId)
  if (idx !== -1) {
    selectedDocIds.value.splice(idx, 1)
  } else {
    selectedDocIds.value.push(docId)
  }
}

const handleToggleSelectAll = () => {
  const readyIds = documents.value.filter(d => d.status === 'READY').map(d => d.document_id)
  const isAll = readyIds.every(id => selectedDocIds.value.includes(id))

  if (isAll) {
    selectedDocIds.value = []
  } else {
    selectedDocIds.value = [...readyIds]
  }
}

/**
 * Hỏi đáp chat RAG hoặc Tư vấn khách hàng (Prompt được huấn luyện trước)
 */
const handleAskQuestion = async ({ question, documentIds, topK, mode = 'customer', scenario = 'AUTO', customerName = 'Quý khách' }) => {
  const priorHistory = messages.value
    .filter(m => m && m.content)
    .slice(-10)
    .map(m => ({ role: m.role, content: m.content }))

  // Thêm tin nhắn câu hỏi của user
  messages.value.push({
    role: 'user',
    content: question
  })

  isAsking.value = true
  chatAreaRef.value?.scrollToBottom()

  if (!isConnected.value) {
    // Trả lời trên demo mode
    setTimeout(() => {
      let matched = sampleQA['default']
      if (question.toLowerCase().includes('kiến trúc') || question.toLowerCase().includes('thành phần') || question.toLowerCase().includes('cổng')) {
        matched = sampleQA['kiến trúc']
      }

      messages.value.push({
        role: 'assistant',
        content: matched.answer,
        question: mode === 'customer' ? question : undefined,
        scenario: mode === 'customer' ? (scenario === 'AUTO' ? 'SALES' : scenario) : undefined,
        citations: matched.citations,
        feedbackSubmitted: null
      })

      isAsking.value = false
      chatAreaRef.value?.scrollToBottom()
    }, 1500)
    return
  }

  try {
    if (mode === 'customer') {
      const res = await api.customerChat({
        question,
        customerName,
        customerRole: defaultCustomerRole.value,
        scenario: scenario === 'AUTO' ? undefined : scenario,
        documentIds,
        topK,
        history: priorHistory
      })

      messages.value.push({
        role: 'assistant',
        content: res.answer || 'Dạ em xin ghi nhận câu hỏi và hỗ trợ Anh/Chị ngay ạ.',
        question,
        scenario: res.scenario,
        interactionId: res.interaction_id,
        evolutionGeneration: res.evolution_generation,
        appliedGuidelines: res.applied_learned_guidelines || 0,
        suggestedQuestions: res.suggested_questions || [],
        citations: res.citations || [],
        feedbackSubmitted: null
      })
      if (typeof res.evolution_generation === 'number') {
        evolutionGen.value = res.evolution_generation
      }
    } else {
      const res = await api.chat({
        documentIds,
        question,
        topK
      })

      messages.value.push({
        role: 'assistant',
        content: res.answer || 'Không tìm thấy câu trả lời phù hợp trong tài liệu.',
        citations: res.citations || []
      })
    }
  } catch (err) {
    messages.value.push({
      role: 'assistant',
      content: `⚠️ **Lỗi truy vấn:** ${err.message}\n\nHãy đảm bảo service backend đang hoạt động.`,
      citations: []
    })
  } finally {
    isAsking.value = false
    chatAreaRef.value?.scrollToBottom()
  }
}

const handleSubmitFeedback = async ({ index, helpful, rating, feedback }) => {
  const msg = messages.value[index]
  if (!msg) return
  msg.feedbackSubmitted = helpful ? 'positive' : 'negative'

  if (!isConnected.value) {
    evolutionGen.value += 1
    showToast('Đã ghi nhận phản hồi và cập nhật quy tắc tự tiến hoá EvoAgentX (Demo)', 'success')
    return
  }

  try {
    const evoStatus = await api.submitCustomerFeedback({
      interactionId: msg.interactionId,
      question: msg.question,
      answer: msg.content,
      scenario: msg.scenario,
      rating,
      helpful,
      feedback
    })
    if (evoStatus && typeof evoStatus.evolution_generation === 'number') {
      evolutionGen.value = evoStatus.evolution_generation
    }
    showToast(
      `EvoAgentX đã học hỏi từ phản hồi (Thế hệ tiến hoá #${evolutionGen.value})!`,
      'success'
    )
  } catch (e) {
    showToast(`Gửi phản hồi thất bại: ${e.message}`, 'error')
  }
}

const handleClearChat = () => {
  messages.value = []
}

onMounted(() => {
  initApp()
})

onUnmounted(() => {
  pollingTimers.forEach(timer => clearInterval(timer))
  pollingTimers.clear()
})
</script>

<style scoped>
.app-shell {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-primary);
}

.main-layout {
  flex: 1;
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* Toast Banner */
.toast-banner {
  position: fixed;
  top: 16px;
  right: 20px;
  z-index: 999;
  padding: 10px 16px;
  border-radius: var(--radius-md);
  font-size: 0.82rem;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: var(--shadow-lg);
  backdrop-filter: blur(12px);
}

.toast-banner.success {
  background: rgba(16, 185, 129, 0.9);
  color: #ffffff;
  border: 1px solid rgba(16, 185, 129, 0.4);
}

.toast-banner.info {
  background: rgba(99, 102, 241, 0.9);
  color: #ffffff;
  border: 1px solid rgba(99, 102, 241, 0.4);
}

.toast-banner.error {
  background: rgba(244, 63, 94, 0.9);
  color: #ffffff;
  border: 1px solid rgba(244, 63, 94, 0.4);
}

.toast-close {
  background: none;
  border: none;
  color: inherit;
  font-size: 1.1rem;
  cursor: pointer;
  padding: 0 2px;
  line-height: 1;
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.25s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-12px);
}
</style>
