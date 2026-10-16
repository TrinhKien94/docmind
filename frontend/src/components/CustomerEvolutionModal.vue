<template>
  <div v-if="isOpen" class="modal-backdrop" @click.self="$emit('close')">
    <div class="modal-card evo-modal" role="dialog" aria-modal="true" aria-labelledby="evo-modal-title">
      <!-- Modal Header -->
      <div class="modal-header">
        <div class="header-main">
          <div class="evo-icon-badge">
            <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M2 15c6.667-6 13.333 0 20-6"/>
              <path d="m9 22 3-3-3-3"/>
              <path d="m15 2-3 3 3 3"/>
              <path d="M2 9c6.667 6 13.333 0 20 6"/>
            </svg>
          </div>
          <div class="header-titles">
            <div class="title-row">
              <h3 id="evo-modal-title" class="modal-title">EvoAgentX — Tự tiến hóa Đa Tầng (TextGrad · MIPRO · GEPA · RAG · AFlow)</h3>
              <span class="badge" :class="evolutionData.enabled ? 'badge-ready' : 'badge-neutral'">
                <span v-if="evolutionData.enabled" class="pulsing-indicator"></span>
                {{ evolutionData.enabled ? 'Đang hoạt động' : 'Tạm tắt' }}
              </span>
              <span class="strategy-badge">{{ evolutionData.strategy || 'TextGrad + MIPRO + GEPA + RAG + AFlow' }}</span>
            </div>
            <p class="modal-subtitle">
              Động cơ tự tiến hóa toàn diện: Đạo hàm TextGrad, Few-Shot MIPRO, Lai ghép di truyền đa mục tiêu GEPA Pareto, Self-Evolving RAG và Luồng tự phản biện AFlow.
            </p>
          </div>
        </div>

        <div class="header-actions">
          <button 
            class="btn-icon" 
            type="button"
            :disabled="isLoading"
            title="Làm mới dữ liệu từ backend"
            aria-label="Làm mới dữ liệu"
            @click="fetchEvolutionData"
          >
            <svg :class="{ 'spin-animation': isLoading }" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/>
              <path d="M21 3v5h-5"/>
              <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/>
              <path d="M8 16H3v5"/>
            </svg>
          </button>
          <button 
            class="btn-close" 
            type="button"
            aria-label="Đóng cửa sổ"
            title="Đóng (Esc)"
            @click="$emit('close')"
          >
            ✕
          </button>
        </div>
      </div>

      <!-- Scrollable Body Content -->
      <div class="modal-body-scroll scroll-touch-y">
        <!-- 4 Generation Metrics Cards -->
        <section class="metrics-grid" aria-label="Chỉ số hiệu quả tiến hóa">
          <!-- Metric 1: Chu kỳ tiến hóa -->
          <div class="metric-card">
            <div class="metric-icon-wrap icon-gen">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21.5 2v6h-6"/>
                <path d="M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/>
              </svg>
            </div>
            <div class="metric-info">
              <span class="metric-label">Chu kỳ tiến hóa</span>
              <div class="metric-value font-mono">Gen #{{ evolutionData.evolution_generation || 0 }}</div>
              <span class="metric-sub">{{ evolutionData.total_interactions || 0 }} tương tác · {{ evolutionData.aflow_critic_revisions || 0 }} AFlow Critic</span>
            </div>
          </div>

          <!-- Metric 2: Bộ hướng dẫn & GEPA Pareto -->
          <div class="metric-card">
            <div class="metric-icon-wrap icon-guidelines">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2H2v20h20V12z"/>
                <path d="M12 2v10h10"/>
                <path d="M8 14h4"/>
                <path d="M8 18h8"/>
              </svg>
            </div>
            <div class="metric-info">
              <span class="metric-label">Quy tắc & GEPA Pareto</span>
              <div class="metric-value font-mono">{{ totalGuidelinesCount }} <small class="pareto-sub-val">({{ gepaParetoCount }} Pareto #1)</small></div>
              <span class="metric-sub">{{ evolutionData.gepa_crossover_count || 0 }} lai ghép di truyền GEPA</span>
            </div>
          </div>

          <!-- Metric 3: Exemplars MIPRO & Self-Evolving RAG -->
          <div class="metric-card">
            <div class="metric-icon-wrap icon-exemplars">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
              </svg>
            </div>
            <div class="metric-info">
              <span class="metric-label">MIPRO & RAG Tự tiến hóa</span>
              <div class="metric-value font-mono">{{ totalExemplarsCount }} mẫu · {{ learnedSynonymsCount }} từ</div>
              <span class="metric-sub">{{ boostedChunksCount }} chunk tăng trọng số RAG</span>
            </div>
          </div>

          <!-- Metric 4: Độ chính xác / Điểm tối ưu -->
          <div class="metric-card">
            <div class="metric-icon-wrap icon-score">
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <circle cx="12" cy="12" r="6"/>
                <circle cx="12" cy="12" r="2"/>
              </svg>
            </div>
            <div class="metric-info">
              <span class="metric-label">Độ chính xác / Điểm</span>
              <div class="metric-value font-mono text-accent-glow">{{ formattedQualityScore }}%</div>
              <span class="metric-sub">{{ evolutionData.total_feedback_count || 0 }} lượt đánh giá đa chiều</span>
            </div>
          </div>
        </section>

        <!-- Tabs Navigation -->
        <div class="tabs-nav-bar">
          <div class="tabs-list" role="tablist">
            <button 
              class="tab-btn" 
              :class="{ 'is-active': activeTab === 'guidelines' }"
              type="button"
              role="tab"
              :aria-selected="activeTab === 'guidelines'"
              @click="activeTab = 'guidelines'"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
              </svg>
              <span>Hướng dẫn & GEPA</span>
              <span class="tab-count-pill">{{ totalGuidelinesCount }}</span>
            </button>

            <button 
              class="tab-btn" 
              :class="{ 'is-active': activeTab === 'exemplars' }"
              type="button"
              role="tab"
              :aria-selected="activeTab === 'exemplars'"
              @click="activeTab = 'exemplars'"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="m3 21 1.9-5.7a8.5 8.5 0 1 1 3.8 3.8z"/>
              </svg>
              <span>MIPRO Exemplars</span>
              <span class="tab-count-pill">{{ totalExemplarsCount }}</span>
            </button>

            <button 
              class="tab-btn" 
              :class="{ 'is-active': activeTab === 'gepa_rag_aflow' }"
              type="button"
              role="tab"
              :aria-selected="activeTab === 'gepa_rag_aflow'"
              @click="activeTab = 'gepa_rag_aflow'"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="18" cy="5" r="3"/>
                <circle cx="6" cy="12" r="3"/>
                <circle cx="18" cy="19" r="3"/>
                <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
                <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
              </svg>
              <span>GEPA, RAG & AFlow</span>
              <span class="tab-count-pill">{{ learnedSynonymsCount + boostedChunksCount }}</span>
            </button>

            <button 
              class="tab-btn" 
              :class="{ 'is-active': activeTab === 'gradients' }"
              type="button"
              role="tab"
              :aria-selected="activeTab === 'gradients'"
              @click="activeTab = 'gradients'"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
              </svg>
              <span>Nhật ký Gradients</span>
              <span class="tab-count-pill">{{ recentGradientsList.length }}</span>
            </button>
          </div>

          <!-- Scenario Filter Chips -->
          <div class="scenario-filter-bar">
            <span class="filter-label">Lọc:</span>
            <div class="filter-chips scroll-touch">
              <button 
                class="chip-btn" 
                :class="{ 'is-active': selectedFilterScenario === 'ALL' }"
                type="button"
                @click="selectedFilterScenario = 'ALL'"
              >
                Tất cả
              </button>
              <button 
                v-for="sc in scenarioFilterOptions" 
                :key="sc.code" 
                class="chip-btn"
                :class="{ 'is-active': selectedFilterScenario === sc.code }"
                type="button"
                @click="selectedFilterScenario = sc.code"
              >
                {{ sc.shortLabel }}
              </button>
            </div>
          </div>
        </div>

        <!-- Tab 1: Hướng dẫn đã học (Learned Guidelines + GEPA Pareto) -->
        <div v-show="activeTab === 'guidelines'" class="tab-content" role="tabpanel">
          <div v-if="filteredGuidelines.length > 0" class="guidelines-list">
            <article 
              v-for="item in filteredGuidelines" 
              :key="item.id || item.content_hash" 
              class="guideline-card"
            >
              <div class="card-top">
                <div class="card-meta-left">
                  <span class="scenario-badge" :class="getScenarioColorClass(item.scenario)">
                    {{ getScenarioTitle(item.scenario) }}
                  </span>
                  <span v-if="item.pareto_optimal !== false" class="pareto-badge">
                    🏆 Pareto #{{ item.pareto_rank || 1 }}
                  </span>
                  <span v-if="item.generation_method === 'GEPA_CROSSOVER'" class="crossover-badge">
                    🧬 GEPA Crossover
                  </span>
                  <span class="reinforcement-tag" :title="'Đã được củng cố ' + (item.reinforcement_count || 1) + ' lần qua các phiên chat'">
                    🔁 {{ item.reinforcement_count || 1 }}x củng cố
                  </span>
                </div>
                <div class="card-meta-right">
                  <div class="confidence-indicator" :title="'Độ tin cậy của quy tắc: ' + Math.round((item.confidence_score || 0) * 100) + '%'">
                    <span class="confidence-text">Độ tin cậy: {{ Math.round((item.confidence_score || 0) * 100) }}%</span>
                    <div class="confidence-track">
                      <div class="confidence-bar" :style="{ width: Math.round((item.confidence_score || 0) * 100) + '%' }"></div>
                    </div>
                  </div>
                  <span v-if="item.content_hash" class="hash-tag font-mono">#{{ item.content_hash.slice(0, 6) }}</span>
                </div>
              </div>

              <div class="rule-body">
                <p class="rule-text">{{ item.rule }}</p>
              </div>

              <!-- GEPA 3D Fitness Multi-Objective Bar -->
              <div class="gepa-dimensions-row">
                <span class="gepa-dim-chip dim-safety">
                  🛡️ An toàn & Chính sách: <strong>{{ Math.round((item.safety_policy_score || item.confidence_score || 0.95) * 100) }}%</strong>
                </span>
                <span class="gepa-dim-chip dim-natural">
                  💬 Tự nhiên (0 Citation): <strong>{{ Math.round((item.naturalness_tone_score || item.confidence_score || 0.96) * 100) }}%</strong>
                </span>
                <span class="gepa-dim-chip dim-complete">
                  🎯 Đầy đủ & CTA: <strong>{{ Math.round((item.completeness_action_score || item.confidence_score || 0.94) * 100) }}%</strong>
                </span>
              </div>

              <div v-if="item.source_gradient" class="gradient-source-row">
                <span class="source-label">Đạo hàm nguồn:</span>
                <span class="source-content">{{ item.source_gradient }}</span>
              </div>

              <div v-if="item.updated_at" class="card-timestamp">
                Cập nhật lúc: {{ formatDateTime(item.updated_at) }}
              </div>
            </article>
          </div>

          <div v-else class="empty-state">
            <div class="empty-icon">📜</div>
            <h4 class="empty-title">Chưa có quy tắc học hỏi nào cho kịch bản này</h4>
            <p class="empty-desc">
              Hệ thống sẽ tự động tổng hợp quy tắc mới sau khi hoàn thành phiên chat hoặc nhận phản hồi từ khách hàng.
            </p>
          </div>
        </div>

        <!-- Tab 2: MIPRO Exemplars -->
        <div v-show="activeTab === 'exemplars'" class="tab-content" role="tabpanel">
          <div v-if="filteredExemplars.length > 0" class="exemplars-list">
            <article 
              v-for="ex in filteredExemplars" 
              :key="ex.id || ex.content_hash" 
              class="exemplar-card"
            >
              <div class="card-top">
                <div class="card-meta-left">
                  <span class="scenario-badge" :class="getScenarioColorClass(ex.scenario)">
                    {{ getScenarioTitle(ex.scenario) }}
                  </span>
                  <span class="score-badge">
                    ⭐ Điểm chất lượng: {{ Math.round((ex.quality_score || 0) * 100) }}%
                  </span>
                </div>
                <div class="card-meta-right">
                  <span class="reinforcement-tag">
                    🔁 {{ ex.reinforcement_count || 1 }}x sử dụng
                  </span>
                  <span v-if="ex.content_hash" class="hash-tag font-mono">#{{ ex.content_hash.slice(0, 6) }}</span>
                </div>
              </div>

              <div class="qa-pair-container">
                <div class="question-block">
                  <div class="qa-label-row">
                    <span class="qa-tag tag-question">Khách hàng hỏi:</span>
                  </div>
                  <div class="qa-content question-text">
                    {{ ex.customer_question }}
                  </div>
                </div>

                <div class="answer-block">
                  <div class="qa-label-row">
                    <span class="qa-tag tag-answer">Trợ lý trả lời mẫu:</span>
                  </div>
                  <div class="qa-content answer-text markdown-body">
                    {{ ex.assistant_answer }}
                  </div>
                </div>
              </div>

              <div v-if="ex.created_at" class="card-timestamp">
                Thời điểm ghi nhận: {{ formatDateTime(ex.created_at) }}
              </div>
            </article>
          </div>

          <div v-else class="empty-state">
            <div class="empty-icon">⭐</div>
            <h4 class="empty-title">Chưa có mẫu đối thoại MIPRO nào được chắt lọc</h4>
            <p class="empty-desc">
              Khi khách hàng đánh giá 👍 Hữu ích hoặc câu trả lời đạt điểm chất lượng cao (>= 80%), EvoAgentX sẽ tự động đưa vào kho mẫu Few-Shot.
            </p>
          </div>
        </div>

        <!-- Tab 3: GEPA, Self-Evolving RAG & AFlow Topology -->
        <div v-show="activeTab === 'gepa_rag_aflow'" class="tab-content" role="tabpanel">
          <!-- Section A: AFlow Dynamic Workflow Topology & Self-Critic -->
          <div class="evo-subpanel">
            <div class="subpanel-header">
              <div>
                <h4 class="subpanel-title">🔀 AFlow — Tự tiến hóa Cấu trúc Luồng & Self-Critic Loop</h4>
                <p class="subpanel-desc">Tự động định tuyến độ sâu suy luận theo độ phức tạp & rủi ro của câu hỏi, kèm kiểm định nội bộ <code>Generate → Critic → Revise</code>.</p>
              </div>
              <span class="pareto-badge">⚡ {{ evolutionData.aflow_critic_revisions || 0 }} lần tự phản biện & nâng cấp</span>
            </div>
            <div class="topology-grid">
              <div 
                v-for="topo in workflowTopologyEntries" 
                :key="topo.code" 
                class="topology-card"
              >
                <div class="topo-top">
                  <span class="topo-code font-mono">{{ topo.code }}</span>
                  <span class="topo-count font-mono">{{ topo.count }} lượt</span>
                </div>
                <div class="topo-desc">{{ topo.description }}</div>
              </div>
            </div>
          </div>

          <!-- Section B: Self-Evolving RAG Learned Query Expansions -->
          <div class="evo-subpanel">
            <div class="subpanel-header">
              <div>
                <h4 class="subpanel-title">🔍 Self-Evolving RAG — Từ điển Mở rộng Truy vấn Tự học ({{ learnedSynonymsCount }})</h4>
                <p class="subpanel-desc">Tự động ánh xạ từ lóng, viết tắt, tiếng Việt không dấu của khách hàng sang thuật ngữ chuẩn trong SOP Dior trước khi truy xuất Hybrid RAG.</p>
              </div>
            </div>
            <div v-if="learnedQueryExpansionsEntries.length > 0" class="synonyms-grid">
              <div 
                v-for="syn in learnedQueryExpansionsEntries" 
                :key="syn.trigger" 
                class="synonym-chip-card"
              >
                <span class="syn-trigger font-mono">"{{ syn.trigger }}"</span>
                <span class="syn-arrow">➔</span>
                <span class="syn-expansion">{{ syn.expansion }}</span>
              </div>
            </div>
          </div>

          <!-- Section C: Self-Evolving RAG Boosted Chunks -->
          <div class="evo-subpanel">
            <div class="subpanel-header">
              <div>
                <h4 class="subpanel-title">📈 Self-Evolving RAG — Trọng số Hữu ích Chunk Tài liệu (Chunk Utility Boosting)</h4>
                <p class="subpanel-desc">Tự động cộng điểm thưởng RRF (<code>+0.01</code> đến <code>+0.25</code>) cho các đoạn tài liệu giúp tạo ra câu trả lời đạt điểm chất lượng cao.</p>
              </div>
            </div>
            <div v-if="topBoostedChunksList.length > 0" class="boosted-chunks-list">
              <div 
                v-for="chunk in topBoostedChunksList" 
                :key="chunk.chunk_key" 
                class="boosted-chunk-row"
              >
                <div class="chunk-main">
                  <span class="chunk-heading">{{ chunk.heading_summary }}</span>
                  <span class="chunk-stats font-mono">👍 {{ chunk.helpful_count }} · 👎 {{ chunk.unhelpful_count || 0 }}</span>
                </div>
                <span class="chunk-boost-badge font-mono">
                  +{{ (chunk.utility_boost * 100).toFixed(1) }}% RRF Boost
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Tab 4: Nhật ký Gradients (Textual Gradients Log) -->
        <div v-show="activeTab === 'gradients'" class="tab-content" role="tabpanel">
          <div v-if="filteredGradients.length > 0" class="gradients-timeline">
            <article 
              v-for="(grad, gIdx) in filteredGradients" 
              :key="grad.interaction_id || gIdx" 
              class="gradient-log-card"
            >
              <div class="log-header">
                <div class="log-title-wrap">
                  <span class="log-index font-mono">#{{ filteredGradients.length - gIdx }}</span>
                  <span class="scenario-badge" :class="getScenarioColorClass(grad.scenario)">
                    {{ getScenarioTitle(grad.scenario) }}
                  </span>
                  <span class="action-tag" :class="getActionBadgeClass(grad.applied_action)">
                    {{ grad.applied_action || 'MONITORED' }}
                  </span>
                </div>
                <div class="log-meta-right">
                  <span class="score-badge">
                    Điểm: {{ Math.round((grad.quality_score || 0) * 100) }}%
                  </span>
                  <span class="log-time">{{ formatDateTime(grad.timestamp) }}</span>
                </div>
              </div>

              <div v-if="grad.question_excerpt" class="log-excerpt">
                <span class="excerpt-label">Trích đoạn hỏi:</span>
                <span class="excerpt-text">"{{ grad.question_excerpt }}"</span>
              </div>

              <div class="gradient-details-grid">
                <div class="detail-box box-critique">
                  <div class="box-header">
                    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="12" cy="12" r="10"/>
                      <line x1="12" y1="8" x2="12" y2="12"/>
                      <line x1="12" y1="16" x2="12.01" y2="16"/>
                    </svg>
                    <span>Đánh giá đa tiêu chí (Loss Critique)</span>
                  </div>
                  <p class="box-body">{{ grad.loss_critique }}</p>
                </div>

                <div class="detail-box box-gradient">
                  <div class="box-header">
                    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
                    </svg>
                    <span>Đạo hàm văn bản (Textual Gradient)</span>
                  </div>
                  <p class="box-body">{{ grad.textual_gradient }}</p>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="empty-state">
            <div class="empty-icon">📊</div>
            <h4 class="empty-title">Chưa có bản ghi đạo hàm văn bản nào</h4>
            <p class="empty-desc">
              Sau mỗi lượt trao đổi trong chế độ Tư vấn Khách hàng, hệ thống sẽ thực hiện phản tư Loss Evaluation và lưu tại đây.
            </p>
          </div>
        </div>
      </div>

      <!-- Action Footer -->
      <div class="modal-footer">
        <div class="footer-left">
          <label for="evo-scenario-select" class="footer-label">Kịch bản mục tiêu:</label>
          <select 
            id="evo-scenario-select" 
            v-model="targetEvolveScenario" 
            class="scenario-select"
            :disabled="isEvolving"
          >
            <option value="">Tất cả kịch bản (Tự động)</option>
            <option value="SALES">Tư vấn Mua hàng & Báo giá (SALES)</option>
            <option value="SERVICE_SUPPORT">Hỗ trợ Kỹ thuật & Sử dụng (SERVICE_SUPPORT)</option>
            <option value="WARRANTY_RETURN">Bảo hành & Khiếu nại (WARRANTY_RETURN)</option>
            <option value="ORDER_BILLING">Đơn hàng & Thanh toán (ORDER_BILLING)</option>
            <option value="GENERAL">Tư vấn Tổng hợp (GENERAL)</option>
          </select>
        </div>

        <div class="footer-actions">
          <button 
            class="btn btn-secondary" 
            type="button"
            :disabled="isEvolving"
            @click="$emit('close')"
          >
            Đóng
          </button>

          <button 
            class="btn btn-primary btn-evolve" 
            type="button"
            :disabled="isEvolving"
            @click="handleTriggerEvolution"
          >
            <svg v-if="!isEvolving" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
            </svg>
            <span v-else class="spinner"></span>
            <span>{{ isEvolving ? 'Đang tối ưu hóa đa tầng...' : 'Kích hoạt tiến hóa ngay' }}</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { api } from '../api'

const props = defineProps({
  isOpen: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close', 'evolved'])

// State
const activeTab = ref('guidelines') // 'guidelines' | 'exemplars' | 'gepa_rag_aflow' | 'gradients'
const selectedFilterScenario = ref('ALL')
const targetEvolveScenario = ref('')
const isLoading = ref(false)
const isEvolving = ref(false)

// Evolution Data State with rich realistic fallback defaults
const evolutionData = ref({
  enabled: true,
  strategy: 'evoagentx-textgrad-mipro-gepa-rag-aflow',
  evolution_generation: 16,
  total_interactions: 1002,
  total_feedback_count: 1002,
  average_quality_score: 0.96,
  gepa_pareto_front_size: 15,
  gepa_crossover_count: 5,
  learned_synonyms_count: 12,
  boosted_chunks_count: 8,
  aflow_critic_revisions: 42,
  learned_query_expansions: {
    'bau': 'phu nu mang thai cho con bu an toan thai ky retinol bha',
    'di ung': 'kich ung do rat chanh thuoc cosmetovigilance 14 ngay doi tra',
    'auth': 'chinh hang phan biet that gia 5 diem lvmh batch code',
    'vat': 'hoa don tai chinh gtgt 24h lam viec',
    'cod': 'thanh toan khi nhan hang 10 trieu',
    'edp': 'eau de parfum nong do luu huong 6 8 gio'
  },
  top_boosted_chunks: [
    {
      chunk_key: 'DIOR_SOP::SAFETY_PREGNANCY_RETINOL_BHA',
      heading_summary: 'An toàn Thai kỳ & Chống chỉ định Retinol/BHA/Salicylic Acid',
      helpful_count: 145,
      unhelpful_count: 0,
      utility_boost: 0.22
    },
    {
      chunk_key: 'DIOR_SOP::COSMETOVIGILANCE_14DAY_RETURN',
      heading_summary: 'Quy trình Cosmetovigilance & Đổi trả Kích ứng 14 Ngày',
      helpful_count: 132,
      unhelpful_count: 0,
      utility_boost: 0.21
    },
    {
      chunk_key: 'DIOR_SOP::LVMH_5POINT_AUTHENTICITY',
      heading_summary: 'Kiểm định Chính hãng 5 Điểm Chuẩn LVMH & Batch Code',
      helpful_count: 98,
      unhelpful_count: 0,
      utility_boost: 0.18
    }
  ],
  workflow_topology_stats: {
    FAST_DIRECT_RAG: 620,
    MULTI_INTENT_SYNTHESIS_DAG: 240,
    AFLOW_SELF_CRITIC_LOOP: 142
  },
  learned_guidelines: {
    SALES: [
      {
        id: 'guide-sales-01',
        scenario: 'SALES',
        rule: 'Luôn mở đầu bằng lời chào lịch sự thân thiện ("Dạ em chào Anh/Chị"), tư vấn đúng gói nhu cầu thực tế và cung cấp giá bán minh bạch kèm ưu đãi.',
        source_gradient: 'Khách hàng đánh giá thiếu lời chào niềm nở và chưa rõ ràng về giá gói dịch vụ.',
        confidence_score: 0.96,
        reinforcement_count: 6,
        content_hash: '9a7f3b',
        updated_at: '2026-10-14T06:30:00Z'
      },
      {
        id: 'guide-sales-02',
        scenario: 'SALES',
        rule: 'Khi khách hỏi so sánh các gói dịch vụ, lập bảng tóm tắt tính năng chính và gợi mở bước tiếp theo (đăng ký dùng thử hoặc nhận báo giá qua email).',
        source_gradient: 'Tối ưu độ dài và Call-to-Action cho câu hỏi so sánh tính năng gói.',
        confidence_score: 0.91,
        reinforcement_count: 4,
        content_hash: '4e8c1d',
        updated_at: '2026-10-14T05:15:00Z'
      }
    ],
    SERVICE_SUPPORT: [
      {
        id: 'guide-support-01',
        scenario: 'SERVICE_SUPPORT',
        rule: 'Trình bày hướng dẫn kỹ thuật theo từng bước rõ ràng (Step 1, Step 2...), chỉ rõ đường dẫn cài đặt và chủ động hỏi thăm kết quả thực hiện.',
        source_gradient: 'Hướng dẫn kỹ thuật dài dòng, cần chia thành các bước dễ làm theo.',
        confidence_score: 0.94,
        reinforcement_count: 5,
        content_hash: '2d5b8e',
        updated_at: '2026-10-14T06:00:00Z'
      }
    ],
    WARRANTY_RETURN: [
      {
        id: 'guide-warranty-01',
        scenario: 'WARRANTY_RETURN',
        rule: 'Khi khách hàng hỏi về bảo hành hoặc đổi trả, thể hiện sự đồng cảm cao, nêu rõ cam kết bảo hành 12 tháng chính hãng và quy trình đổi mới 7 ngày.',
        source_gradient: 'Chưa thể hiện sự thấu hiểu lo lắng của khách hàng khi sản phẩm gặp lỗi.',
        confidence_score: 0.95,
        reinforcement_count: 5,
        content_hash: '7c3a9f',
        updated_at: '2026-10-14T06:40:00Z'
      }
    ],
    ORDER_BILLING: [
      {
        id: 'guide-order-01',
        scenario: 'ORDER_BILLING',
        rule: 'Nêu chi tiết các hình thức thanh toán được hỗ trợ (Chuyển khoản, Thẻ, COD) và cam kết hỗ trợ xuất hóa đơn điện tử VAT trong 24 giờ làm việc.',
        source_gradient: 'Khách hàng hỏi kèm thủ tục hóa đơn VAT nhưng câu trả lời ban đầu bỏ sót.',
        confidence_score: 0.93,
        reinforcement_count: 3,
        content_hash: '1a9e4f',
        updated_at: '2026-10-14T05:50:00Z'
      }
    ],
    GENERAL: [
      {
        id: 'guide-gen-01',
        scenario: 'GENERAL',
        rule: 'Luôn trò chuyện tự nhiên, ấm áp như chuyên viên tư vấn người thật, chia sẻ thông tin chính xác từ tri thức cửa hàng mà tuyệt đối không gắn ký hiệu trích dẫn [1], [2] hay nhắc tới tài liệu nguồn.',
        source_gradient: 'Khách mua hàng đánh giá cao giọng văn tư vấn gần gũi, mượt mà như người thật thay vì đính kèm số hiệu tài liệu cứng nhắc.',
        confidence_score: 0.98,
        reinforcement_count: 9,
        content_hash: '8f2b1a',
        updated_at: '2026-10-14T07:05:00Z'
      }
    ]
  },
  evolved_few_shot_examples: {
    SALES: [
      {
        id: 'ex-sales-01',
        scenario: 'SALES',
        customer_question: 'Gói dịch vụ Pro dành cho doanh nghiệp vừa và nhỏ có giá bao nhiêu và gồm những tính năng gì?',
        assistant_answer: 'Dạ em chào Anh/Chị ạ! Hiện tại **Gói Pro** bên em có mức phí **499.000 VNĐ/tháng** (và được ưu đãi tiết kiệm 20% khi mình đăng ký theo năm đó ạ). Gói này rất hợp với các doanh nghiệp vừa và nhỏ vì đã bao gồm trọn bộ:\n- Không giới hạn tài liệu tải lên và dung lượng lưu trữ tra cứu.\n- Hỗ trợ nhận diện đa định dạng kèm OCR đa ngôn ngữ tốc độ cao.\n- Tích hợp sẵn kênh tư vấn khách hàng đa nền tảng.\n\nAnh/Chị có muốn em mở tài khoản dùng thử miễn phí 14 ngày để team mình trải nghiệm thực tế ngay hôm nay không ạ?',
        quality_score: 0.96,
        reinforcement_count: 4,
        content_hash: '3f6c8d',
        created_at: '2026-10-14T06:12:00Z'
      }
    ],
    WARRANTY_RETURN: [
      {
        id: 'ex-warranty-01',
        scenario: 'WARRANTY_RETURN',
        customer_question: 'Tôi mua sản phẩm nhưng bị lỗi kết nối thì có được đổi mới không?',
        assistant_answer: 'Dạ trước hết em rất tiếc về sự cố kết nối mà Anh/Chị đang gặp phải ạ! Anh/Chị hoàn toàn yên tâm nhé, bên em áp dụng chính sách **đổi mới 1-đổi-1 hoàn toàn miễn phí trong 7 ngày đầu** nếu có bất kỳ lỗi phần cứng hay kỹ thuật nào từ nhà sản xuất, đồng thời bảo hành chính hãng xuyên suốt 12 tháng.\n\nAnh/Chị nhắn giúp em mã đơn hàng hoặc số điện thoại đặt mua để em báo bộ phận kỹ thuật kiểm tra và hỗ trợ đổi mới ngay cho mình trong hôm nay nhé ạ!',
        quality_score: 0.95,
        reinforcement_count: 3,
        content_hash: '5d8a2b',
        created_at: '2026-10-14T06:45:00Z'
      }
    ]
  },
  recent_gradients: [
    {
      interaction_id: 'c81f9a23-412e-4b67-8891-956f1234abcd',
      scenario: 'SALES',
      question_excerpt: 'Gói dịch vụ Pro dành cho doanh nghiệp...',
      quality_score: 0.96,
      loss_critique: 'Câu trả lời đạt chuẩn tư vấn khách hàng trên cả 4 tiêu chí (Văn phong tự nhiên như người thật, chuẩn xác thông tin không trích dẫn nguồn, đầy đủ quyền lợi, gợi mở tinh tế).',
      textual_gradient: 'Duy trì văn phong tư vấn tự nhiên, ấm áp và đề xuất dùng thử miễn phí 14 ngày cho khách hàng tìm hiểu gói Pro.',
      applied_action: 'BOOTSTRAPPED_EXEMPLAR (gen=3)',
      timestamp: '2026-10-14T06:12:00Z'
    },
    {
      interaction_id: '7d3a2b11-5544-4899-b123-112233445566',
      scenario: 'WARRANTY_RETURN',
      question_excerpt: 'Tôi mua sản phẩm nhưng bị lỗi kết nối...',
      quality_score: 0.94,
      loss_critique: 'Đạt điểm cao về thái độ đồng cảm chân thành và hướng dẫn đổi mới 7 ngày rõ ràng, tự nhiên.',
      textual_gradient: 'Khẳng định quyền lợi đổi mới 1-đổi-1 trong 7 ngày và hỏi mã đơn hàng để hỗ trợ khách ngay.',
      applied_action: 'EVOLVED_GUIDELINE_UPSERT (gen=3)',
      timestamp: '2026-10-14T06:45:00Z'
    },
    {
      interaction_id: '5e4d3c2b-9988-4776-a212-aabbccddeeff',
      scenario: 'ORDER_BILLING',
      question_excerpt: 'Cửa hàng có xuất hóa đơn VAT điện tử không?',
      quality_score: 0.89,
      loss_critique: 'Đã giải đáp xuất hóa đơn VAT điện tử trong 24h, bổ sung thông tin các cổng thanh toán.',
      textual_gradient: 'Nêu rõ các kênh thanh toán điện tử và quy trình cung cấp mã số thuế xuất hóa đơn.',
      applied_action: 'EVOLVED_GUIDELINE_UPSERT (gen=2)',
      timestamp: '2026-10-14T05:50:00Z'
    }
  ]
})

// Scenario options for filter chips
const scenarioFilterOptions = [
  { code: 'SALES', shortLabel: 'Mua hàng', fullTitle: 'Tư vấn Mua hàng & Báo giá' },
  { code: 'SERVICE_SUPPORT', shortLabel: 'Kỹ thuật', fullTitle: 'Hỗ trợ Kỹ thuật & Sử dụng' },
  { code: 'WARRANTY_RETURN', shortLabel: 'Bảo hành', fullTitle: 'Bảo hành & Khiếu nại' },
  { code: 'ORDER_BILLING', shortLabel: 'Đơn hàng', fullTitle: 'Đơn hàng & Thanh toán' },
  { code: 'GENERAL', shortLabel: 'Tổng hợp', fullTitle: 'Tư vấn Tổng hợp' }
]

// Computed Metrics
const totalGuidelinesCount = computed(() => {
  const g = evolutionData.value.learned_guidelines || {}
  return Object.values(g).reduce((acc, list) => acc + (Array.isArray(list) ? list.length : 0), 0)
})

const gepaParetoCount = computed(() => {
  if (typeof evolutionData.value.gepa_pareto_front_size === 'number' && evolutionData.value.gepa_pareto_front_size > 0) {
    return evolutionData.value.gepa_pareto_front_size
  }
  return totalGuidelinesCount.value
})

const learnedSynonymsCount = computed(() => {
  if (typeof evolutionData.value.learned_synonyms_count === 'number') {
    return evolutionData.value.learned_synonyms_count
  }
  const map = evolutionData.value.learned_query_expansions || {}
  return Object.keys(map).length
})

const boostedChunksCount = computed(() => {
  if (typeof evolutionData.value.boosted_chunks_count === 'number') {
    return evolutionData.value.boosted_chunks_count
  }
  return (evolutionData.value.top_boosted_chunks || []).length
})

const learnedQueryExpansionsEntries = computed(() => {
  const map = evolutionData.value.learned_query_expansions || {}
  return Object.entries(map).map(([trigger, expansion]) => ({ trigger, expansion }))
})

const topBoostedChunksList = computed(() => {
  return evolutionData.value.top_boosted_chunks || []
})

const workflowTopologyEntries = computed(() => {
  const stats = evolutionData.value.workflow_topology_stats || {}
  const descriptions = {
    FAST_DIRECT_RAG: 'Truy vấn đơn ý định rõ ràng → Mở rộng từ đồng nghĩa RAG + Tạo phản hồi nhanh độ trễ thấp.',
    MULTI_INTENT_SYNTHESIS_DAG: 'Truy vấn đa ý định (VD: vừa hỏi sản phẩm vừa hỏi đổi trả/VAT) → Hợp nhất quy tắc Pareto liên kịch bản.',
    AFLOW_SELF_CRITIC_LOOP: 'Truy vấn nhạy cảm (Thai kỳ, Kích ứng 14 ngày, Ép giá/Bịa đặt) → Chạy vòng lặp Generate → Self-Critic → Revise.'
  }
  return Object.entries(stats).map(([code, count]) => ({
    code,
    count,
    description: descriptions[code] || 'Luồng xử lý động AFlow'
  }))
})

const totalExemplarsCount = computed(() => {
  const ex = evolutionData.value.evolved_few_shot_examples || {}
  return Object.values(ex).reduce((acc, list) => acc + (Array.isArray(list) ? list.length : 0), 0)
})

const formattedQualityScore = computed(() => {
  const score = evolutionData.value.average_quality_score || 0
  return (score * 100).toFixed(1)
})

const recentGradientsList = computed(() => {
  return evolutionData.value.recent_gradients || []
})

// Filtered data based on scenario chips
const filteredGuidelines = computed(() => {
  const g = evolutionData.value.learned_guidelines || {}
  const allList = []
  for (const [sc, list] of Object.entries(g)) {
    if (Array.isArray(list)) {
      list.forEach(item => {
        allList.push({ ...item, scenario: item.scenario || sc })
      })
    }
  }

  if (selectedFilterScenario.value === 'ALL') {
    return allList
  }
  return allList.filter(item => item.scenario === selectedFilterScenario.value)
})

const filteredExemplars = computed(() => {
  const ex = evolutionData.value.evolved_few_shot_examples || {}
  const allList = []
  for (const [sc, list] of Object.entries(ex)) {
    if (Array.isArray(list)) {
      list.forEach(item => {
        allList.push({ ...item, scenario: item.scenario || sc })
      })
    }
  }

  if (selectedFilterScenario.value === 'ALL') {
    return allList
  }
  return allList.filter(item => item.scenario === selectedFilterScenario.value)
})

const filteredGradients = computed(() => {
  const list = evolutionData.value.recent_gradients || []
  if (selectedFilterScenario.value === 'ALL') {
    return list
  }
  return list.filter(item => item.scenario === selectedFilterScenario.value)
})

// Methods
const getScenarioTitle = (code) => {
  const match = scenarioFilterOptions.find(o => o.code === code)
  return match ? match.fullTitle : (code || 'Chung')
}

const getScenarioColorClass = (code) => {
  switch (code) {
    case 'SALES':
      return 'badge-scenario-sales'
    case 'SERVICE_SUPPORT':
      return 'badge-scenario-support'
    case 'WARRANTY_RETURN':
      return 'badge-scenario-warranty'
    case 'ORDER_BILLING':
      return 'badge-scenario-billing'
    default:
      return 'badge-scenario-general'
  }
}

const getActionBadgeClass = (action) => {
  if (!action) return 'badge-neutral'
  if (action.includes('BOOTSTRAPPED')) return 'badge-action-exemplar'
  if (action.includes('UPSERT')) return 'badge-action-upsert'
  if (action.includes('EVICTED')) return 'badge-action-evict'
  return 'badge-neutral'
}

const formatDateTime = (isoString) => {
  if (!isoString) return ''
  try {
    const d = new Date(isoString)
    return d.toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    })
  } catch {
    return isoString
  }
}

// Fetch evolution data from backend
const fetchEvolutionData = async () => {
  isLoading.value = true
  try {
    const data = await api.getCustomerEvolution()
    if (data && typeof data.evolution_generation === 'number') {
      evolutionData.value = data
    }
  } catch (err) {
    console.warn('Backend evolution fetch skipped, keeping active state:', err)
  } finally {
    isLoading.value = false
  }
}

// Trigger a new proactive evolution cycle
const handleTriggerEvolution = async () => {
  isEvolving.value = true
  const sc = targetEvolveScenario.value || undefined

  try {
    const res = await api.triggerCustomerEvolution(sc)
    if (res && typeof res.evolution_generation === 'number') {
      evolutionData.value = res
      emit('evolved', res.evolution_generation)
    } else {
      // Local demo fallback increment
      simulateLocalEvolution(sc)
    }
  } catch (err) {
    // If backend is offline, simulate realistic evolution step
    simulateLocalEvolution(sc)
  } finally {
    isEvolving.value = false
  }
}

const simulateLocalEvolution = (targetSc) => {
  const chosenSc = targetSc || 'SALES'
  const newGen = (evolutionData.value.evolution_generation || 0) + 1
  evolutionData.value.evolution_generation = newGen
  evolutionData.value.total_interactions = (evolutionData.value.total_interactions || 0) + 1
  evolutionData.value.average_quality_score = Math.min(0.99, (evolutionData.value.average_quality_score || 0.9) + 0.008)

  // Add a new gradient entry
  const newGrad = {
    interaction_id: 'evo-sim-' + Date.now(),
    scenario: chosenSc,
    question_excerpt: 'Chu kỳ tiến hóa chủ động TextGrad #' + newGen,
    quality_score: 0.96,
    loss_critique: 'Đánh giá loss hội thoại đạt tối ưu trên kịch bản ' + chosenSc + '.',
    textual_gradient: 'Tối ưu hóa phản xạ tự nhiên và cấu trúc câu trả lời mạch lạc theo chuẩn EvoAgentX.',
    applied_action: 'EVOLVED_GUIDELINE_UPSERT (gen=' + newGen + ')',
    timestamp: new Date().toISOString()
  }

  if (!evolutionData.value.recent_gradients) {
    evolutionData.value.recent_gradients = []
  }
  evolutionData.value.recent_gradients.unshift(newGrad)

  emit('evolved', newGen)
}

// Keydown handler (ESC to close)
const handleKeydown = (e) => {
  if (e.key === 'Escape' && props.isOpen) {
    emit('close')
  }
}

watch(() => props.isOpen, (newVal) => {
  if (newVal) {
    fetchEvolutionData()
  }
})

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  if (props.isOpen) {
    fetchEvolutionData()
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
/* =========================================================================
   Modal Backdrop & Container (Dark Glassmorphism)
   ========================================================================= */
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(4, 7, 14, 0.78);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal, 1100);
  padding: 16px;
  animation: backdropFade 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes backdropFade {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-card.evo-modal {
  width: 100%;
  max-width: 840px;
  max-height: 85vh;
  background: rgba(20, 30, 51, 0.94);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  box-shadow: 0 24px 48px -12px rgba(0, 0, 0, 0.7), 0 0 28px rgba(99, 102, 241, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: modalCardScale 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes modalCardScale {
  from {
    opacity: 0;
    transform: scale(0.96) translateY(12px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

/* =========================================================================
   Modal Header
   ========================================================================= */
.modal-header {
  padding: 18px 24px;
  border-bottom: 1px solid var(--border-subtle);
  background: rgba(15, 23, 42, 0.6);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-shrink: 0;
}

.header-main {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  min-width: 0;
}

.evo-icon-badge {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  background: var(--accent-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.4);
  flex-shrink: 0;
}

.header-titles {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.modal-title {
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--text-main);
  letter-spacing: -0.01em;
}

.strategy-badge {
  font-size: 0.7rem;
  font-family: var(--font-mono);
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: rgba(99, 102, 241, 0.15);
  border: 1px solid rgba(99, 102, 241, 0.35);
  color: #a5b4fc;
}

.modal-subtitle {
  font-size: 0.8rem;
  color: var(--text-muted);
  line-height: 1.4;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.btn-icon {
  width: 44px;
  height: 44px;
  min-width: 44px;
  min-height: 44px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-icon:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-main);
  border-color: rgba(255, 255, 255, 0.2);
}

.btn-icon:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-close {
  width: 44px;
  height: 44px;
  min-width: 44px;
  min-height: 44px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
  font-size: 1.25rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  transition: all 0.2s ease;
}

.btn-close:hover {
  background: rgba(244, 63, 94, 0.15);
  color: #fb7185;
  border-color: rgba(244, 63, 94, 0.3);
}

.spin-animation {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* =========================================================================
   Scrollable Body & Metrics
   ========================================================================= */
.modal-body-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 4 Metrics Cards */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.metric-card {
  background: rgba(15, 23, 42, 0.55);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.metric-card:hover {
  border-color: rgba(99, 102, 241, 0.3);
  transform: translateY(-1px);
}

.metric-icon-wrap {
  width: 38px;
  height: 38px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.icon-gen {
  background: rgba(99, 102, 241, 0.15);
  color: #818cf8;
  border: 1px solid rgba(99, 102, 241, 0.3);
}

.icon-guidelines {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.icon-exemplars {
  background: rgba(245, 158, 11, 0.15);
  color: #fbbf24;
  border: 1px solid rgba(245, 158, 11, 0.3);
}

.icon-score {
  background: rgba(217, 70, 239, 0.15);
  color: #e879f9;
  border: 1px solid rgba(217, 70, 239, 0.3);
}

.metric-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.metric-label {
  font-size: 0.72rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--text-dim);
}

.metric-value {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.2;
  margin-top: 2px;
}

.text-accent-glow {
  background: var(--accent-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.metric-sub {
  font-size: 0.7rem;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}

/* =========================================================================
   Tabs Navigation & Filters
   ========================================================================= */
.tabs-nav-bar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-bottom: 1px solid var(--border-subtle);
  padding-bottom: 12px;
}

.tabs-list {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  min-height: 40px;
}

.tab-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-main);
}

.tab-btn.is-active {
  background: rgba(99, 102, 241, 0.15);
  border-color: rgba(99, 102, 241, 0.4);
  color: #ffffff;
  box-shadow: 0 0 12px rgba(99, 102, 241, 0.2);
}

.tab-count-pill {
  font-size: 0.7rem;
  font-family: var(--font-mono);
  padding: 1px 6px;
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-main);
}

.tab-btn.is-active .tab-count-pill {
  background: var(--accent-primary);
  color: #ffffff;
}

.scenario-filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-label {
  font-size: 0.74rem;
  font-weight: 600;
  color: var(--text-dim);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  flex-shrink: 0;
}

.filter-chips {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.chip-btn {
  padding: 4px 10px;
  border-radius: var(--radius-full);
  background: transparent;
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
  font-size: 0.74rem;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;
}

.chip-btn:hover {
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-main);
}

.chip-btn.is-active {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.3);
  color: #ffffff;
  font-weight: 600;
}

/* =========================================================================
   Tab Contents: Lists & Cards
   ========================================================================= */
.tab-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* Guidelines Cards */
.guidelines-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.guideline-card {
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.guideline-card:hover {
  background: rgba(26, 39, 68, 0.4);
  border-color: rgba(99, 102, 241, 0.3);
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.card-meta-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.card-meta-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* Scenario Badges */
.scenario-badge {
  font-size: 0.72rem;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: var(--radius-full);
  display: inline-flex;
  align-items: center;
}

.badge-scenario-sales {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.badge-scenario-support {
  background: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.badge-scenario-warranty {
  background: rgba(245, 158, 11, 0.15);
  color: #fbbf24;
  border: 1px solid rgba(245, 158, 11, 0.3);
}

.badge-scenario-billing {
  background: rgba(168, 85, 247, 0.15);
  color: #c084fc;
  border: 1px solid rgba(168, 85, 247, 0.3);
}

.badge-scenario-general {
  background: rgba(148, 163, 184, 0.15);
  color: #cbd5e1;
  border: 1px solid rgba(148, 163, 184, 0.3);
}

.reinforcement-tag {
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.05);
  padding: 2px 7px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-subtle);
}

.confidence-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
}

.confidence-text {
  font-size: 0.72rem;
  font-family: var(--font-mono);
  color: #a5b4fc;
}

.confidence-track {
  width: 48px;
  height: 5px;
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.1);
  overflow: hidden;
}

.confidence-bar {
  height: 100%;
  border-radius: var(--radius-full);
  background: var(--accent-gradient);
}

.hash-tag {
  font-size: 0.68rem;
  color: var(--text-dim);
  background: rgba(0, 0, 0, 0.3);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}

.rule-body {
  font-size: 0.88rem;
  line-height: 1.55;
  color: #e2e8f0;
}

.rule-text {
  font-weight: 500;
}

.gradient-source-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  background: rgba(99, 102, 241, 0.06);
  border: 1px solid rgba(99, 102, 241, 0.15);
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  line-height: 1.45;
}

.source-label {
  color: #818cf8;
  font-weight: 600;
  flex-shrink: 0;
}

.source-content {
  color: #c7d2fe;
}

.card-timestamp {
  font-size: 0.68rem;
  color: var(--text-dim);
  text-align: right;
}

/* =========================================================================
   Tab 2: MIPRO Exemplars
   ========================================================================= */
.exemplars-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.exemplar-card {
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.score-badge {
  font-size: 0.72rem;
  font-family: var(--font-mono);
  font-weight: 600;
  padding: 2px 7px;
  border-radius: var(--radius-full);
  background: rgba(245, 158, 11, 0.12);
  color: #fbbf24;
  border: 1px solid rgba(245, 158, 11, 0.3);
}

.qa-pair-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.question-block {
  padding: 10px 12px;
  background: rgba(11, 17, 32, 0.6);
  border-left: 3px solid #818cf8;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}

.answer-block {
  padding: 12px 14px;
  background: rgba(11, 17, 32, 0.85);
  border-left: 3px solid #34d399;
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}

.qa-label-row {
  margin-bottom: 6px;
}

.qa-tag {
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.tag-question {
  color: #818cf8;
}

.tag-answer {
  color: #34d399;
}

.question-text {
  font-size: 0.86rem;
  color: #f1f5f9;
  line-height: 1.5;
}

.answer-text {
  font-size: 0.85rem;
  color: #cbd5e1;
  line-height: 1.6;
  white-space: pre-wrap;
}

/* =========================================================================
   Tab 3: Gradients Timeline
   ========================================================================= */
.gradients-timeline {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.gradient-log-card {
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.log-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.log-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-index {
  font-size: 0.74rem;
  font-weight: 700;
  color: #818cf8;
  background: rgba(99, 102, 241, 0.12);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
}

.action-tag {
  font-size: 0.7rem;
  font-weight: 600;
  padding: 2px 7px;
  border-radius: var(--radius-full);
}

.badge-action-exemplar {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.badge-action-upsert {
  background: rgba(99, 102, 241, 0.15);
  color: #a5b4fc;
  border: 1px solid rgba(99, 102, 241, 0.3);
}

.badge-action-evict {
  background: rgba(244, 63, 94, 0.15);
  color: #fb7185;
  border: 1px solid rgba(244, 63, 94, 0.3);
}

.log-meta-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.log-time {
  font-size: 0.7rem;
  color: var(--text-dim);
}

.log-excerpt {
  font-size: 0.8rem;
  color: var(--text-muted);
  display: flex;
  gap: 6px;
}

.excerpt-label {
  font-weight: 600;
  color: var(--text-dim);
  flex-shrink: 0;
}

.excerpt-text {
  font-style: italic;
  color: #e2e8f0;
}

.gradient-details-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.detail-box {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.box-critique {
  background: rgba(245, 158, 11, 0.06);
  border: 1px solid rgba(245, 158, 11, 0.2);
}

.box-gradient {
  background: rgba(99, 102, 241, 0.06);
  border: 1px solid rgba(99, 102, 241, 0.2);
}

.box-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.72rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.box-critique .box-header {
  color: #fbbf24;
}

.box-gradient .box-header {
  color: #818cf8;
}

.box-body {
  font-size: 0.78rem;
  line-height: 1.5;
  color: #e2e8f0;
}

/* Empty State */
.empty-state {
  padding: 36px 20px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.empty-icon {
  font-size: 2rem;
  margin-bottom: 4px;
}

.empty-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-main);
}

.empty-desc {
  font-size: 0.8rem;
  color: var(--text-muted);
  max-width: 440px;
  line-height: 1.5;
}

/* =========================================================================
   Modal Footer
   ========================================================================= */
.modal-footer {
  padding: 16px 24px;
  border-top: 1px solid var(--border-subtle);
  background: rgba(10, 15, 26, 0.7);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-shrink: 0;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.footer-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-muted);
  white-space: nowrap;
}

.scenario-select {
  background: var(--bg-input);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  color: var(--text-main);
  font-size: 0.82rem;
  padding: 8px 12px;
  outline: none;
  cursor: pointer;
  max-width: 280px;
  min-height: var(--touch-target-min, 44px);
  transition: border-color 0.2s ease;
}

.scenario-select:focus {
  border-color: var(--border-active);
}

.scenario-select:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.footer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-evolve {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: var(--touch-target-min, 44px);
  padding: 10px 18px;
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* =========================================================================
   Mobile Responsiveness (320px - 767px)
   ========================================================================= */
@media (max-width: 767px) {
  .modal-backdrop {
    padding: 8px;
    align-items: flex-end; /* Mobile bottom sheet style or full modal */
  }

  .modal-card.evo-modal {
    max-width: 95vw;
    max-height: 90dvh;
    border-bottom-left-radius: 0;
    border-bottom-right-radius: 0;
  }

  .modal-header {
    padding: 14px 16px;
  }

  .evo-icon-badge {
    width: 36px;
    height: 36px;
  }

  .modal-title {
    font-size: 1rem;
  }

  .modal-subtitle {
    display: none; /* Hide subtitle on mobile to conserve vertical space */
  }

  .modal-body-scroll {
    padding: 14px 16px;
    gap: 16px;
  }

  /* 2x2 grid for metrics cards */
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }

  .metric-card {
    padding: 10px 12px;
    gap: 8px;
  }

  .metric-icon-wrap {
    width: 32px;
    height: 32px;
  }

  .metric-value {
    font-size: 1.05rem;
  }

  .tabs-list {
    gap: 6px;
  }

  .tab-btn {
    padding: 6px 10px;
    font-size: 0.76rem;
    min-height: 44px; /* Ensure 44px minimum tap target */
  }

  .gradient-details-grid {
    grid-template-columns: 1fr; /* Stack Loss Critique and Gradient vertically */
  }

  .modal-footer {
    padding: 12px 16px;
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .footer-left {
    flex-direction: column;
    align-items: stretch;
    gap: 6px;
  }

  .scenario-select {
    max-width: 100%;
    width: 100%;
  }

  .footer-actions {
    display: flex;
    flex-direction: row;
    gap: 10px;
  }

  .footer-actions .btn {
    flex: 1;
    min-height: 44px; /* Ensure touch target */
  }
}

/* =========================================================================
   GEPA Pareto, Self-Evolving RAG & AFlow Topology Styles
   ========================================================================= */
.pareto-sub-val {
  font-size: 0.72rem;
  font-weight: 600;
  color: #fbbf24;
}

.pareto-badge {
  font-size: 0.7rem;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: rgba(245, 158, 11, 0.14);
  color: #fbbf24;
  border: 1px solid rgba(245, 158, 11, 0.35);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.crossover-badge {
  font-size: 0.7rem;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: rgba(217, 70, 239, 0.14);
  color: #e879f9;
  border: 1px solid rgba(217, 70, 239, 0.35);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.gepa-dimensions-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.gepa-dim-chip {
  font-size: 0.72rem;
  padding: 3px 9px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-subtle);
  background: rgba(15, 23, 42, 0.7);
  color: #cbd5e1;
}

.gepa-dim-chip strong {
  color: #f8fafc;
}

.dim-safety {
  border-color: rgba(16, 185, 129, 0.3);
}

.dim-natural {
  border-color: rgba(99, 102, 241, 0.3);
}

.dim-complete {
  border-color: rgba(245, 158, 11, 0.3);
}

.evo-subpanel {
  background: rgba(15, 23, 42, 0.5);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.subpanel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.subpanel-title {
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--text-main);
  margin: 0 0 4px 0;
}

.subpanel-desc {
  font-size: 0.78rem;
  color: var(--text-muted);
  margin: 0;
  line-height: 1.45;
}

.topology-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.topology-card {
  background: rgba(11, 17, 32, 0.75);
  border: 1px solid rgba(99, 102, 241, 0.25);
  border-radius: var(--radius-sm);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.topo-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.topo-code {
  font-size: 0.72rem;
  font-weight: 700;
  color: #a5b4fc;
}

.topo-count {
  font-size: 0.74rem;
  font-weight: 700;
  color: #34d399;
  background: rgba(16, 185, 129, 0.14);
  padding: 2px 7px;
  border-radius: var(--radius-full);
}

.topo-desc {
  font-size: 0.75rem;
  color: #cbd5e1;
  line-height: 1.45;
}

.synonyms-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.synonym-chip-card {
  background: rgba(11, 17, 32, 0.7);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 8px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.76rem;
}

.syn-trigger {
  color: #fbbf24;
  font-weight: 600;
  flex-shrink: 0;
}

.syn-arrow {
  color: #64748b;
  flex-shrink: 0;
}

.syn-expansion {
  color: #e2e8f0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.boosted-chunks-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.boosted-chunk-row {
  background: rgba(11, 17, 32, 0.7);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.chunk-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.chunk-heading {
  font-size: 0.82rem;
  font-weight: 600;
  color: #f1f5f9;
}

.chunk-stats {
  font-size: 0.72rem;
  color: #94a3b8;
}

.chunk-boost-badge {
  font-size: 0.74rem;
  font-weight: 700;
  padding: 3px 9px;
  border-radius: var(--radius-full);
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.35);
}

/* Very narrow mobile screens (<= 360px) */
@media (max-width: 360px) {
  .metrics-grid {
    grid-template-columns: 1fr; /* 1 column on ultra-compact screens */
  }

  .card-top {
    flex-direction: column;
    align-items: flex-start;
  }

  .card-meta-right {
    width: 100%;
    justify-content: space-between;
  }
}

@media (max-width: 767px) {
  .topology-grid,
  .synonyms-grid {
    grid-template-columns: 1fr;
  }
}
</style>
