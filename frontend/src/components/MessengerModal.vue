<template>
  <transition name="modal-fade">
    <div
      v-if="isOpen"
      class="modal-backdrop"
      data-testid="messenger-modal-backdrop"
      @click.self="handleClose"
    >
      <div
        class="modal-container"
        role="dialog"
        aria-modal="true"
        aria-labelledby="messenger-modal-title"
      >
        <!-- Modal Header -->
        <header class="modal-header">
          <div class="header-branding">
            <div class="messenger-avatar-badge">
              <svg class="messenger-logo-svg" xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none">
                <path d="M12 2C6.477 2 2 6.145 2 11.258c0 2.91 1.454 5.512 3.736 7.185V22l3.418-1.875c.905.251 1.861.387 2.846.387 5.523 0 10-4.145 10-9.254C22 6.145 17.523 2 12 2Z" fill="url(#msgr-head-grad)"/>
                <path d="m6.5 13.5 3.5-3.75 2.5 2.5 4.5-4.75-3.5 3.75-2.5-2.5-4.5 4.75Z" fill="#ffffff"/>
                <defs>
                  <linearGradient id="msgr-head-grad" x1="2" y1="2" x2="22" y2="22" gradientUnits="userSpaceOnUse">
                    <stop stop-color="#00C6FF"/>
                    <stop offset="1" stop-color="#0078FF"/>
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <div class="header-title-group">
              <div class="title-with-badge">
                <h3 id="messenger-modal-title" class="modal-title">Tích hợp Facebook Messenger</h3>
                <span
                  class="badge status-pill"
                  :class="statusBadgeClass"
                >
                  <span class="status-dot"></span>
                  {{ statusBadgeText }}
                </span>
              </div>
              <p class="modal-subtitle">
                Tư vấn khách hàng đa kênh & Cầu nối tự tiến hoá EvoAgentX (Meta Graph API v21.0)
              </p>
            </div>
          </div>

          <button
            class="btn-close-modal"
            type="button"
            aria-label="Đóng cửa sổ"
            title="Đóng cửa sổ (Esc)"
            @click="handleClose"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </header>

        <!-- Segmented Tab Navigation -->
        <nav class="tabs-nav" aria-label="Messenger tabs">
          <button
            class="tab-btn"
            :class="{ active: activeTab === 'status' }"
            type="button"
            @click="activeTab = 'status'"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            </svg>
            <span>Trạng thái & Webhook</span>
          </button>

          <button
            class="tab-btn"
            :class="{ active: activeTab === 'simulator' }"
            type="button"
            @click="activeTab = 'simulator'"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            <span>Giả lập Bot Messenger</span>
          </button>

          <button
            class="tab-btn"
            :class="{ active: activeTab === 'sessions' }"
            type="button"
            @click="activeTab = 'sessions'"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            <span>Phiên khách hàng</span>
            <span v-if="sessionsCount > 0" class="tab-badge">{{ sessionsCount }}</span>
          </button>
        </nav>

        <!-- Modal Body Content -->
        <main class="modal-body scroll-touch-y">
          <!-- TAB 1: TRẠNG THÁI & WEBHOOK -->
          <section v-if="activeTab === 'status'" class="tab-pane status-pane">
            <!-- Webhook Callback URL Card -->
            <div class="glass-card webhook-card">
              <div class="card-header-row">
                <div class="card-title-group">
                  <span class="card-icon-badge">🔗</span>
                  <h4 class="card-title">Webhook Callback URL</h4>
                </div>
                <span class="protocol-badge">Meta Webhook Standard</span>
              </div>
              <p class="card-description">
                Cấu hình URL này vào mục <strong>Facebook App &gt; Messenger &gt; Webhooks &gt; Callback URL</strong> để tiếp nhận tin nhắn từ người dùng.
              </p>
              <div class="copy-input-group">
                <input
                  type="text"
                  readonly
                  :value="resolvedWebhookUrl"
                  class="input-readonly font-mono"
                  aria-label="Webhook URL"
                />
                <button
                  class="btn btn-copy"
                  type="button"
                  :class="{ copied: copiedWebhook }"
                  @click="copyText(resolvedWebhookUrl, 'webhook')"
                >
                  <svg v-if="!copiedWebhook" xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                  </svg>
                  <svg v-else xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                  <span>{{ copiedWebhook ? 'Đã chép!' : 'Sao chép URL' }}</span>
                </button>
              </div>
            </div>

            <!-- Configuration Parameters Grid (4 Cards) -->
            <div class="config-grid">
              <!-- Card 1: Verify Token -->
              <div class="glass-card stat-card">
                <div class="stat-header">
                  <span class="stat-label">Verify Token</span>
                  <span
                    class="badge"
                    :class="messengerStatus.verifyTokenConfigured ? 'badge-ready' : 'badge-failed'"
                  >
                    {{ messengerStatus.verifyTokenConfigured ? 'Đã cấu hình' : 'Thiếu Token' }}
                  </span>
                </div>
                <div class="stat-value-row">
                  <code class="token-code font-mono">{{ displayVerifyToken }}</code>
                  <button
                    class="btn-icon-tiny"
                    type="button"
                    title="Sao chép Verify Token"
                    @click="copyText(displayVerifyToken, 'token')"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                    </svg>
                  </button>
                </div>
                <p class="stat-hint">Dán vào ô <em>Verify Token</em> khi thêm webhook trên Meta Dashboard.</p>
              </div>

              <!-- Card 2: App Secret & HMAC-SHA256 -->
              <div class="glass-card stat-card">
                <div class="stat-header">
                  <span class="stat-label">Chữ ký X-Hub-Signature</span>
                  <span
                    class="badge"
                    :class="messengerStatus.verifySignature ? 'badge-ready' : 'badge-neutral'"
                  >
                    {{ messengerStatus.verifySignature ? 'Bật HMAC-SHA256' : 'Chế độ Dev (Tùy chọn)' }}
                  </span>
                </div>
                <div class="stat-value-row">
                  <span class="stat-bold">
                    {{ messengerStatus.appSecretConfigured ? 'Đã nạp App Secret' : 'Chưa cấu hình Secret' }}
                  </span>
                </div>
                <p class="stat-hint">Kiểm tra tính toàn vẹn payload và chống tấn công giả mạo (Timing attack safe).</p>
              </div>

              <!-- Card 3: Meta Graph Send API -->
              <div class="glass-card stat-card">
                <div class="stat-header">
                  <span class="stat-label">Graph Send API</span>
                  <span
                    class="badge"
                    :class="messengerStatus.pageTokenConfigured ? 'badge-ready' : 'badge-parsing'"
                  >
                    {{ messengerStatus.pageTokenConfigured ? 'Đã kết nối Page' : 'Chế độ Giả lập (Offline)' }}
                  </span>
                </div>
                <div class="stat-value-row">
                  <span class="stat-bold font-mono">{{ messengerStatus.apiVersion || 'v21.0' }}</span>
                  <span class="text-dim">/me/messages</span>
                </div>
                <p class="stat-hint">
                  {{ messengerStatus.pageTokenConfigured
                    ? 'Sẵn sàng gửi tin nhắn thật tới Fanpage qua Facebook Graph API.'
                    : 'Chưa có Page Token: tin nhắn sẽ chạy giả lập nội bộ phục vụ kiểm thử.' }}
                </p>
              </div>

              <!-- Card 4: Active Customer Sessions -->
              <div class="glass-card stat-card">
                <div class="stat-header">
                  <span class="stat-label">Phiên khách hàng (PSID)</span>
                  <span class="badge badge-ready">{{ sessionsCount }} phiên đang nhớ</span>
                </div>
                <div class="stat-value-row">
                  <span class="stat-big-number">{{ messengerStatus.recentInteractions?.length || 0 }}</span>
                  <span class="stat-unit">lượt tương tác đã ghi</span>
                </div>
                <p class="stat-hint">Bộ nhớ đa lượt tự động phục vụ tự tiến hoá hội thoại EvoAgentX.</p>
              </div>
            </div>

            <!-- Meta Quick Integration Guide -->
            <div class="glass-card guide-card">
              <h4 class="guide-title">
                <span class="guide-icon">📋</span>
                4 bước cấu hình nhanh trên Meta for Developers
              </h4>
              <div class="steps-grid">
                <div class="step-item">
                  <div class="step-num">1</div>
                  <div class="step-text">
                    <strong>Tạo App & Thêm Messenger</strong>
                    <span>Chọn loại <em>Business</em> hoặc <em>Khác</em>, thêm sản phẩm Messenger trong Meta Developer Dashboard.</span>
                  </div>
                </div>
                <div class="step-item">
                  <div class="step-num">2</div>
                  <div class="step-text">
                    <strong>Đăng ký Webhook</strong>
                    <span>Dán <em>Callback URL</em> và <em>Verify Token</em> ở trên, nhấn <em>Xác minh và lưu</em>.</span>
                  </div>
                </div>
                <div class="step-item">
                  <div class="step-num">3</div>
                  <div class="step-text">
                    <strong>Đăng ký sự kiện (Fields)</strong>
                    <span>Bật tích chọn 2 trường sự kiện: <code>messages</code> và <code>messaging_postbacks</code>.</span>
                  </div>
                </div>
                <div class="step-item">
                  <div class="step-num">4</div>
                  <div class="step-text">
                    <strong>Thử nghiệm ngay</strong>
                    <span>Nhắn tin thử tới Fanpage hoặc sử dụng tab <em>Giả lập Bot Messenger</em> bên cạnh!</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Actions Bar -->
            <div class="status-actions-bar">
              <button
                class="btn btn-secondary"
                type="button"
                :disabled="isLoadingStatus"
                @click="loadStatus"
              >
                <svg
                  class="btn-icon"
                  :class="{ 'spin-anim': isLoadingStatus }"
                  xmlns="http://www.w3.org/2000/svg"
                  width="15"
                  height="15"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <polyline points="23 4 23 10 17 10"/>
                  <polyline points="1 20 1 14 7 14"/>
                  <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
                </svg>
                <span>{{ isLoadingStatus ? 'Đang kiểm tra...' : 'Làm mới trạng thái' }}</span>
              </button>
            </div>
          </section>

          <!-- TAB 2: GIẢ LẬP BOT MESSENGER -->
          <section v-if="activeTab === 'simulator'" class="tab-pane simulator-pane">
            <!-- Simulator Control Bar -->
            <div class="simulator-controls-bar">
              <!-- Sender ID (PSID) Input -->
              <div class="control-group sender-group">
                <label for="simulator-psid" class="control-label">Mã khách hàng (PSID):</label>
                <div class="psid-input-wrap">
                  <span class="psid-prefix">PSID</span>
                  <input
                    id="simulator-psid"
                    v-model="simSenderId"
                    type="text"
                    placeholder="customer_psid_test"
                    class="input-psid"
                  />
                  <button
                    class="btn-icon-tiny"
                    type="button"
                    title="Tạo PSID ngẫu nhiên"
                    @click="generateRandomPsid"
                  >
                    🎲
                  </button>
                </div>
              </div>

              <!-- Scenario Selector -->
              <div class="control-group scenario-group">
                <label for="simulator-scenario" class="control-label">Kịch bản tư vấn:</label>
                <select id="simulator-scenario" v-model="simScenario" class="select-scenario">
                  <option value="">Tự động nhận diện (Contextual Auto)</option>
                  <option value="SALES">Tư vấn bán hàng & dịch vụ (SALES)</option>
                  <option value="SUPPORT">Hỗ trợ kỹ thuật & tài liệu (SUPPORT)</option>
                  <option value="COMPLAINT">Tiếp nhận khiếu nại (COMPLAINT)</option>
                </select>
              </div>

              <!-- Reset Current Session Button -->
              <button
                class="btn btn-secondary btn-reset-sim"
                type="button"
                title="Xóa phiên làm việc hiện tại của PSID này"
                @click="resetCurrentSession"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="1 4 1 10 7 10"/>
                  <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>
                </svg>
                <span>Làm mới đoạn chat</span>
              </button>
            </div>

            <!-- Messenger Simulator Phone Screen Box -->
            <div class="messenger-phone-frame">
              <!-- Messenger App Header Bar -->
              <div class="messenger-app-header">
                <div class="msgr-header-left">
                  <div class="msgr-bot-avatar">
                    <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none">
                      <path d="M12 2C6.477 2 2 6.145 2 11.258c0 2.91 1.454 5.512 3.736 7.185V22l3.418-1.875c.905.251 1.861.387 2.846.387 5.523 0 10-4.145 10-9.254C22 6.145 17.523 2 12 2Z" fill="#0084FF"/>
                      <path d="m6.5 13.5 3.5-3.75 2.5 2.5 4.5-4.75-3.5 3.75-2.5-2.5-4.5 4.75Z" fill="#ffffff"/>
                    </svg>
                    <span class="active-badge-dot"></span>
                  </div>
                  <div class="msgr-bot-info">
                    <span class="msgr-bot-name">DocMind Messenger Bot</span>
                    <span class="msgr-bot-status">Đang hoạt động • Phản hồi tức thì</span>
                  </div>
                </div>
                <div class="msgr-header-right">
                  <span class="psid-badge-tiny font-mono">{{ simSenderId }}</span>
                </div>
              </div>

              <!-- Message History Transcript -->
              <div ref="chatTranscriptRef" class="messages-container scroll-touch-y">
                <!-- Empty Conversation Welcome Screen -->
                <div v-if="conversation.length === 0" class="empty-chat-welcome">
                  <div class="welcome-icon">💬</div>
                  <h5 class="welcome-title">Trình giả lập Facebook Messenger</h5>
                  <p class="welcome-text">
                    Gửi tin nhắn mẫu để kiểm tra phản hồi từ <code>CustomerChatService</code> kết hợp RAG và cơ chế tự tiến hoá <strong>EvoAgentX</strong>.
                  </p>
                  <div class="starter-chips-group">
                    <span class="chips-label">Gợi ý thử nhanh:</span>
                    <button
                      v-for="(prompt, idx) in starterPrompts"
                      :key="idx"
                      class="starter-chip"
                      type="button"
                      @click="sendQuickPrompt(prompt)"
                    >
                      {{ prompt }}
                    </button>
                  </div>
                </div>

                <!-- Chat Messages Stream -->
                <div
                  v-for="(msg, index) in conversation"
                  :key="msg.id || index"
                  class="message-row"
                  :class="msg.sender === 'user' ? 'row-user' : 'row-bot'"
                >
                  <!-- Bot Avatar -->
                  <div v-if="msg.sender === 'bot'" class="avatar-small">
                    🤖
                  </div>

                  <!-- Message Bubble Container -->
                  <div class="bubble-wrap" :class="msg.sender === 'user' ? 'wrap-user' : 'wrap-bot'">
                    <div
                      class="chat-bubble"
                      :class="msg.sender === 'user' ? 'bubble-user' : 'bubble-bot'"
                    >
                      <!-- User Text (Plain) -->
                      <p v-if="msg.sender === 'user'" class="user-text">{{ msg.text }}</p>

                      <!-- Bot Markdown Content -->
                      <div
                        v-else
                        class="bot-text markdown-body"
                        v-html="renderMarkdown(msg.text)"
                      ></div>
                    </div>

                    <!-- Meta Information & Feedback (Bot only) -->
                    <div v-if="msg.sender === 'bot'" class="bot-meta-bar">
                      <div class="meta-tags-row">
                        <span v-if="msg.scenario" class="scenario-pill">
                          {{ msg.scenario }}
                        </span>
                        <span v-if="msg.interactionId" class="interaction-pill font-mono">
                          #{{ msg.interactionId.substring(0, 8) }}
                        </span>
                        <span class="msg-timestamp">{{ msg.timestamp }}</span>
                      </div>

                      <!-- Inline Feedback Triggers (Thumbs Up / Down) -->
                      <div class="bot-feedback-triggers">
                        <span class="feedback-label">Đánh giá:</span>
                        <button
                          class="btn-feedback"
                          :class="{ active: msg.feedbackState === 'helpful' }"
                          type="button"
                          title="Hài lòng (Gửi tín hiệu tiến hoá tới EvoAgentX)"
                          :disabled="msg.feedbackState !== undefined && msg.feedbackState !== null"
                          @click="triggerFeedback(msg, true)"
                        >
                          👍
                        </button>
                        <button
                          class="btn-feedback"
                          :class="{ active: msg.feedbackState === 'unhelpful' }"
                          type="button"
                          title="Chưa rõ (Gửi tín hiệu điều chỉnh tới EvoAgentX)"
                          :disabled="msg.feedbackState !== undefined && msg.feedbackState !== null"
                          @click="triggerFeedback(msg, false)"
                        >
                          👎
                        </button>
                        <span v-if="msg.feedbackState" class="feedback-acknowledged">
                          {{ msg.feedbackState === 'helpful' ? 'Đã ghi nhận 👍' : 'Đã ghi nhận 👎' }}
                        </span>
                      </div>
                    </div>

                    <!-- Quick Replies Pills underneath Bot Message -->
                    <div
                      v-if="msg.sender === 'bot' && msg.quickReplies && msg.quickReplies.length > 0"
                      class="quick-replies-container"
                    >
                      <button
                        v-for="(qr, qrIdx) in msg.quickReplies"
                        :key="qrIdx"
                        class="quick-reply-pill"
                        type="button"
                        :title="`Chọn: ${qr}`"
                        @click="handleQuickReplyClick(qr, msg)"
                      >
                        <span class="qr-text">{{ enforceMetaQuickReplyLimit(qr) }}</span>
                      </button>
                    </div>
                  </div>
                </div>

                <!-- Bot Typing Indicator Animation -->
                <div v-if="isSimulating" class="message-row row-bot row-typing">
                  <div class="avatar-small">🤖</div>
                  <div class="chat-bubble bubble-bot bubble-typing">
                    <span class="typing-dot"></span>
                    <span class="typing-dot"></span>
                    <span class="typing-dot"></span>
                  </div>
                </div>
              </div>

              <!-- Message Input Bar -->
              <footer class="simulator-input-area">
                <form class="input-form" @submit.prevent="handleSubmitMessage">
                  <input
                    ref="messageInputRef"
                    v-model="simMessageText"
                    type="text"
                    class="input-message"
                    placeholder="Nhập tin nhắn giả lập từ khách hàng... (Enter để gửi)"
                    :disabled="isSimulating"
                  />
                  <button
                    class="btn btn-send-message"
                    type="submit"
                    :disabled="!canSendMessage || isSimulating"
                    title="Gửi tin nhắn giả lập"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <line x1="22" y1="2" x2="11" y2="13"/>
                      <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                    </svg>
                    <span class="btn-send-label">Gửi</span>
                  </button>
                </form>
              </footer>
            </div>
          </section>

          <!-- TAB 3: PHIÊN KHÁCH HÀNG GẦN ĐÂY -->
          <section v-if="activeTab === 'sessions'" class="tab-pane sessions-pane">
            <!-- Header Summary Row -->
            <div class="sessions-header-card glass-card">
              <div class="sessions-stat-wrap">
                <div class="stat-bubble">
                  <span class="stat-number">{{ sessionsCount }}</span>
                  <span class="stat-text">Khách hàng (PSID)</span>
                </div>
                <div class="stat-bubble">
                  <span class="stat-number">{{ messengerStatus.recentInteractions?.length || 0 }}</span>
                  <span class="stat-text">Lượt tương tác lưu trong bộ nhớ</span>
                </div>
              </div>

              <button
                class="btn btn-danger-soft"
                type="button"
                :disabled="isClearingSessions || sessionsCount === 0"
                @click="confirmClearSessions"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                </svg>
                <span>{{ isClearingSessions ? 'Đang xóa...' : 'Xóa tất cả phiên' }}</span>
              </button>
            </div>

            <!-- Search / Filter input -->
            <div class="sessions-search-bar">
              <input
                v-model="sessionsFilterQuery"
                type="text"
                class="search-input"
                placeholder="Tìm kiếm theo PSID, câu hỏi hoặc kịch bản..."
              />
            </div>

            <!-- Interactions List -->
            <div v-if="filteredInteractions.length > 0" class="interactions-list">
              <div
                v-for="(item, idx) in filteredInteractions"
                :key="item.interaction_id || idx"
                class="glass-card interaction-item-card"
              >
                <div class="item-header">
                  <div class="psid-info">
                    <span class="psid-avatar-icon">👤</span>
                    <strong class="font-mono psid-label">{{ item.sender_id }}</strong>
                    <span v-if="item.scenario" class="scenario-badge">{{ item.scenario }}</span>
                    <span
                      class="delivery-badge"
                      :class="item.simulated ? 'badge-parsing' : 'badge-ready'"
                    >
                      {{ item.simulated ? 'SIMULATED' : (item.delivery_status || 'SENT') }}
                    </span>
                  </div>
                  <span class="interaction-time">{{ formatTime(item.timestamp) }}</span>
                </div>

                <div class="interaction-dialog-preview">
                  <div class="preview-row customer-row">
                    <span class="speaker-tag customer-tag">Khách:</span>
                    <span class="preview-text">{{ item.incoming_text }}</span>
                  </div>
                  <div class="preview-row bot-row">
                    <span class="speaker-tag bot-tag">Bot:</span>
                    <span class="preview-text text-bot-reply">{{ truncateText(item.reply_text, 140) }}</span>
                  </div>
                </div>

                <div class="item-footer">
                  <span class="quick-reply-count-info">
                    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                    </svg>
                    {{ item.quick_replies_count || 0 }} Quick Replies đính kèm
                  </span>

                  <div class="item-action-btns">
                    <button
                      class="btn-text-action"
                      type="button"
                      title="Nạp phiên của khách hàng này vào Giả lập"
                      @click="loadPsidToSimulator(item.sender_id, item.scenario)"
                    >
                      Mở trong giả lập ↗
                    </button>
                    <button
                      class="btn-text-action text-danger"
                      type="button"
                      title="Xóa phiên lưu trữ của khách hàng này"
                      @click="deleteSingleSession(item.sender_id)"
                    >
                      Xóa phiên
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <!-- Empty Sessions State -->
            <div v-else class="empty-sessions-state glass-card">
              <span class="empty-emoji">📭</span>
              <h5>Không có phiên hội thoại nào</h5>
              <p>Hiện chưa có khách hàng nào gửi tin nhắn qua Facebook Messenger hoặc các phiên đã được xóa.</p>
              <button class="btn btn-primary" type="button" @click="activeTab = 'simulator'">
                Thử nghiệm ngay trong Giả lập
              </button>
            </div>
          </section>
        </main>

        <!-- Modal Footer -->
        <footer class="modal-footer">
          <div class="footer-left">
            <span class="footer-status-indicator">
              <span class="indicator-dot"></span>
              DocMind RAG &amp; EvoAgentX Self-Evolution Active
            </span>
          </div>
          <button class="btn btn-secondary" type="button" @click="handleClose">
            Đóng
          </button>
        </footer>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { marked } from 'marked'
