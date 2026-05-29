import { CommonModule } from '@angular/common';
import { AfterViewChecked, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ChatContextService, ChatService } from './chat.service';
import { Message, SprintContext } from './chat.models';

@Component({
  selector: 'app-chat-widget',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat-widget.component.html',
  styleUrls: ['./chat-widget.component.scss']
})
export class ChatWidgetComponent implements AfterViewChecked, OnDestroy, OnInit {
  @ViewChild('messageThread') messageThread?: ElementRef<HTMLDivElement>;

  isOpen = false;
  unreadCount = 0;
  draft = '';
  isTyping = false;
  sprintContext: SprintContext | null = null;
  private shouldScroll = false;
  private contextSubscription?: Subscription;

  quickActions = [
    { label: 'Sprint Health', message: 'Generate a Sprint Health Score and identify bottlenecks.' },
    { label: 'Optimize Workload', message: 'Analyze team capacity and suggest workload balancing.' },
    { label: 'Predict Risks', message: 'Detect any upcoming sprint risks or missed deadlines.' },
    { label: 'Smart Assignment', message: 'Suggest the best assignees for current unassigned tasks.' }
  ];

  constructor(public chatService: ChatService, private chatContextService: ChatContextService) {}

  get messages(): Message[] {
    return this.chatService.history;
  }

  ngOnInit(): void {
    this.sprintContext = this.chatContextService.context;
    this.contextSubscription = this.chatContextService.context$.subscribe(context => {
      this.sprintContext = context;
    });
  }

  ngOnDestroy(): void {
    this.contextSubscription?.unsubscribe();
  }

  ngAfterViewChecked(): void {
    if (this.shouldScroll && this.messageThread) {
      this.messageThread.nativeElement.scrollTop = this.messageThread.nativeElement.scrollHeight;
      this.shouldScroll = false;
    }
  }

  toggleOpen(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      this.unreadCount = 0;
      this.shouldScroll = true;
    }
  }

  minimize(): void {
    this.isOpen = false;
  }

  useQuickAction(message: string): void {
    this.draft = message;
  }

  send(): void {
    const message = this.draft.trim();
    if (!message || this.isTyping) return;

    if (!this.sprintContext) {
      this.chatService.addAssistantMessage('Please open a project workspace first. I need project context to assist you with sprint management.');
      this.draft = '';
      this.shouldScroll = true;
      return;
    }

    this.draft = '';
    this.isTyping = true;
    this.shouldScroll = true;

    this.chatService.sendMessage(message, this.sprintContext).subscribe({
      next: () => {
        this.isTyping = false;
        this.shouldScroll = true;
        if (!this.isOpen) this.unreadCount += 1;
      },
      error: (err) => {
        this.isTyping = false;
        this.chatService.addAssistantMessage(err.error?.detail || 'Je n\'ai pas pu terminer cette requête. Veuillez réessayer.');
        this.shouldScroll = true;
        if (!this.isOpen) this.unreadCount += 1;
      }
    });
  }
}
