// ── Users / auth ─────────────────────────────────────────────────────────────
export type Role = 'ADMIN' | 'ENGINEER' | 'CUSTOMER_ADMIN' | 'CUSTOMER'
export type UserStatus = 'INVITED' | 'ACTIVE' | 'INACTIVE' | 'IS_DELETED'

export interface User {
  id: number
  userCode: string
  email: string
  firstName: string
  lastName: string
  phone: string
  role: Role
  status: UserStatus
  companyId?: number
  companyName?: string
  photoUrl?: string
  notifications?: NotificationPreferences
  /** Engineers/admins only (admin listing): companies they look after. */
  assignedCompanies?: { id: number; name: string }[]
  /** Additive RBAC: roles granted on top of `role`, e.g. an ENGINEER also granted ADMIN. */
  additionalRoles?: Role[]
  createdAt: string
  updatedAt: string
}

export interface NotificationPreferences {
  email: boolean
  browser: boolean
  onAssign: boolean
  onReply: boolean
  onResolve: boolean
}

export interface UpdateProfileRequest { firstName: string; lastName: string; phone: string }
export interface InviteTeamMemberRequest { firstName: string; lastName: string; email: string; phone: string; admin: boolean }

export interface CreateUserRequest {
  firstName: string
  lastName: string
  email: string
  phone: string
  role: Role
  companyId?: number
}

// ── Subscription / plans ─────────────────────────────────────────────────────
export type PlanType = 'PREDEFINED' | 'CUSTOM'
export type PlanStatus = 'ACTIVE' | 'INACTIVE'
export type SubscriptionStatus = 'ACTIVE' | 'INACTIVE' | 'EXPIRED'
/** Backend SLA levels (NORMAL is the doc's wording; MEDIUM/LOW arrive with the ticket module). */
export type SlaPriority = 'CRITICAL' | 'HIGH' | 'NORMAL' | 'MEDIUM' | 'LOW'

export interface Feature { id: number; code: string; name: string; enabled: boolean | null }
export interface Limits { maxUsers: number; monthlyTicketLimit: number; storageLimitMb: number }
export interface SlaRow { priority: SlaPriority; responseTimeMinutes: number; resolutionTimeMinutes: number }

export interface Plan {
  id: number
  planName: string
  description?: string
  planType: PlanType
  status: PlanStatus
  features: Feature[]
  limits: Limits
  sla: SlaRow[]
  createdAt: string
  updatedAt: string
}

export interface PlanRequest {
  planName: string
  description?: string
  status: PlanStatus
  featureCodes: string[]
  limits: Limits
  sla: SlaRow[]
}

export interface CompanySubscriptionRequest {
  subscriptionType: PlanType
  planId?: number
  featureCodes?: string[]
  limits?: Limits
  sla?: SlaRow[]
}

export interface CompanySubscription {
  companyId: number
  companyName: string
  subscriptionType: PlanType
  status: SubscriptionStatus
  planId?: number
  planName?: string
  locked: boolean
  features: Feature[]
  limits: Limits
  sla: SlaRow[]
  usage: { activeUsers: number }
  createdDate: string
  updatedAt: string
}

export interface SubscriptionDefaults {
  customDefaults: Limits
  customDefaultSla: SlaRow[]
  caps: Limits
  slaResponseMinutesCap: number
  slaResolutionMinutesCap: number
}

// ── Companies ────────────────────────────────────────────────────────────────
export interface Company {
  id: number
  name: string
  address?: string
  contactEmail?: string
  contactPhone?: string
  logoUrl?: string
  createdAt: string
  updatedAt: string
  subscription?: CompanySubscription
  // list-view summary
  planName?: string
  subscriptionType?: PlanType
  activeUsers?: number
  maxUsers?: number
  engineers?: User[]
  companyAdmin?: User
  /** Engineer/admin notified on every new ticket for this company; undefined when none set. */
  mainEngineer?: User
}

export interface CreateCompanyRequest {
  name: string
  address?: string
  contactEmail?: string
  contactPhone?: string
  subscription: CompanySubscriptionRequest
  mainEngineerId?: number
}
export type UpdateCompanyRequest = Omit<CreateCompanyRequest, 'subscription'>

// ── Tickets ──────────────────────────────────────────────────────────────────
export type TicketPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type TicketCategory = 'TECHNICAL' | 'BILLING' | 'ACCOUNT' | 'FEATURE'

export interface Person { id: number; firstName: string; lastName: string; photoUrl?: string }

export interface TicketSummary {
  id: number
  title: string
  category: TicketCategory
  priority: TicketPriority
  status: TicketStatus
  companyId: number
  companyName: string
  createdBy: Person
  assignee?: Person
  responseDueAt?: string
  resolutionDueAt?: string
  firstResponseAt?: string
  resolvedAt?: string
  closedAt?: string
  createdAt: string
  updatedAt: string
  commentCount: number
}

export interface TicketComment { id: number; author?: Person; body: string; internal: boolean; system: boolean; createdAt: string }

// -- Notifications --
export type NotificationType = 'TICKET_ASSIGNED_TO_YOU' | 'TICKET_ASSIGNED_NOTICE' | 'TICKET_REPLY' | 'TICKET_RESOLVED' | 'TICKET_CLOSED' | 'TICKET_CREATED'
export interface AppNotification { id: number; type: NotificationType; title: string; body: string; ticketId?: number; read: boolean; createdAt: string }
export interface TicketAttachment { id: string; name: string; contentType: string; sizeBytes: number; url: string; createdAt: string }
export interface Ticket extends TicketSummary { description: string; stepsToReproduce?: string; comments: TicketComment[]; attachments: TicketAttachment[] }

export interface CreateTicketRequest {
  title: string
  description: string
  stepsToReproduce?: string
  category: TicketCategory
  priority: TicketPriority
  /** Staff only; customers always create for their own company. */
  companyId?: number
}
