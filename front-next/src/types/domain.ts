export type ClientRole = 'MEMBER' | 'BEAUTICIAN'

export interface AuthSession {
  token: string
  accountId: number
  shopId: number
  username: string
  role: ClientRole
  staffId?: number | null
  memberId?: number | null
  v3AccessToken?: string
  v3RefreshToken?: string
}

export interface ApiEnvelope<T> {
  code: number
  msg: string
  data: T
}

export interface Banner {
  id: number
  title: string
  imageUrl: string
  targetType?: string
  targetValue?: string
}

export interface Category {
  id: number
  name: string
  sortOrder: number
}

export interface ServiceItem {
  id: number
  serviceCode: string
  name: string
  subtitle?: string
  coverUrl?: string
  description?: string
  durationMinutes: number
  cleanupMinutes: number
  listPrice: number
  memberPrice?: number | null
  featured: boolean | number
  clicknum?: number
  storeupnum?: number
  categoryId: number
  categoryName: string
}

export interface Staff {
  id: number
  staffNo: string
  name: string
  jobRole: string
  levelName?: string
  avatarUrl?: string
  bio?: string
  specialties?: string
}

export interface Availability {
  schedules: Array<{
    id: number
    startTime: string
    endTime: string
    scheduleType: string
    remark?: string
  }>
  bookings: Array<{
    id: number
    appointmentNo: string
    startAt: string
    endAt: string
    status: string
  }>
}

export interface ClientProfile {
  accountId: number
  shopId: number
  shopName: string
  shopPhone?: string
  shopAddress?: string
  username: string
  roleCode: ClientRole
  staffId?: number
  memberId?: number
  memberNo?: string
  staffNo?: string
  name: string
  phone?: string
  gender?: string
  avatarUrl?: string
  points?: number
  levelName?: string
  jobRole?: string
  bio?: string
}

export interface Appointment {
  id: number
  appointmentNo: string
  shopId: number
  shopName: string
  startAt: string
  endAt: string
  status: string
  source: string
  memberNote?: string
  version: number
  memberId: number
  memberName: string
  memberPhone: string
  staffId: number
  staffName: string
  staffAvatarUrl?: string
  serviceNames?: string
  totalPrice: number
  serviceRecordId?: number | null
  serviceRecordStatus?: string | null
  serviceRecordVersion?: number | null
  confirmationId?: number | null
  confirmationStatus?: string | null
  confirmationVersion?: number | null
  orderId?: number | null
  orderStatus?: string | null
  payableAmount?: number | null
  paidAmount?: number | null
  orderVersion?: number | null
}

export interface ServiceConsumable {
  productId: number
  sku: string
  productName: string
  unitName: string
  locationId: number
  locationName: string
  quantityAvailable: number
  balanceVersion: number
}

export interface ServiceResources {
  consumables: ServiceConsumable[]
  skinTypes: string[]
}

export interface ServiceRecordDetail {
  id: number
  recordNo: string
  shopId: number
  appointmentId?: number
  memberId: number
  memberName: string
  staffId: number
  staffName: string
  status: string
  version: number
  serviceSummary?: string
  nextVisitRecommendation?: string
  skinType?: string
  concerns?: string[]
  observations?: string
  homeCareAdvice?: string
  nextRecommendedAt?: string
  items?: Array<{
    id: number
    serviceId: number
    serviceName: string
  }>
  consumptions?: Array<{
    id: number
    productName: string
    locationName: string
    quantity: number
    unitName: string
  }>
}

export interface PackageInstanceItem {
  id: number
  serviceId: number
  serviceName: string
  totalQuantity: number
  remainingQuantity: number
}

export interface PackageInstance {
  id: number
  instanceNo: string
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
  items: PackageInstanceItem[]
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

export interface MemberRefund {
  id: number
  shopId: number
  orderId: number
  orderNo: string
  paymentId: number
  paymentMethod: string
  refundNo: string
  amount: number
  reason: string
  status: 'PENDING' | 'APPROVED' | 'PROCESSING' | 'SUCCESS' | 'REJECTED' | 'FAILED'
  version: number
  decisionNote?: string
  executionMode?: string
  channelStatus?: string
  failureCode?: string
  reviewedAt?: string
  refundedAt?: string
  createdAt: string
}

export interface CustomerConfirmation {
  id: number
  shopId: number
  appointmentId?: number
  serviceRecordId: number
  confirmationType: string
  status: 'PENDING' | 'CONFIRMED' | 'SYSTEM_AUTO_CONFIRMED' | 'REJECTED'
  rejectReason?: string
  dueAt?: string
  finalizationSource?: 'MEMBER' | 'SYSTEM' | 'DISPUTE'
  afterSaleCaseId?: number
  version: number
  actedAt?: string
  createdAt: string
  recordNo: string
  serviceNames?: string
  serviceSummary?: string
  nextVisitRecommendation?: string
  completedAt?: string
  staffName: string
  skinType?: string
  concerns?: string
  observations?: string
  homeCareAdvice?: string
  nextRecommendedAt?: string
}

export interface ClientDashboard {
  profile: ClientProfile
  summary: Record<string, number>
  appointments: Appointment[]
  todaySchedule?: Array<{
    id: number
    scheduleDate: string
    startTime: string
    endTime: string
    scheduleType: string
    remark?: string
  }>
}

export interface NotificationItem {
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

export interface NotificationPage {
  records: NotificationItem[]
  total: number
  unreadCount: number
  page: number
  pageSize: number
  asOf: string
}

export interface AfterSaleCase {
  id: number
  caseNo: string
  memberId: number
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
  customerConfirmedAt?: string
  reopenCount?: number
  assigneeAccountId?: number
  refundId?: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface ServiceReview {
  id: number
  shopId: number
  serviceRecordId: number
  confirmationId: number
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
  afterSaleCaseId?: number
  deletedAt?: string
  version: number
}

export interface MallReturnRequest {
  id: number
  mallOrderId: number
  orderNo?: string
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

export interface CommissionEntry {
  id: number
  entryNo: string
  staffId: number
  staffName: string
  entryType: 'ACCRUAL' | 'REVERSAL' | 'ADJUSTMENT'
  baseAmount: number
  amount: number
  status: 'PENDING' | 'FROZEN' | 'SETTLED' | 'REVERSED'
  version: number
  businessNo: string
  createdAt: string
}

export interface TechnicianCommissionSummary {
  staffId: number
  pendingAmount: number
  frozenAmount: number
  settledAmount: number
  paidAmount: number
}

export interface ApprovalItem {
  id: number
  approvalNo: string
  businessType: string
  businessId: number
  approvalType: string
  safeSummary: string
  requesterAccountId: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED'
  version: number
  createdAt: string
}

export interface MarketingConsentRecord {
  channel: 'IN_APP' | 'SMS' | 'EMAIL' | 'WECHAT'
  status: 'GRANTED' | 'REVOKED'
  version: number
  available: boolean
  consentTextVersion: string
  consentTextSha256: string
  grantedAt?: string
  revokedAt?: string
  updatedAt?: string
}

export interface MarketingConsentSettings {
  consentText: string
  records: MarketingConsentRecord[]
}

export interface TrainingRecord {
  id: number
  shopId: number
  recordNo: string
  courseTitle: string
  courseSummary: string
  courseRevision: number
  passScore: number
  status: 'ASSIGNED' | 'IN_PROGRESS' | 'SUBMITTED' | 'PASSED' | 'FAILED' | 'EXPIRED' | 'CANCELLED'
  dueAt?: string
  score?: number
  certificateNo?: string
  validUntil?: string
  version: number
}
