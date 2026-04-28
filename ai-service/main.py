import os
import json
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from groq import Groq

app = FastAPI(title="Project Manager AI Service")

GROQ_API_KEY = os.getenv("GROQ_API_KEY")
client = Groq(api_key=GROQ_API_KEY) if GROQ_API_KEY else None

class TaskRequest(BaseModel):
    projectDescription: str
    teamSkills: str
    methodology: str

class TaskSuggestion(BaseModel):
    title: str
    description: str
    assigned_to: str
    status: str = "todo"
    priority: str
    story_points: int
    estimated_days: int
    deadline_offset_days: int
    sprint: str
    depends_on: list[str] = []

class TaskResponse(BaseModel):
    tasks: list[TaskSuggestion]

class RiskAnalysisRequest(BaseModel):
    tasks: list[dict]
    team_members: list[str]

class RiskAnalysisResponse(BaseModel):
    bottlenecks: list[str]
    overloaded_members: list[str]
    likely_delays: list[str]
    recommendations: list[str]

@app.post("/generate-tasks", response_model=TaskResponse)
async def generate_tasks(request: TaskRequest):
    if not client:
        raise HTTPException(status_code=500, detail="GROQ_API_KEY not configured on server")

    prompt = f"""
    You are an expert project manager AI. Based on the following project, suggest a list of highly realistic and actionable tasks.
    
    Project Description: {request.projectDescription}
    Methodology: {request.methodology}
    Team Skills (format: name:skill1,skill2; name2:skill3): {request.teamSkills}
    
    CRITICAL RULES:
    1. Assign each task to the most qualified team member based on their listed skills.
    2. priority: Must be one of: "low", "medium", "high", "critical".
    3. story_points: Use the Fibonacci scale (1, 2, 3, 5, 8, 13).
    4. estimated_days: Provide a realistic duration for the task.
    5. deadline_offset_days: The number of days from the project start date by which the task should be completed.
    6. sprint: Group tasks into logical sprints like "Sprint 1", "Sprint 2", etc.
    7. description: Provide a clear, detailed description of what needs to be done.
    8. depends_on: List the titles of other tasks that MUST be completed before this task can start. If no dependencies, return an empty list [].
    
    Respond ONLY in JSON format matching this structure:
    {{
        "tasks": [
            {{
                "title": "Clear and concise title",
                "description": "Detailed explanation of task requirements",
                "assigned_to": "Team member name",
                "status": "todo",
                "priority": "low/medium/high/critical",
                "story_points": 5,
                "estimated_days": 3,
                "deadline_offset_days": 10,
                "sprint": "Sprint 1",
                "depends_on": ["Task Title A", "Task Title B"]
            }}
        ]
    }}
    """

    try:
        chat_completion = client.chat.completions.create(
            messages=[
                {"role": "system", "content": "You are a helpful project management assistant designed to output strict JSON."},
                {"role": "user", "content": prompt}
            ],
            model="llama-3.3-70b-versatile",
            response_format={"type": "json_object"},
            temperature=0.5,
            max_tokens=2048,
        )
        content = chat_completion.choices[0].message.content
        parsed_json = json.loads(content)
        
        # Simple validation to ensure 'tasks' key exists
        if "tasks" not in parsed_json:
             raise ValueError("AI response missing 'tasks' field")
             
        return TaskResponse(**parsed_json)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"AI Generation Error: {str(e)}")

@app.post("/analyze-project-risk", response_model=RiskAnalysisResponse)
async def analyze_project_risk(request: RiskAnalysisRequest):
    if not client:
        raise HTTPException(status_code=500, detail="GROQ_API_KEY not configured on server")

    prompt = f"""
    You are an expert Project Risk Analyst. Analyze the following project data and identify risks.
    
    Team Members: {request.team_members}
    Tasks Data: {request.tasks}
    
    Analyze:
    1. Bottlenecks: Which tasks or dependencies are causing the most friction?
    2. Overloaded Members: Who has too many story points or high-priority tasks?
    3. Likely Delays: Which tasks are overdue or at high risk of missing deadlines?
    4. Recommendations: Provide actionable advice to mitigate these risks.
    
    Respond ONLY in JSON format matching this structure:
    {{
        "bottlenecks": ["description of bottleneck 1", "..."],
        "overloaded_members": ["Member Name: reason", "..."],
        "likely_delays": ["Task Title: reason", "..."],
        "recommendations": ["Actionable advice 1", "..."]
    }}
    """

    chat_completion = client.chat.completions.create(
        messages=[
            {
                "role": "system",
                "content": "You are a professional project risk analyzer that outputs only valid JSON.",
            },
            {
                "role": "user",
                "content": prompt,
            }
        ],
        model="llama-3.3-70b-versatile",
        response_format={"type": "json_object"},
        max_tokens=1500,
    )

    return RiskAnalysisResponse.model_validate_json(chat_completion.choices[0].message.content)

@app.get("/health")
async def health_check():
    return {"status": "healthy"}
