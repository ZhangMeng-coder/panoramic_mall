<template>
  <el-card v-loading="loading" shadow="never">
    <template #header>
      <div class="detail-header">
        <div class="detail-header-left">
          <el-button link type="primary" @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <span class="detail-title">{{ goods?.name || '店铺商品详情' }}</span>
          <el-tag v-if="goods" :type="goods.shelfStatus === 1 ? 'success' : 'info'">
            {{ goods.shelfStatus === 1 ? '上架中' : '已下架' }}
          </el-tag>
          <el-tag v-if="goods && goods.lockStatus === 1" type="danger">已锁定</el-tag>
        </div>
        <div v-if="goods" class="detail-header-right">
          <el-button v-if="goods.lockStatus !== 1" v-perm="'store:goods:lock'" type="danger" @click="lockVisible = true">
            锁定
          </el-button>
          <el-button v-else v-perm="'store:goods:lock'" type="primary" @click="handleUnlock">解锁</el-button>
        </div>
      </div>
    </template>

    <template v-if="goods">
      <!-- 基础信息 -->
      <div class="section-title">基础信息</div>
      <el-descriptions :column="3" border class="section-body">
        <el-descriptions-item label="商品ID">{{ goods.id }}</el-descriptions-item>
        <el-descriptions-item label="所属店铺">{{ goods.storeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="品牌">{{ goods.brandName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分类" :span="2">
          {{ goods.categoryPath || goods.categoryName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="上下架">
          {{ goods.shelfStatus === 1 ? '上架中' : '已下架' }}
        </el-descriptions-item>
        <el-descriptions-item label="锁定状态">
          <el-tag v-if="goods.lockStatus === 1" type="danger" size="small">已锁定</el-tag>
          <el-tag v-else type="success" size="small" effect="plain">正常</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="中台关联">
          <span v-if="goods.goodsSpuId">
            中台模板 ID {{ goods.goodsSpuId }}{{ goods.centerVersion ? `（版本 ${goods.centerVersion}）` : '' }}
          </span>
          <span v-else>未关联中台</span>
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ goods.updateTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 主图 -->
      <div class="section-title">主图</div>
      <div class="section-body img-wrap">
        <el-image
          v-if="goods.mainImage"
          :src="goods.mainImage"
          :preview-src-list="[goods.mainImage]"
          preview-teleported
          fit="contain"
          class="main-img"
        />
        <span v-else class="muted">未设置主图</span>
      </div>

      <!-- 轮播图 -->
      <div class="section-title">轮播图</div>
      <div class="section-body">
        <div v-if="goods.imageList && goods.imageList.length" class="carousel">
          <el-image
            v-for="(u, i) in goods.imageList"
            :key="i"
            :src="u"
            :preview-src-list="goods.imageList"
            :initial-index="i"
            preview-teleported
            fit="cover"
            class="thumb"
          />
        </div>
        <span v-else class="muted">未设置轮播图</span>
      </div>

      <!-- 商品详情（富文本，与既有商品预览弹窗口径一致：v-html 直接渲染） -->
      <div class="section-title">商品详情</div>
      <div class="section-body">
        <div v-if="goods.description" class="rich-text" v-html="goods.description"></div>
        <span v-else class="muted">未填写商品详情</span>
      </div>

      <!-- 规格属性配置 -->
      <div class="section-title">规格属性配置</div>
      <div class="section-body">
        <div v-if="goods.specConfig && goods.specConfig.length">
          <div v-for="d in goods.specConfig" :key="d.spec" class="config-item">
            <span class="config-spec">{{ d.spec }}</span>
            <div class="config-values">
              <el-tag v-for="v in d.values || []" :key="v" size="small" effect="plain">{{ v }}</el-tag>
            </div>
          </div>
        </div>
        <span v-else class="muted">未配置规格属性</span>
      </div>

      <!-- SKU 列表 -->
      <div class="section-title">SKU 列表</div>
      <div class="section-body">
        <el-table v-if="goods.skus && goods.skus.length" :data="goods.skus" border size="small">
          <el-table-column prop="id" label="SKU ID" width="90" />
          <el-table-column label="规格组合" min-width="240">
            <template #default="{ row }">
              <div class="sku-attrs">
                <el-tag v-for="(a, i) in row.specAttrs || []" :key="i" size="small" type="info">
                  {{ a.spec }}：{{ a.value }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="skuCode" label="商家编码" min-width="140">
            <template #default="{ row }">{{ row.skuCode || '—' }}</template>
          </el-table-column>
          <el-table-column label="SKU 图片" width="100">
            <template #default="{ row }">
              <el-image
                v-if="row.mainImage"
                :src="row.mainImage"
                :preview-src-list="[row.mainImage]"
                preview-teleported
                fit="cover"
                class="sku-img"
              />
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="价格" width="110" align="right">
            <template #default="{ row }">{{ row.price != null ? `¥${row.price}` : '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.shelfStatus === 1 ? 'success' : 'info'" size="small">
                {{ row.shelfStatus === 1 ? '上架中' : '已下架' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <span v-else class="muted">暂无 SKU</span>
      </div>

      <!-- 锁定信息 -->
      <div class="section-title">锁定信息</div>
      <el-descriptions :column="1" border class="section-body">
        <el-descriptions-item label="锁定状态">
          <el-tag v-if="goods.lockStatus === 1" type="danger" size="small">已锁定</el-tag>
          <el-tag v-else type="success" size="small" effect="plain">正常</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="goods.lockStatus === 1" label="锁定原因">
          {{ goods.lockReason || '-' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="goods.lockStatus === 1" label="锁定人">
          {{ lockUserText(goods.lockUser) }}
        </el-descriptions-item>
        <el-descriptions-item v-if="goods.lockStatus === 1" label="锁定时间">
          {{ goods.lockTime || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </template>

    <!-- 锁定弹窗：原因必填 -->
    <el-dialog v-model="lockVisible" title="锁定商品" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="商品">
          <span>{{ goods?.name }}</span>
        </el-form-item>
        <el-form-item label="锁定原因">
          <el-input
            v-model="lockReason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="请填写锁定原因（必填，店主可见）"
          />
        </el-form-item>
      </el-form>
      <div class="lock-tip">锁定会将该商品全部 SKU 下架，锁定期店主不可编辑 / 上下架 / 删除。</div>
      <template #footer>
        <el-button @click="lockVisible = false">取消</el-button>
        <el-button v-perm="'store:goods:lock'" type="danger" :loading="locking" @click="submitLock">确定锁定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { shopGoodsApi, type ShopGoodsDetail } from '../../api/shopGoods'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
// 详情未加载完成前为 null（模板里用 v-if="goods" 收窄）
const goods = ref<ShopGoodsDetail | null>(null)

const lockVisible = ref(false)
const lockReason = ref('')
const locking = ref(false)

async function loadDetail() {
  loading.value = true
  try {
    // 路由参数恒为字符串，接口入参是 number，显式转一次（拼进 URL 后形状不变）
    goods.value = await shopGoodsApi.detail(Number(route.params.id))
  } catch {
    // 拦截器已提示（如商品不存在）
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/shop-goods')
}

/** 锁定人展示：库里存 `UserType:UserId`（如 admin:1），此处只渲染为「平台管理员(1)」不做用户表联查 */
function lockUserText(lockUser: string | null): string {
  if (!lockUser) return '-'
  const [type, id] = String(lockUser).split(':')
  const label = type === 'admin' ? '平台管理员' : type
  return id ? `${label}(${id})` : label
}

async function submitLock() {
  const target = goods.value
  // 详情未加载时锁定按钮不可达，此处只为收窄类型
  if (!target) return
  if (!lockReason.value.trim()) {
    ElMessage.warning('请填写锁定原因')
    return
  }
  locking.value = true
  try {
    await shopGoodsApi.lock(target.id, { reason: lockReason.value.trim() })
    ElMessage.success('商品已锁定，其全部 SKU 已下架')
    lockVisible.value = false
    lockReason.value = ''
    loadDetail()
  } catch {
    // 后端业务校验失败（如已锁定）时拦截器已提示
  } finally {
    locking.value = false
  }
}

async function handleUnlock() {
  const target = goods.value
  // 详情未加载时解锁按钮不可达，此处只为收窄类型
  if (!target) return
  try {
    await ElMessageBox.confirm(
      `确定解锁商品「${target.name}」吗？解锁后商品保持下架，需店主手动重新上架`,
      '解锁确认',
      { type: 'warning', confirmButtonText: '解锁', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await shopGoodsApi.unlock(target.id)
    ElMessage.success('已解锁，商品保持下架')
    loadDetail()
  } catch {
    // 拦截器已提示
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 18px 0 8px;
}

.section-title:first-of-type {
  margin-top: 0;
}

.img-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  padding: 8px;
  background-color: var(--el-fill-color-lighter);
  border-radius: 4px;
}

.main-img {
  width: 260px;
  height: 200px;
}

.carousel {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.thumb {
  width: 64px;
  height: 48px;
  border-radius: 4px;
}

.rich-text {
  max-height: 320px;
  overflow-y: auto;
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  line-height: 1.6;
  font-size: 13px;
}

.config-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
}

.config-spec {
  flex-shrink: 0;
  line-height: 24px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.config-values {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.sku-attrs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.sku-img {
  width: 48px;
  height: 36px;
  border-radius: 3px;
}

.muted {
  font-size: 13px;
  color: var(--el-text-color-disabled);
}

.lock-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
</style>
