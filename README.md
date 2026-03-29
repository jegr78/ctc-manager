# CTC Manager

**CTC Manager** is a Gran Turismo Team vs. Team League Manager — an admin application for managing racing leagues across multiple seasons.

## Tech Stack

- Java 25, Spring Boot 4.x, Maven
- Thymeleaf (Server-side rendering)
- MariaDB (Production) / H2 (Development)
- Flyway (Database migrations)
- Google Sheets API v4 (Scorecard import)
- Playwright (E2E testing)
- Docker (Local + Production deployment)

## Features

- **Seasons & Matchdays** — League and Swiss-system formats with configurable rounds
- **Teams & Sub-Teams** — Parent/child team hierarchy with sub-team lineups per matchday
- **Drivers** — PSN ID tracking, season-team assignments, fuzzy name matching
- **Race Results & Scoring** — Position points (20-2), qualifying points (3-2-1), fastest lap bonus (2)
- **Standings & Rankings** — Season and all-time standings for teams and drivers
- **Playoffs** — Single/double elimination brackets (4/8 teams) with best-of-legs support
- **Swiss Pairing** — Automatic round generation with Buchholz tiebreaker
- **Data Import** — CSV upload and Google Sheets scorecard import with driver matching preview
- **GT7 Sync** — Scrape cars and tracks (with images) from gran-turismo.com
- **Static Site Generation** — Generate a complete static website for GitHub Pages
- **Race Attachments** — Upload files or link external resources to races
- **Docker** — Local development and production deployment with MariaDB

## Quick Start

```bash
# Development (H2 in-memory, port 9090)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Development with GT7 demo data (cars, tracks, images)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo

# Run tests
./mvnw verify

# Docker (App + MariaDB, port 8080)
docker compose up --build -d
```

## Documentation

See the [Wiki](../../wiki) for detailed documentation on architecture, features, setup, and configuration.
