<template>
  <el-dialog
    :model-value="modelValue"
    :title="`SKU 管理${goods && goods.name ? '：' + goods.name : ''}`"
    width="980px"
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
        <span class="sku-tip">
          上下架按 SKU 操作：上架任一 SKU → 商品自动上架；SKU 全部下架 → 商品自动下架。
          <b>已上架 SKU 整行锁定</b>，需先下架才能修改或删除。
        </span>
        <div class="sku-toolbar-actions">
          <el-button
            v-if="centerSkus.length"
            type="warning"
            plain
            size="small"
            @click="syncFromCenter"
          >同步中台 SKU</el-button>
          <el-button type="primary" plain size="small" :disabled="!configs.length" @click="generateMissing">
            生成缺失组合
          </el-button>
        </div>
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
              :disabled="isOnShelf(row)"
              :placeholder="`请选择${dim.spec}`"
              style="width: 100%"
              @change="(v) => onValueChange(row, dim, v)"
            >
              <el-option v-for="ov in optionValues(dim)" :key="ov" :label="ov" :value="ov" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="价格（元）" width="130">
          <template #default="{ row }">
            <el-input-number
              v-model="row.price"
              :min="0.01"
              :precision="2"
              :step="1"
              :controls="false"
              :disabled="isOnShelf(row)"
              placeholder="必填"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="SKU 编码" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.skuCode" maxlength="64" :disabled="isOnShelf(row)" placeholder="可选" />
          </template>
        </el-table-column>
        <el-table-column label="SKU 图片 URL" min-width="200">
          <template #default="{ row }">
            <div class="sku-image-cell">
              <el-input v-model="row.mainImage" :disabled="isOnShelf(row)" placeholder="可选" />
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
        <el-table-column label="上下架" width="90" align="center">
          <template #default="{ row }">
            <el-tooltip
              :disabled="row.id != null"
              content="新 SKU 需先保存后才能上架"
              placement="top"
            >
              <el-switch
                :model-value="isOnShelf(row)"
                :disabled="row.id == null"
                @change="(v) => onShelfChange(row, v)"
              />
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ $index, row }">
            <el-button type="danger" link :disabled="isOnShelf(row)" @click="removeRow($index)">删除</el-button>
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
import { goodsApi } from '../../api/goods'

const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 商品详情（含规格属性配置 specConfig、存量 skus、中台快照 centerSpu） */
  goods: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved', 'changed'])

const configs = ref([]) // [{ spec, values }]
const rows = ref([]) // [{ id, attrs:[{spec,value}], skuCode, mainImage, price, shelfStatus }]
const loading = ref(false)

const emptyText = computed(() =>
  configs.value.length ? '暂无 SKU，点击「生成缺失组合」按规格配置创建' : ''
)

/** 中台模板可同步的 SKU（未关联/中台不可达时为空） */
const centerSkus = computed(() => props.goods?.centerSpu?.skus || [])

function onUpdateVisible(visible) {
  emit('update:modelValue', visible)
}

function isOnShelf(row) {
  return row.shelfStatus === 1
}

