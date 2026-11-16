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
  /** Role Management: admin-created custom roles (with their privileges) granted to this user. */
  customRoles?: CustomRoleRef[]
  /** Feature codes the user's company subscription enables. Customers only — staff aren't plan-gated. */
  companyFeatures?: FeatureCode[]
  createdAt: string
  updatedAt: string
}

/** The plan feature catalog (app-config.properties app.subscription.features[*].code). */
export type FeatureCode =
  | 'TICKET_MANAGEMENT' | 'SLA_MANAGEMENT' | 'AUTOMATION_RULES' | 'KNOWLEDGE_BASE'
  | 'REPORTING' | 'AUDIT_LOGS' | 'ASSET_MANAGEMENT' | 'AI_FEATURES'

// ── Role Management (custom roles + privileges) ─────────────────────────────
export type Privilege =
  | 'MANAGE_USERS' | 'MANAGE_COMPANIES' | 'MANAGE_PLANS' | 'MANAGE_ROLES'
  | 'MANAGE_CONTRACTS' | 'VIEW_AUDIT_LOG' | 'MANAGE_TEMPLATES' | 'MANAGE_ASSETS'

export interface CustomRoleRef {
  id: number
  name: string
  privileges: Privilege[]
}

export interface RoleDef {
  id: number
  name: string
  description?: string
  custom: boolean
  privileges: Privilege[]
}

export interface RoleRequest {
  name: string
  description?: string
  privileges: Privilege[]
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
  usage: { activeUsers: number; storageUsedBytes: number; ticketsThisMonth: number }
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
  /** SLA clock: when true, deadlines only advance inside businessHours. */
  businessHoursEnabled?: boolean
  /** Always seven rows, Monday first, when the API returns a single company. */
  businessHours?: BusinessHours[]
}

export interface CreateCompanyRequest {
  name: string
  address?: string
  contactEmail?: string
  contactPhone?: string
  subscription: CompanySubscriptionRequest
  mainEngineerId?: number
  businessHoursEnabled?: boolean
  businessHours?: BusinessHours[]
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
  responseBreachedAt?: string
  resolutionBreachedAt?: string
  responseSla?: SlaState
  resolutionSla?: SlaState
}

/** How a ticket stands against one of its SLA clocks. Mirrors the backend SlaState enum. */
export type SlaState = 'NONE' | 'MET' | 'ON_TRACK' | 'AT_RISK' | 'BREACHED'

// ── Business hours (SLA clock) ───────────────────────────────────────────────
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface BusinessHours {
  dayOfWeek: DayOfWeek
  /** "HH:mm" */
  openTime: string
  closeTime: string
  closed: boolean
}

// ── Reporting ────────────────────────────────────────────────────────────────
export interface ReportBucket { period: string; created: number; resolved: number }
export interface ReportSlice { key: string; label: string; count: number }

export interface Report {
  from: string
  to: string
  granularity: 'DAY' | 'MONTH'
  companyName?: string
  totals: {
    created: number
    resolved: number
    open: number
    inProgress: number
    closed: number
    /** WORKING hours (business calendar), matching how SLA deadlines are set — not elapsed time. */
    avgResolutionHours: number
    /** WORKING hours, same basis as avgResolutionHours. */
    avgFirstResponseHours: number
  }
  series: ReportBucket[]
  byStatus: ReportSlice[]
  byPriority: ReportSlice[]
  byCategory: ReportSlice[]
  /** All figures are over tickets CREATED in the period — the same population as `totals`. */
  sla: {
    resolutionCompliancePct: number
    responseCompliancePct: number
    resolutionMet: number
    resolutionMissed: number
    responseMet: number
    responseMissed: number
    /** Tickets in the period whose resolution clock has stopped and had a target to judge it by. */
    measured: number
    /** Tickets created in the period. Equals totals.created. */
    total: number
    /** LIVE — ignores the date range, and counts tickets where met/missed count clocks. */
    currentlyBreaching: number
  }
  byCompany: { companyId: number; companyName: string; created: number; resolved: number; slaPct: number }[]
  byEngineer: { userId: number; firstName: string; lastName: string; assigned: number; resolved: number; avgResolutionHours: number }[]
}

