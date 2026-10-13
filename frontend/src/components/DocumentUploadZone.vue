<template>
  <div 
    class="upload-container"
    :class="{ 'is-dragging': isDragging, 'is-uploading': isUploading }"
    @dragover.prevent="onDragOver"
    @dragleave.prevent="onDragLeave"
    @drop.prevent="onDrop"
    @click="triggerFileInput"
  >
    <input 
      ref="fileInputRef"
      type="file" 
      class="hidden-input" 
      accept=".pdf,.docx,.pptx,.xlsx,.md,.html,.txt" 
      @change="handleFileSelect"
    />

    <div class="upload-content">
      <div class="upload-icon-wrapper">
        <svg v-if="!isUploading" xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="upload-icon">
          <path d="M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242"/>
          <path d="M12 12v9"/>
          <path d="m16 16-4-4-4 4"/>
        </svg>
        <div v-else class="upload-spinner"></div>
      </div>

      <div class="upload-text">
        <h4 v-if="!isUploading" class="upload-title">
          Kéo thả tệp hoặc <span class="highlight">Chọn từ máy</span>
        </h4>
        <h4 v-else class="upload-title text-indigo">
          Đang tải lên & chuyển giao parser...
        </h4>
        <p class="upload-subtitle">Hỗ trợ PDF, DOCX, PPTX, XLSX, MD, HTML (tối đa 50MB)</p>
      </div>
    </div>

    <!-- Error notice -->
    <div v-if="uploadError" class="upload-error" @click.stop>
      <span>{{ uploadError }}</span>
      <button class="btn-clear-error" @click="uploadError = ''">×</button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  isUploading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['file-selected'])

const fileInputRef = ref(null)
const isDragging = ref(false)
const uploadError = ref('')

const onDragOver = () => {
  isDragging.value = true
}

const onDragLeave = () => {
  isDragging.value = false
}

const onDrop = (e) => {
  isDragging.value = false
  if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length > 0) {
    validateAndEmit(e.dataTransfer.files[0])
  }
}

const triggerFileInput = () => {
  if (props.isUploading) return
  fileInputRef.value?.click()
}

const handleFileSelect = (e) => {
  if (e.target.files && e.target.files.length > 0) {
    validateAndEmit(e.target.files[0])
    e.target.value = '' // reset input
  }
}

const validateAndEmit = (file) => {
  uploadError.value = ''
  if (!file) return

  const maxSize = 50 * 1024 * 1024 // 50MB
  if (file.size > maxSize) {
    uploadError.value = 'Kích thước tệp vượt quá 50MB cho phép.'
    return
  }

  emit('file-selected', file)
}
</script>

<style scoped>
.upload-container {
  border: 1.5px dashed var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 16px 14px;
  background: rgba(15, 23, 42, 0.4);
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
  overflow: hidden;
}

.upload-container:hover {
  border-color: var(--border-active);
  background: rgba(99, 102, 241, 0.05);
  box-shadow: inset 0 0 16px rgba(99, 102, 241, 0.06);
}

.upload-container.is-dragging {
  border-color: var(--accent-primary);
  background: rgba(99, 102, 241, 0.12);
  transform: scale(1.01);
}

.upload-container.is-uploading {
  border-color: var(--accent-primary);
  cursor: wait;
}

.hidden-input {
  display: none;
}

.upload-content {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-icon-wrapper {
  width: 44px;
  height: 44px;
  min-width: 44px;
  border-radius: var(--radius-md);
  background: rgba(99, 102, 241, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a5b4fc;
}

.upload-icon {
  stroke: #818cf8;
}

.upload-spinner {
  width: 22px;
  height: 22px;
  border: 2.5px solid rgba(99, 102, 241, 0.2);
  border-top-color: #818cf8;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.upload-text {
  flex: 1;
  text-align: left;
}

.upload-title {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-main);
  margin-bottom: 2px;
}

.upload-title .highlight {
  color: #818cf8;
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.upload-subtitle {
  font-size: 0.72rem;
  color: var(--text-dim);
}

.text-indigo {
  color: #818cf8;
}

.upload-error {
  margin-top: 10px;
  padding: 6px 10px;
  background: var(--status-failed-bg);
  border: 1px solid var(--status-failed-border);
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  color: var(--status-failed-text);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.btn-clear-error {
  background: none;
  border: none;
  color: var(--status-failed-text);
  font-size: 1rem;
  cursor: pointer;
  padding: 0 4px;
}
</style>
