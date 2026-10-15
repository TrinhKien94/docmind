<template>
  <main class="chat-container">
    <!-- Chat Header -->
    <header class="chat-header">
      <!-- Primary Bar: compact, single-row on mobile and desktop -->
      <div class="header-primary-row">
        <div class="header-left">
          <!-- Mobile Drawer Toggle Button (< 768px) -->
          <button
            class="btn-drawer-toggle"
            data-testid="drawer-toggle"
            type="button"
            aria-label="Mở danh sách tài liệu"
            title="Mở danh sách tài liệu"
            @click="emit('toggle-sidebar')"
          >
            <svg class="hamburger-icon" xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="3" y1="12" x2="21" y2="12"/>
              <line x1="3" y1="6" x2="21" y2="6"/>
              <line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
            <span class="drawer-toggle-text">Tài liệu</span>
            <span v-if="selectedDocs.length > 0" class="drawer-badge">{{ selectedDocs.length }}</span>
          </button>

          <!-- Context Indicator (Brand / Title / Subtitle) -->
          <div class="context-indicator">
            <div class="pulse-sparkle">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/>
              </svg>
            </div>
            <div class="header-title-box">
              <h3 class="header-title">
                {{ chatMode === 'customer' ? 'Tư vấn Khách hàng' : 'Hỏi đáp tài liệu' }}
                <span class="header-mode-tag" :class="chatMode">
                  {{ chatMode === 'customer' ? 'Bán hàng & Dịch vụ' : 'RAG Grounded' }}
                </span>
              </h3>
              <p class="context-subtitle">
                <span v-if="chatMode === 'customer'">
                  Prompt huấn luyện sẵn · <strong class="text-accent">EvoAgentX Gen #{{ evolutionGen }}</strong>
                  <template v-if="selectedDocs.length > 0"> · {{ selectedDocs.length }} tài liệu</template>
                </span>
                <span v-else-if="selectedDocs.length > 0">
                  Phạm vi: <strong>{{ selectedDocs.length }} tài liệu</strong> ({{ selectedDocsNames }})
                </span>
                <span v-else class="text-warning">
                  ⚠️ Chưa chọn tài liệu nào. Vui lòng tick chọn tài liệu bên trái!
                </span>
              </p>
            </div>
          </div>
        </div>

        <!-- Primary Right Action Buttons -->
        <div class="header-primary-actions">
          <!-- EvoAgentX Evolution Modal Trigger -->
          <button
            class="header-action-btn btn-evo"
            type="button"
            title="Xem bảng tri thức tự tiến hoá EvoAgentX (TextGrad & MIPRO)"
            @click="emit('open-evolution')"
          >
            <span class="action-icon">🧬</span>
            <span class="action-label evo-text">Gen #{{ evolutionGen }}</span>
          </button>

          <!-- AstrBot Multi-Platform Hub Modal Trigger -->
          <button
            class="header-action-btn btn-astrbot"
            data-testid="open-astrbot-btn"
            type="button"
            title="Mở Trung tâm Điều khiển Đa Nền tảng AstrBot (19 Platform Adapters gồm Facebook Messenger, UMO Sessions, Star Commands)"
            @click="emit('open-astrbot')"
          >
            <span class="action-icon">🛰️</span>
            <span class="action-label astrbot-text">AstrBot Hub</span>
            <span class="messenger-dot" title="19 Platform Adapters Ready (Messenger, Telegram, Discord...)"></span>
          </button>

          <!-- Settings Toggle Button (Mobile & Tablet) -->
          <button
            class="header-action-btn btn-settings-toggle"
            type="button"
            :class="{ 'is-active': isSettingsOpen }"
            :title="isSettingsOpen ? 'Ẩn bộ lọc & cài đặt' : 'Mở bộ lọc & cài đặt (Chế độ, Kịch bản, Top-K)'"
            @click="isSettingsOpen = !isSettingsOpen"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
            <span class="action-label settings-text">Tuỳ chọn</span>
          </button>
        </div>
      </div>

      <!-- Secondary Row: Collapsible on Mobile/Tablet, Always visible on Desktop -->
      <transition name="settings-slide">
        <div v-show="isSettingsOpen" class="header-secondary-row">
          <div class="secondary-controls-wrap">
            <!-- Chat Mode Selector -->
            <div class="control-group" title="Chế độ hội thoại">
              <label for="mode-select" class="control-label">Chế độ:</label>
              <select id="mode-select" v-model="chatMode" class="compact-select">
                <option value="customer">🛍️ Khách hàng (Mua hàng & Dịch vụ)</option>
                <option value="rag">📄 Hỏi đáp Tài liệu (RAG thuần)</option>
              </select>
            </div>

            <!-- Customer Scenario Selector -->
            <div v-if="chatMode === 'customer'" class="control-group" title="Kịch bản prompt được huấn luyện trước">
              <label for="scenario-select" class="control-label">Kịch bản:</label>
              <select id="scenario-select" v-model="selectedScenario" class="compact-select">
                <option value="AUTO">Tự động nhận diện</option>
                <option v-for="sc in scenarioOptions" :key="sc.scenario" :value="sc.scenario">
                  {{ sc.display_name }}
                </option>
              </select>
            </div>

            <!-- Top-K Setting -->
            <div class="control-group" title="Số lượng chunk ngữ cảnh tối đa gửi vào prompt RAG">
              <label for="topk-select" class="control-label">Top-K:</label>
              <select id="topk-select" v-model="topK" class="compact-select compact-select-sm">
                <option :value="3">3 chunks</option>
                <option :value="5">5 chunks (chuẩn)</option>
                <option :value="10">10 chunks (sâu)</option>
                <option :value="20">20 chunks</option>
              </select>
            </div>

            <!-- Clear Chat Button -->
            <button 
              v-if="messages.length > 0" 
              class="btn-clear-chat" 
              type="button"
              title="Xóa toàn bộ cuộc hội thoại"
              @click="clearChat"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3 6h18"/>
                <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
                <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
              </svg>
              <span>Xóa chat</span>
            </button>
          </div>
        </div>
      </transition>
    </header>

    <!-- Messages Scroll Area -->
    <div ref="messagesScrollRef" class="messages-area">
      <!-- Empty State -->
      <div v-if="messages.length === 0" class="empty-chat-state">
        <div class="hero-icon-bubble">
          <svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
        </div>
        <h2 class="hero-title">Chào mừng đến với DocMind</h2>
        <p class="hero-desc">
          <template v-if="chatMode === 'customer'">
            Trò chuyện tự nhiên, gần gũi cùng <strong>chuyên viên tư vấn</strong> — giải đáp tận tâm mọi thắc mắc khi bạn <strong>mua hàng hoặc sử dụng dịch vụ</strong>.
          </template>
          <template v-else>
            Hệ thống trả lời câu hỏi <strong>chính xác 100%</strong> dựa trên tài liệu đã upload, kèm số trang và trích dẫn kiểm chứng nguồn.
          </template>
        </p>

        <!-- Starter Suggestions -->
        <div class="starters-container">
          <div class="starters-label">Gợi ý câu hỏi bắt đầu:</div>
          <div class="starters-grid">
            <button 
              v-for="(prompt, idx) in activeStarterPrompts" 
              :key="idx" 
              class="starter-card"
              :disabled="chatMode === 'rag' && selectedDocs.length === 0"
              @click="submitQuestion(prompt)"
            >
              <div class="starter-icon">✨</div>
              <div class="starter-text">{{ prompt }}</div>
            </button>
          </div>
        </div>
      </div>

      <!-- Messages Stream -->
      <div v-else class="messages-list">
        <div 
          v-for="(msg, index) in messages" 
          :key="index"
          class="message-row"
          :class="msg.role === 'user' ? 'row-user' : 'row-assistant'"
        >
          <!-- Assistant Avatar -->
          <div v-if="msg.role === 'assistant'" class="avatar-assistant">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/>
            </svg>
          </div>

          <!-- Message Body -->
          <div class="message-bubble" :class="msg.role">
            <!-- User text -->
            <div v-if="msg.role === 'user'" class="user-text">
              {{ msg.content }}
            </div>

            <!-- Assistant Markdown & Citations -->
            <div v-else class="assistant-content">
              <!-- Answer Body -->
              <div class="markdown-body" v-html="renderMarkdown(msg.content)"></div>

              <!-- Citations Section (ẩn hoàn toàn khi ở chế độ Khách hàng mua hàng) -->
              <div v-if="chatMode !== 'customer' && !msg.scenario && msg.citations && msg.citations.length > 0" class="citations-section">
                <div class="citations-header-row">
                  <span class="citations-label">
                    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path d="M12 2H2v10l9.29 9.29c.94.94 2.48.94 3.42 0l6.58-6.58c.94-.94.94-2.48 0-3.42L12 2Z"/>
                      <path d="M7 7h.01"/>
                    </svg>
                    Nguồn trích dẫn kiểm chứng ({{ msg.citations.length }})
                  </span>
                </div>

                <div class="citations-grid">
                  <CitationCard 
                    v-for="(cite, cIdx) in msg.citations"
                    :key="cIdx"
                    :citation="cite"
                    :doc-name="getDocName(cite.document_id)"
                  />
                </div>
              </div>

              <!-- Action Bar -->
              <div class="message-actions">
                <button 
                  class="action-btn" 
                  :title="copiedIndex === index ? 'Đã sao chép' : 'Sao chép câu trả lời'"
                  @click="copyAnswer(msg.content, index)"
                >
                  <svg v-if="copiedIndex !== index" xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect width="14" height="14" x="8" y="8" rx="2" ry="2"/>
                    <path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/>
                  </svg>
                  <svg v-else xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#34d399" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                  <span>{{ copiedIndex === index ? 'Đã sao chép' : 'Sao chép' }}</span>
                </button>

                <template v-if="chatMode === 'customer' && (msg.interactionId || msg.question)">
                  <button
                    class="action-btn"
                    :disabled="!!msg.feedbackSubmitted"
                    title="Đánh giá hữu ích để EvoAgentX chắt lọc mẫu Few-Shot (MIPRO)"
                    @click="emit('submit-feedback', { index, helpful: true, rating: 5 })"
                  >
                    <span>👍 {{ msg.feedbackSubmitted === 'positive' ? 'Đã ghi nhận hữu ích' : 'Hữu ích' }}</span>
                  </button>
                  <button
                    class="action-btn"
                    :disabled="!!msg.feedbackSubmitted"
                    title="Báo cần cải thiện để EvoAgentX sinh quy tắc TextGrad mới"
                    @click="emit('submit-feedback', { index, helpful: false, rating: 2, feedback: 'Cần trả lời chi tiết, rõ ràng và bám sát nhu cầu khách hàng hơn' })"
                  >
                    <span>👎 {{ msg.feedbackSubmitted === 'negative' ? 'Đã gửi phản tư TextGrad' : 'Cần cải thiện' }}</span>
                  </button>
                </template>
              </div>
            </div>
          </div>
        </div>

        <!-- Loading Indicator -->
        <div v-if="isAsking" class="message-row row-assistant">
          <div class="avatar-assistant pulse-glow">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/>
            </svg>
          </div>
          <div class="message-bubble assistant loading-bubble">
            <div class="retrieval-indicator">
              <div class="typing-dots">
                <span></span>
                <span></span>
                <span></span>
              </div>
              <span class="loading-label">
                {{ chatMode === 'customer' ? 'Chuyên viên đang soạn phản hồi tư vấn cho bạn...' : 'Đang thực hiện Dense Embedding + BM25 Hybrid Retrieval...' }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Chat Input Section -->
    <footer class="chat-input-wrapper">
      <div class="input-card" :class="{ 'has-focus': isFocused, 'is-disabled': chatMode === 'rag' && selectedDocs.length === 0 }">
        <textarea 
          ref="inputRef"
          v-model="questionInput" 
          :placeholder="chatMode === 'customer' ? 'Nhập câu hỏi của bạn trong vai trò khách hàng mua hàng hoặc sử dụng dịch vụ...' : 'Đặt câu hỏi về nội dung trong các tài liệu đã chọn...'"
          rows="1"
          :disabled="(chatMode === 'rag' && selectedDocs.length === 0) || isAsking"
          @focus="isFocused = true"
          @blur="isFocused = false"
          @keydown.enter="handleEnter"
          @input="autoResize"
        ></textarea>

        <div class="input-bottom-row">
          <div class="hint-text">
            <span><strong>Enter</strong> để gửi · <strong>Shift+Enter</strong> xuống dòng</span>
          </div>

          <button 
            class="btn btn-send" 
            :disabled="!canSend"
            @click="submitQuestion(questionInput)"
          >
            <span v-if="!isAsking">Gửi</span>
            <div v-else class="btn-spinner"></div>
            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="22" y1="2" x2="11" y2="13"/>
              <polygon points="22 2 15 22 11 13 2 9 22 2"/>
            </svg>
          </button>
        </div>
      </div>
    </footer>
  </main>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { marked } from 'marked'
import CitationCard from './CitationCard.vue'

const props = defineProps({
  selectedDocs: {
    type: Array,
    default: () => []
  },
  allDocs: {
    type: Array,
    default: () => []
  },
  messages: {
    type: Array,
    default: () => []
  },
  isAsking: {
    type: Boolean,
    default: false
  },
  evolutionGen: {
    type: Number,
    default: 0
  },
  customerScenarios: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits([
  'ask-question',
  'submit-feedback',
  'clear-chat',
  'toggle-sidebar',
  'open-evolution',
  'open-astrbot'
])

const chatMode = ref('customer')
const selectedScenario = ref('AUTO')
const topK = ref(5)
const questionInput = ref('')
const isFocused = ref(false)
const inputRef = ref(null)
const messagesScrollRef = ref(null)
const copiedIndex = ref(-1)

// Responsive secondary settings bar state
// Default to true on desktop (>= 1024px), false on tablet/mobile (< 1024px)
const isSettingsOpen = ref(typeof window !== 'undefined' ? window.innerWidth >= 1024 : false)

const handleWindowResize = () => {
  if (typeof window !== 'undefined' && window.innerWidth >= 1024) {
    isSettingsOpen.value = true
  }
}

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', handleWindowResize)
  }
})

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', handleWindowResize)
  }
})

