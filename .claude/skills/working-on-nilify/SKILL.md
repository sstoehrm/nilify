---
name: working-on-nilify
description: Project guide for contributing to nilify itself. Covers repo layout, CLI architecture, skill suite, release process, and conventions. Use when working in the nilify codebase.
---

# Working on nilify

You are contributing to **nilify** itself -- a language-agnostic system specification language for LLM-fused development.

## What nilify is

nilify is a CLI tool + skill suite that helps users define system architecture as a tree of typed specs and validate contracts between components. The harness (Claude Code, superpowers, ralph loop) drives implementation; nilify validates the contracts.

Users define specs in their project's `nil/` folder; nilify validates them. The skill suite teaches the harness how to author, generate, and verify.

## Repository

- **GitHub:** [sstoehrm/nilify](https://github.com/sstoehrm/nilify) (public)
- **Default branch:** `main`
- **License:** Apache 2.0
- **Local checkout (this machine):** `/home/soeren/repos/private/drill-clj` (folder name is legacy from the drill-clj origin; remote is `nilify`)

## File layout

```
nilify              The CLI. Single bb script. Self-contained.
                    Embeds all spec/validation logic. Lives in PATH after install.

install.sh          User-facing installer. curl-pipe into ~/.local/bin.

bb.edn              Local dev dependency declaration (Malli 0.20.1).
                    Not needed by end users -- CLI declares its own deps inline.

test/
  runner.clj        Test entry point (run with `bb test/runner.clj`)
  nilify_test.clj   All tests. Loads the CLI via load-file and tests its
                    functions directly + one subprocess integration test.

skills/nilify/      Skill suite source. Deployed by `nilify init` into
                    user projects at .claude/skills/nilify[-<sub>]/.
  nilify/SKILL.md           Entry point
  author/SKILL.md           Spec authoring
  validate/SKILL.md         Validation
  generate/SKILL.md         Code generation orchestration
  extract/SKILL.md          Reverse-engineer specs from code
  onboard/SKILL.md          Project onboarding
  diff/SKILL.md             Diff spec state (utility)

examples/
  easy-calc.clj     Single-system TUI calculator (tree form)
  todo.clj          Multi-system CRUD app (React + Babashka + SQLite)

.github/workflows/
  ci.yml            Tests on push/PR to main
  release.yml       On tag push: tests, version check, GitHub release

.claude/skills/
  working-on-nilify/SKILL.md   This file. For contributors.

README.md           User-facing intro
LICENSE             Apache 2.0
```

## The CLI (`nilify` script)

Single Babashka file. Self-contained -- declares its Malli dep inline via `babashka.deps/add-deps` so it works installed in `~/.local/bin/` without a project bb.edn.

### Commands

| Command | What it does |
|---------|--------------|
| `init` | Creates `nil/root.clj` stub, downloads skills from GitHub main into `.claude/skills/nilify[-<sub>]/` |
| `validate` | Loads and evaluates `nil/root.clj` via sci, runs three validation passes (Structure / References / Schemas) |
| `spec` | Prints the complete annotated spec reference |
| `update` | Self-updates from GitHub main |
| `version` | Show version |
| `help` | Show command list |

### Structure (inside the file)

1. Shebang + `babashka.deps/add-deps` for Malli
2. `(ns nilify.cli ...)` form
3. Malli spec schemas (embedded from former `src/nilify/spec.clj`)
4. Validation functions (embedded from former `src/nilify/validate.clj`)
5. Core helpers (load-spec, load-system, validate-all)
6. GitHub helpers (fetch-text, latest-version)
7. Command handlers (`cmd-init`, `cmd-validate`, etc.)
8. Main dispatch (only runs when invoked as a script, not when load-file'd by tests)

### Key design decisions

- **Single source of truth.** Spec/validation logic lives only in the CLI file. No `src/` directory anymore. Tests load the CLI via `load-file` and test its functions directly.
- **Self-contained.** Declares its own deps inline. No external setup required.
- **Skills downloaded, not embedded.** Init fetches skill content from GitHub at runtime so skills can evolve without CLI updates.

## The skill suite

Skills compose with superpowers and ralph loop. They don't reimplement orchestration -- they provide structured input (the spec tree) and tell the agent which superpowers skill to use.

### The skills

| Skill | Source path | Deployed to |
|-------|------------|-------------|
| `nilify` (entry) | `skills/nilify/nilify/SKILL.md` | `.claude/skills/nilify/SKILL.md` |
| `nilify-author` | `skills/nilify/author/SKILL.md` | `.claude/skills/nilify-author/SKILL.md` |
| `nilify-validate` | `skills/nilify/validate/SKILL.md` | `.claude/skills/nilify-validate/SKILL.md` |
| `nilify-generate` | `skills/nilify/generate/SKILL.md` | `.claude/skills/nilify-generate/SKILL.md` |
| `nilify-extract` | `skills/nilify/extract/SKILL.md` | `.claude/skills/nilify-extract/SKILL.md` |
| `nilify-onboard` | `skills/nilify/onboard/SKILL.md` | `.claude/skills/nilify-onboard/SKILL.md` |
| `nilify-diff` | `skills/nilify/diff/SKILL.md` | `.claude/skills/nilify-diff/SKILL.md` |

### Naming convention

- Folder under `skills/nilify/<sub>/` in this repo
- Deployed as `.claude/skills/nilify-<sub>/SKILL.md` in user projects (hyphen, not colon -- colons are for plugin-distributed skills only)
- Frontmatter `name:` matches the deployed folder name

## Spec language

A project's spec is a single Clojure file, `nil/root.clj`, whose last form is `(nilify/root [...])`. `nilify validate` evaluates it with babashka's bundled sci (not sandboxed), so `nilify/prompt` and shared `def`'d Malli schema vars work. The `nilify.core` runtime (`prompt`, `root`) is defined in-process by the CLI.

### Tree node types

- `root` -- one per project, contains systems; the file's last form `(nilify/root [...])`
- `system` -- deployable unit with `:tech` (free-form), `:provides` (interface-name vector), `:connects-to`. Children are all layers OR all subsystems
- `subsystem` -- optional grouping with `:uses` and a typed `:provides` (route map); owns the interface definitions
- `layer` -- ordered top-first; lower layers are private. Dependencies flow downward only.
- `feature` -- leaf node with `:desc`, `:internals`

### Naming conventions

- `:sys/<name>` -- system ids
- `:feat/<name>` -- feature ids
- `:iface/<name>` -- interface names
- `:<feature>/<field>` -- schema fields owned by a feature

### Validation passes (all schema-level, only the spec file is evaluated)

1. Structure (the tree conforms to the node Malli schemas)
2. References (`:connects-to` resolves to a provided interface; advertised interfaces are defined by subsystem routes and vice versa; `:uses` resolves to a sibling subsystem; ids are unique)
3. Schemas (each subsystem route's `:input`/`:output` is a valid, generatable Malli schema via `malli.generator`; `[]`/`nil` = empty payload)

## Development workflow

### Run tests

```bash
bb test/runner.clj
```

20 tests, 29 assertions currently. Tests load the CLI via `load-file` and call functions directly; one integration test runs the CLI as a subprocess.

### Local CLI testing

```bash
bb nilify <command>          # Run CLI from repo
bb nilify init               # Test init
bb nilify validate           # Test validate
bb nilify spec               # Print spec reference
```

### Editing skills

Skills are markdown with YAML frontmatter. After editing in `skills/nilify/<sub>/`, push to main; users get the new content next time they run `nilify init` or after manual re-deploy.

### Editing the CLI

Edit `nilify` directly. Bump `(def version "...")` for any user-facing change. Update tests if function signatures change.

## Release process

1. Edit `(def version "X.Y.Z")` in the `nilify` script
2. Commit
3. Tag: `git tag vX.Y.Z`
4. Push: `git push origin main && git push origin vX.Y.Z`
5. The release workflow (`.github/workflows/release.yml`) runs:
   - Tests
   - Verifies CLI version matches tag
   - Creates GitHub release with `nilify` + `install.sh` attached

Users update via `nilify update`, which pulls the latest from `main` (not the latest release tag).

## Open work / known gaps

- **`nilify diff`** -- skill exists, CLI command doesn't. Would write `.nil/state.edn` after generation, diff against current spec tree.
- **`nilify generate`** -- skill describes orchestration via superpowers, but there's no CLI command. Generation today is fully driven by the harness reading specs.
- **Plugin distribution** -- shipping nilify as a Claude Code plugin would give skills the proper `nilify:` namespace prefix and simpler distribution.
- **Visualization** -- the spec tree has clear architecture, but no rendering to diagrams (Structurizr-style).

## When working on this codebase

- **Bump version** in the CLI for any user-facing change before pushing
- **Run `bb test/runner.clj`** before committing
- **Don't add `src/`** back -- the CLI is single source of truth
- **Skills are prose** -- they teach the agent, they don't execute. Keep them concise.
- **Follow existing patterns** -- adapted-from-v1 conventions are intentional, not legacy debt
- **Avoid colons in skill folder names** -- only plugin-distributed skills support `name:sub` syntax
- **Check `evolution-log.md` was deleted** -- don't recreate it; the README and this skill carry the project context now
