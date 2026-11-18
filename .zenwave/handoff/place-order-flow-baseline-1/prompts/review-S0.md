You are the reviewer of slice S0 of the board .zenwave/work/integration/docs/tasks.md. Work in .zenwave/work/review-S0, a read-only checkout at 71a5999.
Follow the skill zenwave-review-slice: .claude/skills/zenwave-review-slice/SKILL.md.
Range: 7e239c1..71a5999. Tasks of the slice: T-01, T-02, T-03, all implemented by claude-sonnet. The range also holds T-04 (slice S1, reserveStock):
judge only the S0 tasks' changes, but report anything in T-04 that breaks the suite. Judge the code, not their reports
(reports in .zenwave/handoff/place-order-flow/reports/T-0{1,2,3}.md). P-04 (.zenwave/work/review-S0/docs/proposals/P-04.md) records the Modulith findings T-02 tolerates: say whether the tolerance is tight enough.
Run the full suite once: export JAVA_HOME=$HOME/.sdkman/candidates/java/current; mvn -q verify -Dmaven.test.failure.ignore=true (about 4 minutes, Docker/Testcontainers). Compare failures with "Known failing tests at the baseline" in docs/zenwave-sdd-overlay.md: T-02 and T-03 rows should now pass; T-06 rows still fail (expected).
Models: sibling repos ../arcadia-editions-architecture and ../arcadia-editions-modulith-api relative to the repository root. Read only.
Standards to check: AGENTS.md, the project rules of the overlay, the conventions of the code next to each change.
Change nothing in the checkout (build output under target/ is fine). Write your report, in the shape of the skill, to .zenwave/handoff/place-order-flow/reports/review-S0.md, and end your final message with it.