export interface ReportRange { from: string; to: string }

export interface TicketComment { id: number; author?: Person; body: string; internal: boolean; system: boolean; createdAt: string }

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

// ── Service contracts ───────────────────────────────────────────────────────
// ── Assets ──────────────────────────────────────────────────────────────────

export type AssetType =
  | 'SERVER' | 'WORKSTATION' | 'LAPTOP' | 'NETWORK_DEVICE' | 'FIREWALL' | 'STORAGE'
  | 'PRINTER' | 'UPS' | 'SOFTWARE_LICENSE' | 'PERIPHERAL' | 'OTHER'

/** Where the asset is in its working life. Orthogonal to WarrantyState — see below. */
export type AssetStatus = 'ACTIVE' | 'IN_REPAIR' | 'IN_STORAGE' | 'RETIRED' | 'DISPOSED'

/**
 * Who an asset belongs to. Three explicit modes rather than a nullable owner, because a null
 * would mean both "shared by design" and "nobody filled this in", and those are different answers.
 */
export type AssetAssignment = 'SYSTEM_USER' | 'GUEST' | 'SHARED'

/**
 * The warranty resolved against today, computed server-side and never stored. NONE ("no end date
 * recorded") is deliberately distinct from EXPIRED ("we know it ran out") — only the second is a
 * renewal conversation.
 */
export type WarrantyState = 'IN_WARRANTY' | 'EXPIRING_SOON' | 'EXPIRED' | 'NONE'

export interface Asset {
  id: number
  companyId: number
  companyName: string
  assetTag?: string | null
  name: string
  type: AssetType
  typeLabel: string
  make?: string | null
  model?: string | null
  serialNumber?: string | null
  location?: string | null
  status: AssetStatus
  statusLabel: string
  assignedTo: AssetAssignment
  /** The MODE's name, e.g. "A user in the system". */
  assignedToLabel: string
  /** Who actually uses it, already resolved: owner name, guest name, or "Shared". */
  usedBy: string
  ownerId?: number | null
  ownerName?: string | null
  ownerEmail?: string | null
  /** Owner exists but can no longer sign in — the offboarding signal, not an error. */
  ownerInactive: boolean
  guestName?: string | null
  guestEmail?: string | null
  purchaseDate?: string | null
  warrantyStart?: string | null
  warrantyEnd?: string | null
  warrantyState: WarrantyState
  warrantyStateLabel: string
  /** Negative once the warranty has run out; null when no end date is recorded. */
  daysToWarrantyEnd?: number | null
  notes?: string | null
  warrantyReminderEnabled: boolean
  warrantyReminderDays?: number | null
  createdAt: string
  updatedAt: string
}

/** One row of the asset-management company picker. */
export interface AssetCompanyRow {
  companyId: number
  companyName: string
  logoUrl?: string | null
  assetCount: number
  expiringSoonCount: number
  expiredCount: number
}

/**
 * Create/update payload. Note there is no `contractIds`: coverage is edited from the contract,
 * by whoever holds MANAGE_CONTRACTS. The asset side links to it and never writes it.
 */
export interface AssetRequest {
  companyId: number
  name: string
  assetTag?: string
  type: AssetType
  make?: string
  model?: string
  serialNumber?: string
  location?: string
  status: AssetStatus
  assignedTo: AssetAssignment
  ownerId?: number | null
  guestName?: string
  guestEmail?: string
  purchaseDate?: string | null
  warrantyStart?: string | null
  warrantyEnd?: string | null
  notes?: string
  warrantyReminderEnabled: boolean
  warrantyReminderDays?: number | null
}

/** Stored lifecycle — only what a human sets. Everything date-driven is `ContractState`. */
export type ContractStatus = 'ACTIVE' | 'CANCELLED'
/** `ContractStatus` resolved against today's date, computed server-side. */
export type ContractState = 'ACTIVE' | 'EXPIRING_SOON' | 'EXPIRED' | 'CANCELLED'
export type ContractFrequency = 'ONE_OFF' | 'MONTHLY' | 'QUARTERLY' | 'SEMI_ANNUAL' | 'ANNUAL'
/** Whether the contract has a visit schedule at all. TICKET_BASED = retainer, no scheduled visits. */
export type ContractType = 'SCHEDULED_VISITS' | 'TICKET_BASED'
export type VisitStatus = 'SCHEDULED' | 'COMPLETED' | 'SKIPPED'

