<template>
  <Teleport to="body">
    <div
      class="modal-backdrop astrbot-modal-backdrop"
      data-testid="astrbot-modal-backdrop"
      @click.self="$emit('close')"
    >
      <div
        class="modal-container astrbot-modal glass-panel animate-scale-in"
        role="dialog"
        aria-modal="true"
        aria-label="AstrBot Multi-Platform Omni-Channel Hub & Management Dashboard"
        data-testid="astrbot-modal"
      >
        <!-- Header -->
        <header class="astrbot-header">
          <div class="astrbot-header-left">
            <div class="astrbot-brand-badge">
              <span class="astrbot-brand-icon">🛰️</span>
              <span class="astrbot-pulse-dot" :class="{ online: overview.enabled }"></span>
            </div>
            <div class="astrbot-title-block">
              <div class="astrbot-title-row">
                <h2>AstrBot Multi-Platform Control Center</h2>
                <span class="badge badge-indigo">{{ overview.version || 'v4.14.0-docmind' }}</span>
                <span class="badge badge-emerald">
                  {{ overview.stats?.running_bots || 0 }}/{{ overview.stats?.total_bots || 0 }} Bots Running
                </span>
                <span class="badge badge-cyan">
                  {{ overview.stats?.supported_platform_types || 19 }} Platform Adapters
                </span>
              </div>
              <p class="astrbot-subtitle">
                Trung tâm kết nối 19 nền tảng nhắn tin (Telegram, Discord, Slack, Lark, LINE, WeCom, WeChat, QQ, OneBot v11, Satori, KOOK, Mattermost, Misskey, Messenger, WebChat) · Định tuyến UMO · Star Slash Commands · Dior KB &amp; EvoAgentX
              </p>
            </div>
          </div>

          <div class="astrbot-header-actions">
            <button
              class="btn btn-secondary btn-sm"
              data-testid="astrbot-open-catalog-btn"
              @click="showAddPlatformModal = true"
            >
              <span>➕ Thêm Nền tảng</span>
            </button>
            <button
              class="btn btn-secondary btn-sm"
              :disabled="loading"
              data-testid="astrbot-refresh-btn"
              @click="fetchOverview"
            >
              <span>{{ loading ? '⏳' : '🔄' }} Làm mới</span>
            </button>
            <button
              class="btn btn-ghost btn-icon"
              title="Đóng bảng điều khiển AstrBot"
              data-testid="astrbot-close-btn"
              @click="$emit('close')"
            >
              ✕
            </button>
          </div>
        </header>

        <!-- Toast / Banner Notification -->
        <div v-if="bannerMessage" class="astrbot-toast-banner" :class="bannerType" data-testid="astrbot-toast">
          <span>{{ bannerMessage }}</span>
          <button class="btn-toast-close" @click="bannerMessage = ''">✕</button>
        </div>

        <!-- Navigation Tabs -->
        <nav class="astrbot-tabs" aria-label="AstrBot Management Sections">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            class="astrbot-tab-btn"
            :class="{ active: activeTab === tab.id }"
            :data-testid="`astrbot-tab-${tab.id}`"
            @click="activeTab = tab.id"
          >
            <span class="tab-icon">{{ tab.icon }}</span>
            <span class="tab-label">{{ tab.label }}</span>
            <span v-if="tab.count !== undefined" class="tab-badge">{{ tab.count }}</span>
          </button>
        </nav>

        <!-- Body Content -->
        <div class="astrbot-body">
          <!-- =============================================================== -->
          <!-- TAB 1: NỀN TẢNG CHAT & ADAPTERS (Bots.vue + PlatformPage.vue)   -->
          <!-- =============================================================== -->
          <div v-if="activeTab === 'platforms'" class="astrbot-tab-panel" data-testid="astrbot-panel-platforms">
            <div class="platforms-split-layout">
              <!-- Left Sidebar: Bot Instances List -->
              <aside class="bot-sidebar">
                <div class="bot-sidebar-header">
                  <div class="bot-sidebar-title">
                    <span>🤖 Thực thể Bot ({{ filteredBots.length }})</span>
                    <button
                      class="btn btn-primary btn-xs"
                      data-testid="astrbot-sidebar-add-btn"
                      @click="showAddPlatformModal = true"
                    >
                      + Thêm Bot
                    </button>
                  </div>
                  <div class="bot-filter-row">
                    <input
                      v-model="botSearch"
                      type="text"
                      class="input input-sm"
                      placeholder="Tìm theo ID hoặc nền tảng..."
                      data-testid="astrbot-bot-search"
                    />
                  </div>
                </div>

                <div class="bot-list">
                  <div
                    v-for="bot in filteredBots"
                    :key="bot.id"
                    class="bot-list-item"
                    :class="{ active: selectedBot?.id === bot.id, disabled: !bot.enable }"
                    :data-testid="`astrbot-bot-item-${bot.id}`"
                    @click="selectBot(bot)"
                  >
                    <div class="bot-item-icon">{{ getPlatformIcon(bot.type) }}</div>
                    <div class="bot-item-info">
                      <div class="bot-item-top">
                        <span class="bot-item-id">{{ bot.id }}</span>
                        <span
                          class="status-pill"
                          :class="bot.status === 'running' ? 'running' : bot.status === 'error' ? 'error' : 'stopped'"
                        >
                          {{ bot.status }}
                        </span>
                      </div>
                      <div class="bot-item-meta">
                        <span>{{ bot.display_name }}</span>
                        <span>· {{ bot.message_count || 0 }} tin</span>
                        <span v-if="bot.unified_webhook" class="webhook-chip" title="Unified Webhook Mode">🔗 WH</span>
                      </div>
                    </div>
                    <label class="switch-toggle" @click.stop>
                      <input
                        type="checkbox"
                        :checked="bot.enable"
                        :data-testid="`astrbot-toggle-${bot.id}`"
                        @change="handleToggleBot(bot, $event.target.checked)"
                      />
                      <span class="switch-slider"></span>
                    </label>
                  </div>
                </div>
              </aside>

              <!-- Right Content: Platform Instance Editor & Route Bindings -->
              <section v-if="selectedBot" class="bot-editor-pane" data-testid="astrbot-bot-editor">
                <div class="bot-editor-header">
                  <div class="bot-editor-title">
                    <span class="editor-platform-icon">{{ getPlatformIcon(selectedBot.type) }}</span>
                    <div>
                      <h3>{{ selectedBot.id }}</h3>
                      <p>
                        Loại Adapter: <code>{{ selectedBot.type }}</code> ({{ selectedBot.display_name }})
                        <a
                          v-if="selectedPlatformMeta?.doc_url"
                          :href="selectedPlatformMeta.doc_url"
                          target="_blank"
                          rel="noopener noreferrer"
                          class="doc-link"
                        >
                          📖 Tài liệu hướng dẫn ↗
                        </a>
                      </p>
                    </div>
                  </div>
                  <div class="bot-editor-actions">
                    <button
                      v-if="selectedPlatformMeta?.support_qr_login"
                      class="btn btn-secondary btn-sm"
                      data-testid="astrbot-qr-btn"
                      @click="handleOpenQrModal(selectedBot)"
                    >
                      📱 Quét QR Kết nối
                    </button>
                    <button
                      v-if="selectedBot.unified_webhook"
                      class="btn btn-secondary btn-sm"
                      data-testid="astrbot-webhook-info-btn"
                      @click="webhookInspectBot = selectedBot"
                    >
                      🔗 Unified Webhook URL
                    </button>
                    <button
                      class="btn btn-secondary btn-sm"
                      data-testid="astrbot-test-bot-btn"
                      @click="handleTestBot(selectedBot)"
                    >
                      🩺 Kiểm tra Kết nối
                    </button>
                    <button
                      class="btn btn-primary btn-sm"
                      data-testid="astrbot-save-bot-btn"
                      @click="handleSaveBot"
                    >
                      💾 Lưu &amp; Reload
                    </button>
                    <button
                      class="btn btn-danger-outline btn-sm"
                      data-testid="astrbot-delete-bot-btn"
                      @click="handleDeleteBot(selectedBot)"
                    >
                      🗑️ Xoá
                    </button>
                  </div>
                </div>

                <!-- Connection & Webhook Banner -->
                <div class="webhook-strip">
                  <div class="webhook-strip-left">
                    <span class="webhook-label">Callback URL Hợp nhất (Unified Webhook):</span>
                    <code class="webhook-code">{{ fullWebhookUrl(selectedBot.webhook_url) }}</code>
                  </div>
                  <div class="webhook-strip-right">
                    <button class="btn btn-secondary btn-xs" @click="copyText(fullWebhookUrl(selectedBot.webhook_url))">
                      📋 Sao chép URL
                    </button>
                    <button
                      class="btn btn-secondary btn-xs"
                      @click="jumpToSimulatorWithBot(selectedBot)"
                    >
                      💬 Giả lập Chat trên Bot này
                    </button>
                  </div>
                </div>

                <!-- Dynamic Schema Configuration Form -->
                <div class="editor-grid">
                  <div class="editor-card">
                    <h4>⚙️ Cấu hình Thông số Adapter ({{ selectedBot.display_name }})</h4>
                    <div class="form-grid">
                      <div class="form-group">
                        <label>Trạng thái kích hoạt (Enable)</label>
                        <select v-model="editForm.enable" class="input">
                          <option :value="true">🟢 Đang bật (Running)</option>
                          <option :value="false">⚪ Tạm dừng (Stopped)</option>
                        </select>
                      </div>
                      <div class="form-group">
                        <label>Chế độ Unified Webhook</label>
                        <select v-model="editForm.unified_webhook" class="input">
                          <option :value="true">🔗 Bật Unified Webhook (/api/astrbot/webhooks/platforms/...)</option>
                          <option :value="false">⚡ WebSocket / Long-Polling trực tiếp</option>
                        </select>
                      </div>
                      <div
                        v-for="field in selectedPlatformMeta?.fields || []"
                        :key="field.key"
                        class="form-group"
                      >
                        <label>
                          {{ field.label }}
                          <span v-if="field.required" class="req-star">*</span>
                        </label>
                        <select
                          v-if="field.type === 'select'"
                          v-model="editForm.config[field.key]"
                          class="input"
                        >
                          <option v-for="opt in field.options || []" :key="opt" :value="opt">{{ opt }}</option>
                        </select>
                        <select
                          v-else-if="field.type === 'boolean'"
                          v-model="editForm.config[field.key]"
                          class="input"
                        >
                          <option :value="true">Bật (True)</option>
                          <option :value="false">Tắt (False)</option>
                        </select>
                        <input
                          v-else
                          v-model="editForm.config[field.key]"
                          :type="field.type === 'password' && !showSecrets ? 'password' : field.type === 'number' ? 'number' : 'text'"
                          class="input"
                          :placeholder="field.placeholder"
                        />
                        <small class="field-hint">{{ field.description }}</small>
                      </div>
                    </div>
                    <div class="secret-toggle-row">
                      <label class="checkbox-label">
                        <input v-model="showSecrets" type="checkbox" />
                        <span>Hiển thị Token / Secret dưới dạng văn bản rõ</span>
                      </label>
                    </div>
                  </div>

                  <!-- Default Routing & UMO Route Table -->
                  <div class="editor-card">
                    <h4>🧭 Định tuyến AI, Persona &amp; Kịch bản (UMO Routing)</h4>
                    <div class="form-grid">
                      <div class="form-group">
                        <label>Nhân cách AI Mặc định (Default Persona)</label>
                        <select v-model="editForm.default_persona_id" class="input">
                          <option v-for="p in overview.personas" :key="p.id" :value="p.id">
                            {{ p.name }} ({{ p.id }})
                          </option>
                        </select>
                      </div>
                      <div class="form-group">
                        <label>Kịch bản Tư vấn Mặc định</label>
                        <select v-model="editForm.default_scenario" class="input">
                          <option value="AUTO">🤖 AUTO — Tự động nhận diện ý định</option>
                          <option value="SALES">🛍️ SALES — Tư vấn bán hàng &amp; Dior Luxury</option>
                          <option value="SERVICE_SUPPORT">🛠️ SERVICE_SUPPORT — Hỗ trợ kỹ thuật &amp; Tài liệu</option>
                          <option value="WARRANTY_RETURN">🛡️ WARRANTY_RETURN — Bảo hành &amp; Đổi trả</option>
                          <option value="ORDER_BILLING">📦 ORDER_BILLING — Đơn hàng &amp; Thanh toán</option>
                        </select>
                      </div>
                      <div class="form-group">
                        <label>Hồ sơ Cấu hình Dự phòng (Fallback Profile ID)</label>
                        <input v-model="editForm.fallback_profile_id" type="text" class="input" />
                      </div>
                    </div>

                    <div class="umo-routes-box">
                      <div class="umo-routes-header">
                        <h5>📌 Bảng Định tuyến UMO Chi tiết ({{ selectedBot.umo_routes?.length || 0 }})</h5>
                      </div>
                      <div class="umo-route-add-row">
                        <select v-model="newRouteMsgType" class="input input-sm">
                          <option value="*">Tất cả loại tin (*)</option>
                          <option value="PrivateMessage">PrivateMessage (Cá nhân)</option>
                          <option value="GroupMessage">GroupMessage (Nhóm/Kênh)</option>
                        </select>
                        <input
                          v-model="newRouteSessionId"
                          type="text"
                          class="input input-sm"
                          placeholder="Session ID (VD: vip_client_8801 hoặc *)"
                        />
                        <select v-model="newRoutePersonaId" class="input input-sm">
                          <option v-for="p in overview.personas" :key="p.id" :value="p.id">{{ p.id }}</option>
                        </select>
                        <select v-model="newRouteScenario" class="input input-sm">
                          <option value="SALES">SALES</option>
                          <option value="SERVICE_SUPPORT">SERVICE_SUPPORT</option>
                          <option value="WARRANTY_RETURN">WARRANTY_RETURN</option>
                          <option value="ORDER_BILLING">ORDER_BILLING</option>
                        </select>
                        <button
                          class="btn btn-primary btn-xs"
                          data-testid="astrbot-add-route-btn"
                          @click="handleAddRoute"
                        >
                          + Gắn Route
                        </button>
                      </div>

                      <div v-if="selectedBot.umo_routes?.length" class="route-items-list">
                        <div v-for="rt in selectedBot.umo_routes" :key="rt.umo" class="route-chip-row">
                          <code>{{ rt.umo }}</code>
                          <span class="route-arrow">➔</span>
                          <span class="badge badge-indigo">{{ rt.persona_id }}</span>
                          <span class="badge badge-cyan">{{ rt.scenario }}</span>
                          <button class="btn-mini-del" @click="handleRemoveRoute(rt.umo)">✕</button>
                        </div>
                      </div>
                      <p v-else class="empty-hint">
                        Chưa có quy tắc định tuyến riêng. Toàn bộ tin nhắn từ <code>{{ selectedBot.id }}</code> sẽ dùng cấu hình mặc định phía trên.
                      </p>
                    </div>
                  </div>
                </div>
              </section>
            </div>
          </div>

          <!-- =============================================================== -->
          <!-- TAB 2: GIẢ LẬP ĐA KÊNH & STAR SLASH COMMANDS                     -->
          <!-- =============================================================== -->
          <div v-else-if="activeTab === 'simulator'" class="astrbot-tab-panel" data-testid="astrbot-panel-simulator">
            <div class="simulator-layout">
              <!-- Left Configuration & Quick Presets -->
              <div class="sim-controls-card">
                <h3>🧪 Giả lập Gói tin Sự kiện Đa Nền tảng &amp; Lệnh Star</h3>
                <p class="card-desc">
                  Kiểm thử toàn bộ đường ống sự kiện AstrBot (<code>AstrMessageEvent</code> ➔ Định tuyến UMO ➔ Star Slash Commands / Hybrid RAG / Dior SOP KB / EvoAgentX) mà không cần mở ứng dụng chat bên ngoài.
                </p>

                <div class="form-grid">
                  <div class="form-group">
                    <label>Chọn Bot Adapter kết nối</label>
                    <select v-model="simForm.bot_id" class="input" data-testid="astrbot-sim-bot-select">
                      <option v-for="b in overview.bots" :key="b.id" :value="b.id">
                        {{ getPlatformIcon(b.type) }} {{ b.id }} ({{ b.display_name }})
                      </option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label>Loại sự kiện tin nhắn (Message Type)</label>
                    <select v-model="simForm.message_type" class="input" data-testid="astrbot-sim-msgtype">
                      <option value="PrivateMessage">PrivateMessage (Tin nhắn riêng)</option>
                      <option value="GroupMessage">GroupMessage (Nhóm / Guild / Kênh)</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label>Session / Channel ID</label>
                    <input v-model="simForm.session_id" type="text" class="input" placeholder="vip_client_8801" />
                  </div>
                  <div class="form-group">
                    <label>Tên hiển thị người gửi</label>
                    <input v-model="simForm.sender_name" type="text" class="input" placeholder="Chị Minh Thư (VIP)" />
                  </div>
                  <div class="form-group">
                    <label>Ghi đè Persona (Tuỳ chọn)</label>
                    <select v-model="simForm.persona_id" class="input">
                      <option value="">-- Theo cấu hình định tuyến UMO --</option>
                      <option v-for="p in overview.personas" :key="p.id" :value="p.id">{{ p.name }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label>Ghi đè Kịch bản (Tuỳ chọn)</label>
                    <select v-model="simForm.scenario" class="input">
                      <option value="AUTO">AUTO (Mặc định)</option>
                      <option value="SALES">SALES (Tư vấn bán hàng)</option>
                      <option value="SERVICE_SUPPORT">SERVICE_SUPPORT (Hỗ trợ kỹ thuật)</option>
                      <option value="WARRANTY_RETURN">WARRANTY_RETURN (Bảo hành / Đổi trả)</option>
                      <option value="ORDER_BILLING">ORDER_BILLING (Đơn hàng / Thanh toán)</option>
                    </select>
                  </div>
                </div>

                <div class="umo-preview-bar">
                  <span>Định danh UMO sẽ tạo:</span>
                  <code data-testid="astrbot-sim-umo-preview">
                    {{ simForm.bot_id }}:{{ simForm.message_type }}:{{ simForm.session_id || 'default' }}
                  </code>
                </div>

                <!-- Star Slash Commands Quick Bar -->
                <div class="preset-section">
                  <h5>⚡ Lệnh quản trị nội bộ AstrBot Star Slash Commands:</h5>
                  <div class="preset-chips">
                    <button
                      v-for="cmd in starCommands"
                      :key="cmd.text"
                      class="chip-btn cmd-chip"
                      :data-testid="`astrbot-cmd-${cmd.label}`"
                      @click="sendSimulatedMessage(cmd.text)"
                    >
                      <code>{{ cmd.text }}</code>
                      <span>{{ cmd.desc }}</span>
                    </button>
                  </div>
                </div>

                <!-- Customer Multi-Platform Scenarios -->
                <div class="preset-section">
                  <h5>💎 Kịch bản Khách hàng Đa kênh mẫu (Dior &amp; DocMind RAG):</h5>
                  <div class="preset-chips">
                    <button
                      v-for="(sample, idx) in sampleQuestions"
                      :key="idx"
                      class="chip-btn"
                      :data-testid="`astrbot-sample-${idx}`"
                      @click="sendSimulatedMessage(sample.text, sample.botId, sample.scenario)"
                    >
                      <span>{{ sample.label }}</span>
                    </button>
                  </div>
                </div>
              </div>

              <!-- Right Live Chat Feed -->
              <div class="sim-chat-card">
                <div class="sim-chat-header">
                  <div>
                    <h4>💬 Luồng Hội thoại Đa Kênh Thời gian thực</h4>
                    <span class="sim-chat-sub">Tích hợp trực tiếp với CustomerChatService, Dior KB &amp; EvoAgentX</span>
                  </div>
                  <button class="btn btn-secondary btn-xs" @click="simMessages = []">🧹 Xoá màn hình</button>
                </div>

                <div class="sim-messages-viewport" data-testid="astrbot-sim-messages">
                  <div v-if="!simMessages.length" class="sim-empty-state">
                    <div class="sim-empty-icon">🛰️</div>
                    <p>Chọn một lệnh Star Slash Command (VD: <code>/help</code>, <code>/status</code>) hoặc nhập câu hỏi khách hàng bên dưới để kiểm thử đường ống đa nền tảng.</p>
                  </div>

                  <div
                    v-for="(msg, index) in simMessages"
                    :key="index"
                    class="sim-turn-block"
                  >
                    <!-- Incoming User Message -->
                    <div class="sim-bubble user-bubble">
                      <div class="bubble-meta">
                        <span class="platform-tag">{{ getPlatformIcon(msg.platform_type) }} {{ msg.platform_id }}</span>
                        <code>{{ msg.umo }}</code>
                        <span>· {{ msg.sender_name }}</span>
                      </div>
                      <div class="bubble-text">{{ msg.incoming_message }}</div>
                    </div>

                    <!-- Outgoing AstrBot Reply -->
                    <div class="sim-bubble bot-bubble" :class="{ 'cmd-reply': msg.is_command }">
                      <div class="bubble-meta">
                        <span class="badge badge-indigo">🤖 AstrBot Reply</span>
                        <span class="badge badge-cyan">{{ msg.scenario }}</span>
                        <span class="badge badge-purple">{{ msg.persona_id }}</span>
                        <span class="badge badge-emerald">EvoGen #{{ msg.evo_generation }}</span>
                        <span class="latency-tag">{{ msg.latency_ms }}ms</span>
                      </div>
                      <div v-if="msg.stages?.length" class="trace-stages">
                        <span v-for="(st, sIdx) in msg.stages" :key="st" class="stage-pill">
                          ✓ {{ st }}<span v-if="sIdx < msg.stages.length - 1" class="stage-arrow">→</span>
                        </span>
                      </div>
                      <div class="bubble-text markdown-pre">{{ msg.reply_sent }}</div>

                      <div v-if="msg.citations?.length && (msg.persona_id === 'docmind_rag_expert' || msg.persona_id === 'docmind_rag_architect')" class="sim-citations">
                        <span
                          v-for="(cit, cIdx) in msg.citations"
                          :key="cIdx"
                          class="cit-pill"
                        >
                          📄 {{ cit.filename || cit.document_id }} (Tr.{{ cit.page_number || 1 }})
                        </span>
                      </div>

                      <div v-if="msg.quick_replies?.length" class="sim-quick-replies">
                        <button
                          v-for="qr in msg.quick_replies"
                          :key="qr"
                          class="qr-pill-btn"
                          @click="sendSimulatedMessage(qr)"
                        >
                          {{ qr }}
                        </button>
                      </div>
                    </div>
                  </div>
                </div>

                <form class="sim-input-bar" @submit.prevent="sendSimulatedMessage()">
                  <input
                    v-model="simForm.message"
                    type="text"
                    class="input"
                    placeholder="Nhập tin nhắn khách hàng hoặc lệnh /help, /status, /persona, /evolve..."
                    data-testid="astrbot-sim-input"
                  />
                  <button
                    type="submit"
                    class="btn btn-primary"
                    :disabled="simSending || !simForm.message.trim()"
                    data-testid="astrbot-sim-send-btn"
                  >
                    {{ simSending ? '⏳ Đang xử lý...' : '🚀 Gửi sự kiện' }}
                  </button>
                </form>
              </div>
            </div>
          </div>

          <!-- =============================================================== -->
          <!-- TAB 3: QUẢN LÝ PHIÊN UMO & QUY TẮC (SessionManagementPage.vue)  -->
          <!-- =============================================================== -->
          <div v-else-if="activeTab === 'sessions'" class="astrbot-tab-panel" data-testid="astrbot-panel-sessions">
            <div class="panel-toolbar">
              <div class="toolbar-left">
                <h3>🗂️ Quản lý Phiên Hội thoại Hợp nhất (UMO Sessions &amp; Custom Rules)</h3>
                <p>Mỗi cuộc hội thoại đa nền tảng được định danh bằng chuẩn <code>{platform_id}:{message_type}:{session_id}</code>.</p>
              </div>
              <div class="toolbar-right">
                <input
                  v-model="sessionSearch"
                  type="text"
                  class="input input-sm"
                  placeholder="Tìm kiếm UMO, khách hàng, nội dung..."
                  data-testid="astrbot-session-search"
                />
                <button
                  class="btn btn-danger-outline btn-sm"
                  data-testid="astrbot-clear-sessions-btn"
                  @click="handleClearAllSessions"
                >
                  🗑️ Xoá toàn bộ phiên
                </button>
              </div>
            </div>

            <div class="sessions-table-wrapper">
              <table class="astrbot-table">
                <thead>
                  <tr>
                    <th>Định danh UMO &amp; Khách hàng</th>
                    <th>Nền tảng</th>
                    <th>AI Tự động (LLM)</th>
                    <th>TTS</th>
                    <th>Persona gắn kèm</th>
                    <th>Kịch bản</th>
                    <th>Lượt nhớ &amp; Tin nhắn cuối</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="sess in filteredSessions" :key="sess.umo" :data-testid="`astrbot-session-row-${sess.session_id}`">
                    <td>
                      <div class="umo-cell">
                        <code>{{ sess.umo }}</code>
                        <input
                          :value="sess.custom_name || sess.sender_name"
                          type="text"
                          class="input input-xs alias-input"
                          placeholder="Đặt biệt danh phiên..."
                          @change="handleUpdateSessionRule(sess, { custom_name: $event.target.value })"
                        />
                      </div>
                    </td>
                    <td>
                      <span class="badge badge-indigo">
                        {{ getPlatformIcon(sess.platform_type) }} {{ sess.platform_id }}
                      </span>
                      <div class="sub-cell">{{ sess.message_type }}</div>
                    </td>
                    <td>
                      <label class="switch-toggle">
                        <input
                          type="checkbox"
                          :checked="sess.llm_enabled"
                          @change="handleUpdateSessionRule(sess, { llm_enabled: $event.target.checked })"
                        />
                        <span class="switch-slider"></span>
                      </label>
                    </td>
                    <td>
                      <label class="switch-toggle">
                        <input
                          type="checkbox"
                          :checked="sess.tts_enabled"
                          @change="handleUpdateSessionRule(sess, { tts_enabled: $event.target.checked })"
                        />
                        <span class="switch-slider"></span>
                      </label>
                    </td>
                    <td>
                      <select
                        :value="sess.persona_id"
                        class="input input-xs"
                        @change="handleUpdateSessionRule(sess, { persona_id: $event.target.value })"
                      >
                        <option v-for="p in overview.personas" :key="p.id" :value="p.id">{{ p.id }}</option>
                      </select>
                    </td>
                    <td>
                      <select
                        :value="sess.scenario"
                        class="input input-xs"
                        @change="handleUpdateSessionRule(sess, { scenario: $event.target.value })"
                      >
                        <option value="AUTO">AUTO</option>
                        <option value="SALES">SALES</option>
                        <option value="SERVICE_SUPPORT">SERVICE_SUPPORT</option>
                        <option value="WARRANTY_RETURN">WARRANTY_RETURN</option>
                        <option value="ORDER_BILLING">ORDER_BILLING</option>
                      </select>
                    </td>
                    <td>
                      <div class="last-msg-cell">
                        <span class="badge badge-cyan">{{ sess.message_count }} lượt</span>
                        <p class="truncate-text" :title="sess.last_message">{{ sess.last_message || '(Chưa có tin nhắn)' }}</p>
                      </div>
                    </td>
                    <td>
                      <div class="row-actions">
                        <button class="btn btn-secondary btn-xs" @click="openSessionChatInSimulator(sess)">
                          💬 Chat
                        </button>
                        <button class="btn btn-danger-outline btn-xs" @click="handleDeleteSingleSession(sess.umo)">
                          ✕
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- =============================================================== -->
          <!-- TAB 4: PERSONAS & NHÀ CUNG CẤP MÔ HÌNH LLM                      -->
          <!-- =============================================================== -->
          <div
            v-else-if="activeTab === 'personas_providers'"
            class="astrbot-tab-panel"
            data-testid="astrbot-panel-personas-providers"
          >
            <div class="dual-column-grid">
              <!-- Column 1: Personas Manager -->
              <div class="editor-card">
                <div class="card-header-row">
                  <div>
                    <h3>🎭 Quản lý Nhân cách AI (AstrBot Personas)</h3>
                    <p class="card-desc">Thiết lập System Prompt, nhiệt độ sáng tạo và bộ công cụ (Tools) cho từng nhân cách tư vấn.</p>
                  </div>
                </div>

                <!-- New/Edit Persona Form -->
                <div class="sub-form-box">
                  <h5>{{ personaForm.id ? '✏️ Cập nhật / Tạo mới Persona' : '➕ Tạo Persona mới' }}</h5>
                  <div class="form-grid">
                    <div class="form-group">
                      <label>Mã định danh (Persona ID)</label>
                      <input v-model="personaForm.id" type="text" class="input input-sm" placeholder="vd: dior_ skincare_specialist" data-testid="astrbot-persona-id-input" />
                    </div>
                    <div class="form-group">
                      <label>Tên hiển thị</label>
                      <input v-model="personaForm.name" type="text" class="input input-sm" placeholder="Chuyên gia Dưỡng da Dior" data-testid="astrbot-persona-name-input" />
                    </div>
                    <div class="form-group">
                      <label>Thư mục phân loại (Folder)</label>
                      <input v-model="personaForm.folder" type="text" class="input input-sm" placeholder="Luxury Retail" />
                    </div>
                    <div class="form-group">
                      <label>Kịch bản Mặc định</label>
                      <select v-model="personaForm.default_scenario" class="input input-sm">
                        <option value="SALES">SALES</option>
                        <option value="SERVICE_SUPPORT">SERVICE_SUPPORT</option>
                        <option value="WARRANTY_RETURN">WARRANTY_RETURN</option>
                        <option value="ORDER_BILLING">ORDER_BILLING</option>
                        <option value="AUTO">AUTO</option>
                      </select>
                    </div>
                  </div>
                  <div class="form-group mt-2">
                    <label>Chỉ dẫn Hệ thống (System Prompt)</label>
                    <textarea
                      v-model="personaForm.system_prompt"
                      rows="3"
                      class="input"
                      placeholder="Nhập chỉ dẫn chi tiết cho nhân cách AI..."
                      data-testid="astrbot-persona-prompt-input"
                    ></textarea>
                  </div>
                  <div class="form-actions-right">
                    <button class="btn btn-primary btn-sm" data-testid="astrbot-save-persona-btn" @click="handleSavePersona">
                      💾 Lưu Persona
                    </button>
                  </div>
                </div>

                <div class="entity-cards-list">
                  <div v-for="p in overview.personas" :key="p.id" class="entity-card">
                    <div class="entity-card-top">
                      <div>
                        <strong>{{ p.name }}</strong>
                        <div class="entity-badges">
                          <code>{{ p.id }}</code>
                          <span class="badge badge-indigo">{{ p.folder }}</span>
                          <span class="badge badge-cyan">{{ p.default_scenario }}</span>
                          <span class="badge badge-purple">Temp {{ p.temperature }}</span>
                        </div>
                      </div>
                      <div class="row-actions">
                        <button class="btn btn-secondary btn-xs" @click="loadPersonaToForm(p)">✏️ Sửa</button>
                        <button class="btn btn-danger-outline btn-xs" @click="handleDeletePersona(p.id)">🗑️</button>
                      </div>
                    </div>
                    <p class="entity-prompt-preview">{{ p.system_prompt }}</p>
                    <div class="entity-tools-row">
                      <span v-for="t in p.tools || []" :key="t" class="tool-chip">🔧 {{ t }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Column 2: LLM / Embedding / Rerank Providers -->
              <div class="editor-card">
                <div class="card-header-row">
                  <div>
                    <h3>⚡ Nguồn Mô hình LLM, Embedding &amp; Rerank (Providers)</h3>
                    <p class="card-desc">Quản lý kết nối 9Router Codex GPT-6 Astra, Google Gemini, OpenAI, DeepSeek &amp; ONNX Runtime.</p>
                  </div>
                </div>

                <!-- Add Provider Form -->
                <div class="sub-form-box">
                  <h5>➕ Thêm / Cập nhật Nguồn Mô hình (Provider Source)</h5>
                  <div class="form-grid">
                    <div class="form-group">
                      <label>Provider ID</label>
                      <input v-model="providerForm.id" type="text" class="input input-sm" placeholder="openai-gpt-5-source" data-testid="astrbot-provider-id-input" />
                    </div>
                    <div class="form-group">
                      <label>Tên Nhà cung cấp</label>
                      <input v-model="providerForm.provider" type="text" class="input input-sm" placeholder="9Router / OpenAI / Anthropic" />
                    </div>
                    <div class="form-group">
                      <label>Model chính</label>
                      <input v-model="providerForm.model" type="text" class="input input-sm" placeholder="codex/gpt-6-astra" data-testid="astrbot-provider-model-input" />
                    </div>
                    <div class="form-group">
                      <label>API Base URL</label>
                      <input v-model="providerForm.api_base" type="text" class="input input-sm" placeholder="http://localhost:20128/v1" />
                    </div>
                  </div>
                  <div class="form-actions-right">
                    <button class="btn btn-primary btn-sm" data-testid="astrbot-save-provider-btn" @click="handleSaveProvider">
                      💾 Lưu Nguồn Mô hình
                    </button>
                  </div>
                </div>

                <div class="entity-cards-list">
                  <div v-for="pr in overview.providers" :key="pr.id" class="entity-card">
                    <div class="entity-card-top">
                      <div>
                        <strong>{{ pr.provider }}</strong>
                        <div class="entity-badges">
                          <code>{{ pr.id }}</code>
                          <span class="badge badge-emerald">{{ pr.model }}</span>
                          <span class="badge badge-indigo">{{ pr.provider_type }}</span>
                          <span class="badge badge-cyan">{{ pr.latency_ms }}ms</span>
                        </div>
                      </div>
                      <div class="row-actions">
                        <button class="btn btn-secondary btn-xs" @click="handleTestProvider(pr.id)">🩺 Test</button>
                        <button class="btn btn-danger-outline btn-xs" @click="handleDeleteProvider(pr.id)">🗑️</button>
                      </div>
                    </div>
                    <div class="provider-meta-line">
                      <span>🌐 Endpoint: <code>{{ pr.api_base }}</code></span>
                      <span>🔑 Key: <code>{{ pr.masked_key }}</code></span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- =============================================================== -->
          <!-- TAB 5: CRON JOBS CHỦ ĐỘNG & STAR PLUGINS / MCP SERVERS          -->
          <!-- =============================================================== -->
          <div
            v-else-if="activeTab === 'automation_extensions'"
            class="astrbot-tab-panel"
            data-testid="astrbot-panel-automation"
          >
            <div class="dual-column-grid">
              <!-- Column 1: Proactive Cron Scheduler -->
              <div class="editor-card">
                <h3>⏰ Lập lịch Tác vụ Chủ động (AstrBot Proactive Cron Jobs)</h3>
                <p class="card-desc">
                  Tự động kích hoạt AI gửi tin nhắn chăm sóc khách hàng định kỳ tới bất kỳ phiên <code>target_umo</code> nào hoặc tổng hợp tri thức EvoAgentX.
                </p>

                <div class="sub-form-box">
                  <h5>➕ Tạo Tác vụ Định kỳ mới</h5>
                  <div class="form-grid">
                    <div class="form-group">
                      <label>Tên tác vụ</label>
                      <input v-model="cronForm.name" type="text" class="input input-sm" placeholder="Chăm sóc VIP Dior sau 7 ngày" data-testid="astrbot-cron-name-input" />
                    </div>
                    <div class="form-group">
                      <label>Biểu thức Cron (5 trường)</label>
                      <input v-model="cronForm.cron_expression" type="text" class="input input-sm" placeholder="0 9 * * *" />
                    </div>
                    <div class="form-group">
                      <label>Phiên UMO nhận tin (Target UMO)</label>
                      <input v-model="cronForm.target_umo" type="text" class="input input-sm" placeholder="telegram_dior_concierge:PrivateMessage:vip_client_8801" />
                    </div>
                    <div class="form-group">
                      <label>Kịch bản</label>
                      <select v-model="cronForm.scenario" class="input input-sm">
                        <option value="SERVICE_SUPPORT">SERVICE_SUPPORT</option>
                        <option value="SALES">SALES</option>
                        <option value="WARRANTY_RETURN">WARRANTY_RETURN</option>
                        <option value="GENERAL">GENERAL</option>
                      </select>
                    </div>
                  </div>
                  <div class="form-group mt-2">
                    <label>Chỉ dẫn Prompt gửi tới AI khi kích hoạt</label>
                    <input v-model="cronForm.prompt" type="text" class="input input-sm" placeholder="Soạn tin nhắn hỏi thăm trải nghiệm khách hàng..." />
                  </div>
                  <div class="form-actions-right">
                    <button class="btn btn-primary btn-sm" data-testid="astrbot-save-cron-btn" @click="handleSaveCronJob">
                      ➕ Lưu Lịch Cron
                    </button>
                  </div>
                </div>

                <div class="entity-cards-list">
                  <div v-for="job in overview.cron_jobs" :key="job.job_id" class="entity-card">
                    <div class="entity-card-top">
                      <div>
                        <strong>{{ job.name }}</strong>
                        <div class="entity-badges">
                          <code>{{ job.cron_expression }}</code>
                          <span class="badge badge-indigo">{{ job.target_umo }}</span>
                          <span class="badge badge-emerald">Đã chạy {{ job.run_count }} lần</span>
                        </div>
                      </div>
                      <div class="row-actions">
                        <button
                          class="btn btn-primary btn-xs"
                          :data-testid="`astrbot-run-cron-${job.job_id}`"
                          @click="handleRunCronNow(job.job_id)"
                        >
                          ▶️ Chạy ngay
                        </button>
                        <button class="btn btn-danger-outline btn-xs" @click="handleDeleteCron(job.job_id)">🗑️</button>
                      </div>
                    </div>
                    <p class="entity-prompt-preview">{{ job.description || job.prompt }}</p>
                    <div class="cron-preview-box">
                      <span>Kết quả gần nhất:</span>
                      <em>{{ job.last_result_preview }}</em>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Column 2: Star Plugins & MCP Servers -->
              <div class="editor-card">
                <h3>🧩 Hệ sinh thái Star Plugins &amp; Máy chủ MCP (Extensions)</h3>
                <p class="card-desc">
                  Quản lý các tiện ích mở rộng <code>Star</code> và kết nối giao thức <code>Model Context Protocol (MCP)</code> phục vụ gọi hàm tự động.
                </p>

                <div class="sub-form-box">
                  <h5>➕ Đăng ký Star Plugin hoặc MCP Server mới</h5>
                  <div class="form-grid">
                    <div class="form-group">
                      <label>Tên gói / Mã Plugin</label>
                      <input v-model="extForm.name" type="text" class="input input-sm" placeholder="mcp_server_crm_loyalty" data-testid="astrbot-ext-name-input" />
                    </div>
                    <div class="form-group">
                      <label>Tên hiển thị</label>
                      <input v-model="extForm.display_name" type="text" class="input input-sm" placeholder="CRM Loyalty MCP Bridge" />
                    </div>
                    <div class="form-group">
                      <label>Phân loại</label>
                      <select v-model="extForm.kind" class="input input-sm">
                        <option value="star_plugin">🌟 Star Plugin</option>
                        <option value="mcp_server">🔌 MCP Server</option>
                      </select>
                    </div>
                    <div class="form-group">
                      <label>Endpoint / Địa chỉ kết nối</label>
                      <input v-model="extForm.endpoint" type="text" class="input input-sm" placeholder="mcp://localhost:9000/loyalty" />
                    </div>
                  </div>
                  <div class="form-actions-right">
                    <button class="btn btn-primary btn-sm" data-testid="astrbot-save-ext-btn" @click="handleSaveExtension">
                      🔌 Cài đặt Tiện ích
                    </button>
                  </div>
                </div>

                <div class="entity-cards-list">
                  <div v-for="ext in overview.extensions" :key="ext.id" class="entity-card">
                    <div class="entity-card-top">
                      <div>
                        <strong>{{ ext.display_name }}</strong>
                        <div class="entity-badges">
                          <span class="badge" :class="ext.kind === 'mcp_server' ? 'badge-cyan' : 'badge-purple'">
                            {{ ext.kind === 'mcp_server' ? '🔌 MCP Server' : '🌟 Star Plugin' }}
                          </span>
                          <code>v{{ ext.version }}</code>
                          <span class="badge badge-indigo">{{ ext.tools_count }} tools</span>
                        </div>
                      </div>
                      <label class="switch-toggle">
                        <input
                          type="checkbox"
                          :checked="ext.enabled"
                          @change="handleToggleExtension(ext.id, $event.target.checked)"
                        />
                        <span class="switch-slider"></span>
                      </label>
                    </div>
                    <p class="entity-prompt-preview">{{ ext.description }}</p>
                    <div class="entity-tools-row">
                      <span v-for="c in ext.commands || []" :key="c" class="tool-chip">⚡ {{ c }}</span>
                      <code class="endpoint-tag">{{ ext.endpoint }}</code>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- =============================================================== -->
          <!-- TAB 6: THỐNG KÊ, PIPELINE TRACES & CONSOLE LOGS                 -->
          <!-- =============================================================== -->
          <div
            v-else-if="activeTab === 'observability'"
            class="astrbot-tab-panel"
            data-testid="astrbot-panel-observability"
          >
            <!-- Top KPI Summary Cards -->
            <div class="kpi-grid">
              <div class="kpi-card">
                <span class="kpi-label">Tổng tin nhắn đa nền tảng</span>
                <strong class="kpi-value">{{ overview.stats?.total_messages || 0 }}</strong>
                <span class="kpi-sub">Trên {{ overview.stats?.running_bots || 0 }} Bot đang hoạt động</span>
              </div>
              <div class="kpi-card">
                <span class="kpi-label">Độ trễ Pipeline Trung bình</span>
                <strong class="kpi-value">{{ overview.stats?.avg_latency_ms || 42 }} ms</strong>
                <span class="kpi-sub">Hybrid RAG + Dior KB + EvoAgentX</span>
              </div>
              <div class="kpi-card">
                <span class="kpi-label">Phiên UMO Đang quản lý</span>
                <strong class="kpi-value">{{ overview.stats?.active_sessions || 0 }}</strong>
                <span class="kpi-sub">{{ overview.stats?.personas_count || 0 }} Personas · {{ overview.stats?.providers_count || 0 }} Providers</span>
              </div>
              <div class="kpi-card">
                <span class="kpi-label">EvoAgentX Self-Evolution</span>
                <strong class="kpi-value">Gen #{{ overview.stats?.evolution_generation || 4 }}</strong>
                <span class="kpi-sub">Chất lượng TB: {{ ((overview.stats?.evolution_quality_score || 0.94) * 100).toFixed(1) }}%</span>
              </div>
            </div>

            <div class="dual-column-grid mt-3">
              <!-- Left: Platform Ranking & Real-Time Traces -->
              <div class="editor-card">
                <h3>📈 Xếp hạng Lưu lượng theo Nền tảng &amp; Pipeline Traces</h3>
                <div class="ranking-bars">
                  <div
                    v-for="item in overview.stats?.platform_ranking || []"
                    :key="item.id"
                    class="ranking-row"
                  >
                    <div class="ranking-label">
                      <span>{{ getPlatformIcon(item.type) }} {{ item.id }}</span>
                      <strong>{{ item.count }} tin</strong>
                    </div>
                    <div class="ranking-track">
                      <div
                        class="ranking-fill"
                        :style="{ width: `${Math.min(100, Math.max(8, (item.count / maxPlatformCount) * 100))}%` }"
                      ></div>
                    </div>
                  </div>
                </div>

                <h4 class="mt-3">🔍 Nhật ký Truy vết Đường ống (TraceDisplayer)</h4>
                <div class="traces-list">
                  <div v-for="tr in overview.traces" :key="tr.trace_id" class="trace-item">
                    <div class="trace-top">
                      <code>{{ tr.trace_id }}</code>
                      <span class="badge badge-indigo">{{ tr.umo }}</span>
                      <span class="badge badge-cyan">{{ tr.scenario }}</span>
                      <span class="badge badge-emerald">{{ tr.latency_ms }}ms</span>
                    </div>
                    <div v-if="tr.stages?.length" class="trace-stages">
                      <span v-for="(st, sIdx) in tr.stages" :key="st" class="stage-pill">
                        ✓ {{ st }}<span v-if="sIdx < tr.stages.length - 1" class="stage-arrow">→</span>
                      </span>
                    </div>
                    <div class="trace-q"><strong>Khách ({{ tr.sender_name }}):</strong> {{ tr.question }}</div>
                    <div class="trace-a"><strong>Bot ({{ tr.persona_id }}):</strong> {{ tr.reply_excerpt }}</div>
                  </div>
                </div>
              </div>

              <!-- Right: Console Logs (ConsolePage.vue style) -->
              <div class="editor-card console-card">
                <div class="console-header">
                  <div>
                    <h3>🖥️ AstrBot Live Console Logs</h3>
                    <span class="card-desc">Theo dõi sự kiện Platform Adapter, Webhook &amp; EventPipeline</span>
                  </div>
                  <div class="console-controls">
                    <label class="checkbox-label">
                      <input v-model="hideUserChatLogs" type="checkbox" />
                      <span>Ẩn nội dung chat khách hàng</span>
                    </label>
                    <button class="btn btn-secondary btn-xs" @click="clearLogsHandler">🧹 Xoá Log</button>
                  </div>
                </div>

                <div class="console-terminal" data-testid="astrbot-console-terminal">
                  <div
                    v-for="log in filteredLogs"
                    :key="log.id"
                    class="console-line"
                    :class="`level-${(log.level || 'INFO').toLowerCase()}`"
                  >
                    <span class="log-time">{{ formatShortTime(log.timestamp) }}</span>
                    <span class="log-level">[{{ log.level }}]</span>
                    <span class="log-source">[{{ log.platform_id }}/{{ log.source }}]</span>
                    <span class="log-msg">{{ log.message }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- ================================================================= -->
        <!-- SUB-MODAL 1: ADD NEW PLATFORM CATALOG (19 ADAPTERS)               -->
        <!-- ================================================================= -->
        <div
          v-if="showAddPlatformModal"
          class="submodal-backdrop"
          data-testid="astrbot-catalog-modal"
          @click.self="showAddPlatformModal = false"
        >
          <div class="submodal-box catalog-submodal glass-panel">
            <div class="submodal-header">
              <div>
                <h3>🌐 Danh mục 19 Cổng Kết nối Nền tảng Nhắn tin (AddNewPlatform)</h3>
                <p>Chọn nền tảng cần kết nối để khởi tạo thực thể Adapter mới trên DocMind x AstrBot.</p>
              </div>
              <button class="btn btn-ghost btn-icon" @click="showAddPlatformModal = false">✕</button>
            </div>

            <div class="catalog-filter-bar">
              <input
                v-model="catalogSearch"
                type="text"
                class="input input-sm"
                placeholder="Tìm kiếm nền tảng (Telegram, Discord, Slack, Lark, LINE, WeChat, QQ, KOOK...)"
                data-testid="astrbot-catalog-search"
              />
              <select v-model="catalogCategory" class="input input-sm">
                <option value="ALL">Tất cả phân nhóm ({{ overview.bot_types?.length || 19 }})</option>
                <option value="Global Messaging">Global Messaging</option>
                <option value="Enterprise Workspace">Enterprise Workspace</option>
                <option value="WeChat Ecosystem">WeChat Ecosystem</option>
                <option value="QQ & OneBot Ecosystem">QQ &amp; OneBot Ecosystem</option>
                <option value="Open Federation & Protocol">Open Federation &amp; Protocol</option>
                <option value="Built-in Channel">Built-in Channel</option>
              </select>
            </div>

            <div class="catalog-grid">
              <div
                v-for="pt in filteredPlatformTypes"
                :key="pt.type"
                class="catalog-card"
                :class="{ selected: newBotForm.type === pt.type }"
                :data-testid="`astrbot-catalog-card-${pt.type}`"
                @click="selectCatalogType(pt)"
              >
                <div class="catalog-card-top">
                  <span class="catalog-icon">{{ getPlatformIcon(pt.type) }}</span>
                  <div>
                    <strong>{{ pt.display_name }}</strong>
                    <div class="catalog-badges">
                      <code>{{ pt.type }}</code>
                      <span v-if="pt.support_unified_webhook" class="badge badge-indigo">🔗 Webhook</span>
                      <span v-if="pt.support_qr_login" class="badge badge-emerald">📱 QR Login</span>
                    </div>
                  </div>
                </div>
                <p class="catalog-desc">{{ pt.description }}</p>
              </div>
            </div>

            <div class="catalog-create-footer">
              <div class="catalog-create-inputs">
                <div class="form-group">
                  <label>Đặt ID định danh cho Bot mới (tự động thay ':' và '!' bằng '_')</label>
                  <input
                    v-model="newBotForm.id"
                    type="text"
                    class="input input-sm"
                    placeholder="vd: telegram_vip_boutique"
                    data-testid="astrbot-new-bot-id"
                  />
                </div>
                <div class="form-group">
                  <label>Persona mặc định</label>
                  <select v-model="newBotForm.default_persona_id" class="input input-sm">
                    <option v-for="p in overview.personas" :key="p.id" :value="p.id">{{ p.name }}</option>
                  </select>
                </div>
                <div class="form-group">
                  <label>Kịch bản mặc định</label>
                  <select v-model="newBotForm.default_scenario" class="input input-sm">
                    <option value="SALES">SALES</option>
                    <option value="SERVICE_SUPPORT">SERVICE_SUPPORT</option>
                    <option value="WARRANTY_RETURN">WARRANTY_RETURN</option>
                    <option value="ORDER_BILLING">ORDER_BILLING</option>
                    <option value="AUTO">AUTO</option>
                  </select>
                </div>
              </div>
              <div class="catalog-create-buttons">
                <button class="btn btn-secondary btn-sm" @click="showAddPlatformModal = false">Huỷ</button>
                <button
                  class="btn btn-primary btn-sm"
                  data-testid="astrbot-confirm-create-bot-btn"
                  @click="handleCreateBot"
                >
                  🚀 Tạo Kết nối {{ newBotForm.type }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- ================================================================= -->
        <!-- SUB-MODAL 2: QR CODE REGISTRATION MODAL (Lark / WeChat / DingTalk) -->
        <!-- ================================================================= -->
        <div
          v-if="qrModalState.open"
          class="submodal-backdrop"
          data-testid="astrbot-qr-modal"
          @click.self="qrModalState.open = false"
        >
          <div class="submodal-box qr-submodal glass-panel">
            <div class="submodal-header">
              <h3>📱 Đăng ký &amp; Cấp quyền nhanh bằng Mã QR ({{ qrModalState.botType }})</h3>
              <button class="btn btn-ghost btn-icon" @click="qrModalState.open = false">✕</button>
            </div>
            <div class="qr-body">
              <div class="qr-visual-box">
                <div class="qr-matrix-art">
                  <div class="qr-corner tl"></div>
                  <div class="qr-corner tr"></div>
                  <div class="qr-corner bl"></div>
                  <span class="qr-center-logo">{{ getPlatformIcon(qrModalState.botType) }}</span>
                </div>
                <code>{{ qrModalState.qrUrl }}</code>
              </div>
              <p class="qr-status-text">{{ qrModalState.message }}</p>
              <div class="qr-actions">
                <button
                  class="btn btn-primary btn-sm"
                  data-testid="astrbot-qr-poll-btn"
                  @click="handlePollQrStatus"
                >
                  ✅ Xác nhận đã quét QR (Đồng bộ Credentials)
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- ================================================================= -->
        <!-- SUB-MODAL 3: UNIFIED WEBHOOK INSPECTOR                            -->
        <!-- ================================================================= -->
        <div
          v-if="webhookInspectBot"
          class="submodal-backdrop"
          data-testid="astrbot-webhook-modal"
          @click.self="webhookInspectBot = null"
        >
          <div class="submodal-box qr-submodal glass-panel">
            <div class="submodal-header">
              <h3>🔗 Cấu hình Unified Webhook ({{ webhookInspectBot.id }})</h3>
              <button class="btn btn-ghost btn-icon" @click="webhookInspectBot = null">✕</button>
            </div>
            <div class="qr-body">
              <p>
                Nền tảng <strong>{{ webhookInspectBot.display_name }}</strong> đang bật chế độ <strong>Unified Webhook Mode</strong>. Hãy dán địa chỉ Callback URL dưới đây vào trang quản trị Developer Console của nền tảng:
              </p>
              <div class="webhook-copy-box">
                <code>{{ fullWebhookUrl(webhookInspectBot.webhook_url) }}</code>
                <button class="btn btn-primary btn-xs" @click="copyText(fullWebhookUrl(webhookInspectBot.webhook_url))">
                  📋 Sao chép
                </button>
              </div>
              <p class="field-hint">
                Hỗ trợ xác thực tự động GET <code>hub.challenge</code> / <code>echostr</code> và POST JSON Event Callback.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import {
  getAstrBotOverview,
  createAstrBotBot,
  updateAstrBotBot,
  toggleAstrBotBot,
  deleteAstrBotBot,
  testAstrBotBot,
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
} from '../api'

defineEmits(['close'])

const loading = ref(false)
const activeTab = ref('platforms')
const bannerMessage = ref('')
const bannerType = ref('success')
const showSecrets = ref(false)

const overview = reactive({
  enabled: true,
  version: 'v4.14.0-docmind',
  timezone: 'Asia/Ho_Chi_Minh',
  webhook_base_path: '/api/astrbot/webhooks/platforms',
  stats: {},
  bot_types: [],
  bots: [],
  sessions: [],
  personas: [],
  providers: [],
  cron_jobs: [],
  extensions: [],
  traces: [],
  logs: []
})

const selectedBot = ref(null)
const botSearch = ref('')
const sessionSearch = ref('')
const hideUserChatLogs = ref(false)

// Editor form for selectedBot
const editForm = reactive({
  enable: true,
  unified_webhook: true,
  default_persona_id: 'dior_luxury_advisor',
  default_scenario: 'SALES',
  fallback_profile_id: 'dior_luxury_default',
  config: {}
})

// UMO Route builder
const newRouteMsgType = ref('PrivateMessage')
const newRouteSessionId = ref('vip_client_8801')
const newRoutePersonaId = ref('dior_luxury_advisor')
const newRouteScenario = ref('SALES')

// Add Platform Submodal
const showAddPlatformModal = ref(false)
const catalogSearch = ref('')
const catalogCategory = ref('ALL')
const newBotForm = reactive({
  type: 'telegram',
  id: 'telegram_vip_boutique',
  default_persona_id: 'dior_luxury_advisor',
  default_scenario: 'SALES'
})

// QR Registration Submodal
const qrModalState = reactive({
  open: false,
  botType: 'lark',
  botId: '',
  qrUrl: '',
  message: ''
})

// Unified Webhook Inspector Submodal
const webhookInspectBot = ref(null)

// Simulator State
const simSending = ref(false)
const simForm = reactive({
  bot_id: 'telegram_dior_concierge',
  message_type: 'PrivateMessage',
  session_id: 'vip_client_8801',
  sender_id: 'vip_client_8801',
  sender_name: 'Chị Minh Thư (VIP Diamond)',
  persona_id: '',
  scenario: 'AUTO',
  message: ''
})
const simMessages = ref([])

const starCommands = [
  { label: 'help', text: '/help', desc: 'Danh sách lệnh Star' },
  { label: 'status', text: '/status', desc: 'Trạng thái Bot & EvoAgentX' },
  { label: 'whoami', text: '/whoami', desc: 'Kiểm tra định danh UMO' },
  { label: 'evolve', text: '/evolve', desc: 'Chạy tối ưu TextGrad + MIPRO' },
  { label: 'persona', text: '/persona dior_luxury_stylist', desc: 'Gắn Persona Dior' },
  { label: 'provider', text: '/provider', desc: 'Kiểm tra LLM Provider' },
  { label: 'kb', text: '/kb Dior Sauvage', desc: 'Tra cứu kho tri thức Dior' },
  { label: 'tts', text: '/tts', desc: 'Bật/tắt giọng nói TTS' },
  { label: 'guidelines', text: '/guidelines', desc: 'Quy tắc vàng EvoAgentX' },
  { label: 'reset', text: '/reset', desc: 'Làm mới phiên UMO' }
]

const sampleQuestions = [
  {
    label: '✈️ Telegram VIP: Tư vấn nước hoa Dior giữ mùi lâu',
    botId: 'telegram_dior_concierge',
    scenario: 'SALES',
    text: 'Mình muốn tìm nước hoa Dior nữ sang trọng, lưu hương trên 8 tiếng để đi tiệc tối, tư vấn giúp mình nhé!'
  },
  {
    label: '💙 Messenger Fanpage: Tư vấn son Rouge Dior 999 & hộp quà',
    botId: 'messenger_dior_page',
    scenario: 'SALES',
    text: 'Shop tư vấn giúp mình son Rouge Dior 999 bản Velvet hay Satin hợp làm quà tặng sinh nhật hơn ạ?'
  },
  {
    label: '💬 Discord Tech: Kiến trúc Hybrid RAG & RRF trong DocMind',
    botId: 'discord_community_hub',
    scenario: 'SERVICE_SUPPORT',
    text: 'Giải thích cơ chế kết hợp pgvector và Full-Text Search bằng thuật toán RRF trong hệ thống DocMind.'
  },
  {
    label: '🪶 Lark VIP: Đổi mới hộp quà Dior bị móp khi giao hàng',
    botId: 'lark_feishu_workspace',
    scenario: 'WARRANTY_RETURN',
    text: 'Mình vừa nhận đơn son Rouge Dior 999 nhưng hộp quà tặng bị móp góc, shop hỗ trợ đổi hộp mới giúp mình được không?'
  },
  {
    label: '🟢 LINE Concierge: Kiểm tra ưu đãi gói quà Art of Gifting',
    botId: 'line_official_vip',
    scenario: 'ORDER_BILLING',
    text: 'Đặt mua combo Dior Prestige có được miễn phí gói quà Art of Gifting và khắc tên lên vỏ son không ạ?'
  }
]

// Persona, Provider, Cron, Extension forms
const personaForm = reactive({
  id: '',
  name: '',
  folder: 'Luxury Retail',
  default_scenario: 'SALES',
  temperature: 0.3,
  system_prompt: ''
})

const providerForm = reactive({
  id: '',
  provider: '9Router OpenAI-Compatible',
  model: 'codex/gpt-6-astra',
  api_base: 'http://localhost:20128/v1'
})

const cronForm = reactive({
  name: '',
  cron_expression: '0 9 * * *',
  target_umo: 'telegram_dior_concierge:PrivateMessage:vip_client_8801',
  scenario: 'SERVICE_SUPPORT',
  prompt: 'Hãy gửi lời hỏi thăm trải nghiệm khách hàng VIP sau 7 ngày sử dụng sản phẩm Dior.'
})

const extForm = reactive({
  name: '',
  display_name: '',
  kind: 'mcp_server',
  endpoint: 'mcp://local/custom-plugin'
})

const tabs = computed(() => [
  { id: 'platforms', icon: '🛰️', label: 'Nền tảng Chat', count: overview.bots?.length || 0 },
  { id: 'simulator', icon: '💬', label: 'Giả lập & Lệnh Star', count: simMessages.value.length || undefined },
  { id: 'sessions', icon: '🗂️', label: 'Phiên UMO & Quy tắc', count: overview.sessions?.length || 0 },
  { id: 'personas_providers', icon: '🎭', label: 'Persona & LLM Providers', count: (overview.personas?.length || 0) + (overview.providers?.length || 0) },
  { id: 'automation_extensions', icon: '⏰', label: 'Cron & Plugin/MCP', count: (overview.cron_jobs?.length || 0) + (overview.extensions?.length || 0) },
  { id: 'observability', icon: '📊', label: 'Thống kê, Trace & Console', count: overview.traces?.length || 0 }
])

const filteredBots = computed(() => {
  const q = botSearch.value.trim().toLowerCase()
  if (!q) return overview.bots || []
  return (overview.bots || []).filter(
    (b) =>
      b.id.toLowerCase().includes(q) ||
      b.type.toLowerCase().includes(q) ||
      (b.display_name && b.display_name.toLowerCase().includes(q))
  )
})

const selectedPlatformMeta = computed(() => {
  if (!selectedBot.value) return null
  return (overview.bot_types || []).find((t) => t.type === selectedBot.value.type) || null
})

const filteredPlatformTypes = computed(() => {
  const q = catalogSearch.value.trim().toLowerCase()
  return (overview.bot_types || []).filter((pt) => {
    if (catalogCategory.value !== 'ALL' && pt.category !== catalogCategory.value) return false
    if (!q) return true
    return (
      pt.type.toLowerCase().includes(q) ||
      pt.display_name.toLowerCase().includes(q) ||
      pt.description.toLowerCase().includes(q)
    )
  })
})

const filteredSessions = computed(() => {
  const q = sessionSearch.value.trim().toLowerCase()
  if (!q) return overview.sessions || []
  return (overview.sessions || []).filter(
    (s) =>
      s.umo.toLowerCase().includes(q) ||
      (s.sender_name && s.sender_name.toLowerCase().includes(q)) ||
      (s.custom_name && s.custom_name.toLowerCase().includes(q)) ||
      (s.last_message && s.last_message.toLowerCase().includes(q))
  )
})

const filteredLogs = computed(() => {
  const list = overview.logs || []
  if (!hideUserChatLogs.value) return list
  return list.filter((l) => !l.is_user_chat)
})

const maxPlatformCount = computed(() => {
  const ranking = overview.stats?.platform_ranking || []
  if (!ranking.length) return 1
  return Math.max(1, ...ranking.map((r) => r.count || 0))
})

function notify(msg, type = 'success') {
  bannerMessage.value = msg
  bannerType.value = type
}

function getPlatformIcon(type) {
  const map = {
    telegram: '✈️',
    discord: '🎮',
    slack: '💼',
    lark: '🪶',
    dingtalk: '📌',
    line: '🟢',
    wecom: '🏢',
    wecom_ai_bot: '🤖',
    weixin_official_account: '📢',
    weixin_oc: '💬',
    qq_official: '🐧',
    qq_official_webhook: '🐧',
    aiocqhttp: '⚡',
    satori: '🌐',
    kook: '🎧',
    mattermost: '🛡️',
    misskey: '🚀',
    facebook_messenger: '💙',
    webchat: '💻'
  }
  return map[type] || '🛰️'
}

function fullWebhookUrl(path) {
  if (!path) return `${window.location.origin}/api/astrbot/webhooks/platforms/default`
  if (path.startsWith('http')) return path
  return `${window.location.origin}${path}`
}

function formatShortTime(iso) {
  if (!iso) return '--:--:--'
  try {
    return new Date(iso).toLocaleTimeString('vi-VN', { hour12: false })
  } catch {
    return iso
  }
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text)
    notify(`Đã sao chép vào bộ nhớ tạm: ${text}`)
  } catch {
    notify(`URL: ${text}`)
  }
}

function selectBot(bot) {
  selectedBot.value = bot
  editForm.enable = bot.enable
  editForm.unified_webhook = bot.unified_webhook
  editForm.default_persona_id = bot.default_persona_id || 'dior_luxury_advisor'
  editForm.default_scenario = bot.default_scenario || 'SALES'
  editForm.fallback_profile_id = bot.fallback_profile_id || 'dior_luxury_default'
  editForm.config = { ...(bot.config || {}) }
}

async function fetchOverview() {
  loading.value = true
  try {
    const data = await getAstrBotOverview()
    Object.assign(overview, data)
    if (overview.bots?.length) {
      const currentId = selectedBot.value?.id
      const found = overview.bots.find((b) => b.id === currentId) || overview.bots[0]
      selectBot(found)
    }
  } finally {
    loading.value = false
  }
}

function selectCatalogType(pt) {
  newBotForm.type = pt.type
  newBotForm.id = `${pt.type}_dior_${Math.floor(10 + Math.random() * 89)}`
}

async function handleCreateBot() {
  if (!newBotForm.id.trim()) {
    notify('Vui lòng nhập ID định danh cho Bot mới', 'error')
    return
  }
  const res = await createAstrBotBot({
    id: newBotForm.id.trim(),
    type: newBotForm.type,
    enable: true,
    default_persona_id: newBotForm.default_persona_id,
    default_scenario: newBotForm.default_scenario
  })
  showAddPlatformModal.value = false
  await fetchOverview()
  if (res?.bot) {
    const created = overview.bots.find((b) => b.id === res.bot.id) || res.bot
    selectBot(created)
  }
  notify(res?.message || `Đã tạo kết nối [${newBotForm.id}] thành công!`)
}

async function handleSaveBot() {
  if (!selectedBot.value) return
  const res = await updateAstrBotBot(selectedBot.value.id, {
    enable: editForm.enable,
    unified_webhook: editForm.unified_webhook,
    default_persona_id: editForm.default_persona_id,
    default_scenario: editForm.default_scenario,
    fallback_profile_id: editForm.fallback_profile_id,
    config: editForm.config
  })
  await fetchOverview()
  notify(res?.message || `Đã lưu cấu hình [${selectedBot.value.id}] thành công!`)
}

async function handleToggleBot(bot, enabled) {
  await toggleAstrBotBot(bot.id, enabled)
  await fetchOverview()
  notify(`Đã ${enabled ? 'bật' : 'tạm dừng'} nền tảng [${bot.id}].`)
}

async function handleDeleteBot(bot) {
  await deleteAstrBotBot(bot.id)
  selectedBot.value = null
  await fetchOverview()
  notify(`Đã xoá cấu hình nền tảng [${bot.id}].`)
}

async function handleTestBot(bot) {
  const res = await testAstrBotBot(bot.id)
  notify(res?.message || `Kiểm tra kết nối [${bot.id}]: ${res?.status}`, res?.status === 'ok' ? 'success' : 'warn')
}

async function handleOpenQrModal(bot) {
  const res = await registerAstrBotQr(bot.type, { action: 'start', bot_id: bot.id })
  qrModalState.open = true
  qrModalState.botType = bot.type
  qrModalState.botId = bot.id
  qrModalState.qrUrl = res.qrcode_img_content || res.verification_uri_complete
  qrModalState.message = res.message
}

async function handlePollQrStatus() {
  const res = await registerAstrBotQr(qrModalState.botType, {
    action: 'poll',
    bot_id: qrModalState.botId
  })
  qrModalState.open = false
  await fetchOverview()
  notify(res.message || 'Đã xác thực QR và điền thông tin kết nối tự động!')
}

async function handleAddRoute() {
  if (!selectedBot.value) return
  const umo = `${selectedBot.value.id}:${newRouteMsgType.value}:${newRouteSessionId.value.trim() || '*'}`
  await upsertAstrBotRoute({
    umo,
    config_id: editForm.fallback_profile_id || 'dior_luxury_default',
    persona_id: newRoutePersonaId.value,
    scenario: newRouteScenario.value
  })
  await fetchOverview()
  notify(`Đã gắn quy tắc định tuyến cho UMO [${umo}]`)
}

async function handleRemoveRoute(umo) {
  await deleteAstrBotRoute(umo)
  await fetchOverview()
  notify(`Đã gỡ định tuyến UMO [${umo}]`)
}

function jumpToSimulatorWithBot(bot) {
  simForm.bot_id = bot.id
  activeTab.value = 'simulator'
}

function openSessionChatInSimulator(sess) {
  simForm.bot_id = sess.platform_id
  simForm.message_type = sess.message_type
  simForm.session_id = sess.session_id
  simForm.sender_id = sess.sender_id
  simForm.sender_name = sess.custom_name || sess.sender_name
  activeTab.value = 'simulator'
}

async function sendSimulatedMessage(presetText, presetBotId, presetScenario) {
  const text = (presetText !== undefined ? presetText : simForm.message).trim()
  if (!text) return
  if (presetBotId) simForm.bot_id = presetBotId
  if (presetScenario) simForm.scenario = presetScenario
  if (presetText === undefined) simForm.message = ''

  simSending.value = true
  try {
    const resp = await simulateAstrBotMessage({
      bot_id: simForm.bot_id,
      message_type: simForm.message_type,
      session_id: simForm.session_id,
      sender_id: simForm.sender_id,
      sender_name: simForm.sender_name,
      persona_id: simForm.persona_id || null,
      scenario: simForm.scenario,
      message: text
    })
    simMessages.value.push(resp)
    await fetchOverview()
  } finally {
    simSending.value = false
  }
}

async function handleUpdateSessionRule(sess, changes) {
  await upsertAstrBotSessionRule({ umo: sess.umo, ...changes })
  await fetchOverview()
  notify(`Đã cập nhật quy tắc cho phiên [${sess.umo}]`)
}

async function handleDeleteSingleSession(umo) {
  await deleteAstrBotSession(umo)
  await fetchOverview()
  notify(`Đã xoá phiên UMO [${umo}]`)
}

async function handleClearAllSessions() {
  const res = await deleteAstrBotSession()
  await fetchOverview()
  notify(res?.message || 'Đã làm sạch toàn bộ phiên hội thoại UMO!')
}

function loadPersonaToForm(p) {
  personaForm.id = p.id
  personaForm.name = p.name
  personaForm.folder = p.folder || 'Luxury Retail'
  personaForm.default_scenario = p.default_scenario || 'SALES'
  personaForm.temperature = p.temperature ?? 0.3
  personaForm.system_prompt = p.system_prompt
}

async function handleSavePersona() {
  if (!personaForm.name.trim() && !personaForm.id.trim()) {
    notify('Vui lòng nhập ID hoặc Tên cho Persona', 'error')
    return
  }
  await saveAstrBotPersona({ ...personaForm })
  personaForm.id = ''
  personaForm.name = ''
  personaForm.system_prompt = ''
  await fetchOverview()
  notify('Đã lưu Persona thành công!')
}

async function handleDeletePersona(id) {
  await deleteAstrBotPersona(id)
  await fetchOverview()
  notify(`Đã xoá Persona [${id}]`)
}

async function handleSaveProvider() {
  if (!providerForm.id.trim() && !providerForm.model.trim()) {
    notify('Vui lòng nhập Provider ID và Model', 'error')
    return
  }
  await saveAstrBotProvider({ ...providerForm })
  providerForm.id = ''
  await fetchOverview()
  notify('Đã lưu Nguồn Mô hình (Provider Source) thành công!')
}

async function handleTestProvider(id) {
  const res = await testAstrBotProvider(id)
  notify(res?.message || `Kết nối Provider [${id}] thành công!`)
}

async function handleDeleteProvider(id) {
  await deleteAstrBotProvider(id)
  await fetchOverview()
  notify(`Đã xoá Provider [${id}]`)
}

async function handleSaveCronJob() {
  if (!cronForm.name.trim()) {
    notify('Vui lòng nhập tên tác vụ Cron Job', 'error')
    return
  }
  await saveAstrBotCronJob({ ...cronForm })
  cronForm.name = ''
  await fetchOverview()
  notify('Đã lưu lịch tác vụ chủ động Cron Job!')
}

async function handleRunCronNow(jobId) {
  const res = await runAstrBotCronJob(jobId)
  await fetchOverview()
  notify(res?.message || `Đã kích hoạt chạy ngay Cron Job [${jobId}]!`)
}

async function handleDeleteCron(jobId) {
  await deleteAstrBotCronJob(jobId)
  await fetchOverview()
  notify(`Đã xoá Cron Job [${jobId}]`)
}

async function handleSaveExtension() {
  if (!extForm.name.trim()) {
    notify('Vui lòng nhập tên gói Star Plugin hoặc MCP Server', 'error')
    return
  }
  await saveAstrBotExtension({ ...extForm })
  extForm.name = ''
  extForm.display_name = ''
  await fetchOverview()
  notify('Đã cài đặt Tiện ích mở rộng / MCP Server!')
}

async function handleToggleExtension(id, enabled) {
  await toggleAstrBotExtension(id, enabled)
  await fetchOverview()
  notify(`Đã ${enabled ? 'bật' : 'tắt'} tiện ích [${id}]`)
}

async function clearLogsHandler() {
  await clearAstrBotLogs()
  await fetchOverview()
  notify('Đã làm sạch bộ đệm Console Logs.')
}

function handleSubmodalEscape(e) {
  if (e.key === 'Escape') {
    if (showAddPlatformModal.value) {
      showAddPlatformModal.value = false
      e.stopImmediatePropagation()
      e.preventDefault()
    } else if (qrModalState.open) {
      qrModalState.open = false
      e.stopImmediatePropagation()
      e.preventDefault()
    } else if (webhookInspectBot.value) {
      webhookInspectBot.value = null
      e.stopImmediatePropagation()
      e.preventDefault()
    }
  }
}

onMounted(() => {
  fetchOverview()
  window.addEventListener('keydown', handleSubmodalEscape, true)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleSubmodalEscape, true)
})
</script>

<style scoped>
.astrbot-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(6, 8, 15, 0.78);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.astrbot-modal {
  width: min(1360px, 100%);
  max-width: 100%;
  height: min(90vh, 920px);
  display: flex;
  flex-direction: column;
  background: var(--bg-glass-heavy, rgba(17, 22, 37, 0.96));
  border: 1px solid var(--border-accent, rgba(99, 102, 241, 0.35));
  border-radius: var(--radius-xl, 20px);
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.65), 0 0 36px rgba(99, 102, 241, 0.16);
  overflow: hidden;
  color: var(--text-primary, #f1f5f9);
}

/* Header */
.astrbot-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 16px 20px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.14), rgba(139, 92, 246, 0.08));
  border-bottom: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.12));
  flex-shrink: 0;
}