const defaultScenarioOptions = [
  { scenario: 'SALES', display_name: 'Mua hàng & Báo giá' },
  { scenario: 'SERVICE_SUPPORT', display_name: 'Sử dụng Dịch vụ & Kỹ thuật' },
  { scenario: 'WARRANTY_RETURN', display_name: 'Bảo hành & Đổi trả' },
  { scenario: 'ORDER_BILLING', display_name: 'Đơn hàng & Thanh toán' }
]

const scenarioOptions = computed(() => {
  if (Array.isArray(props.customerScenarios) && props.customerScenarios.length > 0) {
    return props.customerScenarios.filter(s => s.scenario && s.scenario !== 'GENERAL')
  }
  return defaultScenarioOptions
})

const customerStarterPrompts = [
  'Tư vấn giúp mình gói dịch vụ phù hợp cho nhóm 10 người và báo giá chi tiết?',
  'Hướng dẫn các bước kích hoạt và sử dụng dịch vụ sau khi thanh toán?',
  'Chính sách bảo hành 12 tháng và đổi trả hoàn tiền trong 7 ngày đầu như thế nào?',
  'Cửa hàng hỗ trợ những hình thức thanh toán nào và có xuất hóa đơn VAT không?'
]

const ragStarterPrompts = [
  'Tóm tắt nội dung chính và các điểm cốt lõi của tài liệu',
  'Kiến trúc hệ thống bao gồm những thành phần và cổng dịch vụ nào?',
  'Quy trình ingest và luồng chat hoạt động như thế nào?',
  'Liệt kê các bảng biểu, công thức hoặc số liệu quan trọng'
]

