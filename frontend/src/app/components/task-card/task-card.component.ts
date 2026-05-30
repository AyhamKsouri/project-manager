import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DragDropModule } from '@angular/cdk/drag-drop';

@Component({
  selector: 'app-task-card',
  standalone: true,
  imports: [CommonModule, DragDropModule],
  templateUrl: './task-card.component.html',
  styleUrls: ['./task-card.component.css']
})
export class TaskCardComponent {
  @Input() task: any;
  @Input() canDrag: boolean = false;
  @Input() canApprove: boolean = false;
  @Input() isOverdue: boolean = false;

  @Output() taskClick = new EventEmitter<any>();
  @Output() approve = new EventEmitter<string>();
  @Output() reject = new EventEmitter<string>();

  onTaskClick() {
    this.taskClick.emit(this.task);
  }

  onApprove(event: Event) {
    event.stopPropagation();
    this.approve.emit(this.task.id);
  }

  onReject(event: Event) {
    event.stopPropagation();
    this.reject.emit(this.task.id);
  }

  priorityLabel(priority?: string): string {
    switch (priority?.toLowerCase()) {
      case 'critical': return 'Critique';
      case 'high': return 'Haute';
      case 'medium': return 'Moyenne';
      case 'low': return 'Basse';
      default: return priority || 'Moyenne';
    }
  }
}
