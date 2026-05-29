import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private apiUrl = '/api/projects';

  constructor(private http: HttpClient) { }

  getMyProjects(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/my-projects`);
  }

  createProject(project: any): Observable<any> {
    return this.http.post<any>(this.apiUrl, project);
  }

  getProject(id: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/${id}`);
  }

  updateProject(projectId: number, project: any): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${projectId}`, project);
  }

  deleteProject(projectId: number): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/${projectId}`);
  }

  transferOwnership(projectId: number, userId: number): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${projectId}/owner/${userId}`, {});
  }

  getProjectMembers(projectId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/${projectId}/members`);
  }

  addMember(projectId: number, email: string, role: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/${projectId}/members`, { email, role });
  }

  updateMemberRole(projectId: number, userId: number, role: string): Observable<any> {
    return this.http.put<any>(`${this.apiUrl}/${projectId}/members/${userId}`, { role });
  }

  removeMember(projectId: number, userId: number): Observable<any> {
    return this.http.delete<any>(`${this.apiUrl}/${projectId}/members/${userId}`);
  }
}
