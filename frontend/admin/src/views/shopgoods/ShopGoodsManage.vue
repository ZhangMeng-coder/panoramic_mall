<template>
  <el-card shadow="never">
    <!-- 筛选区：分类（子树匹配）/ 品牌 / 店铺 / 上下架 / 锁定状态 / 名称关键字 -->
    <el-form inline class="filter-form">
      <el-form-item label="分类">
        <el-tree-select
          v-model="query.categoryId"
          :data="categoryOptions"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          check-strictly
          clearable
          default-expand-all
          placeholder="全部分类"
          style="width: 180px"
        />
      </el-form-item>
      <el-form-item label="品牌">
        <el-select
          v-model="query.brandIds"
          multiple
          collapse-tags
          clearable
          filterable
          placeholder="全部品牌"
          style="width: 200px"
        >
          <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="店铺">
        <el-select v-model="query.storeId" clearable filterable placeholder="全部店铺" style="width: 170px">
          <el-option v-for="s in shops" :key="s.id" :label="s.shopName" :value="s.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.shelfStatus" clearable placeholder="全部状态" style="width: 120px">
          <el-option label="上架中" :value="1" />
          <el-option label="已下架" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="锁定">
        <el-select v-model="query.lockStatus" clearable placeholder="全部" style="width: 110px">
          <el-option label="已锁定" :value="1" />
          <el-option label="未锁定" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称">
        <el-input
          v-model="query.keyword"
          placeholder="商品名称关键字"
          clearable
          style="width: 170px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-button v-perm="'store:goods:list'" type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 商品列表（全店铺） -->
    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="mainImage" label="主图" width="90">
        <template #default="{ row }">
          <el-image
            v-if="row.mainImage"
            :src="row.mainImage"
            :preview-src-list="[row.mainImage]"
            preview-teleported
            fit="contain"
            style="width: 56px; height: 40px"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row as ShopGoodsPageItem)">{{ row.name }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="storeName" label="所属店铺" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.storeName || '-' }}</template>
      </el-table-column>
      <!-- 分类显示全路径（BFF 读时解析）；解析失败时回退落库快照的分类名 -->
      <el-table-column label="分类" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.categoryPath || row.categoryName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="brandName" label="品牌" min-width="110">
        <template #default="{ row }">{{ row.brandName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="skuCount" label="SKU" width="70" align="center" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.shelfStatus === 1 ? 'success' : 'info'">
            {{ row.shelfStatus === 1 ? '上架中' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="锁定状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.lockStatus === 1" type="danger">已锁定</el-tag>
          <el-tag v-else type="success" effect="plain">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.lockStatus !== 1" v-perm="'store:goods:lock'" link type="danger" @click="openLock(row as ShopGoodsPageItem)">
            锁定
          </el-button>
          <template v-else>
            <el-button link type="warning" @click="openLockInfo(row as ShopGoodsPageItem)">锁定信息</el-button>
            <el-button v-perm="'store:goods:lock'" link type="primary" @click="handleUnlock(row as ShopGoodsPageItem)">解锁</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      class="pager"
      layout="total, prev, pager, next, sizes"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="loadPage"
      @size-change="loadPage"
    />

    <!-- 锁定弹窗：原因必填 -->
    <el-dialog v-model="lockVisible" title="锁定商品" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="商品">
          <span>{{ lockTarget?.name }}</span>
        </el-form-item>
        <el-form-item label="所属店铺">
          <span>{{ lockTarget?.storeName || '-' }}</span>
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

    <!-- 锁定信息（只读） -->
    <el-dialog v-model="lockInfoVisible" title="锁定信息" width="460px" destroy-on-close>
      <el-descriptions v-if="lockTarget" :column="1" border>
        <el-descriptions-item label="商品">{{ lockTarget.name }}</el-descriptions-item>
        <el-descriptions-item label="所属店铺">{{ lockTarget.storeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="锁定原因">{{ lockTarget.lockReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="锁定人">{{ lockUserText(lockTarget.lockUser) }}</el-descriptions-item>
        <el-descriptions-item label="锁定时间">{{ lockTarget.lockTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="lockInfoVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { shopGoodsApi, type ShopGoodsPageItem, type ShopGoodsPageQuery, type ShopOption } from '../../api/shopGoods'
import type { BrandItem, CategoryNode } from '../../types/goods'

/** 分类下拉树的节点：只保留树选择器需要的字段（parentId / level / sort 此处用不到） */
interface CategoryOption {
  id: number
  name: string
  children: CategoryOption[]
}

const router = useRouter()

const loading = ref(false)
const records = ref<ShopGoodsPageItem[]>([])
const total = ref(0)
const brands = ref<BrandItem[]>([])
const shops = ref<ShopOption[]>([])
const categoryOptions = ref<CategoryOption[]>([])

// 筛选项未选时为 undefined（axios 对 undefined / null 一视同仁地跳过，请求参数不变）
const query = reactive<ShopGoodsPageQuery>({
  pageNum: 1,
  pageSize: 10,
  categoryId: undefined,
  brandIds: undefined,
  storeId: undefined,
  shelfStatus: undefined,
  lockStatus: undefined,
  keyword: ''
})

// 锁定弹窗 / 锁定信息弹窗（共用当前行）
const lockVisible = ref(false)
const lockInfoVisible = ref(false)
const lockTarget = ref<ShopGoodsPageItem | null>(null)
const lockReason = ref('')
const locking = ref(false)

async function loadOptions() {
  const [brandList, tree, shopList] = await Promise.all([
    shopGoodsApi.brands(),
    shopGoodsApi.categories(),
    shopGoodsApi.shops()
  ])
  brands.value = brandList
  shops.value = shopList
  const walk = (nodes: CategoryNode[]): CategoryOption[] =>
    nodes.map((n) => ({
      id: n.id,
      name: n.name,
      children: walk(n.children || [])
    }))
  categoryOptions.value = walk(tree)
}

async function loadPage() {
  loading.value = true
  try {
    const data = await shopGoodsApi.page({ ...query })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadPage()
}

function handleReset() {
  query.categoryId = undefined
  query.brandIds = undefined
  query.storeId = undefined
  query.shelfStatus = undefined
  query.lockStatus = undefined
  query.keyword = ''
  handleSearch()
}

// ⚠ el-table 插槽把 row 声明成 EP 的 DefaultRow（表格组件非泛型，拿不到 :data 的行类型），
//   故下列各表格行处理器在【模板调用处】断言一次（`row as ShopGoodsPageItem`），函数本身保持强类型 ——
//   `DefaultRow` 未从 element-plus 根导出，模板插槽注解也会因逆变报错，断言是唯一可行解。
function openDetail(row: ShopGoodsPageItem) {
  router.push(`/shop-goods/${row.id}`)
}

/** 锁定人展示：库里存 `UserType:UserId`（如 admin:1），此处只渲染为「平台管理员(1)」不做用户表联查 */
function lockUserText(lockUser: string | null): string {
  if (!lockUser) return '-'
  const [type, id] = String(lockUser).split(':')
  const label = type === 'admin' ? '平台管理员' : type
  return id ? `${label}(${id})` : label
}

function openLock(row: ShopGoodsPageItem) {
  lockTarget.value = row
  lockReason.value = ''
  lockVisible.value = true
}

function openLockInfo(row: ShopGoodsPageItem) {
  lockTarget.value = row
  lockInfoVisible.value = true
}

async function submitLock() {
  const target = lockTarget.value
  // 锁定弹窗打开时必有目标行，此处只为收窄类型
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
    loadPage()
  } catch {
    // 后端业务校验失败（如已锁定）时拦截器已提示
  } finally {
    locking.value = false
  }
}

async function handleUnlock(row: ShopGoodsPageItem) {
  try {
    await ElMessageBox.confirm(
      `确定解锁商品「${row.name}」吗？解锁后商品保持下架，需店主手动重新上架`,
      '解锁确认',
      { type: 'warning', confirmButtonText: '解锁', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await shopGoodsApi.unlock(row.id)
    ElMessage.success('已解锁，商品保持下架')
    loadPage()
  } catch {
    // 拦截器已提示
  }
}

onMounted(async () => {
  await loadOptions()
  loadPage()
})
</script>

<style scoped>
.filter-form {
  margin-bottom: 4px;
}

.filter-form .el-form-item {
  margin-bottom: 8px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.lock-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
</style>
