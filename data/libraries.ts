export type Library = {
  id: string;
  name: string;
  wrongQuestionCount: number;
};

export const initialLibraries: Library[] = [
  {
    id: 'operating-systems',
    name: '操作系统期末',
    wrongQuestionCount: 12,
  },
  {
    id: 'data-structures',
    name: '数据结构',
    wrongQuestionCount: 5,
  },
  {
    id: 'english-vocabulary',
    name: '英语四级词汇',
    wrongQuestionCount: 0,
  },
];
