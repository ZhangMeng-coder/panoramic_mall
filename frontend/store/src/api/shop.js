import request from './request'

/**
 * 店主店铺 API（经网关 /store/** 前缀转发到 store-center）
 */
export const shopApi = {
  /** 我的店铺（未创建返回 null） */
  mine() {
    return request.get('/store/shops/mine')
  },

  /** 保存草稿 */
  save(data) {
    return request.post('/store/shops/save', data)
  },

  /** 提交审核 */
  submit(data) {
    return request.post('/store/shops/submit', data)
  }
}
