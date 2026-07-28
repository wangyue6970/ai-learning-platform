export type QuestionType = 'single_choice' | 'multiple_choice' | 'true_false';

export type QuestionOption = {
  id: string;
  text: string;
};

export type Question = {
  id: string;
  libraryId: string;
  type: QuestionType;
  stem: string;
  options: QuestionOption[];
  correctOptionIds: string[];
};

export const initialQuestions: Question[] = [
  {
    id: 'os-question-1',
    libraryId: 'operating-systems',
    type: 'single_choice',
    stem: '在操作系统中，负责选择下一个运行进程的是？',
    options: [
      { id: 'option-a', text: '文件系统' },
      { id: 'option-b', text: '进程调度程序' },
      { id: 'option-c', text: '内存管理器' },
      { id: 'option-d', text: '编译器' },
    ],
    correctOptionIds: ['option-b'],
  },
  {
    id: 'os-question-2',
    libraryId: 'operating-systems',
    type: 'multiple_choice',
    stem: '以下哪些属于操作系统的基本功能？',
    options: [
      { id: 'option-a', text: '进程管理' },
      { id: 'option-b', text: '内存管理' },
      { id: 'option-c', text: '图像编辑' },
      { id: 'option-d', text: '文件管理' },
    ],
    correctOptionIds: ['option-a', 'option-b', 'option-d'],
  },
  {
    id: 'os-question-3',
    libraryId: 'operating-systems',
    type: 'true_false',
    stem: '操作系统负责管理计算机的硬件和软件资源。',
    options: [
      { id: 'option-true', text: '正确' },
      { id: 'option-false', text: '错误' },
    ],
    correctOptionIds: ['option-true'],
  },
];