import { api } from '../api'

const props = defineProps({
  isOpen: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['close', 'toast'])

// Active tab selection
const activeTab = ref('status')

// Loading states
const isLoadingStatus = ref(false)
const isSimulating = ref(false)
const isClearingSessions = ref(false)

// Clipboard feedback
const copiedWebhook = ref(false)
const copiedToken = ref(false)

// Simulator states
const simSenderId = ref('customer_psid_test')
const simScenario = ref('')
const simMessageText = ref('')
const conversation = ref([])
const chatTranscriptRef = ref(null)
const messageInputRef = ref(null)

// Sessions filter
const sessionsFilterQuery = ref('')

// Default initial status structure
const messengerStatus = ref({
  enabled: true,
  verifyTokenConfigured: true,
  pageTokenConfigured: false,
  appSecretConfigured: false,
  verifySignature: false,
  activeSessionsCount: 0,
  webhookUrl: '/api/messenger/webhook',
  graphBaseUrl: 'https://graph.facebook.com',
  apiVersion: 'v21.0',
  recentInteractions: []
})

// Starter questions for quick testing
const starterPrompts = [
  'Xin chào! Tôi muốn tìm hiểu giải pháp DocMind',
  'DocMind hỗ trợ những định dạng tài liệu nào?',
  'Hệ thống bảo mật dữ liệu khách hàng như thế nào?',
  'Gói dịch vụ doanh nghiệp có hỗ trợ on-premise không?'
]

// Computed: Full Webhook URL with domain origin
const resolvedWebhookUrl = computed(() => {
  const origin = typeof window !== 'undefined' ? window.location.origin : 'http://localhost:8080'
  const path = messengerStatus.value.webhookUrl || '/api/messenger/webhook'
  return `${origin}${path.startsWith('/') ? path : '/' + path}`
})

// Computed: Display Verify Token
const displayVerifyToken = computed(() => {
  return 'docmind-messenger-token'
})

// Computed: Status Badge
const statusBadgeClass = computed(() => {
  if (!messengerStatus.value.enabled) return 'badge-failed'
  if (messengerStatus.value.pageTokenConfigured) return 'badge-ready'
  return 'badge-parsing'
})

const statusBadgeText = computed(() => {
  if (!messengerStatus.value.enabled) return 'Đã tắt tích hợp'
  if (messengerStatus.value.pageTokenConfigured) return 'Đang hoạt động (Meta Graph API)'
  return 'Sẵn sàng (Chế độ Giả lập Offline)'
})

// Computed: Active sessions count
const sessionsCount = computed(() => {
  if (messengerStatus.value.activeSessionsCount > 0) {
    return messengerStatus.value.activeSessionsCount
  }
  // Deduplicate PSIDs from recent interactions if count is 0
  if (messengerStatus.value.recentInteractions?.length > 0) {
    const unique = new Set(messengerStatus.value.recentInteractions.map(i => i.sender_id))
    return unique.size
  }
  return 0
})

// Computed: Filtered interactions list for Tab 3
const filteredInteractions = computed(() => {
  const list = messengerStatus.value.recentInteractions || []
  if (!sessionsFilterQuery.value.trim()) return list

  const q = sessionsFilterQuery.value.toLowerCase().trim()
  return list.filter(item => {
    return (
      (item.sender_id && item.sender_id.toLowerCase().includes(q)) ||
      (item.incoming_text && item.incoming_text.toLowerCase().includes(q)) ||
      (item.reply_text && item.reply_text.toLowerCase().includes(q)) ||
      (item.scenario && item.scenario.toLowerCase().includes(q))
    )
  })
})

// Computed: Can send message
const canSendMessage = computed(() => {
  return simMessageText.value.trim().length > 0 && simSenderId.value.trim().length > 0
})

/**
 * Enforce Meta constraint: Quick Reply title <= 20 characters
 */
const enforceMetaQuickReplyLimit = (title) => {
  if (!title) return 'Xem thêm'
  const clean = title.trim()
  if (clean.length <= 20) return clean
  return clean.substring(0, 17) + '...'
}

/**
 * Render Markdown safely using marked
 */
const renderMarkdown = (text) => {
  if (!text) return ''
  try {
    return marked.parse(text)
  } catch {
    return text
  }
}

/**
 * Truncate long text
 */
const truncateText = (text, max = 120) => {
  if (!text) return ''
  return text.length > max ? text.substring(0, max) + '...' : text
}

/**
 * Format timestamp
 */
const formatTime = (ts) => {
  if (!ts) return ''
  try {
    const date = new Date(ts)
    return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) + ' ' +
      date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })
  } catch {
    return ts
  }
}

