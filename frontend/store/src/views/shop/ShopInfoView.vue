<template>
  <div v-loading="loading" class="shop-info">
    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>店铺信息</span>
          <el-button link type="primary" :loading="loading" @click="reload">刷新</el-button>
        </div>
      </template>

      <!-- 状态提示条：驱动当前可编辑/只读与操作按钮 -->
      <el-alert
        v-if="status === 1"
        type="warning"
        :closable="false"
        show-icon
        class="status-alert"
        title="店铺资料审核中"
        :description="`提交时间：${shop.submitTime || '-'}。审核期间不可修改，请耐心等待平台审核结果。`"
      />
      <el-alert
        v-else-if="status === 3"
        type="error"
        :closable="false"
        show-icon
        class="status-alert"
        title="店铺审核未通过"
        :description="`驳回原因：${shop.auditRemark || '-'}。请修改资料后重新提交审核。`"
      />
      <el-alert
        v-else-if="status === 2"
        type="success"
        :closable="false"
        show-icon
        class="status-alert"
        title="店铺已审核通过"
        description="已开放 商品管理 / 订单管理 / 库存管理 入口（侧边栏），店铺资料只读。"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-width="130px" class="shop-form">
        <el-divider content-position="left">基础信息</el-divider>
        <el-form-item label="店铺名称" prop="shopName">
          <el-input v-model="form.shopName" :disabled="readOnly" placeholder="请输入店铺名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="店铺 LOGO">
          <el-input v-model="form.logo" :disabled="readOnly" placeholder="图片 URL（本期手动填写）" />
        </el-form-item>
        <el-form-item label="店铺简介">
          <el-input
            v-model="form.intro"
            :disabled="readOnly"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="一句话介绍您的店铺"
          />
        </el-form-item>

        <el-divider content-position="left">联系人信息</el-divider>
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="form.contactName" :disabled="readOnly" placeholder="请输入联系人姓名" maxlength="50" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" :disabled="readOnly" placeholder="请输入联系电话" maxlength="20" />
        </el-form-item>
        <el-form-item label="所在地区" prop="region">
          <el-input v-model="form.region" :disabled="readOnly" placeholder="省市区，如：广东省 深圳市 南山区" maxlength="100" />
        </el-form-item>
        <el-form-item label="详细地址" prop="address">
          <el-input v-model="form.address" :disabled="readOnly" placeholder="请输入详细街道地址" maxlength="255" />
        </el-form-item>

        <el-divider content-position="left">营业执照</el-divider>
        <el-form-item label="企业名称" prop="licenseName">
          <el-input v-model="form.licenseName" :disabled="readOnly" placeholder="营业执照上的企业/个体工商户名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="统一社会信用代码" prop="licenseNo">
          <el-input v-model="form.licenseNo" :disabled="readOnly" placeholder="请输入统一社会信用代码" maxlength="50" />
        </el-form-item>
        <el-form-item label="营业执照照片" prop="licenseImg">
          <el-input v-model="form.licenseImg" :disabled="readOnly" placeholder="营业执照照片 URL（本期手动填写）" />
        </el-form-item>

        <el-form-item v-if="status === 1 || status === 3" label="审核信息">
          <div class="audit-info">
            <div>提交时间：{{ shop.submitTime || '-' }}</div>
            <div v-if="status === 3">
              审核时间：{{ shop.auditTime || '-' }}　驳回原因：{{ shop.auditRemark || '-' }}
            </div>
          </div>
        </el-form-item>

        <el-form-item v-if="!readOnly" class="form-actions">
          <el-button @click="handleSave" :loading="saving">保存草稿</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交审核</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { shopApi } from '../../api/shop'
import { shop, shopStatus, fetchMyShop } from '../../store/shop'

const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive(emptyForm())

const status = computed(() => shopStatus.value)
// 待审核(1)/已通过(2)：表单只读；草稿(0)/已驳回(3)（含未创建视为草稿）可编辑
const readOnly = computed(() => status.value === 1 || status.value === 2)

const rules = {
  shopName: [{ required: true, message: '请输入店铺名称', trigger: 'blur' }]
}

/** 表单字段初始值（与后端 ShopSaveDTO 字段对应） */
function emptyForm() {
  return {
    shopName: '',
    logo: '',
    intro: '',
    contactName: '',
    contactPhone: '',
    region: '',
    address: '',
    licenseName: '',
    licenseNo: '',
    licenseImg: ''
  }
}

/** 用「我的店铺」当前值回填表单（只读态/刷新后展示已存内容） */
function syncFromShop() {
  const s = shop.value
  Object.keys(form).forEach((key) => {
    form[key] = (s && s[key]) != null ? s[key] : ''
  })
}

/** 提交审核前的前端完整性校验（与后端 assertSubmitComplete 一致） */
function checkSubmitComplete() {
  const required = [
    ['shopName', '店铺名称'],
    ['contactName', '联系人'],
    ['contactPhone', '联系电话'],
    ['region', '所在地区'],
    ['address', '详细地址'],
    ['licenseName', '企业名称'],
    ['licenseNo', '统一社会信用代码'],
    ['licenseImg', '营业执照照片']
  ]
  const missing = required.filter(([key]) => !String(form[key] || '').trim()).map(([, label]) => label)
  if (missing.length) {
    ElMessage.warning(`提交前请补全：${missing.join('、')}`)
    return false
  }
  return true
}

async function reload() {
  loading.value = true
  try {
    await fetchMyShop()
    syncFromShop()
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await shopApi.save({ ...form })
    ElMessage.success('草稿已保存')
    await fetchMyShop()
    syncFromShop()
  } catch {
    // 状态机守卫（审核中/已通过）报错时拦截器已提示
  } finally {
    saving.value = false
  }
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!checkSubmitComplete()) return
  submitting.value = true
  try {
    await shopApi.submit({ ...form })
    ElMessage.success('提交成功，等待平台审核')
    await fetchMyShop()
    syncFromShop()
  } catch {
    // 状态机/资质校验失败时拦截器已提示
  } finally {
    submitting.value = false
  }
}

onMounted(reload)
</script>

<style scoped>
.shop-info {
  max-width: 860px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.status-alert {
  margin-bottom: 16px;
}

.shop-form {
  margin-top: 4px;
}

.audit-info {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.8;
}

.form-actions {
  margin-top: 8px;
}
</style>
