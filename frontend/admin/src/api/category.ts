import request from './request'
import type { CategoryNode } from '../types/goods'

/** 分类新增请求体，与后端 `CategorySaveDTO` 同构 */
export interface CategoryPayload {
  /** 父分类 ID，0 表示顶级 */
  parentId: number
  name: string
  sort?: number
  /** 分类图标图片 URL（可空） */
  icon?: string
}

/** 分类编辑请求体，与后端 `CategoryUpdateDTO` 同构（名称、排序与图标） */
export type CategoryUpdatePayload = Omit<CategoryPayload, 'parentId'>

/**
 * 商品分类 API
 */
export const categoryApi = {
  /** 新建分类 → 新分类 ID */
  add(data: CategoryPayload) {
    return request.post<number>('/admin/goods/categories', data)
  },

  /** 查询全量分类树 */
  tree() {
    return request.get<CategoryNode[]>('/admin/goods/categories/tree')
  },

  /** 更新分类（名称、排序与图标） */
  update(id: number, data: CategoryUpdatePayload) {
    return request.put<void>(`/admin/goods/categories/${id}`, data)
  },

  /** 删除分类 */
  remove(id: number) {
    return request.delete<void>(`/admin/goods/categories/${id}`)
  }
}
