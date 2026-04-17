# UI Reference

Этот раздел не используется как исполняемое приложение, но используется как **каноничная UI-документация для агентов**.

Назначение: хранить ссылку на исходный дизайн, зафиксированные токены и правила того, какой визуальный язык считать основным.

## Источник дизайна

- Figma: https://www.figma.com/design/GGZ0QZl68wUlAJPAJvRaiR/%D0%9C%D0%BE%D0%B1%D0%B8%D0%BB%D1%8C%D0%BD%D0%BE%D0%B5-%D0%BF%D1%80%D0%B8%D0%BB%D0%BE%D0%B6%D0%B5%D0%BD%D0%B8%D0%B5-%D0%B4%D0%BB%D1%8F-%D0%90%D0%9F%D0%9E%D0%A4%D0%95%D0%9E%D0%97

## Где находится актуальная UI-реализация

- Основной visual source of truth: `web/` (текущая web-админка).
- Android Compose theme: `android/app/src/main/java/com/apofeoz/shiftmanager/presentation/theme/`
- HTML мокапы экранов: `docs/ui-mockups/`
- Документация с мокапами: `docs/mobile_mockup.html`, `docs/mobile_presentation.html`

## Что считать главным стилем сейчас

- Зафиксирован основной стиль: dark graphite + gold accent + premium dashboard language.
- Этот стиль уже реализован в `web/` и должен использоваться как референс для следующих UI-задач.
- Старые мобильные мокапы и ранние описания экранов считать полезными по сценариям, но не по финальной визуальной подаче.

## Правило для разработки

- Не добавлять сюда runtime-код, JS-зависимости, Vite/Tailwind-конфиги.
- При редизайне Android ориентироваться на `web/` и `THEME_TOKENS.md`.
