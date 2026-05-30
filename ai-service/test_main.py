import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch, MagicMock
from main import (
    SprintContext,
    SprintTaskContext,
    TeamMemberContext,
    build_project_context_payload,
    extract_json_object,
    has_open_project,
    is_scope_clarification,
    parse_groq_intent,
    project_has_task_data,
    NO_PROJECT_MESSAGE,
    app,
)

client = TestClient(app)


def test_build_project_context_payload_includes_workload_summary():
    context = SprintContext(
        projectId=1,
        sprintName="Sprint 2",
        tasks=[
            SprintTaskContext(id=1, title="API", status="IN_PROGRESS", priority="High", assignee="Lucas"),
            SprintTaskContext(id=2, title="UI", status="TODO", priority="Medium", assignee="Emma"),
            SprintTaskContext(id=3, title="Ops", status="TODO", priority="High", assignee=None),
        ],
        teamMembers=[
            TeamMemberContext(id=10, name="Lucas"),
            TeamMemberContext(id=11, name="Emma"),
        ],
    )
    payload = build_project_context_payload(context)
    assert payload["summary"]["totalTasks"] == 3
    assert payload["summary"]["unassignedTasks"] == 1
    assert payload["summary"]["workloadByMember"]["Lucas"]["assignedTaskCount"] == 1
    assert project_has_task_data(context) is True


def test_is_scope_clarification_detects_member_task_prompts():
    assert is_scope_clarification("Which team members should I analyze?") is True
    assert is_scope_clarification("Quelles tâches dois-je examiner ?") is True
    assert is_scope_clarification("Quel titre pour la nouvelle tâche ?") is False


def test_has_open_project():
    assert has_open_project(None) is False
    assert has_open_project(SprintContext(projectId=None)) is False
    assert has_open_project(SprintContext(projectId=42)) is True


def test_parse_groq_intent_extracts_embedded_json():
    raw = 'Voici la réponse {"intent": "ADVISORY", "parameters": {}, "confirmation_message": "OK", "clarification_needed": null}'
    intent = parse_groq_intent(raw)
    assert intent.intent == "ADVISORY"
    assert intent.confirmation_message == "OK"


def test_parse_groq_intent_plain_text_fallback():
    intent = parse_groq_intent("Lucas est surchargé avec 3 tâches prioritaires.")
    assert intent.intent == "ADVISORY"
    assert "Lucas" in intent.confirmation_message


def test_chat_no_project():
    response = client.post("/chat", json={"message": "Analyse la capacité", "sprintContext": None})
    assert response.status_code == 200
    assert response.json()["reply"] == NO_PROJECT_MESSAGE

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
