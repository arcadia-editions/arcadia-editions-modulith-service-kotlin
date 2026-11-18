You are the reviewer of slice S4 of the board .zenwave/work/integration/docs/tasks.md. Work in .zenwave/work/review-S4-r2, a read-only checkout at 5dc4a45.
Follow the skill zenwave-review-slice: .claude/skills/zenwave-review-slice/SKILL.md.
Fix round: your previous report is .zenwave/handoff/place-order-flow/reports/review-S4.md; verify each finding first (fixed, not fixed, or made worse), then look at the fix tasks' diffs for new problems.
Slice tasks (original and fixes): T-22, T-23, T-24, T-41, fix T-44. Judge the code, not the reports (.zenwave/handoff/place-order-flow/reports/T-*.md).
SUITE: do NOT rerun the full suite. It ran at this exact commit 5dc4a45 (log .zenwave/handoff/place-order-flow/logs/suite-5dc4a45.log): surefire 98/98, failsafe 48/48 green, all 8 PlaceOrderFlow* flow tests included. Use it; if you doubt a test, rerun only that test class through: bash .zenwave/handoff/place-order-flow/docker-run-it.sh mvn -q verify -Dit.test=<Class> -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false
Decisions: P-01 D, P-03 A (code only), P-04 A; open for the person: P-02 (A-01), P-05, P-06. Do not re-raise them.
Models: sibling repos ../arcadia-editions-architecture and ../arcadia-editions-modulith-api relative to the repository root. Read only.
Change nothing in the checkout. Write your report to .zenwave/handoff/place-order-flow/reports/review-S4-r2.md and end your final message with it.
