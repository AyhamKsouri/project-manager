# ProManager: AI-Powered Project Management

ProManager is a full-stack project management application for teams that want task planning, workload visibility, and AI-assisted delivery workflows in one place. It combines an Angular 17 frontend, a Spring Boot backend, a PostgreSQL database, and a FastAPI AI service powered by Groq.

The application features a **10/10 Premium UI/UX** inspired by modern SaaS platforms like Linear and Raycast, focusing on precision, depth, and micro-interactions.

## Features

- **Premium UI/UX**: High-end design with a refined Zinc color palette, geometric radius scale, and multi-layered elevation system.
- **Dynamic Kanban Board**: Advanced board with glassmorphic headers, real-time health metrics, and smooth drag-and-drop interactions.
- **AI Task Generation**: Automatically creates realistic project tasks and provides assignment reasoning based on member skills and current workload.
- **AI Risk Analysis**: Proactively identifies bottlenecks, overloaded members, and potential delays with actionable recommendations.
- **AI CV Analyzer**: Extracts skills from PDF resumes using PyMuPDF and Groq Llama 3.3 to build rich user profiles.
- **Real-time Notifications**: Integrated system for project updates, task assignments, and system alerts.
- **Sprint Management**: Grouped view for organized sprint planning and backlog management.
- **Role-Based Access Control**: Granular permissions with OWNER, ADMIN, MEMBER, and VIEWER roles.
- **Admin Control Center**: Centralized management for users, projects, tasks, and system logs.
- **Dockerized Infrastructure**: Seamless deployment using Docker Compose for all microservices.

## Design Principles

- **Precision**: Strict adherence to a geometric spacing and radius scale.
- **Depth**: Use of multi-layered shadows and radial gradients to create a professional SaaS feel.
- **Micro-interactions**: Snappy, tactile feedback on all interactive elements using premium cubic-bezier timings.
- **Consistency**: Unified design system across authentication, dashboard, and kanban views.

## Tech Stack

### Frontend

- Angular 17 (Standalone Components)
- Premium CSS System (SCSS Variables, Custom Animations)
- Bootstrap Icons & Lucide-style SVG icons
- Angular CDK (Drag & Drop)
- STOMP WebSocket for real-time synchronization

### Backend

- Spring Boot 3
- Spring Security (JWT Authentication)
- Spring Data JPA (PostgreSQL)
- REST Proxy for AI microservices

### AI Service

- Python FastAPI
- Groq API (Llama 3.3)
- PyMuPDF (PDF Processing)
- Pydantic for strict data validation

### Infrastructure

- Docker & Docker Compose
- Nginx (Production runtime for Angular)

## Project Structure

```text
project-manager/
|-- frontend/                 Premium Angular 17 application
|   |-- src/styles/           Unified Design System (Variables, Utilities)
|   |-- src/app/components/   Refined UI Components (Kanban, Task Cards, Auth)
|   `-- Dockerfile            Optimized Nginx build
|-- backend/                  Spring Boot API
|   |-- src/main/java/com/pm/config/ Data Seeding & Migrations
|   `-- Dockerfile
|-- ai-service/               FastAPI AI microservice
|   |-- main.py               AI task & risk analysis logic
|   `-- Dockerfile
|-- docker-compose.yml        Full stack orchestration
|-- .env                      Secrets management
`-- README.md
```

## Environment Variables

Create or update `.env` in the repository root:

```env
JWT_SECRET=your-long-jwt-secret
GROQ_API_KEY=your-groq-api-key
```

## Getting Started

### Prerequisites

- Docker and Docker Desktop (Windows/Mac)
- Git

### Run with Docker

1. **Clone the repository**:
   ```bash
   git clone https://github.com/AyhamKsouri/project-manager.git
   cd project-manager
   ```

2. **Configure environment**:
   Copy `.env.example` to `.env` and add your keys.

3. **Start the stack**:
   ```bash
   docker-compose up -d --build
   ```

4. **Access the application**:
   - **Frontend**: [http://localhost:8081](http://localhost:8081)
   - **Backend**: [http://localhost:8080](http://localhost:8080)
   - **AI Service**: [http://localhost:8001](http://localhost:8001)

### Default Credentials

- **Admin**: `admin@pm.com` / `password`
- **Chef (Owner)**: `chef@pm.com` / `password`
- **Member**: `member@pm.com` / `password`

- Frontend: http://localhost:8081
- Backend API: http://localhost:8080
- AI service: http://localhost:8000
- PostgreSQL: localhost:5432

Seeded demo logins (password for all: `password`):

```text
admin@promanager.demo   — Alexandre Rousseau (Admin, recommandé pour la démo jury)
admin@pm.com            — Administrateur (alias legacy)
sophie.martin@promanager.demo — Product Owner (NovaShop)
lucas.bernard@promanager.demo — Backend (NovaShop, FitTrack)
emma.dubois@promanager.demo   — Frontend (NovaShop, Portail RH)
thomas.leroy@promanager.demo  — DevOps
```

On each backend startup, demo data is reset when `pm.app.reseed-on-startup=true` (default).

## Testing The CV Skill Analyzer

1. Start the stack with Docker Compose.
2. Open http://localhost:8081/profile.
3. Sign in with the seeded admin account or another user.
4. Use the AI CV Analyzer card to upload a text-based PDF resume.
5. Review the extracted skills.
6. Remove unwanted skills, add missing skills manually, then confirm and save.

If extraction fails:

- Check that the PDF is text-based and not only a scanned image.
- Check AI service logs:

```bash
docker compose logs -f ai-service
```

- Check backend proxy logs:

```bash
docker compose logs -f backend
```

Common issues are missing `GROQ_API_KEY`, malformed AI responses, or PDFs with no extractable text.

## Useful Docker Commands

```bash
docker compose ps
docker compose logs -f frontend
docker compose logs -f backend
docker compose logs -f ai-service
docker compose down
```

## Development Notes

- The frontend Dockerfile uses `npm install --legacy-peer-deps` and invokes the local Angular CLI directly with `./node_modules/.bin/ng build`.
- The profile UI is implemented as a standalone Angular component to avoid module dependency friction.
- The backend `/api/ai/analyze-cv` endpoint accepts PDF uploads, forwards them to the FastAPI service, and returns a normalized `List<String>`.
- The AI service extracts PDF text with PyMuPDF, asks Groq for strict JSON skill data, and normalizes the response before returning it.
- The Kanban route uses a fixed-height workspace so task columns scroll internally instead of making the whole page scroll.

## License

Distributed under the MIT License. See `LICENSE` for more information.

---

Developed by Ayham Ksouri.