/**
 * Copy to clipboard with visual feedback
 */
const copyText = async (text, type = 'webhook') => {
  if (!text) return
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = text
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }

    if (type === 'webhook') {
      copiedWebhook.value = true
      setTimeout(() => { copiedWebhook.value = false }, 2500)
    } else {
      copiedToken.value = true
      setTimeout(() => { copiedToken.value = false }, 2500)
    }
  } catch (e) {
    console.error('Failed to copy text:', e)
  }
}

/**
 * Generate random PSID for customer simulator
 */
const generateRandomPsid = () => {
  const randNum = Math.floor(100000 + Math.random() * 900000)
  simSenderId.value = `customer_psid_${randNum}`
}

/**
 * Load Messenger integration status from backend
 */
const loadStatus = async () => {
  isLoadingStatus.value = true
  try {
    let data = null
    if (typeof api?.getMessengerStatus === 'function') {
      data = await api.getMessengerStatus()
    } else {
      const res = await fetch('/api/messenger/status')
      if (res.ok) {
        data = await res.json()
      }
    }

    if (data) {
      messengerStatus.value = {
        enabled: data.enabled ?? true,
        verifyTokenConfigured: data.verify_token_configured ?? true,
        pageTokenConfigured: data.page_token_configured ?? false,
        appSecretConfigured: data.app_secret_configured ?? false,
        verifySignature: data.verify_signature ?? false,
        activeSessionsCount: data.active_sessions_count ?? 0,
        webhookUrl: data.webhook_url || '/api/messenger/webhook',
        graphBaseUrl: data.graph_base_url || 'https://graph.facebook.com',
        apiVersion: data.api_version || 'v21.0',
        recentInteractions: Array.isArray(data.recent_interactions) ? data.recent_interactions : []
      }
    }
  } catch (err) {
    console.warn('Backend Messenger status offline or unreachable; using local mock state', err)
    // Graceful fallback to realistic mock data
    if (messengerStatus.value.recentInteractions.length === 0) {
      messengerStatus.value.recentInteractions = [
        {
          interaction_id: 'msgr-sample-01',
          sender_id: 'customer_psid_test',
          incoming_text: 'Xin chào, DocMind hỗ trợ định dạng tài liệu nào?',
          reply_text: 'Dạ DocMind hỗ trợ PDF, DOCX, PPTX, XLSX, HTML, Markdown và hình ảnh (PNG, JPEG) với cơ chế bóc tách thông minh PyMuPDF, Docling và PaddleOCR ạ.',
          scenario: 'SALES',
          delivery_status: 'SIMULATED',
          quick_replies_count: 3,
          timestamp: new Date().toISOString(),
          simulated: true
        }
      ]
      messengerStatus.value.activeSessionsCount = 1
    }
  } finally {
    isLoadingStatus.value = false
  }
}

