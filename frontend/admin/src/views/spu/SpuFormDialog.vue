<template>
  <el-dialog
    :model-value="modelValue"
    :title="type === 'edit' ? '编辑商品' : '新增商品'"
    width="860px"
    top="4vh"
    :close-on-click-modal="false"
    @update:model-value="onUpdateVisible"
    @open="initForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <!-- 基础信息 -->
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="商品名称" prop="name">
            <el-input v-model="form.name" maxlength="128" placeholder="请输入商品名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio :value="1">展示</el-radio>
              <el-radio :value="0">隐藏</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
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
        <el-col :span="12">
          <el-form-item label="品牌" prop="brandId">
            <el-select v-model="form.brandId" filterable placeholder="请选择品牌" style="width: 100%">
              <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="主图 URL" prop="mainImage">
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
          <!-- 轮播图逐行编辑：写回 form.imageList[idx]，故 v-for 的值位不使用（_img 占位） -->
          <div v-for="(_img, idx) in form.imageList" :key="idx" class="image-url-row">
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

      <!-- 规格属性配置：定义该商品规格维度及各自可选项（SKU 组合在「规格」管理中按此生成） -->
      <el-divider content-position="left">规格属性配置</el-divider>
      <div class="spec-config-block">
        <div v-for="(dim, idx) in form.specConfig" :key="idx" class="spec-config-row">
          <el-input v-model="dim.spec" maxlength="32" placeholder="规格名，如 颜色" class="spec-name-input" />
          <el-select
            v-model="dim.values"
            multiple
            filterable
            allow-create
            default-first-option
            :reserve-keyword="false"
            placeholder="可选项（输入后回车添加），如 黑色"
            class="spec-values-select"
          />
          <el-button type="danger" link @click="removeSpecDim(idx)">删除</el-button>
        </div>
        <div class="spec-config-actions">
          <el-button type="primary" plain size="small" @click="addSpecDim">添加规格维度</el-button>
          <span class="spec-config-tip">可选项供「规格」生成 SKU 组合；商品可暂不维护 SKU</span>
        </div>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="onUpdateVisible(false)">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { FormInstance } from 'element-plus'
import { brandApi } from '../../api/brand'
import { categoryApi } from '../../api/category'
import type { SpuDetail } from '../../api/spu'
import type { BrandItem, CategoryNode, SpecConfigItem } from '../../types/goods'

const props = withDefaults(
  defineProps<{
    /** 弹窗显隐（v-model） */
    modelValue?: boolean
    /** add | edit */
    type?: string
    /** 编辑时的商品数据（SpuDetailVO，含规格属性配置 specConfig） */
    spu?: SpuDetail | null
  }>(),
  { modelValue: false, type: 'add', spu: null }
)

// save 的载荷里「清空的可空字段」显式传 null（与 SpuPayload 可选字段的口径不同），故沿用运行期声明
const emit = defineEmits(['update:modelValue', 'save'])

/** 分类下拉项：树选择只用到 id / name / children / disabled，故不复用 CategoryNode 的全字段 */
interface CategoryOption {
  id: number
  name: string
  children: CategoryOption[]
  /** 非叶子分类不可选（商品须挂叶子分类） */
  disabled: boolean
}

/** 表单模型：categoryId / brandId 未选时为 null（树选择、下拉清空后即 null） */
interface SpuForm {
  name: string
  categoryId: number | null
  brandId: number | null
  mainImage: string
  imageList: string[]
  description: string
  status: number
  specConfig: SpecConfigItem[]
}

const formRef = ref<FormInstance | null>(null)
const brands = ref<BrandItem[]>([])
const categoryOptions = ref<CategoryOption[]>([])

const form = ref<SpuForm>({
  name: '',
  categoryId: null,
  brandId: null,
  mainImage: '',
  imageList: [],
  description: '',
  status: 0,
  specConfig: []
})

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  brandId: [{ required: true, message: '请选择品牌', trigger: 'change' }]
}

function onUpdateVisible(visible: boolean) {
  emit('update:modelValue', visible)
}

function removeImage(idx: number) {
  form.value.imageList.splice(idx, 1)
}

function addSpecDim() {
  form.value.specConfig.push({ spec: '', values: [] })
}

function removeSpecDim(idx: number) {
  form.value.specConfig.splice(idx, 1)
}

async function initForm() {
  await Promise.all([loadBrands(), loadCategories()])

  const spu = props.type === 'edit' ? props.spu : null
  if (spu) {
    form.value = {
      name: spu.name,
      categoryId: spu.categoryId,
      brandId: spu.brandId,
      mainImage: spu.mainImage || '',
      imageList: [...(spu.imageList || [])],
      description: spu.description || '',
      status: spu.status,
      specConfig: (spu.specConfig || []).map((d) => ({ spec: d.spec, values: [...(d.values || [])] }))
    }
  } else {
    form.value = {
      name: '',
      categoryId: null,
      brandId: null,
      mainImage: '',
      imageList: [],
      description: '',
      status: 0,
      specConfig: []
    }
  }
  formRef.value?.clearValidate()
}

async function loadBrands() {
  brands.value = await brandApi.list()
}

async function loadCategories() {
  const tree = await categoryApi.tree()
  // 商品需挂在叶子分类：非叶子节点禁用选择
  const walk = (nodes: CategoryNode[]): CategoryOption[] =>
    nodes.map((n) => ({
      id: n.id,
      name: n.name,
      children: walk(n.children || []),
      disabled: !!(n.children && n.children.length)
    }))
  categoryOptions.value = walk(tree)
}

async function handleSubmit() {
  // 弹窗打开时表单必已挂载；取不到表单实例时等同校验未通过，直接返回
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  // 清理空白轮播图与空白规格维度；SKU 不在本弹窗维护（见「规格」管理）
  form.value.imageList = form.value.imageList.filter((u) => u && u.trim())
  const specConfig = form.value.specConfig
    .filter((d) => d.spec && d.spec.trim() && d.values && d.values.some((v) => v && v.trim()))
    .map((d) => ({
      spec: d.spec.trim(),
      values: [...new Set(d.values.map((v) => (v || '').trim()).filter((v) => v))]
    }))
  const payload = {
    name: form.value.name,
    categoryId: form.value.categoryId,
    brandId: form.value.brandId,
    mainImage: form.value.mainImage || null,
    imageList: form.value.imageList,
    description: form.value.description || null,
    status: form.value.status,
    specConfig
  }
  emit('save', payload)
}

onMounted(() => {
  if (!brands.value.length) loadBrands()
})
</script>

<style scoped>
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
</style>
