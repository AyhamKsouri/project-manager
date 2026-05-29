import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';
import { AiService } from '../../services/ai.service';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class ProfileComponent implements OnInit {
  @ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;

  user: any = null;
  newSkills: string = '';
  loading = false;
  analyzing = false;
  suggestion: string[] = []; 
  manualSkill: string = '';
  showSuggestion = false; 
  analysisError = '';

  constructor(
    private authService: AuthService,
    private aiService: AiService,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.loadProfile();
  }

  loadProfile(): void {
    this.loading = true;
    this.authService.getUserDetails().subscribe({
      next: (user) => {
        this.user = user;
        this.newSkills = user.skills || '';
        this.loading = false;
      },
      error: () => {
        this.toastService.error('Échec du chargement du profil');
        this.loading = false;
      }
    });
  }

  saveSkills(): void {
    this.loading = true;
    this.authService.updateSkills(this.newSkills).subscribe({
      next: (user) => {
        this.user = user;
        this.toastService.success('Compétences mises à jour avec succès');
        this.loading = false;
      },
      error: () => {
        this.toastService.error('Échec de la mise à jour des compétences');
        this.loading = false;
      }
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    this.handleSelectedFile(file);
  }

  onFileDropped(event: DragEvent): void {
    event.preventDefault();
    this.handleSelectedFile(event.dataTransfer?.files?.[0]);
  }

  private handleSelectedFile(file?: File): void {
    
    if (!file) {
      return;
    }

    const fileName = file.name.toLowerCase();
    const isPdf = file.type === 'application/pdf' || fileName.endsWith('.pdf');
    
    if (!isPdf) {
      this.toastService.error('Seuls les fichiers PDF sont autorisés');
      this.resetFileInput();
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      this.toastService.error('La taille du fichier dépasse la limite de 10 Mo');
      this.resetFileInput();
      return;
    }

    this.analyzeCv(file);
    this.resetFileInput();
  }

  analyzeCv(file: File): void {
    this.analyzing = true;
    this.showSuggestion = false;
    this.suggestion = [];
    this.analysisError = '';
    this.aiService.analyzeCv(file).subscribe({
      next: (skills) => {
        this.suggestion = Array.isArray(skills)
          ? skills
              .filter((skill): skill is string => typeof skill === 'string' && skill.trim().length > 0)
              .map(skill => skill.trim())
          : [];
        this.showSuggestion = true;
        this.analyzing = false;
        if (this.suggestion.length === 0) {
          this.toastService.info('Aucune compétence n\'a été trouvée. Vous pouvez les ajouter manuellement.');
        } else {
          this.toastService.success('CV analysé avec succès');
        }
      },
      error: (err) => {
        this.analysisError = this.resolveAnalysisError(err);
        this.toastService.error(this.analysisError);
        this.analyzing = false;
      }
    });
  }

  removeSuggestedSkill(index: number): void {
    this.suggestion.splice(index, 1);
  }

  addManualSkill(): void {
    const skill = this.manualSkill.trim();
    if (skill && !this.suggestion.some(existing => existing.toLowerCase() === skill.toLowerCase())) {
      this.suggestion.push(skill);
      this.manualSkill = '';
    }
  }

  applySuggestion(): void {
    const techSkills = this.suggestion.join(', ');
    if (techSkills) {
      this.newSkills = techSkills;
      this.saveSkills();
    }
    this.showSuggestion = false;
  }

  simulateAnalysis(): void {
    this.analyzing = true;
    this.showSuggestion = false;
    this.analysisError = '';
    
    // Simulate a more realistic multi-step AI process for better testing
    const steps = [
      'Extraction du texte du PDF...',
      'Identification des mots-clés techniques...',
      'Correspondance avec l\'expertise professionnelle...',
      'Génération de suggestions...'
    ];
    
    let currentStep = 0;
    const interval = setInterval(() => {
      if (currentStep < steps.length) {
        this.toastService.info(steps[currentStep], 'AI Analyzer');
        currentStep++;
      } else {
        clearInterval(interval);
        this.suggestion = [
          'Angular', 
          'TypeScript', 
          'RxJS', 
          'State Management', 
          'REST API Design', 
          'Docker', 
          'CI/CD Pipelines', 
          'Unit Testing (Jasmine/Karma)',
          'UI/UX Design Systems'
        ];
        this.showSuggestion = true;
        this.analyzing = false;
        this.toastService.success('Analyse terminée ! 9 compétences pertinentes identifiées.', 'Succès IA');
      }
    }, 800);
  }

  clearAnalysisError(): void {
    this.analysisError = '';
  }

  private resetFileInput(): void {
    if (this.fileInput?.nativeElement) {
      this.fileInput.nativeElement.value = '';
    }
  }

  private resolveAnalysisError(err: any): string {
    if (err?.status === 0) {
      return 'Le service d\'IA est injoignable. Veuillez réessayer quand l\'analyseur sera de nouveau en ligne.';
    }

    const detail = err?.error?.detail || err?.error?.error || err?.message;
    if (typeof detail === 'string' && detail.trim()) {
      return detail;
    }

    return 'Impossible d\'analyser ce PDF. Veuillez essayer un PDF textuel ou ajouter les compétences manuellement.';
  }
}
