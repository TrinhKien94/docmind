<template>
  <aside 
    class="sidebar-container" 
    :class="{ 'is-mobile-open': isDrawerOpen, 'is-open': isDrawerOpen }"
    role="complementary"
    aria-label="Quản lý tài liệu DocMind"
  >
    <!-- Header / Branding -->
    <div class="sidebar-header">
      <div class="brand">
        <div class="brand-logo">
          <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1-2.5-2.5Z"/>
            <path d="M6 6h10"/>
            <path d="M6 10h10"/>
            <path d="M6 14h6"/>
          </svg>
        </div>
        <div>
          <h2 class="brand-title">DocMind</h2>
          <span class="brand-tag">Grounded RAG AI</span>
        </div>
      </div>

      <div class="sidebar-header-actions">
        <!-- Live / Demo Indicator -->
        <div class="connection-badge" :class="{ 'is-live': isConnected, 'is-demo': !isConnected }" :title="connectionTooltip">
          <span class="status-dot"></span>
          <span class="badge-text">{{ isConnected ? 'API Live (8090)' : 'Chế độ Demo' }}</span>
          <button class="toggle-mode-btn" @click="$emit('toggle-demo')" :title="isConnected ? 'Chuyển sang Demo' : 'Thử kết nối lại API thật'">
            <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
              <path d="M3 3v5h5"/>
              <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
              <path d="M16 21h5v-5"/>
            </svg>
          </button>
        </div>

        <!-- Mobile Drawer Close Button (>= 44px touch target) -->
        <button 
          class="btn-drawer-close"
          data-testid="drawer-close"
          title="Đóng thanh tài liệu (Esc)"
          aria-label="Đóng thanh tài liệu"
          @click="$emit('close'); $emit('close-sidebar');"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- Upload Section -->
    <div class="upload-section">
      <DocumentUploadZone 
        :is-uploading="isUploading"
        @file-selected="$emit('upload-file', $event)"
      />
    </div>

    <!-- Filter & Action Controls -->
    <div class="documents-header">
      <div class="header-title-row">
        <h3 class="section-title">
          Tài liệu đã tải lên
          <span class="count-badge">{{ filteredDocuments.length }}</span>
        </h3>
        <button class="btn-refresh" title="Làm mới danh sách" @click="$emit('refresh-documents')">
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
            <path d="M3 3v5h5"/>
            <path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/>
            <path d="M16 21h5v-5"/>
          </svg>
        </button>
      </div>

      <!-- Quick Status Filter Pills -->
      <div class="filter-pills">
        <button 
          class="pill-btn" 
          :class="{ active: currentFilter === 'all' }"
          @click="currentFilter = 'all'"
        >
          Tất cả ({{ documents.length }})
        </button>
        <button 
          class="pill-btn" 
          :class="{ active: currentFilter === 'READY' }"
          @click="currentFilter = 'READY'"
        >
          Sẵn sàng ({{ readyCount }})
        </button>
        <button 
          v-if="parsingCount > 0"
          class="pill-btn pill-amber" 
          :class="{ active: currentFilter === 'PARSING' }"
          @click="currentFilter = 'PARSING'"
        >
          Đang xử lý ({{ parsingCount }})
        </button>
        <button 
          v-if="failedCount > 0"
          class="pill-btn pill-rose" 
          :class="{ active: currentFilter === 'FAILED' }"
          @click="currentFilter = 'FAILED'"
        >
          Lỗi ({{ failedCount }})
        </button>
      </div>

      <!-- Selection Helper -->
      <div class="selection-bar">
        <button class="btn-text" @click="$emit('toggle-select-all')">
          {{ isAllSelected ? 'Bỏ chọn tất cả' : 'Chọn tất cả tài liệu sẵn sàng' }}
        </button>
        <span class="selection-count">
          Đã chọn: <strong>{{ selectedDocIds.length }}</strong>
        </span>
      </div>
    </div>

    <!-- Document List -->
    <div class="documents-list">
      <div v-if="filteredDocuments.length === 0" class="empty-docs">
        <svg xmlns="http://www.w3.org/2000/svg" width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="empty-icon">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="12" y1="18" x2="12" y2="12"/>
          <line x1="9" y1="15" x2="15" y2="15"/>
        </svg>
        <p>Chưa có tài liệu nào phù hợp.</p>
        <span class="empty-hint">Hãy kéo thả tệp PDF, DOCX vào ô phía trên.</span>
      </div>

      <DocumentCard 
        v-for="doc in filteredDocuments"
        :key="doc.document_id"
        :doc="doc"
        :is-selected="selectedDocIds.includes(doc.document_id)"
        @toggle-select="$emit('toggle-select', $event)"
        @view-detail="$emit('view-detail', $event)"
      />
    </div>

    <!-- Footer Stats / Notes -->
    <div class="sidebar-footer">
      <div class="engine-info">
        <span class="dot-spring"></span>
        <span>Spring Boot 4 + FastPARSER + pgvector</span>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import DocumentUploadZone from './DocumentUploadZone.vue'