const activeStarterPrompts = computed(() => {
  if (chatMode.value !== 'customer') {
    return ragStarterPrompts
  }
  if (Array.isArray(props.customerScenarios) && props.customerScenarios.length > 0) {
    if (selectedScenario.value !== 'AUTO') {
      const matched = props.customerScenarios.find(s => s.scenario === selectedScenario.value)
      if (matched && Array.isArray(matched.suggested_questions) && matched.suggested_questions.length > 0) {
        return matched.suggested_questions
      }
    }
    const collected = props.customerScenarios
      .filter(s => s.scenario !== 'GENERAL' && Array.isArray(s.suggested_questions) && s.suggested_questions.length > 0)
      .map(s => s.suggested_questions[0])
    if (collected.length > 0) {
      return collected.slice(0, 4)
    }
  }
  return customerStarterPrompts
})

const selectedDocsNames = computed(() => {
  return props.selectedDocs.map(d => d.filename).slice(0, 2).join(', ') + 
    (props.selectedDocs.length > 2 ? ` (+${props.selectedDocs.length - 2})` : '')
})

const canSend = computed(() => {
  const hasDocCondition = chatMode.value === 'customer' || props.selectedDocs.length > 0
  return questionInput.value.trim().length > 0 && hasDocCondition && !props.isAsking
})

