import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DragDropModule } from '@angular/cdk/drag-drop';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { LoginComponent } from './components/login/login.component';
import { KanbanComponent } from './components/kanban/kanban.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { AdminComponent } from './components/admin/admin.component';
import { MyTasksComponent } from './components/my-tasks/my-tasks.component';
import { ConfirmModalComponent } from './components/confirm-modal/confirm-modal.component';
import { ProfileComponent } from './components/profile/profile.component';
import { NotificationsComponent } from './components/notifications/notifications.component';
import { TaskCardComponent } from './components/task-card/task-card.component';
import { ChatWidgetComponent } from './components/chat-widget/chat-widget.component';
import { ModalComponent } from './components/ui/modal/modal.component';
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { AiService } from './services/ai.service';

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    KanbanComponent,
    DashboardComponent,
    AdminComponent,
    MyTasksComponent,
    ConfirmModalComponent
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    FormsModule,
    DragDropModule,
    AppRoutingModule,
    ProfileComponent,
    NotificationsComponent,
    TaskCardComponent,
    ChatWidgetComponent,
    ModalComponent
  ],
  providers: [
    AiService,
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
