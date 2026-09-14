import axios, { type RawAxiosHeaders } from 'axios'

export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

export interface V3ApiResponse<T> {
  code: string
  message: string
  data: T
  request_id: string
  timestamp: string
}

export interface UploadedImage {
  fileName: string
  url: string
  bytes: number
}

export interface LoginResult {
  token: string
  accountId: number
  shopId: number
  username: string
  role: string
  v3AccessToken: string
  v3RefreshToken: string
}

export interface TenantContext {
  accountId: number
  tenantId: number
  homeShopId: number | null
  username: string
  roles: string[]
  regionIds: number[]
  shopIds: number[]
  tenantWide: boolean
  permissions?: string[]
}

export interface ShopSummary {
  id: number
  shopCode: string
  name: string
  phone?: string
  address?: string
  timezone: string
  currencyCode: string
  status: string
}

export interface ServiceCatalogItem {
  id: number
  serviceCode: string
  name: string
  coverUrl?: string
  categoryId?: number
  categoryName?: string
  durationMinutes: number
  cleanupMinutes: number
  listPrice: number
  memberPrice: number
  status: 'ACTIVE' | 'INACTIVE'
  version: number
  updatedAt: string
}

export interface StaffDirectoryItem {
  id: number
  staffNo: string
  name: string
  jobRole: string
  levelName?: string
  phone?: string
  status: 'ACTIVE' | 'INACTIVE'
  serviceIds?: string
}

export interface ServiceResourceItem {
  id: number
  resourceCode: string
  resourceName: string
  resourceType: 'ROOM' | 'EQUIPMENT'
  capacity: number
  status: 'ACTIVE' | 'INACTIVE'
  version: number
  updatedAt: string
}

export interface StaffSkillItem {
  id: number
  serviceId: number
  serviceCode: string
  serviceName: string
  enabled: boolean
  customDurationMinutes?: number
  effectiveFrom: string
  version: number
}

export interface StaffScheduleItem {
  id: number
  staffId: number
  scheduleDate: string
  startTime?: string
  endTime?: string
  scheduleType: 'WORK' | 'LEAVE' | 'BLOCKED'
  remark?: string
  status: 'ACTIVE' | 'INACTIVE'
  version: number
  updatedAt: string
}

export interface MemberSummary {
  id: number
  memberNo: string
  globalMemberNo: string
  name: string
  phone: string
  gender?: string
  birthday?: string
  source?: string
  notes?: string
  points: number
  homeShopId: number
  homeShopName: string
  shopNames?: string
  balance: number
  giftBalance: number
  visitCount: number
  lastVisitAt?: string
  status: 'ACTIVE' | 'INACTIVE'
  version: number
  createdAt: string
  updatedAt: string
}

export interface MemberPage {
  records: MemberSummary[]
  total: number
  active: number
  inactive: number
  page: number
  pageSize: number
}

export interface MemberQuery {
  shopId?: number
  keyword?: string
  status?: 'ACTIVE' | 'INACTIVE' | 'ALL'
  page?: number
  pageSize?: number
}

export interface MemberFormPayload {
  shopId: number
  name: string
  phone: string
  gender?: string
  birthday?: string
  source?: string
  notes?: string
}

export interface MemberUpdatePayload extends MemberFormPayload {
  version: number
}

export interface PackageProductItem {
  id?: number
  serviceId: number
  serviceName?: string
  quantity: number
}

export interface PackageProduct {
  id: number
  packageCode: string
  name: string
  description?: string
  salePrice: number
  validityDays: number
  status: 'DRAFT' | 'ACTIVE' | 'INACTIVE'
  version: number
  shopId?: number
  items: PackageProductItem[]
}

export interface PackageInstance {
  id: number
  instanceNo: string
  memberId: number
  packageProductId: number
  packageName: string
  sourceOrderId: number
  purchasePrice: number
  validFrom: string
  validUntil: string
  totalQuantity: number
  remainingQuantity: number
  status: 'ACTIVE' | 'FROZEN' | 'EXHAUSTED' | 'EXPIRED' | 'CANCELLED'
  version: number
  items: Array<{
    id: number
    serviceId: number
    serviceName: string
    totalQuantity: number
    remainingQuantity: number
  }>
}

export interface MemberAssetAccount {
  id: number
  accountType: 'BALANCE' | 'GIFT_BALANCE' | 'POINTS'
  currencyCode: string
  balance: number
  version: number
  status: 'ACTIVE' | 'FROZEN'
  updatedAt: string
}

export type AppointmentStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'IN_SERVICE'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'

export interface AppointmentSummary {
  id: number
  appointmentNo: string
  shopId: number
  shopName: string
  memberId: number
  memberNo: string
  memberName: string
  memberPhone: string
  staffId: number
  staffName: string
  staffLevel?: string
  startAt: string
  endAt: string
  status: AppointmentStatus
  source: 'ONLINE' | 'FRONT_DESK' | 'PHONE' | 'WECHAT'
  memberNote?: string
  internalNote?: string
  cancelReason?: string
  version: number
  createdAt: string
  serviceNames: string
  serviceIds: string
  totalPrice: number
}

export interface AppointmentPage {
  records: AppointmentSummary[]
  total: number
  summary: Partial<Record<AppointmentStatus, number>>
  page: number
  pageSize: number
}

export interface AppointmentMemberOption {
  id: number
  memberNo: string
  name: string
  phone: string
}

export interface AppointmentServiceOption {
  id: number
  serviceCode: string
  name: string
  durationMinutes: number
  cleanupMinutes: number
  listPrice: number
  memberPrice: number
}

export interface AppointmentStaffOption {
  id: number
  staffNo: string
  name: string
  jobRole: string
  levelName?: string
  serviceIds?: string
}

export interface StaffScheduleOption {
  id: number
  staffId: number
  staffName: string
  startTime?: string
  endTime?: string
  scheduleType: 'WORK' | 'LEAVE' | 'BLOCKED'
  remark?: string
}

export interface AppointmentResources {
  members: AppointmentMemberOption[]
  services: AppointmentServiceOption[]
  staff: AppointmentStaffOption[]
  schedules: StaffScheduleOption[]
  date: string
}

export interface AvailabilitySlot {
  startAt: string
  endAt: string
}

export interface AppointmentAvailability {
  date: string
  staffId: number
  durationMinutes: number
  slots: AvailabilitySlot[]
  schedules: Array<Record<string, unknown>>
  bookings: Array<Record<string, unknown>>
}

export interface AppointmentCreatePayload {
  shopId: number
  memberId: number
  staffId: number
  serviceIds: number[]
  startAt: string
  source: 'ONLINE' | 'FRONT_DESK' | 'PHONE' | 'WECHAT'
  memberNote?: string
  internalNote?: string
}

export interface AppointmentReschedulePayload {
  shopId: number
  staffId: number
  startAt: string
  version: number
  reason?: string
}

export type OrderStatus =
  | 'UNPAID'
  | 'PARTIALLY_PAID'
  | 'PAID'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED'
  | 'VOID'

export type PaymentMethod = 'CASH' | 'CARD' | 'WECHAT' | 'ALIPAY' | 'BALANCE' | 'LEGACY'

export interface PaymentSummary {
  id: number
  paymentNo: string
  paymentMethod: PaymentMethod
  amount: number
  refundedAmount: number
  externalTransactionNo?: string
  status: 'PENDING' | 'SUCCESS' | 'FAILED' | 'CANCELLED'
  paidAt?: string
}

export interface RefundSummary {
  id: number
  orderId: number
  paymentId: number
  refundNo: string
  amount: number
  reason: string
  decisionNote?: string
  status: 'PENDING' | 'APPROVED' | 'PROCESSING' | 'SUCCESS' | 'REJECTED' | 'FAILED'
  version: number
  createdBy?: number
  approvedBy?: number
  executedBy?: number
  executionMode?: string
  channelStatus?: string
  failureCode?: string
  reviewedAt?: string
  refundedAt?: string
  createdAt: string
}

export interface OrderSummary {
  id: number
  orderNo: string
  shopId: number
  shopName: string
  businessDate: string
  memberId: number
  memberNo: string
  memberName: string
  memberPhone: string
  appointmentId?: number
  appointmentNo?: string
  serviceRecordId?: number
  subtotalAmount: number
  discountAmount: number
  payableAmount: number
  paidAmount: number
  refundedAmount: number
  paymentMethod?: PaymentMethod | 'MIXED'
  status: OrderStatus
  version: number
  notes?: string
  paidAt?: string
  createdAt: string
  itemNames: string
  payments: PaymentSummary[]
  refunds: RefundSummary[]
}

export interface OrderPage {
  records: OrderSummary[]
  total: number
  summary: Partial<Record<OrderStatus, number>>
  page: number
  pageSize: number
}

export type AnalyticsItemType = 'ALL' | 'SERVICE' | 'PRODUCT'

export interface AnalyticsSummary {
  orderCount: number
  consumingCustomerCount: number
  grossOrderAmount: number
  discountAmount: number
  collectedAmount: number
  refundedAmount: number
  netCollectedAmount: number
}

export interface AnalyticsShopBreakdown extends AnalyticsSummary {
  shopId: number
}

export interface AnalyticsComparison {
  previousFromDate: string
  previousToDate: string
  previousSummary: AnalyticsSummary
}

export interface AnalyticsBreakdown {
  itemType: Exclude<AnalyticsItemType, 'ALL'>
  categoryName?: string
  orderCount: number
  consumingCustomerCount: number
  quantity: number
  lineSalesAmountBeforeRefund: number
}

export interface AnalyticsRanking extends AnalyticsBreakdown {
  itemId: number
  itemName: string
  categoryName: string
  brandName?: string
}

export interface AnalyticsBrandBreakdown {
  brandName: string
  orderCount: number
  consumingCustomerCount: number
  quantity: number
  lineSalesAmountBeforeRefund: number
}

export interface AnalyticsTrend {
  businessDate: string
  orderCount: number
  collectedAmount: number
  refundedAmount: number
  netCollectedAmount: number
}

export interface AnalyticsDataQuality {
  metricVersion: string
  businessDateBasis: string
  refundAllocation: 'ORDER_LEVEL_ONLY'
  lineSalesDefinition: string
  dimensionSnapshot: string
  brandSalesDefinition: string
  shopComparisonDefinition: string
  periodComparisonDefinition: string
}

export interface AnalyticsOverview {
  fromDate: string
  toDate: string
  itemType: AnalyticsItemType
  shopIds: number[]
  asOf: string
  dataQuality: AnalyticsDataQuality
  summary: AnalyticsSummary
  comparison: AnalyticsComparison
  shopBreakdown: AnalyticsShopBreakdown[]
  typeBreakdown: AnalyticsBreakdown[]
  categoryComposition: AnalyticsBreakdown[]
  brandComposition: AnalyticsBrandBreakdown[]
  ranking: AnalyticsRanking[]
  trend: AnalyticsTrend[]
}

export interface AnalyticsLine {
  orderId: number
  orderNo: string
  shopId: number
  businessDate: string
  orderStatus: OrderStatus
  orderRefundedAmount: number
  orderItemId: number
  itemType: Exclude<AnalyticsItemType, 'ALL'>
  itemName: string
  categoryName: string
  brandName?: string
  quantity: number
  unitPrice: number
  discountAmount: number
  lineSalesAmountBeforeRefund: number
  dimensionSnapshotQuality: 'TRANSACTION_TIME' | 'CURRENT_MASTER_BACKFILL' | 'MISSING'
}

export interface AnalyticsLinePage {
  fromDate: string
  toDate: string
  itemType: AnalyticsItemType
  shopIds: number[]
  asOf: string
  dataQuality: AnalyticsDataQuality
  records: AnalyticsLine[]
  total: number
  page: number
  pageSize: number
}

