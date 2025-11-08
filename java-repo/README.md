# Java Application Repository

Этот репозиторий содержит Java приложение, которое автоматически собирается и развертывается при пуше в feature ветки.

## Структура проекта

```
java-repo/
├── src/                    # Исходный код Java приложения
├── pom.xml                 # Maven конфигурация
├── Dockerfile              # Docker образ для приложения
└── .github/
    └── workflows/
        └── build-image.yml # GitHub Actions workflow
```

## Workflow

1. **Создание feature ветки**: `git checkout -b feature/new-feature`
2. **Разработка и коммиты**: Делаем изменения в коде
3. **Пуш в ветку**: `git push origin feature/new-feature`
4. **GitHub Actions**: Автоматически собирает приложение и создает Docker образ
5. **Загрузка образа**: Образ загружается в minikube (вручную или автоматически)
6. **ArgoCD**: Автоматически развертывает приложение в новом namespace

## Локальная сборка

### Сборка JAR файла

```bash
mvn clean package
```

### Сборка Docker образа

```bash
docker build -t java-app:latest .
```

### Загрузка образа в minikube

```bash
minikube image load java-app:latest
```

Или используйте скрипт из GitOps репозитория:

```bash
../gitops-demo/scripts/load-image-to-minikube.sh java-app latest
```

## Требования

- Java 17+
- Maven 3.9+
- Docker
- Minikube (для локального развертывания)

## Настройка

1. Обновите `pom.xml` с вашими зависимостями
2. Настройте `Dockerfile` под ваше приложение
3. Убедитесь, что приложение слушает порт 8080
4. Настройте health check endpoints (Spring Boot Actuator)

## Health Checks

Приложение должно предоставлять health check endpoints:
- `/actuator/health/liveness` - для liveness probe
- `/actuator/health/readiness` - для readiness probe

Если используете Spring Boot, добавьте зависимость:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

И настройте в `application.properties`:

```properties
management.endpoints.web.exposure.include=health
management.endpoint.health.probes.enabled=true
```

## GitHub Actions

Workflow автоматически:
1. Собирает приложение с Maven
2. Создает Docker образ с тегом = имя feature ветки
3. Сохраняет образ как артефакт для загрузки в minikube

## Интеграция с GitOps

После сборки образа:
1. Скачайте артефакт из GitHub Actions
2. Загрузите образ в minikube
3. GitOps репозиторий автоматически создаст overlay для feature ветки
4. ArgoCD развернет приложение

## Troubleshooting

### Образ не загружается в minikube

Убедитесь, что:
- Minikube запущен: `minikube status`
- Образ существует локально: `docker images | grep java-app`
- Используете правильное имя и тег: `minikube image load java-app:tag`

### Приложение не запускается

Проверьте:
- Логи подов: `kubectl logs -n preview-feature-xxx deployment/auth-service`
- События: `kubectl describe pod -n preview-feature-xxx`
- Health checks: убедитесь, что endpoints доступны

