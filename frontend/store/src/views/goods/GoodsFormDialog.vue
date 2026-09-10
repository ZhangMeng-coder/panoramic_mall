<template>
  <el-dialog
    :model-value="modelValue"
    :title="type === 'edit' ? '编辑商品' : '新增商品'"
    width="880px"
    top="4vh"
    :close-on-click-modal="false"
    @update:model-value="onUpdateVisible"
    @open="initForm"
  >
    <!-- 新增：填中台 SKU 编码整单预填（可选，查不到可继续自建） -->
    <el-form v-if="type === 'add'" inline class="center-query-form">
      <el-form-item label="SKU 编码">
        <el-input
          v-model="skuCodeQuery"
          placeholder="填入中台 SKU 编码可整单预填（可选）"
          clearable
          style="width: 260px"
          @keyup.enter="handleQueryCenter"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" plain :loading="querying" @click="handleQueryCenter">查询中台</el-button>
        <span class="center-query-tip">按中台模板预填商品信息与规格，SKU 编码保留、价格需自行填写</span>
      </el-form-item>
    </el-form>

    <!-- 编辑：关联中台的版本比对结果 -->
    <el-alert
      v-if="type === 'edit' && goods && goods.centerOutdated"
      class="center-alert"
      type="warning"
      show-icon
      :closable="false"
      title="中台模板已更新"
    >
      <template #default>
        <span>本商品关联的标准商品在中台已被修改（版本不一致）。可点「同步中台」用中台当前内容覆盖表单；不同步则保留你当前编辑的内容。</span>
        <div class="center-sync-action">
          <el-button type="warning" plain size="small" @click="applyCenter">{{ syncButtonText }}</el-button>
        </div>
      </template>
    </el-alert>
    <el-alert
      v-else-if="type === 'edit' && goods && goods.centerMissing"
      class="center-alert"
      type="info"
      show-icon
      :closable="false"
      title="关联的中台商品已不存在"
      description="该商品原先关联的中台模板已被删除或暂不可取；商品信息不受影响，可继续编辑保存。"
    />

    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="商品名称" prop="name">
            <el-input v-model="form.name" maxlength="128" placeholder="请输入商品名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="分类" prop="categoryId">
            <el-tree-select
              v-model="form.categoryId"
              :data="categoryOptions"
              :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
              node-key="id"
              check-strictly
              default-expand-all
              placeholder="请选择叶子分类"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="品牌">
            <el-select v-model="form.brandId" filterable clearable placeholder="请选择品牌（非必填）" style="width: 100%">
              <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="上下架">
            <el-tag :type="form.shelfStatus === 1 ? 'success' : 'info'">
              {{ form.shelfStatus === 1 ? '上架中' : '已下架' }}
            </el-tag>
            <span class="shelf-tip">由上架中的 SKU 联动，去「规格」中切换</span>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="主图 URL">
        <div class="image-url-row">
          <el-input v-model="form.mainImage" placeholder="请输入商品主图地址" />
          <el-image
            v-if="form.mainImage"
            :src="form.mainImage"
            :preview-src-list="[form.mainImage]"
            preview-teleported
            fit="contain"
            class="main-image-preview"
          />
        </div>
      </el-form-item>

      <el-form-item label="轮播图">
        <div class="image-list-block">
          <div v-for="(img, idx) in form.imageList" :key="idx" class="image-url-row">
            <el-input v-model="form.imageList[idx]" placeholder="请输入轮播图地址" />
            <el-button type="danger" link @click="removeImage(idx)">删除</el-button>
          </div>
          <el-button type="primary" plain size="small" @click="form.imageList.push('')">添加轮播图</el-button>
        </div>
      </el-form-item>

      <el-form-item label="商品详情">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="请输入商品详情（支持 HTML）"
        />
      </el-form-item>

      <!-- 规格属性配置：存在上架 SKU 时只读（R6，防止已上架 SKU 的规格组合沦为孤儿） -->
      <el-divider content-position="left">规格属性配置</el-divider>
      <el-alert
        v-if="specLocked"
        class="center-alert"
        type="warning"
        show-icon
        :closable="false"
        title="存在已上架 SKU，规格属性不可修改"
        description="请先在「规格」中下架相关 SKU，再回来调整规格属性。"
      />
      <div class="spec-config-block">
        <div v-for="(dim, idx) in form.specConfig" :key="idx" class="spec-config-row">
          <el-input
            v-model="dim.spec"
            maxlength="32"
            placeholder="规格名，如 颜色"
            class="spec-name-input"
            :disabled="specLocked"
          />
          <el-select
            v-model="dim.values"
            multiple
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            :disabled="specLocked"
            placeholder="可选项（输入后回车添加），如 黑色"
            class="spec-values-select"
          />
          <el-button type="danger" link :disabled="specLocked" @click="removeSpecDim(idx)">删除</el-button>
        </div>
        <div class="spec-config-actions">
          <el-button type="primary" plain size="small" :disabled="specLocked" @click="addSpecDim">
            添加规格维度
          </el-button>
          <span class="spec-config-tip">可选项供「规格」生成 SKU 组合；商品可暂不维护 SKU</span>
        </div>
      </div>

      <!-- SKU：仅新增时在本表单维护（可整单预填）；编辑时的 SKU 增删改与上下架在「规格」中 -->
      <template v-if="type === 'add'">
        <el-divider content-position="left">SKU（价格必填，≥0.01）</el-divider>
        <div class="sku-toolbar">
          <span class="sku-tip">规格组合取自上方规格属性配置；价格按 SKU 填写，编码/图片可选</span>
          <el-button type="primary" plain size="small" :disabled="!form.specConfig.length" @click="generateMissing">
            按规格生成组合
          </el-button>
        </div>
        <el-table v-if="skuRows.length" :data="skuRows" border size="small">
          <el-table-column
            v-for="dim in validConfigs"
            :key="dim.spec"
            :label="dim.spec"
            min-width="110"
          >
            <template #default="{ row }">
              <span>{{ cellValue(row, dim.spec) || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="价格（元）" width="150">
            <template #default="{ row }">
              <el-input-number
                v-model="row.price"
                :min="0.01"
                :precision="2"
                :step="1"
                :controls="false"
                placeholder="必填"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="SKU 编码" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.skuCode" maxlength="64" placeholder="商家编码（可选）" />
            </template>
          </el-table-column>
          <el-table-column label="SKU 图片 URL" min-width="200">
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
              <el-button type="danger" link @click="skuRows.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无 SKU：配置规格属性后点「按规格生成组合」，也可先只建商品" />
      </template>
      <p v-else class="edit-sku-tip">SKU 的新增/修改/删除与上下架在列表页的「规格」中维护。</p>
    </el-form>

    <template #footer>
      <el-button @click="onUpdateVisible(false)">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { goodsMetaApi } from '../../api/goods'

const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** add | edit */
  type: { type: String, default: 'add' },
  /** 编辑时的商品详情（含 SKU 列表与中台比对结果 centerOutdated/centerMissing/centerSpu） */
  goods: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'save'])