export interface AnalyticsQuery {
  shopId?: number
  fromDate: string
  toDate: string
  itemType?: AnalyticsItemType
}

export interface OperationsOverview {
  fromDate: string
  toDate: string
  shopIds: number[]
  asOf: string
  sales: AnalyticsOverview
  summary: {
    appointmentCount: number
    completedAppointmentCount: number
    completionRate: number
    activeMemberCount: number
    newMemberCount: number
  }
  appointmentTrend: Array<{ businessDate: string; appointmentCount: number; completedCount: number }>
  appointmentStatus: Array<{ status: string; value: number }>
  serviceRanking: Array<{ serviceId: number; serviceName: string; appointmentCount: number; bookedAmount: number }>
  memberTrend: Array<{ businessDate: string; newMemberCount: number }>
  staffWorkload: Array<{ staffId: number; staffName: string; appointmentCount: number; completedCount: number }>
}

export interface AnalyticsReportSnapshot {
  id: number
  reportType: 'SALES_OVERVIEW'
  format: 'CSV'
  status: 'READY' | 'EXPIRED'
  shopIds: number[]
  fromDate: string
  toDate: string
  itemType: AnalyticsItemType
  metricVersion: string
  contentSha256: string
  contentBytes: number
  rowCount: number
  readyAt: string
  expiresAt: string
  createdAt?: string
}

export interface AnalyticsReportPage {
  records: AnalyticsReportSnapshot[]
  total: number
  page: number
  pageSize: number
}

export interface TransactionMemberOption {
  id: number
  memberNo: string
  name: string
  phone: string
  balance: number
  giftBalance: number
  points: number
}

export interface CheckoutAppointmentOption {
  id: number
  appointmentNo: string
  memberId: number
  memberName: string
  memberPhone: string
  startAt: string
  serviceNames: string
  totalAmount: number
}

export interface TransactionCatalogOption {
  id: number
  name: string
  price: number
  serviceCode?: string
  sku?: string
  brandName?: string
  unitName?: string
  stockQuantity?: number
}

export interface TransactionResources {
  members: TransactionMemberOption[]
  appointments: CheckoutAppointmentOption[]
  services: TransactionCatalogOption[]
  products: TransactionCatalogOption[]
}

export interface MemberAccountSummary {
  id: number
  accountType: 'BALANCE' | 'GIFT_BALANCE' | 'POINTS'
  currencyCode: string
  balance: number
  version: number
  status: string
  updatedAt: string
}

export interface MemberLedgerEntry {
  id: number
  accountType: 'BALANCE' | 'GIFT_BALANCE' | 'POINTS'
  entryType: string
  amountDelta: number
  balanceAfter: number
  referenceType?: string
  referenceId?: number
  remark?: string
  createdAt: string
}

export interface MemberAccountActivity {
  accounts: MemberAccountSummary[]
  ledger: MemberLedgerEntry[]
}

export interface InventoryLocationOption {
  id: number
  shopId: number
  shopName: string
  locationCode: string
  name: string
  locationType: 'HEADQUARTERS' | 'SHOP' | 'ROOM'
}

export interface InventoryProductOption {
  id: number
  sku: string
  name: string
  brandName?: string
  unitName: string
  quantityOnHand: number
  warningQuantity: number
}

export interface InventoryResources {
  locations: InventoryLocationOption[]
  products: InventoryProductOption[]
}

export interface InventoryLocationBalance {
  id: number
  locationId: number
  locationName: string
  locationType: 'HEADQUARTERS' | 'SHOP' | 'ROOM'
  quantityOnHand: number
  quantityReserved: number
  quantityAvailable: number
  version: number
  updatedAt: string
}

export interface InventoryProductSummary extends InventoryProductOption {
  quantityReserved: number
  quantityAvailable: number
  lowStock: number | boolean
  updatedAt?: string
  locations: InventoryLocationBalance[]
}

export interface InventoryPage {
  records: InventoryProductSummary[]
  total: number
  summary: {
    productCount: number
    quantityOnHand: number
    quantityReserved: number
    lowStockCount: number
  }
  page: number
  pageSize: number
}

export type InventoryMovementType =
  | 'INITIAL_BALANCE'
  | 'LEGACY_OUT'
  | 'PURCHASE_IN'
  | 'SALE_OUT'
  | 'SERVICE_USE'
  | 'RETURN_IN'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN'
  | 'MANUAL_IN'
  | 'MANUAL_OUT'
  | 'ADJUSTMENT'

export interface InventoryMovement {
  id: number
  shopId: number
  locationId: number
  locationName: string
  productId: number
  sku: string
  productName: string
  unitName: string
  movementType: InventoryMovementType
  quantityDelta: number
  balanceAfter: number
  orderId?: number
  referenceNo?: string
  remark?: string
  createdByName: string
  createdAt: string
}

export interface InventoryTransfer {
  id: number
  transferNo: string
  sourceShopId: number
  sourceShopName: string
  sourceLocationId: number
  sourceLocationName: string
  destinationShopId: number
  destinationShopName: string
  destinationLocationId: number
  destinationLocationName: string
  productId: number
  sku: string
  productName: string
  quantity: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  remark?: string
  decisionNote?: string
  version: number
  createdByName: string
  approvedByName?: string
  createdAt: string
  reviewedAt?: string
  completedAt?: string
}

export type ServiceRecordStatus = 'IN_PROGRESS' | 'COMPLETED' | 'VOID'

export interface ServiceRecordSummary {
  id: number
  recordNo: string
  shopId: number
  shopName: string
  appointmentId?: number
  appointmentNo?: string
  memberId: number
  memberNo: string
  memberName: string
  memberPhone: string
  staffId: number
  staffNo: string
  staffName: string
  staffLevel?: string
  actualStartAt: string
  actualEndAt?: string
  serviceSummary?: string
  nextVisitRecommendation?: string
  status: ServiceRecordStatus
  version: number
  careRecordId?: number
  careVersion?: number
  careCompleted: number
  nextRecommendedAt?: string
  serviceNames?: string
  consumptionCount: number
  createdAt: string
  updatedAt: string
}

export interface ServiceRecordPage {
  records: ServiceRecordSummary[]
  total: number
  summary: {
    total: number
    inProgress: number
    completed: number
    carePending: number
  }
  page: number
  pageSize: number
}

export interface ReadyServiceAppointment {
  id: number
  appointmentNo: string
  memberId: number
  memberName: string
  memberPhone: string
  staffId: number
  staffName: string
  startAt: string
  status: 'CHECKED_IN'
  version: number
  serviceNames: string
}

export interface ServiceConsumableOption {
  productId: number
  sku: string
  productName: string
  unitName: string
  locationId: number
  locationName: string
  locationType: string
  quantityOnHand: number
  quantityReserved: number
  quantityAvailable: number
  balanceVersion: number
}

export interface CareKnowledgeOption {
  id: number
  title: string
  category: string
  cause?: string
  observationFocus?: string
  advice?: string
}

export interface ServiceRecordResources {
  readyAppointments: ReadyServiceAppointment[]
  consumables: ServiceConsumableOption[]
  knowledge: CareKnowledgeOption[]
  skinTypes: string[]
}

export interface ServiceRecordItem {
  id: number
  serviceId: number
  serviceName: string
  durationMinutes: number
  price: number
  sortOrder: number
}

export interface ServiceConsumptionSummary {
  id: number
  productId: number
  sku: string
  productName: string
  locationId: number
  locationName: string
  quantity: number
  inventoryMovementId: number
  createdAt: string
}

export interface ServiceRecordDetail extends ServiceRecordSummary {
  skinType?: string
  concerns: string[]
  observations?: string
  productsUsed: Array<Record<string, unknown>>
  homeCareAdvice?: string
  items: ServiceRecordItem[]
  consumptions: ServiceConsumptionSummary[]
}

export interface MemberCareHistory {
  id: number
  recordNo: string
  actualStartAt: string
  serviceSummary?: string
  nextVisitRecommendation?: string
  staffName: string
  skinType?: string
  concerns: string[]
  observations?: string
  homeCareAdvice?: string
  nextRecommendedAt?: string
  serviceNames?: string
}

const chainApi = axios.create({
  baseURL: import.meta.env.VITE_CHAIN_API ?? 'http://127.0.0.1:8090/face-next',
  timeout: 10000,
})

const v3Api = axios.create({
  baseURL: import.meta.env.VITE_CHAIN_API ?? 'http://127.0.0.1:8090/face-next',
  timeout: 10000,
})

type RetriableRequestConfig = NonNullable<Parameters<typeof v3Api.request>[0]> & {
  _v3AuthRetried?: boolean
}

let refreshSessionPromise: Promise<string> | undefined

function currentAdminSurface(): 'MOBILE' | 'DESKTOP' {
  if (typeof window === 'undefined') return 'DESKTOP'
  const compact = window.matchMedia?.('(max-width: 900px)').matches ?? window.innerWidth <= 900
  return compact ? 'MOBILE' : 'DESKTOP'
}

chainApi.interceptors.request.use((config) => {
  const token = localStorage.getItem('face-chain-token')
  if (token) config.headers.Token = token
  config.headers['X-FACE-Admin-Surface'] = currentAdminSurface()
  return config
})

v3Api.interceptors.request.use((config) => {
  const token = localStorage.getItem('face-chain-v3-access-token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  config.headers['X-FACE-Admin-Surface'] = currentAdminSurface()
  return config
})

v3Api.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error) || error.response?.status !== 401 || !error.config) {
      return Promise.reject(error)
    }
    const request = error.config as RetriableRequestConfig
    if (request._v3AuthRetried || request.url?.includes('/api/v3/auth/')) {
      return Promise.reject(error)
    }
    const refreshToken = localStorage.getItem('face-chain-v3-refresh-token')
    if (!refreshToken) return Promise.reject(error)

    request._v3AuthRetried = true
    const activeRefresh = refreshSessionPromise ??= axios
      .post<V3ApiResponse<{ access_token: string; refresh_token: string }>>(
        `${v3Api.defaults.baseURL}/api/v3/auth/refresh`,
        { refresh_token: refreshToken },
        { timeout: 10000 },
      )
      .then(({ data }) => {
        if (data.code !== 'SUCCESS') throw new Error(data.message || '登录状态续期失败')
        localStorage.setItem('face-chain-v3-access-token', data.data.access_token)
        localStorage.setItem('face-chain-v3-refresh-token', data.data.refresh_token)
        return data.data.access_token
      })

    try {
      const accessToken = await activeRefresh
      request.headers = axios.AxiosHeaders.from(request.headers as RawAxiosHeaders | undefined)
      request.headers.Authorization = `Bearer ${accessToken}`
      return v3Api.request(request)
    } catch (refreshError) {
      localStorage.removeItem('face-chain-token')
      localStorage.removeItem('face-chain-v3-access-token')
      localStorage.removeItem('face-chain-v3-refresh-token')
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.assign('/login')
      }
      return Promise.reject(refreshError)
    } finally {
      if (refreshSessionPromise === activeRefresh) refreshSessionPromise = undefined
    }
  },
)

function messageFrom(error: unknown, fallback: string) {
  if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
    return error.response?.data?.msg || error.message || fallback
  }
  return error instanceof Error ? error.message : fallback
}

async function unwrap<T>(request: Promise<{ data: ApiResponse<T> }>, fallback: string): Promise<T> {
  try {
    const response = await request
    if (response.data.code !== 0) throw new Error(response.data.msg || fallback)
    return response.data.data
  } catch (error) {
    throw new Error(messageFrom(error, fallback))
  }
}

async function unwrapV3<T>(
  request: Promise<{ data: V3ApiResponse<T> }>,
  fallback: string,
): Promise<T> {
  try {
    const response = await request
    if (response.data.code !== 'SUCCESS') throw new Error(response.data.message || fallback)
    return response.data.data
  } catch (error) {
    if (axios.isAxiosError<V3ApiResponse<unknown>>(error)) {
      throw new Error(error.response?.data?.message || error.message || fallback)
    }
    throw new Error(error instanceof Error ? error.message : fallback)
  }
}