/**
 * Scroll chat transcript to bottom
 */
const scrollChatToBottom = () => {
  nextTick(() => {
    if (chatTranscriptRef.value) {
      chatTranscriptRef.value.scrollTop = chatTranscriptRef.value.scrollHeight
    }
  })
}

/**
 * Send user message to Simulator
 */
const sendMessage = async (textToSend) => {
  const content = (textToSend || simMessageText.value).trim()
  if (!content) return

  const senderId = simSenderId.value.trim() || 'customer_psid_test'
  const scenario = simScenario.value || undefined

  // Append user message immediately
  const userMsgId = `usr_${Date.now()}`
  conversation.value.push({
    id: userMsgId,
    sender: 'user',
    text: content,
    scenario: scenario || 'AUTO',
    timestamp: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
  })

  simMessageText.value = ''
  isSimulating.value = true
  scrollChatToBottom()

  try {
    let replyData = null

    // Try api method first
    if (typeof api?.simulateMessengerMessage === 'function') {
      replyData = await api.simulateMessengerMessage({
        sender_id: senderId,
        message: content,
        scenario
      })
    } else {
      // Direct POST fallback
      const res = await fetch('/api/messenger/simulate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sender_id: senderId,
          message: content,
          scenario
        })
      })
      if (res.ok) {
        replyData = await res.json()
      }
    }

    if (replyData) {
      // Ensure quick replies adhere to <= 20 chars
      const quickReplies = Array.isArray(replyData.quick_replies)
        ? replyData.quick_replies.map(enforceMetaQuickReplyLimit)
        : []

      conversation.value.push({
        id: `bot_${Date.now()}`,
        sender: 'bot',
        text: replyData.reply_sent || replyData.answer || 'Dạ DocMind đã ghi nhận câu hỏi.',
        scenario: replyData.scenario || scenario || 'SALES',
        interactionId: replyData.interaction_id || `sim_${Date.now()}`,
        timestamp: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
        quickReplies,
        feedbackState: null
      })

      // Add to recent interactions list
      messengerStatus.value.recentInteractions.unshift({
        interaction_id: replyData.interaction_id || `sim_${Date.now()}`,
        sender_id: senderId,
        incoming_text: content,
        reply_text: replyData.reply_sent || replyData.answer,
        scenario: replyData.scenario || scenario || 'SALES',
        delivery_status: 'SIMULATED',
        quick_replies_count: quickReplies.length,
        timestamp: new Date().toISOString(),
        simulated: true
      })
    } else {
      throw new Error('Không nhận được phản hồi giả lập từ backend')
    }
  } catch (err) {
    console.warn('Simulator offline fallback triggered:', err)
    // Offline simulated response
    setTimeout(() => {
      const fallbackReplies = [
        '👍 Hài lòng',
        '👎 Cần cải thiện',
        'Hỏi giá sản phẩm'
      ].map(enforceMetaQuickReplyLimit)

      conversation.value.push({
        id: `bot_mock_${Date.now()}`,
        sender: 'bot',
        text: `Dạ chào Anh/Chị! DocMind đã nhận được câu hỏi: **"${content}"**.\n\nHệ thống đang vận hành ở chế độ **Giả lập Facebook Messenger (Offline Demo)** với đầy đủ tính năng bóc tách block, truy xuất RAG hybrid và tự tiến hoá **EvoAgentX**.`,
        scenario: scenario || 'SALES',
        interactionId: `mock_${Date.now().toString().substring(6)}`,
        timestamp: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
        quickReplies: fallbackReplies,
        feedbackState: null
      })
      scrollChatToBottom()
    }, 600)
  } finally {
    isSimulating.value = false
    scrollChatToBottom()
  }
}

