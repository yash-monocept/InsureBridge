# 09 — UI Design System (Updated)

**Status:** COMPLETE (redesigned)

## Design Direction

A mature B2B enterprise operations dashboard for insurance integration engineering teams. Deliberately avoids generic AI SaaS patterns: no gradients, glows, neon, glassmorphism, oversized cards, floating elements, large empty heroes, or decorative animations.

## Visual Tokens (CSS custom properties, `console.css`)

| Token | Light | Dark | Usage |
|-------|-------|------|-------|
| `--background` | `#fafafa` | `#09090b` | page background |
| `--foreground` | `#18181b` | `#fafafa` | primary text |
| `--card` | `#ffffff` | `#111113` | surface |
| `--muted` | `#f4f4f5` | `#18181b` | subtle backgrounds |
| `--border` | `#e4e4e7` | `#27272a` | separators |
| `--accent` | `#0f6b54` | `#45a98b` | primary interaction |
| `--danger` | `#b42318` | `#f97066` | errors / failed |
| `--warning` | `#946200` | `#fdb022` | warnings / pending |
| `--success` | `#067647` | `#47cd89` | success / validated |
| `--ring` | rgba focus ring | rgba focus ring | focus outline |
| `--radius` | `6px` | `6px` | component radius |

## Status Pill Colours

| Status | Class | Appearance |
|--------|-------|------------|
| Active / Validated | `.status-pill.validated` | green text + soft green bg |
| Validation Failed | `.status-pill.validation-failed` | red text + soft red bg |
| Received / Validating / Processing | `.status-pill.received` | amber text + soft amber bg |
| Inactive | default | muted |

## Layout

- Sidebar: 232px fixed, deep neutral card background, thin border right
- Main content: margin-left 232px, padding 40px, responsive to 24px/16px
- Topbar: 56px height, border bottom, health dot + theme toggle + breadcrumbs
- Panels: 1px border, 6px radius, white/card surface, 18px padding
- Tables: border-collapse, small uppercase header text, hover row, `tabular-nums` for numbers

## Typography

- System font stack (`ui-sans-serif`, -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif)
- 14px base, 1.5 line height
- Eyebrows (10px, uppercase, letter-spaced) for section labels
- h1: 24px / h2: 15px for page/section headings

## Components

- Buttons: primary (accent), secondary (bordered), quiet (text)
- Inputs/selects: border, focus ring (accent), no outline
- Dialogs: `<dialog>` with backdrop overlay, max 640px / 90vh
- Toasts: fixed bottom-right, success/error variants with coloured left border
- Badges/tags: small, bordered, muted

## Responsive

- ≤1100px: narrower padding, 2-col stats, stacked layouts
- ≤720px: sidebar becomes horizontal, nav grid 3-col, stacked forms
- ≤480px: stats single column, environment hidden

## Accessibility

- Skip-to-content link
- `aria-label` on icon buttons, `aria-pressed` on theme toggle
- `aria-live="polite"` toast region
- Focus-visible rings
- Colour contrast meets neutral foreground on card backgrounds

## Files

- `src/main/resources/static/console.css` — all styling
- `src/main/resources/static/console.css` dark variant via `html[data-theme="dark"]`
