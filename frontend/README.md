# PayLens frontend

Angular 19 + TypeScript + Angular Material shell for the PayLens HR salary app.

## Run

```bash
npm start
```

Dev server: `http://localhost:4200`  
API proxy: `/api` → `http://localhost:8081` (see `proxy.conf.json`)

## Scripts

| Command | Purpose |
| --- | --- |
| `npm start` | Dev server with proxy |
| `npm run build` | Production build |
| `npm run test:ci` | Headless unit tests |

## Structure

- `layout/shell` — sidenav + header ("PayLens")
- `features/*` — dashboard / employees placeholders
- `services/` — `EmployeeService`, `AnalyticsService` (HTTP only here)
- `models/` — typed API interfaces
- `shared/` — loading / error / page header
- `environments/` — `apiBaseUrl`
