# Testing Guidelines

## Match Tests To Change Scope
- `web-app` UI logic: add or update Vitest tests in `web-app/src/__tests__`.
- Gateway Java behavior: add or update JUnit tests under `gateway/**/src/test/java`.
- SDK changes: update `typescript-sdk/tests`.
- Cross-service or routing behavior: update `test/*.test.ts`.
- End-to-end user flows: update Playwright specs in `test/e2e`.

## Minimum Expectations
- Bug fixes should include a regression test where practical.
- API or contract changes should update both unit/integration coverage and affected E2E scenarios if user-visible.
- Startup/config changes should be reflected in script/config tests when applicable.

## Execution
```bash
cd test && npm test
cd test && npm run test:e2e
cd gateway && mvn test
cd web-app && npm test
```

Playwright requires the app stack to already be running.
