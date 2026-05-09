import { Component, OnInit } from '@angular/core';
import { CdkDragDrop, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { TaskService } from '../../services/task.service';
import { ProjectService } from '../../services/project.service';
import { Client } from '@stomp/stompjs';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { ConfirmService } from '../../services/confirm.service';

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
  showProjectSettingsModal = false;
  selectedTask: any = null;
  riskAnalysis: any = null;
  analyzingRisk = false;
  newTask = { title: '', description: '', priority: 'Medium', assigneeId: null };
  inviteEmail = '';
  inviteRole = 'MEMBER';
  projectSettings = { name: '', description: '', methodology: 'Agile', newOwnerId: null as number | null };
  searchResults: any[] = [];
  
  currentUserRole: string = 'MEMBER';
  currentUserId: number | null = null;
  availableRoles = ['OWNER', 'ADMIN', 'MEMBER', 'VIEWER'];

  // Project Health Indicators
  get unassignedTasksCount(): number {
    return [...this.todo, ...this.inProgress, ...this.inReview].filter(t => !t.assignee).length;
  }

  get highPriorityTasksCount(): number {
    return [...this.todo, ...this.inProgress, ...this.inReview].filter(t => t.priority === 'High' || t.priority === 'Critical').length;
  }

  get overloadedMembersCount(): number {
    return this.projectMembers.filter(m => this.getMemberWorkload(m.user.id) >= 5).length;
  }

  get totalTasksCount(): number {
    return this.todo.length + this.inProgress.length + this.inReview.length + this.completed.length;
  }

  constructor(
    private taskService: TaskService,
    private projectService: ProjectService,
    private route: ActivatedRoute,
    private authService: AuthService,
    private router: Router,
    private toastService: ToastService,
    private confirmService: ConfirmService
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
      next: (project) => {
        this.project = project;
        this.projectSettings = {
          name: project.name || '',
          description: project.description || '',
          methodology: project.methodology || 'Agile',
          newOwnerId: null
        };
      },
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
        const currentUser = this.authService.getCurrentUser();
        this.currentUserId = currentUser?.id || null;
        const myMembership = memberships.find(m => 
          (currentUser?.id && m.user.id === currentUser.id) || 
          (currentUser?.email && m.user.email === currentUser.email)
        );
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
        this.searchResults = [];
        this.toastService.success('Member added successfully');
      },
      error: (err) => this.toastService.error(err.error?.message || err.error || 'Failed to invite')
    });
  }

  onSearchUsers(query: string): void {
    if (query.length < 2) {
      this.searchResults = [];
      return;
    }
    this.authService.searchUsers(query).subscribe({
      next: (users) => {
        // Filter out users who are already members
        const memberEmails = this.projectMembers.map(m => m.user.email);
        this.searchResults = users.filter(u => !memberEmails.includes(u.email));
      }
    });
  }

  selectUser(user: any): void {
    this.inviteEmail = user.email;
    this.searchResults = [];
  }

  updateMemberRole(userId: number, role: string): void {
    this.projectService.updateMemberRole(this.projectId, userId, role).subscribe({
      next: () => {
        this.loadProjectMembers();
        this.toastService.success('Role updated successfully');
      },
      error: (err) => this.toastService.error(err.error?.message || err.error || 'Failed to update role')
    });
  }

  removeMember(userId: number): void {
    this.confirmService.confirm({
      title: 'Remove Member',
      message: 'Are you sure you want to remove this member?',
      confirmText: 'Remove',
      type: 'danger'
    }).then(confirmed => {
      if (!confirmed) return;
      this.projectService.removeMember(this.projectId, userId).subscribe({
        next: () => {
          this.loadProjectMembers();
          this.toastService.success('Member removed');
        },
        error: (err) => this.toastService.error(err.error?.message || err.error || 'Failed to remove member')
      });
    });
  }

  canManageMembers(): boolean {
    return this.currentUserRole === 'OWNER' || this.currentUserRole === 'ADMIN';
  }

  isOwner(): boolean {
    return this.currentUserRole === 'OWNER';
  }

  openProjectSettings(): void {
    if (!this.project) return;
    this.projectSettings = {
      name: this.project.name || '',
      description: this.project.description || '',
      methodology: this.project.methodology || 'Agile',
      newOwnerId: null
    };
    this.showProjectSettingsModal = true;
  }

  saveProjectSettings(): void {
    this.projectService.updateProject(this.projectId, this.projectSettings).subscribe({
      next: (project) => {
        this.project = project;
        this.showProjectSettingsModal = false;
        this.toastService.success('Project settings updated');
        this.loadProject();
      },
      error: (err) => this.toastService.error(err.error?.message || err.error || 'Failed to update project')
    });
  }

  transferOwnership(): void {
    if (!this.projectSettings.newOwnerId) return;
    this.confirmService.confirm({
      title: 'Transfer Ownership',
      message: 'Transfer project ownership to this member?',
      confirmText: 'Transfer',
      type: 'primary'
    }).then(confirmed => {
      if (!confirmed) return;
      this.projectService.transferOwnership(this.projectId, this.projectSettings.newOwnerId as number).subscribe({
        next: () => {
          this.toastService.success('Ownership transferred');
          this.showProjectSettingsModal = false;
          this.loadProjectMembers();
        },
        error: (err) => this.toastService.error(err.error?.message || err.error || 'Failed to transfer ownership')
      });
    });
  }

  deleteProject(): void {
    this.confirmService.confirm({
      title: 'Delete Project',
      message: `Delete project "${this.project?.name}" and all of its tasks? This action cannot be undone.`,
      confirmText: 'Delete Project',
      type: 'danger'
    }).then(confirmed => {
      if (!confirmed) return;
      this.projectService.deleteProject(this.projectId).subscribe({
        next: () => {
          this.toastService.success('Project deleted');
          this.router.navigate(['/dashboard']);
        },
        error: (err) => this.toastService.error(err.error?.message || err.error || 'Failed to delete project')
      });
    });
  }

  isAssignee(task: any): boolean {
    return task.assignee?.id === this.currentUserId;
  }

  canReassign(): boolean {
    return this.currentUserRole === 'OWNER' || this.currentUserRole === 'ADMIN';
  }

  canApprove(): boolean {
    return this.currentUserRole === 'OWNER' || this.currentUserRole === 'ADMIN';
  }

  approveTask(taskId: number): void {
    if (!this.canApprove()) return;
    this.taskService.updateTaskStatus(taskId, 'COMPLETED').subscribe({
      next: () => {
        this.toastService.success('Task approved and completed');
        this.loadTasks();
      },
      error: (err) => this.toastService.error(err.error?.message || 'Failed to approve task')
    });
  }

  sendBackTask(taskId: number): void {
    if (!this.canApprove()) return;
    this.taskService.updateTaskStatus(taskId, 'IN_PROGRESS').subscribe({
      next: () => {
        this.toastService.success('Task sent back for revision');
        this.loadTasks();
      },
      error: (err) => this.toastService.error(err.error?.message || 'Failed to send back task')
    });
  }

  canEditTask(task: any): boolean {
    const isCreator = task.creator && task.creator.id === this.currentUserId;
    const isManager = this.currentUserRole === 'OWNER' || this.currentUserRole === 'ADMIN';
    const isAssignee = task.assignee?.id === this.currentUserId;
    return isCreator || isManager || isAssignee;
  }

  canDeleteTask(task: any): boolean {
    const isCreator = task.creator && task.creator.id === this.currentUserId;
    const isAdmin = this.currentUserRole === 'OWNER' || this.currentUserRole === 'ADMIN';
    return isCreator || isAdmin;
  }

  getMemberWorkload(userId: number): number {
    const allTasks = [...this.todo, ...this.inProgress, ...this.inReview];
    return allTasks.filter(t => t.assignee?.id === userId).length;
  }

  suggestAssignee(): void {
    if (this.projectMembers.length === 0) return;
    
    const taskPriority = this.newTask.priority || 'Medium';
    const taskTitle = this.newTask.title.toLowerCase();
    const taskDesc = this.newTask.description.toLowerCase();

    // Scoring system for members
    const scores = this.projectMembers.map(member => {
      let score = 0;
      const workload = this.getMemberWorkload(member.user.id);
      const skills = (member.user.skills || '').toLowerCase().split(',').map((s: string) => s.trim());
      
      // 1. Workload penalty (heavy)
      score -= workload * 10;
      if (workload >= 5) score -= 50; // Overload penalty

      // 2. Skill matching (bonus)
      skills.forEach((skill: string) => {
        if (skill && (taskTitle.includes(skill) || taskDesc.includes(skill))) {
          score += 30;
        }
      });

      // 3. Priority matching
      if (taskPriority === 'High' || taskPriority === 'Critical') {
        // Prefer more experienced or specific role members for high priority if workload allows
        if (member.projectRole === 'ADMIN' || member.projectRole === 'OWNER') score += 10;
      }

      return { member, score };
    });

    // Sort by score descending
    scores.sort((a, b) => b.score - a.score);
    
    if (scores.length > 0) {
      this.newTask.assigneeId = scores[0].member.user.id;
      this.toastService.info(`Suggested ${scores[0].member.user.name} based on workload and skills`);
    }
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
      
      // Move task rule: Only Assignee can move the task
      if (!this.isAssignee(task)) {
        this.toastService.warning('Only the assignee can move this task', 'Permission Denied');
        return;
      }

      transferArrayItem(event.previousContainer.data, event.container.data, event.previousIndex, event.currentIndex);
      this.taskService.updateTaskStatus(task.id, newStatus).subscribe({
        error: (err: any) => { 
          this.loadTasks(); 
          if(err.status === 403) {
            this.toastService.error('Only Owners or Admins can validate task completion!', 'Restricted');
          } else {
            this.toastService.error(err.error?.message || 'Failed to update status');
          }
        }
      });
    }
  }

  updateTaskAssignee(taskId: number, assigneeId: any): void {
    const id = assigneeId === 'null' ? null : +assigneeId;
    this.taskService.updateTaskAssignee(taskId, id).subscribe({
      next: (updatedTask) => {
        if (this.selectedTask && this.selectedTask.id === taskId) {
          this.selectedTask = updatedTask;
        }
        this.toastService.success('Assignee updated');
      },
      error: (err) => this.toastService.error(err.error?.message || 'Error updating assignee')
    });
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
        this.toastService.success(`Successfully generated ${tasks.length} tasks!`, 'AI Planner');
        this.loadTasks();
      },
      error: (err: any) => this.toastService.error(err.error?.error || 'Unknown error', 'AI Error')
    });
  }

  updateTaskDetails(): void {
    if (!this.selectedTask) return;
    this.taskService.updateTask(this.selectedTask.id, {
      title: this.selectedTask.title,
      description: this.selectedTask.description
    }).subscribe({
      next: () => {
        this.showTaskDetailModal = false;
        this.toastService.success('Task updated successfully');
      },
      error: (err) => this.toastService.error(err.error?.message || 'Failed to update task')
    });
  }

  deleteTask(taskId: number): void {
    this.confirmService.confirm({
      title: 'Delete Task',
      message: 'Are you sure you want to delete this task?',
      confirmText: 'Delete',
      type: 'danger'
    }).then(confirmed => {
      if (!confirmed) return;
      this.taskService.deleteTask(taskId).subscribe({
        next: () => {
          this.loadTasks();
          this.showTaskDetailModal = false;
          this.selectedTask = null;
          this.toastService.success('Task deleted');
        },
        error: (err) => this.toastService.error(err.error?.message || 'Failed to delete task')
      });
    });
  }

  createTask(): void {
    if (!this.newTask.title) return;
    const taskToCreate = { ...this.newTask, projectId: this.projectId };
    this.taskService.createTask(taskToCreate).subscribe({
      next: (res) => {
        this.showCreateTaskModal = false;
        this.newTask = { title: '', description: '', priority: 'Medium', assigneeId: null };
        this.toastService.success('Task created');
        // The WebSocket will trigger loadTasks() automatically
      },
      error: (err) => {
        this.toastService.error(err.error?.message || err.error?.error || 'Failed to create task');
      }
    });
  }
}
