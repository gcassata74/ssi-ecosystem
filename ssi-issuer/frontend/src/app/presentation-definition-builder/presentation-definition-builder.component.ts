import { Component, OnDestroy } from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { Subscription } from 'rxjs';

interface PresentationDefinition {
  id: string;
  name: string;
  purpose: string;
  format: {
    ldp_vp: {
      proof_type: string[];
    };
  };
  submission_requirements?: SubmissionRequirement[];
  input_descriptors: InputDescriptor[];
}

interface SubmissionRequirement {
  name: string;
  rule: string;
  from?: string;
}

interface InputDescriptor {
  id: string;
  name: string;
  purpose: string;
  group?: string[];
  constraints?: {
    fields: DescriptorField[];
  };
}

interface DescriptorField {
  path: string[];
  purpose?: string;
  filter?: unknown;
}

const DEFAULT_PRESENTATION_DEFINITION: PresentationDefinition = {
  id: 'staff-credential',
  name: 'Izylife Staff Credential',
  purpose: 'Share these attributes to confirm Izylife staff membership.',
  format: {
    ldp_vp: {
      proof_type: ['Ed25519Signature2018', 'EdDSA']
    }
  },
  submission_requirements: [
    {
      name: 'Provide a staff credential',
      rule: 'all',
      from: 'staff'
    }
  ],
  input_descriptors: [
    {
      id: 'staff',
      name: 'Staff identity attributes',
      purpose: 'Used to verify the employee identity before issuing a credential.',
      group: ['staff'],
      constraints: {
        fields: [
          {
            path: ['$.type'],
            filter: {
              type: 'array',
              contains: {
                const: 'PublicAuthorityStaffCredential'
              }
            }
          },
          {
            path: ['$.credentialSubject.fiscalNumber'],
            purpose: 'Employee fiscal number (e.g. TINIT-LVLDAA85T50G702B).',
            filter: {
              type: 'string',
              minLength: 1
            }
          },
          {
            path: ['$.credentialSubject.name'],
            purpose: 'Given name (e.g. Ada).',
            filter: {
              type: 'string',
              minLength: 1
            }
          },
          {
            path: ['$.credentialSubject.familyName'],
            purpose: 'Family name (e.g. Lovelace).',
            filter: {
              type: 'string',
              minLength: 1
            }
          },
          {
            path: ['$.credentialSubject.email'],
            purpose: 'Institutional email address (e.g. ada.lovelace@izylife.com).',
            filter: {
              type: 'string',
              format: 'email'
            }
          }
        ]
      }
    }
  ]
};

@Component({
  selector: 'app-presentation-definition-builder',
  templateUrl: './presentation-definition-builder.component.html',
  styleUrls: ['./presentation-definition-builder.component.scss']
})
export class PresentationDefinitionBuilderComponent implements OnDestroy {
  readonly builderForm: FormGroup;
  definitionJson?: string;
  downloadUrl?: string;
  copyFeedback?: string;
  importError?: string;
  formIssues: string[] = [];
  readonly downloadFileName = 'presentation-definition.json';

  private suspendPreviewUpdates = false;
  private formChangesSub?: Subscription;
  private latestDefinition?: PresentationDefinition;

  constructor(private readonly fb: FormBuilder) {
    this.builderForm = this.fb.group({
      id: ['', Validators.required],
      name: ['', Validators.required],
      purpose: ['', Validators.required],
      proofTypes: this.fb.array<FormControl<string>>([]),
      submissionRequirements: this.fb.array<FormGroup>([]),
      inputDescriptors: this.fb.array<FormGroup>([])
    });

    this.applyDefinition(DEFAULT_PRESENTATION_DEFINITION);
    this.formChangesSub = this.builderForm.valueChanges.subscribe(() => {
      if (this.suspendPreviewUpdates) {
        return;
      }
      this.updatePreview();
    });
    this.updatePreview();
  }

  get proofTypes(): FormArray<FormControl<string>> {
    return this.builderForm.get('proofTypes') as FormArray<FormControl<string>>;
  }

  get submissionRequirements(): FormArray<FormGroup> {
    return this.builderForm.get('submissionRequirements') as FormArray<FormGroup>;
  }

  get inputDescriptors(): FormArray<FormGroup> {
    return this.builderForm.get('inputDescriptors') as FormArray<FormGroup>;
  }

  get inputDescriptorGroups(): FormGroup[] {
    return this.inputDescriptors.controls as FormGroup[];
  }

