# Автоматическое развертывание Java приложения при пуше в feature ветку

Этот документ описывает настройку автоматического развертывания Java приложения в новом namespace Kubernetes при пуше в feature ветку.

## Архитектура

1. **Java репозиторий** - содержит Java приложение
2. **GitOps репозиторий** - содержит Kubernetes манифесты и ArgoCD конфигурации
3. **GitHub Actions** - автоматически собирает приложение и создает overlay
4. **ArgoCD ApplicationSet** - автоматически обнаруживает feature ветки и развертывает приложения
5. **Minikube** - локальный Kubernetes кластер с локальными образами

## Workflow

```
Разработчик → Push в feature/* → GitHub Actions (Java repo) → Сборка образа
                                                              ↓
ArgoCD ApplicationSet ← Overlay создан ← GitHub Actions (GitOps repo) ← Образ загружен в minikube
         ↓
  Развертывание в namespace preview-feature-*
```

## Настройка

### Шаг 1: Настройка GitOps репозитория

1. **Обновите ApplicationSet** с URL вашего репозитория:

```bash
# Отредактируйте argocd/applicationset-feature-branches.yaml
# Замените YOUR_USERNAME/YOUR_REPO.git на ваш репозиторий
```

2. **Примените ApplicationSet**:

```bash
kubectl apply -f argocd/applicationset-feature-branches.yaml
```

3. **Проверьте ApplicationSet**:

```bash
kubectl get applicationset -n argocd
kubectl describe applicationset preview-feature-branches -n argocd
```

### Шаг 2: Настройка Java репозитория

**Важно**: Если Java код находится в подпапке `java-repo/` внутри GitOps репозитория, workflow уже настроен и находится в `.github/workflows/build-java-image.yml`.

Если у вас отдельный Java репозиторий:

1. **Скопируйте файлы** из `java-repo/` в ваш Java репозиторий:
   - `Dockerfile`
   - `pom.xml`
   - `src/` (исходный код)
   - `README.md` (опционально)

2. **Создайте workflow** в `.github/workflows/build-image.yml` (скопируйте из `java-repo/.github/workflows/build-image.yml`)

3. **Настройте Dockerfile** под ваше приложение

4. **Убедитесь, что приложение**:
   - Слушает порт 8080
   - Предоставляет health check endpoints (`/actuator/health/liveness`, `/actuator/health/readiness`)
   - Использует Spring Boot Actuator (рекомендуется)

### Шаг 3: Тестирование

1. **Создайте feature ветку** в Java репозитории:

```bash
git checkout -b feature/test-feature
# Внесите изменения
git commit -am "Test changes"
git push origin feature/test-feature
```

**Важно**: После первого push GitHub Actions создаст overlay и закоммитит его в ту же ветку. Перед следующим pushом выполните:

```bash
git pull --rebase origin feature/test-feature
```

Подробнее см. [.github/GIT_WORKFLOW.md](.github/GIT_WORKFLOW.md)

2. **GitHub Actions соберет образ**:
   - Перейдите в Actions в GitHub
   - Дождитесь завершения сборки
   - Скачайте артефакт с образом

3. **Загрузите образ в minikube**:

```bash
# Распакуйте артефакт
unzip artifact.zip
docker load -i image.tar

# Загрузите в minikube
minikube image load java-app:test-feature
# Или используйте скрипт:
./scripts/load-image-to-minikube.sh java-app test-feature
```

4. **Создайте overlay в GitOps репозитории**:

```bash
# В GitOps репозитории создайте feature ветку
git checkout -b feature/test-feature
git push origin feature/test-feature
```

5. **GitHub Actions автоматически создаст overlay**:
   - Перейдите в Actions в GitOps репозитории
   - Дождитесь создания overlay
   - Overlay будет создан в `envs/feature-test-feature/`

6. **ArgoCD автоматически развернет приложение**:
   - ArgoCD ApplicationSet обнаружит новую директорию
   - Создаст Application для feature ветки
   - Развернет приложение в namespace `preview-feature-test-feature`

7. **Проверьте развертывание**:

```bash
# Список приложений
argocd app list

# Статус приложения
argocd app get preview-feature-test-feature

# Проверка подов
kubectl get pods -n preview-feature-test-feature

# Логи
kubectl logs -n preview-feature-test-feature deployment/auth-service
```

## Детальная настройка

### Настройка ApplicationSet

ApplicationSet использует git generator для автоматического обнаружения директорий `envs/feature-*`:

```yaml
generators:
- git:
    repoURL: https://github.com/YOUR_USERNAME/YOUR_REPO.git
    revision: HEAD
    directories:
    - path: envs/feature-*
```

**Важно**: Git generator сканирует директории в текущей ветке (HEAD). Для работы с feature ветками есть два подхода:

1. **Подход 1 (рекомендуется)**: Overlay создается в той же feature ветке, где находится код
   - ApplicationSet сканирует feature ветку
   - Overlay и код находятся в одной ветке
   - Проще для изоляции изменений

2. **Подход 2**: Overlay создается в main ветке
   - ApplicationSet сканирует main ветку
   - Все overlay находятся в main ветке
   - Проще для управления, но требует merge в main

