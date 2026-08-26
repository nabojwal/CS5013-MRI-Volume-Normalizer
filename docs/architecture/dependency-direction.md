# Dependency Direction

Allowed dependencies:

```text
presentation -> application -> domain
infrastructure -> domain
```

The domain is the innermost layer. It uses only Java standard-library types and domain types. Infrastructure may depend on domain contracts, but domain code must never depend on infrastructure.

M4 architecture tests enforce the currently materialized domain and presentation contracts using ordinary JUnit reflection. They do not parse bytecode, create custom class loaders, or add architecture-testing dependencies.