const handleSubmitMessage = () => {
  if (canSendMessage.value && !isSimulating.value) {
    sendMessage()
  }
}

const sendQuickPrompt = (prompt) => {
  simMessageText.value = prompt
  sendMessage(prompt)
}

/**
 * Handle Quick Reply pill click
 */
const handleQuickReplyClick = (replyText, botMsg) => {
  if (replyText.includes('Hài lòng') || replyText.includes('👍')) {
    triggerFeedback(botMsg, true)
    return
  }
  if (replyText.includes('Cần cải thiện') || replyText.includes('👎')) {
    triggerFeedback(botMsg, false)
    return
  }
  // Otherwise send as next user prompt
  sendMessage(replyText)
}

/**
 * Trigger feedback for a bot message (thumbs up/down)
 * Feeds directly into EvoAgentX CustomerSelfEvolutionService
 */
const triggerFeedback = async (msg, isHelpful) => {
  if (!msg || msg.feedbackState !== null) return
  msg.feedbackState = isHelpful ? 'helpful' : 'unhelpful'

  try {
    if (typeof api?.submitCustomerFeedback === 'function') {
      await api.submitCustomerFeedback({
        interactionId: msg.interactionId,
        question: 'Tương tác qua Messenger Simulator',
        answer: msg.text,
        scenario: msg.scenario,
        rating: isHelpful ? 5 : 1,
        helpful: isHelpful,
        feedback: isHelpful ? 'Hài lòng trên Messenger' : 'Cần cải thiện trên Messenger'
      })
    } else {
      // Call standard feedback endpoint
      await fetch('/api/customer-chat/feedback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          interaction_id: msg.interactionId,
          rating: isHelpful ? 5 : 1,
          helpful: isHelpful,
          scenario: msg.scenario
        })
      })
    }
  } catch (err) {
    console.warn('Feedback API call finished with fallback note:', err)
  }
}