Для каждой найденной директории создается Application с:
- Именем: `preview-{feature-name}`
- Namespace: `preview-{feature-name}`
- Источник: `envs/feature-{feature-name}` из текущей ветки (HEAD)

### Настройка GitHub Actions в GitOps репозитории

Workflow автоматически создает overlay при push в feature ветку:

```yaml
on:
  push:
    branches:
      - 'feature/**'
```

Workflow:
1. Извлекает имя feature ветки
2. Проверяет, существует ли overlay
3. Создает overlay из шаблона, если не существует
4. Коммитит изменения в ветку

### Настройка GitHub Actions в Java репозитории

Workflow автоматически собирает приложение при push в feature ветку:

```yaml
on:
  push:
    branches:
      - 'feature/**'
```

Workflow:
1. Собирает Java приложение с Maven
2. Создает Docker образ с тегом = имя feature ветки
3. Сохраняет образ как артефакт

### Загрузка образов в minikube

Есть несколько способов загрузки образов:

#### Способ 1: Ручная загрузка

```bash
# После сборки образа в GitHub Actions
docker load -i image.tar
minikube image load java-app:feature-name
```

#### Способ 2: Использование скрипта

```bash
./scripts/load-image-to-minikube.sh java-app feature-name
```

#### Способ 3: Автоматическая загрузка (если есть доступ к minikube)

Если у вас есть self-hosted runner с доступом к minikube, можно автоматизировать загрузку в workflow.

## Структура overlay

Каждый overlay содержит `kustomization.yaml` с:

```yaml
namespace: preview-feature-{name}
resources:
- ../../base
images:
- name: java-app
  newName: java-app
  newTag: {feature-name}
patches:
- # Патчи для ingress, environment variables и т.д.
```

## Переменные окружения

В deployment можно добавить переменные окружения через kustomization:

```yaml
patches:
- patch: |-
    - op: add
      path: /spec/template/spec/containers/0/env/-
      value:
        name: FEATURE_NAME
        value: "{feature-name}"
```

## Ingress

Каждое приложение доступно по уникальному hostname:

```
feature-{name}.local
```

Для доступа добавьте в `/etc/hosts`:

```
<minikube-ip> feature-{name}.local
```

Или используйте port-forward:

```bash
kubectl port-forward -n preview-feature-{name} service/auth-service 8080:8080
```

## Очистка

### Удаление feature окружения

1. **Удалите приложение в ArgoCD**:

```bash
argocd app delete preview-feature-{name}
```

2. **Удалите namespace**:

```bash
kubectl delete namespace preview-feature-{name}
```

3. **Удалите overlay из репозитория**:

```bash
git rm -r envs/feature-{name}
git commit -m "Remove feature {name}"
git push
```

4. **Удалите образ из minikube** (опционально):

```bash
minikube ssh "crictl rmi java-app:{feature-name}"
```

### Автоматическая очистка

ArgoCD ApplicationSet автоматически удалит Application при удалении директории из репозитория (благодаря finalizer и prune policy).

## Troubleshooting

### ApplicationSet не создает приложения

1. Проверьте логи ApplicationSet контроллера:

```bash
kubectl logs -n argocd deployment/argocd-applicationset-controller
```

2. Проверьте статус ApplicationSet:

```bash
kubectl describe applicationset preview-feature-branches -n argocd
```

3. Убедитесь, что:
   - Репозиторий добавлен в ArgoCD
   - Директории `envs/feature-*` существуют в репозитории
   - Ветки `feature/*` существуют в репозитории

### Образ не загружается

1. Проверьте, что minikube запущен:

```bash
minikube status
```

2. Проверьте, что образ существует локально:

```bash
docker images | grep java-app
```

3. Попробуйте загрузить вручную:

```bash
minikube image load java-app:tag
```

### Приложение не запускается

1. Проверьте логи:

```bash
kubectl logs -n preview-feature-{name} deployment/auth-service
```

2. Проверьте события:

```bash
kubectl describe pod -n preview-feature-{name}
```

3. Убедитесь, что:
   - Образ загружен в minikube
   - `imagePullPolicy: Never` установлен
   - Health check endpoints доступны
   - Порты настроены правильно (8080)

### Overlay не создается

1. Проверьте логи GitHub Actions:

```bash
# В GitHub перейдите в Actions и проверьте логи workflow
```

2. Убедитесь, что:
   - Workflow активирован при push в feature ветку
   - Шаблон существует в `envs/_templates/feature-kustomization.yaml`
   - Есть права на коммит в репозиторий

## Лучшие практики

1. **Используйте осмысленные имена веток**: `feature/user-authentication`, а не `feature/test`
2. **Регулярно очищайте старые feature окружения**
3. **Мониторьте использование ресурсов** в minikube
4. **Используйте health checks** для проверки готовности приложения
5. **Тестируйте локально** перед пушем в feature ветку
6. **Используйте теги** для версионирования образов

## Дополнительные ресурсы

- [ArgoCD ApplicationSet Documentation](https://argocd-applicationset.readthedocs.io/)
- [Kustomize Documentation](https://kustomize.io/)
- [Minikube Documentation](https://minikube.sigs.k8s.io/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

