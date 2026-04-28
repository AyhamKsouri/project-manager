import { Component, OnInit } from '@angular/core';
import { CdkDragDrop, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { TaskService } from '../../services/task.service';
import { ProjectService } from '../../services/project.service';
import { Client } from '@stomp/stompjs';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-kanban',
  templateUrl: './kanban.component.html'
})
export class KanbanComponent implements OnInit {
  projectId: number = 0;
  project: any = null;
  todo: any[] = []; inProgress: any[] = []; inReview: any[] = []; completed: any[] = [];
  projectMembers: any[] = [];
  
  // Phase 5: Sprint View Data
  sprints: string[] = [];
  tasksBySprint: { [key: string]: any[] } = {};
  selectedSprint: string = 'All Sprints';
  activeView: 'kanban' | 'sprint' = 'kanban';

  private stompClient: any = null;

  showCreateTaskModal = false;
  showRiskAnalysisModal = false;
  showTaskDetailModal = false;
  showMembersModal = false;
  showInviteModal = false;
  selectedTask: any = null;
  riskAnalysis: any = null;
  analyzingRisk = false;
  newTask = { title: '', description: '', priority: 'Medium', assigneeId: null };
  inviteEmail = '';
  inviteRole = 'MEMBER';
  
  currentUserRole: string = 'MEMBER';
  availableRoles = ['OWNER', 'ADMIN', 'MEMBER', 'VIEWER'];

  get totalTasksCount(): number {
    return this.todo.length + this.inProgress.length + this.inReview.length + this.completed.length;
  }

  constructor(
    private taskService: TaskService,
    private projectService: ProjectService,
    private route: ActivatedRoute,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (!this.authService.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }
    this.route.params.subscribe((params: any) => {
      this.projectId = +params['id'];
      this.loadProject();
      this.loadTasks();
      this.loadProjectMembers();
      this.connectWebSocket();
    });
  }

  openTaskDetail(task: any): void {
    this.selectedTask = task;
    this.showTaskDetailModal = true;
  }

  closeTaskDetail(): void {
    this.showTaskDetailModal = false;
    this.selectedTask = null;
  }

  analyzeRisk(): void {
    this.analyzingRisk = true;
    this.showRiskAnalysisModal = true;
    this.projectService.analyzeProjectRisk(this.projectId).subscribe({
      next: (analysis) => {
        this.riskAnalysis = analysis;
        this.analyzingRisk = false;
      },
      error: (err) => {
        console.error('Error analyzing risk:', err);
        this.analyzingRisk = false;
      }
    });
  }

  loadProject(): void {
    this.projectService.getProject(this.projectId).subscribe({
      next: (project) => this.project = project,
      error: (err) => console.error('Error loading project:', err)
    });
  }

  loadTasks(): void {
    this.taskService.getTasksByProject(this.projectId).subscribe({
      next: (tasks: any[]) => {
        console.log('Tasks loaded:', tasks);
        this.todo = tasks.filter(t => t.status === 'TODO');
        this.inProgress = tasks.filter(t => t.status === 'IN_PROGRESS');
        this.inReview = tasks.filter(t => t.status === 'IN_REVIEW');
        this.completed = tasks.filter(t => t.status === 'COMPLETED');
        
        // Phase 5: Group tasks by sprint
        this.groupTasksBySprint(tasks);
      },
      error: (err) => {
        console.error('Error loading tasks:', err);
      }
    });
  }

  loadProjectMembers(): void {
    this.projectService.getProjectMembers(this.projectId).subscribe({
      next: (memberships: any[]) => {
        this.projectMembers = memberships;
        // Determine current user's role
        const currentUserId = this.authService.getCurrentUser()?.id;
        const myMembership = memberships.find(m => m.user.id === currentUserId);
        if (myMembership) {
          this.currentUserRole = myMembership.projectRole;
        }
      },
      error: (err) => {
        console.error('Error loading project members:', err);
      }
    });
  }

  inviteMember(): void {
    if (!this.inviteEmail) return;
    this.projectService.addMember(this.projectId, this.inviteEmail, this.inviteRole).subscribe({
      next: () => {
        this.loadProjectMembers();
        this.showInviteModal = false;
        this.inviteEmail = '';
      },
      error: (err) => alert('Error: ' + (err.error?.message || err.error || 'Failed to invite'))
    });
  }

  updateMemberRole(userId: number, role: string): void {
    this.projectService.updateMemberRole(this.projectId, userId, role).subscribe({
      next: () => this.loadProjectMembers(),
      error: (err) => alert('Error: ' + (err.error?.message || err.error || 'Failed to update role'))
    });
  }

  removeMember(userId: number): void {
    if (!confirm('Are you sure you want to remove this member?')) return;
    this.projectService.removeMember(this.projectId, userId).subscribe({
      next: () => this.loadProjectMembers(),
      error: (err) => alert('Error: ' + (err.error?.message || err.error || 'Failed to remove member'))
    });
  }

