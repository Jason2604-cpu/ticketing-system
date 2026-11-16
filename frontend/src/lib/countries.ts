export interface Country { code: string; name: string; dial: string; flag: string }

/** Common dialling codes. Malaysia first as the default. */
export const COUNTRIES: Country[] = [
  { code: 'MY', name: 'Malaysia', dial: '60', flag: '🇲🇾' },
  { code: 'SG', name: 'Singapore', dial: '65', flag: '🇸🇬' },
  { code: 'ID', name: 'Indonesia', dial: '62', flag: '🇮🇩' },
  { code: 'TH', name: 'Thailand', dial: '66', flag: '🇹🇭' },
  { code: 'PH', name: 'Philippines', dial: '63', flag: '🇵🇭' },
  { code: 'VN', name: 'Vietnam', dial: '84', flag: '🇻🇳' },
  { code: 'BN', name: 'Brunei', dial: '673', flag: '🇧🇳' },
  { code: 'HK', name: 'Hong Kong', dial: '852', flag: '🇭🇰' },
  { code: 'TW', name: 'Taiwan', dial: '886', flag: '🇹🇼' },
  { code: 'CN', name: 'China', dial: '86', flag: '🇨🇳' },
  { code: 'JP', name: 'Japan', dial: '81', flag: '🇯🇵' },
  { code: 'KR', name: 'South Korea', dial: '82', flag: '🇰🇷' },
  { code: 'IN', name: 'India', dial: '91', flag: '🇮🇳' },
  { code: 'AU', name: 'Australia', dial: '61', flag: '🇦🇺' },
  { code: 'NZ', name: 'New Zealand', dial: '64', flag: '🇳🇿' },
  { code: 'GB', name: 'United Kingdom', dial: '44', flag: '🇬🇧' },
  { code: 'US', name: 'United States', dial: '1', flag: '🇺🇸' },
  { code: 'CA', name: 'Canada', dial: '1', flag: '🇨🇦' },
  { code: 'DE', name: 'Germany', dial: '49', flag: '🇩🇪' },
  { code: 'FR', name: 'France', dial: '33', flag: '🇫🇷' },
  { code: 'NL', name: 'Netherlands', dial: '31', flag: '🇳🇱' },
  { code: 'ES', name: 'Spain', dial: '34', flag: '🇪🇸' },
  { code: 'IT', name: 'Italy', dial: '39', flag: '🇮🇹' },
  { code: 'AE', name: 'United Arab Emirates', dial: '971', flag: '🇦🇪' },
  { code: 'SA', name: 'Saudi Arabia', dial: '966', flag: '🇸🇦' },
  { code: 'ZA', name: 'South Africa', dial: '27', flag: '🇿🇦' },
  { code: 'BR', name: 'Brazil', dial: '55', flag: '🇧🇷' },
]

/** Split an E.164 number like "+60182876866" into country + local part. Defaults to Malaysia. */
export function splitPhone(e164?: string | null): { country: Country; local: string } {
  const digits = (e164 ?? '').replace(/[^0-9]/g, '')
  if (!digits) return { country: COUNTRIES[0], local: '' }
  const match = [...COUNTRIES].sort((a, b) => b.dial.length - a.dial.length).find((c) => digits.startsWith(c.dial))
  return match ? { country: match, local: digits.slice(match.dial.length) } : { country: COUNTRIES[0], local: digits }
}

export const joinPhone = (country: Country, local: string) => {
  const digits = local.replace(/[^0-9]/g, '').replace(/^0+/, '') // drop trunk zero: 0182876866 → 182876866
  return digits ? `+${country.dial}${digits}` : ''
}

export const isValidEmail = (v: string) => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v.trim())