import DocumentCard from './DocumentCard.vue'

const props = defineProps({
  documents: {
    type: Array,
    default: () => []
  },
  selectedDocIds: {
    type: Array,
    default: () => []
  },
  isUploading: {
    type: Boolean,
    default: false
  },
  isConnected: {
    type: Boolean,
    default: false
  },
  isOpen: {
    type: Boolean,
    default: false
  },
  isMobileOpen: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits([
  'upload-file',
  'toggle-select',
  'toggle-select-all',
  'refresh-documents',
  'view-detail',
  'toggle-demo',
  'close',
  'close-sidebar'
])

const isDrawerOpen = computed(() => props.isOpen || props.isMobileOpen)

// Close drawer on Escape key
const handleKeyDown = (e) => {
  if (e.key === 'Escape' && isDrawerOpen.value) {
    emit('close')
    emit('close-sidebar')
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown)
})

const currentFilter = ref('all')

const readyCount = computed(() => props.documents.filter(d => d.status === 'READY').length)
const parsingCount = computed(() => props.documents.filter(d => d.status === 'PARSING').length)
const failedCount = computed(() => props.documents.filter(d => d.status === 'FAILED').length)

const filteredDocuments = computed(() => {
  if (currentFilter.value === 'all') return props.documents
  return props.documents.filter(d => d.status === currentFilter.value)
})

const readyDocIds = computed(() => 
  props.documents.filter(d => d.status === 'READY').map(d => d.document_id)
)

const isAllSelected = computed(() => {
  if (readyDocIds.value.length === 0) return false
  return readyDocIds.value.every(id => props.selectedDocIds.includes(id))
})

const connectionTooltip = computed(() => {
  return props.isConnected 
    ? 'Đã kết nối backend Spring Boot localhost:8090' 
    : 'Chế độ Demo (Không cần bật Docker/backend)'
})
</script>

<style scoped>
.sidebar-container {
  width: 380px;
  min-width: 340px;
  max-width: 420px;
  height: 100%;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  user-select: none;
  position: relative;
  flex-shrink: 0;
}

.sidebar-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-drawer-close {
  display: none;
}

.sidebar-header {
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-subtle);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-logo {
  width: 38px;
  height: 38px;
  border-radius: var(--radius-md);
  background: var(--accent-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.4);
}

.brand-title {
  font-size: 1.15rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: linear-gradient(to right, #ffffff, #c7d2fe);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  line-height: 1.2;
}

.brand-tag {
  font-size: 0.7rem;
  color: var(--text-dim);
  font-weight: 500;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.connection-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: var(--radius-full);
  font-size: 0.72rem;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid var(--border-subtle);
}

.connection-badge.is-live {
  background: var(--status-ready-bg);
  border-color: var(--status-ready-border);
  color: var(--status-ready-text);
}

.connection-badge.is-demo {
  background: var(--status-parsing-bg);
  border-color: var(--status-parsing-border);
  color: var(--status-parsing-text);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.toggle-mode-btn {
  background: none;
  border: none;
  color: inherit;
  cursor: pointer;
  padding: 2px;
  display: flex;
  align-items: center;
  opacity: 0.7;
  transition: opacity 0.15s ease;
}

.toggle-mode-btn:hover {
  opacity: 1;
}

.upload-section {
  padding: 16px 20px 12px;
}

.documents-header {
  padding: 0 20px 10px;
}

.header-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.section-title {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  display: flex;
  align-items: center;
  gap: 8px;
}

.count-badge {
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-main);
  padding: 1px 7px;
  border-radius: var(--radius-full);
  font-size: 0.7rem;
}

.btn-refresh {
  background: none;
  border: none;
  color: var(--text-dim);
  cursor: pointer;
  padding: 4px;
  border-radius: var(--radius-sm);
  transition: all 0.15s ease;
  display: flex;
  align-items: center;
}

.btn-refresh:hover {
  color: var(--text-main);
  background: rgba(255, 255, 255, 0.08);
  transform: rotate(45deg);
}

.filter-pills {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.pill-btn {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--border-subtle);
  color: var(--text-dim);
  font-size: 0.72rem;
  font-weight: 500;
  padding: 3px 8px;
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.pill-btn:hover {
  color: var(--text-main);
  background: rgba(255, 255, 255, 0.08);
}

.pill-btn.active {
  background: rgba(99, 102, 241, 0.15);
  border-color: var(--border-active);
  color: #a5b4fc;
}

.pill-btn.pill-amber.active {
  background: var(--status-parsing-bg);
  border-color: var(--status-parsing-border);
  color: var(--status-parsing-text);
}

.pill-btn.pill-rose.active {
  background: var(--status-failed-bg);
  border-color: var(--status-failed-border);
  color: var(--status-failed-text);
}

.selection-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 0.75rem;
  color: var(--text-dim);
  padding-top: 4px;
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}

.btn-text {
  background: none;
  border: none;
  color: #818cf8;
  cursor: pointer;
  font-size: 0.75rem;
  padding: 0;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.btn-text:hover {
  color: #a5b4fc;
}

.documents-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.empty-docs {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 10px;
  color: var(--text-dim);
}

.empty-icon {
  stroke: var(--text-dim);
  margin-bottom: 12px;
  opacity: 0.5;
}

.empty-docs p {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.empty-hint {
  font-size: 0.75rem;
  color: var(--text-dim);
}

.sidebar-footer {
  padding: 12px 20px;
  border-top: 1px solid var(--border-subtle);
  background: rgba(10, 15, 26, 0.4);
}

.engine-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.7rem;
  color: var(--text-dim);
  font-family: var(--font-mono);
}

.dot-spring {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #6366f1;
}

/* Mobile Responsive Off-Canvas Drawer (< 768px) */
@media (max-width: 767px) {
  .sidebar-container {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    width: min(340px, 85vw);
    min-width: 0;
    max-width: 85vw;
    height: 100%;
    height: 100dvh;
    z-index: 1000;
    transform: translateX(-100%);
    transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    box-shadow: none;
    border-right: 1px solid var(--border-active);
    padding-top: max(0px, env(safe-area-inset-top));
    padding-bottom: max(0px, env(safe-area-inset-bottom));
  }

  .sidebar-container.is-mobile-open,
  .sidebar-container.is-open {
    transform: translateX(0);
    box-shadow: 8px 0 32px rgba(0, 0, 0, 0.65);
  }

  .btn-drawer-close {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    min-width: 44px;
    min-height: 44px;
    padding: 0;
    border-radius: var(--radius-md);
    background: rgba(255, 255, 255, 0.06);
    border: 1px solid var(--border-subtle);
    color: var(--text-muted);
    cursor: pointer;
    transition: all 0.15s ease;
  }

  .btn-drawer-close:hover,
  .btn-drawer-close:active {
    background: rgba(255, 255, 255, 0.14);
    color: #ffffff;
    border-color: var(--border-active);
  }

  /* Touch Targets >= 44px for Action Controls */
  .btn-refresh {
    width: 44px;
    height: 44px;
    min-width: 44px;
    min-height: 44px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  .filter-pills {
    gap: 8px;
    padding-bottom: 4px;
  }

  .pill-btn {
    min-height: 44px;
    padding: 8px 14px;
    font-size: 0.8rem;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  .selection-bar {
    min-height: 44px;
    padding: 4px 0;
  }

  .btn-text {
    min-height: 44px;
    display: inline-flex;
    align-items: center;
    padding: 8px 4px;
    font-size: 0.8rem;
  }

  .connection-badge {
    min-height: 44px;
    padding: 4px 10px;
  }

  .toggle-mode-btn {
    width: 32px;
    height: 32px;
    min-width: 32px;
    min-height: 32px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .connection-badge .badge-text {
    display: none;
  }
  .connection-badge {
    padding: 4px 6px;
  }
}
</style>