export async function login(username: string, password: string): Promise<LoginResult> {
  const v3 = await unwrapV3<{
    access_token: string
    refresh_token: string
  }>(
    v3Api.post<V3ApiResponse<{ access_token: string; refresh_token: string }>>(
      '/api/v3/auth/admin-login',
      { username, password },
    ),
    '安全会话建立失败，请重新登录',
  )
  return {
    token: v3.access_token,
    accountId: 0,
    shopId: 0,
    username,
    role: '',
    v3AccessToken: v3.access_token,
    v3RefreshToken: v3.refresh_token,
  }
}

export async function getTenantContext(): Promise<TenantContext> {
  const context = await unwrapV3<{
    account_id: number
    tenant_id: number
    home_shop_id: number | null
    username: string
    roles: string[]
    region_ids: number[]
    shop_ids: number[]
    tenant_wide: boolean
    permissions: string[]
  }>(
    v3Api.get<V3ApiResponse<{
      account_id: number
      tenant_id: number
      home_shop_id: number | null
      username: string
      roles: string[]
      region_ids: number[]
      shop_ids: number[]
      tenant_wide: boolean
      permissions: string[]
    }>>('/api/v3/me/context'),
    '无法读取账号权限',
  )
  return {
    accountId: context.account_id,
    tenantId: context.tenant_id,
    homeShopId: context.home_shop_id,
    username: context.username,
    roles: context.roles,
    regionIds: context.region_ids,
    shopIds: context.shop_ids,
    tenantWide: context.tenant_wide,
    permissions: context.permissions,
  }
}

export async function getAccessibleShops(): Promise<ShopSummary[]> {
  return unwrapV3(
    v3Api.get<V3ApiResponse<ShopSummary[]>>('/api/v3/shops'),
    '无法读取门店范围',
  )
}

export async function getServiceCatalog(shopId: number) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<ServiceCatalogItem[]>>('/api/v3/services', {
      params: { shop_id: shopId },
    }),
    '服务项目加载失败',
  )
}

export async function updateServiceCatalogItem(
  serviceId: number,
  payload: {
    shop_id: number
    name: string
    cover_url?: string
    duration_minutes: number
    cleanup_minutes: number
    list_price: number
    member_price: number
    status: 'ACTIVE' | 'INACTIVE'
    version: number
  },
) {
  return unwrapV3(
    v3Api.put<V3ApiResponse<{ accepted: boolean }>>(
      `/api/v3/services/${serviceId}`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '服务项目保存失败',
  )
}

export async function getStaffDirectory(shopId: number) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<StaffDirectoryItem[]>>('/api/v3/staff', {
      params: { shop_id: shopId },
    }),
    '技师目录加载失败',
  )
}

export async function getServiceResources(shopId: number) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<ServiceResourceItem[]>>('/api/v3/resources', {
      params: { shop_id: shopId },
    }),
    '房间与设备加载失败',
  )
}

export async function createServiceResource(payload: {
  shop_id: number
  resource_code: string
  resource_name: string
  resource_type: 'ROOM' | 'EQUIPMENT'
  capacity: number
}) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<{ accepted: boolean }>>('/api/v3/resources', payload, {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    }),
    '新增房间或设备失败',
  )
}

export async function updateServiceResource(
  resourceId: number,
  payload: {
    shop_id: number
    resource_code: string
    resource_name: string
    resource_type: 'ROOM' | 'EQUIPMENT'
    capacity: number
    version: number
  },
) {
  return unwrapV3(
    v3Api.put<V3ApiResponse<{ accepted: boolean }>>(
      `/api/v3/resources/${resourceId}`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '房间或设备保存失败',
  )
}

export async function deactivateServiceResource(
  resourceId: number,
  shopId: number,
  version: number,
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<{ accepted: boolean }>>(
      `/api/v3/resources/${resourceId}/deactivate`,
      { shop_id: shopId, version },
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '停用房间或设备失败',
  )
}

export async function getStaffSkills(shopId: number, staffId: number) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<StaffSkillItem[]>>(`/api/v3/staff/${staffId}/skills`, {
      params: { shop_id: shopId },
    }),
    '技师技能加载失败',
  )
}

export async function changeStaffSkill(
  shopId: number,
  staffId: number,
  serviceId: number,
  payload: {
    enabled: boolean
    custom_duration_minutes?: number
    effective_from: string
    version: number
  },
) {
  return unwrapV3(
    v3Api.put<V3ApiResponse<{ accepted: boolean }>>(
      `/api/v3/staff/${staffId}/skills/${serviceId}`,
      { shop_id: shopId, ...payload },
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '技师技能保存失败',
  )
}

export async function getStaffSchedules(
  shopId: number,
  staffId: number,
  from?: string,
  to?: string,
) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<StaffScheduleItem[]>>(
      `/api/v3/staff/${staffId}/schedules`,
      { params: { shop_id: shopId, from, to } },
    ),
    '技师排班加载失败',
  )
}

