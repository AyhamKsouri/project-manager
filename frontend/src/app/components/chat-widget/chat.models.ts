export interface Message {
  role: 'user' | 'assistant';
  content: string;
}

export interface SprintTaskContext {
  id: number | string;
  title: string;
  status?: string;
  priority?: string;
  assignee?: string | { id?: number; name?: string; email?: string } | null;
  sprintName?: string | null;
}

export interface TeamMemberContext {
  id?: number | string;
  username?: string;
  name?: string;
  email?: string;
}

export interface SprintContext {
  sprintId: number | string;
  projectId?: number | string;
  sprintName?: string;
  tasks: SprintTaskContext[];
  teamMembers: TeamMemberContext[];
}

export interface ChatRequest {
  message: string;
  sprintContext: SprintContext;
  conversationHistory: Message[];
}

export interface BotResponse {
  reply: string;
  actionTaken: boolean;
  intent: string;
  parameters?: {
    taskId?: string | null;
    title?: string | null;
    status?: string | null;
    priority?: string | null;
    assignee?: string | null;
    targetSprintId?: string | null;
  };
}
