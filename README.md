# ProManager: AI-Powered Project Management

ProManager is a full-stack project management application for teams that want task planning, workload visibility, and AI-assisted delivery workflows in one place. It combines an Angular 17 frontend, a Spring Boot backend, a PostgreSQL database, and a FastAPI AI service powered by Groq.

The latest version includes AI-powered CV skill extraction: users can upload a PDF resume, review extracted skills, add or remove suggestions, and save the final skill list to their profile. Those profile skills are then used by the project AI features to make better task assignment decisions.

## Features

- Dynamic Kanban board with drag-and-drop task movement, approval actions, project roles, and per-column scrolling.
- AI task generation that creates realistic project tasks and explains suggested assignments based on member skills and workload.
- AI project risk analysis for bottlenecks, overloaded members, likely delays, and recommendations.
- AI CV analyzer that extracts skills from PDF resumes using PyMuPDF and Groq Llama 3.3.
- Profile skill review flow with removable skill chips, manual skill entry, and save-to-profile confirmation.
- Sprint grouped view for tasks organized by sprint or backlog.
- Project member management with OWNER, ADMIN, MEMBER, and VIEWER roles.
- Admin control center for users, projects, tasks, logs, and system-level actions.
- JWT authentication, route guards, role checks, and backend authorization services.
- Docker Compose setup for frontend, backend, PostgreSQL, and AI service.

## Tech Stack

### Frontend

- Angular 17
- Standalone Angular component support for the profile workflow
- Bootstrap 5 and Bootstrap Icons
- Angular CDK drag and drop
- STOMP WebSocket client for project refresh events

### Backend

- Spring Boot 3
- Spring Security with JWT authentication
- Spring Data JPA and Hibernate
- PostgreSQL
- REST proxy endpoints for AI task generation, risk analysis, and CV skill extraction

### AI Service

- Python FastAPI
- Groq API with Llama 3.3
- PyMuPDF for PDF text extraction
- Pydantic response models
- Strict JSON parsing and normalization for AI responses

### Infrastructure

- Docker
- Docker Compose
- Nginx container for the Angular production build

## Project Structure

```text
project-manager/
|-- frontend/                 Angular 17 application
|   |-- src/app/components/   UI components, including Kanban and Profile
|   |-- src/app/services/     API services, including AI and auth services
|   `-- Dockerfile            Angular build and Nginx runtime image
|-- backend/                  Spring Boot API
|   |-- src/main/java/com/pm/controller/
|   |-- src/main/java/com/pm/security/
|   `-- Dockerfile
|-- ai-service/               FastAPI AI microservice
|   |-- main.py               AI task, risk, and CV extraction endpoints
|   |-- requirements.txt
|   `-- Dockerfile
|-- docker-compose.yml        Multi-service local environment
|-- .env                      Local secrets and environment variables
`-- README.md
```

## Environment Variables

Create or update `.env` in the repository root:

```env
JWT_SECRET=your-long-jwt-secret
GROQ_API_KEY=your-groq-api-key
```

`GROQ_API_KEY` is required for real AI responses. Some AI endpoints include demo/mock fallbacks when the key is missing, but real CV skill extraction should be tested with a valid Groq key.

## Run With Docker

Build and start the full stack:

```bash
docker compose up --build
```

Or rebuild a specific service after changes:

```bash
docker compose build frontend
docker compose up -d frontend
```

Service URLs:

- Frontend: http://localhost:8081
- Backend API: http://localhost:8080
- AI service: http://localhost:8000
- PostgreSQL: localhost:5432

Seeded demo login:

```text
admin@pm.com / password
```

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
