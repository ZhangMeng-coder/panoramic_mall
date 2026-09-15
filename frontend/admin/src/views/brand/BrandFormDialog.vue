<template>
  <el-dialog
    :model-value="modelValue"
    :title="type === 'edit' ? '编辑品牌' : '新增品牌'"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="onUpdateVisible"
    @open="initForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="品牌名称" prop="name">
        <el-input v-model="form.name" maxlength="64" placeholder="请输入品牌名称" />
      </el-form-item>
      <el-form-item label="LOGO URL" prop="logo">
        <el-input v-model="form.logo" placeholder="请输入品牌 LOGO 图片地址" />
      </el-form-item>
      <el-form-item label="简介" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="请输入品牌简介"
        />
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number v-model="form.sort" :min="0" :max="99999" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onUpdateVisible(false)">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { PropType } from 'vue'
import type { FormInstance } from 'element-plus'
import type { BrandItem } from '../../types/goods'

const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** add | edit */
  type: { type: String, default: 'add' },
  /** 编辑时的品牌数据 */
  brand: { type: Object as PropType<BrandItem | null>, default: null }
})

const emit = defineEmits(['update:modelValue', 'save'])

const formRef = ref<FormInstance | null>(null)
const form = ref({ name: '', logo: '', description: '', sort: 0 })

const rules = {
  name: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }]
}

function onUpdateVisible(visible: boolean) {
  emit('update:modelValue', visible)
}

function initForm() {
  if (props.type === 'edit' && props.brand) {
    form.value = {
      name: props.brand.name,
      logo: props.brand.logo || '',
      description: props.brand.description || '',
      sort: props.brand.sort ?? 0
    }
  } else {
    form.value = { name: '', logo: '', description: '', sort: 0 }
  }
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  // 表单实例由模板 ref 挂载时赋值；空值原本走 catch 分支返回，效果一致，此处只为收窄类型
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  // 实际保存由父组件响应 save 事件完成，成功后父组件关闭弹窗
  emit('save', { ...form.value })
}
</script>
