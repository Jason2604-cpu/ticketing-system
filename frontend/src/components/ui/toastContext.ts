import { createContext } from 'react'

export type ToastTone = 'success' | 'error'
export const ToastContext = createContext<{ toast: (message: string, tone?: ToastTone) => void } | undefined>(undefined)