const renderMarkdown = (text) => {
  if (!text) return ''
  const cleaned = chatMode.value === 'customer'
    ? text.replace(/\s*(?:\[\d{1,2}(?:\s*[,;-]\s*\d{1,2})*\])+/g, '')
    : text
  return marked.parse(cleaned)
}

const getDocName = (docId) => {
  const found = props.allDocs.find(d => d.document_id === docId)
  return found ? found.filename : docId
}

const autoResize = () => {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  const maxH = (typeof window !== 'undefined' && window.innerWidth < 640) ? 120 : 180
  el.style.height = Math.min(el.scrollHeight, maxH) + 'px'
}

const handleEnter = (e) => {
  if (e.shiftKey) return // Cho phép xuống dòng
  e.preventDefault()
  if (canSend.value) {
    submitQuestion(questionInput.value)
  }
}

const submitQuestion = (query) => {
  const q = query.trim()
  const hasDocCondition = chatMode.value === 'customer' || props.selectedDocs.length > 0
  if (!q || !hasDocCondition || props.isAsking) return

  emit('ask-question', {
    question: q,
    documentIds: props.selectedDocs.map(d => d.document_id),
    topK: topK.value,
    mode: chatMode.value,
    scenario: selectedScenario.value
  })

  questionInput.value = ''
  if (inputRef.value) {
    inputRef.value.style.height = 'auto'
  }

  scrollToBottom()
}

