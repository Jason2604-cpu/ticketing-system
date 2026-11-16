import {
  BookOpen, Cloud, CreditCard, Database, HardDrive, KeyRound, LifeBuoy, Mail, Monitor, Network, Printer,
  Server, Settings, Shield, Smartphone, Users, Wifi, Wrench, type LucideIcon,
} from 'lucide-react'

/** The category icons the backend accepts (KbCategory.ICONS) — keep the two lists in step. */
export const KB_ICONS: Record<string, LucideIcon> = {
  BookOpen, Wrench, Printer, Wifi, Shield, Mail, Monitor, Server, KeyRound, HardDrive, Smartphone,
  CreditCard, Users, Cloud, Database, LifeBuoy, Settings, Network,
}
export const KB_ICON_NAMES = Object.keys(KB_ICONS)
export const kbIcon = (name?: string | null): LucideIcon => (name && KB_ICONS[name]) || BookOpen
