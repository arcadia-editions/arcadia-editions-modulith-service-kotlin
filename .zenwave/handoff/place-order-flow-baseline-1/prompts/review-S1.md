You are the reviewer of slice S1 (tracer bullet, path "completed") of the board .zenwave/work/integration/docs/tasks.md. Work in .zenwave/work/review-S1, a read-only checkout at 2c897e8.
Follow the skill zenwave-review-slice: .claude/skills/zenwave-review-slice/SKILL.md.
Range: 0c1fddd..2c897e8 (S1 base 0c1fddd). S1 tasks: T-04, T-05, T-06, T-08, T-09, T-10, T-12 (claude-sonnet), T-07, T-11, T-13 (claude-opus). Both Claude workers implemented part of S1. Judge the code, not their reports (.zenwave/handoff/place-order-flow/reports/T-*.md).
The range also holds S0 fixes (reviewed, PASS) and tasks of S2..S8 (T-14..T-31, partly): judge only S1 tasks' changes, but report anything else that breaks the suite or contradicts an S1 model line.
Points the orchestrator wants checked: fakes (FakePaymentProcessor, FakeWarehouse, FakeEmailSender) are @Component in main with no profile (T-33 picks real ones); redelivery no-ops on every listener; StockUnavailableException + noRollbackFor in startOrderCheckout (T-14) with the real event publication registry; listener mappers.
Decisions: P-01 D, P-03 A (code only), P-04 A; P-02 open (assumption A-01), P-05 open. Proposals in .zenwave/work/review-S1/docs/proposals/.
Run the full suite once, ONLY through the Docker lock wrapper (Docker is shared with another project):
  bash .zenwave/handoff/place-order-flow/docker-run.sh mvn -q verify -Dmaven.test.failure.ignore=true
If a run fails on "port is already allocated" or a DB I/O error, it is the environment: rerun once and say so.
Models: sibling repos ../arcadia-editions-architecture and ../arcadia-editions-modulith-api relative to the repository root; the ZFL @zdl paths map as the overlay says. Read only.
Standards: AGENTS.md, the project rules of the overlay, the conventions of the code next to each change.
Change nothing in the checkout (target/ is fine). Write your report to .zenwave/handoff/place-order-flow/reports/review-S1.md and end your final message with it.
