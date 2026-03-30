# Repository Guidelines

## Project Structure & Module Organization
`ops-factory` is a multi-service monorepo. Core services live in [`gateway/`](./gateway) and [`web-app/`](./web-app). The gateway is a Java 21 Maven project split into `gateway-common` and `gateway-service`; application code is under `src/main/java` and tests under `src/test/java`. The React/Vite frontend keeps code in `web-app/src`, with pages in `src/pages`, shared UI in `src/components`, hooks in `src/hooks`, and tests in `src/__tests__`. Cross-service tests live in [`test/`](./test), the TypeScript SDK in [`typescript-sdk/`](./typescript-sdk), and Docker-backed helpers in [`langfuse/`](./langfuse), [`onlyoffice/`](./onlyoffice), and [`prometheus-exporter/`](./prometheus-exporter). Agent-specific configs and skills are under `gateway/agents/*`.

## Build, Test, and Development Commands
Use the root orchestrator for local development:

```bash
./scripts/ctl.sh startup all
./scripts/ctl.sh status
./scripts/ctl.sh shutdown all
```

Targeted workflows:

```bash
cd web-app && npm run dev
cd web-app && npm run build
cd test && npm test
cd test && npm run test:e2e
cd typescript-sdk && npm run build && npm test
cd gateway && mvn test
cd prometheus-exporter && mvn test
```

Playwright expects the app stack to already be running at `http://127.0.0.1:5173`.

## Coding Style & Naming Conventions
Follow the existing style in each module. TypeScript in this repo uses `strict` mode, 4-space indentation in app code, semicolon-light formatting, `PascalCase` for React components, `camelCase` for hooks/utilities, and `*.test.ts(x)` for tests. Java follows standard Spring conventions: `PascalCase` classes, `camelCase` members, one public class per file, package paths under `com.huawei.opsfactory`. No repo-wide ESLint/Prettier config is checked in, so keep diffs consistent with nearby files and rely on TypeScript/Maven compilation as the baseline check.

## AI Frontend Delivery Rules
When implementing or extending frontend features, do not start from page-specific styling. First identify which existing page pattern the change belongs to, then reuse the matching layout, interaction model, and visual primitives from the current app.

- Treat the route shell, section cards, toolbar/form blocks, result lists, and right-panel/detail-panel flows as the default building blocks for new UI work.
- Prefer extending existing shared components, hooks, and CSS primitives before adding new page-specific wrappers or class families.
- If a feature needs a new interaction pattern, document the reason in the relevant UI or architecture doc and keep the first implementation narrow and reusable.
- Avoid inventing a new visual language per feature. Reuse the established spacing, border, radius, empty-state, banner, tag, and button treatments unless the product explicitly calls for a new shared pattern.
- For comparison, testing, or inspection workflows, default to the existing workbench model: controls and context in the main area, results in structured cards or grids, and detail inspection in the right panel or modal fallback on smaller screens.
- Frontend handoffs and PRs should call out which existing patterns were reused, which new shared primitives were introduced, and include screenshots or GIFs for validation.

## Testing Guidelines
Use Vitest for frontend and integration coverage, Playwright for E2E, Node’s test runner for the SDK, and JUnit/Spring Boot tests for Java services. Keep unit tests close to the code they exercise when a module already does that (`web-app/src/__tests__`, `gateway/**/src/test/java`); otherwise place cross-service scenarios in `test/`. Name tests after behavior, for example `Files.delete.test.ts` or `InstanceManagerTest.java`.

## Commit & Pull Request Guidelines
Recent history favors short Conventional Commit-style subjects such as `feat：support reasoning block` and `fix：startup script`. Prefer `feat:`, `fix:`, `test:`, or `docs:` with a focused summary. PRs should describe user-visible impact, list touched services, link related issues, and include screenshots or GIFs for frontend changes. Call out config changes explicitly when `config.yaml` or service startup behavior changes.

## Collaboration Constraints
Treat [`docs/architecture/overview.md`](./docs/architecture/overview.md), [`docs/architecture/api-boundaries.md`](./docs/architecture/api-boundaries.md), [`docs/architecture/process-management.md`](./docs/architecture/process-management.md), and [`docs/development/ui-guidelines.md`](./docs/development/ui-guidelines.md) as the source of truth for cross-team work. Do not bypass the gateway from the frontend, do not change auth headers or SSE/event payloads without explicit review, and keep new UI work aligned with the existing route/layout/right-panel model and shared visual primitives. Any new config key must be added to the matching `config.yaml.example` and documented in the relevant development or architecture doc.
