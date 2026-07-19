# Agent skills (synced)

<!-- AUTO-SYNCED from notebox — do not edit here. Canonical: notebox/scripts/shared-conventions/skills-README.sibling.md -->

**Source of truth:** notebox `.cursor/skills/` (subset).  
`.claude/skills` is a **symlink** to `.cursor/skills` so Claude Code and Cursor load the same files.

Re-sync from notebox:

```bash
/path/to/notebox/scripts/sync-shared-conventions.sh "$(pwd)"
```

## Synced skills

- `design-an-interface`
- `git-guardrails-claude-code`
- `grill-me`
- `improve-codebase-architecture`
- `plan-next-pr`
- `request-refactor-plan`
- `tdd`
- `to-prd`
- `ubiquitous-language`
- `zoom-out`

Most skills are imported from [mattpocock/skills](https://github.com/mattpocock/skills/tree/main); see `mattpocock-skills-LICENSE`. `plan-next-pr` is authored in notebox.

Repo-specific paragraphs in the canonical notebox skills are stripped on sync; generic fallbacks remain where defined.