.astrbot-header-left {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

.astrbot-brand-badge {
  position: relative;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.35rem;
  flex-shrink: 0;
  box-shadow: 0 6px 18px rgba(99, 102, 241, 0.35);
}

.astrbot-pulse-dot {
  position: absolute;
  bottom: -2px;
  right: -2px;
  width: 11px;
  height: 11px;
  border-radius: 50%;
  background: #64748b;
  border: 2px solid #0b0e17;
}

.astrbot-pulse-dot.online {
  background: #10b981;
  box-shadow: 0 0 8px #10b981;
}

.astrbot-title-block {
  min-width: 0;
  flex: 1;
}

.astrbot-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.astrbot-title-row h2 {
  font-size: 1.12rem;
  font-weight: 700;
  margin: 0;
  color: #f8fafc;
}

.astrbot-subtitle {
  margin: 4px 0 0;
  font-size: 0.78rem;
  color: var(--text-secondary, #94a3b8);
  line-height: 1.4;
}

.astrbot-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}

/* Toast Banner */
.astrbot-toast-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 18px;
  font-size: 0.82rem;
  background: rgba(16, 185, 129, 0.16);
  border-bottom: 1px solid rgba(16, 185, 129, 0.35);
  color: #6ee7b7;
  flex-shrink: 0;
}

