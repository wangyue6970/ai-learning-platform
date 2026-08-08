import { API_BASE_URL } from './apiConfig';

export type PracticeQuestionType = 'single_choice' | 'multiple_choice' | 'true_false';

export type PracticeQuestion = {
  id: string;
  libraryId: string;
  type: PracticeQuestionType;
  stem: string;
  options: Array<{ id: string; text: string }>;
};

type PracticeQuestionResponse = {
  id: number;
  libraryId: number;
  questionType: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';
  stem: string;
  options: Array<{ optionKey: string; content: string; sortOrder: number }>;
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

async function fetchQuestions(path: string): Promise<PracticeQuestion[]> {
  const response = await fetch(`${API_BASE_URL}${path}`);

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