const formRef = ref(null)
const brands = ref([])
const categoryOptions = ref([])
const skuCodeQuery = ref('')
const querying = ref(false)
const skuRows = ref([]) // 新增模式：[{ attrs:[{spec,value}], price, skuCode, mainImage }]

const form = ref({
  name: '',
  categoryId: null,
  brandId: null,
  mainImage: '',
  imageList: [],
  description: '',
  specConfig: [],
  shelfStatus: 0,
  goodsSpuId: null,
  centerVersion: null
})

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

/** 过滤后的规格配置（去空白，用于生成组合的维度） */
const validConfigs = computed(() => normalizeConfig(form.value.specConfig))

/** 存在上架 SKU 时规格配置只读（R6）；新增模式无上架 SKU */
const specLocked = computed(
  () => props.type === 'edit' && (props.goods?.skus || []).some((s) => s.shelfStatus === 1)
)

/** 中台有更新时的同步按钮文案（说明会覆盖哪些内容） */
const syncButtonText = computed(() => '同步中台（覆盖商品信息与规格）')

function onUpdateVisible(visible) {
  emit('update:modelValue', visible)
}

function removeImage(idx) {
  form.value.imageList.splice(idx, 1)
}

function addSpecDim() {
  form.value.specConfig.push({ spec: '', values: [] })
}

function removeSpecDim(idx) {
  form.value.specConfig.splice(idx, 1)
}

