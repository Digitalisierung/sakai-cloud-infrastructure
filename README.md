# SAKAI AWS Infrastructure.

`SAKAI AWS Infrastructure` ist das AWS-Infrastrukturmodul für das "SAKAI Inventory Management System".  
Das Modul wurde mit **AWS CDK v2** und **Java 21** umgesetzt und beschreibt die serverlose Backend-Infrastruktur, die
Deployment-Pipeline, die Stage-Konfiguration sowie unterstützende AWS-Ressourcen als **Infrastructure as Code**.

Das Modul stellt unter anderem folgende AWS-Komponenten bereit und verwaltet sie:

- Amazon API Gateway
- AWS Lambda
- Amazon DynamoDB
- Amazon S3
- AWS CodePipeline
- AWS CodeBuild
- AWS IAM
- AWS Systems Manager Parameter Store
- CloudWatch Logs

---

## Überblick.

Dieses Modul stellt die Cloud-Infrastruktur für ein serverloses SAKAI IMS-Backend bereit. Es erzeugt eine stagefähige
AWS-Umgebung, die für Entwicklung, Test, Produktion oder lokale branch-basierte Umgebungen verwendet werden kann.

Die Infrastruktur unterstützt REST-basierte Funktionen über API Gateway und Java-basierte Lambda-Funktionen. Die Daten
werden in DynamoDB gespeichert. Die Lambda-Artefakte werden aus einem S3-Bucket geladen und über Einträge im AWS Systems
Manager Parameter Store referenziert.

Zusätzlich definiert das Modul eine Self-Mutated CI/CD-Pipeline, die die CDK-Anwendung aus dem konfigurierten
Repository-Branch synthetisiert und automatisiert deployt.

---

## Architektur.

Das Modul besteht im Wesentlichen aus zwei Bereichen:

1. **Infrastructure Pipeline:**
   - verbindet sich über AWS CodeConnections mit dem Source Repository,
   - führt den CDK Synth über CodeBuild aus,
   - unterstützt Self-Mutation der Pipeline,
   - deployt die Application Infrastructure Stage.

2. **Application Infrastructure:**
   - erstellt die Backend-Ressourcen für das Inventory Management System,
   - stellt DynamoDB, API Gateway, Lambda-Funktionen und IAM-Rollen bereit,
   - verbindet API-Gateway-Routen mit Lambda-Handlern,
   - liest Lambda-Artefaktinformationen aus dem SSM Parameter Store.

```text
Git Repository
|
v
AWS CodePipeline
|
v
AWS CodeBuild / CDK Synth
|
v
CloudFormation Deployment
|
v
API Gateway --> Lambda-Funktionen --> DynamoDB
|
v
CloudWatch Logs
```

---

## Modulstruktur.

```text
sakai-aws-infrastructure/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/sakai/cloud/infra/
│   │   │       ├── app/
│   │   │       │   └── SakaiInfrastructureApp.java
│   │   │       ├── config/
│   │   │       │   ├── ApiGatewayConfigurator.java
│   │   │       │   └── StageConfigurator.java
│   │   │       ├── factory/
│   │   │       │   ├── ApiGatewayFactory.java
│   │   │       │   └── DynamoDbTableFactory.java
│   │   │       └── stack/
│   │   │           ├── InfrastructurePipelineStack.java
│   │   │           ├── SakaiApplicationStage.java
│   │   │           └── SakaiServiceStack.java
│   │   └── resources/
│   │       └── log4j2.properties
│   └── test/
│       └── java/
│           └── com/sakai/cloud/infra/
│               └── SakaiServiceStackTest.java
├── buildspec.yaml
├── cdk.json
├── pom.xml
└── README.md
```

---

## Zentrale Bestandteile.

### CDK Application.

Die CDK Application ist der Einstiegspunkt für die Infrastrukturdefinition. Sie liest die Zielumgebung aus AWS Account,
Region und Stage-Konfiguration ein und erstellt anschließend den Infrastructure Pipeline Stack.

Die Stage kann über folgende Wege gesetzt werden:

- CDK Context Parameter: `stage`
- Umgebungsvariable: `STAGE_NAME`
- lokaler branch-basierter Fallback über `branch`

**Beispiel:**

```terminaloutput
cdk deploy <stack-name> -c stage=<stage-name> --profile <aws-profile-name>
```

---

### Stage-Konfiguration.

Das Modul verwendet eine zentrale Stage-Konfiguration, um umgebungsspezifisches Verhalten zu steuern.

Unterstützte vordefinierte Stages:

| Stage       | Branch                 | Zweck                    |
|-------------|------------------------|--------------------------|
| `Dev`       | `develop`              | Entwicklungsumgebung     |
| `Test`      | aktuell konfigurierbar | Test- / QA-Umgebung      |
| `Prod`      | `main`                 | Produktionsumgebung      |
| `local-env` | individueller Branch   | Private Sandbox Umgebung |

Stage-spezifische Einstellungen umfassen unter anderem (heißt, je nach Stage unterschiedlich):

- Source Branch.
- DynamoDB Removal Policy.
- DynamoDB Point-in-Time Recovery.
- API Gateway Data Tracing.
- Lambda Timeout.
- Lambda Memory Size.
- Log Level.
- CORS Origins.

---

### Infrastructure Pipeline.

Der `InfrastructurePipelineStack` definiert die CI/CD-Pipeline für die AWS-Infrastruktur.

Er erstellt unter anderem:

- S3 Artifact Bucket.
- CodePipeline Source Stage.
- CodeBuild Synth Step.
- CDK Self-Mutation.
- Deployment Stage für die Application Infrastructure.
- IAM-Berechtigungen für AWS CodeConnections.

Ablauf der Pipeline:

- Der Source Code wird aus dem konfigurierten Repository-Branch geladen.
- AWS CDK wird in der Build-Umgebung installiert.
- Die CDK Application wird synthetisiert (CloudFormation template).
- Die generierten CloudFormation Templates werden als Pipeline Output bereitgestellt.
- Die Application Stage wird automatisch deployt.

---

### Application Stage.

Die `SakaiApplicationStage` bündelt die deploybaren Infrastruktur-Stacks für eine bestimmte Umgebung.

Aktuell wird folgender Stack deployt:

- `SakaiServiceStack`

Dadurch kann dieselbe Infrastrukturdefinition für mehrere Umgebungen wie Dev, Test und Prod wiederverwendet werden.

---

### Service Stack

Der `SakaiServiceStack` enthält die eigentliche Backend-Infrastruktur für das Inventory Management System.

Er stellt bereit:

- DynamoDB Inventory Table.
- Global Secondary Indexes.
- Lambda Execution Roles.
- Java-21-Lambda-Funktionen.
- REST API Gateway.
- Lambda Proxy Integrations.
- SSM-basierte Artefaktreferenzen.
- Resource Tags für Projekt, Stage und Service-Zuordnung.

---

## AWS-Ressourcen.

### DynamoDB.

Das Modul erstellt pro Stage eine DynamoDB-Tabelle für Artikelverwaltung.

Point-in-Time Recovery wird abhängig von der gewählten Stage-Konfiguration aktiviert oder deaktiviert.

---

### Lambda-Funktionen.

Der Stack definiert Java-21-basierte Lambda-Funktionen für Artikel- und Katalogoperationen.

Runtime-Konfiguration:

| Einstellung | Wert        |
|-------------|-------------|
| Runtime     | Java 21     |
| Architektur | x86_64      |
| Timeout     | 30 Sekunden |
| Memory      | 1024 MB     |

Der S3-Bucket für Lambda-Artefakte und die jeweiligen Object Keys werden über den AWS Systems Manager Parameter Store
aufgelöst.

---

### API Gateway.

Das Modul erstellt eine REST-API für Artikel- und Katalogoperationen.

Konfigurierte Endpunkte:

| Methode | Pfad                      | Lambda Integration               |
|---------|---------------------------|----------------------------------|
| `GET`   | `/articles`               | Artikel auflisten                |
| `POST`  | `/articles`               | Artikel erstellen                |
| `GET`   | `/articles/{id}`          | Artikel anhand der ID abrufen    |
| `GET`   | `/catalogs`               | Kataloge auflisten               |
| `POST`  | `/catalogs`               | Katalog erstellen                |
| `GET`   | `/catalogs/{id}`          | Katalog anhand der ID abrufen    |
| `PUT`   | `/catalogs/{id}`          | Katalog aktualisieren            |
| `GET`   | `/catalogs/{id}/articles` | Artikel eines Katalogs auflisten |

Die API verwendet Lambda Proxy Integrations.

CORS und API Gateway Tracing werden abhängig von der ausgewählten Stage konfiguriert.

---

### IAM.

Das Modul erstellt IAM-Rollen für die Ausführung der Lambda-Funktionen.

Die Rollen enthalten unter anderem:

- CloudWatch-Logs-Berechtigungen über `AWSLambdaBasicExecutionRole`
- lesenden Zugriff auf DynamoDB für reine Leseoperationen
- Lese- und Schreibzugriff auf DynamoDB für schreibende Operationen

Die Pipeline erhält zusätzlich die Berechtigung, die konfigurierte AWS CodeConnection zu verwenden.

---

### S3 Artifact Bucket.

Die Infrastructure Pipeline erstellt einen S3-Bucket für Pipeline-Artefakte.

---

### SSM Parameter Store.

Das Modul verwendet AWS Systems Manager Parameter Store, um Infrastruktur-Deployment und Lambda-Artefaktbereitstellung
voneinander zu entkoppeln.

Statt Lambda-JAR-Dateien fest zu verdrahten, liest die Infrastruktur den Artefakt-Bucket und die Object Keys aus
SSM-Parametern.

Dadurch können Lambda-Artefakte in einem separaten Build- oder Deployment-Prozess erzeugt und anschließend von diesem
Infrastrukturmodul verwendet werden.

---

## Umgebungskonfiguration.

Die Zielumgebung (Dev, Test, Prod oder Local) wird über die Standard-CDK-Umgebungsvariablen bestimmt:

* `CDK_DEFAULT_ACCOUNT`
* `CDK_DEFAULT_REGION`

Die Stage kann als CDK Context Value übergeben werden:

```bash
cdk synth -c stage=Dev
```

oder als Umgebungsvariable:

```bash
export STAGE_NAME=Dev
```

Wenn keine gültige vordefinierte Stage gesetzt ist, kann eine lokale branch-basierte Konfiguration verwendet werden:

```bash
cdk synth -c branch=<my-feature-branch>
```

---

## Voraussetzungen.

Benötigte Werkzeuge:

- Java 21
- Maven
- Node.js / npm
- AWS CDK CLI
- AWS CLI
- konfigurierte AWS Credentials
- gebootstrappte AWS-CDK-Umgebung

AWS CDK CLI installieren:

```bash
npm install -g aws-cdk
```

Installation prüfen:

```bash
cdk --version
java -version
mvn -version
aws --version
```

AWS Account und Region bootstrappen, falls noch nicht geschehen:

```bash
cdk bootstrap aws://<ACCOUNT_ID>/<REGION>
```

Beispiel:

```bash
cdk bootstrap aws://123456789012/eu-central-1
```

oder einfach nur mit:

```bash
cdk bootstrap
```

Dann werden Default-Werte (Account und Region) verwendet.

---

## Build.

Aus dem Modulverzeichnis:

```bash
mvn clean compile
```

Aus dem Repository Root. Maven Wrapper sollte verwendet werden:

```bash
./mvnw clean compile
```

---

## CDK-Befehle.

Alle CDK-Befehle sollten aus dem Verzeichnis `sakai-aws-infrastructure` ausgeführt werden, sofern nicht anders
angegeben.

### Dev mit Standard-Profil synthetisieren:

```bash
cdk synth -c stage=Dev
```

### Dev mit Dev-Profil synthetisieren:

```bash
cdk synth -c stage=Dev --profile <my-dev-profile-name>
```

### Test synthetisieren:

```bash
cdk synth -c stage=Test
```

### Prod synthetisieren:

```bash
cdk synth -c stage=Prod
```

### Lokale Branch-Umgebung (Sandbox) synthetisieren::

```bash
cdk synth -c branch=my-feature-branch
```

### Diff anzeigen:

```bash
cdk diff -c stage=Dev
```

oder mit AWS Profile:

```bash
cdk diff -c stage=Dev --profile <my-aws-profile>
```

### Deploy ausführen:

```bash
cdk deploy -c stage=Dev
```

oder mit AWS Profile:

```bash
cdk deploy -c stage=Dev --profile <aws-profile>
```

### Destroy ausführen (Standard AWS Profil):

Vorsichtig verwenden. Je nach Stage-Konfiguration können bestimmte Ressourcen erhalten bleiben.

```bash
cdk destroy -c stage=Dev
```

---

## Deployment-Ablauf.

Typischer Ablauf eines Deployments:

```text
1. Ein Entwickler pusht Infrastrukturänderungen in den konfigurierten Branch.
2. AWS CodePipeline erkennt die Änderung.
3. CodeBuild installiert die benötigten Tools und führt CDK Synth aus.
4. CDK erzeugt CloudFormation Templates.
5. Die Pipeline aktualisiert sich bei Bedarf selbst.
6. Die Application Infrastructure Stage wird deployt.
7. API Gateway, Lambda, DynamoDB und IAM-Ressourcen werden in AWS aktualisiert.
```

---

## Resource Tagging.

Das Modul setzt Tags auf Infrastrukturressourcen, um Nachvollziehbarkeit, Zuordnung und Kostenanalyse zu erleichtern.

Häufige Tags:

| Tag         | Wert                  |
|-------------|-----------------------|
| `Project`   | `Sakai`               |
| `Stage`     | ausgewählte Stage     |
| `ManagedBy` | `CDK`                 |
| `Owner`     | `Digitalisierung`     |
| `Service`   | `InventoryManagement` |

---

## Logging.

Das Modul verwendet `Log4j2` für das Logging der CDK Application.

Die Logging-Konfiguration befindet sich unter:

```text
src/main/resources/log4j2.properties
```

Lambda-Funktionen erhalten CloudWatch-Logs-Berechtigungen über die AWS-managed Policy `AWSLambdaBasicExecutionRole`.

---

## Sicherheitshinweise

Das Modul enthält mehrere sicherheitsrelevante Konfigurationen:

- IAM-Rollen für Lambda-Funktionen.
- getrennte Lese- und Lese-/Schreibrechte für DynamoDB.
- blockierter öffentlicher Zugriff auf den Pipeline Artifact Bucket.
- erzwungene SSL-Kommunikation für S3-Zugriffe.
- verschlüsselte S3-Artefaktspeicherung.
- stage-abhängiges Verhalten für Datenaufbewahrung.
- optionale DynamoDB Point-in-Time Recovery.
- API Gateway Stage-Konfiguration.

Vor produktiver Nutzung sollten insbesondere folgende Punkte geprüft werden:

- CORS-Konfiguration für Production.
- CodeConnection ARNs.
- IAM Permission Boundaries, falls erforderlich.
- Verfügbarkeit der benötigten SSM-Parameter.
- DynamoDB Retention- und Backup-Strategie.
- Authentifizierung und Autorisierung der API.

---

## Technologien.

- Java 21
- Maven
- AWS CDK v2
- AWS Lambda
- Amazon API Gateway
- Amazon DynamoDB
- Amazon S3
- AWS CodePipeline
- AWS CodeBuild
- AWS IAM
- AWS Systems Manager Parameter Store
- JUnit 5
- Log4j2

---

## Zweck des Moduls

Ziel dieses Moduls ist es, das Deployment der AWS-Infrastruktur:

- reproduzierbar,
- automatisiert,
- versioniert,
- stagefähig,
- wartbar,
- und nach Serverless-Prinzipien

bereitzustellen.

Es bildet damit die technische Grundlage für den Betrieb des SAKAI Inventory Management Backends auf AWS.