  fieldsAt(descriptorIndex: number): FormArray<FormGroup> {
    return this.inputDescriptors.at(descriptorIndex).get('fields') as FormArray<FormGroup>;
  }

  addProofType(): void {
    this.proofTypes.push(this.fb.control('', { validators: Validators.required }));
    this.proofTypes.updateValueAndValidity();
  }

  removeProofType(index: number): void {
    if (index < 0 || index >= this.proofTypes.length) {
      return;
    }
    this.proofTypes.removeAt(index);
    if (this.proofTypes.length === 0) {
      this.proofTypes.push(this.fb.control('', { validators: Validators.required }));
    }
    this.proofTypes.updateValueAndValidity();
  }

  addSubmissionRequirement(): void {
    this.submissionRequirements.push(this.createSubmissionRequirement());
  }

  removeSubmissionRequirement(index: number): void {
    if (index < 0 || index >= this.submissionRequirements.length) {
      return;
    }
    this.submissionRequirements.removeAt(index);
  }

  addInputDescriptor(): void {
    this.inputDescriptors.push(this.createInputDescriptor());
  }

  removeInputDescriptor(index: number): void {
    if (index < 0 || index >= this.inputDescriptors.length) {
      return;
    }
    this.inputDescriptors.removeAt(index);
    if (this.inputDescriptors.length === 0) {
      this.inputDescriptors.push(this.createInputDescriptor());
    }
  }

  addField(descriptorIndex: number): void {
    this.fieldsAt(descriptorIndex).push(this.createField());
  }

  removeField(descriptorIndex: number, fieldIndex: number): void {
    const fields = this.fieldsAt(descriptorIndex);
    if (fieldIndex < 0 || fieldIndex >= fields.length) {
      return;
    }
    fields.removeAt(fieldIndex);
    if (fields.length === 0) {
      fields.push(this.createField());
    }
  }

  resetToSample(): void {
    this.applyDefinition(DEFAULT_PRESENTATION_DEFINITION);
  }

  loadDefinition(definition: PresentationDefinition): void {
    this.applyDefinition(definition);
  }

  loadDefinitionFromJson(json: string): void {
    try {
      const parsed = JSON.parse(json) as PresentationDefinition;
      this.importError = undefined;
      this.applyDefinition(parsed);
    } catch (error) {
      console.error('Unable to parse presentation definition', error);
      this.importError = 'Unable to parse presentation definition JSON.';
    }
  }

  exportDefinition(): PresentationDefinition | undefined {
    const issues: string[] = [];
    const definition = this.buildDefinition(issues);
    this.formIssues = issues;
    if (issues.length) {
      return undefined;
    }
    return definition;
  }

  importFromFile(event: Event): void {
    const target = event.target as HTMLInputElement;
    const file = target?.files?.[0];
    target.value = '';
    if (!file) {
      return;
    }

    file
      .text()
      .then(text => {
        try {
          this.importError = undefined;
          this.loadDefinitionFromJson(text);
        } catch (error) {
          console.error('Unable to import presentation definition', error);
          this.importError = 'The selected file does not contain valid JSON.';
        }
      })
      .catch(error => {
        console.error('Unable to read presentation definition file', error);
        this.importError = 'An error occurred while reading the selected file.';
      });
  }

  async copyJson(): Promise<void> {
    if (!this.definitionJson) {
      return;
    }

    if (!navigator.clipboard) {
      this.copyFeedback = 'Clipboard access is not available in this browser.';
      return;
    }

    try {
      await navigator.clipboard.writeText(this.definitionJson);
      this.copyFeedback = 'JSON copied to clipboard.';
      setTimeout(() => {
        this.copyFeedback = undefined;
      }, 3000);
    } catch (error) {
      console.error('Unable to copy JSON', error);
      this.copyFeedback = 'Unable to copy JSON.';
    }
  }

  ngOnDestroy(): void {
    this.formChangesSub?.unsubscribe();
    this.revokeDownloadUrl();
  }

  private applyDefinition(definition: PresentationDefinition): void {
    if (!definition) {
      return;
    }

    this.suspendPreviewUpdates = true;
    this.builderForm.patchValue(
      {
        id: definition.id ?? '',
        name: definition.name ?? '',
        purpose: definition.purpose ?? ''
      },
      { emitEvent: false }
    );

    this.setProofTypes(definition.format?.ldp_vp?.proof_type ?? []);
    this.setSubmissionRequirements(definition.submission_requirements ?? []);
    this.setInputDescriptors(definition.input_descriptors ?? []);

    this.builderForm.markAsPristine();
    this.builderForm.markAsUntouched();
    this.suspendPreviewUpdates = false;
    this.updatePreview();
  }