function init() {
  configs.value = (props.goods?.specConfig || []).map((d) => ({
    spec: d.spec,
    values: [...(d.values || [])]
  }))
  rows.value = (props.goods?.skus || []).map((s) => ({
    id: s.id,
    attrs: (s.specAttrs || []).map((a) => ({ spec: a.spec, value: a.value })),
    skuCode: s.skuCode || '',
    mainImage: s.mainImage || '',
    price: s.price,
    shelfStatus: s.shelfStatus
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
      rows.value.push({ id: null, attrs, skuCode: '', mainImage: '', price: null, shelfStatus: 0 })
      existingKeys.add(key)
      added++
    }
  }
  ElMessage.success(added ? `已生成 ${added} 个缺失组合，请补充价格/编码/图片` : '组合已齐全，无新增')
}

/**
 * 从中台模板同步 SKU：按 SKU 编码对齐（无编码则按规格组合对齐），
 * 保留店主已填价格，中台新行价格留空待填；已上架行锁死不动（R4）。
 */
function syncFromCenter() {
  const centerList = centerSkus.value
  if (!centerList.length) {
    ElMessage.warning('中台模板暂无 SKU 可同步')
    return
  }
  // 规格维度须先与中台一致（维度在「编辑」中同步商品信息时一并更新），否则组合对不上
  const localSpecs = new Set(configs.value.map((d) => d.spec))
  const centerSpecs = new Set((centerList[0].specAttrs || []).map((a) => a.spec))
  if (centerSpecs.size !== localSpecs.size || [...centerSpecs].some((s) => !localSpecs.has(s))) {
    ElMessage.warning('中台模板的规格维度与本地不一致，请先在「编辑」中点「同步中台」更新规格属性')
    return
  }
  // 中台新增的规格值需先进入规格属性配置，否则后端会拒绝该 SKU 组合
  const unknownValues = []
  for (const cs of centerList) {
    for (const attr of cs.specAttrs || []) {
      const dim = configs.value.find((d) => d.spec === attr.spec)
      if (dim && !(dim.values || []).includes(attr.value)) {
        unknownValues.push(`${attr.spec}=${attr.value}`)
      }
    }
  }
  if (unknownValues.length) {
    ElMessage.warning(
      `中台规格值「${[...new Set(unknownValues)].join('、')}」不在本地规格属性配置中，请先在「编辑」中点「同步中台」`
    )
    return
  }

  let updated = 0
  let added = 0
  let skipped = 0
  for (const cs of centerList) {
    const code = (cs.skuCode || '').trim()
    let row = code ? rows.value.find((r) => (r.skuCode || '').trim() === code) : null
    if (!row) row = rows.value.find((r) => rowKey(r.attrs) === rowKey(cs.specAttrs || []))
    if (row) {
      if (isOnShelf(row)) {
        skipped++ // 已上架锁死，保持不变
        continue
      }
      row.attrs = (cs.specAttrs || []).map((a) => ({ spec: a.spec, value: a.value }))
      row.mainImage = cs.mainImage || row.mainImage
      if (!row.skuCode && cs.skuCode) row.skuCode = cs.skuCode
      updated++
    } else {
      rows.value.push({
        id: null,
        attrs: (cs.specAttrs || []).map((a) => ({ spec: a.spec, value: a.value })),
        skuCode: cs.skuCode || '',
        mainImage: cs.mainImage || '',
        price: null,
        shelfStatus: 0
      })
      added++
    }
  }
  ElMessage.success(
    `已同步中台 SKU：更新 ${updated} 行、新增 ${added} 行（价格保留/待填）${skipped ? `，跳过已上架 ${skipped} 行` : ''}`
  )
}

function removeRow(index) {
  rows.value.splice(index, 1)
}

/** SKU 上下架：即时生效（服务端联动商品上下架）；失败回滚开关 */
async function onShelfChange(row, checked) {
  const prev = row.shelfStatus
  row.shelfStatus = checked ? 1 : 0
  try {
    await goodsApi.updateSkuShelf(props.goods.id, row.id, row.shelfStatus)
    ElMessage.success(row.shelfStatus === 1 ? 'SKU 已上架，商品已联动上架' : 'SKU 已下架')
    emit('changed')
  } catch {
    row.shelfStatus = prev // 拦截器已提示
  }
}

async function handleSave() {
  if (rows.value.length && !configs.value.length) {
    ElMessage.warning('该商品暂无规格属性配置，无法保存 SKU；请先在「编辑」中配置规格属性或清空 SKU')
    return
  }
  if (rows.value.some((r) => !r.price || r.price < 0.01)) {
    ElMessage.warning('请为每个 SKU 填写价格（不低于 0.01）')
    return
  }
  if (rows.value.some((r) => (r.attrs || []).some((a) => !a.value))) {
    ElMessage.warning('存在未选择规格值的 SKU，请补全后再保存')
    return
  }
  const skus = rows.value.map((r) => ({
    id: r.id,
    specAttrs: r.attrs.map((a) => ({ spec: a.spec, value: a.value })),
    skuCode: r.skuCode || null,
    mainImage: r.mainImage || null,
    price: r.price
  }))
  loading.value = true
  try {
    await goodsApi.replaceSkus(props.goods.id, skus)
    ElMessage.success('SKU 已保存')
    emit('saved')
  } catch {
    // 后端业务校验失败（如已上架 SKU 被改）时拦截器已提示，弹窗保持打开供修改
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.sku-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.sku-toolbar-actions {
  flex-shrink: 0;
}

.sku-tip {
  font-size: 12px;
  line-height: 1.6;
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
