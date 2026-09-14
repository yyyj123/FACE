import axios from 'axios'
import type {
  ApiEnvelope,
  AfterSaleCase,
  ServiceReview,
  MallReturnRequest,
  ApprovalItem,
  Appointment,
  AuthSession,
  Availability,
  Banner,
  Category,
  ClientDashboard,
  ClientProfile,
  CommissionEntry,
  CustomerConfirmation,
  MemberAssetAccount,
  MemberRefund,
  MarketingConsentSettings,
  NotificationPage,
  PackageInstance,
  ServiceItem,
  ServiceRecordDetail,
  ServiceResources,
  Staff,
  TechnicianCommissionSummary,
} from '../types/domain'
import { toRequestError } from './errors'
import { PORTAL_SESSION_KEY } from '../config/portal'

const SESSION_KEY = PORTAL_SESSION_KEY

interface V3Envelope<T> {
  code: string
  message: string
  data: T
}

interface IdentityResponse {
  account_id: number
  member_id: number
  shop_id: number
  display_name: string
  role: 'MEMBER'
  access_token: string
  refresh_token: string
}

export interface HomeContent {
  shop: { id: number; name: string; phone: string; address: string; business_hours: string }
  content: Array<{
    id: number
    contentType: string
    title: string
    summary?: string
    body?: string
    imageUrl?: string
    targetType?: string
    targetValue?: string
    sortOrder: number
  }>
}

export interface BookingAvailability {
  shop_id: number
  service: {
    id: number
    name: string
    duration_minutes: number
    slot_interval_minutes: number
    buffer_before_minutes: number
    buffer_after_minutes: number
    minimum_advance_minutes: number
    same_day_booking_allowed: boolean
    free_cancel_minutes: number
    reschedule_cutoff_minutes: number
    max_reschedules: number
    late_cancel_policy: string
    late_cancel_value: number
    terms_version: string
    booking_notice?: string
  }
  assignment_modes: Array<'SPECIFIED' | 'UNASSIGNED'>
  from_date: string
  to_date: string
  max_booking_date: string
  generated_at: string
  days: Array<{
    date: string
    slots: Array<{
      start_at: string
      staff: Array<{ id: number; name: string; duration_minutes: number }>
    }>
  }>
}

export type WeeklyAvailabilityStatus =
  | 'AVAILABLE'
  | 'PARTIALLY_AVAILABLE'
  | 'FULL'
  | 'UNAVAILABLE'

export interface PublicWeeklySchedule {
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
      slots: Array<{ start: string; end: string; status: WeeklyAvailabilityStatus }>
    }>
  }>
}

export interface BookingLock {
  id: number
  lock_token: string
  status: 'HELD'
  staff: { id: number; name: string }
  service: { id: number; name: string }
  start_at: string
  end_at: string
  occupied_start_at: string
  occupied_end_at: string
  expires_at: string
  lock_minutes: number
  terms_version: number
  rule_snapshot: string
}

export interface BookingWaitlistItem {
  id: number
  serviceId: number
  serviceName: string
  requestedStaffId?: number
  requestedStaffName?: string
  matchedStaffId?: number
  matchedStaffName?: string
  dateFrom: string
  dateTo: string
  timeFrom: string
  timeTo: string
  status: string
  confirmationExpiresAt?: string
  lockToken?: string
  matchedStartAt?: string
  termsVersion?: number
}

export interface CheckoutDiscountOption {
  selectionType: 'NONE' | 'ACTIVITY' | 'COUPON' | 'DISCOUNT_CARD' | 'POINTS'
  referenceId?: number
  name: string
  discountAmount: number
  payableAmount: number
  recommended: boolean
}