export async function createStaffSchedule(
  staffId: number,
  payload: {
    shop_id: number
    schedule_date: string
    start_time?: string
    end_time?: string
    schedule_type: 'WORK' | 'LEAVE' | 'BLOCKED'
    remark?: string
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<{ accepted: boolean }>>(
      `/api/v3/staff/${staffId}/schedules`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '技师排班保存失败',
  )
}

export async function deactivateStaffSchedule(
  staffId: number,
  scheduleId: number,
  shopId: number,
  version: number,
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<{ accepted: boolean }>>(
      `/api/v3/staff/${staffId}/schedules/${scheduleId}/deactivate`,
      { shop_id: shopId, version },
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '停用技师排班失败',
  )
}

export async function getMembers(params: MemberQuery): Promise<MemberPage> {
  return unwrap(
    chainApi.get<ApiResponse<MemberPage>>('/api/v2/members', { params }),
    '会员列表加载失败',
  )
}

export async function createMember(payload: MemberFormPayload) {
  return unwrap(
    chainApi.post<ApiResponse<{ id: number; memberNo?: string; linkedExisting: boolean }>>(
      '/api/v2/members',
      payload,
    ),
    '新增会员失败',
  )
}

export async function updateMember(memberId: number, payload: MemberUpdatePayload) {
  return unwrap(
    chainApi.put<ApiResponse<{ id: number; version: number }>>(
      `/api/v2/members/${memberId}`,
      payload,
    ),
    '保存会员资料失败',
  )
}

export async function deactivateMember(memberId: number, shopId: number) {
  return unwrap(
    chainApi.delete<ApiResponse<null>>(`/api/v2/members/${memberId}`, {
      data: { shopId, status: 'INACTIVE' },
    }),
    '停用会员失败',
  )
}

export async function restoreMember(memberId: number, shopId: number) {
  return unwrap(
    chainApi.post<ApiResponse<null>>(`/api/v2/members/${memberId}/restore`, {
      shopId,
      status: 'ACTIVE',
    }),
    '恢复会员失败',
  )
}

export async function getAppointments(params: {
  shopId?: number
  fromDate: string
  toDate: string
  status?: AppointmentStatus | 'ALL'
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<AppointmentPage> {
  return unwrap(
    chainApi.get<ApiResponse<AppointmentPage>>('/api/v2/appointments', { params }),
    '预约列表加载失败',
  )
}

export async function getAppointmentResources(
  shopId: number,
  date: string,
): Promise<AppointmentResources> {
  return unwrap(
    chainApi.get<ApiResponse<AppointmentResources>>('/api/v2/appointments/resources', {
      params: { shopId, date },
    }),
    '预约资源加载失败',
  )
}

export async function getAppointmentAvailability(
  shopId: number,
  staffId: number,
  date: string,
  serviceIds: number[],
): Promise<AppointmentAvailability> {
  return unwrap(
    chainApi.get<ApiResponse<AppointmentAvailability>>('/api/v2/appointments/availability', {
      params: { shopId, staffId, date, serviceIds: serviceIds.join(',') },
    }),
    '可预约时间加载失败',
  )
}

export async function createAppointment(payload: AppointmentCreatePayload) {
  return unwrap(
    chainApi.post<
      ApiResponse<{
        id: number
        appointmentNo: string
        startAt: string
        endAt: string
        status: AppointmentStatus
        version: number
      }>
    >('/api/v2/appointments', payload),
    '创建预约失败',
  )
}

export async function rescheduleAppointment(
  appointmentId: number,
  payload: AppointmentReschedulePayload,
) {
  return unwrap(
    chainApi.put<ApiResponse<{ id: number; startAt: string; endAt: string; version: number }>>(
      `/api/v2/appointments/${appointmentId}/reschedule`,
      payload,
    ),
    '预约改期失败',
  )
}

export async function changeAppointmentStatus(
  appointmentId: number,
  payload: {
    shopId: number
    status: AppointmentStatus
    version: number
    reason?: string
  },
) {
  return unwrap(
    chainApi.post<
      ApiResponse<{
        id: number
        status: AppointmentStatus
        version: number
        allowedNext: AppointmentStatus[]
      }>
    >(`/api/v2/appointments/${appointmentId}/status`, payload),
    '预约状态更新失败',
  )
}

export async function getTransactions(params: {
  shopId?: number
  fromDate: string
  toDate: string
  status?: OrderStatus | 'ALL'
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<OrderPage> {
  return unwrap(
    chainApi.get<ApiResponse<OrderPage>>('/api/v2/transactions', { params }),
    '交易列表加载失败',
  )
}

export async function getAnalyticsOverview(params: AnalyticsQuery): Promise<AnalyticsOverview> {
  return unwrap(
    v3Api.get<ApiResponse<AnalyticsOverview>>('/api/v3/analytics/sales/overview', { params }),
    '经营分析加载失败',
  )
}

export async function getOperationsOverview(
  params: Omit<AnalyticsQuery, 'itemType'>,
): Promise<OperationsOverview> {
  return unwrap(
    v3Api.get<ApiResponse<OperationsOverview>>('/api/v3/analytics/operations/overview', { params }),
    '运营总览加载失败',
  )
}

export async function getAnalyticsLines(
  params: AnalyticsQuery & { page?: number; pageSize?: number },
): Promise<AnalyticsLinePage> {
  return unwrap(
    v3Api.get<ApiResponse<AnalyticsLinePage>>('/api/v3/analytics/sales/lines', { params }),
    '销售明细加载失败',
  )
}

export async function createAnalyticsReport(
  payload: AnalyticsQuery & { format: 'CSV' },
  idempotencyKey: string,
): Promise<AnalyticsReportSnapshot> {
  return unwrap(
    v3Api.post<ApiResponse<AnalyticsReportSnapshot>>('/api/v3/analytics/reports', payload, {
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
    '经营报表生成失败',
  )
}

export async function getAnalyticsReports(
  params: { page?: number; pageSize?: number } = {},
): Promise<AnalyticsReportPage> {
  return unwrap(
    v3Api.get<ApiResponse<AnalyticsReportPage>>('/api/v3/analytics/reports', { params }),
    '经营报表列表加载失败',
  )
}

export async function downloadAnalyticsReport(reportId: number) {
  try {
    const response = await v3Api.get<Blob>(`/api/v3/analytics/reports/${reportId}/download`, {
      responseType: 'blob',
    })
    return {
      blob: response.data,
      contentDisposition: String(response.headers['content-disposition'] ?? ''),
    }
  } catch (error) {
    throw new Error(messageFrom(error, '经营报表下载失败'))
  }
}

export async function getTransactionResources(shopId: number): Promise<TransactionResources> {
  return unwrap(
    chainApi.get<ApiResponse<TransactionResources>>('/api/v2/transactions/resources', {
      params: { shopId },
    }),
    '结算资源加载失败',
  )
}

export async function getMemberAccountActivity(
  shopId: number,
  memberId: number,
): Promise<MemberAccountActivity> {
  return unwrap(
    chainApi.get<ApiResponse<MemberAccountActivity>>(
      `/api/v2/transactions/members/${memberId}/accounts`,
      { params: { shopId } },
    ),
    '会员账户流水加载失败',
  )
}

export async function createOrder(payload: {
  shopId: number
  memberId: number
  appointmentId?: number
  items?: Array<{
    itemType: 'SERVICE' | 'PRODUCT'
    referenceId: number
    quantity: number
    discountAmount?: number
  }>
  notes?: string
}) {
  return unwrap(
    chainApi.post<
      ApiResponse<{
        id: number
        orderNo: string
        status: OrderStatus
        payableAmount: number
        version: number
      }>
    >('/api/v2/transactions', payload),
    '创建订单失败',
  )
}

export async function payOrder(
  orderId: number,
  payload: {
    shopId: number
    paymentMethod: Exclude<PaymentMethod, 'LEGACY'>
    amount: number
    version: number
    idempotencyKey: string
    externalTransactionNo?: string
  },
) {
  return unwrap(
    chainApi.post<ApiResponse<PaymentSummary>>(
      `/api/v2/transactions/${orderId}/payments`,
      payload,
    ),
    '订单收款失败',
  )
}

export async function requestOrderRefund(
  orderId: number,
  payload: {
    shopId: number
    paymentId: number
    amount: number
    reason: string
    idempotencyKey: string
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<RefundSummary>>(
      `/api/v3/payments/${payload.paymentId}/refunds`,
      {
        shop_id: payload.shopId,
        order_id: orderId,
        amount: payload.amount,
        reason: payload.reason,
      },
      { headers: { 'Idempotency-Key': payload.idempotencyKey } },
    ),
    '退款申请提交失败',
  )
}

export async function decideOrderRefund(
  refundId: number,
  payload: {
    shopId: number
    action: 'APPROVE' | 'REJECT'
    version: number
    decisionNote?: string
    idempotencyKey: string
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<RefundSummary>>(
      `/api/v3/refunds/${refundId}/decision`,
      {
        shop_id: payload.shopId,
        action: payload.action,
        version: payload.version,
        decision_note: payload.decisionNote,
      },
      { headers: { 'Idempotency-Key': payload.idempotencyKey } },
    ),
    '退款审核失败',
  )
}

export async function executeOrderRefund(
  refundId: number,
  payload: {
    shopId: number
    version: number
    idempotencyKey: string
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<RefundSummary>>(
      `/api/v3/refunds/${refundId}/execute`,
      {
        shop_id: payload.shopId,
        version: payload.version,
      },
      { headers: { 'Idempotency-Key': payload.idempotencyKey } },
    ),
    '退款执行失败',
  )
}

export async function voidOrder(
  orderId: number,
  payload: { shopId: number; version: number; reason?: string },
) {
  return unwrap(
    chainApi.post<ApiResponse<{ id: number; status: OrderStatus; version: number }>>(
      `/api/v2/transactions/${orderId}/void`,
      payload,
    ),
    '订单作废失败',
  )
}

export async function getInventory(params: {
  shopId: number
  keyword?: string
  lowStockOnly?: boolean
  page?: number
  pageSize?: number
}): Promise<InventoryPage> {
  return unwrap(
    chainApi.get<ApiResponse<InventoryPage>>('/api/v2/inventory', { params }),
    '库存列表加载失败',
  )
}

export async function getInventoryResources(shopId: number): Promise<InventoryResources> {
  return unwrap(
    chainApi.get<ApiResponse<InventoryResources>>('/api/v2/inventory/resources', {
      params: { shopId },
    }),
    '库存基础数据加载失败',
  )
}

export async function getInventoryMovements(params: {
  shopId: number
  productId?: number
  movementType?: InventoryMovementType | 'ALL'
  page?: number
  pageSize?: number
}) {
  return unwrap(
    chainApi.get<
      ApiResponse<{ records: InventoryMovement[]; total: number; page: number; pageSize: number }>
    >('/api/v2/inventory/movements', { params }),
    '库存流水加载失败',
  )
}

export async function adjustInventory(payload: {
  shopId: number
  locationId: number
  productId: number
  quantityDelta: number
  movementType:
    | 'PURCHASE_IN'
    | 'RETURN_IN'
    | 'MANUAL_IN'
    | 'MANUAL_OUT'
    | 'SERVICE_USE'
    | 'ADJUSTMENT'
  version: number
  idempotencyKey: string
  referenceNo?: string
  remark: string
}) {
  return unwrap(
    chainApi.post<ApiResponse<InventoryMovement>>('/api/v2/inventory/adjustments', payload),
    '库存变动提交失败',
  )
}

export async function getInventoryTransfers(params: {
  shopId: number
  status?: 'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'
  page?: number
  pageSize?: number
}) {
  return unwrap(
    chainApi.get<
      ApiResponse<{ records: InventoryTransfer[]; total: number; page: number; pageSize: number }>
    >('/api/v2/inventory/transfers', { params }),
    '调拨单加载失败',
  )
}

export async function createInventoryTransfer(payload: {
  sourceShopId: number
  sourceLocationId: number
  destinationLocationId: number
  productId: number
  quantity: number
  version: number
  idempotencyKey: string
  remark?: string
}) {
  return unwrap(
    chainApi.post<ApiResponse<InventoryTransfer>>('/api/v2/inventory/transfers', payload),
    '创建调拨单失败',
  )
}

export async function decideInventoryTransfer(
  transferId: number,
  payload: {
    shopId: number
    action: 'APPROVE' | 'REJECT'
    version: number
    decisionNote?: string
  },
) {
  return unwrap(
    chainApi.post<ApiResponse<InventoryTransfer>>(
      `/api/v2/inventory/transfers/${transferId}/decision`,
      payload,
    ),
    '调拨审核失败',
  )
}

export async function getServiceRecords(params: {
  shopId: number
  fromDate: string
  toDate: string
  status?: ServiceRecordStatus | 'ALL'
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<ServiceRecordPage> {
  return unwrap(
    chainApi.get<ApiResponse<ServiceRecordPage>>('/api/v2/service-records', { params }),
    '服务记录加载失败',
  )
}

export async function getServiceRecordResources(
  shopId: number,
): Promise<ServiceRecordResources> {
  return unwrap(
    chainApi.get<ApiResponse<ServiceRecordResources>>('/api/v2/service-records/resources', {
      params: { shopId },
    }),
    '服务与护理资源加载失败',
  )
}

export async function getServiceRecord(serviceRecordId: number, shopId: number) {
  return unwrap(
    chainApi.get<ApiResponse<ServiceRecordDetail>>(`/api/v2/service-records/${serviceRecordId}`, {
      params: { shopId },
    }),
    '服务记录详情加载失败',
  )
}

export async function getMemberCareHistory(shopId: number, memberId: number) {
  return unwrap(
    chainApi.get<ApiResponse<MemberCareHistory[]>>(
      `/api/v2/service-records/members/${memberId}/history`,
      { params: { shopId } },
    ),
    '会员护理历史加载失败',
  )
}

export async function startService(payload: {
  shopId: number
  appointmentId: number
  appointmentVersion: number
}) {
  return unwrap(
    chainApi.post<ApiResponse<ServiceRecordDetail>>('/api/v2/service-records/start', payload),
    '开始服务失败',
  )
}

export interface CarePayload {
  shopId: number
  serviceSummary: string
  nextVisitRecommendation?: string
  skinType?: string
  concerns: string[]
  observations: string
  homeCareAdvice?: string
  nextRecommendedAt?: string
}

export async function completeService(
  serviceRecordId: number,
  payload: CarePayload & {
    version: number
    consumptions: Array<{
      locationId: number
      productId: number
      quantity: number
      balanceVersion: number
    }>
    idempotencyKey: string
  },
) {
  return unwrap(
    chainApi.post<ApiResponse<ServiceRecordDetail>>(
      `/api/v2/service-records/${serviceRecordId}/complete`,
      payload,
    ),
    '完成服务失败',
  )
}

export async function updateServiceCare(
  serviceRecordId: number,
  payload: CarePayload & { careVersion?: number },
) {
  return unwrap(
    chainApi.put<ApiResponse<ServiceRecordDetail>>(
      `/api/v2/service-records/${serviceRecordId}/care`,
      payload,
    ),
    '护理档案保存失败',
  )
}

export interface ServiceRecordCorrection {
  id: number
  serviceRecordId: number
  baseVersion: number
  correctionType: string
  reason: string
  correctedFields: string | Record<string, unknown>
  createdBy: number
  createdAt: string
}

export async function getServiceRecordCorrections(
  serviceRecordId: number,
  shopId: number,
) {
  return unwrap(
    chainApi.get<ApiResponse<ServiceRecordCorrection[]>>(
      `/api/v2/service-records/${serviceRecordId}/corrections`,
      { params: { shopId } },
    ),
    '护理更正记录加载失败',
  )
}

export async function appendServiceRecordCorrection(
  serviceRecordId: number,
  payload: {
    shopId: number
    serviceRecordVersion: number
    reason: string
    correctedFields: Record<string, unknown>
    idempotencyKey: string
  },
) {
  return unwrap(
    chainApi.post<ApiResponse<{
      accepted: boolean
      serviceRecordId: number
      correctionId?: number
      replayed: boolean
    }>>(
      `/api/v2/service-records/${serviceRecordId}/corrections`,
      payload,
    ),
    '护理更正提交失败',
  )
}

export async function getPackageProducts(
  shopId: number,
  status: 'ALL' | 'DRAFT' | 'ACTIVE' | 'INACTIVE' = 'ALL',
) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<PackageProduct[]>>('/api/v3/package-products', {
      params: { shop_id: shopId, status },
    }),
    '套餐产品加载失败',
  )
}

export async function createPackageProduct(payload: {
  shop_id: number
  package_code: string
  name: string
  description?: string
  sale_price: number
  validity_days: number
  status: 'DRAFT' | 'ACTIVE'
  items: Array<{ service_id: number; quantity: number }>
}) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<PackageProduct>>('/api/v3/package-products', payload, {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    }),
    '套餐产品创建失败',
  )
}

export async function updatePackageProduct(
  packageProductId: number,
  payload: {
    shop_id: number
    name: string
    description?: string
    sale_price: number
    validity_days: number
    status: 'DRAFT' | 'ACTIVE' | 'INACTIVE'
    version: number
    items: Array<{ service_id: number; quantity: number }>
  },
) {
  return unwrapV3(
    v3Api.patch<V3ApiResponse<PackageProduct>>(
      `/api/v3/package-products/${packageProductId}`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '套餐产品保存失败',
  )
}

export async function getMemberPackages(shopId: number, memberId: number) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<PackageInstance[]>>(`/api/v3/members/${memberId}/packages`, {
      params: { shop_id: shopId },
    }),
    '会员套餐加载失败',
  )
}

export async function issueMemberPackage(
  memberId: number,
  payload: {
    shop_id: number
    package_product_id: number
    source_order_id: number
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<PackageInstance>>(
      `/api/v3/members/${memberId}/packages`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '会员套餐发放失败',
  )
}

export async function changePackageStatus(
  packageInstanceId: number,
  action: 'freeze' | 'unfreeze',
  shopId: number,
  version: number,
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<PackageInstance>>(
      `/api/v3/package-instances/${packageInstanceId}/${action}`,
      { shop_id: shopId, version },
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    action === 'freeze' ? '套餐冻结失败' : '套餐解冻失败',
  )
}

export async function getMemberAssetAccounts(shopId: number, memberId: number) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<MemberAssetAccount[]>>(`/api/v3/members/${memberId}/accounts`, {
      params: { shop_id: shopId },
    }),
    '会员账户加载失败',
  )
}

export async function getMemberAssetAccountLedger(shopId: number, accountId: number) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<Array<Record<string, unknown>>>>(
      `/api/v3/member-accounts/${accountId}/ledger`,
      { params: { shop_id: shopId } },
    ),
    '会员账户流水加载失败',
  )
}

export async function postMemberAccountEntry(
  accountId: number,
  direction: 'credits' | 'debits',
  payload: {
    shop_id: number
    entry_type: string
    amount: number
    reference_type?: string
    reference_id?: number
    remark?: string
    version: number
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<Record<string, unknown>>>(
      `/api/v3/member-accounts/${accountId}/${direction}`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    direction === 'credits' ? '账户入账失败' : '账户扣减失败',
  )
}

export async function changeMemberAssetAccountStatus(
  accountId: number,
  action: 'freeze' | 'unfreeze',
  shopId: number,
  version: number,
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<MemberAssetAccount>>(
      `/api/v3/member-accounts/${accountId}/${action}`,
      { shop_id: shopId, version },
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    action === 'freeze' ? '账户冻结失败' : '账户解冻失败',
  )
}

export async function reverseMemberAccountEntry(
  ledgerId: number,
  payload: { shop_id: number; version: number; reason: string },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<Record<string, unknown>>>(
      `/api/v3/member-account-ledger/${ledgerId}/reversals`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '账户流水冲正失败',
  )
}

export type PurchaseOrderStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'PARTIALLY_RECEIVED'
  | 'RECEIVED'
  | 'CLOSED'

export interface PurchaseOrderItem {
  id: number
  productId: number
  sku: string
  productName: string
  unitName: string
  orderedQuantity: number
  receivedQuantity: number
  outstandingQuantity: number
  unitCost: number
  lineAmount: number
  version: number
}

export interface PurchaseReceiptSummary {
  id: number
  receiptNo: string
  receivedAt: string
  remark?: string
  createdBy: number
  createdByName: string
  createdAt: string
}

export interface PurchaseOrderSummary {
  id: number
  shopId: number
  purchaseOrderNo: string
  supplierName: string
  expectedDate?: string
  currencyCode: string
  totalAmount: number
  status: PurchaseOrderStatus
  version: number
  createdBy: number
  createdByName: string
  approvedBy?: number
  approvedByName?: string
  submittedAt?: string
  approvedAt?: string
  closedAt?: string
  createdAt: string
  itemCount: number
  orderedQuantity: number
  receivedQuantity: number
}

export interface PurchaseOrderDetail extends Omit<PurchaseOrderSummary, 'itemCount' | 'orderedQuantity' | 'receivedQuantity'> {
  remark?: string
  decisionNote?: string
  updatedAt: string
  items: PurchaseOrderItem[]
  receipts: PurchaseReceiptSummary[]
}

export interface PurchaseOrderPage {
  records: PurchaseOrderSummary[]
  total: number
  page: number
  pageSize: number
}

export interface StockBatch {
  id: number
  shopId: number
  locationId: number
  locationName: string
  productId: number
  sku: string
  productName: string
  unitName: string
  batchNo: string
  vendorBatchNo?: string
  producedDate?: string
  expiryDate?: string
  unitCost: number
  quantityReceived: number
  quantityOnHand: number
  quantityReserved: number
  status: string
  sourceType: string
  sourceId: number
  version: number
  createdAt: string
}

export async function getPurchaseOrders(params: {
  shopId: number
  status?: PurchaseOrderStatus | 'ALL'
  page?: number
  pageSize?: number
}) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<PurchaseOrderPage>>('/api/v3/purchase-orders', {
      params: {
        shop_id: params.shopId,
        status: params.status ?? 'ALL',
        page: params.page ?? 1,
        page_size: params.pageSize ?? 30,
      },
    }),
    '采购单加载失败',
  )
}

