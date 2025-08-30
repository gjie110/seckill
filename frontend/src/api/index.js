import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

// ============================================================
// 演出商品相关 - 对应后端 ProductController (/product)
// ============================================================
export const productApi = {
  // 演出列表：GET /api/product/list?channel=seckill
  getList: (channel) => api.get('/product/list', { params: channel ? { channel } : {} }),
  getBanners: () => api.get('/product/banners'),

  // 演出详情（含票种）：GET /api/product/{productId}
  // 返回 { product: {...}, ticketTypes: [...] }
  getDetail: id => api.get(`/product/${id}`),

  // 即将开售/进行中的秒杀配置：GET /api/product/seckill-upcoming
  getSeckillUpcoming: () => api.get('/product/seckill-upcoming'),

  // 票种实时库存：GET /api/product/ticket/{ticketTypeId}/stock
  getTicketStock: ticketTypeId => api.get(`/product/ticket/${ticketTypeId}/stock`),

  // 票种购买状态（是否可购买、活动状态、活动时间等）：GET /api/product/ticket/{ticketTypeId}/status
  getTicketStatus: ticketTypeId => api.get(`/product/ticket/${ticketTypeId}/status`),

  // 单张票详情（仅展示该票，不展示其他票种）：GET /api/product/ticket/{ticketTypeId}
  getTicketDetail: ticketTypeId => api.get(`/product/ticket/${ticketTypeId}`),

  // 申请候补（模拟，走库存接口判断是否需要）
  applyWaitlist: data => api.post('/product/ticket/waitlist', data)
}

// ============================================================
// 订单相关 - 对应后端 OrderController (/order)
// ============================================================
export const orderApi = {
  // 创建订单：POST /api/order/create
  create: data => api.post('/order/create', data),

  // 订单列表：GET /api/order/list?userId=xxx
  getList: userId => api.get('/order/list', { params: { userId } }),

  // 订单详情：GET /api/order/{orderNo}
  getDetail: orderNo => api.get(`/order/${orderNo}`),

  // 支付订单：POST /api/order/{orderNo}/pay?userId=xxx
  pay: (orderNo, userId) => api.post(`/order/${orderNo}/pay`, null, { params: { userId } }),

  // 取消订单：POST /api/order/{orderNo}/cancel?userId=xxx
  cancel: (orderNo, userId) => api.post(`/order/${orderNo}/cancel`, null, { params: { userId } }),

  // 我的票：GET /api/order/tickets?userId=xxx
  getMyTickets: userId => api.get('/order/tickets', { params: { userId } }),

  // 用户自助退票：POST /api/order/ticket/refund?ticketNo=xxx&userId=xxx
  refundTicket: (ticketNo, userId) => api.post('/order/ticket/refund', null, { params: { ticketNo, userId } }),

  // 候补记录：GET /api/order/waitlist?userId=xxx
  getWaitlist: userId => api.get('/order/waitlist', { params: { userId } })
}

// ============================================================
// 消息通知 - 对应后端 MessageController (/message)
// ============================================================
export const messageApi = {
  // 消息列表：GET /api/message/list?userId=xxx
  getList: (userId = 1001) => api.get('/message/list', { params: { userId } }),

  // 标记全部已读：POST /api/message/read?userId=xxx
  readAll: (userId = 1001) => api.post('/message/read', null, { params: { userId } }),

  // 标记单条已读：POST /api/message/{id}/read
  readOne: (id) => api.post(`/message/${id}/read`),

  // 消息详情：GET /api/message/{id}
  getDetail: (id) => api.get(`/message/${id}`),

  // 未读数量：GET /api/message/unread?userId=xxx
  getUnreadCount: (userId = 1001) => api.get('/message/unread', { params: { userId } })
}

// ============================================================
// 认证相关 - 对应后端 AuthController (/auth)
// ============================================================
export const authApi = {
  // 用户登录：POST /api/auth/login  { username, password }
  // 返回 { userId, username, role, token }
  login: (username, password) => api.post('/auth/login', { username, password }),

  // 查询用户角色：GET /api/auth/role?userId=xxx
  getRole: userId => api.get(`/auth/role?userId=${userId}`),

  // 校验是否管理员：GET /api/auth/is-admin?userId=xxx
  isAdmin: userId => api.get(`/auth/is-admin?userId=${userId}`)
}

