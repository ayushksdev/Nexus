# NEXUS Platform — Account Metadata

This document contains repository and author information for the NEXUS platform submission.

---

## 1. Project Information
- **Project Name**: NEXUS
- **Purpose**: Reliability Engineering & Resiliency Platform
- **Build Stack**: Java 21, Spring Boot 3.2.5, Spring Data JPA, SQLite, React, Vite, Tailwind CSS

---

## 2. Author Information
- **Author Name**: [Developer Placeholder]
- **Role**: Senior Resiliency/Backend Engineer
- **Email**: [Developer Email Placeholder]
- **Organization**: [Organization Placeholder]

---

## 3. Repository Information
- **Repository URI**: `/Users/mack/Desktop/Code/Intershala/Nexus`
- **Main Branch**: `main`
- **Local SQLite DB Location**: `backend/nexus.db`

---

## 4. Quick Demo Instructions
To run all test scenarios from cold start:
1. Start the system:
   ```bash
   ./scripts/start.sh
   ```
2. Navigate to the Operator Dashboard at `http://localhost:5173`.
3. Submit a job from the **Failure Lab** panel and verify it reaches success.
4. Set Worker-1 to **Crash** and publish another job to observe retries.
5. Deploy **v2** from the Release panel. Inject crashes to fail the release watch check and click **Rollback** to revert to **v1**.
6. Stop the system:
   ```bash
   ./scripts/stop.sh
   ```
