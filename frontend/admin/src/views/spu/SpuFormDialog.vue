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
              <el-radio :value="1">上架</el-radio>
              <el-radio :value="0">下架</el-radio>
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

      <!-- SKU 编辑区 -->
      <el-divider content-position="left">SKU 规格</el-divider>
      <SkuEditor ref="skuEditorRef" :skus="form.skus" />
    </el-form>

    <template #footer>
      <el-button @click="onUpdateVisible(false)">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { brandApi } from '../../api/brand'
import { categoryApi } from '../../api/category'
import SkuEditor from './SkuEditor.vue'

const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** add | edit */
  type: { type: String, default: 'add' },
  /** 编辑时的商品数据（SpuDetailVO） */
  spu: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'save'])

const formRef = ref(null)
const skuEditorRef = ref(null)
const brands = ref([])
const categoryOptions = ref([])

const form = ref({
  name: '',
  categoryId: null,
  brandId: null,
  mainImage: '',
  imageList: [],
  description: '',
  status: 0,
  skus: []
})

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  brandId: [{ required: true, message: '请选择品牌', trigger: 'change' }]
}

function onUpdateVisible(visible) {
  emit('update:modelValue', visible)
}

function removeImage(idx) {
  form.value.imageList.splice(idx, 1)
}

async function initForm() {
  await Promise.all([loadBrands(), loadCategories()])

  const isEdit = props.type === 'edit' && props.spu
  if (isEdit) {
    const spu = props.spu
    form.value = {
      name: spu.name,
      categoryId: spu.categoryId,
      brandId: spu.brandId,
      mainImage: spu.mainImage || '',
      imageList: [...(spu.imageList || [])],
      description: spu.description || '',
      status: spu.status,
      skus: []
    }
    form.value.skus = spu.skus || []
    skuEditorRef.value?.reset(spu.skus || [])
  } else {
    form.value = {
      name: '',
      categoryId: null,
      brandId: null,
      mainImage: '',
      imageList: [],
      description: '',
      status: 0,
      skus: []
    }
    skuEditorRef.value?.reset(null)
  }
  formRef.value?.clearValidate()
}

async function loadBrands() {
  brands.value = await brandApi.list()
}

async function loadCategories() {
  const tree = await categoryApi.tree()
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

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  // 清理空白轮播图与空白规格值
  form.value.imageList = form.value.imageList.filter((u) => u && u.trim())
  const payload = {
    name: form.value.name,
    categoryId: form.value.categoryId,
    brandId: form.value.brandId,
    mainImage: form.value.mainImage || null,
    imageList: form.value.imageList,
    description: form.value.description || null,
    status: form.value.status,
    skus: form.value.skus
      .filter((s) => s.specAttrs && s.specAttrs.length)
      .map((s) => ({
        id: s.id,
        specAttrs: s.specAttrs,
        skuCode: s.skuCode || null,
        mainImage: s.mainImage || null
      }))
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
</style>