.astrbot-toast-banner.error {
  background: rgba(244, 63, 94, 0.16);
  border-color: rgba(244, 63, 94, 0.35);
  color: #fda4af;
}

.btn-toast-close {
  background: transparent;
  border: none;
  color: inherit;
  cursor: pointer;
  font-size: 0.9rem;
}

/* Tabs */
.astrbot-tabs {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  background: rgba(11, 14, 23, 0.65);
  border-bottom: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.12));
  overflow-x: auto;
  flex-shrink: 0;
}

.astrbot-tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: rgba(30, 41, 59, 0.45);
  color: var(--text-secondary, #94a3b8);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.18s ease;
}

.astrbot-tab-btn:hover {
  color: #f1f5f9;
  background: rgba(99, 102, 241, 0.14);
}

.astrbot-tab-btn.active {
  color: #ffffff;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.32), rgba(139, 92, 246, 0.28));
  border-color: rgba(99, 102, 241, 0.55);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.22);
}

.tab-badge {
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 0.7rem;
  background: rgba(99, 102, 241, 0.28);
  color: #c7d2fe;
}

/* Main Body */
.astrbot-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px 20px;
  min-height: 0;
}

.astrbot-tab-panel {
  min-width: 0;
}

/* Split Layout for Platforms Tab */
.platforms-split-layout {
  display: grid;
  grid-template-columns: 310px minmax(0, 1fr);
  gap: 16px;
  min-width: 0;
}

