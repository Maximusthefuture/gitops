# Git Workflow для Feature Branches

## Проблема: Non-fast-forward при push

Когда GitHub Actions автоматически создает overlay и коммитит изменения в feature ветку, локальная ветка может отстать от удаленной. Это нормальная ситуация.

## Решение

### Вариант 1: Pull с rebase (рекомендуется)

```bash
# Синхронизируем с удаленной веткой
git pull --rebase origin feature/new-test

# Если есть конфликты, разрешите их и продолжите:
git add .
git rebase --continue

# Затем push
git push origin feature/new-test
```

### Вариант 2: Pull с merge

```bash
# Синхронизируем с удаленной веткой
git pull origin feature/new-test

# Если есть конфликты, разрешите их и закоммитьте:
git add .
git commit -m "Merge remote changes"

# Затем push
git push origin feature/new-test
```

### Вариант 3: Force push (осторожно!)

Если вы уверены, что хотите перезаписать удаленные изменения:

```bash
# НЕ используйте это, если другие работают с веткой!
git push --force origin feature/new-test
```

## Рекомендуемый workflow

### При работе с feature веткой:

1. **Перед началом работы** - синхронизируйтесь с удаленной веткой:
   ```bash
   git pull --rebase origin feature/new-test
   ```

2. **Делайте изменения** в коде

3. **Коммитьте изменения**:
   ```bash
   git add .
   git commit -m "Your changes"
   ```

4. **Перед push** - снова синхронизируйтесь:
   ```bash
   git pull --rebase origin feature/new-test
   ```

5. **Push изменений**:
   ```bash
   git push origin feature/new-test
   ```

## Что делает GitHub Actions

GitHub Actions workflow автоматически:
1. Создает overlay в `envs/feature-{name}/`
2. Коммитит изменения в ту же feature ветку
3. Пушит изменения в удаленный репозиторий

Поэтому после первого push в feature ветку, GitHub Actions создаст overlay и закоммитит его, что приведет к расхождению локальной и удаленной веток.

## Избежание конфликтов

### Стратегия 1: Дайте GitHub Actions завершиться

После первого push в feature ветку:
1. Дождитесь завершения GitHub Actions workflow
2. Выполните `git pull --rebase` перед следующим коммитом

### Стратегия 2: Используйте отдельные коммиты для overlay

Если вы вручную создаете overlay, используйте специальное сообщение:
```bash
git commit -m "[skip ci] Manual overlay creation"
```

Это предотвратит запуск GitHub Actions workflow.

### Стратегия 3: Создавайте overlay вручную

Вместо автоматического создания через GitHub Actions, можно создать overlay вручную перед первым pushом:

```bash
# Создайте overlay вручную
mkdir -p envs/feature-new-test
cp envs/_templates/feature-kustomization.yaml envs/feature-new-test/kustomization.yaml
sed -i '' 's/FEATURE_NAME/new-test/g' envs/feature-new-test/kustomization.yaml

# Закоммитьте overlay
git add envs/feature-new-test/
git commit -m "Add overlay for feature-new-test"

# Push
git push origin feature/new-test
```

## Проверка статуса

```bash
# Проверить статус локальной ветки
git status

# Сравнить с удаленной веткой
git fetch origin
git log HEAD..origin/feature/new-test  # Коммиты в удаленной ветке
git log origin/feature/new-test..HEAD  # Коммиты в локальной ветке

# Посмотреть последние коммиты
git log --oneline --graph --all -10
```

## Часто задаваемые вопросы

### Q: Почему GitHub Actions создает коммиты в моей ветке?

A: Это часть автоматизации - GitHub Actions создает overlay для вашей feature ветки автоматически, чтобы ArgoCD мог развернуть приложение.

### Q: Можно ли отключить автоматическое создание overlay?

A: Да, можно создать overlay вручную перед первым pushом, или изменить workflow чтобы он не коммитил изменения.

### Q: Что делать, если постоянно возникают конфликты?

A: Используйте `git pull --rebase` перед каждым pushом, или создавайте overlay вручную перед первым коммитом.

### Q: Можно ли использовать merge вместо rebase?

A: Да, но rebase создает более чистую историю коммитов. Используйте то, что удобнее для вашей команды.

## Лучшие практики

1. **Всегда синхронизируйтесь перед pushом**: `git pull --rebase origin feature/name`
2. **Проверяйте статус перед коммитом**: `git status`
3. **Используйте осмысленные сообщения коммитов**: это поможет в отладке
4. **Следите за GitHub Actions**: проверяйте, что workflow завершился успешно
5. **Создавайте overlay вручную**: если хотите избежать автоматических коммитов