const clearChat = () => {
  emit('clear-chat')
}

const copyAnswer = async (text, idx) => {
  try {
    await navigator.clipboard.writeText(text)
    copiedIndex.value = idx
    setTimeout(() => {
      copiedIndex.value = -1
    }, 2000)
  } catch {
    // ignore
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesScrollRef.value) {
      messagesScrollRef.value.scrollTop = messagesScrollRef.value.scrollHeight
    }
  })
}

defineExpose({ scrollToBottom })
</script>

<style scoped>
.chat-container {
  flex: 1;
  height: 100%;
  min-height: 0;
  min-width: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
  position: relative;
  overflow: hidden;
}

/* Header */
.chat-header {
  flex-shrink: 0;
  width: 100%;
  border-bottom: 1px solid var(--border-subtle);
  background: rgba(15, 23, 42, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  z-index: 20;
  display: flex;
  flex-direction: column;
}

/* Primary Header Row */
.header-primary-row {
  height: 58px;
  min-height: 58px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  box-sizing: border-box;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

/* Drawer Toggle Button (< 768px) */
.btn-drawer-toggle {
  display: none; /* hidden on desktop & tablet >= 768px */
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 44px;
  min-height: 44px;
  padding: 6px 12px;
  background: rgba(99, 102, 241, 0.14);
  border: 1px solid rgba(99, 102, 241, 0.35);
  border-radius: var(--radius-sm);
  color: #c7d2fe;
  font-family: var(--font-sans);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  user-select: none;
  touch-action: manipulation;
  transition: all 0.2s ease;
}

.btn-drawer-toggle:hover {
  background: rgba(99, 102, 241, 0.25);
  border-color: var(--accent-primary);
  color: #ffffff;
}

.drawer-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: var(--radius-full);
  background: var(--accent-gradient);
  color: #ffffff;
  font-size: 0.7rem;
  font-weight: 700;
}

/* Context Indicator */
.context-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  overflow: hidden;
}

