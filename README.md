# 🚀 ProManager: AI-Powered Project Management System

ProManager is a modern, full-stack project management application designed to streamline workflows, enhance collaboration, and leverage AI for intelligent task generation and risk analysis.

## ✨ Features

- **📊 Dynamic Kanban Board**: Drag-and-drop tasks across different statuses (To Do, In Progress, Review, Done).
- **🤖 AI Task Generation**: Automatically generate realistic tasks based on project descriptions and team skills using the Groq AI API.
- **🔍 AI Risk Analysis**: Identify bottlenecks, overloaded team members, and potential delays with AI-driven insights.
- **👥 Team Management**: Assign roles (CHEF, MEMBER) and manage project collaborators.
- **📈 Real-time Statistics**: Track project progress with live task and member counts.
- **🎨 Modern UI/UX**: Clean, responsive interface with smooth animations and intuitive design.
- **🔒 Secure Authentication**: JWT-based authentication with role-based access control.

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
