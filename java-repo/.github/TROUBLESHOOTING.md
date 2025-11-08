# Troubleshooting GitHub Actions Build

## Проблема: Workflow не собирает образ

### Проверка 1: Workflow запускается?

1. Перейдите в GitHub → Actions
2. Проверьте, есть ли запущенные workflows для вашей ветки
3. Если workflow не запускается, проверьте:
   - Находится ли файл в `.github/workflows/build-image.yml`
   - Правильно ли настроен триггер (ветки `feature/**`)
   - Есть ли коммиты в feature ветке

### Проверка 2: Workflow падает с ошибкой

Проверьте логи в GitHub Actions:

1. Откройте failed workflow run
2. Проверьте, на каком шаге произошла ошибка
3. Посмотрите логи каждого шага

### Частые проблемы

#### Проблема: Maven build fails

**Симптомы:**
```
[ERROR] Failed to execute goal ...
```

**Решение:**
1. Проверьте, что `pom.xml` существует и валиден
2. Проверьте, что все зависимости доступны
3. Проверьте версию Java (должна быть 17)

**Исправление:**
```yaml
- name: Build with Maven
  run: |
    mvn clean package -DskipTests -X  # -X для подробного лога
```

#### Проблема: JAR file not found

**Симптомы:**
```
Error: JAR file not found in target/ directory
```

**Решение:**
1. Проверьте, что Maven build завершился успешно
2. Проверьте название JAR файла в `pom.xml`
3. Добавьте проверку после build:

```yaml
- name: Verify JAR file
  run: |
    ls -la target/
    find target -name "*.jar"
```

#### Проблема: Dockerfile not found

**Симптомы:**
```
Error: Dockerfile not found
```

**Решение:**
1. Убедитесь, что `Dockerfile` находится в корне репозитория
2. Проверьте путь в workflow:
   ```yaml
   docker build -t "$FULL_IMAGE" -f Dockerfile .
   ```

#### Проблема: Docker build fails

**Симптомы:**
```
Error: Docker build failed
```

**Решение:**
1. Проверьте синтаксис Dockerfile
2. Проверьте, что все файлы на месте (JAR, pom.xml)
3. Проверьте базовые образы (maven, eclipse-temurin)

**Добавьте подробный лог:**
```yaml
- name: Build Docker image
  run: |
    docker build -t "$FULL_IMAGE" -f Dockerfile . --progress=plain
```

#### Проблема: Workflow не запускается для feature ветки

**Симптомы:**
Workflow не появляется в Actions

**Решение:**
1. Проверьте паттерн веток в workflow:
   ```yaml
   on:
     push:
       branches:
         - 'feature/**'
   ```

2. Убедитесь, что ветка начинается с `feature/`
3. Попробуйте запустить вручную через `workflow_dispatch`

#### Проблема: Image artifact не создается

**Симптомы:**
Артефакт не появляется в Actions

**Решение:**
1. Проверьте, что образ был сохранен:
   ```yaml
   - name: Verify artifact
     run: |
       ls -lh *.tar
       du -h *.tar
   ```

2. Проверьте размер артефакта (должен быть > 0)
3. Проверьте права доступа

## Диагностика

### Проверка структуры репозитория

```bash
# Убедитесь, что все файлы на месте
ls -la
ls -la .github/workflows/
ls -la src/main/java/
ls -la src/main/resources/
```

### Локальная проверка сборки

```bash
# Соберите локально
mvn clean package

# Проверьте JAR
ls -lh target/*.jar

# Соберите Docker образ локально
docker build -t java-app:test .

# Проверьте образ
docker images | grep java-app
```

### Проверка workflow синтаксиса

```bash
# Установите actionlint (если есть)
actionlint .github/workflows/build-image.yml

# Или проверьте вручную YAML синтаксис
yamllint .github/workflows/build-image.yml
```

## Улучшенный workflow с диагностикой

Добавьте эти шаги для лучшей диагностики:

```yaml
- name: Debug information
  run: |
    echo "GitHub Context:"
    echo "  Branch: ${{ github.ref }}"
    echo "  Event: ${{ github.event_name }}"
    echo "  Workflow: ${{ github.workflow }}"
    echo ""
    echo "Repository structure:"
    ls -la
    echo ""
    echo "Source files:"
    find src -type f
    echo ""
    echo "Maven version:"
    mvn --version
    echo ""
    echo "Java version:"
    java -version
```

## Полезные команды для отладки

### В workflow добавить:

```yaml
- name: Debug Maven build
  run: |
    mvn clean package -DskipTests -X | tee build.log
    tail -100 build.log

- name: Debug Docker build
  run: |
    docker build -t test-image -f Dockerfile . --progress=plain 2>&1 | tee docker-build.log
    tail -100 docker-build.log
```

## Контакты и поддержка

Если проблема не решается:
1. Проверьте логи в GitHub Actions
2. Попробуйте собрать локально
3. Создайте issue с описанием проблемы и логами