.bot-sidebar {
  background: rgba(15, 23, 42, 0.65);
  border: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.12));
  border-radius: 14px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.bot-sidebar-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-weight: 700;
  font-size: 0.86rem;
}

.bot-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 540px;
  overflow-y: auto;
}

.bot-list-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 10px;
  background: rgba(30, 41, 59, 0.45);
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.16s ease;
  min-width: 0;
}

.bot-list-item:hover {
  border-color: rgba(99, 102, 241, 0.35);
  background: rgba(30, 41, 59, 0.75);
}

.bot-list-item.active {
  border-color: #6366f1;
  background: rgba(99, 102, 241, 0.16);
}

.bot-item-icon {
  font-size: 1.25rem;
  flex-shrink: 0;
}

.bot-item-info {
  flex: 1;
  min-width: 0;
}

.bot-item-top {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.bot-item-id {
  font-size: 0.82rem;
  font-weight: 700;
  color: #f8fafc;
  word-break: break-all;
}

.bot-item-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 0.72rem;
  color: #94a3b8;
  margin-top: 2px;
}

.status-pill {
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 0.66rem;
  font-weight: 700;
  text-transform: uppercase;
}

.status-pill.running {
  background: rgba(16, 185, 129, 0.18);
  color: #34d399;
}

.status-pill.stopped {
  background: rgba(148, 163, 184, 0.18);
  color: #94a3b8;
}

