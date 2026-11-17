---
name: zenwave-regenerate
description: Assist the person in regenerating a ZenWave SDK project after a model change, without losing hand-written code - run the generator with --force in a scratch worktree, sort every changed file into new, generated-always and generated-once, take the new files and the generated-always ones, merge into the generated-once files (service implementations, listeners, mappers, tests) only the methods the models added, stub the new service methods, wire only the listeners that can run, compile and run the full suite, and leave a curated diff for the person to review and commit. Use in the HITL loop after the models change and before the test data and the board; never in the AFK loop.
---

# ZenWave Regenerate

Regeneration is a step of the HITL loop. The person does it either on their own or with this skill. The orchestrator
and its workers never regenerate.

Two problems come with regenerating a project that already has code:
- A normal run (no `--force`) skips every file that exists. New `@listener`, `@calls` or commands never reach the
  generated-once files that are already there.
- A forced run rewrites those files and wipes the code the person and the AFK loop wrote.

By hand, the person handles it in one of two ways. For one class: delete it on a clean tree, regenerate, and keep
the hunks they need with `git diff`. For many: run `--force` and roll back the files whose changes they don't want.
This skill does the second, file by file and hunk by hunk, in a scratch worktree, so the project's working tree
only ever receives the curated result.

## Authorization

An explicit invocation of `$zenwave-regenerate` authorizes:
- creating and removing a scratch git worktree (outside the project, or under `.zenwave/work/`);
- running the generator there with `--force`;
- editing the project's working tree with the curated result: new files, generated-always files, the added methods
  of generated-once files, compile stubs, and the tolerance lists of architecture tests when the change adds a
  module dependency the models require (step 5);
- compiling and running the full suite;
- writing `.zenwave/handoff/<flow>/regen-reference-<models version>.patch`.

It does not commit. Ask before anything else: changing a model, a contract or the approved test data; pushing; or
installing the models package (`mvn install` of the models jar is the person's step).

## 0. Preconditions

- The models are changed, linted and packaged. The project points at the new models version (`pom.xml`,
  `zenwave-scripts.zw`). Read both from the overlay (`docs/zenwave-sdd-overlay.md`).
- The working tree is clean apart from that version bump. If not, ask what to do with the changes.
- Read the overlay's **protected files**: generated-always files the project changed on purpose (for example an
  annotation added to `BaseMapper`, or a generated file removed) that a regeneration must never take. If the
  overlay has no such list, look for them in the history (`git log --diff-filter=D`, commits touching generated
  files) and propose the list in the report.

## 1. Regenerate in scratch

```bash
git worktree add <scratch> HEAD
# copy the uncommitted version bump into <scratch>, then commit it there
```

In `<scratch>`, run every plugin of `zenwave-scripts.zw` with `--force`: the `jbang zw -p <Plugin> ...` command each
one shows, with the same dependencies and options. When a released version of a plugin is not available, use the
version the overlay says (for example a `-SNAPSHOT`). Then run `git status` and `git diff` in `<scratch>`. That diff
is everything the generator would change.

## 2. Sort every changed file

| Kind | How to tell | What to take |
|---|---|---|
| **New** | untracked in `<scratch>` | the whole file, unless it is protected |
| **Generated-always** | DTOs, inputs, events, enums, entities, service interfaces, event publisher interfaces and their default and in-memory implementations, `*Transitions` | the whole file, unless it is protected or its diff removes something the project added on purpose |
| **Generated-once** | service implementations (`*Impl`), listeners, listener mappers, controllers, tests, `data.sql`, fixtures, `DockerComposeInitializer` | only the hunks the model change added: new methods, new `@ApplicationModuleListener`/`@EventListener` methods, new mapper methods, new imports. Never the rest of the file |

Ignore hunks that only reorder methods or imports. When you can't tell a file's kind, compare it with the generator's
templates and the overlay, then ask. For each generated-once file, read the model lines behind each added hunk
(`@listener`, `@calls`, a new command) and check that it belongs to the change.

## 3. Merge into the project

Apply the sorted result to the project's working tree. For generated-once files, write the added methods into our
version, keeping its style. Don't copy the generated file over ours.

Compile. Each service interface method with no implementation gets a **compile stub** in the implementation:
- `TODO("<method>: " + input.<id>)`;
- or a no-op (`null`, an empty list) when a caller can reach it today and an exception would break a passing test.

Each stub has one comment: `// <baseline tag> compile stub: implemented by a task of docs/tasks.md`. Each stub
becomes a task on the board. When a signature changes (a new return type, a new required input field), adapt the
existing implementation and its tests just enough to compile and keep their current behavior. Don't implement the
new behavior: that is the board's job.

## 4. Wire listeners only when they can run

A new listener method that calls real code, or a stub that does nothing, is merged and wired. A listener that would
call a `TODO()` stub, or would replace a listener that passing tests depend on (a timer moved to a new command, for
example), is left out. It becomes the first task of its slice. List each one, wired or not, in the report.

## 5. Run everything

Run the overlay's compile command and the full suite (`verify`, containers included). A new failure is caused by
one of three things:
- **A stub reached by an existing test:** turn it into a no-op, or leave its listener out (step 4).
- **A changed signature:** adapt the test call, keeping the assertion.
- **A module dependency the models now require** (a new cycle through event types): add it to the architecture
  test's tolerance list, with the model line that causes it.

Never weaken an assertion or delete a test. If a failure has another cause, stop and report it.

## 6. Leave it for the person

- Save `git diff` of the project (new files included) as `.zenwave/handoff/<flow>/regen-reference-<version>.patch`.
- Remove the scratch worktree.
- Report:
  - the sorted file list (new, generated-always taken, generated-once merged, protected or left out);
  - the stubs;
  - the listeners wired and left out;
  - any tolerance added;
  - the suite results and how long they took.

The person reviews the diff and commits it, signed. The commit is part of the next baseline. Then come the test data
(`zenwave-test-data`, `zenwave-test-scenarios`) and the board (`zenwave-tasks-from-code-and-models`). The board gets
one task per stub and per listener left out.
