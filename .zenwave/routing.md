# Routing: who does what

The orchestrator reads this file whenever it hands out work. Edit it at any time, or tell the orchestrator to ("send
the advanced tasks to Sonnet", "Codex is out until six"): it changes this file and uses the new routing from the next
task. The model names are the ones in use in November 2026; change them to what you have.

## By kind of work

The first worker that is available takes the task; the next one is the fallback when the first is out of quota,
failed twice, or is already busy (Codex runs one job at a time).

| Work | First | Then | Then |
|---|---|---|---|
| mechanical (follows a pattern already in the code) | claude-sonnet | claude-opus | |
| bounded (fully described by the model and the test data) | claude-sonnet | claude-opus | |
| advanced (several contexts, a scheduler, an external API) | claude-opus | claude-sonnet | |
| review (one slice, full suite) | claude-opus | claude-sonnet | |

A review is done by a worker that implemented none of the slice when there is one. If there is none, an implementer
reviews it and the orchestrator says so in its report.

## Workers

Only Claude workers are used (the person, 2026-11-12): no task or review goes to codex-terra, codex-sol or grok.

| Name | How it runs | Model | Notes |
|---|---|---|---|
| claude-sonnet | Agent tool, `subagent_type: implementer` or `reviewer`, `model: sonnet`, `run_in_background: true` | sonnet | parallel is fine; one per clone |
| claude-opus | the same, `model: opus` | opus | |
| codex-terra | `codex exec` (below) | gpt-5.6-terra, effort medium | one job at a time |
| codex-sol | `codex exec` (below) | gpt-6.1-sol, effort high | one job at a time. Its sandbox could not start the JDK in November 2026: for Gradle or Maven work, use it for reading reviews, not for running the build |
| grok | `grok` (below) | grok-4.7, effort high | no sandbox (`--always-approve`): only in a task clone |

Start Codex and Grok from the Bash tool, in the background, from the task's clone. Keep their output in the handoff
folder, never in the conversation:

```bash
# Codex: the prompt on stdin, the final message as the report
codex exec -C <clone> --model <model> --config 'model_reasoning_effort="<effort>"' --approve-for-me --json \
  --output-last-message <report> - < <prompt-file> > <log>.jsonl 2> <log>.err

# Grok
grok --cwd <clone> --model <model> --reasoning-effort <effort> --always-approve --output-format json \
  --prompt-file <prompt-file> > <log>.json 2> <log>.err
```

## Limits

- Tasks running at once: 3.
- Codex jobs at once: 1.
- Two tasks run together only when they touch different bounded contexts (see the `model:` files of each).

## Files the workers must never change

Checked on every task before it is committed. Edit the list to the project's layout.

```text
*.zfl *.zdl *openapi*.yml *asyncapi*.yml
src/test/resources/data.sql
src/test/resources/data/*
```

## Commands of this project

The orchestrator and the workers use the ones in `docs/zenwave-sdd-overlay.md`. Only set here what is specific to
unattended work:

- Integration tests run only in slice verification (the person, 2026-11-13). Workers and the orchestrator check each
  task with its unit tests, the compile and ArchitectureTest; flow-test tasks are written and compiled, then run in
  the slice review's full suite (the reviewer reruns a failing flow test once alone before reporting it).
- One long-running test compose (`docker compose -f docker-compose-test.yml up -d`, project `arcadia-editions-modulith-it`, ports
  15432/29092/22181/18081, T-37) is reused by every integration run; tests restore their data. Never two integration
  runs at once: always through `.zenwave/handoff/place-order-flow/docker-run-it.sh` (lock). Docker is shared with
  `arcadia-editions-modulith-service-java`; host 5432/9092/2181/8081 may be held by others.
