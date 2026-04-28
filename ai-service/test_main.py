import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch, MagicMock
from main import app

client = TestClient(app)

def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200

@patch("main.client")
def test_generate_tasks_success(mock_groq_client):
    mock_response = MagicMock()
    mock_response.choices[0].message.content = '{"tasks": [{"title": "Setup DB", "assigned_to": "Member", "status": "todo"}]}'
    mock_groq_client.chat.completions.create.return_value = mock_response

    response = client.post("/generate-tasks", json={
        "projectDescription": "Build a web app",
        "teamSkills": "Member:java,angular",
        "methodology": "Agile"
    })
    assert response.status_code == 200
