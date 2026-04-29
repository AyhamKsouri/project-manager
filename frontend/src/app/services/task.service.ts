import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class TaskService {
  private apiUrl = '/api/tasks';
  constructor(private http: HttpClient) { }

  getTasksByProject(projectId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/project/${projectId}`);
  }

  updateTaskStatus(taskId: number, status: string): Observable<any> {
    return this.http.put(`${this.apiUrl}/${taskId}/status`, { status });
  }

  updateTaskAssignee(taskId: number, assigneeId: number | null): Observable<any> {
    return this.http.put(`${this.apiUrl}/${taskId}/assignee`, { assigneeId });
  }

  updateTask(taskId: number, taskData: any): Observable<any> {
    return this.http.put(`${this.apiUrl}/${taskId}`, taskData);
  }

  deleteTask(taskId: number): Observable<any> {
    return this.http.delete(`${this.apiUrl}/${taskId}`);
  }

  createTask(task: any): Observable<any> {
    return this.http.post<any>(this.apiUrl, task);
  }

  generateAiTasks(projectId: number, projectDesc: string, teamSkills: string, methodology: string): Observable<any> {
    return this.http.post('/api/ai/generate-tasks', { 
      projectId, 
      projectDescription: projectDesc, 
      teamSkills: teamSkills, 
      methodology: methodology 
    });
  }
}