export interface ContractVisit {
  id: number
  sequence: number
  scheduledDate: string
  status: VisitStatus
  overdue: boolean
  notes?: string
  ticketId?: number
  completedAt?: string
  completedByName?: string
}

export interface ContractAttachment {
  id: string
  name: string
  contentType: string
  sizeBytes: number
  url: string
  createdAt: string
}

export interface ServiceContract {
  id: number
  companyId: number
  companyName: string
  name: string
  contractNumber?: string
  description?: string
  status: ContractStatus
  contractType: ContractType
  contractTypeLabel: string
  state: ContractState
  startDate: string
  endDate: string
  /** Negative once the contract has run out. */
  daysToExpiry: number
  frequency: ContractFrequency
  frequencyLabel: string
  occurrences: number
  firstVisitDate: string
  coveredAssets?: string
  ownerId?: number
  ownerName?: string
  renewalReminderDays?: number
  autoCreateTicket: boolean
  ticketLeadDays: number
  ticketPriority: TicketPriority
  ticketCategory: TicketCategory
  visitsTotal: number
  visitsCompleted: number
  visitsOverdue: number
  nextVisitDate?: string
  /** Detail responses only. */
  visits?: ContractVisit[]
  attachments?: ContractAttachment[]
  createdAt: string
  updatedAt: string
}

export interface ContractRequest {
  companyId: number
  name: string
  contractNumber?: string
  description?: string
  status: ContractStatus
  contractType: ContractType
  startDate: string
  endDate: string
  frequency: ContractFrequency
  occurrences: number
  firstVisitDate?: string
  coveredAssets?: string
  ownerId?: number | null
  renewalReminderDays?: number | null
  autoCreateTicket: boolean
  ticketLeadDays: number
  ticketPriority: TicketPriority
  ticketCategory: TicketCategory
  /** Update only: rebuild the visit schedule from the dates above. */
  regenerateSchedule?: boolean
}

export interface VisitRequest {
  status: VisitStatus
  scheduledDate?: string
  notes?: string
}

// ── Audit trail ─────────────────────────────────────────────────────────────
export type AuditLevel = 'INFO' | 'WARN'

export interface AuditEvent {
  id: number
  action: string
  level: AuditLevel
  /** Null for anonymous events — a failed sign-in against an address that doesn't exist. */
  actorId?: number
  /** Snapshot taken at write time, so the trail survives the user being deleted. */
  actorName?: string
  actorEmail?: string
  actorRole?: Role
  details?: string
  ipAddress?: string
  createdAt: string
}

export interface AuditPage {
  events: AuditEvent[]
  totalElements: number
  totalPages: number
  page: number
  size: number
  /** Only the actions actually present, so the filter never offers an empty result. */
  availableActions: string[]
}

// ── Ticket templates / canned replies ───────────────────────────────────────
export type TemplateKind = 'TICKET' | 'REPLY'

export interface TicketTemplate {
  id: number
  kind: TemplateKind
  name: string
  description?: string
  titleTemplate?: string
  body: string
  stepsTemplate?: string
  category?: TicketCategory
  priority?: TicketPriority
  /** Empty = available to every company; otherwise pinned to exactly these. */
  companies: { id: number; name: string }[]
  visibleToCustomers: boolean
  active: boolean
  sortOrder: number
  useCount: number
  createdAt: string
  updatedAt: string
}

export interface TemplateRequest {
  kind: TemplateKind
  name: string
  description?: string
  titleTemplate?: string
  body: string
  stepsTemplate?: string
  category?: TicketCategory | null
  priority?: TicketPriority | null
  /** Empty or omitted = available to every company. */
  companyIds?: number[]
  visibleToCustomers: boolean
  active: boolean
  sortOrder: number
}