async function initForm() {
  await Promise.all([loadBrands(), loadCategories()])
  skuCodeQuery.value = ''
  skuRows.value = []

  if (props.type === 'edit' && props.goods) {
    const g = props.goods
    form.value = {
      name: g.name,
      categoryId: g.categoryId,
      brandId: g.brandId,
      mainImage: g.mainImage || '',
      imageList: [...(g.imageList || [])],
      description: g.description || '',
      specConfig: cloneConfig(g.specConfig),
      shelfStatus: g.shelfStatus,
      goodsSpuId: g.goodsSpuId,
      centerVersion: g.centerVersion
    }
  } else {
    form.value = {
      name: '',
      categoryId: null,
      brandId: null,
      mainImage: '',
      imageList: [],
      description: '',
      specConfig: [],
      shelfStatus: 0,
      goodsSpuId: null,
      centerVersion: null
    }
  }
  formRef.value?.clearValidate()
}

async function loadBrands() {
  brands.value = await goodsMetaApi.brands()
}

async function loadCategories() {
  const tree = await goodsMetaApi.categories()
  // 商品需挂在叶子分类：非叶子节点禁用选择
  const walk = (nodes) =>
    nodes.map((n) => ({
      id: n.id,
      name: n.name,
      children: walk(n.children || []),
      disabled: !!(n.children && n.children.length)
    }))
  categoryOptions.value = walk(tree)
}

/** 按 SKU 编码反查中台模板并整单预填（未命中仅提示，允许继续自建） */
async function handleQueryCenter() {
  const code = (skuCodeQuery.value || '').trim()
  if (!code) {
    ElMessage.warning('请先填写 SKU 编码')
    return
  }
  querying.value = true
  try {
    const res = await goodsMetaApi.spuBySkuCode(code)
    const spu = res ? res.spu : null
    if (!spu) {
      ElMessage.warning('中台未找到该 SKU 编码对应的标准商品，可继续自行填写')
      return
    }
    if (res.matchedSkuCount > 1) {
      ElMessage.warning(`该 SKU 编码在中台匹配到 ${res.matchedSkuCount} 条，已取第一条`)
    }
    applyCenterSpu(spu)
    ElMessage.success('已按中台模板预填，请补填各 SKU 价格后保存')
  } catch {
    // 拦截器已提示（中台不可用等）
  } finally {
    querying.value = false
  }
}

/**
 * 用中台模板覆盖表单（新增预填 / 编辑同步共用）：
 * 商品基础信息 + 规格属性配置 + SKU 组合；同步后记录中台版本戳，随保存提交。
 * SKU 价格一律保留店主已填值（新增预填时为空），编码/图片取中台值。
 */
function applyCenterSpu(spu) {
  form.value.name = spu.name || form.value.name
  form.value.categoryId = spu.categoryId
  form.value.brandId = spu.brandId
  form.value.mainImage = spu.mainImage || ''
  form.value.imageList = [...(spu.imageList || [])]
  form.value.description = spu.description || ''
  form.value.specConfig = cloneConfig(spu.specConfig)
  form.value.goodsSpuId = spu.id
  // 版本戳随之刷新：保存后与中台一致，不再提示「有更新」
  form.value.centerVersion = spu.version

  if (props.type === 'add') {
    // 新增：整单生成 SKU 行（价格留空，等店主填写）
    skuRows.value = (spu.skus || []).map((s) => ({
      attrs: (s.specAttrs || []).map((a) => ({ spec: a.spec, value: a.value })),
      price: null,
      skuCode: s.skuCode || '',
      mainImage: s.mainImage || ''
    }))
    // 中台未维护 SKU 时，按规格配置生成全部组合，避免预填后无 SKU 可填
    if (!skuRows.value.length) {
      generateMissing()
    }
  }
}

/** 编辑模式的「同步中台」：覆盖表单（SKU 在「规格」中按需同步，避免与锁定规则冲突） */
function applyCenter() {
  const spu = props.goods?.centerSpu
  if (!spu) return
  // 存在上架 SKU 时规格配置不可改（R6）：中台规格与本地不一致则先拦住，避免保存必然失败
  const nextConfig = cloneConfig(spu.specConfig)
  if (specLocked.value && !sameConfig(nextConfig, form.value.specConfig)) {
    ElMessage.warning('存在已上架 SKU，规格属性不可修改；请先在「规格」中下架全部 SKU 再同步')
    return
  }
  applyCenterSpu(spu)
  ElMessage.success('已同步中台商品信息，保存后生效')
}

