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

- Material 3 foundation.
- Light, simple, and fluid interactions.
- System-aware light and dark themes.
- Leaf-inspired dark green, warm neutral backgrounds, and a restrained fresh-green accent.
- The product name remains `Stay Leafy` in both supported languages.

## Supported languages

- English
- Spanish

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
