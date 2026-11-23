// ── Users / auth ─────────────────────────────────────────────────────────────
export type Role = 'ADMIN' | 'ENGINEER' | 'STAFF' | 'CUSTOMER_ADMIN' | 'CUSTOMER'
export type UserStatus = 'INVITED' | 'ACTIVE' | 'INACTIVE' | 'IS_DELETED'

export interface User {
  id: number
  userCode: string
  email: string
  firstName: string
  lastName: string
  phone: string
  /** Employees: ADMIN (everything), ENGINEER, or STAFF = the Default role (no modules). */
  role: Role
  status: UserStatus
  companyId?: number
  companyName?: string
  photoUrl?: string
  notifications?: NotificationPreferences
  /** Engineers/admins only (admin listing): companies they look after. */
  assignedCompanies?: { id: number; name: string }[]
  /** Role Management: admin-created custom roles (with their privileges) granted to this user. */
  customRoles?: CustomRoleRef[]
  /** Feature codes the user's company subscription enables. Customers only — staff aren't plan-gated. */
  companyFeatures?: FeatureCode[]
  /** The default admin (admin@admin.com / admin) until they choose their own password. */
  mustChangePassword?: boolean
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
  | 'MANAGE_KNOWLEDGE_BASE'

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
/** ON_HOLD: waiting on a part / vendor / visit / the customer — the resolution SLA is paused. */
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'ON_HOLD' | 'RESOLVED' | 'CLOSED'
/** Why a ticket is ON_HOLD. Customers see the label; the staff note is an internal comment. */
export type HoldReason = 'SPARE_PART' | 'VENDOR' | 'SCHEDULED' | 'CUSTOMER_REPLY' | 'OTHER'
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
  /** Who typed the ticket in. Never changes. */
  createdBy: Person
  /** Who the ticket is for (gets its customer notifications). Absent on a company-level ticket. */
  requester?: Person
  assignee?: Person
  responseDueAt?: string
  resolutionDueAt?: string
  firstResponseAt?: string
  resolvedAt?: string
  closedAt?: string
  /** ON_HOLD only. */
  holdReason?: HoldReason
  holdReasonLabel?: string
  /** Optional date shown on the ticket (e.g. a booked visit). Display only. */
  holdUntil?: string
  /** When the current hold started — the resolution clock is read here while on hold. */
  slaPausedAt?: string
  /** Working minutes on hold during the current SLA clock. */
  slaPausedMinutes?: number
  createdAt: string
  updatedAt: string
  commentCount: number
  responseBreachedAt?: string
  resolutionBreachedAt?: string
  responseSla?: SlaState
  resolutionSla?: SlaState
}

/** How a ticket stands against one of its SLA clocks. Mirrors the backend SlaState enum. */
export type SlaState = 'NONE' | 'MET' | 'ON_TRACK' | 'AT_RISK' | 'BREACHED' | 'PAUSED'

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
    onHold?: number
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

/**
 * The quote shown above a reply. `unavailable` = the original can't be shown to this viewer (or is gone):
 * then only `id` is set.
 */
export interface ReplyPreview { id: number; authorName?: string; excerpt?: string; internal: boolean; unavailable: boolean }
export interface TicketComment { id: number; author?: Person; body: string; internal: boolean; system: boolean; createdAt: string; replyTo?: ReplyPreview | null }

export type NotificationType = 'TICKET_ASSIGNED_TO_YOU' | 'TICKET_ASSIGNED_NOTICE' | 'TICKET_REPLY' | 'TICKET_RESOLVED' | 'TICKET_CLOSED' | 'TICKET_CREATED'
  | 'SLA_BREACH' | 'CONTRACT_RENEWAL_DUE' | 'KB_PUBLISH_REQUESTED' | 'KB_PUBLISH_DECIDED'
  | 'TICKET_REQUESTER_CHANGED' | 'TICKET_ON_HOLD' | 'TICKET_RESUMED'
export interface AppNotification {
  id: number; type: NotificationType; title: string; body: string
  ticketId?: number
  /** In-app path to open (Knowledge Base, contract reminders); when absent, the ticket is opened. */
  link?: string
  read: boolean; createdAt: string
}
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
  /** Staff only, optional: an active user of that company the ticket is for. Omit for a company-level ticket. */
  requesterId?: number
}

