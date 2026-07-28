import { createContext, useContext, useState, type Dispatch, type ReactNode, type SetStateAction } from 'react';
import { initialQuestionLearningStatuses, type QuestionLearningStatus } from '../data/questionLearning';

type QuestionLearningContextValue = {
  learningStatuses: QuestionLearningStatus[];
  setLearningStatuses: Dispatch<SetStateAction<QuestionLearningStatus[]>>;
};

const QuestionLearningContext = createContext<QuestionLearningContextValue | undefined>(undefined);

export function QuestionLearningProvider({ children }: { children: ReactNode }) {
  const [learningStatuses, setLearningStatuses] = useState<QuestionLearningStatus[]>(initialQuestionLearningStatuses);

  return (
    <QuestionLearningContext.Provider value={{ learningStatuses, setLearningStatuses }}>
      {children}
    </QuestionLearningContext.Provider>
  );
}

export function useQuestionLearning() {
  const context = useContext(QuestionLearningContext);

  if (!context) {
    throw new Error('useQuestionLearning 必须在 QuestionLearningProvider 内使用');
  }

  return context;
}