.status-pill.error {
  background: rgba(244, 63, 94, 0.2);
  color: #fb7185;
}

.webhook-chip {
  color: #38bdf8;
  font-weight: 600;
}

/* Bot Editor Pane */
.bot-editor-pane {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.bot-editor-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px 16px;
  background: rgba(15, 23, 42, 0.65);
  border: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.12));
  border-radius: 14px;
}

.bot-editor-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.editor-platform-icon {
  font-size: 1.8rem;
}

.bot-editor-title h3 {
  margin: 0;
  font-size: 1.02rem;
  word-break: break-all;
}

.bot-editor-title p {
  margin: 3px 0 0;
  font-size: 0.78rem;
  color: #94a3b8;
}

.doc-link {
  margin-left: 8px;
  color: #818cf8;
  text-decoration: none;
}

.bot-editor-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.webhook-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(6, 182, 212, 0.1);
  border: 1px solid rgba(6, 182, 212, 0.28);
}

.webhook-strip-left {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.webhook-label {
  font-size: 0.76rem;
  color: #67e8f9;
  font-weight: 600;
}

.webhook-code {
  font-size: 0.75rem;
  color: #e0f2fe;
  background: rgba(15, 23, 42, 0.7);
  padding: 3px 8px;
  border-radius: 6px;
  word-break: break-all;
}

.webhook-strip-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.editor-grid,
.dual-column-grid,
.simulator-layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  min-width: 0;
}