.pulse-sparkle {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-radius: var(--radius-sm);
  background: rgba(99, 102, 241, 0.15);
  color: #a5b4fc;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.header-title-box {
  min-width: 0;
  overflow: hidden;
}

.header-title {
  font-size: 0.92rem;
  font-weight: 600;
  color: var(--text-main);
  line-height: 1.25;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-mode-tag {
  font-size: 0.68rem;
  font-weight: 500;
  padding: 2px 7px;
  border-radius: var(--radius-full);
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-muted);
}

.header-mode-tag.customer {
  background: rgba(168, 85, 247, 0.12);
  border-color: rgba(168, 85, 247, 0.3);
  color: #d8b4fe;
}

.header-mode-tag.rag {
  background: rgba(99, 102, 241, 0.12);
  border-color: rgba(99, 102, 241, 0.3);
  color: #a5b4fc;
}

.context-subtitle {
  font-size: 0.74rem;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}

.text-accent {
  color: #c084fc;
}

.text-warning {
  color: #fbbf24;
  font-weight: 500;
}

/* Primary Right Actions */
.header-primary-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.header-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 44px;
  padding: 6px 12px;
  border-radius: var(--radius-full);
  font-family: var(--font-sans);
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  touch-action: manipulation;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
  white-space: nowrap;
}

.btn-evo {
  background: rgba(168, 85, 247, 0.12);
  border: 1px solid rgba(168, 85, 247, 0.35);
  color: #d8b4fe;
  box-shadow: 0 0 12px rgba(168, 85, 247, 0.15);
}

.btn-evo:hover {
  background: rgba(168, 85, 247, 0.22);
  border-color: rgba(168, 85, 247, 0.6);
  transform: translateY(-1px);
}

.btn-astrbot {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.16), rgba(139, 92, 246, 0.14));
  border: 1px solid rgba(99, 102, 241, 0.42);
  color: #c7d2fe;
  box-shadow: 0 0 12px rgba(99, 102, 241, 0.18);
}

.btn-astrbot:hover {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.28), rgba(139, 92, 246, 0.24));
  border-color: rgba(99, 102, 241, 0.7);
  transform: translateY(-1px);
}

.messenger-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 6px #34d399;
}

.btn-settings-toggle {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
}

.btn-settings-toggle:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-main);
  border-color: rgba(255, 255, 255, 0.2);
}

.btn-settings-toggle.is-active {
  background: rgba(99, 102, 241, 0.2);
  border-color: var(--border-active);
  color: #c7d2fe;
}

/* Secondary Row */
.header-secondary-row {
  width: 100%;
  padding: 8px 16px;
  background: rgba(11, 17, 32, 0.85);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  box-sizing: border-box;
}

.secondary-controls-wrap {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  width: 100%;
}

.control-group {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.78rem;
  color: var(--text-dim);
}

.control-label {
  font-size: 0.74rem;
  color: var(--text-muted);
  white-space: nowrap;
}

.compact-select {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--border-subtle);
  color: var(--text-main);
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  font-size: 0.76rem;
  outline: none;
  cursor: pointer;
  max-width: 100%;
  transition: border-color 0.15s ease;
}

.compact-select:focus {
  border-color: var(--accent-primary);
}

.compact-select-sm {
  width: auto;
}

.btn-clear-chat {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 32px;
  padding: 4px 10px;
  background: rgba(244, 63, 94, 0.1);
  border: 1px solid rgba(244, 63, 94, 0.25);
  color: #fb7185;
  border-radius: var(--radius-sm);
  font-size: 0.74rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  margin-left: auto;
}

.btn-clear-chat:hover {
  background: rgba(244, 63, 94, 0.2);
  border-color: rgba(244, 63, 94, 0.45);
}

/* Settings Slide Animation */
.settings-slide-enter-active,
.settings-slide-leave-active {
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.settings-slide-enter-from,
.settings-slide-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  transform: translateY(-6px);
}

.settings-slide-enter-to,
.settings-slide-leave-from {
  opacity: 1;
  max-height: 200px;
  transform: translateY(0);
}

/* Messages Area */
.messages-area {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
  padding: 24px 32px;
  display: flex;
  flex-direction: column;
}

