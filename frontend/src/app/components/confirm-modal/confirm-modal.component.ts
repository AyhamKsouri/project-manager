import { Component, OnInit } from '@angular/core';
import { ConfirmOptions, ConfirmService } from '../../services/confirm.service';

@Component({
  selector: 'app-confirm-modal',
  templateUrl: './confirm-modal.component.html',
  styleUrls: ['./confirm-modal.component.css']
})
export class ConfirmModalComponent implements OnInit {
  options: ConfirmOptions | null = null;
  private resolve: ((result: boolean) => void) | null = null;

  constructor(private confirmService: ConfirmService) {}

  ngOnInit(): void {
    this.confirmService.confirm$.subscribe(data => {
      this.options = data.options;
      this.resolve = data.resolve;
    });
  }

  onConfirm(): void {
    if (this.resolve) {
      this.resolve(true);
      this.close();
    }
  }

  onCancel(): void {
    if (this.resolve) {
      this.resolve(false);
      this.close();
    }
  }

  private close(): void {
    this.options = null;
    this.resolve = null;
  }
}