  canManageMembers(): boolean {
    return this.currentUserRole === 'OWNER' || this.currentUserRole === 'ADMIN';
  }

  getMemberWorkload(userId: number): number {
    const allTasks = [...this.todo, ...this.inProgress, ...this.inReview];
    return allTasks.filter(t => t.assignee?.id === userId).length;
  }

  suggestAssignee(): void {
    // Basic auto-assignment logic based on workload
    if (this.projectMembers.length === 0) return;
    
    let bestMember = this.projectMembers[0];
    let minWorkload = this.getMemberWorkload(bestMember.user.id);

    this.projectMembers.forEach(m => {
      const workload = this.getMemberWorkload(m.user.id);
      if (workload < minWorkload) {
        minWorkload = workload;
        bestMember = m;
      }
    });

    this.newTask.assigneeId = bestMember.user.id;
  }

  groupTasksBySprint(tasks: any[]): void {
    const groups: { [key: string]: any[] } = {};
    const sprintNames: Set<string> = new Set();

    tasks.forEach(task => {
      const sprint = task.sprintName || 'Backlog';
      if (!groups[sprint]) {
        groups[sprint] = [];
      }
      groups[sprint].push(task);
      sprintNames.add(sprint);
    });

    this.tasksBySprint = groups;
    this.sprints = Array.from(sprintNames).sort((a, b) => {
      if (a === 'Backlog') return 1;
      if (b === 'Backlog') return -1;
      return a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' });
    });
  }

  switchView(view: 'kanban' | 'sprint'): void {
    this.activeView = view;
  }

  connectWebSocket(): void {
    const token = localStorage.getItem('token');
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    
    // Add token as query parameter for environments where headers aren't supported during handshake
    const brokerURL = `${protocol}//${host}/ws${token ? '?token=' + encodeURIComponent(token) : ''}`;
    
    this.stompClient = new Client({
      brokerURL: brokerURL,
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      debug: (str) => {
        console.log('STOMP Debug:', str);
      },
      onConnect: () => {
        console.log('Connected to WebSocket');
        this.stompClient.subscribe(`/topic/project/${this.projectId}`, (message: any) => {
          console.log('WebSocket message received:', message.body);
          this.loadTasks();
        });
      },
      onStompError: (frame) => {
        console.error('Broker reported error: ' + frame.headers['message']);
        console.error('Additional details: ' + frame.body);
      },
      onWebSocketError: (event) => {
        console.error('WebSocket error:', event);
      }
    });
    this.stompClient.activate();
  }

  drop(event: CdkDragDrop<any[]>, newStatus: string): void {
    if (event.previousContainer === event.container) { moveItemInArray(event.container.data, event.previousIndex, event.currentIndex); } 
    else {
      const task = event.previousContainer.data[event.previousIndex];
      transferArrayItem(event.previousContainer.data, event.container.data, event.previousIndex, event.currentIndex);
      this.taskService.updateTaskStatus(task.id, newStatus).subscribe({
        error: (err: any) => { this.loadTasks(); if(err.status === 403) alert('Only Owners or Admins can validate task completion!'); }
      });
    }
  }

  requestAiTasks(): void {
    if (!this.project) return;
    
    // Format team skills: "Name:Skill1,Skill2; Name2:Skill3"
    // Use real user skills and active task counts for smarter assignment
    const teamData = this.projectMembers.map(m => {
      const skills = m.user.skills || 'Developer';
      const activeTasks = this.getMemberWorkload(m.user.id);
      return `${m.user.name}:${skills} (${activeTasks} active tasks)`;
    }).join('; ');

    const projectDesc = this.project.description || this.project.name;
    const methodology = this.project.methodology || 'Agile';

    this.taskService.generateAiTasks(this.projectId, projectDesc, teamData, methodology).subscribe({
      next: (tasks: any[]) => {
        alert(`Successfully generated ${tasks.length} tasks!`);
        // The WebSocket will trigger loadTasks() automatically, 
        // but we can also do it manually just in case
        this.loadTasks();
      },
      error: (err: any) => alert('Error: ' + (err.error?.error || 'Unknown error'))
    });
  }

  createTask(): void {
    if (!this.newTask.title) return;
    const taskToCreate = { ...this.newTask, projectId: this.projectId };
    this.taskService.createTask(taskToCreate).subscribe({
      next: (res) => {
        console.log('Task created:', res);
        this.showCreateTaskModal = false;
        this.newTask = { title: '', description: '', priority: 'Medium', assigneeId: null };
        // The WebSocket will trigger loadTasks() automatically
      },
      error: (err) => {
        console.error('Error creating task:', err);
      }
    });
  }
}
