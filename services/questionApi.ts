import { apiFetch } from './apiClient';

export type PracticeQuestionType = 'single_choice' | 'multiple_choice' | 'true_false';

export type PracticeQuestion = {
  id: string;
  libraryId: string;
  type: PracticeQuestionType;
  stem: string;
  options: Array<{ id: string; text: string }>;
};

export type EditableQuestion = PracticeQuestion & {
  correctAnswer: string[];
  explanation: string | null;
};

export type UpdateQuestionPayload = {
  questionType: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';
  stem: string;
  options: Array<{ optionKey: string; content: string; sortOrder: number }>;
  correctAnswer: string[];
  explanation: string | null;
};

export type SubmitAnswerResult = {
  correct: boolean;
  correctAnswer: string[];
  explanation: string | null;
  consecutiveCorrectCount: number;
  removedFromWrongQuestions: boolean;
};

type PracticeQuestionResponse = {
  id: number;
  libraryId: number;
  questionType: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';
  stem: string;
  options: Array<{ optionKey: string; content: string; sortOrder: number }>;
};

type QuestionDetailResponse = PracticeQuestionResponse & {
  correctAnswer: string[];
  explanation: string | null;
};

const questionTypeMap: Record<PracticeQuestionResponse['questionType'], PracticeQuestionType> = {
  SINGLE_CHOICE: 'single_choice',
  MULTIPLE_CHOICE: 'multiple_choice',
  TRUE_FALSE: 'true_false',
};

export async function fetchPracticeQuestions(libraryId: string): Promise<PracticeQuestion[]> {
  return fetchQuestions(`/api/questions/library/${libraryId}`);
}

export async function fetchWrongQuestions(libraryId: string): Promise<PracticeQuestion[]> {
  return fetchQuestions(`/api/practice/wrong-questions/library/${libraryId}`);
}

export async function fetchQuestionDetail(questionId: string): Promise<EditableQuestion> {
  const response = await apiFetch(`/api/questions/${questionId}`);

  if (!response.ok) {
    throw new Error('题目详情加载失败');
  }

  const question: QuestionDetailResponse = await response.json();
  return {
    ...normalizePracticeQuestion(question),
    correctAnswer: question.correctAnswer,
    explanation: question.explanation,
  };
}

export async function updateQuestion(
  questionId: string,
  payload: UpdateQuestionPayload
): Promise<EditableQuestion> {
  const response = await apiFetch(`/api/questions/${questionId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error('题目保存失败');
  }

  const question: QuestionDetailResponse = await response.json();
  return {
    ...normalizePracticeQuestion(question),
    correctAnswer: question.correctAnswer,
    explanation: question.explanation,
  };
}

export async function deleteQuestion(questionId: string): Promise<void> {
  const response = await apiFetch(`/api/questions/${questionId}`, {
    method: 'DELETE',
  });

  if (!response.ok) {
    throw new Error('题目删除失败');
  }
}

export async function submitAnswer(
  libraryId: string,
  questionId: string,
  selectedAnswer: string[]
): Promise<SubmitAnswerResult> {
  const response = await apiFetch('/api/practice/answers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ libraryId: Number(libraryId), questionId: Number(questionId), selectedAnswer }),
  });

  if (!response.ok) {
    throw new Error('答案提交失败');
  }

  return response.json();
}

async function fetchQuestions(path: string): Promise<PracticeQuestion[]> {
  const response = await apiFetch(path);

  if (!response.ok) {
    throw new Error('题目加载失败');
  }

  const questions: PracticeQuestionResponse[] = await response.json();
  return questions.map(normalizePracticeQuestion);
}

function normalizePracticeQuestion(question: PracticeQuestionResponse): PracticeQuestion {
  return {
    id: String(question.id),
    libraryId: String(question.libraryId),
    type: questionTypeMap[question.questionType],
    stem: question.stem,
    options: question.options
      .sort((firstOption, secondOption) => firstOption.sortOrder - secondOption.sortOrder)
      .map((option) => ({ id: option.optionKey, text: option.content })),
  };
}
