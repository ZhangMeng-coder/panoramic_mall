<template>
  <el-card shadow="never">
    <!-- 筛选区 -->
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
        <el-select v-model="query.brandId" clearable filterable placeholder="全部品牌" style="width: 160px">
          <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.shelfStatus" clearable placeholder="全部状态" style="width: 120px">
          <el-option label="上架中" :value="1" />
          <el-option label="已下架" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称">
        <el-input
          v-model="query.keyword"
          placeholder="商品名称关键字"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button type="success" @click="openAdd">新增商品</el-button>
      </el-form-item>
    </el-form>

    <!-- 商品列表 -->
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
      <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip />
      <!-- 分类显示全路径（如「服饰 / 男装 / T恤」），由 store-bff 读时解析；解析失败回退落库快照的分类名 -->
      <el-table-column label="分类" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.categoryPath || row.categoryName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="brandName" label="品牌" min-width="110" />
      <el-table-column prop="skuCount" label="SKU" width="70" align="center" />
      <!-- 上下架状态只读：SPU 状态由名下 SKU 联动推导（上架任一 SKU → SPU 上架；全下架 → SPU 下架），
           切换入口在「规格」弹窗里按 SKU 操作 -->
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.shelfStatus === 1 ? 'success' : 'info'">
            {{ row.shelfStatus === 1 ? '上架中' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <!-- 平台锁定状态（只读）：锁定期整行只读，仅平台可解锁 -->
      <el-table-column label="锁定状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.lockStatus === 1" type="danger">已锁定</el-tag>
          <el-tag v-else type="success" effect="plain">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="中台关联" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.goodsSpuId" type="warning" effect="plain">已关联</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.lockStatus === 1" link type="warning" @click="openLockInfo(row as StoreGoodsSpuPageItem)">锁定信息</el-button>
          <el-button link type="primary" :disabled="row.lockStatus === 1" @click="openEdit(row as StoreGoodsSpuPageItem)">编辑</el-button>
          <el-button link type="success" :disabled="row.lockStatus === 1" @click="openSku(row as StoreGoodsSpuPageItem)">规格</el-button>
          <el-button
            link
            type="danger"
            :disabled="row.lockStatus === 1 || row.shelfStatus === 1"
            @click="handleDelete(row as StoreGoodsSpuPageItem)"
          >删除</el-button>
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

    <GoodsFormDialog
      v-model="dialogVisible"
      :type="dialogType"
      :goods="dialogGoods"
      @save="handleSave"
    />
    <GoodsSkuManageDialog
      v-model="skuDialogVisible"
      :goods="dialogGoods"
      @saved="handleSkuSaved"
      @changed="handleSkuChanged"
    />

    <!-- 锁定信息（只读）：仅展示锁定原因与锁定时间——按 A2 不展示锁定人 -->
    <el-dialog v-model="lockInfoVisible" title="锁定信息" width="440px" destroy-on-close>
      <el-descriptions v-if="lockInfo" :column="1" border>
        <el-descriptions-item label="商品">{{ lockInfo.name }}</el-descriptions-item>
        <el-descriptions-item label="锁定原因">{{ lockInfo.lockReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="锁定时间">{{ lockInfo.lockTime || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="lock-tip">商品被平台锁定期间不可编辑、上下架或删除 SKU；如需恢复，请联系平台管理员解锁，解锁后需自行重新上架。</div>
      <template #footer>
        <el-button type="primary" @click="lockInfoVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { goodsApi, goodsMetaApi } from '../../api/goods'
import type {
  BrandItem,
  CategoryNode,
  StoreGoodsSpuDetail,
  StoreGoodsSpuPageItem,
  StoreGoodsSpuPageQuery,
  StoreGoodsSpuSavePayload
} from '../../api/goods'
import GoodsFormDialog from './GoodsFormDialog.vue'
import GoodsSkuManageDialog from './GoodsSkuManageDialog.vue'

/** 分类下拉项：树选择只用到 id / name / children，故不复用 CategoryNode 的全字段 */
interface CategoryOption {
  id: number
  name: string
  children: CategoryOption[]
}

/** 筛选条件：分类/品牌/状态「全部」为 null（与未传等效，仅用于拼查询参数） */
interface GoodsQuery {
  pageNum: number
  pageSize: number
  categoryId: number | null
  brandId: number | null
  shelfStatus: number | null
  keyword: string
}

const loading = ref(false)
const records = ref<StoreGoodsSpuPageItem[]>([])
const total = ref(0)
const brands = ref<BrandItem[]>([])
const categoryOptions = ref<CategoryOption[]>([])

const query = reactive<GoodsQuery>({
  pageNum: 1,
  pageSize: 10,
  categoryId: null,
  brandId: null,
  shelfStatus: null,
  keyword: ''
})

// 弹窗状态：编辑（基本信息+规格配置） / 规格（SKU 管理 + 上下架）
const dialogVisible = ref(false)
const dialogType = ref('add') // add | edit
const dialogGoods = ref<StoreGoodsSpuDetail | null>(null) // 共享的商品详情（含 SKU 与中台比对结果，打开前各自刷新拉取）
const skuDialogVisible = ref(false)

// 锁定信息弹窗（只读；数据直接取列表行，无需额外请求）
const lockInfoVisible = ref(false)
const lockInfo = ref<StoreGoodsSpuPageItem | null>(null)

async function loadOptions() {
  const [brandList, tree] = await Promise.all([goodsMetaApi.brands(), goodsMetaApi.categories()])
  brands.value = brandList
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
    // 树选择/下拉的「全部」是 null，而查询参数类型把这些字段标成可选（undefined 口径）；
    // 两者在 axios 序列化时都会被丢弃，故这里断言抹平口径差，运行期传的值不变
    const data = await goodsApi.page({ ...query } as StoreGoodsSpuPageQuery)
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
  query.categoryId = null
  query.brandId = null
  query.shelfStatus = null
  query.keyword = ''
  handleSearch()
}

function openAdd() {
  dialogType.value = 'add'
  dialogGoods.value = null
  dialogVisible.value = true
}

// 注：el-table 的插槽把 row 声明为 EP 的 DefaultRow（索引签名行类型，未按 :data 的行类型泛化），
// 模板里拿不到行类型，故在模板调用处用 `row as StoreGoodsSpuPageItem` 断言一次（运行时值不变），
// 以下处理器一律按 StoreGoodsSpuPageItem 收参。
async function openEdit(row: StoreGoodsSpuPageItem) {
  try {
    const detail = await goodsApi.detail(row.id)
    dialogType.value = 'edit'
    dialogGoods.value = detail
    dialogVisible.value = true
  } catch {
    // 详情加载失败时拦截器已提示
  }
}

/** 打开「规格」弹窗（每次拉最新详情，避免 SKU 与上下架状态陈旧） */
async function openSku(row: StoreGoodsSpuPageItem) {
  try {
    dialogGoods.value = await goodsApi.detail(row.id)
    skuDialogVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

/** SKU 保存成功：关闭弹窗并刷新列表（SPU 上下架状态可能已联动变化） */
function handleSkuSaved() {
  skuDialogVisible.value = false
  loadPage()
}

/** SKU 上下架切换：弹窗保持打开（仅供弹窗内继续操作），后台刷新列表上的 SPU 状态 */
function handleSkuChanged() {
  loadPage()
}

/** 打开锁定信息弹窗（只读：原因 + 时间，不含锁定人——按 A2） */
function openLockInfo(row: StoreGoodsSpuPageItem) {
  lockInfo.value = row
  lockInfoVisible.value = true
}

async function handleSave(payload: StoreGoodsSpuSavePayload) {
  try {
    if (dialogType.value === 'add') {
      await goodsApi.add(payload)
      ElMessage.success('商品创建成功')
    } else {
      // 编辑态必非空：openEdit 先拉到详情才打开弹窗；取不到即中止
      //（原写法读 null 抛错会落进下方 catch，结果同为「弹窗不关、无提示」）
      const goods = dialogGoods.value
      if (!goods) return
      await goodsApi.update(goods.id, payload)
      ElMessage.success('商品更新成功')
    }
    dialogVisible.value = false
    loadPage()
  } catch {
    // 后端业务校验失败（如 SKU 价格未填、规格组合重复）时拦截器已提示，弹窗保持打开供修改
  }
}

async function handleDelete(row: StoreGoodsSpuPageItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除商品「${row.name}」吗？删除会一并移除其全部 SKU；上架中的商品需先下架全部 SKU`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await goodsApi.remove(row.id)
    ElMessage.success('商品删除成功')
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
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
</style>
