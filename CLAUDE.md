# Agent Instructions

Rules:

- Code and comments MUST be in US English
- UI code must not read files directly.
- UI code must not call Git directly.
- Composables receive state and callbacks only.
- ViewModels depend on repositories or use cases, not Android Context.
- Git operations live only in data/git.
- Markdown parsing and wiki-link resolution live outside UI.
- Inbox editability is a domain rule: inbox notes editable, all other notes read-only.
- No background sync in the PoC.
- No full-text search in the PoC.
- No multi-workspace support in the PoC.
- Prefer small files. New files over 250 lines need justification.
- Every feature slice must include at least one unit test for domain/data behavior.
