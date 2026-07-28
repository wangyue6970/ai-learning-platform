export type QuestionLearningStatus = {
  questionId: string;
  libraryId: string;
  isInWrongSet: boolean;
  consecutiveCorrectCount: number;
};

export const initialQuestionLearningStatuses: QuestionLearningStatus[] = [];
