# Product vision

## Summary

Stay Leafy is a personal Android companion for managing household plants. It provides a clear inventory, a chronological record of care, and timely local reminders without requiring an account or sending personal data to a server.

## Problem

Plant care information is often scattered across memory, photographs, calendar entries, and unrelated notes. Different plants have different watering intervals and seasonal needs, which makes it easy to lose track of what was done and what is due next.

Stay Leafy puts that information in one private, local place.

## Primary user

The primary and currently only user is the app owner. Multi-user access, social functionality, and administrative roles are outside the product scope.

## Product principles

1. **Local first** — Core functionality never depends on connectivity or an external account.
2. **Fast to update** — Recording a watering or adding a note should require very little effort.
3. **Calm, not demanding** — Reminders should be useful, deduplicated, and easy to act on.
4. **History over guesswork** — The app records what happened and derives the next expected care date from user-defined rules.
5. **Simple architecture** — Technical complexity must be justified by current product needs.
6. **User-controlled knowledge** — Care frequencies and seasonal guidance are configured by the user; the app does not claim to diagnose plants or prescribe care automatically.

## Core experience

The user opens Stay Leafy and sees their plants, including each plant's primary image and care status. From a plant detail screen, the user can review information, add dated notes, inspect care history, and mark watering, pruning, or fertilizing as complete. Completing interval-based care automatically calculates the next due date. Seasonal rules notify the user when an annual care window begins.

## Visual direction

- Material 3 provides the interaction and accessibility foundation, while the visual identity remains recognizably Stay Leafy.
- The canonical Botanical Calm anchors are pine `#184A36`, sage `#7FA58A`, clay `#C97052`, cream `#F7F3E9`, paper `#FFFCF7`, ink `#1F2923`, dark background `#0E1A15`, and dark surface `#17271F`.
- Light and dark schemes are explicit; the default follows the system theme, with app-level Light/Dark overrides available in Settings. Dynamic system color remains disabled so the identity and care-status meanings stay stable.
- Lora is bundled locally and reserved for display headings; platform sans-serif typography keeps body copy, forms, and controls compact and readable.
- Cards use warm tonal surfaces, rounded 12/16/24 dp shapes, low elevation, and a 4/8/12/16/24/32 dp spacing rhythm.
- The reference compositions are structural guidance, not just a palette: inventory and Notice lists stay compact and image-led, Plant detail starts with a wide hero image, related information is grouped in flat sections, and editor/configuration primary actions remain pinned near the bottom edge.
- Plant context travels into note and care editors through the plant thumbnail and identity. The note editor itself exposes only back, content, and Save; note list actions remain contextual where editing and deletion are already supported.
- Terracotta is restrained to identity accents and unread/favorite emphasis. Upcoming, due-today, and overdue care each combine distinct wording with accessible semantic colors rather than relying on color alone.
- Navigation and actions use deterministic local vector icons. The cactus-and-pot master artwork is preserved under `design/brand/`; production launcher assets are derived from it without packaging the source SVG.
- The product name remains `Stay Leafy` in both supported languages.

## Photo framing and personalization

- New or replaced plant photos pass through a local interactive 1:1 crop and are stored as square JPEGs. Photos already stored before this change remain as they are.
- Settings lets the user choose System/Light/Dark appearance and System/English/Spanish language, with system-following defaults and immediate application.
- The user can choose the approximate local time for daily WorkManager reminder evaluation, initially 09:00. The operating system may defer execution; the setting does not promise an exact notification time.
- Settings summarizes notification capability, explains that camera/photo selection use system UI without broad permissions, and shows app version and local-only privacy facts.

## Supported languages

- English
- Spanish

The app offers an in-app System/English/Spanish choice without adding another supported language.

Documentation, source identifiers, and code comments are written in English. User-facing text is localized through Android resources.

## Out of scope

- Authentication and user profiles
- Cloud synchronization or remote backup
- Social features, chat, or shared gardens
- Payments, subscriptions, or advertising
- Plant identification, disease diagnosis, or generative AI
- Weather-aware automatic recommendations
- GPS, maps, Bluetooth, NFC, microphone, or unrelated sensors
- iOS, web, or desktop clients
- Google Play publishing automation

## Success criteria

Stay Leafy is successful when the owner can reliably answer:

- Which plants do I have?
- What do I know about each one?
- When did I last water, prune, or fertilize it?
- What care is due now or soon?
- What care season is beginning?
- What observations have I recorded over time?