/* Empty State */
.empty-chat-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  max-width: 680px;
  margin: 0 auto;
}

.hero-icon-bubble {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: var(--accent-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  margin-bottom: 20px;
  box-shadow: 0 0 32px rgba(99, 102, 241, 0.4);
}

.hero-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 8px;
}

.hero-desc {
  font-size: 0.95rem;
  color: var(--text-muted);
  line-height: 1.6;
  margin-bottom: 28px;
}

.hero-desc strong {
  color: #818cf8;
}

.starters-container {
  width: 100%;
}

.starters-label {
  font-size: 0.78rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text-dim);
  margin-bottom: 12px;
  text-align: left;
}

.starters-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.starter-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  min-height: 48px;
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.starter-card:hover:not(:disabled) {
  background: var(--bg-card-hover);
  border-color: var(--border-active);
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.starter-card:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.starter-icon {
  font-size: 1.1rem;
}

.starter-text {
  font-size: 0.82rem;
  color: var(--text-main);
  line-height: 1.4;
}

/* Messages List */
.messages-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
}

.message-row {
  display: flex;
  gap: 14px;
}

.row-user {
  justify-content: flex-end;
}

.row-assistant {
  justify-content: flex-start;
}

.avatar-assistant {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-radius: 8px;
  background: var(--accent-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  margin-top: 4px;
  box-shadow: 0 2px 10px rgba(99, 102, 241, 0.3);
}

.message-bubble {
  max-width: 82%;
  border-radius: var(--radius-lg);
  padding: 14px 18px;
  position: relative;
  word-break: break-word;
}

.message-bubble.user {
  background: #4338ca;
  color: #ffffff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 4px 14px rgba(67, 56, 202, 0.3);
}

.user-text {
  font-size: 0.95rem;
  line-height: 1.55;
  white-space: pre-wrap;
}

.message-bubble.assistant {
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  border-top-left-radius: 4px;
  box-shadow: var(--shadow-md);
  flex: 1;
}

.citations-section {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.citations-header-row {
  margin-bottom: 8px;
}

.citations-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.74rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #a5b4fc;
}

.citations-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.message-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.72rem;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border-subtle);
  color: var(--text-dim);
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.15s ease;
}

.action-btn:hover {
  color: var(--text-main);
  background: rgba(255, 255, 255, 0.1);
}

/* Loading state */
.loading-bubble {
  padding: 12px 16px;
}

.retrieval-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
}

.typing-dots {
  display: flex;
  align-items: center;
  gap: 4px;
}

.typing-dots span {
  width: 6px;
  height: 6px;
  background: #818cf8;
  border-radius: 50%;
  animation: typingBounce 1.2s infinite ease-in-out;
}