/**
 * Reset current session conversation
 */
const resetCurrentSession = async () => {
  conversation.value = []
  const psid = simSenderId.value.trim()
  if (psid) {
    try {
      if (typeof api?.clearMessengerSessions === 'function') {
        await api.clearMessengerSessions(psid)
      } else {
        await fetch(`/api/messenger/sessions?sender_id=${encodeURIComponent(psid)}`, {
          method: 'DELETE'
        })
      }
    } catch {
      // ignore
    }
  }
}

/**
 * Load PSID from Tab 3 into Simulator (Tab 2)
 */
const loadPsidToSimulator = (psid, scenario) => {
  simSenderId.value = psid
  if (scenario) simScenario.value = scenario
  activeTab.value = 'simulator'
  nextTick(() => {
    messageInputRef.value?.focus()
  })
}

/**
 * Delete a single session by PSID
 */
const deleteSingleSession = async (psid) => {
  if (!psid) return
  try {
    if (typeof api?.clearMessengerSessions === 'function') {
      await api.clearMessengerSessions(psid)
    } else {
      await fetch(`/api/messenger/sessions?sender_id=${encodeURIComponent(psid)}`, {
        method: 'DELETE'
      })
    }
    // Remove from local list
    messengerStatus.value.recentInteractions = messengerStatus.value.recentInteractions.filter(
      i => i.sender_id !== psid
    )
  } catch (e) {
    console.error('Error clearing single session:', e)
  }
}

/**
 * Clear all Messenger sessions
 */
const confirmClearSessions = async () => {
  if (!confirm('Bạn có chắc chắn muốn xóa toàn bộ phiên hội thoại khách hàng Messenger không?')) {
    return
  }

  isClearingSessions.value = true
  try {
    if (typeof api?.clearMessengerSessions === 'function') {
      await api.clearMessengerSessions()
    } else {
      await fetch('/api/messenger/sessions', { method: 'DELETE' })
    }
    messengerStatus.value.recentInteractions = []
    messengerStatus.value.activeSessionsCount = 0
    conversation.value = []
  } catch (err) {
    console.error('Error clearing all sessions:', err)
  } finally {
    isClearingSessions.value = false
  }
}

/**
 * Close modal
 */
const handleClose = () => {
  emit('close')
}

// Handle ESC key to dismiss
const handleKeydown = (e) => {
  if (e.key === 'Escape' && props.isOpen) {
    handleClose()
  }
}

// Watchers
watch(() => props.isOpen, (newVal) => {
  if (newVal) {
    loadStatus()
    nextTick(() => {
      scrollChatToBottom()
    })
  }
})

