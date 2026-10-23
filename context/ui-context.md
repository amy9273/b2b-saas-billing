# UI & Usability Standards — B2B SaaS Billing Engine

Enterprise Design System, Accessibility (a11y), Ergonomics, and Anti-Slop Guardrails.

---

## 1. Non-Negotiable Consistency Rules (Anti-Slop Guardrails)

Every AI coding agent and human contributor must follow these rules without exception:

### Rule 1: Zero Arbitrary Styles (No Magic Numbers or Hex Codes)
- **Web / Dashboard**: NEVER use inline styles (`style={{ color: '#2563EB' }}`) or arbitrary Tailwind classes like `bg-[#1e293b]`. Use semantic tokens and Tailwind theme classes only (`bg-surface-card`, `text-primary-600`, `border-border-subtle`).
- **Spacing Scale**: Follow a strict 4px/8px modular scale (`p-2` [8px], `p-4` [16px], `p-6` [24px], `gap-4`). No random margins (`mt-[17px]`).
- **Typography Hierarchy**: Use standard font scale tokens:
  - Header H1: `text-2xl font-bold tracking-tight`
  - Section Header: `text-lg font-semibold`
  - Body Text: `text-sm font-normal text-muted-foreground`
  - Micro-copy / Metadata: `text-xs text-subtle`

### Rule 2: The Mandatory 4-State UI Rule
Every screen, table, or modal consuming asynchronous backend data **MUST** explicitly implement four states:
1. **Loading State**: Content-shaped **Skeleton loaders** with subtle shimmer matching the exact layout of the target table or card. Never show a lone centered spinner on an empty white page.
2. **Empty State**: Icon + clear title + descriptive copy + primary Call-to-Action (e.g., *"No invoices generated yet. Invoices appear automatically on your next billing cycle."*).
3. **Error State**: Non-technical explanation + clear error badge + prominent **"Retry"** button that re-triggers the query.
4. **Populated State**: Full data view with pagination controls, sorting indicators, and status badges.

---

## 2. Color Palette & Semantic Tokens (Enterprise B2B FinTech)

Designed for data-dense dashboards, financial reporting, and high visual clarity.

### Semantic Status Tokens

| Semantic Token | Meaning / Context | Tailwind Class | Badge Styling |
| :--- | :--- | :--- | :--- |
| **STATUS_PAID** | Successful payment / invoice settled | `bg-emerald-500/10 text-emerald-600 border-emerald-500/20` | Emerald Pill + Checkmark |
| **STATUS_PENDING** | Invoice issued, awaiting processing | `bg-amber-500/10 text-amber-600 border-amber-500/20` | Amber Pill + Clock |
| **STATUS_FAILED** | Payment failed / card declined | `bg-rose-500/10 text-rose-600 border-rose-500/20` | High-contrast Rose Alert |
| **STATUS_ACTIVE** | Active tenant subscription | `bg-blue-500/10 text-blue-600 border-blue-500/20` | Blue Pill + Pulse dot |
| **STATUS_SUSPENDED** | Quota exceeded / past due | `bg-slate-500/10 text-slate-600 border-slate-500/20` | Slate Gray Warning |

### Surface & Neutral Hierarchy

| Token | Light Mode Hex | Dark Mode Hex | Usage |
| :--- | :--- | :--- | :--- |
| `background` | `#F8FAFC` (Slate-50) | `#0B0F17` (Deep Obsidian) | Main application canvas |
| `surface` | `#FFFFFF` (Pure White) | `#131B2A` (Navy Slate) | Cards, invoice tables, modal dialogs |
| `surface-secondary` | `#F1F5F9` (Slate-100) | `#1E293B` (Slate-800) | Table headers, code blocks, active rows |
| `border-subtle` | `#E2E8F0` (Slate-200) | `#26354A` (Slate-700) | Table row dividers, input borders |
| `brand-primary` | `#2563EB` (Blue-600) | `#3B82F6` (Blue-500) | Primary buttons, active tabs, focus rings |

---

## 3. Financial Data & Metric Display Rules

1. **Tabular Figures:** Always use monospace or tabular numbers (`font-mono` / `tabular-nums`) when displaying prices, percentages, dates, and currency amounts to guarantee columns align vertically in data tables.
2. **Currency Standardization:** Display currency with ISO currency codes or unambiguous symbols (`$1,250.00 USD`). Never omit cents (display `$12.00`, never `$12`).
3. **Usage Meter Bars:** Quotas must be visually indicated with dual-colored progress meters:
   - Green (`< 75%` utilized)
   - Yellow (`75% – 90%` utilized)
   - Red (`> 90%` utilized + alert callout: *"8/10 seats used"*)

---

## 4. API & UI Usability Standards (Developer Experience)

- **Toast Notifications:** Always display optimistic feedback for mutations:
  - Success: *"User invited successfully. Verification email sent."*
  - Error: *"Failed to upgrade subscription: Payment method declined."*
- **Dangerous Action Confirmation:** Irreversible actions (cancelling a subscription, deleting a user account) must require a two-step confirmation modal with destructive button coloring (`bg-rose-600`).
- **Keyboard Ergonomics:** Modals must close on `Escape` key press and trap focus. Forms must submit on `Ctrl+Enter` / `Cmd+Enter`.
