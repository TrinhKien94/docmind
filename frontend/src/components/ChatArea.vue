<template>
  <main class="chat-container">
    <!-- Chat Header -->
    <header class="chat-header">
      <div class="header-left">
        <div class="context-indicator">
          <div class="pulse-sparkle">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/>
            </svg>
          </div>
          <div>
            <h3 class="header-title">Hỏi đáp tài liệu (RAG Grounded)</h3>
            <p class="context-subtitle">
              <span v-if="selectedDocs.length > 0">
                Phạm vi: <strong>{{ selectedDocs.length }} tài liệu</strong> ({{ selectedDocsNames }})
              </span>
              <span v-else class="text-warning">
                ⚠️ Chưa chọn tài liệu nào. Vui lòng tick chọn ít nhất 1 tài liệu ở bên trái!
              </span>
            </p>
          </div>
        </div>
      </div>

      <div class="header-right">
        <!-- Top-K Setting -->
        <div class="top-k-control" title="Số lượng chunk ngữ cảnh tối đa gửi vào prompt RAG">
          <label for="topk-select">Top-K:</label>
          <select id="topk-select" v-model="topK">
            <option :value="3">3 chunks</option>
            <option :value="5">5 chunks (chuẩn)</option>
            <option :value="10">10 chunks (sâu)</option>
            <option :value="20">20 chunks</option>
          </select>
        </div>

        <!-- Clear Chat Button -->
        <button 
          v-if="messages.length > 0" 
          class="btn btn-secondary btn-sm" 
          title="Xóa toàn bộ cuộc hội thoại"
          @click="clearChat"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 6h18"/>
            <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
            <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
          </svg>
          Xóa chat
        </button>
      </div>
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
          Hệ thống trả lời câu hỏi <strong>chính xác 100%</strong> dựa trên tài liệu đã upload, kèm số trang và trích dẫn kiểm chứng nguồn.
        </p>

        <!-- Starter Suggestions -->
        <div class="starters-container">
          <div class="starters-label">Gợi ý câu hỏi bắt đầu:</div>
          <div class="starters-grid">
            <button 
              v-for="(prompt, idx) in starterPrompts" 
              :key="idx" 
              class="starter-card"
              :disabled="selectedDocs.length === 0"
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

              <!-- Citations Section -->
              <div v-if="msg.citations && msg.citations.length > 0" class="citations-section">
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
              <span class="loading-label">Đang thực hiện Dense Embedding + BM25 Hybrid Retrieval...</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Chat Input Section -->
    <footer class="chat-input-wrapper">
      <div class="input-card" :class="{ 'has-focus': isFocused, 'is-disabled': selectedDocs.length === 0 }">
        <textarea 
          ref="inputRef"
          v-model="questionInput" 
          placeholder="Đặt câu hỏi về nội dung trong các tài liệu đã chọn..."
          rows="1"
          :disabled="selectedDocs.length === 0 || isAsking"
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
import { ref, computed, nextTick } from 'vue'
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
  }
})

const emit = defineEmits(['ask-question', 'clear-chat'])

const topK = ref(5)
const questionInput = ref('')
const isFocused = ref(false)
const inputRef = ref(null)
const messagesScrollRef = ref(null)
const copiedIndex = ref(-1)

const starterPrompts = [
  'Tóm tắt nội dung chính và các điểm cốt lõi của tài liệu',
  'Kiến trúc hệ thống bao gồm những thành phần và cổng dịch vụ nào?',
  'Quy trình ingest và luồng chat hoạt động như thế nào?',
  'Liệt kê các bảng biểu, công thức hoặc số liệu quan trọng'
]

const selectedDocsNames = computed(() => {
  return props.selectedDocs.map(d => d.filename).slice(0, 2).join(', ') + 
    (props.selectedDocs.length > 2 ? ` (+${props.selectedDocs.length - 2})` : '')
})

const canSend = computed(() => {
  return questionInput.value.trim().length > 0 && props.selectedDocs.length > 0 && !props.isAsking
})

const renderMarkdown = (text) => {
  if (!text) return ''
  return marked.parse(text)
}

const getDocName = (docId) => {
  const found = props.allDocs.find(d => d.document_id === docId)
  return found ? found.filename : docId
}

const autoResize = () => {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 180) + 'px'
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
  if (!q || props.selectedDocs.length === 0 || props.isAsking) return

  emit('ask-question', {
    question: q,
    documentIds: props.selectedDocs.map(d => d.document_id),
    topK: topK.value
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
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
  position: relative;
  overflow: hidden;
}

/* Header */
.chat-header {
  height: 64px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-subtle);
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(12px);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
}

.context-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pulse-sparkle {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: rgba(99, 102, 241, 0.15);
  color: #a5b4fc;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-main);
  line-height: 1.2;
}

.context-subtitle {
  font-size: 0.76rem;
  color: var(--text-muted);
}

.text-warning {
  color: #fbbf24;
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.top-k-control {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.78rem;
  color: var(--text-dim);
}

.top-k-control select {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--border-subtle);
  color: var(--text-main);
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  outline: none;
  cursor: pointer;
}

.top-k-control select:focus {
  border-color: var(--accent-primary);
}

.btn-sm {
  padding: 5px 10px;
  font-size: 0.75rem;
}

/* Messages Scroll */
.messages-area {
  flex: 1;
  overflow-y: auto;
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
  padding: 12px 24px 20px;
  background: rgba(9, 13, 22, 0.8);
  backdrop-filter: blur(12px);
  border-top: 1px solid var(--border-subtle);
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
</style>
