import { EventEmitter, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, of, tap } from 'rxjs';
import { BotResponse, ChatRequest, Message, SprintContext } from './chat.models';

export const CHAT_GREETING =
  "Bonjour ! Ouvre un projet et je m'occupe du reste — charge d'équipe, santé du sprint, risques, tâches non assignées.";

export const NO_PROJECT_MESSAGE =
  "Aucun projet ouvert. Ouvre un projet pour que je puisse analyser les données.";

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
      content: CHAT_GREETING
    }
  ]);

  history$ = this.historySubject.asObservable();

  constructor(private http: HttpClient, private sprintRefreshService: SprintRefreshService) {}

  get history(): Message[] {
    return this.historySubject.value;
  }

  formatChatError(err: unknown): string {
    console.error('[ChatService] chat API error:', err);

    const httpErr = err as { error?: { detail?: string | Array<{ msg?: string }> }; message?: string; status?: number };
    const detail = httpErr?.error?.detail;

    if (typeof detail === 'string' && detail.trim()) {
      return detail;
    }
    if (Array.isArray(detail)) {
      const messages = detail.map(item => item?.msg).filter(Boolean);
      if (messages.length) {
        return messages.join(' ');
      }
    }
    if (httpErr?.status === 0) {
      return "Impossible de joindre l'assistant. Vérifiez votre connexion.";
    }
    if (httpErr?.status === 429) {
      return 'Trop de requêtes. Attendez quelques secondes et réessayez.';
    }
    return "Une erreur est survenue. Réessayez dans un instant.";
  }

  sendMessage(message: string, sprintContext: SprintContext | null): Observable<BotResponse> {
    const userMessage: Message = { role: 'user', content: message };
    const conversationHistory = this.history.filter(item => item.content.trim()).slice(-20);
    this.historySubject.next([...this.history, userMessage]);

    const payload: ChatRequest = {
      message,
      sprintContext: sprintContext ?? undefined,
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
      }),
      catchError(err => {
        const errorMessage = this.formatChatError(err);
        this.historySubject.next([
          ...this.history,
          { role: 'assistant', content: errorMessage }
        ]);
        return of({
          reply: errorMessage,
          actionTaken: false,
          intent: 'UNKNOWN'
        } as BotResponse);
      })
    );
  }

  addAssistantMessage(content: string): void {
    this.historySubject.next([...this.history, { role: 'assistant', content }]);
  }
}