export async function getPurchaseOrder(shopId: number, purchaseOrderId: number) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<PurchaseOrderDetail>>(
      `/api/v3/purchase-orders/${purchaseOrderId}`,
      { params: { shop_id: shopId } },
    ),
    '采购单详情加载失败',
  )
}

export async function createPurchaseOrder(payload: {
  shop_id: number
  supplier_name: string
  expected_date?: string
  currency_code: string
  remark?: string
  lines: Array<{ product_id: number; quantity: number; unit_cost: number }>
}) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<PurchaseOrderDetail>>('/api/v3/purchase-orders', payload, {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    }),
    '采购单创建失败',
  )
}

export async function submitPurchaseOrder(
  purchaseOrderId: number,
  shopId: number,
  version: number,
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<PurchaseOrderDetail>>(
      `/api/v3/purchase-orders/${purchaseOrderId}/submit`,
      { shop_id: shopId, version },
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '采购单提交失败',
  )
}

export async function decidePurchaseOrder(
  purchaseOrderId: number,
  payload: {
    shop_id: number
    version: number
    action: 'APPROVE' | 'CLOSE'
    decision_note?: string
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<PurchaseOrderDetail>>(
      `/api/v3/purchase-orders/${purchaseOrderId}/decision`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    payload.action === 'APPROVE' ? '采购单审批失败' : '采购单关闭失败',
  )
}

export async function receivePurchaseOrder(
  purchaseOrderId: number,
  payload: {
    shop_id: number
    version: number
    received_at?: string
    remark?: string
    lines: Array<{
      purchase_order_item_id: number
      location_id: number
      received_quantity: number
      unit_cost: number
      vendor_batch_no?: string
      produced_date?: string
      expiry_date?: string
    }>
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<Record<string, unknown>>>(
      `/api/v3/purchase-orders/${purchaseOrderId}/receipts`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '采购收货失败',
  )
}

export async function getInventoryBatches(params: {
  shopId: number
  productId?: number
  locationId?: number
}) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<StockBatch[]>>('/api/v3/inventory/batches', {
      params: {
        shop_id: params.shopId,
        product_id: params.productId,
        location_id: params.locationId,
      },
    }),
    '库存批次加载失败',
  )
}

export type ReconciliationStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'MATCHED'
  | 'DIFFERENT'
  | 'RESOLVED'
  | 'CLOSED'

export interface ReconciliationItem {
  id: number
  businessType: 'PAYMENT' | 'REFUND'
  businessNo: string
  systemAmount?: number
  channelAmount?: number
  differenceType: 'MISSING_CHANNEL' | 'MISSING_SYSTEM' | 'AMOUNT_MISMATCH' | 'STATUS_MISMATCH'
  createdAt: string
}

export interface ReconciliationResolution {
  id: number
  resolutionNote: string
  evidenceReference?: string
  createdBy: number
  createdByName: string
  createdAt: string
}

export interface ReconciliationBatch {
  id: number
  shopId: number
  channelCode: string
  accountingDate: string
  systemPaymentCount: number
  systemPaymentAmount: number
  systemRefundCount: number
  systemRefundAmount: number
  channelPaymentCount: number
  channelPaymentAmount: number
  channelRefundCount: number
  channelRefundAmount: number
  status: ReconciliationStatus
  version: number
  createdBy: number
  startedAt?: string
  completedAt?: string
  closedAt?: string
  createdAt: string
  items?: ReconciliationItem[]
  resolutions?: ReconciliationResolution[]
}

export async function getReconciliationBatches(
  shopId: number,
  status: ReconciliationStatus | 'ALL' = 'ALL',
) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<ReconciliationBatch[]>>('/api/v3/reconciliation-batches', {
      params: { shop_id: shopId, status },
    }),
    '对账批次加载失败',
  )
}

export async function getReconciliationBatch(shopId: number, batchId: number) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<ReconciliationBatch>>(
      `/api/v3/reconciliation-batches/${batchId}`,
      { params: { shop_id: shopId } },
    ),
    '对账批次详情加载失败',
  )
}

export async function runReconciliation(payload: {
  shop_id: number
  channel_code: string
  accounting_date: string
  channel_payment_count: number
  channel_payment_amount: number
  channel_refund_count: number
  channel_refund_amount: number
}) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<ReconciliationBatch>>(
      '/api/v3/reconciliation-batches',
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '日终对账执行失败',
  )
}

export async function resolveReconciliation(
  batchId: number,
  payload: {
    shop_id: number
    version: number
    resolution_note: string
    evidence_reference?: string
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<ReconciliationBatch>>(
      `/api/v3/reconciliation-batches/${batchId}/resolve`,
      payload,
    ),
    '对账差异处理失败',
  )
}

export async function closeReconciliation(
  batchId: number,
  shopId: number,
  version: number,
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<ReconciliationBatch>>(
      `/api/v3/reconciliation-batches/${batchId}/close`,
      { shop_id: shopId, version },
    ),
    '对账批次关闭失败',
  )
}

export interface M5Page<T> {
  records: T[]
  total?: number
  page: number
  pageSize: number
}

export interface CommissionRuleSummary {
  id: number
  ruleCode: string
  ruleName: string
  sourceType: string
  calculationType: string
  rateValue: number
  fixedAmount: number
  status: 'DRAFT' | 'ACTIVE' | 'RETIRED'
  version: number
  versionNo: number
  effectiveFrom: string
  effectiveTo?: string
}

export interface CommissionEntrySummary {
  id: number
  entryNo: string
  staffId: number
  staffName: string
  entryType: 'ACCRUAL' | 'REVERSAL' | 'ADJUSTMENT'
  baseAmount: number
  amount: number
  status: 'PENDING' | 'FROZEN' | 'SETTLED' | 'REVERSED'
  version: number
  sourceType: string
  businessNo: string
  createdAt: string
}

export interface CommissionSettlementSummary {
  id: number
  settlementNo: string
  periodStart: string
  periodEnd: string
  currencyCode: string
  status: 'DRAFT' | 'CALCULATED' | 'CONFIRMED' | 'PAID' | 'CLOSED' | 'VOIDED'
  itemCount: number
  totalAmount: number
  version: number
  createdAt: string
}

export interface AfterSaleCaseSummary {
  id: number
  caseNo: string
  memberId?: number
  orderId?: number
  serviceRecordId?: number
  category: string
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
  summary: string
  status: string
  originType?: string
  entryDeadlineAt?: string
  resolutionType?: string
  resolutionNote?: string
  riskAmount?: number
  requiresSuperAdmin?: boolean
  customerResponseDueAt?: string
  reopenCount?: number
  assigneeAccountId?: number
  refundId?: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface Sc6ReviewSummary {
  id: number
  memberId: number
  memberName: string
  serviceRecordId: number
  staffRating: number
  effectRating: number
  environmentRating: number
  averageRating: number
  visibility: 'PUBLIC' | 'SHOP_ONLY'
  moderationStatus: 'PENDING' | 'APPROVED' | 'HIDDEN' | 'NOT_REQUIRED'
  currentVersionNo: number
  wantsContact: boolean
  content?: string
  replyText?: string
  hiddenReason?: string
  afterSaleCaseId?: number
  deletedAt?: string
  version: number
  createdAt: string
}

export interface Sc6MallReturnSummary {
  id: number
  mallOrderId: number
  orderNo: string
  afterSaleCaseId: number
  reasonCode: string
  reasonDetail: string
  status: string
  returnTrackingNo?: string
  inspectionResult?: 'PASS' | 'FAIL'
  inspectionReason?: string
  version: number
  submittedAt: string
}

export interface ApprovalSummary {
  id: number
  approvalNo: string
  businessType: string
  businessId: number
  approvalType: string
  safeSummary: string
  requesterAccountId: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED'
  version: number
  decidedBy?: number
  decidedAt?: string
  createdAt: string
}

export interface NotificationSummary {
  id: number
  shopId?: number
  eventType: string
  businessType: string
  businessId: string
  category: 'APPROVAL' | 'AFTERSALE' | 'COMMISSION' | 'SETTLEMENT' | 'SYSTEM' | 'MARKETING'
  deliveryStatus: 'DELIVERED'
  externalStatus: 'UNAVAILABLE' | 'NOT_REQUESTED'
  title: string
  safeSummary: string
  actionPath?: string
  status: 'UNREAD' | 'READ'
  readAt?: string
  version: number
  createdAt: string
}

export interface NotificationPage extends M5Page<NotificationSummary> {
  unreadCount: number
  asOf: string
}

export type MarketingCampaignStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'CANCELLED'

export interface MarketingCampaignSummary {
  id: number
  campaignNo: string
  title: string
  safeSummary: string
  channel: 'IN_APP' | 'SMS' | 'EMAIL' | 'WECHAT'
  actionPath?: string
  scheduledAt?: string
  status: MarketingCampaignStatus
  version: number
  createdBy: number
  submittedBy?: number
  approvedBy?: number
  audienceCount?: number
  deliveredCount?: number
  createdAt: string
  updatedAt: string
}

export interface MarketingCampaignPage extends M5Page<MarketingCampaignSummary> {}

export async function getCommissionRules(shopId: number) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<CommissionRuleSummary[]>>('/api/v3/commission/rules', {
      params: { shop_id: shopId },
    }),
    '提成规则加载失败',
  )
}

