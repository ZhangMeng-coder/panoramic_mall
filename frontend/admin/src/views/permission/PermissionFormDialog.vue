<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="onUpdateVisible"
    @open="initForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="上级">
        <el-input :model-value="parentLabel" disabled />
      </el-form-item>
      <el-form-item label="类型">
        <el-tag :type="typeTagType" effect="plain">{{ typeLabel }}</el-tag>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" maxlength="50" placeholder="请输入名称" />
      </el-form-item>
      <el-form-item label="权限字符串" prop="perms">
        <el-input
          v-model="form.perms"
          maxlength="128"
          :placeholder="type === 1 ? '目录可不填' : '如 system:user:list'"
        />
      </el-form-item>
      <el-form-item v-if="type === 2" label="路由地址" prop="route">
        <el-input v-model="form.route" maxlength="200" placeholder="页面路由地址，如 /user" />
      </el-form-item>
      <el-form-item label="图标" prop="icon">
        <el-input v-model="form.icon" maxlength="128" placeholder="菜单图标标识，如 setting/user" />
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

<script setup>
import { ref, computed } from 'vue'

const TYPE_LABELS = { 1: '目录', 2: '页面', 3: '按钮' }
const TYPE_TAGS = { 1: 'warning', 2: 'success', 3: 'info' }

const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** add | edit */
  type: { type: String, default: 'add' },
  /** 新增时的上级节点（null 表示顶级目录） */
  parent: { type: Object, default: null },
  /** 编辑时的权限节点 */
  permission: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'save'])

const formRef = ref(null)
const form = ref({ parentId: 0, name: '', type: 1, perms: '', route: '', icon: '', sort: 0 })

// 页面级权限必须填写路由地址（前端菜单据此导航），目录/按钮不要求
const rules = computed(() => ({
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  ...(type.value === 2
    ? { route: [{ required: true, message: '页面需要填写路由地址', trigger: 'blur' }] }
    : {})
}))

const title = computed(() => (props.type === 'edit' ? '编辑权限' : props.parent ? '新增子权限' : '新增顶级目录'))

// 新增时类型 = 父类型 + 1（目录→页面→按钮）；编辑时为原有类型
const type = computed(() => {
  if (props.type === 'edit') return props.permission ? props.permission.type : 1
  return props.parent ? props.parent.type + 1 : 1
})

const typeLabel = computed(() => TYPE_LABELS[type.value] || '-')
const typeTagType = computed(() => TYPE_TAGS[type.value] || 'info')

const parentLabel = computed(() => {
  if (props.type === 'edit') return props.parent ? '' : '不可修改（由创建时决定）'
  return props.parent ? `${props.parent.name}（${TYPE_LABELS[props.parent.type]}）` : '顶级（无上级）'
})

function onUpdateVisible(visible) {
  emit('update:modelValue', visible)
}

function initForm() {
  if (props.type === 'edit' && props.permission) {
    form.value = {
      name: props.permission.name,
      perms: props.permission.perms || '',
      route: props.permission.route || '',
      icon: props.permission.icon || '',
      sort: props.permission.sort ?? 0
    }
  } else {
    form.value = {
      parentId: props.parent ? props.parent.id : 0,
      name: '',
      type: type.value,
      perms: '',
      route: '',
      icon: '',
      sort: 0
    }
  }
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (props.type === 'edit') {
    // 编辑仅允许更新名称/权限字符串/图标/路由地址/排序，父级与类型不可变更
    const { name, perms, route, icon, sort } = form.value
    const routeVal = (route || '').trim()
    emit('save', {
      name,
      perms: perms || null,
      route: routeVal || null,
      icon: icon || null,
      sort
    })
  } else {
    const { perms, route } = form.value
    const routeVal = (route || '').trim()
    emit('save', { ...form.value, perms: perms || null, route: routeVal || null })
  }
}
</script>
