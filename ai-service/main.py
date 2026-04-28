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

class ProjectPlanRequest(BaseModel):
    idea: str
    methodology: str
    team_members: list[dict] = []  # List of {name: str, skills: str, current_workload: int}

class ProjectPlanResponse(BaseModel):
    product_summary: str
    target_users: list[str]
    key_features: list[str]
    recommended_team_roles: list[str]
    timeline_estimate_weeks: int
    epics: list[str]
    milestones: list[str]
    sprint_roadmap: list[str]
    prioritized_tasks: list[TaskSuggestion]
    risks: list[str]

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

@app.post("/generate-project-plan", response_model=ProjectPlanResponse)
async def generate_project_plan(request: ProjectPlanRequest):
    if not client:
        raise HTTPException(status_code=500, detail="GROQ_API_KEY not configured on server")

    prompt = f"""
    You are a Senior Product Manager and Technical Lead. Transform the following raw idea into a professional, execution-ready project plan.
    
    Raw Idea: {request.idea}
    Methodology: {request.methodology}
    Team Members: {request.team_members}
    
    Your goal is to extract real business intent and create a high-quality SaaS/Mobile/Web product roadmap.
    
    RULES:
    1. Product Summary: Concise explanation of the product's value proposition.
    2. Target Users: Who will use this product?
    3. Key Features: Strategic features that define the MVP and beyond.
    4. Team Roles: Suggest roles based on the project needs.
    5. Timeline: Realistic estimate in weeks.
    6. Backlog: Generate at least 8-10 prioritized tasks. 
       CRITICAL: Assign tasks to real team members from the provided list based on their skills and workload. 
       If no members are provided or they don't fit, use placeholders like "Frontend Dev", "Backend Dev".
    7. Roadmap: Define the sprint sequence according to {request.methodology}.
    
    Respond ONLY in JSON format matching this structure:
    {{
        "product_summary": "...",
        "target_users": ["...", "..."],
        "key_features": ["...", "..."],
        "recommended_team_roles": ["...", "..."],
        "timeline_estimate_weeks": 12,
        "epics": ["...", "..."],
        "milestones": ["...", "..."],
        "sprint_roadmap": ["...", "..."],
        "prioritized_tasks": [
            {{
                "title": "...",
                "description": "...",
                "assigned_to": "Member 1",
                "status": "todo",
                "priority": "high",
                "story_points": 5,
                "estimated_days": 3,
                "deadline_offset_days": 7,
                "sprint": "Sprint 1",
                "depends_on": []
            }}
        ],
        "risks": ["...", "..."]
    }}
    """

    try:
        chat_completion = client.chat.completions.create(
            messages=[
                {"role": "system", "content": "You are a senior product lead designed to output strict, high-quality project plans in JSON format."},
                {"role": "user", "content": prompt}
            ],
            model="llama-3.3-70b-versatile",
            response_format={"type": "json_object"},
            temperature=0.7,
            max_tokens=4096,
        )
        content = chat_completion.choices[0].message.content
        return ProjectPlanResponse.model_validate_json(content)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"AI Planning Error: {str(e)}")

@app.get("/health")
async def health_check():
    return {"status": "healthy"}