.typing-dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dots span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typingBounce {
  0%, 80%, 100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  40% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.loading-label {
  font-size: 0.8rem;
  color: var(--text-muted);
  font-style: italic;
}

/* Input Area */
.chat-input-wrapper {
  flex-shrink: 0;
  padding: 12px 24px max(14px, env(safe-area-inset-bottom));
  background: rgba(9, 13, 22, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1px solid var(--border-subtle);
  width: 100%;
  box-sizing: border-box;
}

.input-card {
  max-width: 860px;
  margin: 0 auto;
  background: var(--bg-card);
  border: 1.5px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  padding: 10px 14px;
  transition: all 0.2s ease;
  box-shadow: var(--shadow-md);
}

.input-card.has-focus {
  border-color: var(--border-active);
  box-shadow: 0 0 20px rgba(99, 102, 241, 0.15);
}

.input-card.is-disabled {
  opacity: 0.6;
}

textarea {
  width: 100%;
  background: transparent;
  border: none;
  color: var(--text-main);
  font-family: var(--font-sans);
  font-size: 0.95rem;
  line-height: 1.5;
  outline: none;
  resize: none;
  min-height: 24px;
  max-height: 180px;
}

textarea::placeholder {
  color: var(--text-dim);
}

.input-bottom-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}

.hint-text {
  font-size: 0.72rem;
  color: var(--text-dim);
}

.btn-send {
  background: var(--accent-gradient);
  color: #ffffff;
  padding: 6px 14px;
  font-size: 0.8rem;
  font-weight: 600;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  gap: 6px;
  box-shadow: 0 2px 10px rgba(99, 102, 241, 0.3);
}

.btn-send:hover:not(:disabled) {
  opacity: 0.95;
  transform: translateY(-1px);
}

.btn-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* ==================================================== */
/* Responsive Media Queries                             */
/* ==================================================== */

/* Desktop (>= 1024px) */
@media (min-width: 1024px) {
  .header-secondary-row {
    display: flex !important;
  }
  .btn-settings-toggle {
    display: none;
  }
}

/* Tablet (768px–1023px) */
@media (max-width: 1023px) {
  .header-primary-row {
    padding: 0 10px;
    gap: 8px;
  }

  .pulse-sparkle,
  .header-mode-tag {
    display: none;
  }

  .header-primary-actions {
    gap: 5px;
  }

  .header-action-btn {
    min-height: 44px;
    min-width: 44px;
    padding: 6px 8px;
  }

  .astrbot-text,
  .messenger-text,
  .settings-text {
    display: none;
  }
}

/* Mobile (< 768px) */
@media (max-width: 767px) {
  .btn-drawer-toggle {
    display: inline-flex;
  }

  .header-primary-row {
    height: 54px;
    min-height: 54px;
    padding: 0 10px;
    gap: 8px;
  }

  .pulse-sparkle {
    display: none; /* Hide sparkle on mobile to save horizontal space */
  }

  .header-mode-tag {
    display: none; /* Hide tag on mobile; shown in title */
  }

  .context-subtitle {
    display: none; /* Hide subtitle on mobile primary row */
  }

  .header-title {
    font-size: 0.88rem;
  }

  .header-primary-actions {
    gap: 5px;
  }

  .header-action-btn {
    min-height: 44px;
    min-width: 44px;
    padding: 6px 8px;
  }

  .astrbot-text,
  .settings-text {
    display: none; /* Icon-only on mobile for AstrBot and Settings */
  }

  .header-secondary-row {
    padding: 8px 10px 10px;
  }

  .secondary-controls-wrap {
    gap: 8px;
  }

  .control-group {
    flex: 1 1 calc(50% - 8px);
    min-width: 130px;
    flex-direction: column;
    align-items: flex-start;
    gap: 3px;
  }

  .control-group select {
    width: 100%;
  }

  .btn-clear-chat {
    margin-left: 0;
    width: 100%;
    justify-content: center;
    min-height: 38px;
  }

  .messages-area {
    padding: 14px 12px;
  }

  .message-bubble {
    max-width: 90%;
    padding: 12px 14px;
  }

  .message-row {
    gap: 8px;
  }

  .avatar-assistant {
    width: 28px;
    height: 28px;
    min-width: 28px;
  }

  .action-btn {
    padding: 8px 12px;
    font-size: 0.76rem;
    min-height: 38px;
  }

  .chat-input-wrapper {
    padding: 8px 12px max(10px, env(safe-area-inset-bottom));
  }

  .hint-text {
    display: none; /* Hide hardware keyboard hints on touch mobile */
  }
}

/* Very narrow mobile (< 640px) */
@media (max-width: 640px) {
  .starters-grid {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  textarea {
    max-height: 120px;
    font-size: 0.9rem;
  }

  .hero-title {
    font-size: 1.25rem;
  }

  .hero-desc {
    font-size: 0.85rem;
    margin-bottom: 20px;
  }

  .hero-icon-bubble {
    width: 56px;
    height: 56px;
    margin-bottom: 14px;
  }
}

/* Compact mobile (< 400px: 320px, 360px, 375px, 390px) */
@media (max-width: 399px) {
  .evo-text {
    display: none;
  }

  .header-primary-actions {
    gap: 4px;
  }
}

/* Ultra narrow mobile (< 360px, e.g. 320px Galaxy Fold / iPhone SE) */
@media (max-width: 359px) {
  .drawer-toggle-text {
    display: none;
  }

  .btn-drawer-toggle {
    min-width: 44px;
    padding: 6px;
  }

  .header-primary-row {
    padding: 0 6px;
    gap: 4px;
  }

  .header-title {
    font-size: 0.82rem;
  }
}
</style>
