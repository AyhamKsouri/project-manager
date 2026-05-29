import { EventEmitter, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { BotResponse, ChatRequest, Message, SprintContext } from './chat.models';

@Injectable({ providedIn: 'root' })
export class SprintRefreshService {
  refreshRequested = new EventEmitter<void>();

  requestRefresh(): void {
    this.refreshRequested.emit();
  }
}

@Injectable({ providedIn: 'root' })
export class ChatContextService {
  private contextSubject = new BehaviorSubject<SprintContext | null>(null);
  context$ = this.contextSubject.asObservable();

  get context(): SprintContext | null {
    return this.contextSubject.value;
  }

  setContext(context: SprintContext): void {
    this.contextSubject.next(context);
  }

  clearContext(): void {
    this.contextSubject.next(null);
  }
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  private apiUrl = '/ai-service/chat';
  private historySubject = new BehaviorSubject<Message[]>([
    {
      role: 'assistant',
      content: '[Result]\nProManager AI Active.\n\n[Analysis]\nI am now integrated as your elite Agile workspace teammate. I am monitoring team velocity, workload distribution, and sprint health.\n\n[Recommendation]\nOpen a project workspace to begin high-performance coordination.'
    }
  ]);

  history$ = this.historySubject.asObservable();

  constructor(private http: HttpClient, private sprintRefreshService: SprintRefreshService) {}

  get history(): Message[] {
    return this.historySubject.value;
  }

  sendMessage(message: string, sprintContext: SprintContext): Observable<BotResponse> {
    const userMessage: Message = { role: 'user', content: message };
    const conversationHistory = this.history.filter(item => item.content.trim()).slice(-20);
    this.historySubject.next([...this.history, userMessage]);

    const payload: ChatRequest = {
      message,
      sprintContext,
      conversationHistory
    };

    return this.http.post<BotResponse>(this.apiUrl, payload).pipe(
      tap(response => {
        this.historySubject.next([
          ...this.history,
          { role: 'assistant', content: response.reply }
        ]);

        if (response.actionTaken) {
          this.sprintRefreshService.requestRefresh();
        }
      })
    );
  }

  addAssistantMessage(content: string): void {
    this.historySubject.next([...this.history, { role: 'assistant', content }]);
  }
}
