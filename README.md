# 🚀 ProManager: AI-Powered Project Management System

ProManager is a modern, full-stack project management application designed to streamline workflows, enhance collaboration, and leverage AI for intelligent task generation, transparent resource allocation, and risk analysis.

## ✨ Features

- **📊 Dynamic Kanban Board**: Advanced drag-and-drop workflow with explicit **Approval Workflows** (Approve/Reject) for project managers.
- **🤖 Smart AI Task Generation**: Automatically generate realistic tasks with **Intelligent Assignment** based on member skills and workload. Includes **AI Assignment Intel** to explain the reasoning behind each suggestion.
- **🔍 AI Risk Analysis**: Proactive identification of bottlenecks, overloaded team members, and potential delays with actionable recommendations.
- **🛡️ Admin Control Center**:
    - **Audit & History Logs**: Full accountability with a searchable timeline of system-wide actions.
    - **System Health & Insights**: Real-time monitoring of project health, unassigned tasks, and idle resources.
    - **Bulk Actions**: Streamlined management with bulk delete and update capabilities.
- **👤 Personal Workspace**:
    - **Grouped My Tasks**: Focus-oriented views sorted by status (Start Now, In Progress) or urgency (Due Today, This Week).
    - **Profile & Skill Management**: Direct skill updates to improve AI assignment relevance.
- **👥 Advanced Team Management**: Granular role-based access (OWNER, ADMIN, MEMBER, VIEWER) with visual workload balancing.
- **🎨 Modern UI/UX**: High-performance Angular interface with custom CSS3 animations, real-time WebSocket updates, and intuitive navigation.
- **🔒 Security & Safety**: JWT-based authentication, functional route guards, normalized error responses, and database-level safeguards against system lockouts.

## 🛠️ Tech Stack

### Frontend
- **Framework**: Angular 17
- **Styling**: Bootstrap 5, Custom CSS3 Animations
- **Icons**: Bootstrap Icons
- **Real-time**: WebSockets (STOMP/SockJS)

### Backend
- **Framework**: Spring Boot 3
- **Database**: PostgreSQL
- **Security**: Spring Security + JWT
- **ORM**: Spring Data JPA (Hibernate)

### AI Service
- **Framework**: FastAPI (Python)
- **AI Engine**: Groq Cloud (Llama 3.3)
- **Validation**: Pydantic

### Infrastructure
- **Containerization**: Docker & Docker Compose

## 🚀 Getting Started

### Prerequisites
- [Docker](https://www.docker.com/products/docker-desktop/) and [Docker Compose](https://docs.docker.com/compose/install/) installed.
- (Optional) [Groq API Key](https://console.groq.com/) for AI features.

### Quick Start with Docker

1. **Clone the repository**:
   ```bash
   git clone https://github.com/AyhamKsouri/project-manager.git
   cd project-manager
   ```

2. **Configure Environment Variables**:
   Update the `docker-compose.yml` file with your `GROQ_API_KEY` if you have one.

3. **Run the application**:
   ```bash
   docker compose up --build
   ```

4. **Access the application**:
   - **Frontend**: [http://localhost](http://localhost)
   - **Backend API**: [http://localhost:8080](http://localhost:8080)
   - **AI Service**: [http://localhost:8000](http://localhost:8000)

## 📁 Project Structure

```text
project-manager/
├── frontend/          # Angular application
├── backend/           # Spring Boot API
├── ai-service/        # Python AI microservice
├── docker-compose.yml # Orchestration for all services
└── README.md          # Project documentation
```

## 🤝 Contributing

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

---
Developed by [Ayham Ksouri](https://github.com/AyhamKsouri)
