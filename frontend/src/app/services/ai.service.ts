import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AiService {
  private apiUrl = '/api/ai';

  constructor(private http: HttpClient) { }

  generateTasks(projectId: number, projectDescription: string, teamSkills: string, methodology: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/generate-tasks`, {
      projectId,
      projectDescription,
      teamSkills,
      methodology
    });
  }

  analyzeProjectRisk(projectId: number): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/${projectId}/analyze-risk`, {});
  }

  analyzeCv(file: File): Observable<string[]> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<string[]>(`${this.apiUrl}/analyze-cv`, formData);
  }
}