.editor-card,
.sim-controls-card,
.sim-chat-card {
  background: rgba(15, 23, 42, 0.65);
  border: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.12));
  border-radius: 14px;
  padding: 14px 16px;
  min-width: 0;
}

.editor-card h3,
.editor-card h4,
.sim-controls-card h3,
.sim-chat-card h4 {
  margin: 0 0 10px;
  font-size: 0.94rem;
}

.card-desc {
  margin: 0 0 12px;
  font-size: 0.78rem;
  color: #94a3b8;
  line-height: 1.45;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.form-group label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #cbd5e1;
}

.req-star {
  color: #fb7185;
}

.field-hint {
  font-size: 0.7rem;
  color: #64748b;
}

.secret-toggle-row {
  margin-top: 10px;
}

.checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.76rem;
  color: #94a3b8;
  cursor: pointer;
}

/* UMO Route Box */
.umo-routes-box {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.12);
}

.umo-routes-header h5 {
  margin: 0 0 8px;
  font-size: 0.82rem;
}

.umo-route-add-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.route-items-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.route-chip-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(30, 41, 59, 0.6);
  font-size: 0.75rem;
}

.btn-mini-del {
  margin-left: auto;
  background: transparent;
  border: none;
  color: #fb7185;
  cursor: pointer;
}