  private setProofTypes(values: string[]): void {
    const target = this.proofTypes;
    clearFormArray(target);
    const proofTypes = values.length ? values : ['Ed25519Signature2018', 'EdDSA'];
    proofTypes.forEach(proofType => {
      target.push(this.fb.control(proofType, { validators: Validators.required }));
    });
    target.updateValueAndValidity({ emitEvent: false });
  }

  private setSubmissionRequirements(values: SubmissionRequirement[]): void {
    const target = this.submissionRequirements;
    clearFormArray(target);
    if (!values.length) {
      target.push(this.createSubmissionRequirement());
    } else {
      values.forEach(req => target.push(this.createSubmissionRequirement(req)));
    }
    target.updateValueAndValidity({ emitEvent: false });
  }

  private setInputDescriptors(values: InputDescriptor[]): void {
    const target = this.inputDescriptors;
    clearFormArray(target);
    if (!values.length) {
      target.push(this.createInputDescriptor());
    } else {
      values.forEach(descriptor => target.push(this.createInputDescriptor(descriptor)));
    }
    target.updateValueAndValidity({ emitEvent: false });
  }

  private createSubmissionRequirement(req?: SubmissionRequirement): FormGroup {
    return this.fb.group({
      name: [req?.name ?? '', Validators.required],
      rule: [req?.rule ?? 'all', Validators.required],
      from: [req?.from ?? '', Validators.required]
    });
  }

  private createInputDescriptor(descriptor?: InputDescriptor): FormGroup {
    const fields = descriptor?.constraints?.fields ?? [];
    const groupControl = this.fb.group({
      id: [descriptor?.id ?? '', Validators.required],
      name: [descriptor?.name ?? '', Validators.required],
      purpose: [descriptor?.purpose ?? '', Validators.required],
      group: [
        Array.isArray(descriptor?.group)
          ? descriptor?.group.join(', ')
          : typeof descriptor?.group === 'string'
          ? descriptor.group
          : ''
      ],
      fields: this.fb.array<FormGroup>([])
    });

    const fieldsArray = groupControl.get('fields') as FormArray<FormGroup>;
    if (!fields.length) {
      fieldsArray.push(this.createField());
    } else {
      fields.forEach(field => fieldsArray.push(this.createField(field)));
    }
    return groupControl;
  }

  private createField(field?: DescriptorField): FormGroup {
    const pathValue = Array.isArray(field?.path) ? field?.path.join('\n') : '';
    const filterValue =
      field?.filter !== undefined ? JSON.stringify(field.filter, null, 2) : '{"type": "string"}';
    return this.fb.group({
      path: [pathValue, Validators.required],
      purpose: [field?.purpose ?? ''],
      filter: [filterValue, jsonValidator]
    });
  }

  private updatePreview(): void {
    const issues: string[] = [];
    const definition = this.buildDefinition(issues);
    this.formIssues = issues;
    this.latestDefinition = definition;

    if (definition) {
      this.definitionJson = JSON.stringify(definition, null, 2);
      this.revokeDownloadUrl();
      this.downloadUrl = this.createDownloadUrl(this.definitionJson);
    } else {
      this.definitionJson = undefined;
      this.revokeDownloadUrl();
    }
  }