export async function changeCommissionRuleStatus(
  ruleId: number,
  action: 'publish' | 'retire',
  shopId: number,
  version: number,
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<Record<string, unknown>>>(
      `/api/v3/commission/rules/${ruleId}/${action}`,
      { shop_id: shopId, version },
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    action === 'publish' ? '发布提成规则失败' : '停用提成规则失败',
  )
}

export async function getCommissionEntries(
  shopId: number,
  status?: string,
): Promise<M5Page<CommissionEntrySummary>> {
  return unwrapV3(
    v3Api.get<V3ApiResponse<M5Page<CommissionEntrySummary>>>(
      '/api/v3/commission/entries',
      { params: { shop_id: shopId, status, page: 1, page_size: 50 } },
    ),
    '提成流水加载失败',
  )
}

export async function changeCommissionEntryStatus(
  entryId: number,
  action: 'freeze' | 'unfreeze',
  payload: { shop_id: number; version: number; reason: string },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<CommissionEntrySummary>>(
      `/api/v3/commission/entries/${entryId}/${action}`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    action === 'freeze' ? '冻结提成流水失败' : '解冻提成流水失败',
  )
}

export async function requestCommissionAdjustment(
  entryId: number,
  payload: { shop_id: number; amount: number; reason: string },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<Record<string, unknown>>>(
      `/api/v3/commission/entries/${entryId}/adjustments`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '提成调整申请失败',
  )
}

export async function getCommissionSettlements(
  shopId: number,
  status?: string,
): Promise<M5Page<CommissionSettlementSummary>> {
  return unwrapV3(
    v3Api.get<V3ApiResponse<M5Page<CommissionSettlementSummary>>>(
      '/api/v3/commission/settlements',
      { params: { shop_id: shopId, status, page: 1, page_size: 50 } },
    ),
    '提成结算加载失败',
  )
}

export async function createCommissionSettlement(payload: {
  shop_id: number
  period_start: string
  period_end: string
  currency_code: string
}) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<CommissionSettlementSummary>>(
      '/api/v3/commission/settlements',
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '创建提成结算失败',
  )
}