/* Simulator Styles */
.umo-preview-bar {
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(99, 102, 241, 0.12);
  font-size: 0.76rem;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.preset-section {
  margin-top: 12px;
}

.preset-section h5 {
  margin: 0 0 6px;
  font-size: 0.78rem;
  color: #cbd5e1;
}

.preset-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: 8px;
  background: rgba(30, 41, 59, 0.7);
  border: 1px solid rgba(148, 163, 184, 0.18);
  color: #e2e8f0;
  font-size: 0.74rem;
  cursor: pointer;
  text-align: left;
}

.chip-btn:hover {
  border-color: #6366f1;
  background: rgba(99, 102, 241, 0.18);
}

.sim-chat-card {
  display: flex;
  flex-direction: column;
  height: 560px;
}

.sim-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
}

.sim-chat-sub {
  font-size: 0.72rem;
  color: #94a3b8;
}

.sim-messages-viewport {
  flex: 1;
  overflow-y: auto;
  padding: 12px 4px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sim-empty-state {
  margin: auto;
  text-align: center;
  max-width: 380px;
  color: #94a3b8;
  font-size: 0.8rem;
}

.sim-empty-icon {
  font-size: 2.2rem;
  margin-bottom: 6px;
}

.sim-turn-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sim-bubble {
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 0.8rem;
  line-height: 1.45;
}

.user-bubble {
  align-self: flex-end;
  max-width: 88%;
  background: rgba(99, 102, 241, 0.22);
  border: 1px solid rgba(99, 102, 241, 0.4);
}

.bot-bubble {
  align-self: flex-start;
  max-width: 92%;
  background: rgba(30, 41, 59, 0.85);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.bot-bubble.cmd-reply {
  border-color: rgba(6, 182, 212, 0.45);
  background: rgba(8, 47, 73, 0.45);
}

.bubble-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 0.7rem;
  color: #94a3b8;
  margin-bottom: 6px;
}

.markdown-pre {
  white-space: pre-wrap;
  word-break: break-word;
}

.sim-citations,
.sim-quick-replies {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.cit-pill {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 0.7rem;
  background: rgba(16, 185, 129, 0.15);
  color: #6ee7b7;
}

.qr-pill-btn {
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 0.7rem;
  background: rgba(99, 102, 241, 0.16);
  border: 1px solid rgba(99, 102, 241, 0.35);
  color: #c7d2fe;
  cursor: pointer;
}

.sim-input-bar {
  display: flex;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid rgba(148, 163, 184, 0.12);
}

/* Sessions Table */
.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}

.toolbar-left h3 {
  margin: 0;
  font-size: 0.96rem;
}

.toolbar-left p {
  margin: 3px 0 0;
  font-size: 0.76rem;
  color: #94a3b8;
}

.toolbar-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.sessions-table-wrapper {
  overflow-x: auto;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.astrbot-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.78rem;
}

.astrbot-table th,
.astrbot-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid rgba(148, 163, 184, 0.1);
}

