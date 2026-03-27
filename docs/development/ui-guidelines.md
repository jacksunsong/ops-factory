# UI Guidelines

## Preserve Existing Interaction Model
- Keep the current route-driven shell and sidebar-based navigation.
- Preserve the right-panel pattern for preview and market-style auxiliary flows.
- Reuse existing contexts, hooks, and shared components before adding parallel state flows.

## Default Page Patterns
Choose the closest existing pattern before designing a new one.

- List/detail workflows should keep primary navigation and bulk context in the main column, with item inspection or editing in the right panel when screen size allows.
- Configuration pages should be assembled from section cards with short headers, focused actions, and compact key-value or form layouts.
- Workbench pages such as testing, comparison, or debugging flows should keep inputs and run controls near the top, render results in structured cards or grids, and open the selected item in the right panel or a modal fallback on small screens.
- Comparison views should reuse a shared board structure: one header explaining the comparison scope, parallel result columns with aligned metadata, and diagnostics or warnings above the board.
- Empty, loading, and error states should stay inline with the current section instead of introducing custom full-page treatments unless the route genuinely has no usable content.

## UI Change Rules
- New pages should fit the current information architecture: `src/pages` for route pages, `src/components` for reusable UI, `src/hooks` for shared logic.
- Keep i18n support in mind when introducing user-facing text.
- Errors should use the established error-handling and toast patterns rather than bespoke banners per page.
- Responsive behavior is required for any new top-level page or major workflow.
- Before adding a new class family, check whether the behavior can be expressed by extending an existing shared component, utility class, or variant.
- Prefer shared primitives for cards, pills/tags, banners, empty states, split layouts, and detail panels. Feature-specific classes should only describe domain-specific content, not restate common card chrome.
- New controls should preserve the existing button hierarchy, form spacing, border treatment, and selection states.
- When a new shared visual pattern is introduced, extract it intentionally and document where it should be reused.

## Visual Consistency Rules
- Reuse the established spacing scale, radius tokens, border colors, muted text treatment, and hover/selected affordances already used across the app.
- Keep section headers concise: title first, optional one-sentence description second, actions aligned with the section rather than embedded deep in content blocks.
- Use tags/pills for compact metadata, banners for actionable warnings or errors, and key-value grids for dense inspection details.
- Keep action density low. Prefer one primary action per section or workbench, with secondary actions visually subordinate.
- Do not create a new color story for a single feature. Any new accent, status, or surface treatment should be justified as a reusable shared pattern.

## Responsive Rules
- Split layouts must degrade predictably: multi-column comparison boards collapse to fewer columns, then a single column; right panels fall back to inline or modal detail views on smaller screens.
- Top-level controls should stack vertically on narrow screens without changing task order or hiding critical actions.
- New workflows should be usable at common laptop widths before adding denser desktop-only layouts.

## AI Implementation Checklist
Before considering a frontend task complete, confirm:

- The feature matches an existing page pattern or documents why a new one was necessary.
- Shared layout and visual primitives were reused before adding page-specific wrappers.
- User-facing text is localized and consistent with nearby features.
- Empty, loading, error, and responsive states were implemented with existing patterns.
- Screenshots or an equivalent visual check were captured for any user-visible layout change.

## Review Triggers
Request design or frontend review when a change affects:
- navigation structure
- right-panel behavior
- chat/file/monitoring core workflows
- shared visual patterns used across multiple pages
- introduction of a new reusable layout or visual primitive
