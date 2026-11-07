# Product documentation

[requirements.md](../../requirements.md) owns AI Study Planner product scope and business rules. It describes planned behavior; code and tests establish what is implemented.

Keep environment and startup instructions in [setup.md](../../setup.md), and validation commands and known gaps in the [verification harness](../../.agents/harness/README.md). Do not duplicate those sources here.

Add domain documents only when the team explicitly chooses to split requirements; update the [documentation map](../README.md) and retain one authoritative owner for each rule. The only domain document here is the [ASP-9 Goal API contract](ASP-9-goals-api.md), a design that turns the requirements into an API and data contract; it does not replace requirements.md.

Record consequential accepted choices in [decisions](../decisions/README.md). Use [plans](../plans/README.md) only for work that needs durable memory.
