<template>
  <div>
    <!-- A区：规格维度编辑器 -->
    <el-divider content-position="left">规格维度</el-divider>
    <div v-for="(dim, dimIdx) in dims" :key="dimIdx" class="dim-row">
      <el-input
        v-model="dim.spec"
        placeholder="规格名，如：颜色"
        maxlength="32"
        class="dim-name"
        @change="syncSkusFromDims"
      />
      <el-select
        v-model="dim.values"
        multiple
        filterable
        allow-create
        default-first-option
        :reserve-keyword="false"
        placeholder="输入规格值后回车，如：曜石黑"
        class="dim-values"
        @change="syncSkusFromDims"
        @remove-tag="syncSkusFromDims"
      />
      <el-button type="danger" link @click="removeDim(dimIdx)">删除</el-button>
    </div>
    <el-button type="primary" plain size="small" @click="addDim">添加规格维度</el-button>

    <!-- B区：SKU 组合表格 -->
    <el-divider content-position="left">SKU 组合（{{ skus.length }}）</el-divider>
    <el-table :data="skus" size="small" border class="sku-table">
      <el-table-column v-for="dim in activeDims" :key="dim.spec" :label="dim.spec" min-width="130">
        <template #default="{ row }">
          <el-select
            :model-value="specValueOf(row, dim.spec)"
            filterable
            size="small"
            style="width: 100%"
            @change="(val) => onRowSpecChange(row, dim.spec, val)"
          >
            <el-option v-for="opt in dim.values" :key="opt" :label="opt" :value="opt" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="SKU 编码" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.skuCode" size="small" maxlength="64" placeholder="可选" />
        </template>
      </el-table-column>
      <el-table-column label="SKU 图片 URL" min-width="200">
        <template #default="{ row }">
          <el-input v-model="row.mainImage" size="small" placeholder="可选" />
        </template>
      </el-table-column>
      <el-table-column v-if="skus.length" label="操作" width="70" align="center">
        <template #default="{ $index }">
          <el-button type="danger" link size="small" @click="removeSku($index)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="请在上方录入规格名称与值，将自动生成 SKU 组合" :image-size="60" />
      </template>
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'

/**
 * SKU 编辑器
 * skus: [{ id, specAttrs: [{spec, value}], skuCode, mainImage }]
 * - 规格维度变化时按笛卡尔积重建组合；已有相同组合保留 id/skuCode/mainImage（id 稳定供后端 diff）
 * - 组合行内可改值/编码/图片/删除
 */
const props = defineProps({
  skus: { type: Array, default: () => [] }
})

/** 规格维度 [{ spec, values: [] }] */
const dims = ref([])

/** 有名称且有值的有效维度（组合表格列） */
const activeDims = computed(() => dims.value.filter((d) => d.spec.trim() && d.values.length))

/** 生成规格组合唯一键（与后端约定一致：按规格名排序拼接） */
function keyOf(specAttrs) {
  return [...specAttrs]
    .map((a) => `${a.spec}=${a.value}`)
    .sort()
    .join('|')
}

/** 笛卡尔积展开 */
function cartesian(active) {
  return active.reduce(
    (acc, dim) => {
      const out = []
      acc.forEach((prefix) => dim.values.forEach((v) => out.push([...prefix, { spec: dim.spec, value: v }])))
      return out
    },
    [[]]
  )
}

/** 用规格维度重建 skus：保留仍存在的组合（id/skuCode/mainImage），新组合 id 为空 */
function syncSkusFromDims() {
  const active = activeDims.value
  if (!active.length) {
    props.skus.splice(0, props.skus.length)
    return
  }
  const existing = new Map(props.skus.map((s) => [keyOf(s.specAttrs), s]))
  const next = cartesian(active).map((specAttrs) => {
    const key = keyOf(specAttrs)
    const old = existing.get(key)
    if (old) {
      return old
    }
    return { id: null, specAttrs, skuCode: '', mainImage: '' }
  })
  props.skus.splice(0, props.skus.length, ...next)
}

/** 编辑回显：从已保存 skus 反推规格维度与组合 */
function buildFromSaved() {
  const specsOrder = []
  const valuesBySpec = new Map()
  props.skus.forEach((sku) => {
    sku.specAttrs.forEach((a) => {
      if (!valuesBySpec.has(a.spec)) {
        valuesBySpec.set(a.spec, [])
        specsOrder.push(a.spec)
      }
      if (!valuesBySpec.get(a.spec).includes(a.value)) {
        valuesBySpec.get(a.spec).push(a.value)
      }
    })
  })
  dims.value = specsOrder.map((spec) => ({ spec, values: valuesBySpec.get(spec) }))
}

function addDim() {
  dims.value.push({ spec: '', values: [] })
}

function removeDim(idx) {
  dims.value.splice(idx, 1)
  syncSkusFromDims()
}

function specValueOf(row, spec) {
  const attr = row.specAttrs.find((a) => a.spec === spec)
  return attr ? attr.value : ''
}

/** 组合行内修改规格值：查重，重复则提示并还原 */
function onRowSpecChange(row, spec, value) {
  const attr = row.specAttrs.find((a) => a.spec === spec)
  const oldVal = attr ? attr.value : null
  if (oldVal === value) return
  if (attr) {
    attr.value = value
  } else {
    row.specAttrs.push({ spec, value })
  }
  const duplicated = props.skus.some((s) => s !== row && keyOf(s.specAttrs) === keyOf(row.specAttrs))
  if (duplicated) {
    ElMessage.warning('该规格组合已存在，无法重复添加')
    if (attr) {
      attr.value = oldVal
    } else {
      const idx = row.specAttrs.findIndex((a) => a.spec === spec)
      row.specAttrs.splice(idx, 1)
    }
  }
}

function removeSku(idx) {
  props.skus.splice(idx, 1)
}

/** 由父组件在弹窗打开时调用：新增传空，编辑传 detail.skus */
function reset(savedSkus) {
  if (savedSkus && savedSkus.length) {
    props.skus.splice(
      0,
      props.skus.length,
      ...savedSkus.map((s) => ({
        id: s.id,
        specAttrs: (s.specAttrs || []).map((a) => ({ ...a })),
        skuCode: s.skuCode || '',
        mainImage: s.mainImage || ''
      }))
    )
    buildFromSaved()
  } else {
    props.skus.splice(0, props.skus.length)
    dims.value = []
    addDim()
  }
}

defineExpose({ reset })
</script>

<style scoped>
.dim-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}

.dim-name {
  width: 160px;
  flex-shrink: 0;
}

.dim-values {
  flex: 1;
}

.sku-table {
  margin-top: 4px;
}
</style>