onMounted(() => {
  if (props.isOpen) {
    loadStatus()
  }
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
/* ==========================================================================
   Modal Backdrop & Container (Dark Glassmorphism & z-index: 1100)
   ========================================================================== */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(4, 8, 16, 0.78);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
  padding: 16px;
  overflow: hidden;
  overscroll-behavior: contain;
}

.modal-container {
  width: 100%;
  max-width: 820px;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg), 0 0 32px rgba(0, 198, 255, 0.12);
  overflow: hidden;
  position: relative;
  animation: modalScaleIn 0.22s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes modalScaleIn {
  from {
    opacity: 0;
    transform: scale(0.97) translateY(10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

/* Modal Transitions */
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

/* ==========================================================================
   Modal Header
   ========================================================================== */
.modal-header {
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-subtle);
  background: rgba(15, 23, 42, 0.85);
  flex-shrink: 0;
}

.header-branding {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.messenger-avatar-badge {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  background: rgba(0, 198, 255, 0.12);
  border: 1px solid rgba(0, 198, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 0 16px rgba(0, 120, 255, 0.2);
}

.header-title-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.title-with-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.modal-title {
  font-size: 1.12rem;
  font-weight: 700;
  color: var(--text-main);
  letter-spacing: -0.01em;
  margin: 0;
}

.status-pill {
  font-size: 0.68rem;
  padding: 2px 8px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: currentColor;
}

.modal-subtitle {
  font-size: 0.8rem;
  color: var(--text-muted);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.btn-close-modal {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  background: transparent;
  border: 1px solid transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;
}

.btn-close-modal:hover {
  color: var(--text-main);
  background: rgba(255, 255, 255, 0.08);
  border-color: var(--border-subtle);
}

/* ==========================================================================
   Tab Navigation
   ========================================================================== */
.tabs-nav {
  display: flex;
  padding: 6px 16px;
  background: rgba(11, 17, 32, 0.6);
  border-bottom: 1px solid var(--border-subtle);
  gap: 8px;
  flex-shrink: 0;
  overflow-x: auto;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  font-size: 0.84rem;
  font-weight: 500;
  color: var(--text-muted);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  min-height: 40px;
}

.tab-btn:hover {
  color: var(--text-main);
  background: rgba(255, 255, 255, 0.05);
}

.tab-btn.active {
  color: #ffffff;
  background: rgba(99, 102, 241, 0.18);
  border-color: var(--border-active);
  font-weight: 600;
  box-shadow: 0 0 12px rgba(99, 102, 241, 0.2);
}

.tab-badge {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: var(--accent-primary);
  color: #ffffff;
}

/* ==========================================================================
   Modal Body & Panes
   ========================================================================== */
.modal-body {
  flex: 1;
  min-height: 0;
  padding: 20px;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}

.tab-pane {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* Common Glass Card */
.glass-card {
  background: rgba(20, 30, 51, 0.7);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 16px;
  transition: border-color 0.2s ease;
}

.glass-card:hover {
  border-color: rgba(255, 255, 255, 0.15);
}

/* ==========================================================================
   Tab 1: Status & Webhook View
   ========================================================================== */
.webhook-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-main);
  margin: 0;
}

.protocol-badge {
  font-size: 0.7rem;
  font-weight: 600;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.12);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.card-description {
  font-size: 0.82rem;
  color: var(--text-muted);
  line-height: 1.45;
  margin: 0;
}

.copy-input-group {
  display: flex;
  gap: 8px;
  align-items: center;
}

.input-readonly {
  flex: 1;
  min-width: 0;
  background: var(--bg-input);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  color: #a5b4fc;
  font-size: 0.85rem;
  padding: 10px 14px;
  outline: none;
}

.btn-copy {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: rgba(99, 102, 241, 0.18);
  border: 1px solid var(--border-active);
  color: #c7d2fe;
  font-size: 0.82rem;
  font-weight: 600;
  padding: 10px 16px;
  border-radius: var(--radius-md);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
  min-height: 44px;
}

.btn-copy:hover {
  background: rgba(99, 102, 241, 0.3);
  color: #ffffff;
}

.btn-copy.copied {
  background: rgba(16, 185, 129, 0.2);
  border-color: rgba(16, 185, 129, 0.4);
  color: #34d399;
}

/* Config Grid */
.config-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-muted);
}

.stat-value-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.token-code {
  font-size: 0.82rem;
  color: #f1f5f9;
  background: var(--bg-input);
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-subtle);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-bold {
  font-size: 0.92rem;
  font-weight: 600;
  color: var(--text-main);
}

.stat-big-number {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1;
}

.stat-unit {
  font-size: 0.78rem;
  color: var(--text-muted);
}

.stat-hint {
  font-size: 0.74rem;
  color: var(--text-dim);
  line-height: 1.4;
  margin: 0;
}

.btn-icon-tiny {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-icon-tiny:hover {
  background: rgba(255, 255, 255, 0.12);
  color: var(--text-main);
}

/* Guide Card */
.guide-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.guide-title {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
}

.steps-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.step-item {
  display: flex;
  gap: 10px;
  background: rgba(11, 17, 32, 0.5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}

.step-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--accent-gradient);
  color: #ffffff;
  font-size: 0.74rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.step-text strong {
  font-size: 0.8rem;
  color: var(--text-main);
}

.step-text span {
  font-size: 0.74rem;
  color: var(--text-muted);
  line-height: 1.35;
}

.status-actions-bar {
  display: flex;
  justify-content: flex-end;
}

/* ==========================================================================
   Tab 2: Simulator View
   ========================================================================== */
.simulator-controls-bar {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 14px;
  background: rgba(11, 17, 32, 0.6);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
}

.control-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sender-group {
  flex: 1.2;
  min-width: 200px;
}

.scenario-group {
  flex: 1;
  min-width: 180px;
}

.control-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-muted);
}

.psid-input-wrap {
  display: flex;
  align-items: center;
  background: var(--bg-input);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 2px 6px;
  gap: 6px;
}

.psid-prefix {
  font-size: 0.7rem;
  font-weight: 700;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.12);
  padding: 2px 5px;
  border-radius: 4px;
}

.input-psid {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: none;
  color: var(--text-main);
  font-size: 0.84rem;
  font-family: var(--font-mono);
  outline: none;
  padding: 6px 0;
}

.select-scenario {
  background: var(--bg-input);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  color: var(--text-main);
  font-size: 0.82rem;
  padding: 8px 10px;
  outline: none;
  cursor: pointer;
  height: 38px;
}

.btn-reset-sim {
  height: 38px;
  font-size: 0.8rem;
  padding: 0 12px;
}

/* Phone Frame */
.messenger-phone-frame {
  background: #090e1a;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  box-shadow: inset 0 2px 6px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}

.messenger-app-header {
  padding: 10px 14px;
  background: rgba(15, 23, 42, 0.95);
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.msgr-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.msgr-bot-avatar {
  position: relative;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(0, 132, 255, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
}

.active-badge-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  border: 1.5px solid #0f172a;
}

.msgr-bot-info {
  display: flex;
  flex-direction: column;
}

.msgr-bot-name {
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--text-main);
}

.msgr-bot-status {
  font-size: 0.7rem;
  color: #34d399;
}

.psid-badge-tiny {
  font-size: 0.68rem;
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-muted);
  padding: 2px 8px;
  border-radius: var(--radius-full);
}

/* Transcript Container */
.messages-container {
  min-height: 260px;
  max-height: 360px;
  overflow-y: auto;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* Empty Welcome Screen */
.empty-chat-welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 30px 16px;
  color: var(--text-muted);
}

.welcome-icon {
  font-size: 2.2rem;
  margin-bottom: 6px;
}

.welcome-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 4px;
}

.welcome-text {
  font-size: 0.8rem;
  max-width: 440px;
  line-height: 1.45;
  margin-bottom: 16px;
}

.starter-chips-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 540px;
}

.chips-label {
  width: 100%;
  font-size: 0.74rem;
  font-weight: 600;
  color: var(--text-dim);
  margin-bottom: 2px;
}

.starter-chip {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-full);
  color: var(--text-main);
  font-size: 0.78rem;
  padding: 6px 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.starter-chip:hover {
  background: rgba(99, 102, 241, 0.18);
  border-color: var(--border-active);
  transform: translateY(-1px);
}

/* Message Rows */
.message-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.row-user {
  justify-content: flex-end;
}

.row-bot {
  justify-content: flex-start;
}

.avatar-small {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.9rem;
  flex-shrink: 0;
  margin-top: 2px;
}

.bubble-wrap {
  display: flex;
  flex-direction: column;
  max-width: 82%;
  gap: 6px;
}

.wrap-user {
  align-items: flex-end;
}

.wrap-bot {
  align-items: flex-start;
}

.chat-bubble {
  padding: 10px 14px;
  border-radius: 16px;
  font-size: 0.88rem;
  line-height: 1.5;
  word-break: break-word;
}

.bubble-user {
  background: linear-gradient(135deg, #0078FF 0%, #00C6FF 100%);
  color: #ffffff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 120, 255, 0.3);
}

.user-text {
  margin: 0;
}

.bubble-bot {
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid var(--border-subtle);
  color: var(--text-main);
  border-bottom-left-radius: 4px;
}

/* Typing Indicator Animation */
.bubble-typing {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px 16px;
}

.typing-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: typingBounce 1.4s infinite ease-in-out both;
}

.typing-dot:nth-child(1) { animation-delay: -0.32s; }
.typing-dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes typingBounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* Bot Meta Bar */
.bot-meta-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  flex-wrap: wrap;
}

.meta-tags-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.scenario-pill {
  font-size: 0.65rem;
  font-weight: 600;
  background: rgba(99, 102, 241, 0.16);
  color: #a5b4fc;
  padding: 1px 6px;
  border-radius: var(--radius-full);
}

.interaction-pill {
  font-size: 0.65rem;
  color: var(--text-dim);
}

.msg-timestamp {
  font-size: 0.65rem;
  color: var(--text-dim);
}

.bot-feedback-triggers {
  display: flex;
  align-items: center;
  gap: 4px;
}

.feedback-label {
  font-size: 0.7rem;
  color: var(--text-dim);
}

.btn-feedback {
  width: 26px;
  height: 26px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.8rem;
  transition: all 0.15s ease;
}

.btn-feedback:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.12);
  transform: scale(1.08);
}

.btn-feedback.active {
  background: rgba(16, 185, 129, 0.2);
  border-color: rgba(16, 185, 129, 0.4);
}

.feedback-acknowledged {
  font-size: 0.7rem;
  color: #34d399;
  font-weight: 500;
}

