<template>
  <el-dialog
    :model-value="modelValue"
    :title="`商品预览${spu && spu.name ? '：' + spu.name : ''}`"
    width="760px"
    top="4vh"
    :close-on-click-modal="false"
    @update:model-value="onUpdateVisible"
  >
    <div v-if="spu" class="preview-body">
      <!-- 名称 / 状态 / 分类完整链条 -->
      <div class="pv-head">
        <div class="pv-title-row">
          <span class="pv-name">{{ spu.name }}</span>
          <el-tag :type="spu.status === 1 ? 'success' : 'info'">
            {{ spu.status === 1 ? '展示中' : '已隐藏' }}
          </el-tag>
        </div>
        <el-breadcrumb v-if="crumbItems.length" separator="/" class="pv-crumbs">
          <el-breadcrumb-item v-for="(c, i) in crumbItems" :key="i">{{ c }}</el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <!-- 主图 -->
      <div class="pv-section">
        <div class="pv-section-title">主图</div>
        <div class="pv-main-img-wrap">
          <el-image
            v-if="spu.mainImage"
            :src="spu.mainImage"
            :preview-src-list="[spu.mainImage]"
            preview-teleported
            fit="contain"
            class="pv-main-img"
          />
          <span v-else class="pv-muted">未设置主图</span>
        </div>
      </div>

      <!-- 轮播图 -->
      <div class="pv-section">
        <div class="pv-section-title">轮播图</div>
        <div v-if="spu.imageList && spu.imageList.length" class="pv-carousel">
          <el-image
            v-for="(u, i) in spu.imageList"
            :key="i"
            :src="u"
            :preview-src-list="spu.imageList"
            :initial-index="i"
            preview-teleported
            fit="cover"
            class="pv-thumb"
          />
        </div>
        <span v-else class="pv-muted">未设置轮播图</span>
      </div>

      <!-- 商品详情（富文本 HTML） -->
      <div class="pv-section">
        <div class="pv-section-title">商品详情</div>
        <div v-if="spu.description" class="pv-desc" v-html="spu.description"></div>
        <span v-else class="pv-muted">未填写商品详情</span>
      </div>

      <!-- 规格属性配置 -->
      <div class="pv-section">
        <div class="pv-section-title">规格属性配置</div>
        <div v-if="spu.specConfig && spu.specConfig.length" class="pv-config">
          <div v-for="d in spu.specConfig" :key="d.spec" class="pv-config-item">
            <span class="pv-config-spec">{{ d.spec }}</span>
            <div class="pv-config-values">
              <el-tag v-for="v in d.values || []" :key="v" size="small" effect="plain">{{ v }}</el-tag>
            </div>
          </div>
        </div>
        <span v-else class="pv-muted">未配置规格属性</span>
      </div>

      <!-- SKU 明细 -->
      <div class="pv-section">
        <div class="pv-section-title">SKU 明细</div>
        <el-table v-if="spu.skus && spu.skus.length" :data="spu.skus" border size="small">
          <el-table-column label="规格组合" min-width="220">
            <template #default="{ row }">
              <div class="pv-sku-attrs">
                <el-tag v-for="(a, i) in row.specAttrs || []" :key="i" size="small" type="info">
                  {{ a.spec }}：{{ a.value }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="skuCode" label="商家编码" min-width="130">
            <template #default="{ row }">
              <span>{{ row.skuCode || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="SKU 图片" min-width="120">
            <template #default="{ row }">
              <el-image
                v-if="row.mainImage"
                :src="row.mainImage"
                :preview-src-list="[row.mainImage]"
                preview-teleported
                fit="cover"
                class="pv-sku-img"
              />
              <span v-else class="pv-muted">—</span>
            </template>
          </el-table-column>
        </el-table>
        <span v-else class="pv-muted">暂无 SKU</span>
      </div>
    </div>

    <template #footer>
      <el-button type="primary" @click="onUpdateVisible(false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 商品详情（SpuDetailVO，含 categoryPath / specConfig / skus） */
  spu: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue'])

const crumbItems = computed(() =>
  (props.spu?.categoryPath || '').split(' / ').filter((s) => s && s.trim())
)

function onUpdateVisible(visible) {
  emit('update:modelValue', visible)
}
</script>

<style scoped>
.preview-body {
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 4px;
}

.pv-head {
  padding-bottom: 10px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.pv-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.pv-name {
  font-size: 17px;
  font-weight: 600;
}

.pv-crumbs {
  font-size: 13px;
}

.pv-section {
  margin-bottom: 14px;
}

.pv-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.pv-main-img-wrap {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--el-fill-color-lighter);
  border-radius: 4px;
  padding: 8px;
}

.pv-main-img {
  width: 260px;
  height: 200px;
}

.pv-carousel {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.pv-thumb {
  width: 64px;
  height: 48px;
  border-radius: 4px;
}

.pv-desc {
  max-height: 180px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 10px;
  line-height: 1.6;
  font-size: 13px;
}

.pv-config-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 8px;
}

.pv-config-spec {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 24px;
}

.pv-config-values {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pv-sku-attrs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pv-sku-img {
  width: 48px;
  height: 36px;
  border-radius: 3px;
}

.pv-muted {
  font-size: 13px;
  color: var(--el-text-color-disabled);
}
</style>
