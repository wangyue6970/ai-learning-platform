import { File } from 'expo-file-system';
import { fetch } from 'expo/fetch';
import { API_BASE_URL } from './apiConfig';

export type ImportFileResult = {
  id: number;
  originalFileName: string;
  status:
    | 'WAITING_RECOGNITION'
    | 'RECOGNIZING'
    | 'WAITING_STRUCTURING'
    | 'STRUCTURING'
    | 'WAITING_CONFIRMATION'
    | 'RECOGNITION_FAILED'
    | 'STRUCTURING_FAILED'
    | 'UPLOAD_FAILED';
  errorMessage: string | null;
};

export type ImportBatchResult = {
  id: number;
  libraryId: number;
  status: string;
  files: ImportFileResult[];
};

export type QuestionDraftOption = {
  optionKey: string;
  content: string | null;
  sortOrder: number;
};

export type QuestionDraft = {
  id: number;
  importFileId: number;
  sortOrder: number;
  status: string;
  questionType: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';
  stem: string;
  correctAnswer: string[];
  explanation: string | null;
  knowledgePoints: string[];
  options: QuestionDraftOption[];
};

export type UpdateQuestionDraftRequest = {
  questionType: QuestionDraft['questionType'];
  stem: string;
  correctAnswer: string[];
  explanation: string | null;
  knowledgePoints: string[];
  options: QuestionDraftOption[];
};

type LocalImportFile = {
  uri: string;
};

export async function uploadImportFiles(
  libraryId: string,
  localFiles: LocalImportFile[]
): Promise<ImportBatchResult> {
  const formData = new FormData();

  for (const localFile of localFiles) {
    formData.append('files', new File(localFile.uri));
  }

  let response: Response;

  try {
    response = await fetch(`${API_BASE_URL}/api/libraries/${libraryId}/import-batches`, {
      method: 'POST',
      body: formData,
    });
  } catch {
    throw new Error(`无法连接上传服务：${API_BASE_URL}`);
  }

  if (!response.ok) {
    let message = '文件上传失败，请稍后重试';

    try {
      const errorBody: { message?: string } = await response.json();
      message = errorBody.message || message;
    } catch {
      // 后端没有返回可读取的错误内容时，使用默认提示。
    }

    throw new Error(message);
  }

  return response.json();
}

export async function fetchLatestImportBatch(libraryId: string): Promise<ImportBatchResult | null> {
  let response: Response;

  try {
    response = await fetch(
      `${API_BASE_URL}/api/libraries/${libraryId}/import-batches/latest`
    );
  } catch {
    throw new Error('无法读取导入进度，请检查电脑后端是否正在运行');
  }

  if (!response.ok) {
    let message = '读取导入进度失败，请稍后重试';
    try {
      const errorBody: { message?: string } = await response.json();
      message = errorBody.message || message;
    } catch {
      // Keep the default error message when the server does not return JSON.
    }
    throw new Error(message);
  }

  return response.json();
}

export async function getImportBatchDrafts(
  libraryId: string,
  importBatchId: string
): Promise<QuestionDraft[]> {
  let response: Response;

  try {
    response = await fetch(
      `${API_BASE_URL}/api/libraries/${libraryId}/import-batches/${importBatchId}/drafts`
    );
  } catch {
    throw new Error('无法连接草稿服务，请检查电脑后端是否正在运行');
  }

  if (!response.ok) {
    let message = '读取本批次草稿失败，请稍后重试';
    try {
      const errorBody: { message?: string } = await response.json();
      message = errorBody.message || message;
    } catch {
      // Keep the default error message when the server does not return JSON.
    }
    throw new Error(message);
  }

  return response.json();
}

export async function getImportFileDrafts(
  libraryId: string,
  importFileId: string
): Promise<QuestionDraft[]> {
  let response: Response;

  try {
    response = await fetch(
      `${API_BASE_URL}/api/libraries/${libraryId}/import-batches/files/${importFileId}/drafts`
    );
  } catch {
    throw new Error('无法连接草稿服务，请检查电脑后端是否正在运行');
  }

  if (!response.ok) {
    let message = '读取题目草稿失败，请稍后重试';
    try {
      const errorBody: { message?: string } = await response.json();
      message = errorBody.message || message;
    } catch {
      // Keep the default error message when the server does not return JSON.
    }
    throw new Error(message);
  }

  return response.json();
}

export async function updateImportFileDraft(
  libraryId: string,
  importFileId: string,
  draftId: string,
  request: UpdateQuestionDraftRequest
): Promise<QuestionDraft> {
  let response: Response;

  try {
    response = await fetch(
      `${API_BASE_URL}/api/libraries/${libraryId}/import-batches/files/${importFileId}/drafts/${draftId}`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      }
    );
  } catch {
    throw new Error('无法连接草稿保存服务，请检查电脑后端是否正在运行');
  }

  if (!response.ok) {
    let message = '保存草稿失败，请检查题目内容后重试';
    try {
      const errorBody: { message?: string } = await response.json();
      message = errorBody.message || message;
    } catch {
      // Keep the default error message when the server does not return JSON.
    }
    throw new Error(message);
  }

  return response.json();
}

export async function confirmImportFileDraft(
  libraryId: string,
  importFileId: string,
  draftId: number
): Promise<QuestionDraft> {
  let response: Response;

  try {
    response = await fetch(
      `${API_BASE_URL}/api/libraries/${libraryId}/import-batches/files/${importFileId}/drafts/${draftId}/confirm`,
      { method: 'POST' }
    );
  } catch {
    throw new Error('无法连接确认入库服务，请检查电脑后端是否正在运行');
  }

  if (!response.ok) {
    let message = '确认入库失败，请检查草稿内容后重试';
    try {
      const errorBody: { message?: string } = await response.json();
      message = errorBody.message || message;
    } catch {
      // Keep the default error message when the server does not return JSON.
    }
    throw new Error(message);
  }

  return response.json();
}
