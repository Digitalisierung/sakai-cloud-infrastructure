# Lambda CI/CD Stack

Automatisches Deployment von Lambda-Funktionen bei GitHub Commits.

## Features

✅ **Neue Lambda-Funktionen**: Automatisch erstellt beim ersten Deployment  
✅ **Updates**: Bestehende Funktionen werden aktualisiert  
✅ **Versioning**: Jedes Deployment erstellt eine neue Lambda-Version  
✅ **Multi-Lambda**: Unterstützt mehrere Lambda-Funktionen in einem Repository

## Architektur

```
GitHub (develop) → CodePipeline → Build Stage → Deploy Stage
                                      ↓              ↓
                                   S3 Bucket    Lambda Update/Create
```

## Repository-Struktur

Dein Lambda-Repository sollte so strukturiert sein:

```
lambda-functions/
├── function-a/
│   ├── pom.xml
│   └── src/
├── function-b/
│   ├── pom.xml
│   └── src/
└── buildspec-lambda.yaml
```

## Deployment

1. **Stack deployen**:
```bash
cd lambda-cicd-pipeline
cdk deploy LambdaCICDStack
```

2. **Lambda-Repository konfigurieren**:
   - Kopiere `buildspec-lambda.yaml` in dein Lambda-Repository
   - Passe Repository-Name in `LambdaCICDStack.java` an (Zeile 186)
   - Passe Lambda-Metadaten im buildspec an (Handler, Runtime, Role)

3. **Commit & Push**:
```bash
git commit -m "Add new lambda function"
git push origin develop
```

## Wie es funktioniert

### Build Stage
1. Maven/Gradle baut alle Lambda-Funktionen
2. JARs werden zu S3 hochgeladen mit Git-Hash
3. `lambda-functions.json` wird erstellt mit Metadaten

### Deploy Stage
1. Liest `lambda-functions.json` aus S3
2. Für jede Funktion:
   - Prüft ob Lambda existiert
   - **Neu**: `CreateFunction` mit S3-Code
   - **Update**: `UpdateFunctionCode` mit neuer Version
3. Publiziert neue Version

## Anpassungen

### Andere GitHub-Repository
```java
.repo("dein-lambda-repo")  // Zeile 186
```

### Lambda-Execution-Role
Erstelle eine IAM-Role für Lambda und passe im buildspec an:
```yaml
"roleArn":"arn:aws:iam::ACCOUNT:role/deine-lambda-role"
```

### Gradle statt Maven
Im buildspec:
```yaml
- gradle build
- JAR_FILE=$(find build/libs -name "*.jar" | head -1)
```

## Kosten

- CodeBuild: ~$0.005/Minute (SMALL)
- S3: ~$0.023/GB
- CodePipeline: $1/Monat pro Pipeline

## Troubleshooting

**Pipeline schlägt fehl**: Prüfe CloudWatch Logs in Build/Deploy LogGroups  
**Lambda nicht erstellt**: Prüfe IAM-Permissions der Deploy-Role  
**S3 Upload fehlgeschlagen**: Prüfe Build-Role Permissions
