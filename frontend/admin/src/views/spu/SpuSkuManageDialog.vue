<template>
  <el-dialog
    :model-value="modelValue"
    :title="`SKU 管理${spu && spu.name ? '：' + spu.name : ''}`"
    width="900px"
    top="4vh"
    :close-on-click-modal="false"
    @update:model-value="onUpdateVisible"
    @open="init"
  >
    <el-empty
      v-if="!configs.length && !rows.length"
      description="该商品暂无规格属性配置，请先在「编辑」中配置规格属性后再添加 SKU"
    />
    <template v-else>
      <div class="sku-toolbar">
        <span class="sku-tip">规格组合取自商品的规格属性配置（各规格维度取一个值）</span>
        <el-button type="primary" plain size="small" :disabled="!configs.length" @click="generateMissing">
          生成缺失组合
        </el-button>
      </div>
      <el-table v-loading="loading" :data="rows" border size="small" :empty-text="emptyText">
        <el-table-column
          v-for="dim in configs"
          :key="dim.spec"
          :label="dim.spec"
          min-width="120"
        >
          <template #default="{ row }">
            <el-select
              :model-value="cellValue(row, dim.spec)"
              :placeholder="`请选择${dim.spec}`"
              style="width: 100%"
              @change="(v) => onValueChange(row, dim, v)"
            >
              <el-option v-for="ov in optionValues(dim)" :key="ov" :label="ov" :value="ov" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="SKU 编码" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.skuCode" maxlength="64" placeholder="商家编码（可选）" />
          </template>
        </el-table-column>
        <el-table-column label="SKU 图片 URL" min-width="220">
          <template #default="{ row }">
            <div class="sku-image-cell">
              <el-input v-model="row.mainImage" placeholder="图片地址（可选）" />
              <el-image
                v-if="row.mainImage"
                :src="row.mainImage"
                :preview-src-list="[row.mainImage]"
                preview-teleported
                fit="cover"
                class="sku-image-thumb"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link @click="removeRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <template #footer>
      <el-button @click="onUpdateVisible(false)">取消</el-button>
      <el-button type="primary" :disabled="!configs.length && !!rows.length" @click="handleSave">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { spuApi } from '../../api/spu'

const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 商品详情（SpuDetailVO，含规格属性配置 specConfig 与存量 skus） */
  spu: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])

const configs = ref([]) // [{ spec, values }]
const rows = ref([]) // [{ id, attrs:[{spec,value}], skuCode, mainImage }]
const loading = ref(false)

const emptyText = computed(() =>
  configs.value.length ? '暂无 SKU，点击「生成缺失组合」按规格配置创建' : ''
)

function onUpdateVisible(visible) {
  emit('update:modelValue', visible)
}

function init() {
  configs.value = (props.spu?.specConfig || []).map((d) => ({
    spec: d.spec,
    values: [...(d.values || [])]
  }))
  rows.value = (props.spu?.skus || []).map((s) => ({
    id: s.id,
    attrs: (s.specAttrs || []).map((a) => ({ spec: a.spec, value: a.value })),
    skuCode: s.skuCode || '',
    mainImage: s.mainImage || ''
  }))
  // 有规格配置时规范化存量行：丢弃不在配置内的规格、补全缺失维度（取该维度第一个可选项），
  // 保证与后端「SKU 规格集合须等于商品规格属性配置」一致
  if (configs.value.length) {
    const specNames = new Set(configs.value.map((d) => d.spec))
    for (const row of rows.value) {
      const attrs = row.attrs.filter((a) => specNames.has(a.spec))
      for (const dim of configs.value) {
        if (!attrs.some((a) => a.spec === dim.spec)) {
          attrs.push({ spec: dim.spec, value: (dim.values && dim.values[0]) || '' })
        }
      }
      row.attrs = attrs
    }
  }
}

/** 组合唯一键（按规格名排序拼接，与后端 comboKey 一致） */
function rowKey(attrs) {
  return [...attrs]
    .sort((a, b) => (a.spec < b.spec ? -1 : a.spec > b.spec ? 1 : 0))
    .map((a) => `${a.spec}=${a.value}`)
    .join('|')
}

function cellValue(row, spec) {
  const attr = row.attrs.find((a) => a.spec === spec)
  return attr ? attr.value : ''
}

function attrOf(row, spec) {
  return row.attrs.find((a) => a.spec === spec)
}

function hasDup(targetRow) {
  const key = rowKey(targetRow.attrs)
  return rows.value.some((r) => r !== targetRow && rowKey(r.attrs) === key)
}

function onValueChange(row, dim, val) {
  const attr = attrOf(row, dim.spec)
  if (!attr || attr.value === val) return
  const oldVal = attr.value
  attr.value = val
  if (hasDup(row)) {
    attr.value = oldVal
    ElMessage.warning('该规格组合已存在，无法重复添加')
  }
}

/** 该维度可选项 = 规格配置值 + 存量行已用值（兼容历史孤儿值显示） */
function optionValues(dim) {
  const set = new Set(dim.values || [])
  for (const row of rows.value) {
    const a = attrOf(row, dim.spec)
    if (a && a.value) set.add(a.value)
  }
  return [...set]
}

/** 规格配置各维度的笛卡尔积 */
function cartesian(dims) {
  return dims.reduce(
    (acc, dim) =>
      acc.flatMap((attrs) => (dim.values || []).map((v) => [...attrs, { spec: dim.spec, value: v }])),
    [[]]
  )
}

function generateMissing() {
  const combos = cartesian(configs.value)
  const existingKeys = new Set(rows.value.map((r) => rowKey(r.attrs)))
  let added = 0
  for (const attrs of combos) {
    const key = rowKey(attrs)
    if (!existingKeys.has(key)) {
      rows.value.push({ id: null, attrs, skuCode: '', mainImage: '' })
      existingKeys.add(key)
      added++
    }
  }
  ElMessage.success(added ? `已生成 ${added} 个缺失组合，请补充编码/图片` : '组合已齐全，无新增')
}

function removeRow(index) {
  rows.value.splice(index, 1)
}

async function handleSave() {
  // 规格配置为空但存在 SKU（历史异常数据）：无规格商品无法保存带规格的 SKU
  if (rows.value.length && !configs.value.length) {
    ElMessage.warning('该商品暂无规格属性配置，无法保存 SKU；请先在「编辑」中配置规格属性或清空 SKU')
    return
  }
  const skus = rows.value.map((r) => ({
    id: r.id,
    specAttrs: r.attrs.map((a) => ({ spec: a.spec, value: a.value })),
    skuCode: r.skuCode || null,
    mainImage: r.mainImage || null
  }))
  loading.value = true
  try {
    await spuApi.updateSkus(props.spu.id, skus)
    ElMessage.success('SKU 已保存')
    emit('saved')
  } catch {
    // 后端业务校验失败时拦截器已提示，弹窗保持打开供修改
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.sku-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.sku-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.sku-image-cell {
  display: flex;
  gap: 6px;
  align-items: center;
}

.sku-image-thumb {
  width: 40px;
  height: 30px;
  flex-shrink: 0;
  border-radius: 3px;
}
</style>