export interface CheckoutQuote {
  targetType: 'BOOKING' | 'CARD_PURCHASE'
  shopId: number
  memberId: number
  item: { id: number; name: string; subtotalAmount: number }
  discountOptions: CheckoutDiscountOption[]
  comboCards: Array<{
    id: number
    name: string
    instanceNo: string
    remainingQuantity: number
    frozenQuantity: number
    availableQuantity: number
    validUntil: string
  }>
  defaultSelection: 'NONE'
  points: { available: boolean; reason?: string; pointsUsed?: number; discountAmount?: number }
  paymentChannels: Array<{ code: string; configured: boolean; message: string }>
  expiresAt?: string
}

export interface CheckoutResult {
  orderId: number
  orderNo: string
  orderStatus: 'UNPAID' | 'PAID'
  subtotalAmount: number
  discountAmount: number
  payableAmount: number
  payment: {
    id: number
    paymentNo: string
    paymentMethod: string
    channelCode?: string
    channelStatus?: string
    channelRequestNo?: string
    amount: number
    status: 'PENDING' | 'SUCCESS'
  }
  fulfillment?: { appointmentId?: number; status?: string }
}

export interface ClientBenefitCard {
  id: number
  instanceNo: string
  cardType: 'COMBO_TIMES' | 'STORED_VALUE' | 'DISCOUNT'
  name: string
  sourceType: string
  validFrom: string
  validUntil: string
  totalQuantity: number
  remainingQuantity: number
  frozenQuantity: number
  principalRemaining?: number
  giftRemaining?: number
  principalFrozen?: number
  giftFrozen?: number
  discountPercent?: number
  status: string
}

export interface ClientCoupon {
  id: number
  couponNo: string
  name: string
  couponType: string
  thresholdAmount: number
  benefitValue: number
  validUntil: string
  sourceType: string
  status: string
}

export interface PurchasableCard {
  id: number
  packageCode: string
  name: string
  cardType: 'COMBO_TIMES' | 'STORED_VALUE' | 'DISCOUNT'
  description?: string
  salePrice: number
  principalAmount: number
  giftAmount: number
  discountPercent?: number
  validityDays: number
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_CLIENT_API_BASE || '/face-next/api/v2/client',
  timeout: 12000,
})

export const v3Http = axios.create({
  baseURL: import.meta.env.VITE_V3_API_BASE || '/face-next/api/v3',
  timeout: 12000,
})