// ============================================================
// 管理员相关 - 对应后端 AdminController (/admin)
// ============================================================
export const adminApi = {
  // 演出列表：GET /api/admin/products
  getProducts: () => api.get('/admin/products'),

  // 演出详情：GET /api/admin/product/{productId}
  getProductDetail: productId => api.get(`/admin/product/${productId}`),

  // 搜索演出：GET /api/admin/product/search?keyword=xxx&status=xxx
  searchProducts: (keyword, status) => api.get('/admin/product/search', { params: { keyword, status } }),

  // 新增演出：POST /api/admin/product/add?name=xxx&venue=xxx&isBanner=xxx...
  addProduct: params => api.post('/admin/product/add', null, { params }),

  // 编辑演出：POST /api/admin/product/{productId}/update?name=xxx...
  updateProduct: (productId, params) => api.post(`/admin/product/${productId}/update`, null, { params }),

  // 删除演出（逻辑）：POST /api/admin/product/{productId}/delete
  deleteProduct: productId => api.post(`/admin/product/${productId}/delete`),

  // 发布演出：POST /api/admin/product/{productId}/publish
  publishProduct: productId => api.post(`/admin/product/${productId}/publish`),

  // 下架演出：POST /api/admin/product/{productId}/unpublish
  unpublishProduct: productId => api.post(`/admin/product/${productId}/unpublish`),

  // 设置票种库存：POST /api/admin/ticket/{ticketTypeId}/stock?stock=xxx
  setStock: (ticketTypeId, stock) => api.post(`/admin/ticket/${ticketTypeId}/stock?stock=${stock}`),

  // 发布票种：POST /api/admin/ticket/{ticketTypeId}/publish
  publishTicket: ticketTypeId => api.post(`/admin/ticket/${ticketTypeId}/publish`),

  // 下架票种：POST /api/admin/ticket/{ticketTypeId}/unpublish
  unpublishTicket: ticketTypeId => api.post(`/admin/ticket/${ticketTypeId}/unpublish`),

  // 新增票种：POST /api/admin/ticket/add?productId=xxx&typeName=xxx...
  addTicket: params => api.post('/admin/ticket/add', null, { params }),

  // 编辑票种：POST /api/admin/ticket/{ticketTypeId}/update?typeName=xxx...
  updateTicket: (ticketTypeId, params) => api.post(`/admin/ticket/${ticketTypeId}/update`, null, { params }),

  // 删除票种（逻辑）：POST /api/admin/ticket/{ticketTypeId}/delete
  deleteTicket: ticketTypeId => api.post(`/admin/ticket/${ticketTypeId}/delete`),

  // 调整库存：POST /api/admin/ticket/{ticketTypeId}/stock/adjust?adjustment=xxx&operation=xxx
  adjustStock: (ticketTypeId, adjustment, operation) => api.post(`/admin/ticket/${ticketTypeId}/stock/adjust`, null, { params: { adjustment, operation } }),

  // 配置票种：POST /api/admin/ticket/{ticketTypeId}/config?startTime=xxx...
  setConfig: (ticketTypeId, params) => api.post(`/admin/ticket/${ticketTypeId}/config`, null, { params }),

  // 管理员订单列表：GET /api/admin/orders?keyword=xxx&status=xxx
  getOrders: (keyword, status, page, size) => api.get('/admin/orders', { params: { keyword, status, page, size } }),

  // 管理员订单详情：GET /api/admin/order/{orderNo}
  getOrderDetail: orderNo => api.get(`/admin/order/${orderNo}`),

  // 强制取消订单：POST /api/admin/order/{orderNo}/force-cancel?reason=xxx
  forceCancelOrder: (orderNo, reason) => api.post(`/admin/order/${orderNo}/force-cancel`, null, { params: { reason } }),

  // 发布票（指定数量）：POST /api/admin/ticket/{ticketTypeId}/publish?publishStock=xxx
  publishTicketWithStock: (ticketTypeId, publishStock) => api.post(`/admin/ticket/${ticketTypeId}/publish`, null, { params: { publishStock } }),

  // 修改票种限购数：POST /api/admin/ticket/{ticketTypeId}/max-per-user?maxPerUser=xxx
  setMaxPerUser: (ticketTypeId, maxPerUser) => api.post(`/admin/ticket/${ticketTypeId}/max-per-user`, null, { params: { maxPerUser } }),

  // 用户管理
  getUsers: (keyword, page, size) => api.get('/admin/users', { params: { keyword, page, size } }),
  addUser: (params) => api.post('/admin/user/add', null, { params }),
  editUser: (userId, params) => api.post(`/admin/user/${userId}/update`, null, { params }),
  setUserStatus: (userId, status) => api.post(`/admin/user/${userId}/status`, null, { params: { status } }),
  setUserRole: (userId, role) => api.post(`/admin/user/${userId}/role`, null, { params: { role } }),
  deleteUser: (userId) => api.post(`/admin/user/${userId}/delete`)
}
