# Apofeoz Admin Web

Web admin panel for `ADMIN` role.

## Stack

- Next.js (App Router)
- TypeScript

## Local run

```bash
cd web
npm install
npm run dev
```

Environment:

- `NEXT_PUBLIC_API_BASE_URL` (default: `http://localhost:8080`)

## Features (v1)

- Login / logout with JWT + refresh flow.
- Tabs: Workers, Users, Reports, Profile.
- Reports table by date range.
- Download `timesheet.xlsx`.