.astrbot-table th {
  background: rgba(15, 23, 42, 0.85);
  color: #94a3b8;
  font-weight: 600;
}

.umo-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.alias-input {
  max-width: 210px;
}

.truncate-text {
  margin: 4px 0 0;
  max-width: 220px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #94a3b8;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* Personas, Providers, Cron, Extensions Cards */
.sub-form-box {
  padding: 12px;
  border-radius: 12px;
  background: rgba(30, 41, 59, 0.45);
  border: 1px solid rgba(148, 163, 184, 0.14);
  margin-bottom: 12px;
}

.sub-form-box h5 {
  margin: 0 0 8px;
  font-size: 0.82rem;
}

.form-actions-right {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.entity-cards-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 360px;
  overflow-y: auto;
}

.entity-card {
  padding: 12px;
  border-radius: 10px;
  background: rgba(30, 41, 59, 0.55);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.entity-card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.entity-badges {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.entity-prompt-preview {
  margin: 8px 0;
  font-size: 0.76rem;
  color: #cbd5e1;
  line-height: 1.4;
}

.entity-tools-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.tool-chip {
  padding: 2px 7px;
  border-radius: 6px;
  font-size: 0.68rem;
  background: rgba(99, 102, 241, 0.16);
  color: #c7d2fe;
}

.provider-meta-line,
.cron-preview-box {
  margin-top: 6px;
  font-size: 0.73rem;
  color: #94a3b8;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

/* KPI & Observability */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.kpi-card {
  padding: 14px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(99, 102, 241, 0.25);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.kpi-label {
  font-size: 0.74rem;
  color: #94a3b8;
}

.kpi-value {
  font-size: 1.35rem;
  color: #f8fafc;
}

.kpi-sub {
  font-size: 0.7rem;
  color: #6ee7b7;
}

.ranking-bars {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ranking-label {
  display: flex;
  justify-content: space-between;
  font-size: 0.76rem;
  margin-bottom: 3px;
}

.ranking-track {
  height: 7px;
  border-radius: 999px;
  background: rgba(30, 41, 59, 0.8);
  overflow: hidden;
}

.ranking-fill {
  height: 100%;
  background: linear-gradient(90deg, #6366f1, #06b6d4);
  border-radius: 999px;
}

.traces-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 280px;
  overflow-y: auto;
}

.trace-item {
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(30, 41, 59, 0.55);
  font-size: 0.75rem;
}

.trace-top {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 4px;
}

.console-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.console-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.console-terminal {
  background: #060911;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 10px;
  padding: 10px;
  height: 410px;
  overflow-y: auto;
  font-family: var(--font-mono, monospace);
  font-size: 0.74rem;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.console-line {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  line-height: 1.4;
}

.log-time {
  color: #64748b;
}

.log-level {
  color: #38bdf8;
  font-weight: 700;
}

.level-warn .log-level {
  color: #fbbf24;
}

.level-error .log-level {
  color: #fb7185;
}

.log-source {
  color: #a78bfa;
}

.log-msg {
  color: #e2e8f0;
  word-break: break-word;
}

/* Submodals */
.submodal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1250;
  background: rgba(4, 6, 12, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px;
}

.submodal-box {
  width: min(980px, 100%);
  max-height: 86vh;
  display: flex;
  flex-direction: column;
  background: #0f1628;
  border: 1px solid rgba(99, 102, 241, 0.45);
  border-radius: 16px;
  padding: 16px;
  overflow: hidden;
}

.qr-submodal {
  width: min(480px, 100%);
}

.submodal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.submodal-header h3 {
  margin: 0;
  font-size: 0.98rem;
}

.submodal-header p {
  margin: 4px 0 0;
  font-size: 0.76rem;
  color: #94a3b8;
}

.catalog-filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.catalog-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  overflow-y: auto;
  padding-right: 4px;
  flex: 1;
}

.catalog-card {
  padding: 10px;
  border-radius: 10px;
  background: rgba(30, 41, 59, 0.55);
  border: 1px solid rgba(148, 163, 184, 0.16);
  cursor: pointer;
  transition: all 0.15s ease;
}

.catalog-card.selected {
  border-color: #6366f1;
  background: rgba(99, 102, 241, 0.2);
}

.catalog-card-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.catalog-icon {
  font-size: 1.4rem;
}

.catalog-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 2px;
}

.catalog-desc {
  margin: 6px 0 0;
  font-size: 0.72rem;
  color: #94a3b8;
  line-height: 1.35;
}

.catalog-create-footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.catalog-create-inputs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.catalog-create-buttons {
  display: flex;
  gap: 8px;
}

/* Switch toggle */
.switch-toggle {
  position: relative;
  display: inline-block;
  width: 36px;
  height: 20px;
  flex-shrink: 0;
}

.switch-toggle input {
  opacity: 0;
  width: 0;
  height: 0;
}

.switch-slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background-color: rgba(100, 116, 139, 0.45);
  transition: 0.2s;
  border-radius: 999px;
}

.switch-slider::before {
  position: absolute;
  content: '';
  height: 14px;
  width: 14px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  transition: 0.2s;
  border-radius: 50%;
}

.switch-toggle input:checked + .switch-slider {
  background-color: #10b981;
}

.switch-toggle input:checked + .switch-slider::before {
  transform: translateX(16px);
}

/* QR Visual */
.qr-visual-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: rgba(15, 23, 42, 0.8);
  border-radius: 12px;
  margin-bottom: 10px;
}

.qr-matrix-art {
  width: 140px;
  height: 140px;
  background: radial-gradient(circle, #1e293b 20%, #0f172a 80%);
  border: 2px dashed #6366f1;
  border-radius: 12px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.qr-center-logo {
  font-size: 2.2rem;
}

.webhook-copy-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px;
  background: rgba(15, 23, 42, 0.9);
  border-radius: 8px;
  margin: 10px 0;
  word-break: break-all;
}

/* Utility badges & buttons */
.badge-indigo {
  background: rgba(99, 102, 241, 0.2);
  color: #a5b4fc;
}

.badge-emerald {
  background: rgba(16, 185, 129, 0.2);
  color: #6ee7b7;
}

.badge-cyan {
  background: rgba(6, 182, 212, 0.2);
  color: #67e8f9;
}

.badge-purple {
  background: rgba(139, 92, 246, 0.2);
  color: #c4b5fd;
}

.btn-xs {
  padding: 4px 8px;
  font-size: 0.72rem;
  border-radius: 6px;
}

.btn-danger-outline {
  background: rgba(244, 63, 94, 0.12);
  border: 1px solid rgba(244, 63, 94, 0.35);
  color: #fda4af;
}

.trace-stages {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: 4px 0 6px;
}

.stage-pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 0.65rem;
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.28);
  color: #6ee7b7;
  font-family: var(--font-mono, monospace);
}

.stage-arrow {
  color: #64748b;
  margin-left: 2px;
}

.mt-2 {
  margin-top: 8px;
}

.mt-3 {
  margin-top: 14px;
}

/* Responsive Breakpoints (Zero Horizontal Overflow down to 320px) */
@media (max-width: 1024px) {
  .platforms-split-layout,
  .editor-grid,
  .dual-column-grid,
  .simulator-layout {
    grid-template-columns: 1fr;
  }

  .catalog-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .astrbot-modal-backdrop {
    padding: 6px;
  }

  .astrbot-modal {
    height: 95vh;
  }

  .astrbot-header {
    flex-direction: column;
    padding: 12px;
  }

  .astrbot-body {
    padding: 10px;
  }

  .form-grid,
  .catalog-grid,
  .catalog-create-inputs,
  .kpi-grid {
    grid-template-columns: 1fr;
  }

  .sim-input-bar {
    flex-direction: column;
  }
}
</style>