/* Quick Replies Container (Meta Pills) */
.quick-replies-container {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.quick-reply-pill {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  background: rgba(0, 132, 255, 0.12);
  border: 1px solid rgba(0, 132, 255, 0.35);
  border-radius: var(--radius-full);
  color: #60a5fa;
  font-size: 0.78rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.18s ease;
  min-height: 32px;
}

.quick-reply-pill:hover {
  background: rgba(0, 132, 255, 0.25);
  border-color: #60a5fa;
  color: #ffffff;
  transform: translateY(-1px);
}

/* Simulator Input Area */
.simulator-input-area {
  padding: 10px 14px;
  background: rgba(15, 23, 42, 0.95);
  border-top: 1px solid var(--border-subtle);
}

.input-form {
  display: flex;
  gap: 8px;
  align-items: center;
}

.input-message {
  flex: 1;
  min-width: 0;
  background: var(--bg-input);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-full);
  color: var(--text-main);
  font-size: 0.86rem;
  padding: 10px 16px;
  outline: none;
  transition: border-color 0.2s ease;
}

.input-message:focus {
  border-color: #0078FF;
}

.btn-send-message {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: linear-gradient(135deg, #0078FF 0%, #00C6FF 100%);
  color: #ffffff;
  border: none;
  border-radius: var(--radius-full);
  padding: 10px 18px;
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  min-height: 44px;
}

.btn-send-message:hover:not(:disabled) {
  opacity: 0.92;
  transform: translateY(-1px);
}

.btn-send-message:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

/* ==========================================================================
   Tab 3: Recent Customer Sessions View
   ========================================================================== */
.sessions-header-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.sessions-stat-wrap {
  display: flex;
  align-items: center;
  gap: 20px;
}

.stat-bubble {
  display: flex;
  flex-direction: column;
}

.stat-bubble .stat-number {
  font-size: 1.3rem;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1;
}

.stat-bubble .stat-text {
  font-size: 0.74rem;
  color: var(--text-muted);
}

.btn-danger-soft {
  background: rgba(244, 63, 94, 0.12);
  border: 1px solid rgba(244, 63, 94, 0.3);
  color: #fb7185;
  font-size: 0.8rem;
  font-weight: 600;
  padding: 8px 14px;
  border-radius: var(--radius-md);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.18s ease;
  min-height: 40px;
}

.btn-danger-soft:hover:not(:disabled) {
  background: rgba(244, 63, 94, 0.22);
  color: #ffffff;
}

.btn-danger-soft:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.sessions-search-bar {
  display: flex;
}

.search-input {
  width: 100%;
  background: var(--bg-input);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  color: var(--text-main);
  font-size: 0.84rem;
  padding: 9px 14px;
  outline: none;
}

.search-input:focus {
  border-color: var(--border-active);
}

/* Interactions List */
.interactions-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.interaction-item-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
}

.item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.psid-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.psid-avatar-icon {
  font-size: 0.85rem;
}

.psid-label {
  font-size: 0.84rem;
  color: var(--text-main);
}

.scenario-badge {
  font-size: 0.68rem;
  font-weight: 600;
  color: #c7d2fe;
  background: rgba(99, 102, 241, 0.16);
  padding: 2px 7px;
  border-radius: var(--radius-full);
}

.delivery-badge {
  font-size: 0.68rem;
  font-weight: 600;
  padding: 2px 7px;
  border-radius: var(--radius-full);
}

.interaction-time {
  font-size: 0.72rem;
  color: var(--text-dim);
}

.interaction-dialog-preview {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: rgba(11, 17, 32, 0.5);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
}

.preview-row {
  display: flex;
  gap: 6px;
  font-size: 0.8rem;
  line-height: 1.4;
}

.speaker-tag {
  font-weight: 700;
  flex-shrink: 0;
}

.customer-tag {
  color: #38bdf8;
}

.bot-tag {
  color: #a5b4fc;
}

.preview-text {
  color: var(--text-muted);
}

.text-bot-reply {
  color: var(--text-main);
}

.item-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 4px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.quick-reply-count-info {
  font-size: 0.72rem;
  color: var(--text-dim);
  display: flex;
  align-items: center;
  gap: 4px;
}

.item-action-btns {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-text-action {
  background: none;
  border: none;
  color: #38bdf8;
  font-size: 0.76rem;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  transition: opacity 0.15s ease;
}

.btn-text-action:hover {
  text-decoration: underline;
  opacity: 0.85;
}

.btn-text-action.text-danger {
  color: #fb7185;
}

/* Empty Sessions State */
.empty-sessions-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 20px;
  gap: 8px;
}

.empty-emoji {
  font-size: 2.2rem;
}

.empty-sessions-state h5 {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-main);
  margin: 0;
}

.empty-sessions-state p {
  font-size: 0.8rem;
  color: var(--text-muted);
  max-width: 400px;
  margin: 0 0 8px 0;
}

/* ==========================================================================
   Modal Footer
   ========================================================================== */
.modal-footer {
  padding: 12px 20px;
  border-top: 1px solid var(--border-subtle);
  background: rgba(15, 23, 42, 0.85);
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.footer-left {
  display: flex;
  align-items: center;
}

.footer-status-indicator {
  font-size: 0.74rem;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}

.indicator-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 6px rgba(52, 211, 153, 0.5);
}

.spin-anim {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ==========================================================================
   Responsive Adaptations (Mobile 320px - 767px & Tablet)
   ========================================================================== */
@media (max-width: 767px) {
  .modal-backdrop {
    padding: 8px;
    align-items: flex-end; /* Mobile bottom-sheet alignment */
  }

  .modal-container {
    max-width: 100vw;
    max-height: 92dvh;
    border-bottom-left-radius: 0;
    border-bottom-right-radius: 0;
  }

  .modal-header {
    padding: 12px 14px;
  }

  .modal-title {
    font-size: 0.98rem;
  }

  .modal-subtitle {
    display: none; /* Hide secondary subtitle on mobile */
  }

  .tabs-nav {
    padding: 6px 10px;
    gap: 4px;
  }

  .tab-btn {
    padding: 6px 10px;
    font-size: 0.78rem;
  }

  .modal-body {
    padding: 14px;
  }

  .config-grid,
  .steps-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .copy-input-group {
    flex-direction: column;
    align-items: stretch;
  }

  .btn-copy {
    justify-content: center;
    width: 100%;
  }

  .simulator-controls-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .sender-group,
  .scenario-group {
    min-width: 100%;
  }

  .btn-reset-sim {
    width: 100%;
    justify-content: center;
  }

  .messages-container {
    min-height: 220px;
    max-height: 280px;
    padding: 10px;
  }

  .bubble-wrap {
    max-width: 92%;
  }

  .btn-send-label {
    display: none; /* Keep paper-plane icon only on small screens */
  }

  .btn-send-message {
    padding: 10px 14px;
  }

  .sessions-header-card {
    flex-direction: column;
    align-items: stretch;
  }

  .btn-danger-soft {
    justify-content: center;
    width: 100%;
  }

  .item-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