export interface HoldRequest {
  reason: HoldReason
  /** Saved as an internal comment — staff only. */
  note: string
  /** "yyyy-MM-ddTHH:mm", optional, must be in the future. */
  until?: string
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
  /** The asset group it belongs to, if any. */
  groupId?: number | null
  groupName?: string | null
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

/** The light form of an asset, embedded in groups and contracts. */
export interface AssetSummary {
  id: number
  assetTag?: string | null
  name: string
  type: AssetType
  typeLabel: string
  serialNumber?: string | null
}

/**
 * A named set of one company's assets, coverable by a contract as a unit. Membership is live and
 * is set from the asset form (each asset belongs to at most one group).
 */
export interface AssetGroup {
  id: number
  companyId: number
  companyName: string
  name: string
  description?: string | null
  assetCount: number
  assets: AssetSummary[]
  createdAt: string
  updatedAt: string
}

export interface AssetGroupRequest {
  companyId: number
  name: string
  description?: string
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
  /** Optional asset group of the same company; null = no group. */
  groupId?: number | null
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
  /** Detail only: assets listed individually. */
  coveredAssetItems?: AssetSummary[]
  /** Detail only: groups covered as a unit, with their current members. */
  coveredGroups?: CoveredGroup[]
  /** Detail only: distinct assets covered, individual picks and group members counted once. */
  coveredAssetTotal?: number
  createdAt: string
  updatedAt: string
}

export interface CoveredGroup {
  id: number
  name: string
  assetCount: number
  assets: AssetSummary[]
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
  /** Registered assets covered individually — same company as the contract. */
  assetIds: number[]
  /** Asset groups covered as a unit — same company as the contract. */
  assetGroupIds: number[]
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

// ── Knowledge Base ───────────────────────────────────────────────────────────
export type KbVisibility = 'INTERNAL' | 'PUBLIC'
export type KbStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface KbCategoryRef { id: number; name: string; icon?: string | null }
export interface KbCompanyRef { id: number; name: string }
export interface KbUserRef { id: number; name: string }

/** One piece of a search excerpt — plain text, never HTML. */
export interface KbSnippetPart { text: string; match: boolean }

export interface KbPage<T> { items: T[]; totalElements: number; totalPages: number; page: number; size: number }

/** A published article in a reader list / search / suggestions. Staff-only fields are null for customers. */
export interface KbArticleSummary {
  id: number
  slug: string
  title: string
  summary?: string | null
  visibility?: KbVisibility | null
  category?: KbCategoryRef | null
  version: number
  publishedAt: string
  viewCount?: number | null
  /** Only on search results. */
  snippet?: KbSnippetPart[] | null
}

/** A published article as a reader sees it. Fields after publishedAt are staff-only. */
export interface KbArticle {
  id: number
  slug: string
  title: string
  summary?: string | null
  body: string
  category?: KbCategoryRef | null
  version: number
  publishedAt: string
  visibility?: KbVisibility | null
  companies?: KbCompanyRef[] | null
  publishedBy?: KbUserRef | null
  viewCount?: number | null
  canEdit?: boolean | null
  hasUnpublishedChanges?: boolean | null
}

export interface KbCategory {
  id: number
  name: string
  description?: string | null
  icon?: string | null
  sortOrder: number
  articleCount: number
}

export interface KbCategoryRequest { name: string; description?: string; icon?: string | null; sortOrder: number }

/** A working copy in the writers' list. */
export interface KbManageSummary {
  id: number
  slug: string
  title: string
  summary?: string | null
  status: KbStatus
  visibility: KbVisibility
  category?: KbCategoryRef | null
  companies: KbCompanyRef[]
  publishedVersion?: number | null
  hasUnpublishedChanges: boolean
  publishRequestedAt?: string | null
  publishRequestedBy?: KbUserRef | null
  updatedAt: string
  updatedBy?: KbUserRef | null
  viewCount: number
}

export interface KbPermissions {
  canEdit: boolean
  canPublish: boolean
  canRequestPublish: boolean
  canDecline: boolean
  canArchive: boolean
  canDelete: boolean
}

/** A working copy for the editor. */
export interface KbManageArticle extends KbManageSummary {
  body: string
  publishedAt?: string | null
  createdBy?: KbUserRef | null
  createdAt: string
  permissions: KbPermissions
}

export interface KbArticleRequest {
  title: string
  summary?: string
  body: string
  categoryId?: number | null
  visibility: KbVisibility
  /** Empty = every company. */
  companyIds: number[]
}

export interface KbVersion {
  version: number
  title: string
  summary?: string | null
  /** Null in the history list; filled when one version is opened. */
  body?: string | null
  visibility: KbVisibility
  category?: KbCategoryRef | null
  companies: KbCompanyRef[]
  changeNote?: string | null
  publishedBy?: KbUserRef | null
  publishedAt: string
  current: boolean
}

/** An image or file uploaded to an article. */
export interface KbAttachment {
  id: string
  name: string
  contentType: string
  sizeBytes: number
  /** /api/media/<id> — what the article's Markdown links to. */
  url: string
  image: boolean
  createdAt: string
  /** The version readers see links to it. */
  inPublished: boolean
  /** The working copy links to it. */
  inDraft: boolean
}

/** An article linked to a ticket. Fields after summary are staff-only. */
export interface KbTicketArticle {
  articleId: number
  slug: string
  title: string
  summary?: string | null
  status?: KbStatus | null
  visibility?: KbVisibility | null
  /** The ticket's customer could open it. */
  visibleToCustomer?: boolean | null
  linkedBy?: KbUserRef | null
  linkedAt?: string | null
}

export interface KbLinkedTicket { id: number; title: string; status: TicketStatus; companyName: string; linkedAt: string }

export type KbFeedbackEvent = 'SUGGESTION_OPENED' | 'TICKET_AVOIDED'

// ── Automation (AUTOMATION_RULES) ───────────────────────────────────────────
export interface AutomationSettings {
  companyId: number
  companyName: string
  /** The plan includes AUTOMATION_RULES. Without it nothing is sent, whatever the switches say. */
  featureEnabled: boolean
  /** Whether THIS user may change the settings right now. */
  canEdit: boolean
  /** False = staff have locked the company's automations. */
  customerCanEdit: boolean
  monthlySummaryEnabled: boolean
  monthlySummaryDay: number
  monthlySummaryExtraRecipients: string[]
  /** Customer Admins who receive the summary today. */
  monthlySummaryAdminRecipients: string[]
  nextSummaryPeriod: string
  nextSummaryAt: string
  zone: string
}

export interface UpdateAutomationSettingsRequest {
  monthlySummaryEnabled?: boolean
  monthlySummaryDay?: number
  monthlySummaryExtraRecipients?: string[]
  customerCanEdit?: boolean
}

export type AutomationRunStatus = 'SENT' | 'SKIPPED' | 'FAILED'

export interface AutomationRun {
  id: number
  type: string
  period: string
  status: AutomationRunStatus
  recipients: number
  attempts: number
  detail?: string
  ranAt: string
}

export interface MonthlySummaryPreview {
  subject: string
  html: string
  period: string
  recipients: string[]
}
