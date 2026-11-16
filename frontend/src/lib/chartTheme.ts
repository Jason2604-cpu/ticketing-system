/**
 * Chart colours that follow the theme.
 *
 * Recharts takes colours as props, not classes, so it can't read Tailwind tokens.
 * These read the same CSS variables index.css defines, which means one source of
 * truth for both — a grid line in a chart is the same grey as a border elsewhere.
 *
 * Read at render time rather than module load, because the variables change when
 * the theme is toggled. Every consumer re-renders on that toggle (ThemeProvider
 * updates context), so the values are always current.
 */
const read = (name: string, fallback: string) => {
  if (typeof window === 'undefined') return fallback
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

export const chartTheme = () => ({
  /** Horizontal rules behind the plot. */
  grid: read('--chart-grid', '#eef0f3'),
  /** Axis tick labels. */
  axis: read('--chart-axis', '#9ca3af'),
  /** Panel background for tooltips and the bar hover band. */
  surface: read('--surface-raised', '#ffffff'),
  line: read('--line', '#e5e7eb'),
  ink: read('--ink', '#111827'),
  /** Hover band behind a bar. */
  cursor: read('--g-100', '#f3f4f6'),
})

/** Shared Recharts <Tooltip contentStyle> so tooltips aren't white boxes on a dark page. */
export const tooltipStyle = (t: ReturnType<typeof chartTheme>) => ({
  borderRadius: 8,
  border: `1px solid ${t.line}`,
  background: t.surface,
  color: t.ink,
  fontSize: 12,
})

/**
 * The neutral fill for progress bars — bars that measure usage rather than
 * status, so they should read as "ink on the page", not as a colour with meaning.
 */
export const neutralBar = () => read('--ink', '#111827')
