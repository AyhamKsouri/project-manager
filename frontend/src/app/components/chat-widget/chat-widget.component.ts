import { CommonModule } from '@angular/common';
import { AfterViewChecked, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ChatContextService, ChatService, NO_PROJECT_MESSAGE } from './chat.service';
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
    { label: 'Santé du sprint', message: 'Génère un score de santé du sprint et identifie les blocages.' },
    { label: 'Charge équipe', message: 'Analyse la capacité de l’équipe et propose un meilleur équilibrage.' },
    { label: 'Risques à venir', message: 'Détecte les risques du sprint et les échéances menacées.' },
    { label: 'Assignation IA', message: 'Suggère les meilleures personnes pour les tâches non assignées.' }
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
      this.chatService.addAssistantMessage(NO_PROJECT_MESSAGE);
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
      error: () => {
        this.isTyping = false;
        this.shouldScroll = true;
        if (!this.isOpen) this.unreadCount += 1;
      }
    });
  }
}
