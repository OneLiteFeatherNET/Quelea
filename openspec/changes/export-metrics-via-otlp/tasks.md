# Tasks

## 1. Backend

- [ ] 1.1 Replace `micronaut-micrometer-registry-prometheus` with `micronaut-micrometer-registry-otlp`.
- [ ] 1.2 `application.yml`: OTLP export block (`QUELEA_METRICS_OTLP_ENABLED`, `QUELEA_METRICS_OTLP_URL`,
      `QUELEA_METRICS_OTLP_STEP`), resource attributes, `/prometheus` endpoint removed.
- [ ] 1.3 Tests: meters still registered (operation counter by result), `/prometheus` answers 404.

## 2. Documentation

- [ ] 2.1 README: observability section and configuration table.

## 3. Pull request

- [ ] 3.1 Open the code pull request `feat(observability): push metrics over otlp`.