  private buildDefinition(issues: string[]): PresentationDefinition | undefined {
    const idControl = this.builderForm.get('id');
    const nameControl = this.builderForm.get('name');
    const purposeControl = this.builderForm.get('purpose');

    const id = (idControl?.value as string)?.trim();
    const name = (nameControl?.value as string)?.trim();
    const purpose = (purposeControl?.value as string)?.trim();

    if (!id) {
      issues.push('Enter an ID for the presentation definition.');
    }
    if (!name) {
      issues.push('Enter a name for the presentation definition.');
    }
    if (!purpose) {
      issues.push('Describe the purpose of the request.');
    }

    const proofTypes = this.proofTypes.controls
      .map(control => (control.value ?? '').trim())
      .filter(value => value.length > 0);
    if (!proofTypes.length) {
      issues.push('Specify at least one proof_type value (e.g. Ed25519Signature2018).');
    }

    const submissionRequirements: SubmissionRequirement[] = [];
    this.submissionRequirements.controls.forEach((group, index) => {
      const nameValue = (group.get('name')?.value as string)?.trim();
      const ruleValue = (group.get('rule')?.value as string)?.trim();
      const fromValue = (group.get('from')?.value as string)?.trim();

      const filledValues = [nameValue, ruleValue, fromValue].filter(v => v && v.length > 0).length;
      if (filledValues === 0) {
        return;
      }
      if (filledValues < 3) {
        issues.push(`Complete every field for submission requirement #${index + 1}.`);
        return;
      }
      submissionRequirements.push({
        name: nameValue,
        rule: ruleValue,
        from: fromValue
      });
    });

    const inputDescriptors: InputDescriptor[] = [];
    this.inputDescriptors.controls.forEach((descriptorGroup, descriptorIndex) => {
      const idValue = (descriptorGroup.get('id')?.value as string)?.trim();
      const nameValue = (descriptorGroup.get('name')?.value as string)?.trim();
      const purposeValue = (descriptorGroup.get('purpose')?.value as string)?.trim();
      const groupValue = (descriptorGroup.get('group')?.value as string)?.trim();

      if (!idValue || !nameValue || !purposeValue) {
        issues.push(`Provide ID, name, and purpose for descriptor #${descriptorIndex + 1}.`);
      }

      const fieldsArray = descriptorGroup.get('fields') as FormArray<FormGroup>;
      const fields: DescriptorField[] = [];

      fieldsArray.controls.forEach((fieldGroup, fieldIndex) => {
        const pathRaw = (fieldGroup.get('path')?.value as string) ?? '';
        const purposeRaw = (fieldGroup.get('purpose')?.value as string) ?? '';
        const filterRaw = (fieldGroup.get('filter')?.value as string) ?? '';

        const paths = splitLines(pathRaw);
        if (!paths.length) {
          issues.push(
            `Provide at least one JSONPath for field #${fieldIndex + 1} in descriptor #${
              descriptorIndex + 1
            }.`
          );
          return;
        }

        let parsedFilter: unknown;
        if (filterRaw.trim().length > 0) {
          try {
            parsedFilter = JSON.parse(filterRaw);
          } catch {
            issues.push(
              `Filter for field #${fieldIndex + 1} in descriptor #${descriptorIndex + 1} is not valid JSON.`
            );
            return;
          }
        }

        const field: DescriptorField = {
          path: paths
        };
        if (purposeRaw.trim().length > 0) {
          field.purpose = purposeRaw.trim();
        }
        if (parsedFilter !== undefined) {
          field.filter = parsedFilter;
        }
        fields.push(field);
      });

      if (!fields.length) {
        issues.push(`Add at least one field to descriptor #${descriptorIndex + 1} constraints.`);
      }

      const groupList = splitCommaSeparated(groupValue);
      const descriptor: InputDescriptor = {
        id: idValue || `descriptor-${descriptorIndex + 1}`,
        name: nameValue || `Descriptor ${descriptorIndex + 1}`,
        purpose: purposeValue || ''
      };
      if (groupList.length) {
        descriptor.group = groupList;
      }
      descriptor.constraints = {
        fields
      };
      inputDescriptors.push(descriptor);
    });

    if (!inputDescriptors.length) {
      issues.push('Configure at least one input descriptor.');
    }

    if (issues.length) {
      return undefined;
    }

    const definition: PresentationDefinition = {
      id,
      name,
      purpose,
      format: {
        ldp_vp: {
          proof_type: proofTypes
        }
      },
      input_descriptors: inputDescriptors
    };

    if (submissionRequirements.length) {
      definition.submission_requirements = submissionRequirements;
    }

    return definition;
  }

  private createDownloadUrl(json: string): string {
    const blob = new Blob([json], { type: 'application/json' });
    return URL.createObjectURL(blob);
  }

  private revokeDownloadUrl(): void {
    if (this.downloadUrl) {
      URL.revokeObjectURL(this.downloadUrl);
      this.downloadUrl = undefined;
    }
  }
}

function clearFormArray(array: FormArray): void {
  while (array.length > 0) {
    array.removeAt(0);
  }
}

function splitLines(value: string): string[] {
  return value
    .split(/\r?\n/)
    .map(item => item.trim())
    .filter(item => item.length > 0);
}

function splitCommaSeparated(value?: string): string[] {
  if (!value) {
    return [];
  }
  return value
    .split(',')
    .map(item => item.trim())
    .filter(item => item.length > 0);
}

function jsonValidator(control: AbstractControl): ValidationErrors | null {
  const rawValue = (control.value as string | undefined) ?? '';
  if (!rawValue.trim()) {
    return null;
  }
  try {
    JSON.parse(rawValue);
    return null;
  } catch {
    return { json: true };
  }
}