export async function transitionCommissionSettlement(
  batchId: number,
  action: 'calculate' | 'confirm' | 'mark-paid' | 'close',
  payload: {
    shop_id: number
    version: number
    payment_reference?: string
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<CommissionSettlementSummary>>(
      `/api/v3/commission/settlements/${batchId}/${action}`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '更新提成结算状态失败',
  )
}

export async function getAfterSaleCases(
  shopId: number,
  status?: string,
): Promise<M5Page<AfterSaleCaseSummary>> {
  return unwrapV3(
    v3Api.get<V3ApiResponse<M5Page<AfterSaleCaseSummary>>>('/api/v3/after-sales/cases', {
      params: { shop_id: shopId, status, page: 1, page_size: 50 },
    }),
    '售后工单加载失败',
  )
}

export async function actOnAfterSaleCase(
  caseId: number,
  payload: {
    shop_id: number
    version: number
    target_status: string
    note?: string
    assignee_account_id?: number
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<AfterSaleCaseSummary>>(
      `/api/v3/after-sales/cases/${caseId}/actions`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '售后工单更新失败',
  )
}

export async function getSc6Reviews(shopId: number, status = 'ALL') {
  return unwrapV3(
    v3Api.get<V3ApiResponse<Sc6ReviewSummary[]>>('/api/v3/admin/reviews', {
      params: { shop_id: shopId, status },
    }),
    '评价审核队列加载失败',
  )
}

export async function moderateSc6Review(
  reviewId: number,
  payload: { shop_id: number; version: number; action: 'APPROVE' | 'HIDE' | 'REPLY'; note?: string },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<Sc6ReviewSummary>>(
      `/api/v3/admin/reviews/${reviewId}/moderation`, payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '评价审核操作失败',
  )
}

export async function decideSc6AfterSale(
  caseId: number,
  payload: {
    shop_id: number
    version: number
    resolution_type: 'REFUND' | 'REDO_SERVICE' | 'RESTORE_ENTITLEMENT' | 'COMPENSATION_COUPON' | 'REJECT'
    risk_amount: number
    note: string
    evidence?: Record<string, unknown>
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<AfterSaleCaseSummary>>(
      `/api/v3/after-sales/cases/${caseId}/solution`, payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '售后方案提交失败',
  )
}

export async function getSc6MallReturns(shopId: number, status?: string) {
  return unwrapV3(
    v3Api.get<V3ApiResponse<Sc6MallReturnSummary[]>>('/api/v3/mall/returns', {
      params: { shop_id: shopId, status },
    }),
    '退换货列表加载失败',
  )
}

export async function reviewSc6MallReturn(
  returnId: number,
  payload: { shop_id: number; version: number; approved: boolean; reason?: string },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<Sc6MallReturnSummary>>(
      `/api/v3/mall/returns/${returnId}/review`, payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '退换货审核失败',
  )
}

export async function inspectSc6MallReturn(
  returnId: number,
  payload: {
    shop_id: number
    version: number
    passed: boolean
    stock_disposition?: 'RESTORE' | 'DAMAGED'
    reason: string
    evidence?: Record<string, unknown>
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<Sc6MallReturnSummary>>(
      `/api/v3/mall/returns/${returnId}/inspection`, payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '退货验货失败',
  )
}

export async function getApprovals(
  shopId: number,
  status?: string,
): Promise<M5Page<ApprovalSummary>> {
  return unwrapV3(
    v3Api.get<V3ApiResponse<M5Page<ApprovalSummary>>>('/api/v3/approvals', {
      params: { shop_id: shopId, status, page: 1, page_size: 50 },
    }),
    '审批列表加载失败',
  )
}

export async function decideApproval(
  approvalId: number,
  payload: {
    shop_id: number
    version: number
    action: 'APPROVE' | 'REJECT'
    reason?: string
  },
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<ApprovalSummary>>(
      `/api/v3/approvals/${approvalId}/decisions`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    payload.action === 'APPROVE' ? '审批通过失败' : '审批拒绝失败',
  )
}

export async function getNotifications(
  status: 'ALL' | 'UNREAD' | 'READ' = 'ALL',
): Promise<NotificationPage> {
  return unwrapV3(
    v3Api.get<V3ApiResponse<NotificationPage>>('/api/v3/notifications', {
      params: { status, page: 1, page_size: 50 },
    }),
    '通知加载失败',
  )
}

export async function markNotificationRead(notificationId: number, version: number) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<NotificationSummary>>(
      `/api/v3/notifications/${notificationId}/read`,
      { version },
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '通知已读更新失败',
  )
}

export async function markAllNotificationsRead() {
  return unwrapV3(
    v3Api.post<V3ApiResponse<{ changed?: number; unreadCount: number }>>(
      '/api/v3/notifications/read-all',
      {},
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '全部已读更新失败',
  )
}

export async function getMarketingCampaigns(
  shopId: number,
  status?: MarketingCampaignStatus,
  page = 1,
): Promise<MarketingCampaignPage> {
  return unwrapV3(
    v3Api.get<V3ApiResponse<MarketingCampaignPage>>('/api/v3/marketing/campaigns', {
      params: { shop_id: shopId, status, page, page_size: 30 },
    }),
    '营销活动加载失败',
  )
}

export async function createMarketingCampaign(payload: {
  shop_id: number
  title: string
  safe_summary: string
  channel: string
  action_path?: string
  scheduled_at?: string
}) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<MarketingCampaignSummary>>(
      '/api/v3/marketing/campaigns',
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '营销活动创建失败',
  )
}

export async function submitMarketingCampaign(campaign: MarketingCampaignSummary, shopId: number) {
  return marketingCommand(campaign.id, 'submit', {
    shop_id: shopId,
    version: campaign.version,
  })
}

export async function decideMarketingCampaign(
  campaign: MarketingCampaignSummary,
  shopId: number,
  action: 'APPROVE' | 'REJECT',
  reason?: string,
) {
  return marketingCommand(campaign.id, 'decisions', {
    shop_id: shopId,
    version: campaign.version,
    action,
    reason,
  })
}

export async function executeMarketingCampaign(campaign: MarketingCampaignSummary, shopId: number) {
  return marketingCommand(campaign.id, 'execute', {
    shop_id: shopId,
    version: campaign.version,
  })
}

export async function cancelMarketingCampaign(
  campaign: MarketingCampaignSummary,
  shopId: number,
  reason: string,
) {
  return marketingCommand(campaign.id, 'cancel', {
    shop_id: shopId,
    version: campaign.version,
    reason,
  })
}

async function marketingCommand(
  campaignId: number,
  action: 'submit' | 'decisions' | 'execute' | 'cancel',
  payload: Record<string, unknown>,
) {
  return unwrapV3(
    v3Api.post<V3ApiResponse<MarketingCampaignSummary>>(
      `/api/v3/marketing/campaigns/${campaignId}/${action}`,
      payload,
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
    '营销活动状态更新失败',
  )
}

export interface TrainingCourse {
  id: number
  courseCode: string
  revision: number
  title: string
  safeSummary: string
  passScore: number
  validityDays?: number
  status: 'DRAFT' | 'ACTIVE' | 'RETIRED'
  version: number
  updatedAt?: string
}

export interface TrainingRecord {
  id: number
  shopId: number
  recordNo: string
  courseId: number
  courseCode: string
  courseRevision: number
  courseTitle: string
  courseSummary: string
  passScore: number
  staffId: number
  staffNo: string
  staffName: string
  status: 'ASSIGNED' | 'IN_PROGRESS' | 'SUBMITTED' | 'PASSED' | 'FAILED' | 'EXPIRED' | 'CANCELLED'
  dueAt?: string
  score?: number
  certificateNo?: string
  validUntil?: string
  version: number
}

export interface IntegrationClient {
  id: number
  clientCode: string
  clientName: string
  safeDescription?: string
  status: 'ACTIVE' | 'REVOKED'
  scopes: string | string[]
  secretVersion: number
  secretPrefix: string
  rateLimitPerMinute: number
  version: number
  lastUsedAt?: string
  clientSecret?: string
  secretShownOnce?: boolean
}

export const getTrainingCourses = (shopId: number) => unwrapV3(
  v3Api.get<V3ApiResponse<TrainingCourse[]>>('/api/v3/training/courses', { params: { shop_id: shopId } }),
  '培训课程加载失败',
)

export const createTrainingCourse = (payload: Record<string, unknown>) => unwrapV3(
  v3Api.post<V3ApiResponse<TrainingCourse>>('/api/v3/training/courses', payload, {
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  }),
  '培训课程创建失败',
)

export const transitionTrainingCourse = (course: TrainingCourse, shopId: number, action: 'publish' | 'retire') => unwrapV3(
  v3Api.post<V3ApiResponse<TrainingCourse>>(`/api/v3/training/courses/${course.id}/${action}`, {
    shop_id: shopId, version: course.version,
  }, { headers: { 'Idempotency-Key': crypto.randomUUID() } }),
  '培训课程状态更新失败',
)

export const getTrainingRecords = (shopId: number) => unwrapV3(
  v3Api.get<V3ApiResponse<TrainingRecord[]>>('/api/v3/training/records', { params: { shop_id: shopId } }),
  '培训记录加载失败',
)

export const assignTrainingRecord = (payload: Record<string, unknown>) => unwrapV3(
  v3Api.post<V3ApiResponse<TrainingRecord>>('/api/v3/training/records', payload, {
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  }),
  '培训分配失败',
)

export const verifyTrainingRecord = (record: TrainingRecord, shopId: number, score: number, safeReason?: string) => unwrapV3(
  v3Api.post<V3ApiResponse<TrainingRecord>>(`/api/v3/training/records/${record.id}/verify`, {
    shop_id: shopId, version: record.version, score, safe_reason: safeReason,
  }, { headers: { 'Idempotency-Key': crypto.randomUUID() } }),
  '培训结果验证失败',
)

export const getIntegrationClients = (shopId: number) => unwrapV3(
  v3Api.get<V3ApiResponse<IntegrationClient[]>>('/api/v3/integrations/clients', { params: { shop_id: shopId } }),
  '集成客户端加载失败',
)

export const createIntegrationClient = (payload: Record<string, unknown>) => unwrapV3(
  v3Api.post<V3ApiResponse<IntegrationClient>>('/api/v3/integrations/clients', payload, {
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  }),
  '集成客户端创建失败',
)

export const commandIntegrationClient = (client: IntegrationClient, shopId: number, action: 'rotate-secret' | 'revoke', reason: string) => unwrapV3(
  v3Api.post<V3ApiResponse<IntegrationClient>>(`/api/v3/integrations/clients/${client.id}/${action}`, {
    shop_id: shopId, version: client.version, reason,
  }, { headers: { 'Idempotency-Key': crypto.randomUUID() } }),
  action === 'rotate-secret' ? '密钥轮换失败' : '客户端撤销失败',
)

export interface ContentEntry {
  id: number
  contentType: string
  title: string
  summary?: string
  body?: string
  imageUrl?: string
  targetType?: string
  targetValue?: string
  sortOrder: number
  status: 'DRAFT' | 'SCHEDULED' | 'PUBLISHED' | 'OFFLINE'
  version: number
  scheduledAt?: string
  publishedAt?: string
  offlineAt?: string
  updatedAt?: string
}

export interface ContentDraftPayload {
  content_type: string
  title: string
  summary?: string
  body?: string
  image_url?: string
  target_type?: string
  target_value?: string
  sort_order: number
}

export interface AdminAccountSummary {
  id: number
  username: string
  displayName: string
  role: 'ADMIN' | 'SUPER_ADMIN'
  status: 'ACTIVE' | 'INACTIVE'
  version: number
  lastLoginAt?: string
  createdAt: string
}

export interface StaffPublicProfile {
  id: number
  name: string
  jobRole: string
  levelName?: string
  avatarUrl?: string
  bio?: string
  specialties?: string
}

export interface BookingPolicyItem {
  id: number
  name: string
  durationMinutes: number
  slotIntervalMinutes: number
  bufferBeforeMinutes: number
  bufferAfterMinutes: number
  minimumAdvanceMinutes: number
  sameDayBookingAllowed: boolean
  freeCancelMinutes: number
  rescheduleCutoffMinutes: number
  maxReschedules: number
  lateCancelPolicy: string
  lateCancelValue: number
  termsVersion: number
  bookingNotice?: string
  status: string
  updatedAt: string
}

export interface BookingScheduleFact {
  id: number
  staffId: number
  staffName: string
  scheduleDate: string
  startTime?: string
  endTime?: string
  scheduleType: 'WORK' | 'BREAK' | 'LEAVE' | 'BLOCKED' | 'STOP_BOOKING'
  remark?: string
  status: string
  version: number
}

export interface BookingScheduleRule {
  id: number
  staffId: number
  staffName: string
  dayOfWeek: number
  startTime: string
  endTime: string
  ruleType: 'WORK' | 'BREAK' | 'STOP_BOOKING'
  effectiveFrom: string
  effectiveTo?: string
  status: string
  version: number
}

export interface BookingScheduleResponse {
  from: string
  to: string
  facts: BookingScheduleFact[]
  rules: BookingScheduleRule[]
}

export type WeeklyAvailabilityStatus = 'AVAILABLE' | 'PARTIALLY_AVAILABLE' | 'FULL' | 'UNAVAILABLE'

export interface AdminWeeklySchedule {
  shopId: number
  from: string
  to: string
  maxDate: string
  dates: Array<{ date: string; weekday: string; today: boolean }>
  staff: Array<{
    id: number
    name: string
    levelName?: string
    avatarUrl?: string
    days: Array<{
      date: string
      scheduleStatus: 'WORKING' | 'REST' | 'LEAVE' | 'UNSCHEDULED'
      availabilityStatus: WeeklyAvailabilityStatus
      workPeriods: Array<{ start: string; end: string }>
      breakPeriods: Array<{ start: string; end: string }>
      appointmentCount: number
      reservedMinutes: number
      availableMinutes: number
      scheduleFacts: Array<{
        id: number
        startTime?: string
        endTime?: string
        scheduleType: BookingScheduleFact['scheduleType']
        remark?: string
        version: number
      }>
      slots: Array<{
        start: string
        end: string
        status: WeeklyAvailabilityStatus
        appointments: Array<{
          id: number
          start: string
          end: string
          customerName: string
          serviceNames?: string
        }>
      }>
    }>
  }>
}

export interface BenefitCardProduct {
  id: number
  packageCode: string
  name: string
  cardType: 'COMBO_TIMES' | 'STORED_VALUE' | 'DISCOUNT'
  description?: string
  salePrice: number
  principalAmount: number
  giftAmount: number
  discountPercent?: number
  minimumSpend: number
  maximumSavings?: number
  usageLimit?: number
  validityDays: number
  status: string
  version: number
}

export interface CouponTemplateSummary {
  id: number
  templateCode: string
  name: string
  couponType: 'THRESHOLD_REDUCTION' | 'CASH' | 'DISCOUNT' | 'SERVICE_EXPERIENCE'
  thresholdAmount: number
  benefitValue: number
  serviceId?: number
  validityDays: number
  returnOnFullRefund: boolean
  status: string
  version: number
}

export interface BenefitAdministration {
  cardProducts: BenefitCardProduct[]
  couponTemplates: CouponTemplateSummary[]
}

export interface PaymentChannelStatus {
  code: string
  configured: boolean
  maskedConfig: string
  message: string
}

export const getContentEntries = () => unwrapV3(
  v3Api.get<V3ApiResponse<ContentEntry[]>>('/api/v3/content'),
  '内容列表加载失败',
)

export const createContentEntry = (payload: ContentDraftPayload) => unwrapV3(
  v3Api.post<V3ApiResponse<{ id: number; status: string; version: number }>>('/api/v3/content', payload),
  '内容创建失败',
)

export const updateContentEntry = (entry: ContentEntry, payload: ContentDraftPayload) => unwrapV3(
  v3Api.put<V3ApiResponse<{ id: number; version: number }>>(`/api/v3/content/${entry.id}`, {
    ...payload,
    version: entry.version,
  }),
  '内容更新失败',
)

export const changeContentStatus = (
  entry: ContentEntry,
  status: ContentEntry['status'],
  scheduledAt?: string,
) => unwrapV3(
  v3Api.post<V3ApiResponse<{ id: number; status: string; version: number }>>(
    `/api/v3/content/${entry.id}/status`,
    { version: entry.version, status, scheduled_at: scheduledAt },
  ),
  '内容状态更新失败',
)

export const getAdminAccounts = () => unwrapV3(
  v3Api.get<V3ApiResponse<AdminAccountSummary[]>>('/api/v3/admin-accounts'),
  '管理员账号加载失败',
)

export const createAdminAccount = (payload: {
  username: string
  display_name: string
  password: string
  role: 'ADMIN' | 'SUPER_ADMIN'
}) => unwrapV3(
  v3Api.post<V3ApiResponse<{ id: number; role: string; version: number }>>('/api/v3/admin-accounts', payload),
  '管理员账号创建失败',
)

export const deactivateAdminAccount = (account: AdminAccountSummary) => unwrapV3(
  v3Api.post<V3ApiResponse<Record<string, unknown>>>(
    `/api/v3/admin-accounts/${account.id}/deactivate`,
    { version: account.version },
  ),
  '管理员账号停用失败',
)

export const resetAdminPassword = (account: AdminAccountSummary, password: string) => unwrapV3(
  v3Api.post<V3ApiResponse<Record<string, unknown>>>(
    `/api/v3/admin-accounts/${account.id}/reset-password`,
    { version: account.version, password },
  ),
  '管理员密码重置失败',
)

export const getStaffPublicProfiles = () => unwrapV3(
  v3Api.get<V3ApiResponse<StaffPublicProfile[]>>('/api/v3/open/staff'),
  '技师公开资料加载失败',
)

export const updateStaffPublicProfile = (profile: StaffPublicProfile) => unwrapV3(
  v3Api.put<V3ApiResponse<Record<string, unknown>>>(`/api/v3/staff/${profile.id}/public-profile`, {
    job_role: profile.jobRole,
    level_name: profile.levelName,
    avatar_url: profile.avatarUrl,
    bio: profile.bio,
  }),
  '技师公开资料更新失败',
)

export const getBookingPolicies = (shopId: number) => unwrapV3(
  v3Api.get<V3ApiResponse<BookingPolicyItem[]>>('/api/v3/booking/admin/policies', { params: { shop_id: shopId } }),
  '预约规则加载失败',
)

export const updateBookingPolicy = (shopId: number, item: BookingPolicyItem) => unwrapV3(
  v3Api.put<V3ApiResponse<Record<string, unknown>>>(
    `/api/v3/booking/admin/policies/${item.id}`,
    {
      slot_interval_minutes: item.slotIntervalMinutes,
      buffer_before_minutes: item.bufferBeforeMinutes,
      buffer_after_minutes: item.bufferAfterMinutes,
      minimum_advance_minutes: item.minimumAdvanceMinutes,
      same_day_booking_allowed: item.sameDayBookingAllowed,
      free_cancel_minutes: item.freeCancelMinutes,
      reschedule_cutoff_minutes: item.rescheduleCutoffMinutes,
      max_reschedules: item.maxReschedules,
      late_cancel_policy: item.lateCancelPolicy,
      late_cancel_value: item.lateCancelValue,
      booking_notice: item.bookingNotice,
    },
    { params: { shop_id: shopId } },
  ),
  '预约规则保存失败',
)

export const getBookingSchedules = (shopId: number, from: string, to: string) => unwrapV3(
  v3Api.get<V3ApiResponse<BookingScheduleResponse>>('/api/v3/booking/admin/schedules', {
    params: { shop_id: shopId, from, to },
  }),
  '排班加载失败',
)

export const getAdminWeeklySchedule = (shopId: number, from: string) => unwrapV3(
  v3Api.get<V3ApiResponse<AdminWeeklySchedule>>('/api/v3/booking/admin/weekly-schedule', {
    params: { shop_id: shopId, from },
  }),
  '一周排班加载失败',
)

export const addBookingScheduleFact = (shopId: number, payload: {
  staff_id: number
  schedule_date: string
  start_time?: string
  end_time?: string
  schedule_type: BookingScheduleFact['scheduleType']
  remark?: string
}) => unwrapV3(
  v3Api.post<V3ApiResponse<{ id: number }>>('/api/v3/booking/admin/schedules/facts', payload, {
    params: { shop_id: shopId },
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  }),
  '日期排班保存失败',
)

export const updateBookingScheduleFact = (
  shopId: number,
  factId: number,
  payload: {
    staff_id: number
    schedule_date: string
    start_time?: string
    end_time?: string
    schedule_type: BookingScheduleFact['scheduleType']
    remark?: string
    version: number
  },
) => unwrapV3(
  v3Api.put<V3ApiResponse<Record<string, unknown>>>(
    `/api/v3/booking/admin/schedules/facts/${factId}`,
    payload,
    {
      params: { shop_id: shopId },
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    },
  ),
  '日期排班更新失败',
)

export const deactivateBookingScheduleFact = (
  shopId: number,
  factId: number,
  version: number,
) => unwrapV3(
  v3Api.delete<V3ApiResponse<Record<string, unknown>>>(
    `/api/v3/booking/admin/schedules/facts/${factId}`,
    {
      params: { shop_id: shopId, version },
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    },
  ),
  '日期排班停用失败',
)

export const addBookingScheduleRule = (shopId: number, payload: {
  staff_id: number
  day_of_week: number
  start_time: string
  end_time: string
  rule_type: BookingScheduleRule['ruleType']
  effective_from: string
  effective_to?: string
}) => unwrapV3(
  v3Api.post<V3ApiResponse<{ id: number }>>('/api/v3/booking/admin/schedules/rules', payload, {
    params: { shop_id: shopId },
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  }),
  '周期排班保存失败',
)

export const deactivateBookingScheduleRule = (shopId: number, rule: BookingScheduleRule) => unwrapV3(
  v3Api.delete<V3ApiResponse<Record<string, unknown>>>(
    `/api/v3/booking/admin/schedules/rules/${rule.id}`,
    {
      params: { shop_id: shopId, version: rule.version },
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    },
  ),
  '排班规则停用失败',
)

export const activateBookingScheduleRule = (shopId: number, rule: BookingScheduleRule) => unwrapV3(
  v3Api.post<V3ApiResponse<Record<string, unknown>>>(
    `/api/v3/booking/admin/schedules/rules/${rule.id}/activate`,
    undefined,
    {
      params: { shop_id: shopId, version: rule.version },
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    },
  ),
  '排班规则启用失败',
)

export const holdProxyBooking = (payload: {
  shop_id: number
  service_id: number
  staff_id?: number
  assignment_mode: 'SPECIFIED' | 'UNASSIGNED'
  start_at: string
  member_id: number
  bypass_minimum_advance: boolean
  proxy_reason: string
}) => unwrapV3(
  v3Api.post<V3ApiResponse<{
    lock_token: string
    terms_version: number
    expires_at: string
    staff: { id: number; name: string }
  }>>('/api/v3/booking/locks', payload),
  '代客预约时段锁定失败',
)

export const confirmProxyBooking = (token: string, termsVersion: number, note?: string) => unwrapV3(
  v3Api.post<V3ApiResponse<{ appointment_id: number; appointment_no: string }>>(
    `/api/v3/booking/locks/${token}/confirm`,
    { terms_version: termsVersion, terms_confirmed: true, member_note: note },
  ),
  '代客预约确认失败',
)

export const processBookingWaitlistVacancy = (
  shopId: number,
  payload: { service_id: number; staff_id: number; start_at: string },
) => unwrapV3(
  v3Api.post<V3ApiResponse<{
    matched: boolean
    waitlist_id?: number
    confirmation_expires_at?: string
    appointment_created?: boolean
  }>>('/api/v3/booking/waitlist/process-vacancy', payload, { params: { shop_id: shopId } }),
  '候补匹配失败',
)

export const getBenefitAdministration = (shopId: number) => unwrapV3(
  v3Api.get<V3ApiResponse<BenefitAdministration>>('/api/v3/benefits', { params: { shop_id: shopId } }),
  '卡项与优惠数据加载失败',
)

export const getPaymentChannelStatuses = (shopId: number) => unwrapV3(
  v3Api.get<V3ApiResponse<PaymentChannelStatus[]>>('/api/v3/benefits/payment-channels', { params: { shop_id: shopId } }),
  '支付通道状态加载失败',
)

export const createBenefitCardProduct = (payload: Record<string, unknown>) => unwrapV3(
  v3Api.post<V3ApiResponse<Record<string, unknown>>>('/api/v3/benefits/card-products', payload),
  '卡产品创建失败',
)

export const issueBenefitCard = (payload: Record<string, unknown>) => unwrapV3(
  v3Api.post<V3ApiResponse<Record<string, unknown>>>('/api/v3/benefits/cards/issue', payload, {
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  }),
  '卡项发放失败',
)

export const createCouponTemplate = (payload: Record<string, unknown>) => unwrapV3(
  v3Api.post<V3ApiResponse<Record<string, unknown>>>('/api/v3/benefits/coupon-templates', payload),
  '优惠券模板创建失败',
)

export const issueMemberCoupon = (payload: Record<string, unknown>) => unwrapV3(
  v3Api.post<V3ApiResponse<Record<string, unknown>>>('/api/v3/benefits/coupons/issue', payload, {
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  }),
  '优惠券发放失败',
)

export const getPointsAdministration = (shopId: number) => unwrapV3<Record<string, any>>(
  v3Api.get<V3ApiResponse<Record<string, any>>>('/api/v3/admin/points', { params: { shop_id: shopId } }),
  '积分运营数据加载失败',
)

export const grantMemberPoints = (payload: Record<string, unknown>) => unwrapV3<Record<string, any>>(
  v3Api.post<V3ApiResponse<Record<string, any>>>('/api/v3/admin/points/grants', payload, {
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  }),
  '积分发放失败',
)

export const updatePointsRule = (ruleId: number, payload: Record<string, unknown>) => unwrapV3<Record<string, any>>(
  v3Api.put<V3ApiResponse<Record<string, any>>>(`/api/v3/admin/points/rules/${ruleId}`, payload),
  '积分规则保存失败',
)

export const updatePointsTask = (taskId: number, payload: Record<string, unknown>) => unwrapV3<Record<string, any>>(
  v3Api.put<V3ApiResponse<Record<string, any>>>(`/api/v3/admin/points/tasks/${taskId}`, payload),
  '签到任务保存失败',
)

export const sendPointsExpiryReminders = (shopId: number) => unwrapV3<{ created: number }>(
  v3Api.post<V3ApiResponse<{ created: number }>>('/api/v3/admin/points/expiry-reminders', { shop_id: shopId }),
  '积分到期提醒扫描失败',
)

export const getMallAdministration = (shopId: number) => unwrapV3<Record<string, any>>(
  v3Api.get<V3ApiResponse<Record<string, any>>>('/api/v3/admin/mall', { params: { shop_id: shopId } }),
  '商城运营数据加载失败',
)

export const createMallProduct = (payload: Record<string, unknown>) => unwrapV3<Record<string, any>>(
  v3Api.post<V3ApiResponse<Record<string, any>>>('/api/v3/admin/mall/products', payload),
  '商城商品创建失败',
)

export const updateMallProductImage = (productId: number, shopId: number, coverUrl: string) => unwrapV3<Record<string, any>>(
  v3Api.put<V3ApiResponse<Record<string, any>>>(`/api/v3/admin/mall/products/${productId}/image`, {
    shop_id: shopId,
    cover_url: coverUrl || null,
  }),
  '商品图片保存失败',
)

export function uploadAdminImage(file: File) {
  const form = new FormData()
  form.append('file', file)
  return unwrapV3(
    v3Api.post<V3ApiResponse<UploadedImage>>('/api/v3/media/images', form),
    '图片上传失败',
  )
}

export const adjustMallInventory = (payload: Record<string, unknown>) => unwrapV3<Record<string, any>>(
  v3Api.post<V3ApiResponse<Record<string, any>>>('/api/v3/admin/mall/inventory/adjustments', payload, {
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  }),
  '共享库存调整失败',
)

export const createMallPackage = (payload: Record<string, unknown>) => unwrapV3<Record<string, any>>(
  v3Api.post<V3ApiResponse<Record<string, any>>>('/api/v3/admin/mall/packages', payload),
  '包裹创建失败',
)

export const createMallCouponRule = (payload: Record<string, unknown>) => unwrapV3<Record<string, any>>(
  v3Api.post<V3ApiResponse<Record<string, any>>>('/api/v3/admin/mall/coupon-rules', payload),
  '商城券规则创建失败',
)

export const issueMallCoupon = (payload: Record<string, unknown>) => unwrapV3<Record<string, any>>(
  v3Api.post<V3ApiResponse<Record<string, any>>>('/api/v3/admin/mall/coupons/issue', payload),
  '商城券发放失败',
)

export interface LegacyImportRow {
  id: number
  sheetName: string
  rowNumber: number
  recordType: string
  sourceSystem?: string
  sourceRecordNo?: string
  maskedSubject?: string
  status: string
  matchType?: string
  matchedMemberId?: number
  resultEntityType?: string
  resultEntityId?: number
  issueCode?: string
  issueMessage?: string
}

export interface LegacyImportBatch {
  id: number
  shopId: number
  batchNo: string
  fileName: string
  fileSha256: string
  status: string
  totalRows: number
  readyRows: number
  conflictRows: number
  errorRows: number
  importedMembers: number
  importedCards: number
  startedAt?: string
  completedAt?: string
  createdAt: string
  rows?: LegacyImportRow[]
}

export interface OperationsWorkbench {
  summary: Record<string, number>
  todayAppointments: Array<Record<string, any>>
  staffSchedule: Array<Record<string, any>>
  waitlist: Array<Record<string, any>>
  shipments: Array<Record<string, any>>
  afterSales: Array<Record<string, any>>
}

export async function downloadLegacyImportTemplate() {
  const response = await v3Api.get<Blob>('/api/v3/legacy-import/template', { responseType: 'blob' })
  return response.data
}

export function preflightLegacyImport(shopId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return unwrap(
    v3Api.post<ApiResponse<LegacyImportBatch>>('/api/v3/legacy-import/preflight', form, {
      params: { shopId },
      headers: { 'X-FACE-Admin-Surface': 'DESKTOP' },
    }),
    '历史数据预检失败',
  )
}

export function getLegacyImportBatches(shopId: number) {
  return unwrap(
    v3Api.get<ApiResponse<{ records: LegacyImportBatch[]; total: number }>>('/api/v3/legacy-import/batches', {
      params: { shopId, page: 1, pageSize: 50 },
    }),
    '导入批次加载失败',
  )
}

export function getLegacyImportBatch(batchId: number) {
  return unwrap(
    v3Api.get<ApiResponse<LegacyImportBatch>>(`/api/v3/legacy-import/batches/${batchId}`),
    '导入批次详情加载失败',
  )
}

export function executeLegacyImport(batchId: number, shopId: number) {
  return unwrap(
    v3Api.post<ApiResponse<LegacyImportBatch>>(
      `/api/v3/legacy-import/batches/${batchId}/execute`,
      { shopId },
      { headers: { 'Idempotency-Key': crypto.randomUUID(), 'X-FACE-Admin-Surface': 'DESKTOP' } },
    ),
    '正式导入失败',
  )
}

export async function downloadLegacyImportIssues(batchId: number) {
  const response = await v3Api.get<Blob>(`/api/v3/legacy-import/batches/${batchId}/issues`, {
    responseType: 'blob',
  })
  return response.data
}

export function getOperationsWorkbench(shopId: number) {
  return unwrap(
    v3Api.get<ApiResponse<OperationsWorkbench>>('/api/v3/operations/workbench', { params: { shopId } }),
    '高频运营工作台加载失败',
  )
}
