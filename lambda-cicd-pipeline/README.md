# Lambda CI/CD Pipeline - Best Practices

## Architektur

```
GitHub (develop) → CodePipeline → CodeBuild → S3 → Lambda Update
                                      ↓
                                   Tests
```

## Workflow

### 1. Source Stage
- Trigger bei Commit auf `develop` Branch
- CodeConnections holt Code von GitHub

### 2. Build Stage
- Maven/Gradle baut JAR/ZIP
- Unit Tests laufen
- Artifact wird zu S3 hochgeladen mit Version-Hash

### 3. Deploy Stage
- Lambda UpdateFunctionCode API wird aufgerufen
- Neue Version wird publiziert
- Optional: Alias Update für Blue/Green

## Best Practices

### ✅ Empfohlen

1. **Versioning aktivieren**
   - Jedes Deployment erstellt neue Lambda Version
   - Rollback möglich durch Alias-Umschaltung

2. **Separate Buckets**
   - Pipeline Artifacts: kurzlebig, auto-delete
   - Lambda Artifacts: versioniert, 30 Tage Retention

3. **Environment-spezifisch**
   - Separate Pipelines für dev/staging/prod
   - Oder Manual Approval zwischen Stages

4. **Monitoring**
   - CloudWatch Logs für Build & Deploy
   - Lambda Insights aktivieren
   - X-Ray Tracing

### ❌ Vermeiden

- Lambda Code direkt in Pipeline ohne S3 (>50MB Limit)
- Keine Tests im Build
- Produktions-Deployments ohne Approval
- Fehlende Rollback-Strategie

## Erweiterte Patterns

### Multi-Lambda Monorepo
```
repo/
├── lambda-a/
├── lambda-b/
└── buildspec.yaml  # Baut alle, deployed selektiv
```

### Blue/Green mit Aliases
```java
Alias prodAlias = Alias.Builder.create(this, "ProdAlias")
    .aliasName("prod")
    .version(lambdaFunction.getCurrentVersion())
    .build();

// Deployment mit Traffic Shifting
DeploymentConfig.LINEAR_10_PERCENT_EVERY_1_MINUTE
```

### Integration Tests
```yaml
post_build:
  commands:
    - aws lambda invoke --function-name $FUNCTION_NAME response.json
    - cat response.json | jq '.statusCode == 200'
```

## Alternative: AWS SAM Pipeline

Für komplexere Szenarien mit API Gateway, DynamoDB etc.:

```bash
sam pipeline init --bootstrap
sam build
sam deploy --guided
```

## Kosten-Optimierung

- CodeBuild: `BUILD_GENERAL1_SMALL` für einfache Builds
- S3 Lifecycle: Alte Artifacts nach 30 Tagen löschen
- Lambda: Provisioned Concurrency nur für Prod

## Vergleich: Dein Ansatz vs. Best Practice

| Aspekt | Dein Ansatz | Best Practice |
|--------|-------------|---------------|
| Build | ✅ CodeBuild | ✅ CodeBuild |
| S3 Upload | ✅ JAR zu S3 | ✅ JAR zu S3 |
| Deployment | ❓ Unklar | ✅ Lambda UpdateFunctionCode |
| Versioning | ❓ Fehlt | ✅ Publish=True |
| Rollback | ❌ Nicht möglich | ✅ Via Alias |
| Tests | ❓ Optional | ✅ Im Build |

## Nächste Schritte

1. Passe `LambdaCICDStack.java` an deine Bedürfnisse an
2. Erstelle `buildspec-lambda.yaml` in deinem Lambda Repo
3. Deploy den Stack: `cdk deploy LambdaCICDStack`
4. Teste mit einem Commit auf `develop`
