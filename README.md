# Reproducer for [UNDERTOW-1445](https://issues.jboss.org/browse/UNDERTOW-1445)

The benchmark may be run from the command line using:

```bash
./gradlew benchmark
```

This will run one set without the request buffering handler, and another with.