http.interceptors.request.use((config) => {
  const stored = localStorage.getItem(SESSION_KEY)
  if (stored) {
    const session = JSON.parse(stored) as AuthSession
    config.headers.Authorization = `Bearer ${session.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiEnvelope<unknown>
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return response
  },
  (reason) => Promise.reject(toRequestError(reason)),
)

v3Http.interceptors.request.use((config) => {
  const stored = localStorage.getItem(SESSION_KEY)
  if (stored) {
    const session = JSON.parse(stored) as AuthSession
    if (session.v3AccessToken) {
      config.headers.Authorization = `Bearer ${session.v3AccessToken}`
    }
  }
  return config
})

v3Http.interceptors.response.use(
  (response) => {
    const body = response.data as V3Envelope<unknown>
    if (body && body.code !== 'SUCCESS') {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response
  },
  (reason) => Promise.reject(toRequestError(reason)),
)

async function data<T>(request: Promise<{ data: ApiEnvelope<T> }>) {
  return (await request).data.data
}

async function v3Data<T>(request: Promise<{ data: V3Envelope<T> }>) {
  return (await request).data.data
}

function identitySession(identity: IdentityResponse, phone: string): AuthSession {
  return {
    token: identity.access_token,
    accountId: identity.account_id,
    shopId: identity.shop_id,
    username: phone,
    role: identity.role,
    memberId: identity.member_id,
    v3AccessToken: identity.access_token,
    v3RefreshToken: identity.refresh_token,
  }
}

export const api = {
  login: async (payload: { phone: string; password: string }) => identitySession(
    await v3Data<IdentityResponse>(
      v3Http.post('/client/identity/password-login', payload),
    ),
    payload.phone,
  ),
  smsLogin: async (payload: { phone: string; code: string }) => identitySession(
    await v3Data<IdentityResponse>(v3Http.post('/client/identity/sms-login', payload)),
    payload.phone,
  ),
  register: async (payload: { phone: string; code: string; password: string; name: string }) =>
    identitySession(
      await v3Data<IdentityResponse>(v3Http.post('/client/identity/register', payload)),
      payload.phone,
    ),
  requestSms: (payload: { phone: string; purpose: 'REGISTER_LOGIN' | 'PASSWORD_RESET' }) =>
    v3Data<{ message: string; expires_in_seconds: number; retry_after_seconds: number; demo_code?: string }>(
      v3Http.post('/client/identity/sms/request', payload),
    ),
  resetPassword: (payload: { phone: string; code: string; password: string }) =>
    v3Data<{ reset: boolean }>(v3Http.post('/client/identity/password-reset', payload)),
  homeContent: () => v3Data<HomeContent>(v3Http.get('/open/content/home')),
  banners: () => data<Banner[]>(http.get('/public/banners')),
  categories: () => data<Category[]>(http.get('/public/service-categories')),
  services: (params?: { categoryId?: number; featured?: boolean; sort?: string }) =>
    data<ServiceItem[]>(http.get('/public/services', { params })),
  service: (id: number) => data<ServiceItem>(http.get(`/public/services/${id}`)),
  recordServiceView: (id: number) => data<boolean>(http.post(`/public/services/${id}/view`)),
  staff: (serviceId?: number) =>
    data<Staff[]>(http.get('/public/staff', { params: { serviceId } })),
  availability: (staffId: number, date: string) =>
    data<Availability>(http.get('/public/availability', { params: { staffId, date } })),
  bookingAvailability: (serviceId: number, staffId: number | undefined, fromDate: string) =>
    v3Data<BookingAvailability>(v3Http.get('/open/booking/availability', {
      params: { service_id: serviceId, staff_id: staffId, from_date: fromDate },
    })),
  staffWeeklySchedule: (fromDate: string, shopId?: number) =>
    v3Data<PublicWeeklySchedule>(v3Http.get('/open/staff-weekly-schedule', {
      params: { from_date: fromDate, shop_id: shopId },
    })),
  holdBooking: (payload: {
    shop_id: number
    service_id: number
    staff_id?: number
    assignment_mode: 'SPECIFIED' | 'UNASSIGNED'
    start_at: string
  }) => v3Data<BookingLock>(v3Http.post('/booking/locks', payload)),
  confirmBooking: (token: string, payload: {
    terms_version: number
    terms_confirmed: boolean
    member_note?: string
  }) => v3Data<{ appointment_id: number; appointment_no: string; status: string }>(
    v3Http.post(`/booking/locks/${token}/confirm`, payload),
  ),
  checkoutQuote: (payload: { lock_token?: string; package_product_id?: number }) =>
    v3Data<CheckoutQuote>(v3Http.post('/checkout/quotes', payload)),
  createCheckout: (payload: {
    lock_token?: string
    package_product_id?: number
    selection_type: string
    selection_reference_id?: number
    payment_method: string
    stored_value_card_id?: number
    combo_card_id?: number
    member_note?: string
  }, idempotencyKey: string) => v3Data<CheckoutResult>(
    v3Http.post('/checkouts', payload, { headers: { 'Idempotency-Key': idempotencyKey } }),
  ),
  checkout: (orderId: number) => v3Data<Record<string, unknown>>(
    v3Http.get(`/checkouts/${orderId}`),
  ),
  clientBenefits: () => v3Data<{ cards: ClientBenefitCard[]; coupons: ClientCoupon[] }>(
    v3Http.get('/client/benefits'),
  ),
  purchasableCards: () => v3Data<PurchasableCard[]>(v3Http.get('/client/card-products')),
  releaseBookingLock: (token: string, reason = 'MEMBER_RELEASED') =>
    v3Data<{ released: boolean }>(v3Http.delete(`/booking/locks/${token}`, { params: { reason } })),
  joinBookingWaitlist: (payload: {
    shop_id: number
    service_id: number
    requested_staff_id?: number
    date_from: string
    date_to: string
    time_from: string
    time_to: string
    flexibility_minutes: number
    accept_other_staff: boolean
  }) => v3Data<{ id: number; status: string }>(v3Http.post('/booking/waitlist', payload)),
  bookingWaitlist: () => v3Data<BookingWaitlistItem[]>(v3Http.get('/booking/waitlist/mine')),
  cancelBookingWaitlist: (id: number) =>
    v3Data<{ id: number; status: string }>(v3Http.delete(`/booking/waitlist/${id}`)),
  confirmBookingWaitlist: (id: number, payload: {
    terms_version: number
    terms_confirmed: boolean
    member_note?: string
  }) => v3Data<{ appointment_id: number; appointment_no: string }>(
    v3Http.post(`/booking/waitlist/${id}/confirm`, payload),
  ),
  createAppointment: (payload: {
    shopId: number
    memberId: number
    staffId: number
    serviceIds: number[]
    startAt: string
    source: string
    memberNote?: string
  }) => data<{ id: number; appointmentNo: string; startAt: string; endAt: string; status: string }>(
    http.post('/appointments', payload),
  ),
  me: () => data<ClientProfile>(http.get('/me')),
  updateMe: (payload: Partial<ClientProfile>) => data<ClientProfile>(http.put('/me', payload)),
  dashboard: () => data<ClientDashboard>(http.get('/dashboard')),
  appointments: (params?: { status?: string; date?: string }) =>
    data<Appointment[]>(http.get('/appointments', { params })),
  cancelAppointment: (id: number, version: number) =>
    data<null>(http.post(`/appointments/${id}/cancel`, { version })),
  updateAppointmentStatus: (id: number, status: string, version: number) =>
    data<null>(http.post(`/appointments/${id}/status`, { status, version })),
  serviceResources: (shopId: number) =>
    data<ServiceResources>(http.get('/service-records/resources', { params: { shopId } })),
  serviceRecord: (id: number, shopId: number) =>
    data<ServiceRecordDetail>(http.get(`/service-records/${id}`, { params: { shopId } })),
  startService: (payload: {
    shopId: number
    appointmentId: number
    appointmentVersion: number
  }) => data<ServiceRecordDetail>(http.post('/service-records/start', payload)),
  completeService: (id: number, payload: {
    shopId: number
    version: number
    serviceSummary: string
    nextVisitRecommendation?: string
    skinType?: string
    concerns: string[]
    observations: string
    homeCareAdvice?: string
    nextRecommendedAt?: string
    consumptions: Array<{
      locationId: number
      productId: number
      quantity: number
      balanceVersion: number
    }>
    idempotencyKey: string
  }) => data<ServiceRecordDetail>(http.post(`/service-records/${id}/complete`, payload)),
  confirmations: () => data<CustomerConfirmation[]>(http.get('/confirmations')),
  actOnConfirmation: (
    id: number,
    payload: { action: 'CONFIRMED' | 'REJECTED'; reason?: string; version: number; idempotencyKey: string },
  ) => data<CustomerConfirmation>(http.post(`/confirmations/${id}/action`, payload)),
  memberPackages: (memberId: number, shopId: number) =>
    v3Data<PackageInstance[]>(
      v3Http.get(`/members/${memberId}/packages`, { params: { shop_id: shopId } }),
    ),
  memberAccounts: (memberId: number, shopId: number) =>
    v3Data<MemberAssetAccount[]>(
      v3Http.get(`/members/${memberId}/accounts`, { params: { shop_id: shopId } }),
    ),
  memberRefunds: (memberId: number, shopId: number) =>
    v3Data<MemberRefund[]>(
      v3Http.get(`/members/${memberId}/refunds`, { params: { shop_id: shopId } }),
    ),
  packageLedger: (instanceId: number, shopId: number) =>
    v3Data<Array<Record<string, unknown>>>(
      v3Http.get(`/package-instances/${instanceId}/ledger`, { params: { shop_id: shopId } }),
    ),
  accountLedger: (accountId: number, shopId: number) =>
    v3Data<Array<Record<string, unknown>>>(
      v3Http.get(`/member-accounts/${accountId}/ledger`, { params: { shop_id: shopId } }),
    ),
  notifications: (status: 'ALL' | 'UNREAD' | 'READ' = 'ALL') =>
    v3Data<NotificationPage>(
      v3Http.get('/notifications', { params: { status, page: 1, page_size: 50 } }),
    ),
  markNotificationRead: (notificationId: number, version: number) =>
    v3Data<Record<string, unknown>>(
      v3Http.post(
        `/notifications/${notificationId}/read`,
        { version },
        { headers: { 'Idempotency-Key': crypto.randomUUID() } },
      ),
    ),
  markAllNotificationsRead: () =>
    v3Data<{ changed?: number; unreadCount: number }>(
      v3Http.post(
        '/notifications/read-all',
        {},
        { headers: { 'Idempotency-Key': crypto.randomUUID() } },
      ),
    ),
  marketingConsents: () =>
    v3Data<MarketingConsentSettings>(v3Http.get('/me/marketing-consents')),
  updateMarketingConsent: (
    channel: string,
    status: 'GRANTED' | 'REVOKED',
    version: number,
  ) => v3Data<Record<string, unknown>>(
    v3Http.put(
      `/me/marketing-consents/${channel}`,
      { status, version },
      { headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ),
  ),
  afterSaleCases: (shopId: number, status?: string) =>
    v3Data<{ records: AfterSaleCase[]; page: number; pageSize: number }>(
      v3Http.get('/after-sales/cases', {
        params: { shop_id: shopId, status, page: 1, page_size: 50 },
      }),
    ),
  createAfterSaleCase: (payload: {
    shop_id: number
    member_id: number
    order_id?: number
    service_record_id?: number
    category: string
    priority: string
    summary: string
  }) =>
    v3Data<AfterSaleCase>(
      v3Http.post('/after-sales/cases', payload, {
        headers: { 'Idempotency-Key': crypto.randomUUID() },
      }),
    ),
  reopenAfterSaleCase: (
    caseId: number,
    payload: { shop_id: number; version: number; reason: string },
  ) =>
    v3Data<AfterSaleCase>(
      v3Http.post(`/after-sales/cases/${caseId}/reopen`, payload, {
        headers: { 'Idempotency-Key': crypto.randomUUID() },
      }),
    ),
  myReviews: () => v3Data<ServiceReview[]>(v3Http.get('/reviews/mine')),
  saveReview: (payload: {
    service_record_id: number
    version?: number
    staff_rating: number
    effect_rating: number
    environment_rating: number
    visibility: 'PUBLIC' | 'SHOP_ONLY'
    content?: string
    wants_contact: boolean
  }, reviewId?: number) => v3Data<ServiceReview>(
    reviewId
      ? v3Http.put(`/reviews/${reviewId}`, payload, { headers: { 'Idempotency-Key': crypto.randomUUID() } })
      : v3Http.post('/reviews', payload, { headers: { 'Idempotency-Key': crypto.randomUUID() } }),
  ),
  deleteReview: (reviewId: number, version: number) => v3Data<ServiceReview>(
    v3Http.delete(`/reviews/${reviewId}`, {
      params: { version }, headers: { 'Idempotency-Key': crypto.randomUUID() },
    }),
  ),
  respondAfterSale: (caseId: number, payload: {
    shop_id: number
    version: number
    accepted: boolean
    reason?: string
  }) => v3Data<AfterSaleCase>(
    v3Http.post(`/after-sales/cases/${caseId}/customer-response`, payload, {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    }),
  ),
  mallReturns: (shopId: number) => v3Data<MallReturnRequest[]>(
    v3Http.get('/mall/returns', { params: { shop_id: shopId } }),
  ),
  createMallReturn: (payload: {
    shop_id: number
    mall_order_id: number
    reason_code: string
    reason_detail: string
    items: Array<{ order_item_id: number; quantity: number }>
  }) => v3Data<MallReturnRequest>(
    v3Http.post('/mall/returns', payload, {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    }),
  ),
  shipMallReturn: (returnId: number, payload: {
    shop_id: number
    version: number
    tracking_no: string
  }) => v3Data<MallReturnRequest>(
    v3Http.post(`/mall/returns/${returnId}/ship`, payload, {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    }),
  ),
  technicianCommissionSummary: (shopId: number) =>
    v3Data<TechnicianCommissionSummary>(
      v3Http.get('/commission/technician/commission-summary', {
        params: { shop_id: shopId },
      }),
    ),
  commissionEntries: (shopId: number) =>
    v3Data<{ records: CommissionEntry[]; total: number; page: number; pageSize: number }>(
      v3Http.get('/commission/entries', {
        params: { shop_id: shopId, page: 1, page_size: 50 },
      }),
    ),
  approvals: (shopId: number) =>
    v3Data<{ records: ApprovalItem[]; page: number; pageSize: number }>(
      v3Http.get('/approvals', {
        params: { shop_id: shopId, page: 1, page_size: 50 },
      }),
    ),
  points: () => v3Data<Record<string, any>>(v3Http.get('/client/points')),
  pointsCheckin: () => v3Data<Record<string, any>>(
    v3Http.post('/client/points/check-in', {}, {
      headers: { 'Idempotency-Key': `checkin-${new Date().toISOString().slice(0, 10)}` },
    }),
  ),
  mallCatalog: () => v3Data<Record<string, any>>(v3Http.get('/client/mall')),
  mallCart: () => v3Data<Record<string, any>>(v3Http.get('/client/mall/cart')),
  putMallCart: (payload: {
    sku_id: number
    purchase_mode: 'CASH' | 'POINTS' | 'COMBINATION'
    quantity: number
    delivery_mode: 'DELIVERY' | 'PICKUP' | 'DIGITAL'
    pickup_shop_id?: number
  }) => v3Data<Record<string, any>>(v3Http.post('/client/mall/cart', payload)),
  removeMallCartItem: (itemId: number) => v3Data<Record<string, any>>(
    v3Http.delete(`/client/mall/cart/${itemId}`),
  ),
  updateMallCartItem: (itemId: number, quantity: number) => v3Data<Record<string, any>>(
    v3Http.put(`/client/mall/cart/${itemId}`, { quantity }),
  ),
  createMallCheckout: (payload: {
    payment_method: string
    mall_coupon_id?: number
    address?: Record<string, unknown>
  }) => v3Data<Record<string, any>>(
    v3Http.post('/client/mall/checkouts', payload, {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    }),
  ),
  mallOrder: (orderId: number) => v3Data<Record<string, any>>(
    v3Http.get(`/client/mall/orders/${orderId}`),
  ),
  receiveMallPackage: (packageId: number) => v3Data<Record<string, any>>(
    v3Http.post(`/client/mall/packages/${packageId}/receive`, {}),
  ),
  writeOffPackage: (
    instanceId: number,
    payload: {
      shop_id: number
      service_record_id: number
      service_id: number
      quantity: number
      version: number
      reason?: string
    },
  ) => v3Data<Record<string, unknown>>(
    v3Http.post(`/package-instances/${instanceId}/write-offs`, payload, {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    }),
  ),
  trainingRecords: () => v3Data<import('../types/domain').TrainingRecord[]>(
    v3Http.get('/training/me'),
  ),
  trainingAction: (
    recordId: number,
    action: 'start' | 'submit',
    version: number,
    evidenceSummary?: string,
  ) => v3Data<import('../types/domain').TrainingRecord>(
    v3Http.post(`/training/me/${recordId}/${action}`, {
      version,
      evidence_summary: evidenceSummary,
    }, { headers: { 'Idempotency-Key': crypto.randomUUID() } }),
  ),
}

export { SESSION_KEY }
