# AI Engineering Provenance

This repository uses AI assistants as engineering agents to assist with software development tasks.

## Prompt Management

Material engineering prompts are treated as versioned engineering artifacts and are stored under i/. Historical prompts are immutable - a semantic change to an engineering prompt requires a new version.

Prompt versions should be referenced by milestone/development records. Git remains the authoritative record of what was actually implemented.

AI prompts are specifications/instructions, not evidence that implementation succeeded. Human researcher/project owner has final authority over project decisions.

## Traceability Chain

`
Prompt
? Decision
? Implementation
? Verification
? Git checkpoint
`

## Roles

**GPT-5.6 Luna**: architecture, scientific reasoning, design review, adversarial review.
**Qwen 30B MoE**: implementation, testing, debugging, repository operations.
**Human researcher**: final authority.

> Do not claim any prompt was successfully executed merely because it is stored.
