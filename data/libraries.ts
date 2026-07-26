export type Library = {
  id: string;
  name: string;
  questionCount: number;
  wrongQuestionCount: number;
};

export const initialLibraries: Library[] = [
  {
    id: 'operating-systems',
    name: '操作系统期末',
    questionCount: 128,
    wrongQuestionCount: 12,
  },
  {
    id: 'data-structures',
    name: '数据结构',
    questionCount: 86,
    wrongQuestionCount: 5,
  },
  {
    id: 'english-vocabulary',
    name: '英语四级词汇',
    questionCount: 245,
    wrongQuestionCount: 0,
  },
];