/** 规格配置各维度的笛卡尔积 → 补齐缺失的 SKU 行 */
function generateMissing() {
  const dims = normalizeConfig(form.value.specConfig)
  if (!dims.length) {
    ElMessage.warning('请先配置规格属性（至少一个维度且包含可选项）')
    return
  }
  const combos = dims.reduce(
    (acc, dim) => acc.flatMap((attrs) => dim.values.map((v) => [...attrs, { spec: dim.spec, value: v }])),
    [[]]
  )
  const existing = new Set(skuRows.value.map((r) => rowKey(r.attrs)))
  let added = 0
  for (const attrs of combos) {
    const key = rowKey(attrs)
    if (!existing.has(key)) {
      skuRows.value.push({ attrs, price: null, skuCode: '', mainImage: '' })
      existing.add(key)
      added++
    }
  }
  ElMessage.success(added ? `已生成 ${added} 个 SKU 组合，请填写价格` : '组合已齐全，无新增')
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

function cloneConfig(config) {
  return (config || []).map((d) => ({ spec: d.spec, values: [...(d.values || [])] }))
}

/** 清理空白规格维度/值（提交前归一） */
function normalizeConfig(config) {
  return (config || [])
    .filter((d) => d.spec && d.spec.trim() && d.values && d.values.some((v) => v && v.trim()))
    .map((d) => ({
      spec: d.spec.trim(),
      values: [...new Set(d.values.map((v) => (v || '').trim()).filter((v) => v))]
    }))
}

/** 规格配置是否等价（忽略书写顺序与空白） */
function sameConfig(a, b) {
  const key = (config) =>
    normalizeConfig(config)
      .map((d) => `${d.spec}=${d.values.join(',')}`)
      .sort()
      .join('|')
  return key(a) === key(b)
}

/** 分类/品牌名称快照（下拉项取名；取不到则不提交该字段，保持库中原值） */
function categoryNameOf(id) {
  const stack = [...categoryOptions.value]
  while (stack.length) {
    const node = stack.pop()
    if (node.id === id) return node.name
    if (node.children) stack.push(...node.children)
  }
  return null
}

function brandNameOf(id) {
  const brand = brands.value.find((b) => b.id === id)
  return brand ? brand.name : null
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const imageList = form.value.imageList.filter((u) => u && u.trim())
  const specConfig = normalizeConfig(form.value.specConfig)

  if (props.type === 'add' && skuRows.value.length && !specConfig.length) {
    ElMessage.warning('已添加 SKU 但未配置规格属性，请先配置规格属性或清空 SKU')
    return
  }
  if (props.type === 'add' && skuRows.value.some((r) => !r.price || r.price < 0.01)) {
    ElMessage.warning('请为每个 SKU 填写价格（不低于 0.01）')
    return
  }

  const payload = {
    name: form.value.name,
    categoryId: form.value.categoryId,
    categoryName: categoryNameOf(form.value.categoryId),
    brandId: form.value.brandId,
    brandName: brandNameOf(form.value.brandId),
    mainImage: form.value.mainImage || null,
    imageList,
    description: form.value.description || null,
    specConfig
  }

  if (props.type === 'add') {
    payload.goodsSpuId = form.value.goodsSpuId
    payload.centerVersion = form.value.centerVersion
    payload.skus = skuRows.value.map((r) => ({
      specAttrs: r.attrs.map((a) => ({ spec: a.spec, value: a.value })),
      price: r.price,
      skuCode: r.skuCode || null,
      mainImage: r.mainImage || null
    }))
  } else if (form.value.centerVersion !== props.goods.centerVersion) {
    // 仅在店主点过「同步中台」后提交版本戳，刷新落库的 center_version
    payload.centerVersion = form.value.centerVersion
  }

  emit('save', payload)
}
</script>

<style scoped>
.center-query-form {
  margin-bottom: 4px;
}

.center-query-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.center-alert {
  margin-bottom: 12px;
}

.center-sync-action {
  margin-top: 8px;
}

.shelf-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.image-url-row {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  margin-bottom: 6px;
}

.image-list-block {
  width: 100%;
}

.main-image-preview {
  width: 48px;
  height: 32px;
  flex-shrink: 0;
}

.spec-config-block {
  width: 100%;
}

.spec-config-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.spec-name-input {
  width: 160px;
  flex-shrink: 0;
}

.spec-values-select {
  flex: 1;
}

.spec-config-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.spec-config-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

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

.edit-sku-tip {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
