# NEXUS — Reliability Engineering Platform

NEXUS is a small but convincing reliability and orchestration platform designed to handle background work processing, worker process health, and version deployment with high durability, bounded recoveries, and audit-trail visibility.

---

## 1. Architecture

```
                ┌──────────────────────┐
                │      Producer        │
                │                      │
                │ Creates work/jobs     │
                └──────────┬───────────┘
                           │
                           │ HTTP
                           ▼
                ┌──────────────────────┐
                │        NEXUS         │
                │    Spring Boot       │
                │                      │
                │ Work Manager         │
                │ Retry Manager        │
                │ Worker Manager       │
                │ Release Manager      │
                │ Audit/Event Manager  │
                └──────────┬───────────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
              ▼            ▼            ▼
          Worker-1     Worker-2     Worker-3
          (Java App)   (Java App)   (Java App)
              │            │            │
              └────────────┼────────────┘
                           │
                           ▼
                ┌──────────────────────┐
                │      SQLite DB        │
                │                      │
                │ Work                 │
                │ Workers              │
                │ Releases             │
                │ Events               │
                │ Attempts             │
                └──────────────────────┘

                           ▲
                           │
                ┌──────────────────────┐
                │    React Dashboard   │
                │                      │
                │ System status        │
                │ Work                 │
                │ Workers              │
                │ Releases             │
                │ Timeline             │
                │ Failure controls     │
                └──────────────────────┘
```

---

## 2. Core Design Decisions

- **Durability**: Accepted work is saved to SQLite *before* acknowledging the request, guaranteeing that jobs survive platform restarts.
- **Idempotency**: Workers maintain a local `.json` transaction file recording job executions, preventing repeat side effects if a job is delivered multiple times.
- **Bounded Retry**: Job failures trigger exponential backoff retries. Once retries hit their threshold, the job is marked `FAILED` rather than looping forever.
- **Bounded Worker Recovery**: When a worker crashes, NEXUS restarts it with backoff. If it fails 5 times, it is placed `OUT_OF_SERVICE` to prevent infinite resource loops. A 30s settling period ensures a worker is truly healthy before resetting its budget.
- **Deterministic Rollback**: NEXUS tracks release metadata (`previousVersion` vs `currentVersion`) enabling a single-click action to stop the faulty release and restore the previous version.
- **Audit Trails**: Every state change (crashes, attempts, rollbacks, accepts) is written to a structured event log table.

---

## 3. Requirements Mapping

| Requirement | Implementation Details | Demo Action |
|:---|:---|:---|
| **R-01 Accepted work is safe** | Persisted to SQLite before HTTP 202 is returned | Restart NEXUS JVM; jobs remain |
| **R-02 Work ends somewhere** | Terminal states `SUCCESS` or `FAILED` | Check failed work panel |
| **R-03 Double delivery harmless** | Worker tracks completed IDs in local JSON | Send duplicate job via Failure Lab |
| **R-04 Retry has limit** | Job retries capped (max 5) with exponential backoff | Set worker to Crash; watch retries stop |
| **R-05 Ask about the past** | Structured event log table in SQLite | View timeline on Dashboard |
| **R-06 Releases undone** | Metadata tracks current/previous versions | Click Rollback in Release Panel |
| **R-07 Releases linked** | Release ID stamped on worker events | Compare release & crash times on timeline |
| **R-11 Recovery does no harm** | Health checks run every 2s; settling checks for 30s | Bounded restart limits and OUT_OF_SERVICE |
| **R-12 Guess within 90s** | Dashboard displays active incidents and status | Read status color & alerts on header |
| **R-15 Triggerable failures** | Failure modes (SLOW, CRASH, CRASH_ON_START) | Click mode buttons in Failure Lab |

---

## 4. How to Run

Ensure Java 21 and Node (with npm) are installed. Run entirely on one machine without internet.

### Quick Start (Automatic Script)
```bash
# Start backend, workers, and frontend
./scripts/start.sh
```
Access the dashboard at **http://localhost:5173**.

### Stop the System
```bash
# Stops all processes cleanly
./scripts/stop.sh
```

### Reset System
```bash
# Halt processes and delete database/logs
./scripts/reset.sh
```

---

## 5. Demo Scenarios

### Scenario 1 — NEXUS Restart
1. Go to the dashboard, click **Send Job** (custom ID: `job-restart-test`).
2. Verify the job appears in the queue as `PENDING`.
3. Kill NEXUS by running `./scripts/stop.sh` (or Ctrl+C on java backend).
4. Start NEXUS again: `./scripts/start.sh`.
5. Open the dashboard; `job-restart-test` is still there.

### Scenario 2 — Worker Crash
1. In the **Worker Panel** on Worker-1, click **Crash**.
2. Submit a job from the **Failure Lab**.
3. Watch the worker health status go from `RUNNING` ➔ `FAILED` ➔ `RESTARTING` ➔ `RUNNING`.
4. Observe the job retry history count increase.

### Scenario 3 — Permanent Worker Failure
1. On Worker-2, click **Crash Start** (sets failure mode `CRASH_ON_START`).
2. The health monitor will observe the crash and attempt 5 restarts with backoff.
3. After the 5th attempt, the worker transitions to `OUT_OF_SERVICE`. It will not be restarted again.

### Scenario 4 — Duplicate Work
1. Click **Send Job** to run a job.
2. In the Failure Lab, click **Send Duplicate** using the same Job ID.
3. Check the worker log `worker-worker-1.log` or the console output. It will print:
   `Idempotency match: Job Already Processed. Skipping side effects.`
   No side effects are executed twice.

### Scenario 5 — Bad Release & Rollback
1. In the Release Panel, deploy version `v2`.
2. Set Worker-3 to **Crash** (representing a bad version release).
3. The Release status will show `WATCHING`, then detect the crash event, changing status to `DEPLOYMENT FAILED`.
4. The timeline will correlate the crash with `Release #X`.
5. Click **Rollback** to instantly restore workers to `v1`.
